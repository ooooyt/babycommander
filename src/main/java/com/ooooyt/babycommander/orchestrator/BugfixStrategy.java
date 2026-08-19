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

/**
 * BUGFIX strategy: run the test suite, and if it fails, let a fixer agent drive
 * an {@link EditLoop} to repair the code until tests pass or attempts are
 * exhausted.
 */
public class BugfixStrategy implements ModeStrategy {

    private final AgentFactory agentFactory;
    private final StatusEventPublisher statusPublisher;
    private final OrchestratorContext context;
    private final TestRunner testRunner;
    private final ToolLocator toolLocator;

    public BugfixStrategy(AgentFactory agentFactory, StatusEventPublisher statusPublisher,
                          OrchestratorContext context, TestRunner testRunner, ToolLocator toolLocator) {
        this.agentFactory = agentFactory;
        this.statusPublisher = statusPublisher;
        this.context = context;
        this.testRunner = testRunner;
        this.toolLocator = toolLocator;
    }

    @Override
    public TaskResult execute(String task, long workflowStart) {
        String fixerSession = "fixer-" + java.util.UUID.randomUUID();

        String testCommand = testRunner.detectTestCommand();
        String testOutput = testRunner.runTests(testCommand);

        if (testRunner.isSuccessful(testOutput)) {
            statusPublisher.workflowCompleted("SUCCESS", System.currentTimeMillis() - workflowStart);
            return TaskResult.success(context.getWorkspace(), I18n.tr(MessageKey.ORCH_TESTS_PASSING) + testOutput);
        }

        AgentContext fixerCtx = agentFactory.createAgent(
            AgentRole.FIXER.getValue(), fixerSession, context.getProjectFolder()
        );

        try {
            EditLoop editLoop = new EditLoop(toolLocator.getShellTool(), context.getProjectFolder());
            EditLoopResult editResult = editLoop.run(fixerCtx, testCommand, testOutput, 3);

            if (editResult.success()) {
                statusPublisher.workflowCompleted("SUCCESS", System.currentTimeMillis() - workflowStart);
                return TaskResult.success(context.getWorkspace(),
                    I18n.tr(MessageKey.ORCH_BUGFIX_SUCCESS, editResult.attempts()) + "\n\nTest output:\n" + editResult.testOutput());
            } else {
                String failureReport = FailureReport.build(editResult);
                statusPublisher.workflowCompleted("FAILURE", System.currentTimeMillis() - workflowStart);
                return TaskResult.failure(context.getWorkspace(),
                    I18n.tr(MessageKey.ORCH_BUGFIX_FAILED, editResult.attempts()) + "\n\n" + failureReport);
            }
        } finally {
            try {
                agentFactory.disposeAgent(fixerSession);
            } catch (IllegalArgumentException ignored) {}
        }
    }
}
