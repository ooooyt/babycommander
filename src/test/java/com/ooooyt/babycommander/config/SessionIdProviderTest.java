package com.ooooyt.babycommander.config;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SessionIdProviderTest {

    private YamlConfigLoader mockLoader(AgentConfig config) {
        return new YamlConfigLoader("agents.yaml") {
            @Override
            public AgentConfig getConfig() {
                return config;
            }
        };
    }

    @Test
    void testSessionIdIsUuid() {
        SessionIdProvider provider = new SessionIdProvider("x-opencode-session");
        assertNotNull(provider.getSessionId());
        // UUID.fromString throws if not a valid UUID
        assertEquals(UUID.fromString(provider.getSessionId()).toString(), provider.getSessionId());
    }

    @Test
    void testHeaderKeyFromConfig() {
        AgentConfig config = new AgentConfig();
        config.header.session.id.key = "x-custom-session";
        SessionIdProvider provider = new SessionIdProvider(mockLoader(config));
        assertEquals("x-custom-session", provider.getHeaderKey());
    }

    @Test
    void testDefaultHeaderKeyWhenConfigMissing() {
        SessionIdProvider provider = new SessionIdProvider(mockLoader(new AgentConfig()));
        assertEquals("x-opencode-session", provider.getHeaderKey());
    }

    @Test
    void testPlainConstructorDefaultsKey() {
        SessionIdProvider provider = new SessionIdProvider((String) null);
        assertEquals("x-opencode-session", provider.getHeaderKey());
    }

    @Test
    void testSessionHeadersMap() {
        AgentConfig config = new AgentConfig();
        config.header.session.id.key = "x-opencode-session";
        SessionIdProvider provider = new SessionIdProvider(mockLoader(config));
        Map<String, String> headers = provider.sessionHeaders();
        assertEquals(1, headers.size());
        assertEquals(provider.getSessionId(), headers.get("x-opencode-session"));
    }

    @Test
    void testSessionIdIsStableWithinProvider() {
        SessionIdProvider provider = new SessionIdProvider("x-opencode-session");
        assertEquals(provider.getSessionId(), provider.getSessionId());
        assertEquals(provider.sessionHeaders().get("x-opencode-session"), provider.getSessionId());
    }
}