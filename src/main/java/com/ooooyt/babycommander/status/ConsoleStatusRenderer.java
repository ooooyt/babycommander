package com.ooooyt.babycommander.status;

import com.ooooyt.babycommander.ui.tui.TerminalUIAdapter;
import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@Startup
@ApplicationScoped
public class ConsoleStatusRenderer {

    private final StepInfo currentStep = new StepInfo();

    @Inject
    private StatusEventPublisher statusPublisher;

    @PostConstruct
    void init() {
        statusPublisher.registerSyncConsumer(this::onEvent);
        Log.info("ConsoleStatusRenderer registered as sync consumer");
    }

    private void onEvent(JsonObject event) {
        if (TerminalUIAdapter.tuiActive) return;
        try {
            String type = event.getString("eventType");
            StatusEventType eventType = StatusEventType.valueOf(type);
            switch (eventType) {
                case WORKFLOW_STARTED -> renderWorkflowStarted(event);
                case WORKFLOW_TYPE_CHANGED -> renderWorkflowTypeChanged(event);
                case STEP_STARTED -> renderStepStarted(event);
                case STEP_COMPLETED -> renderStepCompleted(event);
                case STEP_FAILED -> renderStepFailed(event);
                case TOOL_CALL_START -> renderToolCallStart(event);
                case TOOL_CALL_TEXT -> renderToolCallText(event);
                case TOOL_CALL_RESULT -> renderToolCallResult(event);
                case TOOL_CALL_ERROR -> renderToolCallError(event);
                case AGENT_RESPONSE -> renderAgentResponse(event);
                case WORKFLOW_COMPLETED -> renderWorkflowCompleted(event);
            }
        } catch (Exception e) {
            Log.warnf("ConsoleStatusRenderer error: %s", e.getMessage());
        }
    }

    private void out(String line) {
        System.out.println(line);
    }

    private void renderWorkflowStarted(JsonObject event) {
        out("\n" + Ansi.CYAN_BOLD + I18n.tr(MessageKey.STATUS_WORKFLOW_STARTED) + Ansi.RESET);
        String desc = truncate(event.getString("taskDescription", ""), 100);
        out(I18n.tr(MessageKey.STATUS_WORKFLOW_TASK) + ": " + Ansi.BOLD + desc + Ansi.RESET);
        out(I18n.tr(MessageKey.STATUS_WORKFLOW_TYPE) + ": " + event.getString("workflowType", "auto"));
        out(Ansi.DIM + "─".repeat(50) + Ansi.RESET);
        out("");
    }

    private void renderWorkflowTypeChanged(JsonObject event) {
        out(Ansi.DIM + "─".repeat(50) + Ansi.RESET);
        out(I18n.tr(MessageKey.STATUS_MODE) + ": " + Ansi.BOLD + event.getString("workflowType", "unknown") + Ansi.RESET);
        out("");
    }

    private void renderStepStarted(JsonObject event) {
        String stepId = event.getString("stepId", "unknown");
        String role = event.getString("agentRole", "unknown");
        currentStep.agentRole = role;
        currentStep.instruction = event.getString("instruction", "");
        currentStep.startTime = System.currentTimeMillis();
        currentStep.toolCallCount = 0;

        String label = role.toUpperCase();
        out(Ansi.BLUE_BOLD + I18n.tr(MessageKey.STATUS_STEP_STARTED, label) + " " + Ansi.RESET + Ansi.DIM + " [" + stepId + "]" + Ansi.RESET);
    }

    private void renderToolCallStart(JsonObject event) {
        String tool = event.getString("toolName", "unknown");
        String input = truncate(event.getString("toolInput", ""), 150);
        currentStep.toolCallCount++;
        out("  " + Ansi.YELLOW + I18n.tr(MessageKey.STATUS_TOOL_CALL, tool, input) + Ansi.RESET);
    }

    private void renderToolCallText(JsonObject event) {
        String text = truncate(event.getString("text", ""), 200);
        if (text.isEmpty()) return;
        out("  " + I18n.tr(MessageKey.STATUS_TOOL_TEXT, text));
    }

    private void renderToolCallResult(JsonObject event) {
        String tool = event.getString("toolName", "unknown");
        String input = truncate(event.getString("toolInput", ""), 150);
        long duration = event.getLong("durationMs", 0L);
        out("  " + Ansi.GREEN + I18n.tr(MessageKey.STATUS_TOOL_RESULT, tool, input) + Ansi.RESET + Ansi.DIM + " (" + duration + "ms)" + Ansi.RESET);
    }

    private void renderToolCallError(JsonObject event) {
        String tool = event.getString("toolName", "unknown");
        String input = truncate(event.getString("toolInput", ""), 150);
        String error = truncate(event.getString("errorMessage", ""), 100);
        out("  " + Ansi.RED + I18n.tr(MessageKey.STATUS_TOOL_ERROR, tool, input) + Ansi.RESET + Ansi.DIM + " — " + error + Ansi.RESET);
    }

    private void renderAgentResponse(JsonObject event) {
        int length = event.getInteger("responseLength", 0);
        String label = length > 1000 ? I18n.tr(MessageKey.STATUS_KB_FORMAT, length / 1024) : I18n.tr(MessageKey.STATUS_B_FORMAT, length);
        out("  " + Ansi.DIM + I18n.tr(MessageKey.STATUS_TOOL_RESPONSE, label) + Ansi.RESET);
    }

    private void renderStepCompleted(JsonObject event) {
        String role = event.getString("agentRole", "unknown").toUpperCase();
        long duration = event.getLong("durationMs", 0L);
        out(Ansi.GREEN_BOLD + I18n.tr(MessageKey.STATUS_STEP_COMPLETE, role) + Ansi.RESET + Ansi.DIM + " (" + formatDuration(duration) + ") — " + currentStep.toolCallCount + I18n.tr(MessageKey.STATUS_TOOL_CALLS) + Ansi.RESET);
        out("");
    }

    private void renderStepFailed(JsonObject event) {
        String role = event.getString("agentRole", "unknown").toUpperCase();
        String error = truncate(event.getString("errorMessage", ""), 125);
        out(Ansi.RED_BOLD + I18n.tr(MessageKey.STATUS_STEP_FAILED, role) + Ansi.RESET + Ansi.DIM + " — " + error + Ansi.RESET);
        out("");
    }

    private void renderWorkflowCompleted(JsonObject event) {
        String status = event.getString("status", "UNKNOWN");
        long total = event.getLong("totalDurationMs", 0L);
        out(Ansi.DIM + "─".repeat(50) + Ansi.RESET);
        if ("SUCCESS".equals(status)) {
            out(Ansi.GREEN_BOLD + I18n.tr(MessageKey.STATUS_WORKFLOW_COMPLETED, status, formatDuration(total)) + Ansi.RESET);
        } else {
            out(Ansi.RED_BOLD + I18n.tr(MessageKey.STATUS_WORKFLOW_COMPLETED, status, formatDuration(total)) + Ansi.RESET);
        }
        out("");
    }

    private static String formatDuration(long ms) {
        if (ms < 1000) return I18n.tr(MessageKey.STATUS_MS_FORMAT, ms);
        return I18n.tr(MessageKey.STATUS_S_FORMAT, String.format("%.1f", ms / 1000.0));
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static class StepInfo {
        String agentRole;
        String instruction;
        int toolCallCount = 0;
        long startTime = System.currentTimeMillis();
    }

    private static class Ansi {
        static final String RESET = "\u001B[0m";
        static final String BOLD = "\u001B[1m";
        static final String DIM = "\u001B[2m";
        static final String RED = "\u001B[31m";
        static final String GREEN = "\u001B[32m";
        static final String YELLOW = "\u001B[33m";
        static final String BLUE = "\u001B[34m";
        static final String GREEN_BOLD = "\u001B[1;32m";
        static final String RED_BOLD = "\u001B[1;31m";
        static final String BLUE_BOLD = "\u001B[1;34m";
        static final String CYAN_BOLD = "\u001B[1;36m";
    }
}
