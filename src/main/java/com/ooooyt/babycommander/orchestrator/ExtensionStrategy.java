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
 * EXTENSION strategy: a writer agent implements the new feature (including
 * tests), then the test suite validates it. If tests fail, a fixer agent
 * drives an {@link EditLoop} to repair the implementation.
 */
public class ExtensionStrategy implements ModeStrategy {

    private final AgentFactory agentFactory;
    private final StatusEventPublisher statusPublisher;
    private final OrchestratorContext context;
    private final TestRunner testRunner;
    private final ToolLocator toolLocator;

    public ExtensionStrategy(AgentFactory agentFactory, StatusEventPublisher statusPublisher,
                             OrchestratorContext context, TestRunner testRunner, ToolLocator toolLocator) {
        this.agentFactory = agentFactory;
        this.statusPublisher = statusPublisher;
        this.context = context;
        this.testRunner = testRunner;
        this.toolLocator = toolLocator;
    }

    @Override
    public TaskResult execute(String task, long workflowStart) {
        String writerSession = "extension-writer-" + UUID.randomUUID();
        String fixerSession = "fixer-" + UUID.randomUUID();

        AgentContext writerCtx = agentFactory.createAgent(
            AgentRole.WRITER.getValue(), writerSession, context.getProjectFolder()
        );

        try {
            String extensionPrompt = I18n.tr(MessageKey.ORCH_EXTENSION_PROMPT) + "\n\n" + task
                + "\n\nImplement all necessary source files and test files for the new feature."
                + "\nAfter implementation, the full test suite will be run to validate."
                + "\nAlso generate test files for any new functionality you add.";

            String extensionOutput;
            int retryCount = 0;
            while (true) {
                try {
                    extensionOutput = writerCtx.agent().chat(extensionPrompt);
                    break;
                } catch (Exception e) {
                    String errorMsg = e.getMessage();
                    boolean isTextError = errorMsg != null && errorMsg.contains("text cannot be null or blank");
                    if (isTextError && writerCtx.chatMemory() != null && retryCount < 3) {
                        Log.warnf("Agent error (attempt %d/3): %s, retrying", retryCount + 1, errorMsg);
                        retryCount++;
                    } else {
                        statusPublisher.workflowCompleted("FAILURE", System.currentTimeMillis() - workflowStart);
                        return TaskResult.failure(context.getWorkspace(), I18n.tr(MessageKey.ORCH_EXTENSION_FAILED_MSG, errorMsg));
                    }
                }
            }

            String testCommand = testRunner.detectTestCommand();
            String testOutput = testRunner.runTests(testCommand);

            if (testRunner.isSuccessful(testOutput)) {
                statusPublisher.workflowCompleted("SUCCESS", System.currentTimeMillis() - workflowStart);
                return TaskResult.success(context.getWorkspace(),
                    I18n.tr(MessageKey.ORCH_EXTENSION_SUCCESS) + "\n\nTest output:\n" + testOutput + "\n\nImplementation details:\n" + extensionOutput);
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
                        I18n.tr(MessageKey.ORCH_EXTENSION_REPAIRED, editResult.attempts()) + "\n\nTest output:\n" + editResult.testOutput() + "\n\nImplementation details:\n" + extensionOutput);
                } else {
                    String failureReport = FailureReport.build(editResult);
                    statusPublisher.workflowCompleted("FAILURE", System.currentTimeMillis() - workflowStart);
                    return TaskResult.failure(context.getWorkspace(),
                        I18n.tr(MessageKey.ORCH_EXTENSION_FAILED, editResult.attempts()) + "\n\n" + failureReport);
                }
            } finally {
                try {
                    agentFactory.disposeAgent(fixerCtx.sessionId());
                } catch (IllegalArgumentException ignored) {}
            }
        } finally {
            try {
                agentFactory.disposeAgent(writerSession);
            } catch (IllegalArgumentException ignored) {}
        }
    }
}
