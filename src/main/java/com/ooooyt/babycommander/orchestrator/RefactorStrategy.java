package com.ooooyt.babycommander.orchestrator;

import com.ooooyt.babycommander.agent.AgentContext;
import com.ooooyt.babycommander.agent.AgentFactory;
import com.ooooyt.babycommander.editloop.EditLoop;
import com.ooooyt.babycommander.editloop.EditLoopResult;
import com.ooooyt.babycommander.model.AgentRole;
import com.ooooyt.babycommander.model.TaskResult;
import com.ooooyt.babycommander.status.StatusEventPublisher;
import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;

import io.quarkus.logging.Log;

import java.util.UUID;

/**
 * REFACTOR strategy: a fixer agent refactors the code, then the test suite
 * validates the change. If tests fail, a repair agent drives an
 * {@link EditLoop} to restore green tests.
 */
public class RefactorStrategy implements ModeStrategy {

    private final AgentFactory agentFactory;
    private final StatusEventPublisher statusPublisher;
    private final OrchestratorContext context;
    private final TestRunner testRunner;
    private final ToolLocator toolLocator;

    public RefactorStrategy(AgentFactory agentFactory, StatusEventPublisher statusPublisher,
                            OrchestratorContext context, TestRunner testRunner, ToolLocator toolLocator) {
        this.agentFactory = agentFactory;
        this.statusPublisher = statusPublisher;
        this.context = context;
        this.testRunner = testRunner;
        this.toolLocator = toolLocator;
    }

    @Override
    public TaskResult execute(String task, long workflowStart) {
        String refactorSession = "refactor-" + UUID.randomUUID();
        String fixerSession = "fixer-" + UUID.randomUUID();

        AgentContext refactorCtx = agentFactory.createAgent(
            AgentRole.FIXER.getValue(), refactorSession, context.getProjectFolder()
        );

        try {
            String refactorPrompt = I18n.tr(MessageKey.ORCH_REFACTOR_PROMPT) + "\n\n" + task
                + "\n\nAfter refactoring, tests will be run to validate.";

            String refactorOutput;
            int retryCount = 0;
            while (true) {
                try {
                    refactorOutput = refactorCtx.agent().chat(refactorPrompt);
                    break;
                } catch (Exception e) {
                    String errorMsg = e.getMessage();
                    boolean isTextError = errorMsg != null && errorMsg.contains("text cannot be null or blank");
                    if (isTextError && refactorCtx.chatMemory() != null && retryCount < 3) {
                        Log.warnf("Agent error (attempt %d/3): %s, retrying", retryCount + 1, errorMsg);
                        retryCount++;
                    } else {
                        statusPublisher.workflowCompleted("FAILURE", System.currentTimeMillis() - workflowStart);
                        return TaskResult.failure(context.getWorkspace(), I18n.tr(MessageKey.ORCH_REFACTOR_FAILED_MSG, errorMsg));
                    }
                }
            }

            String testCommand = testRunner.detectTestCommand();
            String testOutput = testRunner.runTests(testCommand);

            if (testRunner.isSuccessful(testOutput)) {
                statusPublisher.workflowCompleted("SUCCESS", System.currentTimeMillis() - workflowStart);
                return TaskResult.success(context.getWorkspace(),
                    I18n.tr(MessageKey.ORCH_REFACTOR_SUCCESS) + "\n\nTest output:\n" + testOutput + "\n\nRefactoring details:\n" + refactorOutput);
            }

            AgentContext repairCtx = agentFactory.createAgent(
                AgentRole.FIXER.getValue(), fixerSession, context.getProjectFolder()
            );

            try {
                EditLoop editLoop = new EditLoop(toolLocator.getShellTool(), context.getProjectFolder());
                EditLoopResult editResult = editLoop.run(repairCtx, testCommand, testOutput, 3);

                if (editResult.success()) {
                    statusPublisher.workflowCompleted("SUCCESS", System.currentTimeMillis() - workflowStart);
                    return TaskResult.success(context.getWorkspace(),
                        I18n.tr(MessageKey.ORCH_REFACTOR_REPAIRED, editResult.attempts()) + "\n\nTest output:\n" + editResult.testOutput());
                } else {
                    String failureReport = FailureReport.build(editResult);
                    statusPublisher.workflowCompleted("FAILURE", System.currentTimeMillis() - workflowStart);
                    return TaskResult.failure(context.getWorkspace(),
                        I18n.tr(MessageKey.ORCH_REFACTOR_FAILED, editResult.attempts()) + "\n\n" + failureReport);
                }
            } finally {
                try {
                    agentFactory.disposeAgent(repairCtx.sessionId());
                } catch (IllegalArgumentException ignored) {}
            }
        } finally {
            try {
                agentFactory.disposeAgent(refactorSession);
            } catch (IllegalArgumentException ignored) {}
        }
    }
}
