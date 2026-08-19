package com.ooooyt.babycommander.tool;

import com.ooooyt.babycommander.model.TaskResult;
import com.ooooyt.babycommander.orchestrator.Orchestrator;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.inject.spi.CDI;

/**
 * Lets the LLM invoke a registered skill's multi-agent workflow by name.
 *
 * <p>Skill selection is driven in-context: the skill catalog (name +
 * description) is injected into the system prompt, and the LLM calls this tool
 * only when the user's task semantically matches a listed skill.
 *
 * <p>The {@link Orchestrator} is resolved lazily via {@link CDI#current()} at
 * invocation time. This sidesteps a construction-time circular dependency
 * ({@code CodeGenLifecycle -> Orchestrator -> AgentFactory -> CodeGenLifecycle})
 * that would arise if {@code CodeGenLifecycle} held a direct
 * {@code @Inject Orchestrator} reference. The lookup is safe because the tool
 * is only ever called during a chat, after the CDI container is running.
 */
public class InvokeSkillWorkflowTool {

    @Tool("Invoke a registered skill's multi-agent workflow by name. "
        + "Use this ONLY when the user's task semantically matches one of the skill workflows "
        + "listed in the 'Available Skill Workflows' section of the system prompt. "
        + "Pass the exact skill name as listed and a clear description of the task. "
        + "If you call this, do NOT also call createPlan — the workflow manages its own plan.")
    public String invokeSkillWorkflow(
            @P("The exact name of the skill workflow, as listed in Available Skill Workflows")
            String skillName,
            @P("A clear description of the task to accomplish via this skill workflow")
            String task) {
        if (skillName == null || skillName.isBlank()) {
            return "invoke_skill_workflow: skillName must not be blank.";
        }
        if (task == null || task.isBlank()) {
            return "invoke_skill_workflow: task must not be blank.";
        }

        Orchestrator orchestrator;
        try {
            orchestrator = CDI.current().select(Orchestrator.class).get();
        } catch (Exception e) {
            return "invoke_skill_workflow: orchestrator unavailable: " + e.getMessage();
        }

        TaskResult result = orchestrator.runSkillWorkflow(skillName, task);
        StringBuilder sb = new StringBuilder();
        sb.append("Skill workflow '").append(skillName).append("' finished with status ")
          .append(result.status()).append('.');
        if (result.result() != null && !result.result().isBlank()) {
            sb.append("\n").append(result.result());
        }
        return sb.toString();
    }
}
