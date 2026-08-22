package com.ooooyt.babycommander.agent.memory;

import com.ooooyt.babycommander.util.TokenCounter;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class ToolCallAwareChatMemory implements ChatMemory {

    private static final int DEFAULT_MAX_TOKENS_IN_MEMORY = 60000;

    private final List<ChatMessage> messages = new ArrayList<>();
    private final Object memoryId;
    @Getter
    private final int maxTokensInMemory;
    @Getter
    private final TokenCounter tokenCounter;
    private int estimatedTokenTotal;
    private long lastCalibrationSampleCount;
    @Setter
    private Consumer<ToolCallAwareChatMemory> onMemoryFull;
    @Setter
    private Consumer<ToolCallAwareChatMemory> onPhaseSummaryCheck;
    private boolean summarizing;

    private BackgroundMessage backgroundMessage; // workspace/project background (read-only after creation)
    private SummaryMessage taskSummary;          // 1/3 — task brief (read-only after creation)
    private SummaryMessage phaseSummary;         // 2/3 — plan phases with status
    private SummaryMessage progressSummary;      // 3/3 — current phase progress (modifiable)

    public ToolCallAwareChatMemory(Object memoryId) {
        this(memoryId, DEFAULT_MAX_TOKENS_IN_MEMORY);
    }

    public ToolCallAwareChatMemory(Object memoryId, int maxTokensInMemory) {
        this.memoryId = memoryId;
        this.maxTokensInMemory = maxTokensInMemory;
        this.tokenCounter = new TokenCounter();
        this.estimatedTokenTotal = 0;
    }

    @Override
    public Object id() {
        return memoryId;
    }

    @Override
    public synchronized void add(ChatMessage message) {
        if (message instanceof SummaryMessage sm) {
            setSummary(sm);
            return;
        }
        if (message instanceof BackgroundMessage bm) {
            backgroundMessage = bm;
            return;
        }
        messages.add(message);
        estimatedTokenTotal += tokenCounter.estimateTokens(message);
        removeOrphanedToolResults();
        // Compaction is only safe when the tool-calling loop has settled, i.e. when the
        // tail message is NOT an in-flight tool call awaiting its result. Firing compaction
        // mid-tool-loop (right after an AiMessage with tool calls is added but before its
        // ToolExecutionResultMessage) would strip the dangling tool call and could produce
        // an invalid assistant message. Defer to settled states: a UserMessage (start of a
        // new user turn) or a final AiMessage without tool calls (end of the tool loop).
        boolean settled = message instanceof UserMessage
                || (message instanceof AiMessage ai && !ai.hasToolExecutionRequests());
        if (settled && onMemoryFull != null
                && estimatedTokenTotal + summaryTokens() > maxTokensInMemory && !summarizing) {
            summarizing = true;
            try {
                onMemoryFull.accept(this);
            } finally {
                summarizing = false;
            }
        }
    }

    @Override
    public synchronized List<ChatMessage> messages() {
        removeOrphanedToolResults();
        if (onPhaseSummaryCheck != null) {
            onPhaseSummaryCheck.accept(this);
        }
        List<ChatMessage> combined = new ArrayList<>(5 + messages.size());

        // 1. Core system prompt (instructions) first — it's the first SystemMessage
        //    added by LangChain4j's DefaultAiServices via systemMessageProvider.
        ChatMessage coreSystem = null;
        if (!messages.isEmpty() && messages.get(0) instanceof SystemMessage) {
            coreSystem = messages.get(0);
        }
        if (coreSystem != null) combined.add(coreSystem);

        // 2. Background (workspace/project info)
        if (backgroundMessage != null) combined.add(backgroundMessage);
        // 3. Summary messages (task summary, plan status, progress)
        if (taskSummary != null) combined.add(taskSummary);
        if (phaseSummary != null) combined.add(phaseSummary);
        if (progressSummary != null) combined.add(progressSummary);

        // 4. Remaining messages (skip the first one if it was the core system prompt)
        int start = (coreSystem != null) ? 1 : 0;
        for (int i = start; i < messages.size(); i++) {
            combined.add(messages.get(i));
        }
        // Guard against the OpenAI/DeepSeek requirement that every assistant
        // message carrying tool calls must be immediately followed by tool
        // messages responding to each toolCallId. A dangling AiMessage (tool
        // calls whose ToolExecutionResultMessages have not been appended) would
        // be rejected with "insufficient tool messages following toolcalls".
        // Strip those tool calls here so the returned list is always API-valid.
        stripDanglingToolCalls(combined);
        return combined;
    }

    /**
     * Removes tool-execution requests from any AiMessage whose results are not
     * present in the immediately following messages. Keeps the assistant's text
     * when present; otherwise drops the empty message entirely (an assistant
     * message with neither content nor tool_calls is also rejected by the API).
     */
    private static void stripDanglingToolCalls(List<ChatMessage> messages) {
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            if (!(msg instanceof AiMessage ai) || !ai.hasToolExecutionRequests()) {
                continue;
            }
            List<String> expectedIds = ai.toolExecutionRequests().stream()
                .map(r -> r.id()).toList();
            int matched = 0;
            for (int j = i + 1; j < messages.size() && matched < expectedIds.size(); j++) {
                ChatMessage next = messages.get(j);
                if (next instanceof ToolExecutionResultMessage trm
                        && expectedIds.contains(trm.id())) {
                    matched++;
                } else {
                    break;
                }
            }
            if (matched < expectedIds.size()) {
                if (ai.text() != null && !ai.text().isEmpty()) {
                    messages.set(i, AiMessage.from(ai.text()));
                } else {
                    messages.remove(i);
                    i--;
                }
            }
        }
    }

    @Override
    public synchronized void clear() {
        messages.clear();
        backgroundMessage = null;
        taskSummary = null;
        phaseSummary = null;
        progressSummary = null;
        estimatedTokenTotal = 0;
        tokenCounter.reset();
    }

    public synchronized int getEstimatedTokenTotal() {
        long currentSampleCount = TokenCounter.getGlobalSampleCount();
        if (currentSampleCount != lastCalibrationSampleCount && currentSampleCount > 0) {
            recalculateTokenTotal();
            lastCalibrationSampleCount = currentSampleCount;
        }
        return estimatedTokenTotal + summaryTokens();
    }

    /** Backward-compat setter — updates the 3/3 progress summary. */
    public void setSummary(SummaryMessage sm) {
        this.progressSummary = sm;
    }

    /** Backward-compat updater — updates the 3/3 progress summary. */
    public void updateSummary(String text) {
        if (progressSummary != null) {
            progressSummary.updateText(text);
        } else {
            progressSummary = new SummaryMessage(text);
        }
    }

    /** Update the 3/3 progress summary (alias for updateSummary). */
    public void updateProgressSummary(String text) {
        updateSummary(text);
    }

    /**
     * Remove old tool-call pairs (AiMessage + ToolExecutionResultMessage) keeping only
     * the most recent {@code maxPairs} pairs. If there are fewer pairs, this is a no-op.
     * As a fallback, if there are no tool-call pairs to remove, non-tool messages (excluding
     * UserMessage) are removed from the beginning.
     */
    public synchronized void ejectOldMessages(int maxPairs) {
        // Collect indices of complete tool-call pairs (AiMessage + matching ToolExecutionResultMessage)
        List<int[]> pairIndices = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            if (msg instanceof AiMessage aiMsg && aiMsg.hasToolExecutionRequests()) {
                List<String> ids = aiMsg.toolExecutionRequests().stream()
                    .map(r -> r.id()).toList();
                List<Integer> resultIndices = new ArrayList<>();
                boolean allFound = true;
                for (String id : ids) {
                    boolean found = false;
                    for (int j = i + 1; j < messages.size(); j++) {
                        if (messages.get(j) instanceof ToolExecutionResultMessage trm
                            && trm.id().equals(id)) {
                            resultIndices.add(j);
                            found = true;
                            break;
                        }
                    }
                    if (!found) { allFound = false; break; }
                }
                if (allFound) {
                    int[] pair = new int[1 + resultIndices.size()];
                    pair[0] = i;
                    for (int k = 0; k < resultIndices.size(); k++) {
                        pair[1 + k] = resultIndices.get(k);
                    }
                    pairIndices.add(pair);
                }
            }
        }

        int tokensRemoved = 0;

        if (pairIndices.size() > maxPairs) {
            int toRemove = pairIndices.size() - maxPairs;
            // Collect all indices to remove from the oldest pairs
            Set<Integer> indicesToRemove = new HashSet<>();
            for (int p = 0; p < toRemove; p++) {
                for (int idx : pairIndices.get(p)) {
                    indicesToRemove.add(idx);
                }
            }
            // Remove in descending order
            List<Integer> sorted = new ArrayList<>(indicesToRemove);
            sorted.sort((a, b) -> b - a);
            for (int idx : sorted) {
                if (idx < messages.size()) {
                    ChatMessage removed = messages.remove(idx);
                    tokensRemoved += tokenCounter.estimateTokens(removed);
                }
            }
        } else if (pairIndices.isEmpty()) {
            // Fallback: remove non-tool, non-UserMessage messages from the beginning
            List<Integer> toRemove = new ArrayList<>();
            for (int i = 0; i < messages.size() && toRemove.size() < messages.size() - maxPairs; i++) {
                ChatMessage msg = messages.get(i);
                if (!(msg instanceof UserMessage) && !(msg instanceof ToolExecutionResultMessage)
                    && !(msg instanceof AiMessage && ((AiMessage) msg).hasToolExecutionRequests())) {
                    toRemove.add(i);
                }
            }
            if (!toRemove.isEmpty()) {
                toRemove.sort((a, b) -> b - a);
                for (int idx : toRemove) {
                    if (idx < messages.size()) {
                        ChatMessage removed = messages.remove(idx);
                        tokensRemoved += tokenCounter.estimateTokens(removed);
                    }
                }
            }
        }

        if (tokensRemoved > 0) {
            estimatedTokenTotal = Math.max(0, estimatedTokenTotal - tokensRemoved);
        }
    }

    /** Backward-compat getter — returns the 3/3 progress summary. */
    public SummaryMessage getSummary() {
        return progressSummary;
    }

    /** Set the background section (workspace/project info). Call once per session. */
    public void setBackground(String text) {
        this.backgroundMessage = new BackgroundMessage(text);
    }

    /** Set the 1/3 task summary (call once per session). */
    public void setTaskSummary(String text) {
        taskSummary = new SummaryMessage("## Task\n" + text + "\n");
    }

    /** Update the 2/3 phase summary from PlanTool phases. */
    public void updatePhaseSummary(List<?> phases) {
        StringBuilder sb = new StringBuilder("## Plan Status\n");
        if (phases != null && !phases.isEmpty()) {
            for (int i = 0; i < phases.size(); i++) {
                Object p = phases.get(i);
                String title = invokeGetter(p, "title");
                String desc = invokeGetter(p, "description");
                String status = invokeGetter(p, "status");
                String icon = switch (status) {
                    case "completed" -> "✓";
                    case "active" -> "▶";
                    case "failed" -> "✗";
                    default -> "·";
                };
                sb.append(icon).append(" Phase ").append(i + 1).append(": ")
                  .append(title);
                if (desc != null && !desc.isBlank()) {
                    sb.append(" - ").append(desc);
                }
                sb.append(" [").append(status).append("]\n");
            }
        }
        phaseSummary = new SummaryMessage(sb.toString());
    }

    /** Count the number of tool call requests across all messages. */
    public int countToolCalls() {
        int count = 0;
        for (ChatMessage msg : messages) {
            if (msg instanceof AiMessage aiMsg && aiMsg.hasToolExecutionRequests()) {
                count += aiMsg.toolExecutionRequests().size();
            }
        }
        return count;
    }

    private int summaryTokens() {
        int t = 0;
        if (backgroundMessage != null) t += tokenCounter.estimateTokens(backgroundMessage);
        if (taskSummary != null) t += tokenCounter.estimateTokens(taskSummary);
        if (phaseSummary != null) t += tokenCounter.estimateTokens(phaseSummary);
        if (progressSummary != null) t += tokenCounter.estimateTokens(progressSummary);
        return t;
    }

    public synchronized void recalculateTokenTotal() {
        estimatedTokenTotal = 0;
        for (ChatMessage msg : messages) {
            estimatedTokenTotal += tokenCounter.estimateTokens(msg);
        }
    }

    /** Remove spent tool-call pairs (AiMessage with tool requests + their ToolExecutionResultMessages). */
    public synchronized void removeSpentToolCallPairs() {
        List<Integer> toRemove = new ArrayList<>();
        int tokensRemoved = 0;

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            if (msg instanceof AiMessage aiMsg && aiMsg.hasToolExecutionRequests()) {
                List<String> ids = aiMsg.toolExecutionRequests().stream()
                    .map(r -> r.id()).toList();
                List<Integer> resultIndices = new ArrayList<>();
                boolean allFound = true;

                for (String id : ids) {
                    boolean found = false;
                    for (int j = i + 1; j < messages.size(); j++) {
                        if (messages.get(j) instanceof ToolExecutionResultMessage resultMsg
                            && resultMsg.id().equals(id)) {
                            resultIndices.add(j);
                            found = true;
                            break;
                        }
                    }
                    if (!found) { allFound = false; break; }
                }

                if (allFound) {
                    toRemove.add(i);
                    toRemove.addAll(resultIndices);
                }
            }
        }

        if (!toRemove.isEmpty()) {
            toRemove.sort((a, b) -> b - a);
            for (int idx : toRemove) {
                if (idx < messages.size()) {
                    ChatMessage removed = messages.remove(idx);
                    tokensRemoved += tokenCounter.estimateTokens(removed);
                }
            }
            estimatedTokenTotal = Math.max(0, estimatedTokenTotal - tokensRemoved);
        }
    }

    /** Remove ToolExecutionResultMessages whose parent AiMessage is no longer present. */
    public synchronized void removeOrphanedToolResults() {
        Set<String> activeToolCallIds = new HashSet<>();
        for (ChatMessage msg : messages) {
            if (msg instanceof AiMessage aiMsg && aiMsg.hasToolExecutionRequests()) {
                for (var req : aiMsg.toolExecutionRequests()) {
                    activeToolCallIds.add(req.id());
                }
            }
        }
        List<Integer> toRemove = new ArrayList<>();
        int tokensRemoved = 0;
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            if (msg instanceof ToolExecutionResultMessage trm
                    && !activeToolCallIds.contains(trm.id())) {
                toRemove.add(i);
            }
        }
        for (int i = toRemove.size() - 1; i >= 0; i--) {
            int idx = toRemove.get(i);
            if (idx < messages.size()) {
                ChatMessage removed = messages.remove(idx);
                tokensRemoved += tokenCounter.estimateTokens(removed);
            }
        }
        if (tokensRemoved > 0) {
            estimatedTokenTotal = Math.max(0, estimatedTokenTotal - tokensRemoved);
        }
    }

    /** Remove both complete spent pairs and orphaned tool results. */
    public synchronized void compact() {
        removeSpentToolCallPairs();
        removeOrphanedToolResults();
    }

    public synchronized void replaceMemoryHistory(ChatMessage... newMessages) {
        SummaryMessage task = this.taskSummary;
        SummaryMessage phase = this.phaseSummary;
        SummaryMessage progress = this.progressSummary;
        BackgroundMessage bg = this.backgroundMessage;

        messages.clear();
        estimatedTokenTotal = 0;

        this.backgroundMessage = bg;
        this.taskSummary = task;
        this.phaseSummary = phase;
        this.progressSummary = progress;

        for (ChatMessage msg : newMessages) {
            messages.add(msg);
            estimatedTokenTotal += tokenCounter.estimateTokens(msg);
        }
    }

    private static String invokeGetter(Object obj, String field) {
        try {
            var m = obj.getClass().getMethod(field);
            return String.valueOf(m.invoke(obj));
        } catch (Exception e) {
            return "?";
        }
    }
}
