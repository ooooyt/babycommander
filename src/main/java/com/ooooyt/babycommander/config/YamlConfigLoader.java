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
import io.quarkus.logging.Log;

/**
 * Loads agent configuration from agents.yaml and hooks configuration from hooks.yaml.
 *
 * <p>The hooks configuration has been extracted into a separate YAML file (hooks.yaml)
 * to keep agents.yaml focused on agent/provider configuration.</p>
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
            Log.infof("Loading config from: %s (classloader: %s)", configPath, getClass().getClassLoader().getClass().getName());
            config = loadConfig();
            loadHooksConfig(config);
            compilePatterns(config);
            Log.infof("Config loaded: defaultModel=%s, providers=%d, agentRoles=%d, hooks=%s",
                config.defaultModel,
                config.providers != null ? config.providers.size() : -1,
                config.agentRoles != null ? config.agentRoles.size() : -1,
                config.hooks != null ? "enabled" : "not-loaded");
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
                    String raw = new String(java.nio.file.Files.readAllBytes(file.toPath()),
                            java.nio.charset.StandardCharsets.UTF_8);
                    AgentConfig cfg = mapper.readValue(resolveString(raw), AgentConfig.class);
                    resolveEnvVars(cfg);
                    return cfg;
                }
                throw new RuntimeException(I18n.tr(MessageKey.CONFIG_FILE_NOT_FOUND, configPath));
            }
            String raw = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            AgentConfig cfg = mapper.readValue(resolveString(raw), AgentConfig.class);
            resolveEnvVars(cfg);
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

    private void resolveEnvVars(AgentConfig cfg) {
        if (cfg.providers != null) {
            cfg.providers.values().forEach(p -> resolveEnvVars(p));
        }
        if (cfg.agentRoles != null) {
            cfg.agentRoles.values().forEach(r -> {
                if (r.systemPrompt != null) {
                    r.systemPrompt = resolveString(r.systemPrompt);
                }
            });
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

    private void resolveEnvVars(AgentConfig.ProviderConfig p) {
        if (p.apiKey != null) {
            p.apiKey = resolveString(p.apiKey);
        }
        if (p.baseUrl != null) {
            p.baseUrl = resolveString(p.baseUrl);
        }
    }

    private String resolveString(String value) {
        if (value == null || !value.contains("${")) {
            return value;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\$\\{([^}]+)\\}").matcher(value);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String token = m.group(1);
            int colon = token.indexOf(':');
            String varName = colon >= 0 ? token.substring(0, colon) : token;
            String defaultValue = colon >= 0 ? token.substring(colon + 1) : "";
            String resolved = lookup(varName);
            m.appendReplacement(sb,
                    java.util.regex.Matcher.quoteReplacement(resolved != null ? resolved : defaultValue));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Looks up a value by name, checking system properties first (so {@code -DVAR=...}
     * command-line flags take precedence) then environment variables.
     *
     * @return the resolved value, or {@code null} if neither a system property nor an
     *         environment variable is set for the given name.
     */
    private String lookup(String varName) {
        String sysProp = System.getProperty(varName);
        if (sysProp != null && !sysProp.isEmpty()) {
            return sysProp;
        }
        return System.getenv(varName);
    }
}
