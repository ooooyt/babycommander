package com.ooooyt.babycommander.status;

import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class RestStatusTracker {

    private final EventBus eventBus;
    private final Object stateLock = new Object();

    private volatile CurrentExecutionState state = new CurrentExecutionState();
    private volatile boolean initialized = false;

    @Inject
    public RestStatusTracker(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Lazily register the EventBus consumer on first status access.
     * This avoids blocking Quarkus startup with EventBus subscription setup.
     */
    @PostConstruct
    void ensureInitialized() {
        // Deferred — registration happens on first getState() call
    }

    private void lazyInit() {
        if (initialized) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            initialized = true;
            if (eventBus == null) {
                Log.warn("RestStatusTracker: eventBus is null, skipping EventBus subscription (likely unit test)");
                return;
            }
            eventBus.consumer(StatusEventPublisher.ADDRESS).handler(message -> {
                try {
                    updateState((JsonObject) message.body());
                } catch (Exception e) {
                    Log.warnf("RestStatusTracker error: %s", e.getMessage());
                }
            });
            Log.info("RestStatusTracker registered on EventBus");
        }
    }

    private void updateState(JsonObject event) {
        synchronized (stateLock) {
            String type = event.getString("eventType");
            if (type == null) {
                return;
            }
            switch (StatusEventType.valueOf(type)) {
                case WORKFLOW_STARTED -> {
                    state = new CurrentExecutionState();
                    state.active = true;
                    state.workflowType = event.getString("workflowType", "unknown");
                    state.startTime = Instant.now().toString();
                    state.recentEvents.add(new RecentEvent(type));
                }
                case STEP_STARTED -> {
                    state.currentStep = event.getString("stepId", "unknown");
                    state.currentStepRole = event.getString("agentRole", "unknown");
                    state.currentStepStatus = I18n.tr(MessageKey.STATUS_RUNNING);
                    state.recentEvents.add(new RecentEvent(type));
                }
                case STEP_COMPLETED -> {
                    if (state.currentStep != null && !state.completedSteps.contains(state.currentStep)) {
                        state.completedSteps.add(state.currentStep);
                    }
                    state.currentStep = null;
                    state.currentStepRole = null;
                    state.currentStepStatus = I18n.tr(MessageKey.STATUS_COMPLETED);
                    state.recentEvents.add(new RecentEvent(type));
                }
                case STEP_FAILED -> {
                    state.currentStepStatus = I18n.tr(MessageKey.STATUS_FAILED);
                    state.status = I18n.tr(MessageKey.STATUS_FAILURE);
                    state.recentEvents.add(new RecentEvent(type));
                }
                case TOOL_CALL_START -> {
                    state.currentToolName = event.getString("toolName", "unknown");
                    state.currentToolInput = event.getString("toolInput", "");
                    state.recentEvents.add(new RecentEvent(type));
                }
                case TOOL_CALL_RESULT, TOOL_CALL_ERROR -> {
                    state.currentToolName = null;
                    state.currentToolInput = null;
                    state.recentEvents.add(new RecentEvent(type));
                }
                case WORKFLOW_COMPLETED -> {
                    state.active = false;
                    state.status = event.getString("status", "UNKNOWN");
                    state.totalDurationMs = event.getLong("totalDurationMs", 0L);
                    state.recentEvents.add(new RecentEvent(type));
                }
                case AGENT_RESPONSE -> {
                    state.recentEvents.add(new RecentEvent(type));
                }
            }
            if (state.recentEvents.size() > 20) {
                state.recentEvents.remove(0);
            }
        }
    }

    public CurrentExecutionState getState() {
        lazyInit();
        synchronized (stateLock) {
            CurrentExecutionState snapshot = new CurrentExecutionState();
            snapshot.active = state.active;
            snapshot.status = state.status;
            snapshot.workflowType = state.workflowType;
            snapshot.startTime = state.startTime;
            snapshot.currentStep = state.currentStep;
            snapshot.currentStepRole = state.currentStepRole;
            snapshot.currentStepStatus = state.currentStepStatus;
            snapshot.currentToolName = state.currentToolName;
            snapshot.currentToolInput = state.currentToolInput;
            snapshot.completedSteps.addAll(state.completedSteps);
            snapshot.recentEvents.addAll(state.recentEvents);

            if (snapshot.active && snapshot.startTime != null && !snapshot.startTime.isEmpty()) {
                snapshot.totalDurationMs = java.time.Duration.between(
                    java.time.Instant.parse(snapshot.startTime),
                    java.time.Instant.now()
                ).toMillis();
            } else {
                snapshot.totalDurationMs = state.totalDurationMs;
            }
            return snapshot;
        }
    }

    public static class RecentEvent {
        public final String type;
        public final String timestamp;

        public RecentEvent(String type) {
            this.type = type;
            this.timestamp = Instant.now().toString();
        }
    }

    @io.quarkus.runtime.annotations.RegisterForReflection
    public static class CurrentExecutionState {
        public boolean active = false;
        public String status = I18n.tr(MessageKey.STATUS_IDLE);
        public String workflowType = "";
        public String startTime = "";
        public String currentStep;
        public String currentStepRole;
        public String currentStepStatus;
        public String currentToolName;
        public String currentToolInput;
        public long totalDurationMs;
        public final List<String> completedSteps = new ArrayList<>();
        public final List<RecentEvent> recentEvents = new ArrayList<>();
    }
}
