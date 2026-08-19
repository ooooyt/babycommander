package com.ooooyt.babycommander.ui;

import com.ooooyt.babycommander.hook.ConfirmationResult;

import java.util.UUID;

public sealed interface CommandEvent {

    record UserInput(String text) implements CommandEvent {}

    record ConfirmationResponse(UUID requestId, ConfirmationResult result) implements CommandEvent {}

    record Cancel() implements CommandEvent {}

    record SessionCommand(SessionAction action) implements CommandEvent {}

    record ClarificationResponse(
        UUID id,
        String question,
        String answer
    ) implements CommandEvent {}

    enum SessionAction { START, STOP }
}
