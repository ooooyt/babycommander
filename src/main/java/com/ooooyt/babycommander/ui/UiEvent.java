package com.ooooyt.babycommander.ui;

import com.ooooyt.babycommander.hook.ToolCallInfo;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public sealed interface UiEvent {

    record MessageOutput(String content, MessageType type, long durationMs) implements UiEvent {
        public MessageOutput(String content, MessageType type) {
            this(content, type, 0);
        }
    }

    record StreamFragment(String token) implements UiEvent {}

    record StatusMessage(String agent, String status, double progress, String message) implements UiEvent {}

    record ConfirmationRequest(UUID id, ToolCallInfo info) implements UiEvent {}

    record ErrorEvent(String message, String cause) implements UiEvent {}

    record SessionEvent(SessionState state) implements UiEvent {}

    record PlanUpdate(List<Phase> phases, String taskName) implements UiEvent {}

    record LocaleChange(Locale newLocale) implements UiEvent {}

    record ClarificationRequest(
        UUID id,
        String question,
        boolean allowFreeAnswer,
        List<String> options,
        CompletableFuture<String> future
    ) implements UiEvent {}

    record Phase(String title, String description, String status) {
        /**
         * Legacy constructor for single-string phases (title defaults to the description).
         */
        Phase(String description, String status) {
            this(description, description, status);
        }
    }

    enum MessageType { MARKDOWN, PLAIN, CODE }
    enum SessionState { STARTED, BUSY, STOPPED, ERROR, READY_FOR_INPUT }
}
