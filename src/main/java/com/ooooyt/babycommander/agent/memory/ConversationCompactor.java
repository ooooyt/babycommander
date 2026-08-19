package com.ooooyt.babycommander.agent.memory;

import com.ooooyt.babycommander.agent.DeepSeekChatModel;
import com.ooooyt.babycommander.config.AgentConfig;
import com.ooooyt.babycommander.config.YamlConfigLoader;
import com.ooooyt.babycommander.db.SummarizerConfig;
import com.ooooyt.babycommander.util.TokenCounter;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class ConversationCompactor {

    private static final String SUMMARY_PROMPT = """
        You are a conversation summarizer. You will receive a series of conversation \
        messages between a user and an AI assistant, including tool calls and their results. \
        Summarize the key points, decisions, and outcomes in a single paragraph. \
        Focus on: what was requested, what was done, what tools were called, and what was the result. \
        Omit redundant or trivial exchanges. Be concise but preserve all actionable information.
        """;

    private static final int MAX_USER_MESSAGE_TOKENS = 2000;

    @Inject
    SummarizerConfig summarizerConfig;

    @Inject
    YamlConfigLoader yamlConfigLoader;

    private final TokenCounter tokenCounter = new TokenCounter();

    public List<ChatMessage> compact(List<ChatMessage> messages) {
        if (messages.isEmpty()) return List.of();

        int boundary = findBoundary(messages);
        if (boundary <= 0) {
            return new ArrayList<>(messages);
        }

        List<ChatMessage> result = new ArrayList<>();
        StringBuilder combinedMsg = new StringBuilder();
        boolean hasPreviousUserMessage = false;

        for (int i = 0; i < boundary; i++) {
            ChatMessage msg = messages.get(i);
            if (msg instanceof SummaryMessage) {
                result.add(msg);
                continue;
            }
            if (msg instanceof BackgroundMessage) {
                result.add(msg);
                continue;
            }            if (isAlreadySummarized(msg)) {
                result.add(msg);
                continue;
            }

            if (msg instanceof UserMessage um) {
                if (hasPreviousUserMessage && !combinedMsg.isEmpty()) {
                    String summary = summarize(combinedMsg.toString());
                    result.add(AiMessage.from("[summarized] " + summary));
                    combinedMsg.setLength(0);
                }

                int tokenCount = tokenCounter.estimateTokens(um);
                if (tokenCount > MAX_USER_MESSAGE_TOKENS) {
                    String summary = summarize(um.singleText());
                    result.add(new UserMessage("[summarized] " + summary));
                } else {
                    result.add(um);
                }
                hasPreviousUserMessage = true;

            } else if (msg instanceof AiMessage aiMsg) {
                if (aiMsg.hasToolExecutionRequests()) {
                    combinedMsg.append("AI (request to call tools): ")
                        .append(aiMsg.toolExecutionRequests()).append("\n");
                }
                if (aiMsg.text() != null) {
                    combinedMsg.append("AI: ").append(aiMsg.text()).append("\n");
                }

            } else if (msg instanceof ToolExecutionResultMessage toolMsg) {
                combinedMsg.append("Agent(tool execution result): ")
                    .append(toolMsg.toolName()).append(": ")
                    .append(toolMsg.text()).append("\n");
            }
        }

        if (!combinedMsg.isEmpty()) {
            String summary = summarize(combinedMsg.toString());
            result.add(AiMessage.from("[summarized] " + summary));
        }

        for (int i = boundary; i < messages.size(); i++) {
            result.add(messages.get(i));
        }

        return stripDanglingToolCalls(result);
    }

    /**
     * Ensures every AiMessage carrying tool_execution_requests in the result is
     * immediately followed by ToolExecutionResultMessages for ALL of its ids.
     * When compaction fires mid-tool-loop (via onMemoryFull in
     * ToolCallAwareChatMemory.add), the last AiMessage may carry tool calls whose
     * results have not been appended yet; OpenAI/DeepSeek then rejects the request
     * with "insufficient tool messages following tool_calls". Stripping the
     * dangling tool_execution_requests (keeping the text) makes the list valid.
     */
    private List<ChatMessage> stripDanglingToolCalls(List<ChatMessage> messages) {
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
                    // Keep the assistant's text but drop the dangling tool calls so the
                    // message is still API-valid (content set, no tool calls).
                    messages.set(i, AiMessage.from(ai.text()));
                } else {
                    // The assistant message carried ONLY dangling tool calls with no text.
                    // Emitting AiMessage.from("") would produce an assistant message with
                    // neither content nor tool_calls, which OpenAI/DeepSeek rejects with
                    // "Invalid assistant message: content or toolcalls must be set".
                    // Remove the empty message entirely.
                    messages.remove(i);
                    i--;
                }
            }
        }
        return messages;
    }

    private int findBoundary(List<ChatMessage> messages) {
        int lastUserIdx = -1;
        int lastAiIdx = -1;
        int secondToLastAiIdx = -1;

        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (lastUserIdx == -1 && msg instanceof UserMessage um
                    && !(msg instanceof SummaryMessage)
                    && !isAlreadySummarized(msg)) {
                lastUserIdx = i;
            }
            if (msg instanceof AiMessage ai && !isAlreadySummarized(msg)) {
                if (lastAiIdx == -1) {
                    lastAiIdx = i;
                } else if (secondToLastAiIdx == -1) {
                    secondToLastAiIdx = i;
                }
            }
        }

        return Math.max(lastUserIdx, secondToLastAiIdx);
    }

    public void compactMemory(ToolCallAwareChatMemory memory) {
        List<ChatMessage> allMessages = memory.messages();
        List<ChatMessage> messagesToCompact = allMessages.stream()
            .filter(m -> !(m instanceof SummaryMessage))
            .filter(m -> !(m instanceof BackgroundMessage))
            .toList();

        if (messagesToCompact.size() <= 1) {
            return;
        }

        // Preserve the tail message (the current in-flight turn). Compaction only ever
        // summarizes the historical prefix; the tail is kept verbatim and re-appended so
        // an in-progress tool call + its result are never torn apart or stripped.
        ChatMessage tail = messagesToCompact.get(messagesToCompact.size() - 1);
        List<ChatMessage> historyToCompact = messagesToCompact.subList(0, messagesToCompact.size() - 1);

        List<ChatMessage> compacted = compact(historyToCompact);
        compacted.add(tail);
        memory.replaceMemoryHistory(compacted.toArray(new ChatMessage[0]));

        Log.infof("ConversationCompactor: compacted %d messages -> %d messages",
            historyToCompact.size(), compacted.size());
    }

    private boolean isAlreadySummarized(ChatMessage msg) {
        if (msg instanceof AiMessage ai && ai.text() != null && ai.text().startsWith("[summarized] ")) {
            return true;
        }
        if (msg instanceof UserMessage um) {
            String text = um.singleText();
            return text != null && text.startsWith("[summarized] ");
        }
        if (msg instanceof SystemMessage sm && sm.text() != null && sm.text().startsWith("[summarized] ")) {
            return true;
        }
        return false;
    }

    private String summarize(String text) {
        try {
            ChatModel model = createModel();
            List<ChatMessage> msgList = new ArrayList<>();
            msgList.add(new SystemMessage(SUMMARY_PROMPT));
            msgList.add(new UserMessage(text));
            String response = model.chat(msgList).aiMessage().text();
            if (response != null && !response.isBlank()) {
                return response;
            }
        } catch (Exception e) {
            Log.errorf(e, "ConversationCompactor: LLM summarization failed, returning original text");
        }
        return text;
    }

    private ChatModel createModel() {
        String providerName = summarizerConfig.provider();
        if ("default".equals(providerName) || providerName.isBlank()) {
            providerName = resolveDefaultProvider();
        }

        var providerConfig = yamlConfigLoader.getConfig().providers.get(providerName);
        if (providerConfig == null) {
            throw new IllegalStateException("Provider not configured for summarization: " + providerName);
        }

        String modelNameOverride = summarizerConfig.modelName();
        if (modelNameOverride == null || modelNameOverride.isBlank() || "default".equalsIgnoreCase(modelNameOverride)) {
            modelNameOverride = providerConfig.modelName;
        }

        Duration timeout = Duration.ofSeconds(providerConfig.timeoutSeconds);

        return switch (providerConfig.type) {
            case "openai" -> OpenAiChatModel.builder()
                .baseUrl(providerConfig.baseUrl == null || providerConfig.baseUrl.isEmpty() ? null : providerConfig.baseUrl)
                .apiKey(providerConfig.apiKey)
                .modelName(modelNameOverride)
                .temperature(summarizerConfig.temperature())
                .maxTokens(providerConfig.maxTokens)
                .timeout(timeout)
                .build();
            case "anthropic" -> AnthropicChatModel.builder()
                .baseUrl(providerConfig.baseUrl == null || providerConfig.baseUrl.isEmpty() ? null : providerConfig.baseUrl)
                .apiKey(providerConfig.apiKey)
                .modelName(modelNameOverride)
                .temperature(summarizerConfig.temperature())
                .maxTokens(providerConfig.maxTokens)
                .timeout(timeout)
                .build();
            case "deepseek" -> DeepSeekChatModel.builder()
                .baseUrl(providerConfig.baseUrl)
                .apiKey(providerConfig.apiKey)
                .modelName(modelNameOverride)
                .temperature(summarizerConfig.temperature())
                .maxTokens(providerConfig.maxTokens)
                .timeout(timeout)
                .build();
            case "ollama" -> OllamaChatModel.builder()
                .baseUrl(providerConfig.baseUrl)
                .modelName(modelNameOverride)
                .temperature(summarizerConfig.temperature())
                .numPredict(providerConfig.maxTokens)
                .timeout(timeout)
                .build();
            default -> throw new IllegalArgumentException("Unsupported provider: " + providerConfig.type);
        };
    }

    private String resolveDefaultProvider() {
        AgentConfig config = yamlConfigLoader.getConfig();
        String defaultModel = config.defaultModel;
        if (defaultModel != null && !defaultModel.isBlank()
                && config.providers != null && config.providers.containsKey(defaultModel)) {
            return defaultModel;
        }
        return config.agentDefaults.provider;
    }
}
