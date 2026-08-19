package com.ooooyt.babycommander.workflow;

public record WorkflowStep(
    String agentId,
    String instruction,
    boolean skipOnError,
    boolean required
) {}
