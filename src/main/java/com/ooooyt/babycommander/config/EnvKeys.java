package com.ooooyt.babycommander.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single source of truth for environment variable names used to override
 * {@code agents.yaml} defaults.
 *
 * <p>Naming convention: {@code BCMD_<PROVIDER>_<FIELD>} where
 * {@code <PROVIDER>} is the exact key used in {@code agents.yaml} (upper-cased)
 * and {@code <FIELD>} is one of {@code API_KEY}, {@code BASE_URL},
 * {@code MODEL}, {@code TEMPERATURE}, {@code MAX_TOKENS},
 * {@code TIMEOUT_SECONDS}, {@code MAX_RETRIES} or {@code EMBEDDING_MODEL}.</p>
 *
 * <p>Deprecated aliases ({@code BABY_COMMANDER_*} and {@code OC_*}) are still
 * honored with a one-time startup warning for backward compatibility.</p>
 *
 * <p>Resolution precedence (low → high): YAML default → {@code ~/.babycommander/.env}
 * → process environment → system property. An empty value is treated as
 * "unset" (falls back to the next source / default).</p>
 */
public final class EnvKeys {

    private EnvKeys() {}

    // ------------------------------------------------------------------
    // Global keys
    // ------------------------------------------------------------------
    public static final String DEFAULT_MODEL = "BCMD_DEFAULT_MODEL";
    public static final String WORKSPACE_ROOT = "BCMD_WORKSPACE_ROOT";
    public static final String SKIP_CONFIRM_WORKSPACE = "BCMD_SKIP_CONFIRM_WORKSPACE";
    public static final String TRUST_PROJECT = "BCMD_TRUST_PROJECT";
    public static final String MAX_RETRIES = "BCMD_MAX_RETRIES";
    public static final String MAX_MESSAGES_IN_MEMORY = "BCMD_MAX_MESSAGES_IN_MEMORY";
    public static final String MAX_TOOL_CALLS = "BCMD_MAX_TOOL_CALLS";
    public static final String DEFAULT_TEMPERATURE = "BCMD_DEFAULT_TEMPERATURE";
    public static final String TOOL_OUTPUT_TRUNCATION_KB = "BCMD_TOOL_OUTPUT_TRUNCATION_KB";
    public static final String MAX_TOKENS_IN_MEMORY = "BCMD_MAX_TOKENS_IN_MEMORY";
    public static final String SHGUARD_DISABLED = "BCMD_SHGUARD_DISABLED";

    // ------------------------------------------------------------------
    // Provider field suffixes
    // ------------------------------------------------------------------
    public static final String SUFFIX_API_KEY = "_API_KEY";
    public static final String SUFFIX_BASE_URL = "_BASE_URL";
    public static final String SUFFIX_MODEL = "_MODEL";
    public static final String SUFFIX_TEMPERATURE = "_TEMPERATURE";
    public static final String SUFFIX_MAX_TOKENS = "_MAX_TOKENS";
    public static final String SUFFIX_TIMEOUT_SECONDS = "_TIMEOUT_SECONDS";
    public static final String SUFFIX_MAX_RETRIES = "_MAX_RETRIES";
    public static final String SUFFIX_EMBEDDING_MODEL = "_EMBEDDING_MODEL";

    /** Builds {@code BCMD_<PROVIDER>_<SUFFIX>} for a provider key from agents.yaml. */
    public static String provider(String providerName, String suffix) {
        return "BCMD_" + providerName.toUpperCase() + suffix;
    }

    /**
     * Deprecated aliases per provider field. Key: {@code BCMD_<PROVIDER>_<SUFFIX>},
     * value: list of legacy names still honored (with a warning).
     */
    public static final Map<String, String[]> DEPRECATED_ALIASES = deprecatedAliases();

    private static Map<String, String[]> deprecatedAliases() {
        Map<String, String[]> m = new LinkedHashMap<>();
        // Global legacy names
        m.put(DEFAULT_MODEL, new String[]{"BABY_COMMANDER_DEFAULT_MODEL"});
        m.put(WORKSPACE_ROOT, new String[]{"BABY_COMMANDER_WORKSPACE_ROOT"});
        m.put(SKIP_CONFIRM_WORKSPACE, new String[]{"BABY_COMMANDER_SKIP_CONFIRM_WORKSPACE"});
        m.put(TRUST_PROJECT, new String[]{"BABY_COMMANDER_TRUST_PROJECT"});
        m.put(MAX_RETRIES, new String[]{"BABY_COMMANDER_MAX_RETRIES"});
        m.put(MAX_MESSAGES_IN_MEMORY, new String[]{"BABY_COMMANDER_MAX_MESSAGES_IN_MEMORY"});
        m.put(MAX_TOOL_CALLS, new String[]{"BABY_COMMANDER_MAX_TOOL_CALLS"});
        m.put(DEFAULT_TEMPERATURE, new String[]{"BABY_COMMANDER_DEFAULT_TEMPERATURE"});
        m.put(TOOL_OUTPUT_TRUNCATION_KB, new String[]{"BABY_COMMANDER_TOOL_OUTPUT_TRUNCATION_KB"});
        m.put(MAX_TOKENS_IN_MEMORY, new String[]{"BABY_COMMANDER_MAX_TOKENS_IN_MEMORY"});
        m.put(SHGUARD_DISABLED, new String[]{"BABY_COMMANDER_SHGUARD_DISABLED"});

        // Generic per-provider legacy names (BABY_COMMANDER_<PROVIDER>_<FIELD>)
        for (String suffix : new String[]{SUFFIX_BASE_URL, SUFFIX_MODEL, SUFFIX_TEMPERATURE,
                SUFFIX_MAX_TOKENS, SUFFIX_TIMEOUT_SECONDS, SUFFIX_MAX_RETRIES, SUFFIX_EMBEDDING_MODEL}) {
            m.put(provider("openai", suffix), new String[]{"BABY_COMMANDER_OPENAI" + suffix});
            m.put(provider("anthropic", suffix), new String[]{"BABY_COMMANDER_ANTHROPIC" + suffix});
            m.put(provider("deepseek", suffix), new String[]{"BABY_COMMANDER_DEEPSEEK" + suffix});
            m.put(provider("ollama", suffix), new String[]{"BABY_COMMANDER_OLLAMA" + suffix});
            m.put(provider("qwen", suffix), new String[]{"BABY_COMMANDER_QWEN" + suffix});
        }
        // opencode used OC_* legacy names
        m.put(provider("opencode", SUFFIX_BASE_URL), new String[]{"BABY_COMMANDER_OC_BASE_URL"});
        m.put(provider("opencode", SUFFIX_MODEL), new String[]{"BABY_COMMANDER_OC_MODEL"});
        m.put(provider("opencode", SUFFIX_TEMPERATURE), new String[]{"BABY_COMMANDER_OC_TEMPERATURE"});
        m.put(provider("opencode", SUFFIX_MAX_TOKENS), new String[]{"BABY_COMMANDER_OC_MAX_TOKENS"});
        m.put(provider("opencode", SUFFIX_TIMEOUT_SECONDS), new String[]{"BABY_COMMANDER_OC_TIMEOUT_SECONDS"});
        // ocdeepseek previously shared BABY_COMMANDER_DEEPSEEK_* with deepseek
        m.put(provider("ocdeepseek", SUFFIX_BASE_URL), new String[]{"BABY_COMMANDER_DEEPSEEK_BASE_URL"});
        m.put(provider("ocdeepseek", SUFFIX_MODEL), new String[]{"BABY_COMMANDER_DEEPSEEK_MODEL"});
        m.put(provider("ocdeepseek", SUFFIX_TEMPERATURE), new String[]{"BABY_COMMANDER_DEEPSEEK_TEMPERATURE"});
        m.put(provider("ocdeepseek", SUFFIX_MAX_TOKENS), new String[]{"BABY_COMMANDER_DEEPSEEK_MAX_TOKENS"});
        m.put(provider("ocdeepseek", SUFFIX_TIMEOUT_SECONDS), new String[]{"BABY_COMMANDER_DEEPSEEK_TIMEOUT_SECONDS"});
        return m;
    }

    /**
     * Conventional API-key fallbacks checked after {@code BCMD_<PROVIDER>_API_KEY}.
     * These are industry-standard names many users already have exported.
     */
    public static final Map<String, String> API_KEY_FALLBACKS = apiKeyFallbacks();

    private static Map<String, String> apiKeyFallbacks() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("openai", "OPENAI_API_KEY");
        m.put("qwen", "OPENAI_API_KEY");      // qwen uses an OpenAI-compatible endpoint
        m.put("anthropic", "ANTHROPIC_API_KEY");
        m.put("deepseek", "DEEPSEEK_API_KEY");
        m.put("ocdeepseek", "OPENCODE_API_KEY");
        m.put("opencode", "OPENCODE_API_KEY");
        m.put("ollama", "OLLAMA_KEY");
        return m;
    }
}