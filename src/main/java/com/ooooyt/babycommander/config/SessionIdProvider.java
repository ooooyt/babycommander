package com.ooooyt.babycommander.config;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.UUID;

/**
 * Provides a single per-app-run session id (UUID) plus the HTTP header name
 * used to send it with every LLM request.
 *
 * <p>The header name is read from {@code agents.yaml} key
 * {@code header.session.id.key} (default {@code x-opencode-session}). The UUID
 * is generated once when this bean is created, i.e. at application startup.</p>
 */
@ApplicationScoped
public class SessionIdProvider {

    public static final String DEFAULT_HEADER_KEY = "x-opencode-session";

    private final String sessionId;
    private final String headerKey;

    /**
     * CDI constructor: resolves the header key from agents.yaml.
     */
    @Inject
    public SessionIdProvider(YamlConfigLoader configLoader) {
        this.sessionId = UUID.randomUUID().toString();
        this.headerKey = resolveHeaderKey(configLoader);
        Log.infof("Session id: %s (header: %s)", sessionId, headerKey);
    }

    /**
     * Plain constructor for tests / manual construction.
     */
    public SessionIdProvider(String headerKey) {
        this.sessionId = UUID.randomUUID().toString();
        this.headerKey = (headerKey == null || headerKey.isBlank())
                ? DEFAULT_HEADER_KEY
                : headerKey;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getHeaderKey() {
        return headerKey;
    }

    /**
     * Header map to attach to every LLM request: {@code {headerKey: sessionId}}.
     */
    public Map<String, String> sessionHeaders() {
        return Map.of(headerKey, sessionId);
    }

    private static String resolveHeaderKey(YamlConfigLoader configLoader) {
        try {
            AgentConfig config = configLoader.getConfig();
            if (config != null && config.header != null && config.header.session != null
                    && config.header.session.id != null && config.header.session.id.key != null
                    && !config.header.session.id.key.isBlank()) {
                return config.header.session.id.key;
            }
        } catch (Exception e) {
            Log.warnf("Failed to resolve header.session.id.key, using default '%s': %s",
                    DEFAULT_HEADER_KEY, e.getMessage());
        }
        return DEFAULT_HEADER_KEY;
    }
}