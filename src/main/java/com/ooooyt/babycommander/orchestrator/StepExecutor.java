package com.ooooyt.babycommander.orchestrator;

import com.ooooyt.babycommander.agent.AgentContext;
import com.ooooyt.babycommander.agent.AgentFactory;
import com.ooooyt.babycommander.agent.memory.ToolCallAwareChatMemory;
import com.ooooyt.babycommander.model.AgentRole;
import com.ooooyt.babycommander.status.StatusEventPublisher;
import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;
import com.ooooyt.babycommander.workflow.StepResult;
import com.ooooyt.babycommander.workflow.WorkflowStep;

import io.quarkus.logging.Log;

/**
 * Runs a single {@link WorkflowStep} on an LLM agent with retry-on-transient-error,
 * publishing step lifecycle status along the way. Extracted to de-duplicate the
 * step-execution retry loop that previously appeared verbatim in
 * {@code executeSkillWorkflow} and {@code executeSequential}.
 */
public class StepExecutor {

    private final AgentFactory agentFactory;
    private final StatusEventPublisher statusPublisher;
    private final OrchestratorContext context;

    public StepExecutor(AgentFactory agentFactory, StatusEventPublisher statusPublisher,
                        OrchestratorContext context) {
        this.agentFactory = agentFactory;
        this.statusPublisher = statusPublisher;
        this.context = context;
    }

    /**
     * Execute one workflow step: create an agent for the step's role, run its
     * instruction with up to 3 retries on transient (tool-execution-limit or
     * blank-text) errors, and return the {@link StepResult}.
     */
    public StepResult execute(WorkflowStep step) {
        long stepStart = System.currentTimeMillis();
        String role = resolveRole(step.agentId());

        statusPublisher.stepStarted(step.agentId(), role, step.instruction());

        AgentContext ctx = agentFactory.createAgent(role, step.agentId(), context.getProjectFolder());

        int retryCount = 0;
        int maxRetries = 3;

        while (retryCount <= maxRetries) {
            try {
                if (ctx.chatMemory() instanceof ToolCallAwareChatMemory tcm) {
                    Log.infof("Agent '%s' memory before LLM call: %d messages, %d tool calls, ~%d tokens",
                        step.agentId(), tcm.messages().size(), tcm.countToolCalls(), tcm.getEstimatedTokenTotal());
                }
                String output = ctx.agent().chat(step.instruction());
                if (output == null || output.isBlank()) {
                    statusPublisher.stepFailed(step.agentId(), role, I18n.tr(MessageKey.ORCH_EMPTY_RESPONSE));
                    return StepResult.failure(I18n.tr(MessageKey.ORCH_AGENT_EMPTY_RESPONSE));
                } else {
                    long duration = System.currentTimeMillis() - stepStart;
                    statusPublisher.agentResponse(step.agentId(), role, output.length());
                    statusPublisher.stepCompleted(step.agentId(), role, duration);
                    return StepResult.success(output);
                }
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                boolean isTextError = errorMsg != null && errorMsg.contains("text cannot be null or blank");
                if ((isToolExecutionLimitError(e) || isTextError) && ctx.chatMemory() != null) {
                    Log.warnf("Agent error (attempt %d/%d): %s, retrying",
                        retryCount + 1, maxRetries, errorMsg);
                    retryCount++;
                    if (retryCount > maxRetries) {
                        statusPublisher.stepFailed(step.agentId(), role, errorMsg);
                        return StepResult.failure(errorMsg);
                    }
                } else {
                    statusPublisher.stepFailed(step.agentId(), role, errorMsg);
                    return StepResult.failure(errorMsg);
                }
            }
        }

        return StepResult.failure(I18n.tr(MessageKey.ORCH_UNEXPECTED_ERROR));
    }

    /**
     * Map an agentId prefix to a concrete {@link AgentRole}.
     */
    static String resolveRole(String agentId) {
        if (agentId.startsWith("plan")) return AgentRole.PLANNER.getValue();
        if (agentId.startsWith("write")) return AgentRole.WRITER.getValue();
        if (agentId.startsWith("test")) return AgentRole.TESTER.getValue();
        if (agentId.startsWith("review")) return AgentRole.REVIEWER.getValue();
        if (agentId.startsWith("fixer")) return AgentRole.FIXER.getValue();
        if (agentId.startsWith("refactor")) return AgentRole.FIXER.getValue();
        if (agentId.startsWith("router")) return AgentRole.ROUTER.getValue();
        if (agentId.startsWith("extension")) return AgentRole.WRITER.getValue();
        return AgentRole.ORCHESTRATOR.getValue();
    }

    static boolean isToolExecutionLimitError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        return msg.toLowerCase().contains("tool") && msg.toLowerCase().contains("execution")
            && (msg.toLowerCase().contains("limit") || msg.toLowerCase().contains("exceed")
                || msg.toLowerCase().contains("too many"));
    }
}
