package com.ooooyt.babycommander.orchestrator;

import com.ooooyt.babycommander.agent.AgentContext;
import com.ooooyt.babycommander.agent.AgentFactory;
import com.ooooyt.babycommander.model.AgentRole;
import com.ooooyt.babycommander.model.TaskResult;
import com.ooooyt.babycommander.status.StatusEventPublisher;
import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;

import io.quarkus.logging.Log;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Runs a task on a single orchestrator agent with retry-on-transient-error,
 * publishing the full step/workflow lifecycle. Used both as the AUTO fallback
 * and for the explicit SINGLE workflow type.
 */
public class SingleAgentStrategy implements ModeStrategy {

    private final AgentFactory agentFactory;
    private final StatusEventPublisher statusPublisher;
    private final OrchestratorContext context;

    public SingleAgentStrategy(AgentFactory agentFactory, StatusEventPublisher statusPublisher,
                                OrchestratorContext context) {
        this.agentFactory = agentFactory;
        this.statusPublisher = statusPublisher;
        this.context = context;
    }

    @Override
    public TaskResult execute(String task, long workflowStart) {
        String sessionId = UUID.randomUUID().toString();
        long stepStart = System.currentTimeMillis();

        statusPublisher.stepStarted(sessionId, "orchestrator", task);

        AgentContext ctx = agentFactory.createAgent(
            AgentRole.ORCHESTRATOR.getValue(), sessionId, context.getProjectFolder()
        );

        int retryCount = 0;
        int maxRetries = 3;

        while (retryCount <= maxRetries) {
            try {
                String output = ctx.agent().chat(task);
                if (output == null || output.isBlank()) {
                    statusPublisher.stepFailed(sessionId, "orchestrator", I18n.tr(MessageKey.ORCH_EMPTY_RESPONSE));
                    agentFactory.disposeAgent(sessionId);
                    statusPublisher.workflowCompleted("FAILURE", System.currentTimeMillis() - stepStart);
                    return TaskResult.failure(context.getWorkspace(), I18n.tr(MessageKey.ORCH_AGENT_EMPTY_RESPONSE));
                }
                statusPublisher.agentResponse(sessionId, "orchestrator", output.length());
                agentFactory.disposeAgent(sessionId);

                statusPublisher.stepCompleted(sessionId, "orchestrator", System.currentTimeMillis() - stepStart);
                statusPublisher.workflowCompleted("SUCCESS", System.currentTimeMillis() - stepStart);

                return new TaskResult(
                    TaskResult.Status.SUCCESS, output, context.getWorkspace(),
                    List.of(),
                    List.of(
                        TaskResult.AgentExecution.of("single-agent", "orchestrator", task, output,
                            Duration.ofMillis(System.currentTimeMillis() - stepStart))
                    )
                );
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                boolean isTextError = errorMsg != null && errorMsg.contains("text cannot be null or blank");
                if ((StepExecutor.isToolExecutionLimitError(e) || isTextError) && ctx.chatMemory() != null) {
                    Log.warnf("Agent error (attempt %d/%d): %s, retrying",
                        retryCount + 1, maxRetries, errorMsg);
                    retryCount++;
                    if (retryCount > maxRetries) {
                        agentFactory.disposeAgent(sessionId);
                        statusPublisher.stepFailed(sessionId, "orchestrator", errorMsg);
                        statusPublisher.workflowCompleted("FAILURE", System.currentTimeMillis() - stepStart);
                        return TaskResult.failure(context.getWorkspace(), I18n.tr(MessageKey.ORCH_EXECUTION_ERROR, errorMsg));
                    }
                } else {
                    agentFactory.disposeAgent(sessionId);
                    statusPublisher.stepFailed(sessionId, "orchestrator", errorMsg);
                    statusPublisher.workflowCompleted("FAILURE", System.currentTimeMillis() - stepStart);
                    return TaskResult.failure(context.getWorkspace(), I18n.tr(MessageKey.ORCH_EXECUTION_ERROR, errorMsg));
                }
            }
        }

        agentFactory.disposeAgent(sessionId);
        statusPublisher.stepFailed(sessionId, "orchestrator", I18n.tr(MessageKey.ORCH_UNEXPECTED_ERROR));
        statusPublisher.workflowCompleted("FAILURE", System.currentTimeMillis() - stepStart);
        return TaskResult.failure(context.getWorkspace(), I18n.tr(MessageKey.ORCH_UNEXPECTED_ERROR));
    }
}
