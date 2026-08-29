package com.ooooyt.babycommander.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;
import java.io.File;
import java.io.InputStream;
import java.util.Map;
import io.quarkus.logging.Log;

/**
 * Loads agent configuration from agents.yaml and hooks configuration from hooks.yaml.
 *
 * <p>The hooks configuration has been extracted into a separate YAML file (hooks.yaml)
 * to keep agents.yaml focused on agent/provider configuration.</p>
 *
 * <p>Environment overrides are applied <em>after</em> parsing plain YAML defaults,
 * through {@link Env}/{@link EnvKeys} (typed, empty→default semantics, deprecated
 * aliases with warnings, {@code ~/.babycommander/.env} support). See
 * {@code docs/env-vars-improvement.md}.</p>
 */
@ApplicationScoped
public class YamlConfigLoader {

    private AgentConfig config;
    private final String configPath;
    private static final String HOOKS_CONFIG_PATH = "hooks.yaml";

    public YamlConfigLoader() {
        this("agents.yaml");
    }

    public YamlConfigLoader(String configPath) {
        this.configPath = configPath;
    }

    @Produces
    @ApplicationScoped
    AgentConfig produceConfig() {
        Log.debug("Producing AgentConfig");
        return getConfig();
    }

    public synchronized AgentConfig getConfig() {
        if (config == null) {
            Env.loadDotEnv();
            Env.warnIfDotEnvInProject();
            Log.infof("Loading config from: %s (classloader: %s)", configPath, getClass().getClassLoader().getClass().getName());
            config = loadConfig();
            loadHooksConfig(config);
            compilePatterns(config);
            validate(config);
            logEffectiveConfig(config);
        }
        return config;
    }

    private AgentConfig loadConfig() {
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            InputStream input = getClass().getClassLoader().getResourceAsStream(configPath);
            if (input == null) {
                input = getClass().getResourceAsStream("/agents.yaml");
            }
            if (input == null) {
                File file = new File(configPath);
                if (file.exists()) {
                    AgentConfig cfg = mapper.readValue(file, AgentConfig.class);
                    applyEnvOverrides(cfg);
                    return cfg;
                }
                throw new RuntimeException(I18n.tr(MessageKey.CONFIG_FILE_NOT_FOUND, configPath));
            }
            AgentConfig cfg = mapper.readValue(input, AgentConfig.class);
            applyEnvOverrides(cfg);
            return cfg;
        } catch (Exception e) {
            throw new RuntimeException(I18n.tr(MessageKey.CONFIG_LOAD_FAILED), e);
        }
    }

    /**
     * Loads hooks configuration from hooks.yaml and merges it into the AgentConfig.
     * If hooks.yaml cannot be found, hooks remain at their default values.
     */
    private void loadHooksConfig(AgentConfig cfg) {
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            AgentConfig.HookConfig hooksConfig = null;

            // Try classpath resource first
            InputStream input = getClass().getClassLoader().getResourceAsStream(HOOKS_CONFIG_PATH);
            if (input == null) {
                input = getClass().getResourceAsStream("/" + HOOKS_CONFIG_PATH);
            }

            if (input != null) {
                hooksConfig = mapper.readValue(input, AgentConfig.HookConfig.class);
                Log.infof("Loaded hooks configuration from classpath: %s", HOOKS_CONFIG_PATH);
            } else {
                // Try filesystem
                File file = new File(HOOKS_CONFIG_PATH);
                if (file.exists()) {
                    hooksConfig = mapper.readValue(file, AgentConfig.HookConfig.class);
                    Log.infof("Loaded hooks configuration from file: %s", file.getAbsolutePath());
                } else {
                    Log.warnf("Hooks configuration file not found: %s, using defaults", HOOKS_CONFIG_PATH);
                }
            }

            if (hooksConfig != null) {
                cfg.hooks = hooksConfig;
            }
        } catch (Exception e) {
            Log.warnf("Failed to load hooks configuration from %s: %s. Using defaults.", HOOKS_CONFIG_PATH, e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Environment overrides (typed, empty→default, deprecated aliases)
    // ------------------------------------------------------------------

    private void applyEnvOverrides(AgentConfig cfg) {
        cfg.defaultModel = Env.getWithAliases(EnvKeys.DEFAULT_MODEL,
                EnvKeys.DEPRECATED_ALIASES.get(EnvKeys.DEFAULT_MODEL), cfg.defaultModel);
        cfg.workspaceRoot = Env.getWithAliases(EnvKeys.WORKSPACE_ROOT,
                EnvKeys.DEPRECATED_ALIASES.get(EnvKeys.WORKSPACE_ROOT), cfg.workspaceRoot);
        cfg.skipConfirmWorkspace = Env.getBooleanWithAliases(EnvKeys.SKIP_CONFIRM_WORKSPACE,
                EnvKeys.DEPRECATED_ALIASES.get(EnvKeys.SKIP_CONFIRM_WORKSPACE), cfg.skipConfirmWorkspace);

        if (cfg.agentDefaults != null) {
            AgentConfig.AgentDefaults d = cfg.agentDefaults;
            d.maxMessagesInMemory = Env.getIntWithAliases(EnvKeys.MAX_MESSAGES_IN_MEMORY,
                    EnvKeys.DEPRECATED_ALIASES.get(EnvKeys.MAX_MESSAGES_IN_MEMORY), d.maxMessagesInMemory);
            d.maxToolCalls = Env.getIntWithAliases(EnvKeys.MAX_TOOL_CALLS,
                    EnvKeys.DEPRECATED_ALIASES.get(EnvKeys.MAX_TOOL_CALLS), d.maxToolCalls);
            d.temperature = Env.getDoubleWithAliases(EnvKeys.DEFAULT_TEMPERATURE,
                    EnvKeys.DEPRECATED_ALIASES.get(EnvKeys.DEFAULT_TEMPERATURE), d.temperature);
            d.toolOutputTruncationKB = Env.getIntWithAliases(EnvKeys.TOOL_OUTPUT_TRUNCATION_KB,
                    EnvKeys.DEPRECATED_ALIASES.get(EnvKeys.TOOL_OUTPUT_TRUNCATION_KB), d.toolOutputTruncationKB);
            d.maxTokensInMemory = Env.getIntWithAliases(EnvKeys.MAX_TOKENS_IN_MEMORY,
                    EnvKeys.DEPRECATED_ALIASES.get(EnvKeys.MAX_TOKENS_IN_MEMORY), d.maxTokensInMemory);
        }

        if (cfg.providers != null) {
            cfg.providers.forEach(this::applyProviderOverrides);
        }

        if (cfg.hooks != null && cfg.hooks.trustProject != null) {
            cfg.hooks.trustProject = Env.getWithAliases(EnvKeys.TRUST_PROJECT,
                    EnvKeys.DEPRECATED_ALIASES.get(EnvKeys.TRUST_PROJECT), cfg.hooks.trustProject);
        }
    }

    private void applyProviderOverrides(String name, AgentConfig.ProviderConfig p) {
        String upper = name.toUpperCase();
        p.baseUrl = Env.getWithAliases(EnvKeys.provider(name, EnvKeys.SUFFIX_BASE_URL),
                EnvKeys.DEPRECATED_ALIASES.get(EnvKeys.provider(name, EnvKeys.SUFFIX_BASE_URL)), p.baseUrl);
        p.modelName = Env.getWithAliases(EnvKeys.provider(name, EnvKeys.SUFFIX_MODEL),
                EnvKeys.DEPRECATED_ALIASES.get(EnvKeys.provider(name, EnvKeys.SUFFIX_MODEL)), p.modelName);
        p.temperature = Env.getDoubleWithAliases(EnvKeys.provider(name, EnvKeys.SUFFIX_TEMPERATURE),
                EnvKeys.DEPRECATED_ALIASES.get(EnvKeys.provider(name, EnvKeys.SUFFIX_TEMPERATURE)), p.temperature);
        p.maxTokens = Env.getIntWithAliases(EnvKeys.provider(name, EnvKeys.SUFFIX_MAX_TOKENS),
                EnvKeys.DEPRECATED_ALIASES.get(EnvKeys.provider(name, EnvKeys.SUFFIX_MAX_TOKENS)), p.maxTokens);
        p.timeoutSeconds = Env.getIntWithAliases(EnvKeys.provider(name, EnvKeys.SUFFIX_TIMEOUT_SECONDS),
                EnvKeys.DEPRECATED_ALIASES.get(EnvKeys.provider(name, EnvKeys.SUFFIX_TIMEOUT_SECONDS)), p.timeoutSeconds);
        p.maxRetries = Env.getIntWithAliases(EnvKeys.provider(name, EnvKeys.SUFFIX_MAX_RETRIES),
                new String[]{EnvKeys.MAX_RETRIES, "BABY_COMMANDER_MAX_RETRIES"}, p.maxRetries);
        p.embeddingModel = Env.getWithAliases(EnvKeys.provider(name, EnvKeys.SUFFIX_EMBEDDING_MODEL),
                EnvKeys.DEPRECATED_ALIASES.get(EnvKeys.provider(name, EnvKeys.SUFFIX_EMBEDDING_MODEL)), p.embeddingModel);
        p.apiKey = resolveApiKey(name, p.apiKey);
    }

    /**
     * API key resolution: {@code BCMD_<PROVIDER>_API_KEY} → conventional fallback
     * (e.g. {@code OPENAI_API_KEY}) → YAML value (usually empty).
     */
    private String resolveApiKey(String providerName, String yamlValue) {
        String primary = EnvKeys.provider(providerName, EnvKeys.SUFFIX_API_KEY);
        String v = Env.get(primary);
        if (v != null) {
            return v;
        }
        String conventional = EnvKeys.API_KEY_FALLBACKS.get(providerName);
        if (conventional != null) {
            v = Env.get(conventional);
            if (v != null) {
                return v;
            }
        }
        return yamlValue;
    }

    // ------------------------------------------------------------------
    // Validation & diagnostics
    // ------------------------------------------------------------------

    private void validate(AgentConfig cfg) {
        if (cfg.defaultModel == null || cfg.defaultModel.isBlank()) {
            Log.warnf("No defaultModel configured in %s", configPath);
        } else if (cfg.providers == null || !cfg.providers.containsKey(cfg.defaultModel)) {
            Log.warnf("defaultModel '%s' is not defined in providers: %s",
                    cfg.defaultModel, cfg.providers != null ? cfg.providers.keySet() : "none");
        }
        if (cfg.providers != null) {
            cfg.providers.forEach((name, p) -> {
                if (isBlank(p.apiKey) && !isLocalOllama(p)) {
                    Log.warnf("Provider '%s' has no apiKey configured. Set %s or %s.",
                            name, EnvKeys.provider(name, EnvKeys.SUFFIX_API_KEY),
                            EnvKeys.API_KEY_FALLBACKS.getOrDefault(name, "the provider's conventional key"));
                }
                if (p.maxTokens <= 0) {
                    Log.warnf("Provider '%s' has invalid maxTokens=%d (must be > 0)", name, p.maxTokens);
                }
                if (p.temperature < 0.0 || p.temperature > 1.0) {
                    Log.warnf("Provider '%s' has invalid temperature=%s (must be 0..1)", name, p.temperature);
                }
                if (p.timeoutSeconds <= 0) {
                    Log.warnf("Provider '%s' has invalid timeoutSeconds=%d (must be > 0)", name, p.timeoutSeconds);
                }
                if (p.maxRetries < 0) {
                    Log.warnf("Provider '%s' has invalid maxRetries=%d (must be >= 0)", name, p.maxRetries);
                }
            });
        }
    }

    private boolean isLocalOllama(AgentConfig.ProviderConfig p) {
        if (p.baseUrl == null) {
            return false;
        }
        String url = p.baseUrl.toLowerCase();
        return url.contains("localhost") || url.contains("127.0.0.1") || url.contains("0.0.0.0");
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Logs the effective configuration with apiKey values redacted. */
    private void logEffectiveConfig(AgentConfig cfg) {
        Log.infof("Effective config: defaultModel=%s, workspaceRoot=%s, skipConfirmWorkspace=%s",
                cfg.defaultModel, cfg.workspaceRoot, cfg.skipConfirmWorkspace);
        if (cfg.providers != null) {
            cfg.providers.forEach((name, p) -> Log.infof(
                    "  provider[%s] type=%s model=%s baseUrl=%s apiKey=%s maxTokens=%d timeout=%ds retries=%d",
                    name, p.type, p.modelName, p.baseUrl, Env.redact(p.apiKey),
                    p.maxTokens, p.timeoutSeconds, p.maxRetries));
        }
        if (cfg.agentRoles != null) {
            cfg.agentRoles.forEach((role, r) -> Log.infof("  role[%s] provider=%s", role, r.provider));
        }
    }

    private void compilePatterns(AgentConfig cfg) {
        if (cfg == null || cfg.hooks == null || cfg.hooks.patterns == null) {
            return;
        }
        for (AgentConfig.PatternGroup pg : cfg.hooks.patterns.values()) {
            if (pg.matches != null) {
                pg.compiledPatterns.clear();
                for (String regex : pg.matches) {
                    try {
                        pg.compiledPatterns.add(java.util.regex.Pattern.compile(regex));
                    } catch (java.util.regex.PatternSyntaxException e) {
                        Log.warnf("Invalid pattern regex '%s': %s", regex, e.getMessage());
                    }
                }
            }
        }
        Log.infof("Compiled %d pattern groups", cfg.hooks.patterns.size());
    }
}