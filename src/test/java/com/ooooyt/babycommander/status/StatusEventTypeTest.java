package com.ooooyt.babycommander.status;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatusEventTypeTest {

    @Test
    void testAllValues() {
        StatusEventType[] values = StatusEventType.values();
        assertEquals(13, values.length);
    }

    @Test
    void testIsActive() {
        assertTrue(StatusEventType.STEP_STARTED.isActive());
        assertTrue(StatusEventType.TOOL_CALL_START.isActive());
        assertFalse(StatusEventType.WORKFLOW_STARTED.isActive());
        assertFalse(StatusEventType.WORKFLOW_COMPLETED.isActive());
        assertFalse(StatusEventType.STEP_COMPLETED.isActive());
        assertFalse(StatusEventType.STEP_FAILED.isActive());
        assertFalse(StatusEventType.AGENT_RESPONSE.isActive());
        assertFalse(StatusEventType.TOOL_CALL_RESULT.isActive());
        assertFalse(StatusEventType.TOOL_CALL_ERROR.isActive());
        assertFalse(StatusEventType.THINKING_DURATION.isActive());
        assertFalse(StatusEventType.THINKING_STARTED.isActive());
    }

    @Test
    void testIsTerminal() {
        assertTrue(StatusEventType.STEP_COMPLETED.isTerminal());
        assertTrue(StatusEventType.STEP_FAILED.isTerminal());
        assertTrue(StatusEventType.WORKFLOW_COMPLETED.isTerminal());
        assertTrue(StatusEventType.TOOL_CALL_RESULT.isTerminal());
        assertTrue(StatusEventType.TOOL_CALL_ERROR.isTerminal());
        assertFalse(StatusEventType.WORKFLOW_STARTED.isTerminal());
        assertFalse(StatusEventType.WORKFLOW_TYPE_CHANGED.isTerminal());
        assertFalse(StatusEventType.STEP_STARTED.isTerminal());
        assertFalse(StatusEventType.AGENT_RESPONSE.isTerminal());
        assertFalse(StatusEventType.TOOL_CALL_START.isTerminal());
        assertFalse(StatusEventType.THINKING_DURATION.isTerminal());
        assertFalse(StatusEventType.THINKING_STARTED.isTerminal());
    }

    @Test
    void testWorkflowStartedNotActiveNotTerminal() {
        assertFalse(StatusEventType.WORKFLOW_STARTED.isActive());
        assertFalse(StatusEventType.WORKFLOW_STARTED.isTerminal());
    }

    @Test
    void testWorkflowTypeChangedNotActiveNotTerminal() {
        assertFalse(StatusEventType.WORKFLOW_TYPE_CHANGED.isActive());
        assertFalse(StatusEventType.WORKFLOW_TYPE_CHANGED.isTerminal());
    }

    @Test
    void testAgentResponseNotActiveNotTerminal() {
        assertFalse(StatusEventType.AGENT_RESPONSE.isActive());
        assertFalse(StatusEventType.AGENT_RESPONSE.isTerminal());
    }

    @Test
    void testToolCallStartIsActive() {
        assertTrue(StatusEventType.TOOL_CALL_START.isActive());
    }

    @Test
    void testStepStartedIsActive() {
        assertTrue(StatusEventType.STEP_STARTED.isActive());
    }

    @Test
    void testStepCompletedIsTerminal() {
        assertTrue(StatusEventType.STEP_COMPLETED.isTerminal());
    }

    @Test
    void testStepFailedIsTerminal() {
        assertTrue(StatusEventType.STEP_FAILED.isTerminal());
    }

    @Test
    void testWorkflowCompletedIsTerminal() {
        assertTrue(StatusEventType.WORKFLOW_COMPLETED.isTerminal());
    }

    @Test
    void testToolCallResultIsTerminal() {
        assertTrue(StatusEventType.TOOL_CALL_RESULT.isTerminal());
    }

    @Test
    void testToolCallErrorIsTerminal() {
        assertTrue(StatusEventType.TOOL_CALL_ERROR.isTerminal());
    }

    @Test
    void testThinkingDurationNotActiveNotTerminal() {
        assertFalse(StatusEventType.THINKING_DURATION.isActive());
        assertFalse(StatusEventType.THINKING_DURATION.isTerminal());
        assertFalse(StatusEventType.THINKING_STARTED.isTerminal());
    }
}
