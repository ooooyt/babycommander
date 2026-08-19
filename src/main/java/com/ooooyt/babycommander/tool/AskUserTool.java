package com.ooooyt.babycommander.tool;

import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;

import com.ooooyt.babycommander.ui.ChatEngine;
import com.ooooyt.babycommander.ui.UiEvent;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.vertx.mutiny.core.eventbus.EventBus;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AskUserTool {

    private final EventBus eventBus;

    public AskUserTool(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Tool("Ask the user a clarifying question when their request is ambiguous or you need input before proceeding. "
         + "Provide clear options when possible. The user will respond via the chat input box.")
    public String ask(
            @P("The question to present to the user") String question,
            @P("Whether the user may type a free-form answer instead of picking an option") boolean allowFreeAnswer,
            @P("Multiple-choice options the user can pick from") String... options) {
        if (eventBus == null) {
            return I18n.tr(MessageKey.ASK_NO_UI);
        }
        UUID id = UUID.randomUUID();
        CompletableFuture<String> future = new CompletableFuture<>();
        eventBus.publish(ChatEngine.UI_EVENT_ADDRESS,
            new UiEvent.ClarificationRequest(id, question, allowFreeAnswer,
                options != null ? List.of(options) : List.of(), future));
        try {
            long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5);
            while (System.currentTimeMillis() < deadline) {
                try {
                    return future.get(100, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    if (Thread.currentThread().isInterrupted()) {
                        return "";
                    }
                }
            }
            return I18n.tr(MessageKey.ASK_TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } catch (ExecutionException e) {
            return "";
        }
    }
}
