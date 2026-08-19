package com.ooooyt.babycommander.agent;

import com.ooooyt.babycommander.status.StatusEventContext;
import com.ooooyt.babycommander.status.StatusEventPublisher;
import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.listener.ChatModelListener;

import java.util.List;

/**
 * A {@link ChatModel} decorator that publishes a {@code TOOL_CALL_TEXT}
 * status event whenever the underlying model returns an assistant message
 * that carries both a descriptive text and one or more tool execution
 * requests.
 *
 * <p>Many LLMs emit a short explanatory sentence alongside the tool-call
 * requests (e.g. "Let me search the codebase for that symbol."). Surfacing
 * this text before the tools actually run improves transparency and keeps
 * the user informed about what the agent is about to do.
 *
 * <p>When no {@link StatusEventPublisher} is bound to the current thread
 * (i.e. outside of an active agent call, e.g. search-model usage), the
 * decorator simply delegates and publishes nothing, so it is a no-op in
 * those contexts.
 */
public class ToolCallTextPublishingChatModel implements ChatModel {

    private final ChatModel delegate;

    public ToolCallTextPublishingChatModel(ChatModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        StatusEventPublisher publisher = StatusEventContext.get();
        if (publisher != null) {
            // A new LLM round-trip is about to start (request being sent). Signal
            // the TUI to show the "thinking..." placeholder for this round-trip.
            publisher.thinkingStarted();
        }
        long start = System.currentTimeMillis();
        ChatResponse response = delegate.doChat(chatRequest);
        long durationMs = System.currentTimeMillis() - start;
        publishToolCallText(response, durationMs);
        return response;
    }

    private void publishToolCallText(ChatResponse response, long durationMs) {
        if (response == null || response.aiMessage() == null) {
            return;
        }
        AiMessage aiMessage = response.aiMessage();
        StatusEventPublisher publisher = StatusEventContext.get();
        if (publisher == null) {
            return;
        }
        // Use the same stepId convention as BaseTool (the current thread name)
        // so this text is correlated with the tool start/result events of the
        // same step.
        String stepId = Thread.currentThread().getName();
        // Report the real thinking time (the LLM round-trip between this request
        // being sent and the response being received) for every call, whether it
        // returns tool calls or the final answer.
        publisher.thinkingDuration(durationMs);
        if (aiMessage.hasToolExecutionRequests()) {
            // Assistant message that requests tool calls: its text is the
            // descriptive sentence shown inline, right before the tools run.
            String text = aiMessage.text();
            if (text != null && !text.isBlank()) {
                publisher.toolCallText(stepId, text, false);
            } else {
                // The model requested tool calls without any accompanying text.
                // Print a fallback line so the empty assistant message is visible.
                publisher.toolCallText(stepId,
                    I18n.tr(MessageKey.STATUS_TOOL_TEXT_EMPTY), false);
            }
        } else {
            // Final assistant message (no tool calls): surface the reasoning /
            // "extra text apart from the response" after the per-turn "done in
            // Xs" line.
            String thinking = aiMessage.thinking();
            if (thinking != null && !thinking.isBlank()) {
                publisher.toolCallText(stepId, thinking, true);
            }
        }
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return delegate.listeners();
    }
}
