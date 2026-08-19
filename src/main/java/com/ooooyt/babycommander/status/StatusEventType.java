package com.ooooyt.babycommander.status;

public enum StatusEventType {
    WORKFLOW_STARTED,
    WORKFLOW_TYPE_CHANGED,
    WORKFLOW_COMPLETED,
    STEP_STARTED,
    STEP_COMPLETED,
    STEP_FAILED,
    AGENT_RESPONSE,
    TOOL_CALL_START,
    TOOL_CALL_TEXT,
    TOOL_CALL_RESULT,
    TOOL_CALL_ERROR,
    THINKING_STARTED,
    THINKING_DURATION;

    public boolean isActive() {
        return this == STEP_STARTED || this == TOOL_CALL_START;
    }

    public boolean isTerminal() {
        return this == STEP_COMPLETED || this == STEP_FAILED
            || this == WORKFLOW_COMPLETED || this == TOOL_CALL_RESULT || this == TOOL_CALL_ERROR;
    }
}
