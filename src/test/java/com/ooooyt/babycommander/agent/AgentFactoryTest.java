package com.ooooyt.babycommander.agent;

import com.ooooyt.babycommander.CodeGenLifecycle;
import com.ooooyt.babycommander.config.AgentConfig;
import com.ooooyt.babycommander.config.AgentConfig.ProviderConfig;
import com.ooooyt.babycommander.config.AgentConfig.AgentRoleConfig;
import com.ooooyt.babycommander.config.YamlConfigLoader;
import com.ooooyt.babycommander.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AgentFactoryTest {

    @TempDir
    Path tempDir;

    private CodeGenLifecycle createNoOpLifecycle() {
        return new CodeGenLifecycle() {
            @Override
            public synchronized void initialize() {
                // no-op for tests
            }
        };
    }

    @Test
    void testCreateAgentReturnsContext() {
        AgentConfig config = createMockConfig();
        ToolRegistry toolRegistry = new ToolRegistry();

        AgentFactory factory = new AgentFactory(createMockLoader(config), toolRegistry, null, createNoOpLifecycle(), null);
        AgentContext ctx = factory.createAgent("orchestrator", "session-1", tempDir.toString());

        assertNotNull(ctx);
        assertEquals("session-1", ctx.sessionId());
        assertEquals(tempDir.toString(), ctx.projectFolder());
        assertEquals("orchestrator", ctx.role());
        assertNotNull(ctx.agent());
        assertTrue(factory.getActiveSessions().contains("session-1"));
    }

    @Test
    void testIsolatedSessions() {
        AgentConfig config = createMockConfig();
        ToolRegistry toolRegistry = new ToolRegistry();

        AgentFactory factory = new AgentFactory(createMockLoader(config), toolRegistry, null, createNoOpLifecycle(), null);
        AgentContext ctx1 = factory.createAgent("orchestrator", "session-1", tempDir.toString());
        AgentContext ctx2 = factory.createAgent("writer", "session-2", tempDir.toString());

        assertNotEquals(ctx1.sessionId(), ctx2.sessionId());
        assertNotEquals(ctx1.role(), ctx2.role());
        assertEquals(2, factory.getActiveAgentCount());
    }

    @Test
    void testDuplicateSessionThrows() {
        AgentConfig config = createMockConfig();
        ToolRegistry toolRegistry = new ToolRegistry();

        AgentFactory factory = new AgentFactory(createMockLoader(config), toolRegistry, null, createNoOpLifecycle(), null);
        factory.createAgent("orchestrator", "dup-session", tempDir.toString());

        assertThrows(IllegalArgumentException.class, () ->
                factory.createAgent("writer", "dup-session", tempDir.toString()));
    }

    @Test
    void testDisposeAgent() {
        AgentConfig config = createMockConfig();
        ToolRegistry toolRegistry = new ToolRegistry();

        AgentFactory factory = new AgentFactory(createMockLoader(config), toolRegistry, null, createNoOpLifecycle(), null);
        factory.createAgent("orchestrator", "to-dispose", tempDir.toString());
        factory.disposeAgent("to-dispose");

        assertEquals(0, factory.getActiveAgentCount());
        assertFalse(factory.getActiveSessions().contains("to-dispose"));
    }

    @Test
    void testDisposeNonExistentThrows() {
        AgentFactory factory = new AgentFactory(createMockLoader(createMockConfig()), new ToolRegistry(), null, createNoOpLifecycle(), null);
        assertThrows(IllegalArgumentException.class, () -> factory.disposeAgent("nonexistent"));
    }

    @Test
    void testUnknownRoleThrows() {
        AgentConfig config = createMockConfig();
        ToolRegistry toolRegistry = new ToolRegistry();
        AgentFactory factory = new AgentFactory(createMockLoader(config), toolRegistry, null, createNoOpLifecycle(), null);

        assertThrows(IllegalArgumentException.class, () ->
                factory.createAgent("unknown-role", "s1", tempDir.toString()));
    }

    @Test
    void testProviderFallbackToDefaults() {
        AgentConfig config = createMockConfig();
        AgentRoleConfig writerRole = config.agentRoles.get("writer");
        writerRole.provider = null;

        ToolRegistry toolRegistry = new ToolRegistry();
        AgentFactory factory = new AgentFactory(createMockLoader(config), toolRegistry, null, createNoOpLifecycle(), null);

        AgentContext ctx = factory.createAgent("writer", "fallback-session", tempDir.toString());
        assertNotNull(ctx);
        assertEquals("writer", ctx.role());
        assertEquals("fallback-session", ctx.sessionId());
    }

    @Test
    void testAgentContextHasToolsPopulated() {
        AgentConfig config = createMockConfig();
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.registerTool(new Object());

        AgentFactory factory = new AgentFactory(createMockLoader(config), toolRegistry, null, createNoOpLifecycle(), null);
        AgentContext ctx = factory.createAgent("orchestrator", "tools-session", tempDir.toString());

        assertNotNull(ctx.tools());
        assertEquals(1, ctx.tools().size()); // registered tool
    }

    @Test
    void testGetActiveSessionsReturnsImmutableList() {
        AgentConfig config = createMockConfig();
        ToolRegistry toolRegistry = new ToolRegistry();

        AgentFactory factory = new AgentFactory(createMockLoader(config), toolRegistry, null, createNoOpLifecycle(), null);
        assertThrows(UnsupportedOperationException.class, () ->
                factory.getActiveSessions().add("hacker-session"));
    }

    @Test
    void testNullSystemPromptThrows() {
        AgentConfig config = createMockConfig();
        config.agentRoles.get("orchestrator").systemPrompt = null;

        ToolRegistry toolRegistry = new ToolRegistry();
        AgentFactory factory = new AgentFactory(createMockLoader(config), toolRegistry, null, createNoOpLifecycle(), null);

        assertThrows(IllegalArgumentException.class, () ->
                factory.createAgent("orchestrator", "null-prompt", tempDir.toString()));
    }

    private AgentConfig createMockConfig() {
        AgentConfig config = new AgentConfig();
        config.defaultModel = "gpt-4o";
        config.agentDefaults = new AgentConfig.AgentDefaults();
        config.agentDefaults.provider = "openai";
        config.agentDefaults.maxMessagesInMemory = 50;
        config.agentDefaults.temperature = 0.7;

        config.providers = new java.util.HashMap<>();
        ProviderConfig openai = new ProviderConfig();
        openai.type = "openai";
        openai.baseUrl = "https://api.openai.com/v1";
        openai.apiKey = "test-key";
        openai.modelName = "gpt-4o";
        openai.temperature = 0.7;
        openai.maxTokens = 4096;
        openai.timeoutSeconds = 60;
        config.providers.put("openai", openai);

        config.agentRoles = new java.util.HashMap<>();
        String[] roles = {"orchestrator", "planner", "writer", "reviewer", "tester"};
        for (String r : roles) {
            AgentRoleConfig rc = new AgentRoleConfig();
            rc.provider = "openai";
            rc.systemPrompt = "You are the " + r + " agent.";
            config.agentRoles.put(r, rc);
        }

        return config;
    }

    private YamlConfigLoader createMockLoader(AgentConfig config) {
        return new YamlConfigLoader("agents.yaml") {
            @Override
            public AgentConfig getConfig() {
                return config;
            }
        };
    }
}
