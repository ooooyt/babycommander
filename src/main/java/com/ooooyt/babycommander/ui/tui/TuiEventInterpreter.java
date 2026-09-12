package com.ooooyt.babycommander.ui.tui;

import com.ooooyt.babycommander.status.StatusEventType;
import com.ooooyt.babycommander.tool.PlanTool;
import com.ooooyt.babycommander.ui.UiEvent;
import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;
import io.vertx.core.json.JsonObject;
import org.jline.utils.AttributedStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * Translates inbound {@link UiEvent}s and status {@link JsonObject} events
 * into mutations of the shared {@link TuiModel} (chat messages, plan/info
 * lines, pending clarification state, dirty flags).
 *
 * <p>Extracted verbatim from {@code TerminalUIAdapter}; the two previously
 * duplicated plan-rendering blocks (the {@code PlanUpdate} case and
 * {@code refreshPlanFromTool}) now share a single {@link #buildPlanInfo}
 * helper, eliminating the copy-paste while preserving identical output.
 */
final class TuiEventInterpreter {

    private final TuiModel model;
    private final boolean showToolCallPairs;
    private final List<String> pendingAfterThinkingTexts = new ArrayList<>();

    /**
     * The most recently rendered plan snapshot. Used by
     * {@link #refreshPlanFromTool()} to decide whether the plan actually
     * changed since the last render, so the idle loop does not force a
     * redundant redraw (and cursor hide/show) every 100 ms once a plan
     * exists &mdash; which would otherwise break the terminal's native
     * cursor blinking.
     */
    private List<UiEvent.Phase> lastRenderedPlan = List.of();

    TuiEventInterpreter(TuiModel model) {
        this(model, false);
    }

    TuiEventInterpreter(TuiModel model, boolean showToolCallPairs) {
        this.model = model;
        this.showToolCallPairs = showToolCallPairs;
    }

    void handleUiEvent(UiEvent event) {
        switch (event) {
            case UiEvent.MessageOutput msg -> {
                String rendered = MarkdownRenderer.stripEmoji(msg.content());
                synchronized (model.chatMessages) {
                    if (msg.durationMs() > 0) {
                        // The final thought/response has arrived: stop the in-progress bar
                        model.thinking = false;
                        model.agentBusy = false;
                        model.chatMessages.removeIf(cm ->
                            I18n.tr(MessageKey.STATUS_THINKING).equals(cm.source()));
                    }
                    // Result summary (the actual response text).
                    model.chatMessages.add(new ChatMessage(msg.content(), rendered));
                    // Surface any "extra text apart from the response" (e.g. reasoning
                    // content) right after the result summary.
                    for (String t : pendingAfterThinkingTexts) {
                        model.chatMessages.add(new ChatMessage(t, t));
                    }
                    pendingAfterThinkingTexts.clear();
                    // Total task duration is printed last, as the final line.
                    if (msg.durationMs() > 0) {
                        String durationText = TerminalTheme.formatDuration(msg.durationMs());
                        model.chatMessages.add(new ChatMessage(durationText, durationText, TerminalTheme.ST_TIME));
                    }
                    model.chatVersion++;
                }
                model.autoScroll = true;
                model.dirty = true;
            }
            case UiEvent.StreamFragment frag -> {}
            case UiEvent.StatusMessage sm -> {
                String line = sm.agent() + ": " + sm.message();
                synchronized (model.chatMessages) {
                    model.chatMessages.add(new ChatMessage(line, line));
                    model.chatVersion++;
                }
                model.autoScroll = true;
                model.dirty = true;
            }
            case UiEvent.ErrorEvent err -> {
                synchronized (model.chatMessages) {
                    model.chatMessages.add(new ChatMessage(I18n.tr(MessageKey.UI_ERROR_PREFIX) + err.message(),
                        I18n.tr(MessageKey.UI_ERROR_PREFIX) + err.message()));
                    model.chatVersion++;
                }
                model.autoScroll = true;
                model.dirty = true;
            }
            case UiEvent.SessionEvent se -> {
                switch (se.state()) {
                    case STARTED -> {
                        if (!model.chatEngineReady) {
                            model.chatEngineReady = true;
                            model.dirty = true;
                        }
                    }
                    case BUSY -> {
                        // The agent started processing: lock the input box.
                        model.agentBusy = true;
                        model.dirty = true;
                    }
                    case READY_FOR_INPUT, STOPPED, ERROR -> {
                        // The agent finished (or the session ended): unlock input.
                        model.agentBusy = false;
                        model.dirty = true;
                    }
                }
            }
            case UiEvent.PlanUpdate pu -> {
                List<InfoLine> lines = buildPlanInfo(PlanTool.getLatestTask(), pu.phases());
                synchronized (model.infoLines) {
                    model.infoLines.clear();
                    model.infoLines.addAll(lines);
                }
                // Keep the last-rendered snapshot in sync so the idle poll in
                // refreshPlanFromTool does not treat this same plan as a change
                // and force a redundant redraw on the next idle tick.
                lastRenderedPlan = List.copyOf(pu.phases());
                model.dirty = true;
            }
            case UiEvent.ClarificationRequest r -> {
                model.pendingClarification = r.future();
                model.pendingClarificationId = r.id();
                model.pendingClarificationQuestion = r.question();
                model.pendingClarificationOptions = r.options();
                model.pendingClarificationAllowFree = r.allowFreeAnswer();
                StringBuilder sb = new StringBuilder(r.question());
                List<String> opts = r.options();
                for (int i = 0; i < opts.size(); i++) {
                    sb.append("\n  ").append(i + 1).append(". ").append(opts.get(i));
                }
                synchronized (model.chatMessages) {
                    model.chatMessages.add(new ChatMessage(sb.toString(), sb.toString()));
                    model.chatVersion++;
                }
                model.autoScroll = true;
                model.dirty = true;
            }
            default -> {}
        }
    }

    void handleStatusEvent(JsonObject event) {
        String typeStr = event.getString("eventType");
        StatusEventType type;
        try {
            type = StatusEventType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return;
        }

        boolean isToolEvent = false;
        boolean isToolCallPair = false;

        String line = switch (type) {
            case TOOL_CALL_TEXT -> { isToolEvent = true;
                String text = event.getString("text", "");
                boolean afterThinking = event.getBoolean("afterThinking", false);
                if (afterThinking) {
                    synchronized (model.chatMessages) {
                        pendingAfterThinkingTexts.add(text);
                    }
                    yield "";
                }
                yield text;
            }
            case TOOL_CALL_START -> { isToolEvent = true; isToolCallPair = true;
                String tool = event.getString("toolName", "?");
                String input = event.getString("toolInput", "");
                yield "\u23F3 " + tool + " \u2192 " + input;
            }
            case TOOL_CALL_RESULT -> { isToolEvent = true; isToolCallPair = true;
                String tool = event.getString("toolName", "?");
                String input = event.getString("toolInput", "");
                long ms = event.getLong("durationMs", 0L);
                yield "\u2713 " + tool + " \u2192 " + input + " (" + ms + "ms)";
            }
            case TOOL_CALL_ERROR -> { isToolEvent = true; isToolCallPair = true;
                String tool = event.getString("toolName", "?");
                String input = event.getString("toolInput", "");
                String err = event.getString("errorMessage", "");
                yield "\u2717 " + tool + " \u2192 " + input + " \u2014 " + err;
            }
            case STEP_STARTED -> "\u25B6 " + event.getString("agentRole", "agent");
            case STEP_COMPLETED -> {
                String role = event.getString("agentRole", "AGENT").toUpperCase();
                long ms = event.getLong("durationMs", 0L);
                yield I18n.tr(MessageKey.TUI_STEP_COMPLETE, role, ms);
            }
            case STEP_FAILED -> {
                String role = event.getString("agentRole", "AGENT").toUpperCase();
                String err = event.getString("errorMessage", "");
                yield I18n.tr(MessageKey.TUI_STEP_FAILED, role, err);
            }
            case WORKFLOW_STARTED -> {
                String wfType = event.getString("workflowType", "");
                String wfLabel = wfType.isEmpty() ? I18n.tr(MessageKey.TUI_WORKFLOW_STARTED_LABEL) : wfType;
                yield I18n.tr(MessageKey.TUI_WORKFLOW_STARTED, wfLabel);
            }
            case WORKFLOW_COMPLETED -> {
                String status = event.getString("status", "DONE");
                long ms = event.getLong("totalDurationMs", 0L);
                yield I18n.tr(MessageKey.TUI_WORKFLOW_COMPLETED, status, ms);
            }
            case AGENT_RESPONSE -> {
                int len = event.getInteger("responseLength", 0);
                yield I18n.tr(MessageKey.TUI_AGENT_RESPONSE, len);
            }
            case WORKFLOW_TYPE_CHANGED -> {
                yield I18n.tr(MessageKey.TUI_WORKFLOW_MODE, event.getString("workflowType", "?"));
            }
            case THINKING_STARTED -> { isToolEvent = true;
                // A new LLM round-trip is about to begin: show the
                // "thinking..." placeholder (and start the animated dot bar)
                // so the user sees thinking each time a request is sent.
                synchronized (model.chatMessages) {
                    model.chatMessages.removeIf(cm ->
                        I18n.tr(MessageKey.STATUS_THINKING).equals(cm.source()));
                    model.chatMessages.add(new ChatMessage(
                        I18n.tr(MessageKey.STATUS_THINKING),
                        I18n.tr(MessageKey.STATUS_THINKING), TerminalTheme.ST_TIME));
                    model.chatVersion++;
                }
                boolean wasThinking = model.thinking;
                model.thinking = true;
                model.agentBusy = true;
                // Only reset the animation when starting a fresh thinking
                // session. THINKING_STARTED also fires on every LLM round-trip
                // within a multi-tool workflow; resetting each time would
                // restart the dot bar so later dots never light up.
                if (!wasThinking) model.dotIndex.set(0);
                yield "";
            }
            case THINKING_DURATION -> { isToolEvent = true;
                long ms = event.getLong("thinkingMs", 0L);
                // Render the real thinking time (LLM round-trip between request
                // sent and response received) inline, per round-trip.
                if (ms > 0) {
                    // A thought has now completed and is reflected in the
                    // "thought in Xs" line, so drop the stale "thinking..."
                    // placeholder instead of leaving it stuck above the thoughts.
                    synchronized (model.chatMessages) {
                        model.chatMessages.removeIf(cm ->
                            I18n.tr(MessageKey.STATUS_THINKING).equals(cm.source()));
                    }
                    yield TerminalTheme.formatThinking(ms);
                }
                yield "";
            }
            case THINKING_FAILED -> { isToolEvent = true;
                // The LLM round-trip failed (e.g. timeout): stop the thinking
                // animation and replace the placeholder with an error line so
                // the UI does not appear hung while retries are exhausted.
                synchronized (model.chatMessages) {
                    model.chatMessages.removeIf(cm ->
                        I18n.tr(MessageKey.STATUS_THINKING).equals(cm.source()));
                    String failLine = I18n.tr(MessageKey.STATUS_THINKING_FAILED,
                        event.getLong("elapsedMs", 0L));
                    model.chatMessages.add(new ChatMessage(failLine, failLine,
                        TerminalTheme.ST_TOOL_ERROR));
                    model.chatVersion++;
                }
                model.thinking = false;
                model.agentBusy = false;
                model.autoScroll = true;
                model.dirty = true;
                yield "";
            }
        };

        AttributedStyle statusStyle = switch (type) {
            case TOOL_CALL_TEXT -> null;
            case TOOL_CALL_START -> TerminalTheme.ST_TOOL;
            case TOOL_CALL_RESULT -> TerminalTheme.ST_TOOL_SUCCESS;
            case TOOL_CALL_ERROR -> TerminalTheme.ST_TOOL_ERROR;
            case THINKING_DURATION -> TerminalTheme.ST_TIME;
            case THINKING_STARTED -> TerminalTheme.ST_TIME;
            case THINKING_FAILED -> TerminalTheme.ST_TOOL_ERROR;
            default -> null;
        };

        if (isToolEvent && !line.isEmpty() && (!isToolCallPair || showToolCallPairs)) {
            synchronized (model.chatMessages) {
                model.chatMessages.add(new ChatMessage(line, line, statusStyle));
                model.chatVersion++;
            }
        }
        model.autoScroll = true;
        model.dirty = true;
    }

    /**
     * Rebuilds the left-pane plan/info lines from the latest {@link PlanTool}
     * snapshot. Returns {@code false} (no redraw needed) when there is no
     * plan snapshot, or when the plan has not changed since the last render,
     * so the idle loop does not force a redundant redraw (and cursor
     * hide/show) every 100 ms once a plan exists &mdash; which would
     * otherwise break the terminal's native cursor blinking.
     */
    boolean refreshPlanFromTool() {
        List<UiEvent.Phase> snapshot = PlanTool.getLatestSnapshot();
        if (snapshot.isEmpty()) {
            return false;
        }
        // Plan unchanged since the last render (either via refreshPlanFromTool
        // or a PlanUpdate event): nothing to repaint, so keep the cursor steady.
        if (lastRenderedPlan.equals(snapshot)) {
            return false;
        }
        lastRenderedPlan = List.copyOf(snapshot);
        List<InfoLine> lines = buildPlanInfo(PlanTool.getLatestTask(), snapshot);
        synchronized (model.infoLines) {
            model.infoLines.clear();
            model.infoLines.addAll(lines);
        }
        return true;
    }

    /**
     * Shared builder for the left-pane plan block. Previously this logic was
     * duplicated verbatim in the {@code PlanUpdate} case and in
     * {@code refreshPlanFromTool}.
     */
    private List<InfoLine> buildPlanInfo(String task, List<UiEvent.Phase> phases) {
        List<InfoLine> lines = new ArrayList<>();
        if (!task.isEmpty()) {
            lines.add(new InfoLine(I18n.tr(MessageKey.UI_LABEL_TASK), TerminalTheme.ST_PLAN_HEADER));
            int wrapW = Math.max(10, model.leftW() - 6);
            for (String wrapped : DisplayTextUtils.wrapText(task, wrapW)) {
                lines.add(new InfoLine("  " + wrapped));
            }
        }
        lines.add(new InfoLine(I18n.tr(MessageKey.UI_LABEL_PLAN), TerminalTheme.ST_PLAN_HEADER));
        for (var p : phases) {
            String status = p.status().toLowerCase();
            AttributedStyle phaseStyle = switch (status) {
                case "completed" -> TerminalTheme.ST_PLAN_COMPLETED;
                case "active" -> TerminalTheme.ST_PLAN_ACTIVE;
                case "failed" -> TerminalTheme.ST_PLAN_FAILED;
                default -> TerminalTheme.ST_PLAN_PENDING;
            };
            String icon = switch (status) {
                case "completed" -> "\u25CF";
                case "active" -> "\u25C9";
                case "failed" -> "\u2717";
                default -> "\u25CB";
            };
            int wrapW = Math.max(10, model.leftW() - 6);
            for (String wrapped : DisplayTextUtils.wrapText(p.title(), wrapW)) {
                lines.add(new InfoLine("  " + icon + " " + wrapped, phaseStyle));
                icon = " ";
            }
        }
        return lines;
    }
}
