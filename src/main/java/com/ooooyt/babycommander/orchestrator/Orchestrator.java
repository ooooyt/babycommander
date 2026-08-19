package com.ooooyt.babycommander.orchestrator;

import com.ooooyt.babycommander.agent.AgentFactory;
import com.ooooyt.babycommander.config.YamlConfigLoader;
import com.ooooyt.babycommander.hook.HookManager;
import com.ooooyt.babycommander.intent.ComplexityAnalyzer;
import com.ooooyt.babycommander.intent.IntentDetector;
import com.ooooyt.babycommander.intent.IntentDetector.Mode;
import com.ooooyt.babycommander.model.TaskResult;
import com.ooooyt.babycommander.model.WorkflowType;
import com.ooooyt.babycommander.skill.SkillRegistry;
import com.ooooyt.babycommander.status.StatusEventContext;
import com.ooooyt.babycommander.status.StatusEventPublisher;
import com.ooooyt.babycommander.tool.ToolRegistry;
import com.ooooyt.babycommander.workflow.WorkflowEngine;

import io.quarkus.logging.Log;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

/**
 * Orchestrates task execution. Formerly a ~1000-line god class, it is now a
 * thin routing facade that delegates to single-responsibility collaborators:
 * <ul>
 *   <li>{@link OrchestratorContext} &mdash; workspace/project/session state + default-path resolution</li>
 *   <li>{@link ToolLocator} &mdash; lazy cached lookups of ShellTool/PlanTool/ComplexityRouter</li>
 *   <li>{@link StepExecutor} &mdash; runs one workflow-step agent with retry (de-duplicated)</li>
 *   <li>{@link TestRunner} &mdash; detects/runs/classifies the project test suite</li>
 *   <li>{@link SkillWorkflowExecutor} &mdash; skill & complexity multi-agent workflows</li>
 *   <li>one {@link ModeStrategy} per mode &mdash; bugfix/refactor/extension/create/document/single-agent</li>
 * </ul>
 *
 * <p>The public API (constructor signature, {@code initialize} overloads,
 * {@code execute} overloads, {@code runSkillWorkflow}, getters) is unchanged so
 * {@code ChatEngine}, {@code InvokeSkillWorkflowTool} and the orchestrator tests
 * are unaffected.
 */
@ApplicationScoped
public class Orchestrator {

    private final OrchestratorContext context;
    private final ToolLocator toolLocator;
    private final SkillWorkflowExecutor skillWorkflowExecutor;
    private final ModeStrategy singleAgent;
    private final Map<Mode, ModeStrategy> modeStrategies;
    private final StatusEventPublisher statusPublisher;
    private final StatusEventContext statusContext;

    public Orchestrator(YamlConfigLoader configLoader, ToolRegistry toolRegistry,
                        SkillRegistry skillRegistry,
                        WorkflowEngine workflowEngine, AgentFactory agentFactory,
                        StatusEventPublisher statusPublisher,
                        StatusEventContext statusContext,
                        HookManager hookManager) {
        this.context = new OrchestratorContext(configLoader, toolRegistry);
        this.toolLocator = new ToolLocator(toolRegistry, agentFactory);
        StepExecutor stepExecutor = new StepExecutor(agentFactory, statusPublisher, context);
        TestRunner testRunner = new TestRunner(toolLocator, hookManager, context);
        this.skillWorkflowExecutor = new SkillWorkflowExecutor(
            skillRegistry, workflowEngine, statusPublisher, statusContext,
            context, toolLocator, stepExecutor);

        this.singleAgent = new SingleAgentStrategy(agentFactory, statusPublisher, context);
        ModeStrategy document = new DocumentStrategy(agentFactory, statusPublisher, context);
        ModeStrategy bugfix = new BugfixStrategy(agentFactory, statusPublisher, context, testRunner, toolLocator);
        ModeStrategy refactor = new RefactorStrategy(agentFactory, statusPublisher, context, testRunner, toolLocator);
        ModeStrategy extension = new ExtensionStrategy(agentFactory, statusPublisher, context, testRunner, toolLocator);
        // Siblings the CREATE strategy may re-dispatch to (excludes CREATE itself).
        Map<Mode, ModeStrategy> siblings = Map.of(
            Mode.BUGFIX, bugfix, Mode.REFACTOR, refactor,
            Mode.EXTENSION, extension, Mode.DOCUMENT, document);
        ModeStrategy create = new CreateStrategy(skillWorkflowExecutor, statusPublisher, siblings);

        this.modeStrategies = Map.of(
            Mode.BUGFIX, bugfix, Mode.REFACTOR, refactor, Mode.EXTENSION, extension,
            Mode.CREATE, create, Mode.DOCUMENT, document);

        this.statusPublisher = statusPublisher;
        this.statusContext = statusContext;
    }

    /**
     * Initialize the orchestrator with both workspace and project folder.
     * Tools (FileSystemTool, ShellTool) operate on the project folder.
     *
     * @param workspace     the workspace directory (for creating/scanning projects)
     * @param projectFolder the project folder (for code gen, file ops, build, tests)
     */
    public void initialize(String workspace, String projectFolder) {
        context.initialize(workspace, projectFolder);
    }

    /**
     * Initialize with a single path used for both workspace and project folder.
     * This is a convenience overload for backward compatibility.
     */
    public void initialize(String workspace) {
        context.initialize(workspace, workspace);
    }

    public String getWorkspace() {
        return context.getWorkspace();
    }

    public String getProjectFolder() {
        return context.getProjectFolder();
    }

    public boolean isInitialized() {
        return context.isInitialized();
    }

    /**
     * Auto-detect execution mode from the task and route to the appropriate
     * mode-specific workflow (bugfix, refactor, extension, create, document).
     * Falls back to single-agent execution if mode detection is inconclusive.
     */
    public TaskResult execute(String task) {
        if (!context.isInitialized()) {
            context.initialize(context.determineDefaultWorkspace(), context.determineDefaultProject());
        }

        // Phase 1.5: Use ComplexityRouter to decide if this complex request needs multi-agent workflow
        {
            int complexityScore = ComplexityAnalyzer.score(task);
            Log.infof("ComplexityAnalyzer score for task: %d", complexityScore);

            if (complexityScore >= 6) {
                // Route to multi-agent workflow for complex tasks
                String workflowName = "complex-task";
                Log.infof("ComplexityRouter: heuristic score %d >= 6, routing to multi-agent workflow", complexityScore);
                return skillWorkflowExecutor.executeSkillWorkflow(workflowName,
                    skillWorkflowExecutor.getWorkflowStepsForComplexity(workflowName), task);
            } else if (complexityScore >= 3) {
                // Borderline: consult the router agent
                boolean isComplex = toolLocator.getComplexityRouter()
                    .isComplex(task, context.getCurrentSessionId(), context.getProjectFolder());
                if (isComplex) {
                    String workflowName = "complex-task";
                    Log.infof("ComplexityRouter: router agent classified as complex, routing to multi-agent workflow");
                    return skillWorkflowExecutor.executeSkillWorkflow(workflowName,
                        skillWorkflowExecutor.getWorkflowStepsForComplexity(workflowName), task);
                }
            }
        }

        // Phase 2: Detect the mode from the task content
        Mode mode = IntentDetector.detect(task);
        if (mode != null) {
            return execute(task, mode.name().toLowerCase());
        }

        // Fallback: single-agent execution
        long workflowStart = System.currentTimeMillis();
        statusContext.activate();
        statusPublisher.workflowStarted("AUTO", task);
        try {
            return singleAgent.execute(task, workflowStart);
        } finally {
            statusContext.deactivate();
        }
    }

    public TaskResult execute(String task, WorkflowType workflowType) {
        if (!context.isInitialized()) {
            context.initialize(context.determineDefaultWorkspace(), context.determineDefaultProject());
        }

        long workflowStart = System.currentTimeMillis();

        try {
            statusContext.activate();
            statusPublisher.workflowStarted(workflowType.name(), task);

            // SINGLE path: singleAgent publishes workflowCompleted itself
            return switch (workflowType) {
                case SINGLE -> singleAgent.execute(task, workflowStart);
                case SEQUENTIAL, PARALLEL -> {
                    TaskResult r = skillWorkflowExecutor.executeMultiAgent(task);
                    statusPublisher.workflowCompleted(r.status().name(), System.currentTimeMillis() - workflowStart);
                    yield r;
                }
            };
        } finally {
            statusContext.deactivate();
        }
    }

    /**
     * Execute a task in a specific mode (bugfix, refactor, extend, create, document).
     * If mode is null or unparseable, falls back to auto-detection from task text.
     */
    public TaskResult execute(String task, String modeStr) {
        Mode mode = IntentDetector.fromFlag(modeStr);
        if (mode == null) {
            mode = IntentDetector.detect(task);
        }

        if (!context.isInitialized()) {
            context.initialize(context.determineDefaultWorkspace(), context.determineDefaultProject());
        }

        long workflowStart = System.currentTimeMillis();

        try {
            statusContext.activate();
            statusPublisher.workflowStarted(mode.name(), task);

            return modeStrategies.get(mode).execute(task, workflowStart);
        } finally {
            statusContext.deactivate();
        }
    }

    /**
     * Public entry point for invoking a named skill's multi-agent workflow.
     * Driven by the LLM in-context via the {@code invoke_skill_workflow} tool.
     */
    public TaskResult runSkillWorkflow(String skillName, String task) {
        return skillWorkflowExecutor.runSkillWorkflow(skillName, task);
    }
}
