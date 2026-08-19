package com.ooooyt.babycommander.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class AgentConfig {

    @JsonProperty("defaultModel")
    public String defaultModel;

    @JsonProperty("workspaceRoot")
    public String workspaceRoot;

    @JsonProperty("skipConfirmWorkspace")
    public boolean skipConfirmWorkspace = false;

    @JsonProperty("skillsDir")
    public String skillsDir = "~/.babycommander/skills";

    @JsonProperty("agentDefaults")
    public AgentDefaults agentDefaults = new AgentDefaults();
    @com.fasterxml.jackson.annotation.JsonSetter(nulls = com.fasterxml.jackson.annotation.Nulls.SKIP)
    public Map<String, ProviderConfig> providers = new java.util.HashMap<>();

    @JsonProperty("mcp")
    @com.fasterxml.jackson.annotation.JsonSetter(nulls = com.fasterxml.jackson.annotation.Nulls.SKIP)
    public McpConfig mcp = new McpConfig();

    @JsonProperty("agentRoles")
    @com.fasterxml.jackson.annotation.JsonSetter(nulls = com.fasterxml.jackson.annotation.Nulls.SKIP)
    public Map<String, AgentRoleConfig> agentRoles = new java.util.HashMap<>();

    @JsonProperty("hooks")
    @com.fasterxml.jackson.annotation.JsonSetter(nulls = com.fasterxml.jackson.annotation.Nulls.SKIP)
    public HookConfig hooks = new HookConfig();

    @JsonProperty("showToolCallPairs")
    public boolean showToolCallPairs = false;

    public static class AgentDefaults {
        @JsonProperty("provider")
        public String provider = "openai";

        @JsonProperty("maxMessagesInMemory")
        public int maxMessagesInMemory = 200;

        @JsonProperty("maxToolCalls")
        public int maxToolCalls = 800;

        @JsonProperty("temperature")
        public double temperature = 0.7;

        @JsonProperty("toolOutputTruncationKB")
        public int toolOutputTruncationKB = 20;

        @JsonProperty("maxTokensInMemory")
        public int maxTokensInMemory = 60000;
    }

    public static class ProviderConfig {
        @JsonProperty("type")
        public String type;

        @JsonProperty("baseUrl")
        public String baseUrl;

        @JsonProperty("apiKey")
        public String apiKey;

        @JsonProperty("modelName")
        public String modelName;

        @JsonProperty("temperature")
        public double temperature = 0.7;

        @JsonProperty("maxTokens")
        public int maxTokens = 8192;

        @JsonProperty("timeoutSeconds")
        public int timeoutSeconds = 300;
    }

    public static class McpConfig {
        @JsonProperty("servers")
        public Map<String, McpServerConfig> servers;
    }

    public static class McpServerConfig {
        @JsonProperty("url")
        public String url;

        @JsonProperty("transport")
        public String transport = "stdio";
    }

    public static class AgentRoleConfig {
        @JsonProperty("provider")
        public String provider;

        @JsonProperty("systemPrompt")
        public String systemPrompt;

        @JsonProperty("temperature")
        public Double temperature;
    }

    public static class HookConfig {
        @JsonProperty("enabled")
        public boolean enabled = true;

        @JsonProperty("rules")
        public List<HookRule> rules = new java.util.ArrayList<>();

        @JsonProperty("patterns")
        @com.fasterxml.jackson.annotation.JsonSetter(nulls = com.fasterxml.jackson.annotation.Nulls.SKIP)
        public Map<String, PatternGroup> patterns = new java.util.HashMap<>();
    }

    public static class HookRule {
        @JsonProperty("tool")
        public String tool;

        @JsonProperty("level")
        public String level = "safe";
    }

    public static class PatternGroup {
        @JsonProperty("tool")
        public String tool;

        @JsonProperty("level")
        public String level = "ask_once";

        @JsonProperty("matches")
        public List<String> matches = new java.util.ArrayList<>();

        @JsonIgnore
        public transient java.util.List<java.util.regex.Pattern> compiledPatterns = new java.util.ArrayList<>();
    }
}
