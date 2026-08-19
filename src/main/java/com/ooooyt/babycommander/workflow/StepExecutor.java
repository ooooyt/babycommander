package com.ooooyt.babycommander.workflow;

@FunctionalInterface
public interface StepExecutor {
    /**
     * Execute a workflow step and return a typed result.
     * Use {@link StepResult#success(String)} or {@link StepResult#failure(String)}.
     */
    StepResult execute(WorkflowStep step);
}
