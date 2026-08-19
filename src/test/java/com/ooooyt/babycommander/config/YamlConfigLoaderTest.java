package com.ooooyt.babycommander.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class YamlConfigLoaderTest {

    @Test
    void testLoadConfigFromResources() {
        YamlConfigLoader loader = new YamlConfigLoader("agents.yaml");
        AgentConfig config = loader.getConfig();
        assertNotNull(config);
        assertNotNull(config.defaultModel);
        assertNotNull(config.workspaceRoot);
        assertNotNull(config.providers);
        assertTrue(config.providers.containsKey("openai"));
        assertNotNull(config.agentRoles);
        assertTrue(config.agentRoles.containsKey("orchestrator"));
    }
}
