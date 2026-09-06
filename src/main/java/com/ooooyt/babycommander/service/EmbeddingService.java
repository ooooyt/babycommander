package com.ooooyt.babycommander.service;

import com.ooooyt.babycommander.CodeGenLifecycle;
import com.ooooyt.babycommander.config.AgentConfig;
import com.ooooyt.babycommander.config.AgentConfig.ProviderConfig;
import com.ooooyt.babycommander.config.YamlConfigLoader;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel;
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
 * Supports OpenAI-compatible, Ollama, and in-process ONNX (bge-small-zh-v1.5)
 * embedding providers. The ONNX model runs fully offline inside the JVM with
 * no API key or network access.
 */
@ApplicationScoped
public class EmbeddingService {

    private static final String DEFAULT_OPENAI_EMBEDDING_MODEL = "text-embedding-3-small";
    private static final String DEFAULT_OLLAMA_EMBEDDING_MODEL = "nomic-embed-text";

    /** Set once so the "no embedding provider configured" hint is logged only on first use. */
    private static volatile boolean noEmbeddingProviderLogged = false;

    /**
     * Cached in-process ONNX embedding model. The model (~94 MB) is loaded once
     * per JVM and reused; building a new instance per embed() call would be fatal
     * for ONNX because each construction pays the full model-load cost.
     */
    private static volatile EmbeddingModel cachedOnnxModel;

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
            if (!noEmbeddingProviderLogged) {
                noEmbeddingProviderLogged = true;
                Log.warn("EmbeddingService: no provider declares an 'embeddingModel', skipping embedding "
                        + "generation; semantic search will fall back to keyword search. To enable "
                        + "embeddings, set 'embeddingModel' on an OpenAI-compatible or Ollama provider "
                        + "in agents.yaml (the chat 'modelName' is never used for embeddings).");
            }
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
            // Embeddings are best-effort: callers (e.g. semantic task search) already fall back
            // to keyword search when this returns null, so log a concise WARN instead of an ERROR
            // stack trace on every task creation.
            Log.warnf("EmbeddingService: failed to generate embedding for text: '%s' (%s); "
                    + "falling back to keyword search",
                truncate(text, 50),
                e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * Resolves the provider name to use for embeddings.
     * Only providers that explicitly declare an {@code embeddingModel} on a type that
     * supports embeddings are considered, so a provider's chat {@code modelName}
     * (e.g. {@code qwen3.8-max}) is never mistaken for an embedding model.
     */
    private String resolveEmbeddingProvider(AgentConfig config) {
        // First, try the default model provider
        String defaultModel = config.defaultModel;
        if (defaultModel != null && !defaultModel.isBlank()
                && config.providers != null && config.providers.containsKey(defaultModel)) {
            ProviderConfig pc = config.providers.get(defaultModel);
            if (isEmbeddingProvider(pc)) {
                return defaultModel;
            }
        }

        // Fall back to the agent defaults provider
        String defaultProvider = config.agentDefaults.provider;
        if (defaultProvider != null && config.providers != null
                && config.providers.containsKey(defaultProvider)) {
            ProviderConfig pc = config.providers.get(defaultProvider);
            if (isEmbeddingProvider(pc)) {
                return defaultProvider;
            }
        }

        // Scan all providers for one that supports embeddings with a declared model
        if (config.providers != null) {
            for (Map.Entry<String, ProviderConfig> entry : config.providers.entrySet()) {
                if (isEmbeddingProvider(entry.getValue())) {
                    return entry.getKey();
                }
            }
        }

        return null;
    }

    private boolean supportsEmbeddings(String type) {
        return "openai".equals(type) || "ollama".equals(type) || "onnx".equals(type);
    }

    private boolean isEmbeddingProvider(ProviderConfig c) {
        return supportsEmbeddings(c.type)
                && c.embeddingModel != null && !c.embeddingModel.isBlank();
    }

    private EmbeddingModel createEmbeddingModel(ProviderConfig c) {
        Duration timeout = Duration.ofSeconds(c.timeoutSeconds > 0 ? c.timeoutSeconds : 60);

        return switch (c.type) {
            case "openai", "deepseek" -> OpenAiEmbeddingModel.builder()
                    .baseUrl(c.baseUrl)
                    .apiKey(c.apiKey)
                    .modelName(c.embeddingModel != null ? c.embeddingModel : DEFAULT_OPENAI_EMBEDDING_MODEL)
                    .timeout(timeout)
                    .maxRetries(1)
                    .build();
            case "ollama" -> OllamaEmbeddingModel.builder()
                    .baseUrl(c.baseUrl)
                    .modelName(c.embeddingModel != null ? c.embeddingModel : DEFAULT_OLLAMA_EMBEDDING_MODEL)
                    .timeout(timeout)
                    .build();
            case "onnx" -> {
                if (cachedOnnxModel == null) {
                    synchronized (this) {
                        if (cachedOnnxModel == null) {
                            Log.info("EmbeddingService: loading in-process ONNX embedding model "
                                    + "(bge-small-zh-v1.5, 512 dims) — first load may take a few seconds");
                            cachedOnnxModel = new BgeSmallZhV15EmbeddingModel();
                        }
                    }
                }
                yield cachedOnnxModel;
            }
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
