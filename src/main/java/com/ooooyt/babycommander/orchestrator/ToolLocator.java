package com.ooooyt.babycommander.orchestrator;

import com.ooooyt.babycommander.agent.AgentFactory;
import com.ooooyt.babycommander.intent.ComplexityRouter;
import com.ooooyt.babycommander.tool.PlanTool;
import com.ooooyt.babycommander.tool.ShellTool;
import com.ooooyt.babycommander.tool.ToolRegistry;

import io.quarkus.logging.Log;

/**
 * Lazily resolves and caches shared tools (ShellTool, PlanTool,
 * ComplexityRouter) from the {@link ToolRegistry}. Tools are registered by
 * {@code CodeGenLifecycle.initialize()} on first agent creation, so lookups
 * are deferred until then.
 */
public class ToolLocator {

    private final ToolRegistry toolRegistry;
    private final AgentFactory agentFactory;

    /** Lazily resolved ShellTool — tools may not be registered yet at construction time. */
    private ShellTool shellTool;
    /** Lazily resolved PlanTool — registered after CodeGenLifecycle.initialize(). */
    private PlanTool planTool;
    private ComplexityRouter complexityRouter;

    public ToolLocator(ToolRegistry toolRegistry, AgentFactory agentFactory) {
        this.toolRegistry = toolRegistry;
        this.agentFactory = agentFactory;
    }

    public ShellTool getShellTool() {
        if (shellTool == null) {
            shellTool = findShellTool(toolRegistry);
        }
        return shellTool;
    }

    public PlanTool getPlanTool() {
        if (planTool == null) {
            for (Object tool : toolRegistry.getAllTools()) {
                if (tool instanceof PlanTool pt) {
                    planTool = pt;
                    return planTool;
                }
            }
            Log.warn("PlanTool not yet registered in ToolRegistry");
        }
        return planTool;
    }

    public ComplexityRouter getComplexityRouter() {
        if (complexityRouter == null) {
            for (Object tool : toolRegistry.getAllTools()) {
                if (tool instanceof ComplexityRouter cr) {
                    complexityRouter = cr;
                    return complexityRouter;
                }
            }
            complexityRouter = new ComplexityRouter(agentFactory);
        }
        return complexityRouter;
    }

    private ShellTool findShellTool(ToolRegistry registry) {
        for (Object tool : registry.getAllTools()) {
            if (tool instanceof ShellTool st) return st;
        }
        throw new IllegalStateException("ShellTool not registered");
    }
}
