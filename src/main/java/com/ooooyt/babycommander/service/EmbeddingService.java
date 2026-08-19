package com.ooooyt.babycommander.service;

import com.ooooyt.babycommander.CodeGenLifecycle;
import com.ooooyt.babycommander.config.AgentConfig;
import com.ooooyt.babycommander.config.AgentConfig.ProviderConfig;
import com.ooooyt.babycommander.config.YamlConfigLoader;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.Map;

/**
 * Service for generating vector embeddings of text using the configured
 * LLM provider's embedding API.
 * <p>
 * Supports OpenAI-compatible and Ollama embedding providers.
 */
@ApplicationScoped
public class EmbeddingService {

    @Inject
    YamlConfigLoader configLoader;

    @Inject
    CodeGenLifecycle lifecycle;

    /**
     * Generates a vector embedding for the given text.
     *
     * @param text the text to embed (e.g., a task name)
     * @return a float array representing the embedding vector, or {@code null}
     *         if embedding generation fails or no suitable provider is configured
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        lifecycle.initialize();
        AgentConfig config = configLoader.getConfig();

        String providerName = resolveEmbeddingProvider(config);
        if (providerName == null) {
            Log.warn("EmbeddingService: no provider supports embeddings, skipping embedding generation");
            return null;
        }

        ProviderConfig providerConfig = config.providers.get(providerName);
        if (providerConfig == null) {
            Log.warnf("EmbeddingService: provider '%s' not found in config", providerName);
            return null;
        }

        try {
            EmbeddingModel embeddingModel = createEmbeddingModel(providerConfig);
            if (embeddingModel == null) {
                return null;
            }
            dev.langchain4j.data.embedding.Embedding response = embeddingModel.embed(text).content();
            float[] vector = response.vector();
            Log.debugf("EmbeddingService: generated embedding with %d dimensions for text: '%s'",
                vector.length, truncate(text, 50));
            return vector;
        } catch (Exception e) {
            Log.errorf(e, "EmbeddingService: failed to generate embedding for text: '%s'", truncate(text, 50));
            return null;
        }
    }

    /**
     * Resolves the provider name to use for embeddings.
     * Prefers providers that support embeddings (OpenAI-compatible, Ollama).
     */
    private String resolveEmbeddingProvider(AgentConfig config) {
        // First, try the default model provider
        String defaultModel = config.defaultModel;
        if (defaultModel != null && !defaultModel.isBlank()
                && config.providers != null && config.providers.containsKey(defaultModel)) {
            ProviderConfig pc = config.providers.get(defaultModel);
            if (supportsEmbeddings(pc.type)) {
                return defaultModel;
            }
        }

        // Fall back to the agent defaults provider
        String defaultProvider = config.agentDefaults.provider;
        if (defaultProvider != null && config.providers != null
                && config.providers.containsKey(defaultProvider)) {
            ProviderConfig pc = config.providers.get(defaultProvider);
            if (supportsEmbeddings(pc.type)) {
                return defaultProvider;
            }
        }

        // Scan all providers for one that supports embeddings
        if (config.providers != null) {
            for (Map.Entry<String, ProviderConfig> entry : config.providers.entrySet()) {
                if (supportsEmbeddings(entry.getValue().type)) {
                    return entry.getKey();
                }
            }
        }

        return null;
    }

    private boolean supportsEmbeddings(String type) {
        return "openai".equals(type) || "deepseek".equals(type) || "ollama".equals(type);
    }

    private EmbeddingModel createEmbeddingModel(ProviderConfig c) {
        Duration timeout = Duration.ofSeconds(c.timeoutSeconds > 0 ? c.timeoutSeconds : 60);

        return switch (c.type) {
            case "openai", "deepseek" -> OpenAiEmbeddingModel.builder()
                    .baseUrl(c.baseUrl)
                    .apiKey(c.apiKey)
                    .modelName(c.modelName != null ? c.modelName : "text-embedding-3-small")
                    .timeout(timeout)
                    .maxRetries(1)
                    .build();
            case "ollama" -> OllamaEmbeddingModel.builder()
                    .baseUrl(c.baseUrl)
                    .modelName(c.modelName != null ? c.modelName : "nomic-embed-text")
                    .timeout(timeout)
                    .build();
            default -> {
                Log.warnf("EmbeddingService: unsupported provider type '%s' for embeddings", c.type);
                yield null;
            }
        };
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
