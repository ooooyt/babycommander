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

    @JsonProperty("header")
    @com.fasterxml.jackson.annotation.JsonSetter(nulls = com.fasterxml.jackson.annotation.Nulls.SKIP)
    public HeaderConfig header = new HeaderConfig();

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

        /**
         * Optional dedicated embedding model name (e.g. {@code text-embedding-v4},
         * {@code nomic-embed-text}). When set on an OpenAI-compatible or Ollama
         * provider, EmbeddingService uses it to generate semantic-search vectors.
         * The chat {@link #modelName} is never used for embeddings.
         */
        @JsonProperty("embeddingModel")
        public String embeddingModel;

        @JsonProperty("temperature")
        public double temperature = 0.7;

        @JsonProperty("maxTokens")
        public int maxTokens = 8192;

        @JsonProperty("timeoutSeconds")
        public int timeoutSeconds = 300;

        /**
         * Number of LLM request retries per round-trip (0 disables retries).
         * LangChain4j defaults to 2; with long read timeouts that can leave
         * callers hanging for many minutes after a failure.
         */
        @JsonProperty("maxRetries")
        public int maxRetries = 2;
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

        /**
         * Controls automatic trust of operations confined to the current
         * project folder. One of {@code auto} (default), {@code always}, or
         * {@code strict}. See {@link TrustProjectMode}.
         */
        @JsonProperty("trustProject")
        public String trustProject = "auto";

        @JsonProperty("rules")
        public List<HookRule> rules = new java.util.ArrayList<>();

        @JsonProperty("patterns")
        @com.fasterxml.jackson.annotation.JsonSetter(nulls = com.fasterxml.jackson.annotation.Nulls.SKIP)
        public Map<String, PatternGroup> patterns = new java.util.HashMap<>();
    }

    /**
     * Trust mode for operations confined to the current project folder.
     * <ul>
     *   <li>{@link #AUTO} — auto-allow in-scope {@code ask_once} operations,
     *   prompt only for out-of-scope or {@code dangerous} ones.</li>
     *   <li>{@link #ALWAYS} — never prompt for in-scope {@code ask_once}
     *   operations (fully autonomous).</li>
     *   <li>{@link #STRICT} — prompt for every {@code ask_once} operation.</li>
     * </ul>
     */
    public enum TrustProjectMode {
        AUTO,
        ALWAYS,
        STRICT;

        /**
         * Parse a string value into a {@code TrustProjectMode}, defaulting to
         * {@link #AUTO} for null, blank, or unrecognized values.
         */
        public static TrustProjectMode fromString(String value) {
            if (value == null || value.isBlank()) return AUTO;
            try {
                return TrustProjectMode.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return AUTO;
            }
        }
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

    public static class HeaderConfig {
        @JsonProperty("session")
        @com.fasterxml.jackson.annotation.JsonSetter(nulls = com.fasterxml.jackson.annotation.Nulls.SKIP)
        public SessionHeaderConfig session = new SessionHeaderConfig();

        public static class SessionHeaderConfig {
            @JsonProperty("id")
            @com.fasterxml.jackson.annotation.JsonSetter(nulls = com.fasterxml.jackson.annotation.Nulls.SKIP)
            public IdHeaderConfig id = new IdHeaderConfig();

            public static class IdHeaderConfig {
                /**
                 * HTTP header name carrying the per-app-run session id on every
                 * LLM request. Defaults to {@code x-opencode-session}.
                 */
                @JsonProperty("key")
                public String key = "x-opencode-session";
            }
        }
    }
}
