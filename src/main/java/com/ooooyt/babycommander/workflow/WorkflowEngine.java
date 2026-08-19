package com.ooooyt.babycommander.workflow;

import com.ooooyt.babycommander.model.TaskResult;
import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class WorkflowEngine {

    /**
     * Execute a sequence of workflow steps with the given workspace path.
     *
     * @param steps         the steps to execute
     * @param stepExecutor  the executor that runs each step
     * @param workspace     the workspace path to include in the result (may be null)
     */
    public TaskResult executeSequential(List<WorkflowStep> steps, StepExecutor stepExecutor, String workspace) {
        if (steps == null) {
            throw new IllegalArgumentException(I18n.tr(MessageKey.WF_STEPS_NULL));
        }

        String resolvedWorkspace = (workspace != null) ? workspace : I18n.tr(MessageKey.WF_WORKSPACE_UNKNOWN);
        List<TaskResult.AgentExecution> executions = new ArrayList<>();
        boolean anyFailed = false;

        for (int i = 0; i < steps.size(); i++) {
            WorkflowStep step = steps.get(i);

            if (step.skipOnError() && anyFailed) {
                executions.add(TaskResult.AgentExecution.of(
                    step.agentId(), "step-" + i, step.instruction(),
                    I18n.tr(MessageKey.WF_SKIPPED), Duration.ZERO
                ));
                continue;
            }

            long start = System.currentTimeMillis();

            StepResult stepResult;
            try {
                stepResult = stepExecutor.execute(step);
            } catch (Exception e) {
                stepResult = StepResult.failure(e.getMessage());
            }

            Duration duration = Duration.ofMillis(System.currentTimeMillis() - start);
            executions.add(TaskResult.AgentExecution.of(
                step.agentId(), "step-" + i, step.instruction(),
                stepResult.output(), duration
            ));

            if (stepResult.failed()) {
                anyFailed = true;
                if (step.required()) {
                    String result = buildFailureSummary(i, steps.size(), step, stepResult.output());
                    return new TaskResult(TaskResult.Status.FAILURE, result, resolvedWorkspace, List.of(), executions);
                }
            }
        }

        StringBuilder summary = new StringBuilder(I18n.tr(MessageKey.WF_COMPLETED) + "\n\n");
        summary.append(I18n.tr(MessageKey.WF_STEPS_EXECUTED, executions.size())).append("\n\n");
        for (TaskResult.AgentExecution exec : executions) {
            summary.append("[").append(exec.agentId()).append("]: ").append(exec.output()).append("\n\n");
        }

        return new TaskResult(TaskResult.Status.SUCCESS, summary.toString().trim(), resolvedWorkspace, List.of(), executions);
    }

    /**
     * Execute a sequence of workflow steps (backward-compatible overload).
     * Uses "unknown" as the workspace.
     */
    public TaskResult executeSequential(List<WorkflowStep> steps, StepExecutor stepExecutor) {
        return executeSequential(steps, stepExecutor, null);
    }

    private static String buildFailureSummary(int failedIndex, int totalSteps, WorkflowStep step, String errorOutput) {
        return I18n.tr(MessageKey.WF_FAILED_AT, failedIndex + 1) + "\n"
            + "Agent: " + step.agentId() + "\n"
            + "Instruction: " + step.instruction() + "\n"
            + "Output: " + errorOutput + "\n\n"
            + I18n.tr(MessageKey.WF_STEPS_COMPLETED, failedIndex + 1, totalSteps);
    }
}
