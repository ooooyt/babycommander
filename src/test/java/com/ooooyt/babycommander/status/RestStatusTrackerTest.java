package com.ooooyt.babycommander.status;

import com.ooooyt.babycommander.util.I18n;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class RestStatusTrackerTest {

    private RestStatusTracker tracker;

    @BeforeAll
    static void setLocaleToEnglish() {
        I18n.setLocale(Locale.ENGLISH);
    }

    @BeforeEach
    void setUp() {
        tracker = new RestStatusTracker(null);
    }

    @Test
    void testInitialStateIsIdle() {
        assertEquals("IDLE", tracker.getState().status);
        assertFalse(tracker.getState().active);
    }

    @Test
    void testWorkflowStartedSetsActive() throws Exception {
        JsonObject event = new JsonObject()
            .put("eventType", "WORKFLOW_STARTED")
            .put("workflowType", "SEQUENTIAL")
            .put("taskDescription", "Test task");

        invokeUpdateState(event);

        RestStatusTracker.CurrentExecutionState state = tracker.getState();
        assertTrue(state.active);
        assertEquals("SEQUENTIAL", state.workflowType);
        assertEquals("IDLE", state.status);
        assertNotNull(state.startTime);
    }

    @Test
    void testStepStartedUpdatesCurrentStep() throws Exception {
        invokeUpdateState(workflowStartedEvent());

        JsonObject event = new JsonObject()
            .put("eventType", "STEP_STARTED")
            .put("stepId", "plan-001")
            .put("agentRole", "planner");

        invokeUpdateState(event);

        RestStatusTracker.CurrentExecutionState state = tracker.getState();
        assertEquals("plan-001", state.currentStep);
        assertEquals("planner", state.currentStepRole);
        assertEquals("RUNNING", state.currentStepStatus);
    }

    @Test
    void testToolCallStartSetsCurrentTool() throws Exception {
        invokeUpdateState(workflowStartedEvent());
        invokeUpdateState(stepStartedEvent());

        JsonObject event = new JsonObject()
            .put("eventType", "TOOL_CALL_START")
            .put("stepId", "plan-001")
            .put("toolName", "write_file")
            .put("toolInput", "doc/design.md");

        invokeUpdateState(event);

        RestStatusTracker.CurrentExecutionState state = tracker.getState();
        assertEquals("write_file", state.currentToolName);
        assertEquals("doc/design.md", state.currentToolInput);
    }

    @Test
    void testStepCompletedMovesToCompleted() throws Exception {
        invokeUpdateState(workflowStartedEvent());
        invokeUpdateState(stepStartedEvent());

        JsonObject event = new JsonObject()
            .put("eventType", "STEP_COMPLETED")
            .put("stepId", "plan-001")
            .put("agentRole", "planner")
            .put("durationMs", 2300L);

        invokeUpdateState(event);

        RestStatusTracker.CurrentExecutionState state = tracker.getState();
        assertNull(state.currentStep);
        assertTrue(state.completedSteps.contains("plan-001"));
    }

    @Test
    void testWorkflowCompletedSetsInactive() throws Exception {
        invokeUpdateState(workflowStartedEvent());

        JsonObject event = new JsonObject()
            .put("eventType", "WORKFLOW_COMPLETED")
            .put("status", "SUCCESS")
            .put("totalDurationMs", 5000L);

        invokeUpdateState(event);

        RestStatusTracker.CurrentExecutionState state = tracker.getState();
        assertFalse(state.active);
        assertEquals("SUCCESS", state.status);
        assertEquals(5000L, state.totalDurationMs);
    }

    @Test
    void testRecentEventsAreCappedAt20() throws Exception {
        invokeUpdateState(workflowStartedEvent());

        for (int i = 0; i < 25; i++) {
            invokeUpdateState(new JsonObject()
                .put("eventType", "TOOL_CALL_START")
                .put("stepId", "step-" + i)
                .put("toolName", "tool-" + i)
                .put("toolInput", "input-" + i));
        }

        assertEquals(20, tracker.getState().recentEvents.size());
    }

    private JsonObject workflowStartedEvent() {
        return new JsonObject()
            .put("eventType", "WORKFLOW_STARTED")
            .put("workflowType", "SEQUENTIAL")
            .put("taskDescription", "Test");
    }

    private JsonObject stepStartedEvent() {
        return new JsonObject()
            .put("eventType", "STEP_STARTED")
            .put("stepId", "plan-001")
            .put("agentRole", "planner");
    }

    private void invokeUpdateState(JsonObject event) throws Exception {
        Method method = RestStatusTracker.class.getDeclaredMethod("updateState", JsonObject.class);
        method.setAccessible(true);
        method.invoke(tracker, event);
    }
}
