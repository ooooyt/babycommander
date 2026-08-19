package com.ooooyt.babycommander.orchestrator;

import com.ooooyt.babycommander.model.TaskResult;
import com.ooooyt.babycommander.skill.SkillRegistry;
import com.ooooyt.babycommander.skill.SkillWorkflowStep;
import com.ooooyt.babycommander.status.StatusEventContext;
import com.ooooyt.babycommander.status.StatusEventPublisher;
import com.ooooyt.babycommander.tool.PlanTool;
import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;
import com.ooooyt.babycommander.workflow.WorkflowEngine;
import com.ooooyt.babycommander.workflow.WorkflowStep;

import io.quarkus.logging.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes multi-agent workflows: skill-defined workflows (driven by the LLM via
 * the {@code invoke_skill_workflow} tool) and the complexity-routed
 * "complex-task" workflow, as well as the default planner/writer/tester
 * {@link #executeMultiAgent} pipeline.
 *
 * <p>Step execution is delegated to {@link StepExecutor}, which de-duplicates
 * the retry loop that previously lived inline here.
 */
public class SkillWorkflowExecutor {

    private final SkillRegistry skillRegistry;
    private final WorkflowEngine workflowEngine;
    private final StatusEventPublisher statusPublisher;
    private final StatusEventContext statusContext;
    private final OrchestratorContext context;
    private final ToolLocator toolLocator;
    private final StepExecutor stepExecutor;

    public SkillWorkflowExecutor(SkillRegistry skillRegistry, WorkflowEngine workflowEngine,
                                  StatusEventPublisher statusPublisher, StatusEventContext statusContext,
                                  OrchestratorContext context, ToolLocator toolLocator,
                                  StepExecutor stepExecutor) {
        this.skillRegistry = skillRegistry;
        this.workflowEngine = workflowEngine;
        this.statusPublisher = statusPublisher;
        this.statusContext = statusContext;
        this.context = context;
        this.toolLocator = toolLocator;
        this.stepExecutor = stepExecutor;
    }

    /**
     * Public entry point for invoking a named skill's multi-agent workflow.
     *
     * <p>Skill selection is driven by the LLM (in-context), which calls the
     * {@code invoke_skill_workflow} tool after seeing the skill catalog in the
     * system prompt. This method resolves the workflow steps by name and
     * delegates to {@link #executeSkillWorkflow}, auto-initializing the
     * orchestrator if needed.
     *
     * @param skillName the exact name of the skill workflow to invoke
     * @param task      the task description to execute
     * @return the workflow result; a {@code FAILURE} with a helpful message if no
     *         workflow skill is registered under {@code skillName}
     */
    public TaskResult runSkillWorkflow(String skillName, String task) {
        if (!context.isInitialized()) {
            context.initialize(context.determineDefaultWorkspace(), context.determineDefaultProject());
        }
        List<SkillWorkflowStep> workflowSteps = skillRegistry.getSkillWorkflow(skillName);
        if (workflowSteps == null || workflowSteps.isEmpty()) {
            String available = String.join(", ", skillRegistry.getSkillsWithWorkflow());
            String hint = available.isEmpty()
                ? "no skill workflows are currently registered"
                : "available skill workflows: " + available;
            return TaskResult.failure(context.getWorkspace(),
                "No skill workflow found for '" + skillName + "' (" + hint + ").");
        }
        Log.infof("LLM invoked skill workflow '%s' for: %.80s", skillName, task);
        return executeSkillWorkflow(skillName, workflowSteps, task);
    }

    /**
     * Execute a multi-agent workflow defined by a skill's workflow steps.
     * Creates a PlanTool plan so the left panel shows step progress.
     */
    public TaskResult executeSkillWorkflow(String skillName, List<SkillWorkflowStep> workflowSteps, String task) {
        long workflowStart = System.currentTimeMillis();
        statusContext.activate();
        statusPublisher.workflowStarted(skillName, task);
        try {
            // Build WorkflowStep list from SkillWorkflowStep records
            List<WorkflowStep> steps = new ArrayList<>();
            for (SkillWorkflowStep sws : workflowSteps) {
                steps.add(new WorkflowStep(
                    sws.agentId(),
                    sws.instruction(),
                    false,  // skipOnError
                    true    // required
                ));
            }

            // Create PlanTool plan so the left panel shows phases
            PlanTool pt = toolLocator.getPlanTool();
            if (pt != null) {
                PlanTool.PhaseInput[] phaseInputs = workflowSteps.stream()
                    .map(sws -> new PlanTool.PhaseInput(sws.agentId(), sws.instruction()))
                    .toArray(PlanTool.PhaseInput[]::new);
                pt.createPlan(skillName + ": " + task, phaseInputs);
            }

            // Execute steps sequentially via WorkflowEngine
            TaskResult engineResult = workflowEngine.executeSequential(steps, stepExecutor::execute, context.getWorkspace());

            // Update PlanTool phases based on result
            if (pt != null) {
                if (engineResult.status() == TaskResult.Status.SUCCESS) {
                    for (int i = 1; i <= workflowSteps.size(); i++) {
                        pt.completePhase(i);
                    }
                } else {
                    for (int i = 1; i <= workflowSteps.size(); i++) {
                        pt.failPhase(i);
                    }
                }
            }

            long elapsed = System.currentTimeMillis() - workflowStart;
            statusPublisher.workflowCompleted(
                engineResult.status().name(), elapsed);
            return engineResult;

        } catch (Exception e) {
            Log.errorf("Skill workflow '%s' failed: %s", skillName, e.getMessage());
            statusPublisher.workflowCompleted("FAILURE", System.currentTimeMillis() - workflowStart);
            return TaskResult.failure(context.getWorkspace(), "Skill workflow failed: " + e.getMessage());
        } finally {
            statusContext.deactivate();
        }
    }

    /**
     * Default multi-agent pipeline: planner -> writer -> tester, each writing
     * into {@code <project>/doc/design.md} as a handoff artifact.
     */
    public TaskResult executeMultiAgent(String task) {
        String planSession = "plan-" + java.util.UUID.randomUUID();
        String writeSession = "write-" + java.util.UUID.randomUUID();
        String testSession = "test-" + java.util.UUID.randomUUID();

        List<WorkflowStep> steps = new ArrayList<>();

        steps.add(new WorkflowStep(
            planSession,
            I18n.tr(MessageKey.ORCH_MULTI_AGENT_DESIGN) + "\n\n" + task
                + "\n\nWrite the design document to " + context.getProjectFolder() + "/doc/design.md",
            false, true
        ));

        steps.add(new WorkflowStep(
            writeSession,
            I18n.tr(MessageKey.ORCH_MULTI_AGENT_READ_DESIGN, context.getProjectFolder() + "/doc/design.md"),
            false, true
        ));

        steps.add(new WorkflowStep(
            testSession,
            I18n.tr(MessageKey.ORCH_MULTI_AGENT_REVIEW, context.getProjectFolder() + "/doc/design.md"),
            false, true
        ));

        return executeSequential(steps);
    }

    public TaskResult executeSequential(List<WorkflowStep> steps) {
        return workflowEngine.executeSequential(steps, stepExecutor::execute, context.getWorkspace());
    }

    /**
     * Get workflow steps for a complexity-classified workflow name.
     * Falls back to skill registry lookup, or returns default planner/writer/tester steps.
     */
    public List<SkillWorkflowStep> getWorkflowStepsForComplexity(String workflowName) {
        List<SkillWorkflowStep> steps = skillRegistry.getSkillWorkflow(workflowName);
        if (!steps.isEmpty()) {
            return steps;
        }
        // Default multi-agent workflow: planner -> writer -> tester
        return List.of(
            new SkillWorkflowStep("planner", "Create a detailed plan for: "),
            new SkillWorkflowStep("writer", "Implement the code for: "),
            new SkillWorkflowStep("tester", "Write and run tests for: ")
        );
    }
}
