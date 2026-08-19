package com.ooooyt.babycommander.workflow;

import com.ooooyt.babycommander.model.TaskResult;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowEngineTest {

    @Test
    void testExecuteSequentialSuccess() {
        WorkflowEngine engine = new WorkflowEngine();

        List<WorkflowStep> steps = List.of(
            new WorkflowStep("agent-1", "Step one", true, true),
            new WorkflowStep("agent-2", "Step two", true, true)
        );

        TaskResult result = engine.executeSequential(steps, mockStepExecutor());
        assertEquals(TaskResult.Status.SUCCESS, result.status());
        assertEquals(2, result.agentExecutions().size());
    }

    @Test
    void testExecuteSequentialStopsOnFailure() {
        WorkflowEngine engine = new WorkflowEngine();

        List<WorkflowStep> steps = List.of(
            new WorkflowStep("agent-1", "ok", true, true),
            new WorkflowStep("agent-2", "fail-me", true, true),
            new WorkflowStep("agent-3", "never-reached", true, true)
        );

        TaskResult result = engine.executeSequential(steps, (step) -> {
            if (step.instruction().contains("fail-me")) {
                return StepResult.failure("STEP_FAILED");
            }
            return StepResult.success("SUCCESS");
        });

        assertEquals(TaskResult.Status.FAILURE, result.status());
        assertEquals(2, result.agentExecutions().size());
    }

    @Test
    void testExecuteSequentialSkipOnErrorContinues() {
        WorkflowEngine engine = new WorkflowEngine();

        List<WorkflowStep> steps = List.of(
            new WorkflowStep("agent-1", "ok", false, false),
            new WorkflowStep("agent-2", "fail-me", true, false),
            new WorkflowStep("agent-3", "continue", true, true)
        );

        TaskResult result = engine.executeSequential(steps, (step) -> {
            if (step.instruction().contains("fail-me")) {
                return StepResult.failure("STEP_FAILED");
            }
            return StepResult.success("SUCCESS");
        });

        assertEquals(TaskResult.Status.SUCCESS, result.status());
        assertEquals(3, result.agentExecutions().size());
    }

    @Test
    void testExecuteSingleAgent() {
        WorkflowEngine engine = new WorkflowEngine();

        List<WorkflowStep> steps = List.of(
            new WorkflowStep("solo-agent", "Do everything", true, true)
        );

        TaskResult result = engine.executeSequential(steps, step -> StepResult.success("DONE"));
        assertEquals(TaskResult.Status.SUCCESS, result.status());
        assertEquals(1, result.agentExecutions().size());
    }

    @Test
    void testEmptySteps() {
        WorkflowEngine engine = new WorkflowEngine();
        TaskResult result = engine.executeSequential(List.of(), step -> StepResult.success("noop"));
        assertEquals(TaskResult.Status.SUCCESS, result.status());
        assertTrue(result.agentExecutions().isEmpty());
    }

    private static StepExecutor mockStepExecutor() {
        return step -> StepResult.success("EXECUTED: " + step.instruction());
    }
}
