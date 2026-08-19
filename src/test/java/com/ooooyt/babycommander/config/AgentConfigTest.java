package com.ooooyt.babycommander.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentConfigTest {

    @Test
    void testDefaultValues() {
        AgentConfig config = new AgentConfig();
        assertNull(config.defaultModel);
        assertNull(config.workspaceRoot);
        assertFalse(config.skipConfirmWorkspace);
        assertEquals("~/.babycommander/skills", config.skillsDir);
        assertNotNull(config.agentDefaults);
        assertNotNull(config.providers);
        assertTrue(config.providers.isEmpty());
        assertNotNull(config.mcp);
        assertNotNull(config.agentRoles);
        assertTrue(config.agentRoles.isEmpty());
        assertNotNull(config.hooks);
    }

    @Test
    void testAgentDefaults() {
        AgentConfig.AgentDefaults defaults = new AgentConfig.AgentDefaults();
        assertEquals("openai", defaults.provider);
        assertEquals(200, defaults.maxMessagesInMemory);
        assertEquals(800, defaults.maxToolCalls);
        assertEquals(0.7, defaults.temperature, 0.001);
        assertEquals(20, defaults.toolOutputTruncationKB);
        assertEquals(60000, defaults.maxTokensInMemory);
    }

    @Test
    void testProviderConfig() {
        AgentConfig.ProviderConfig provider = new AgentConfig.ProviderConfig();
        assertNull(provider.type);
        assertNull(provider.baseUrl);
        assertNull(provider.apiKey);
        assertNull(provider.modelName);
        assertEquals(0.7, provider.temperature, 0.001);
        assertEquals(8192, provider.maxTokens);
        assertEquals(300, provider.timeoutSeconds);
    }

    @Test
    void testMcpConfig() {
        AgentConfig.McpConfig mcp = new AgentConfig.McpConfig();
        assertNull(mcp.servers);
    }

    @Test
    void testMcpServerConfig() {
        AgentConfig.McpServerConfig server = new AgentConfig.McpServerConfig();
        assertNull(server.url);
        assertEquals("stdio", server.transport);
    }

    @Test
    void testAgentRoleConfig() {
        AgentConfig.AgentRoleConfig role = new AgentConfig.AgentRoleConfig();
        assertNull(role.provider);
        assertNull(role.systemPrompt);
        assertNull(role.temperature);
    }

    @Test
    void testHookConfig() {
        AgentConfig.HookConfig hook = new AgentConfig.HookConfig();
        assertTrue(hook.enabled);
        assertNotNull(hook.rules);
        assertTrue(hook.rules.isEmpty());
        assertNotNull(hook.patterns);
        assertTrue(hook.patterns.isEmpty());
    }

    @Test
    void testHookRule() {
        AgentConfig.HookRule rule = new AgentConfig.HookRule();
        assertNull(rule.tool);
        assertEquals("safe", rule.level);
    }

    @Test
    void testPatternGroup() {
        AgentConfig.PatternGroup group = new AgentConfig.PatternGroup();
        assertNull(group.tool);
        assertEquals("ask_once", group.level);
        assertNotNull(group.matches);
        assertTrue(group.matches.isEmpty());
        assertNotNull(group.compiledPatterns);
        assertTrue(group.compiledPatterns.isEmpty());
    }

    @Test
    void testJsonSerialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(new AgentConfig());
        assertNotNull(json);
        assertTrue(json.contains("skillsDir"));
        assertTrue(json.contains("~/.babycommander/skills"));
    }

    @Test
    void testJsonDeserialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = """
            {
                "defaultModel": "gpt-4",
                "workspaceRoot": "/projects",
                "skipConfirmWorkspace": true,
                "skillsDir": "/custom/skills",
                "agentDefaults": {
                    "provider": "azure",
                    "maxMessagesInMemory": 100,
                    "maxToolCalls": 500,
                    "temperature": 0.5,
                    "toolOutputTruncationKB": 10,
                    "maxTokensInMemory": 30000
                },
                "providers": {
                    "openai": {
                        "type": "openai",
                        "baseUrl": "https://api.openai.com",
                        "apiKey": "sk-test",
                        "modelName": "gpt-4",
                        "temperature": 0.7,
                        "maxTokens": 4096,
                        "timeoutSeconds": 60
                    }
                },
                "mcp": {
                    "servers": {
                        "local": {
                            "url": "http://localhost:8080",
                            "transport": "http"
                        }
                    }
                },
                "agentRoles": {
                    "writer": {
                        "provider": "openai",
                        "systemPrompt": "You are a writer",
                        "temperature": 0.3
                    }
                },
                "hooks": {
                    "enabled": false,
                    "rules": [{"tool": "shell", "level": "dangerous"}],
                    "patterns": {
                        "rm-rf": {
                            "tool": "shell",
                            "level": "dangerous",
                            "matches": ["rm -rf /"]
                        }
                    }
                }
            }
            """;

        AgentConfig config = mapper.readValue(json, AgentConfig.class);
        assertEquals("gpt-4", config.defaultModel);
        assertEquals("/projects", config.workspaceRoot);
        assertTrue(config.skipConfirmWorkspace);
        assertEquals("/custom/skills", config.skillsDir);

        // AgentDefaults
        assertEquals("azure", config.agentDefaults.provider);
        assertEquals(100, config.agentDefaults.maxMessagesInMemory);
        assertEquals(500, config.agentDefaults.maxToolCalls);
        assertEquals(0.5, config.agentDefaults.temperature, 0.001);
        assertEquals(10, config.agentDefaults.toolOutputTruncationKB);
        assertEquals(30000, config.agentDefaults.maxTokensInMemory);

        // Providers
        AgentConfig.ProviderConfig provider = config.providers.get("openai");
        assertNotNull(provider);
        assertEquals("openai", provider.type);
        assertEquals("https://api.openai.com", provider.baseUrl);
        assertEquals("sk-test", provider.apiKey);
        assertEquals("gpt-4", provider.modelName);
        assertEquals(0.7, provider.temperature, 0.001);
        assertEquals(4096, provider.maxTokens);
        assertEquals(60, provider.timeoutSeconds);

        // MCP
        AgentConfig.McpServerConfig mcpServer = config.mcp.servers.get("local");
        assertNotNull(mcpServer);
        assertEquals("http://localhost:8080", mcpServer.url);
        assertEquals("http", mcpServer.transport);

        // Agent roles
        AgentConfig.AgentRoleConfig writer = config.agentRoles.get("writer");
        assertNotNull(writer);
        assertEquals("openai", writer.provider);
        assertEquals("You are a writer", writer.systemPrompt);
        assertEquals(0.3, writer.temperature, 0.001);

        // Hooks
        assertFalse(config.hooks.enabled);
        assertEquals(1, config.hooks.rules.size());
        assertEquals("shell", config.hooks.rules.get(0).tool);
        assertEquals("dangerous", config.hooks.rules.get(0).level);
        assertNotNull(config.hooks.patterns.get("rm-rf"));
        assertEquals("dangerous", config.hooks.patterns.get("rm-rf").level);
        assertTrue(config.hooks.patterns.get("rm-rf").matches.contains("rm -rf /"));
    }
}
