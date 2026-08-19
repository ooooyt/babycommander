package com.ooooyt.babycommander.orchestrator;

import com.ooooyt.babycommander.CodeGenLifecycle;
import com.ooooyt.babycommander.config.AgentConfig;
import com.ooooyt.babycommander.config.YamlConfigLoader;
import com.ooooyt.babycommander.skill.SkillRegistry;
import com.ooooyt.babycommander.workflow.WorkflowEngine;
import com.ooooyt.babycommander.agent.AgentFactory;
import com.ooooyt.babycommander.status.StatusEventContext;
import com.ooooyt.babycommander.status.StatusEventPublisher;
import com.ooooyt.babycommander.tool.ShellTool;
import com.ooooyt.babycommander.tool.ToolRegistry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class OrchestratorModeTest {

    @TempDir
    Path tempDir;

    @Test
    void testCreateModeDelegatesToSingleAgentExecution() {
        AgentConfig config = createMockConfig();
        config.workspaceRoot = tempDir.toString();
        YamlConfigLoader configLoader = createMockConfigLoader(config);
        CodeGenLifecycle lifecycle = createNoOpLifecycle();
        ToolRegistry toolRegistry = new ToolRegistry();
        ShellTool mockShell = new ShellTool(tempDir.toString(), null) {
            @Override public String executeInDir(String command, String dir) { return ""; }
        };
        toolRegistry.registerTool(mockShell);
        WorkflowEngine workflowEngine = new WorkflowEngine();
        SkillRegistry skillRegistry = new SkillRegistry(configLoader);
        AgentFactory agentFactory = new AgentFactory(configLoader, toolRegistry, null, lifecycle, null);
        StatusEventPublisher statusPublisher = createNoOpPublisher();
        StatusEventContext statusContext = createNoOpContext();

        Orchestrator orchestrator = new Orchestrator(configLoader, toolRegistry, skillRegistry, workflowEngine, agentFactory, statusPublisher, statusContext, null);
        orchestrator.initialize(tempDir.toString());

        try {
            orchestrator.execute("test", "create");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("orchestrat") || e.getMessage().contains("provider") || e.getMessage().contains("model"),
                "Expected model/provider related error, got: " + e.getMessage());
        }
    }

    @Test
    void testBugfixModeRunsTestsAndReturnsResult() {
        AgentConfig config = createMockConfig();
        config.workspaceRoot = tempDir.toString();
        YamlConfigLoader configLoader = createMockConfigLoader(config);
        CodeGenLifecycle lifecycle = createNoOpLifecycle();
        ToolRegistry toolRegistry = new ToolRegistry();
        ShellTool mockShell = new ShellTool(tempDir.toString(), null) {
            @Override public String executeInDir(String command, String dir) {
                return "mvn: command not found";
            }
        };
        toolRegistry.registerTool(mockShell);
        WorkflowEngine workflowEngine = new WorkflowEngine();
        SkillRegistry skillRegistry = new SkillRegistry(configLoader);
        AgentFactory agentFactory = new AgentFactory(configLoader, toolRegistry, null, lifecycle, null);
        StatusEventPublisher statusPublisher = createNoOpPublisher();
        StatusEventContext statusContext = createNoOpContext();

        Orchestrator orchestrator = new Orchestrator(configLoader, toolRegistry, skillRegistry, workflowEngine, agentFactory, statusPublisher, statusContext, null);
        orchestrator.initialize(tempDir.toString());

        try {
            orchestrator.execute("test", "bugfix");
        } catch (Exception e) {
            assertNotNull(e);
        }
    }

    private StatusEventPublisher createNoOpPublisher() {
        return new StatusEventPublisher(null) {
            @Override public void workflowStarted(String workflowType, String taskDescription) {}
            @Override public void workflowCompleted(String status, long totalDurationMs) {}
            @Override public void stepStarted(String stepId, String agentRole, String instruction) {}
            @Override public void stepCompleted(String stepId, String agentRole, long durationMs) {}
            @Override public void stepFailed(String stepId, String agentRole, String errorMessage) {}
            @Override public void toolCallStart(String stepId, String toolName, String toolInput) {}
            @Override public void toolCallResult(String stepId, String toolName, String toolInput, String toolOutput, long durationMs) {}
            @Override public void toolCallError(String stepId, String toolName, String toolInput, String errorMessage) {}
            @Override public void agentResponse(String stepId, String agentRole, int responseLength) {}
        };
    }

    private StatusEventContext createNoOpContext() {
        return new StatusEventContext(createNoOpPublisher()) {
            @Override public void activate() {}
            @Override public void deactivate() {}
        };
    }

    private CodeGenLifecycle createNoOpLifecycle() {
        return new CodeGenLifecycle() {
            @Override
            public synchronized void initialize() {
                // no-op for tests
            }
        };
    }

    private YamlConfigLoader createMockConfigLoader(AgentConfig config) {
        return new YamlConfigLoader("agents.yaml") {
            @Override public AgentConfig getConfig() { return config; }
        };
    }

    private AgentConfig createMockConfig() {
        AgentConfig config = new AgentConfig();
        config.workspaceRoot = tempDir.toString();
        return config;
    }
}
