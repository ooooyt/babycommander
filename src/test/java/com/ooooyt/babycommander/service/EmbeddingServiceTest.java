package com.ooooyt.babycommander.service;

import com.ooooyt.babycommander.CodeGenLifecycle;
import com.ooooyt.babycommander.config.AgentConfig;
import com.ooooyt.babycommander.config.YamlConfigLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

/**
 * Tests the in-process ONNX embedding branch of {@link EmbeddingService}
 * (option B: bge-small-zh-v1.5, fully local, 512 dimensions).
 */
class EmbeddingServiceTest {

    @Mock
    YamlConfigLoader configLoader;

    @Mock
    CodeGenLifecycle lifecycle;

    EmbeddingService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new EmbeddingService();
        service.configLoader = configLoader;
        service.lifecycle = lifecycle;

        AgentConfig cfg = new AgentConfig();
        cfg.defaultModel = "ocdeepseek";
        AgentConfig.ProviderConfig onnx = new AgentConfig.ProviderConfig();
        onnx.type = "onnx";
        onnx.embeddingModel = "bge-small-zh-v15";
        cfg.providers.put("local-embed", onnx);
        when(configLoader.getConfig()).thenReturn(cfg);
    }

    @Test
    @Timeout(180)
    void embedWithOnnxReturns512Dimensions() {
        float[] v = service.embed("修复登录bug");
        assertNotNull(v, "embedding should not be null");
        assertEquals(512, v.length, "bge-small-zh-v1.5 must produce 512 dimensions");
    }

    @Test
    @Timeout(180)
    void embedWorksForEnglishAndChinese() {
        float[] en = service.embed("fix login bug");
        float[] zh = service.embed("修复登录bug");
        assertNotNull(en);
        assertNotNull(zh);
        assertEquals(512, en.length);
        assertEquals(512, zh.length);
    }

    @Test
    @Timeout(180)
    void onnxModelInstanceIsCached() throws Exception {
        service.embed("hello");
        Object first = cachedOnnxModel();
        assertNotNull(first, "ONNX model should be cached after first embed");

        service.embed("world");
        Object second = cachedOnnxModel();
        assertSame(first, second, "subsequent embeds must reuse the cached model instance");
    }

    @Test
    void embedReturnsNullForBlankText() {
        assertNull(service.embed(null));
        assertNull(service.embed("   "));
    }

    private static Object cachedOnnxModel() throws Exception {
        java.lang.reflect.Field f = EmbeddingService.class.getDeclaredField("cachedOnnxModel");
        f.setAccessible(true);
        return f.get(null);
    }
}