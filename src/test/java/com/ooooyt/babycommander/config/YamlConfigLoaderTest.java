package com.ooooyt.babycommander.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class YamlConfigLoaderTest {

    @BeforeEach
    @AfterEach
    void reset() {
        Env.resetForTests();
        System.clearProperty("BCMD_DEFAULT_MODEL");
        System.clearProperty("BCMD_MAX_TOKENS");
        System.clearProperty("BCMD_OPENAI_API_KEY");
        System.clearProperty("BCMD_OPENAI_MODEL");
        System.clearProperty("BCMD_OCDEEPSEEK_MODEL");
        System.clearProperty("BCMD_DEEPSEEK_MODEL");
        System.clearProperty("OPENAI_API_KEY");
        System.clearProperty("BABY_COMMANDER_DEFAULT_MODEL");
    }

    private AgentConfig load() {
        return new YamlConfigLoader("agents.yaml").getConfig();
    }

    @Test
    void testLoadConfigFromResources() {
        AgentConfig config = load();
        assertNotNull(config);
        assertNotNull(config.defaultModel);
        assertNotNull(config.workspaceRoot);
        assertNotNull(config.providers);
        assertTrue(config.providers.containsKey("openai"));
        assertNotNull(config.agentRoles);
        assertTrue(config.agentRoles.containsKey("orchestrator"));
    }

    @Test
    void emptyEnvVarFallsBackToDefault() {
        System.setProperty("BCMD_MAX_TOKENS", "");
        AgentConfig config = load();
        assertEquals(30720, config.providers.get("openai").maxTokens);
    }

    @Test
    void envVarOverridesYamlDefault() {
        System.setProperty("BCMD_OPENAI_MODEL", "glm-4.6");
        AgentConfig config = load();
        assertEquals("glm-4.6", config.providers.get("openai").modelName);
    }

    @Test
    void deprecatedAliasStillWorks() {
        System.setProperty("BABY_COMMANDER_DEFAULT_MODEL", "deepseek");
        AgentConfig config = load();
        assertEquals("deepseek", config.defaultModel);
    }

    @Test
    void ocdeepseekIsDecoupledFromDeepseek() {
        System.setProperty("BCMD_OCDEEPSEEK_MODEL", "oc-model-x");
        System.setProperty("BCMD_DEEPSEEK_MODEL", "ds-model-y");
        AgentConfig config = load();
        assertEquals("oc-model-x", config.providers.get("ocdeepseek").modelName);
        assertEquals("ds-model-y", config.providers.get("deepseek").modelName);
    }

    @Test
    void apiKeyFallsBackToConventionalName() {
        System.setProperty("OPENAI_API_KEY", "sk-conventional");
        AgentConfig config = load();
        assertEquals("sk-conventional", config.providers.get("openai").apiKey);
    }

    @Test
    void apiKeyPrimaryWinsOverConventional() {
        System.setProperty("BCMD_OPENAI_API_KEY", "sk-primary");
        System.setProperty("OPENAI_API_KEY", "sk-conventional");
        AgentConfig config = load();
        assertEquals("sk-primary", config.providers.get("openai").apiKey);
    }

    @Test
    void globalMaxRetriesFallsBackForAllProviders() {
        System.setProperty("BCMD_MAX_RETRIES", "5");
        AgentConfig config = load();
        assertEquals(5, config.providers.get("openai").maxRetries);
        assertEquals(5, config.providers.get("deepseek").maxRetries);
    }
}