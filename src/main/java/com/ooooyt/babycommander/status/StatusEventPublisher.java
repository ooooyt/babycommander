package com.ooooyt.babycommander.status;

import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@ApplicationScoped
public class StatusEventPublisher {

    public static final String ADDRESS = "babycommander.status";

    private final EventBus eventBus;
    private final List<Consumer<JsonObject>> syncConsumers = new CopyOnWriteArrayList<>();

    @Inject
    public StatusEventPublisher(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void registerSyncConsumer(Consumer<JsonObject> consumer) {
        syncConsumers.add(consumer);
    }

    public void workflowStarted(String workflowType, String taskDescription) {
        publish(StatusEventType.WORKFLOW_STARTED, b -> b
            .put("workflowType", workflowType)
            .put("taskDescription", taskDescription));
    }

    public void workflowTypeChanged(String newType) {
        publish(StatusEventType.WORKFLOW_TYPE_CHANGED, b -> b
            .put("workflowType", newType));
    }

    public void workflowCompleted(String status, long totalDurationMs) {
        publish(StatusEventType.WORKFLOW_COMPLETED, b -> b
            .put("status", status)
            .put("totalDurationMs", totalDurationMs));
    }

    public void stepStarted(String stepId, String agentRole, String instruction) {
        publish(StatusEventType.STEP_STARTED, b -> b
            .put("stepId", stepId)
            .put("agentRole", agentRole)
            .put("instruction", truncate(instruction, 200)));
    }

    public void stepCompleted(String stepId, String agentRole, long durationMs) {
        publish(StatusEventType.STEP_COMPLETED, b -> b
            .put("stepId", stepId)
            .put("agentRole", agentRole)
            .put("durationMs", durationMs));
    }

    public void stepFailed(String stepId, String agentRole, String errorMessage) {
        publish(StatusEventType.STEP_FAILED, b -> b
            .put("stepId", stepId)
            .put("agentRole", agentRole)
            .put("errorMessage", truncate(errorMessage, 500)));
    }

    public void toolCallStart(String stepId, String toolName, String toolInput) {
        publish(StatusEventType.TOOL_CALL_START, b -> b
            .put("stepId", stepId)
            .put("toolName", toolName)
            .put("toolInput", truncate(toolInput, 200)));
    }

    public void toolCallText(String stepId, String text, boolean afterThinking) {
        publish(StatusEventType.TOOL_CALL_TEXT, b -> b
            .put("stepId", stepId)
            .put("text", truncate(text, 500))
            .put("afterThinking", afterThinking));
    }

    public void thinkingStarted() {
        publish(StatusEventType.THINKING_STARTED, b -> b);
    }

    public void thinkingDuration(long thinkingMs) {
        publish(StatusEventType.THINKING_DURATION, b -> b
            .put("thinkingMs", thinkingMs));
    }

    /**
     * Signals that an LLM round-trip failed (e.g. read timeout after internal
     * retries were exhausted), so consumers can clear the "thinking..."
     * placeholder instead of leaving it stuck forever.
     */
    public void thinkingFailed(long elapsedMs) {
        publish(StatusEventType.THINKING_FAILED, b -> b
            .put("elapsedMs", elapsedMs));
    }

    public void toolCallResult(String stepId, String toolName, String toolInput, String toolOutput, long durationMs) {
        publish(StatusEventType.TOOL_CALL_RESULT, b -> b
            .put("stepId", stepId)
            .put("toolName", toolName)
            .put("toolInput", truncate(toolInput, 200))
            .put("toolOutput", truncate(toolOutput, 200))
            .put("durationMs", durationMs));
    }

    public void toolCallError(String stepId, String toolName, String toolInput, String errorMessage) {
        publish(StatusEventType.TOOL_CALL_ERROR, b -> b
            .put("stepId", stepId)
            .put("toolName", toolName)
            .put("toolInput", truncate(toolInput, 200))
            .put("errorMessage", truncate(errorMessage, 500)));
    }

    public void agentResponse(String stepId, String agentRole, int responseLength) {
        publish(StatusEventType.AGENT_RESPONSE, b -> b
            .put("stepId", stepId)
            .put("agentRole", agentRole)
            .put("responseLength", responseLength));
    }

    private void publish(StatusEventType type, java.util.function.Function<JsonObject, JsonObject> builder) {
        try {
            JsonObject payload = builder.apply(new JsonObject());
            payload.put("eventType", type.name())
                   .put("timestamp", Instant.now().toString());
            for (Consumer<JsonObject> consumer : syncConsumers) {
                consumer.accept(payload);
            }
            eventBus.publish(ADDRESS, payload);
        } catch (Exception e) {
            Log.warnf("Failed to publish status event: %s", e.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + I18n.tr(MessageKey.STATUS_TRUNCATED, s.length());
    }
}
