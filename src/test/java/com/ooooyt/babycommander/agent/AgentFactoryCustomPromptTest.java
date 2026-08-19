package com.ooooyt.babycommander.agent;

import com.ooooyt.babycommander.CodeGenLifecycle;
import com.ooooyt.babycommander.config.AgentConfig;
import com.ooooyt.babycommander.config.YamlConfigLoader;
import com.ooooyt.babycommander.config.AgentConfig.ProviderConfig;
import com.ooooyt.babycommander.config.AgentConfig.AgentRoleConfig;
import com.ooooyt.babycommander.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AgentFactoryCustomPromptTest {

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
    void createWithValidCustomPrompt() {
        AgentConfig config = createMockConfig();
        ToolRegistry toolRegistry = new ToolRegistry();
        AgentFactory factory = new AgentFactory(createMockLoader(config), toolRegistry, null, createNoOpLifecycle(), null);

        AgentContext ctx = factory.createAgentWithCustomPrompt(
            "You are a helpful assistant.", "test-session-1", tempDir.toString()
        );
        assertNotNull(ctx.sessionId());
        assertEquals("test-session-1", ctx.sessionId());

        factory.disposeAgent("test-session-1");
    }

    @Test
    void throwsOnNullPrompt() {
        AgentConfig config = createMockConfig();
        ToolRegistry toolRegistry = new ToolRegistry();
        AgentFactory factory = new AgentFactory(createMockLoader(config), toolRegistry, null, createNoOpLifecycle(), null);

        assertThrows(IllegalArgumentException.class, () ->
            factory.createAgentWithCustomPrompt(null, "test-null", tempDir.toString())
        );
    }

    @Test
    void throwsOnBlankPrompt() {
        AgentConfig config = createMockConfig();
        ToolRegistry toolRegistry = new ToolRegistry();
        AgentFactory factory = new AgentFactory(createMockLoader(config), toolRegistry, null, createNoOpLifecycle(), null);

        assertThrows(IllegalArgumentException.class, () ->
            factory.createAgentWithCustomPrompt("   ", "test-blank", tempDir.toString())
        );
    }

    @Test
    void throwsOnDuplicateSession() {
        AgentConfig config = createMockConfig();
        ToolRegistry toolRegistry = new ToolRegistry();
        AgentFactory factory = new AgentFactory(createMockLoader(config), toolRegistry, null, createNoOpLifecycle(), null);

        factory.createAgentWithCustomPrompt(
            "test", "test-dup", tempDir.toString()
        );
        assertThrows(IllegalArgumentException.class, () ->
            factory.createAgentWithCustomPrompt(
                "test", "test-dup", tempDir.toString()
            )
        );
        factory.disposeAgent("test-dup");
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
