package com.ooooyt.babycommander.ui.tui;

import com.ooooyt.babycommander.hook.ConfirmationResult;
import com.ooooyt.babycommander.hook.DangerLevel;
import com.ooooyt.babycommander.ui.ChatEngine;
import com.ooooyt.babycommander.ui.CommandEvent;
import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;
import io.vertx.mutiny.core.eventbus.EventBus;

import java.util.List;
import java.util.UUID;

/**
 * Handles a line of user input entered at the TUI prompt.
 *
 * <p>Dispatches to: a pending confirmation (y/n/a), a pending clarification
 * (numeric choice / free text), built-in slash commands
 * ({@code /exit}, {@code /help}, {@code /history}, {@code /cnc}) or, by
 * default, publishing the text as a {@link CommandEvent.UserInput} on the
 * event bus.
 *
 * <p>Extracted verbatim from {@code TerminalUIAdapter.processInput}. The
 * {@code /exit} path previously called {@code stop()} directly; it now
 * invokes the supplied {@code onStop} callback (bound to
 * {@code TerminalUIAdapter.stop()}) so this class has no back-reference to the
 * adapter. The event bus is mutable because the adapter is often constructed
 * with {@code null} and wired later via {@code wire(...)}.
 */
final class InputProcessor {

    private final TuiModel model;
    private volatile EventBus eventBus;
    private final Runnable onStop;

    InputProcessor(TuiModel model, EventBus eventBus, Runnable onStop) {
        this.model = model;
        this.eventBus = eventBus;
        this.onStop = onStop;
    }

    void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    void processInput(String text) {
        // Reject input while the agent is busy (thinking / calling tools /
        // generating). This is the defensive backstop behind the read-loop
        // gate in TerminalSession, so a command such as "/exit" that arrives
        // mid-task via any other path cannot interrupt the in-flight run.
        // Input is still accepted while a confirmation or clarification is
        // pending because the agent is blocked waiting for the user's answer.
        if (model.agentBusy
                && model.pendingConfirmation == null
                && model.pendingClarification == null) {
            synchronized (model.chatMessages) {
                model.chatMessages.add(new ChatMessage(
                    I18n.tr(MessageKey.UI_AGENT_BUSY), I18n.tr(MessageKey.UI_AGENT_BUSY)));
                model.chatVersion++;
            }
            model.autoScroll = true;
            model.dirty = true;
            return;
        }

        if (!model.chatEngineReady) {
            synchronized (model.chatMessages) {
                model.chatMessages.add(new ChatMessage(I18n.tr(MessageKey.UI_INITIALIZING),
                    I18n.tr(MessageKey.UI_INITIALIZING)));
            }
            model.dirty = true;
            return;
        }

        var future = model.pendingConfirmation;
        if (future != null) {
            String trimmed = text.toLowerCase();
            var info = model.pendingInfo;
            if ("y".equals(trimmed) || "yes".equals(trimmed)) {
                future.complete(ConfirmationResult.ALLOW);
                synchronized (model.chatMessages) {
                    model.chatMessages.add(new ChatMessage("", ""));
                    model.chatMessages.add(new ChatMessage(">> " + text, ">> " + text, TerminalTheme.ST_USER_INPUT));
                    model.chatMessages.add(new ChatMessage("", ""));
                }
                return;
            }
            if ("n".equals(trimmed) || "no".equals(trimmed)) {
                future.complete(ConfirmationResult.DENY);
                synchronized (model.chatMessages) {
                    model.chatMessages.add(new ChatMessage("", ""));
                    model.chatMessages.add(new ChatMessage(">> " + text, ">> " + text, TerminalTheme.ST_USER_INPUT));
                    model.chatMessages.add(new ChatMessage("", ""));
                }
                return;
            }
            if (info != null && info.level() == DangerLevel.ASK_ONCE
                && ("a".equals(trimmed) || "always".equals(trimmed))) {
                future.complete(ConfirmationResult.ALLOW_ALWAYS);
                synchronized (model.chatMessages) {
                    model.chatMessages.add(new ChatMessage("", ""));
                    model.chatMessages.add(new ChatMessage(">> " + text, ">> " + text, TerminalTheme.ST_USER_INPUT));
                    model.chatMessages.add(new ChatMessage("", ""));
                }
                return;
            }
            synchronized (model.chatMessages) {
                model.chatMessages.add(new ChatMessage("", ""));
                model.chatMessages.add(new ChatMessage(
                    ">> " + text + "  (y/n" + (info != null && info.level() == DangerLevel.ASK_ONCE ? "/a" : "") + ")",
                    ">> " + text + "  (y/n" + (info != null && info.level() == DangerLevel.ASK_ONCE ? "/a" : "") + ")",
                    TerminalTheme.ST_USER_INPUT));
                model.chatMessages.add(new ChatMessage("", ""));
            }
            return;
        }

        var cf = model.pendingClarification;
        if (cf != null) {
            String answer;
            UUID id = model.pendingClarificationId;
            String question = model.pendingClarificationQuestion;
            List<String> opts = model.pendingClarificationOptions;
            if (opts != null && !opts.isEmpty()) {
                String trimmed = text.trim();
                try {
                    int idx = Integer.parseInt(trimmed) - 1;
                    if (idx >= 0 && idx < opts.size()) {
                        answer = opts.get(idx);
                    } else {
                        synchronized (model.chatMessages) {
                            model.chatMessages.add(new ChatMessage(
                                I18n.tr(MessageKey.UI_INVALID_CHOICE, idx + 1, opts.size()),
                                I18n.tr(MessageKey.UI_INVALID_CHOICE, idx + 1, opts.size())));
                        }
                        model.dirty = true;
                        return;
                    }
                } catch (NumberFormatException e) {
                    if (!model.pendingClarificationAllowFree) {
                        synchronized (model.chatMessages) {
                            model.chatMessages.add(new ChatMessage(
                                I18n.tr(MessageKey.UI_PLEASE_ENTER_NUMBER),
                                I18n.tr(MessageKey.UI_PLEASE_ENTER_NUMBER)));
                        }
                        model.dirty = true;
                        return;
                    }
                    answer = text;
                }
            } else {
                answer = text;
            }
            synchronized (model.chatMessages) {
                model.chatMessages.add(new ChatMessage("", ""));
                model.chatMessages.add(new ChatMessage(">> " + answer, ">> " + answer, TerminalTheme.ST_USER_INPUT));
                model.chatMessages.add(new ChatMessage("", ""));
            }
            model.pendingClarification = null;
            model.pendingClarificationId = null;
            model.pendingClarificationQuestion = null;
            model.pendingClarificationOptions = null;
            model.pendingClarificationAllowFree = true;
            eventBus.publish(ChatEngine.UI_COMMAND_ADDRESS,
                new CommandEvent.ClarificationResponse(id, question, answer));
            cf.complete(answer);
            model.autoScroll = true;
            model.dirty = true;
            return;
        }

        if ("/exit".equalsIgnoreCase(text) || "/quit".equalsIgnoreCase(text)) {
            synchronized (model.chatMessages) {
                model.chatMessages.add(new ChatMessage("", ""));
                model.chatMessages.add(new ChatMessage(">> " + text, ">> " + text, TerminalTheme.ST_USER_INPUT));
                model.chatMessages.add(new ChatMessage("", ""));
                model.chatMessages.add(new ChatMessage(I18n.tr(MessageKey.UI_GOODBYE), I18n.tr(MessageKey.UI_GOODBYE)));
            }
            if (eventBus != null) {
                eventBus.publish(ChatEngine.UI_COMMAND_ADDRESS,
                    new CommandEvent.SessionCommand(CommandEvent.SessionAction.STOP));
            }
            onStop.run();
            return;
        }

        if ("/help".equalsIgnoreCase(text)) {
            synchronized (model.chatMessages) {
                model.chatMessages.add(new ChatMessage("", ""));
                model.chatMessages.add(new ChatMessage(">> " + text, ">> " + text, TerminalTheme.ST_USER_INPUT));
                model.chatMessages.add(new ChatMessage("", ""));
                model.chatMessages.add(new ChatMessage("", ""));
                model.chatMessages.add(new ChatMessage(I18n.tr(MessageKey.CHAT_HELP_LINES),
                    I18n.tr(MessageKey.CHAT_HELP_LINES)));
                for (SlashCommandRegistry.SlashCommand cmd : SlashCommandRegistry.commands()) {
                    model.chatMessages.add(new ChatMessage(cmd.description(), cmd.description()));
                }
                model.chatMessages.add(new ChatMessage("", ""));
            }
            model.autoScroll = true;
            model.dirty = true;
            return;
        }

        if ("/history".equalsIgnoreCase(text) || text.toLowerCase().startsWith("/history ")) {
            synchronized (model.chatMessages) {
                model.chatMessages.add(new ChatMessage("", ""));
                model.chatMessages.add(new ChatMessage(">> " + text, ">> " + text, TerminalTheme.ST_USER_INPUT));
                model.chatMessages.add(new ChatMessage("", ""));
            }
            model.autoScroll = true;
            model.dirty = true;
            eventBus.publish(ChatEngine.UI_COMMAND_ADDRESS, new CommandEvent.UserInput(text));
            return;
        }

        if ("/cnc".equalsIgnoreCase(text)) {
            String expanded = "commit necessary changes";
            synchronized (model.chatMessages) {
                model.chatMessages.add(new ChatMessage("", ""));
                model.chatMessages.add(new ChatMessage(">> " + expanded, ">> " + expanded, TerminalTheme.ST_USER_INPUT));
                model.chatMessages.add(new ChatMessage("", ""));
            }
            model.autoScroll = true;
            model.dirty = true;
            eventBus.publish(ChatEngine.UI_COMMAND_ADDRESS, new CommandEvent.UserInput(expanded));
            return;
        }

        synchronized (model.chatMessages) {
            model.chatMessages.add(new ChatMessage("", ""));
            model.chatMessages.add(new ChatMessage(">> " + text, ">> " + text, TerminalTheme.ST_USER_INPUT));
            model.chatMessages.add(new ChatMessage("", ""));
        }
        model.autoScroll = true;
        model.dirty = true;
        eventBus.publish(ChatEngine.UI_COMMAND_ADDRESS, new CommandEvent.UserInput(text));
}
}
