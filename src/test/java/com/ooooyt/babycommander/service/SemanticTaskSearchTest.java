package com.ooooyt.babycommander.service;

import com.ooooyt.babycommander.CodeGenLifecycle;
import com.ooooyt.babycommander.config.AgentConfig;
import com.ooooyt.babycommander.config.YamlConfigLoader;
import com.ooooyt.babycommander.db.entity.MyObjectBox;
import com.ooooyt.babycommander.db.entity.TaskEntity;
import com.ooooyt.babycommander.db.repository.TaskRepository;
import io.objectbox.BoxStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * End-to-end semantic task search using the in-process ONNX embedding model
 * (bge-small-zh-v1.5) and a real ObjectBox HNSW vector index (512 dims).
 *
 * <p>Verifies the option-B requirement: a Chinese query surfaces an English
 * task and vice-versa, plus the stale-embedding re-embed migration.</p>
 */
class SemanticTaskSearchTest {

    private static final String PROJECT_ID = "proj-embed-test";

    static BoxStore boxStore;
    static Path dbDir;

    @Mock
    YamlConfigLoader configLoader;

    @Mock
    CodeGenLifecycle lifecycle;

    TaskRepository taskRepository;
    EmbeddingService embeddingService;
    ProjectTaskService service;

    @BeforeAll
    static void openStore() throws Exception {
        dbDir = Files.createTempDirectory("bc-embed-test");
        boxStore = MyObjectBox.builder().directory(dbDir.toFile()).build();
    }

    @AfterAll
    static void closeStore() throws Exception {
        if (boxStore != null) {
            boxStore.close();
        }
        if (dbDir != null) {
            try (var stream = Files.walk(dbDir)) {
                stream.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        embeddingService = new EmbeddingService();
        embeddingService.configLoader = configLoader;
        embeddingService.lifecycle = lifecycle;

        AgentConfig cfg = new AgentConfig();
        cfg.defaultModel = "ocdeepseek";
        AgentConfig.ProviderConfig onnx = new AgentConfig.ProviderConfig();
        onnx.type = "onnx";
        onnx.embeddingModel = "bge-small-zh-v15";
        cfg.providers.put("local-embed", onnx);
        when(configLoader.getConfig()).thenReturn(cfg);

        taskRepository = new TaskRepository();
        setField(taskRepository, "boxStore", boxStore);

        service = new ProjectTaskService();
        setField(service, "taskRepository", taskRepository);
        setField(service, "embeddingService", embeddingService);
        setField(service, "boxStore", boxStore);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private TaskEntity saveTask(String name, float[] embedding) {
        TaskEntity t = new TaskEntity();
        t.id = java.util.UUID.randomUUID().toString();
        t.projectId = PROJECT_ID;
        t.name = name;
        t.createdDatetime = System.currentTimeMillis();
        t.updatedDatetime = System.currentTimeMillis();
        t.status = TaskEntity.Status.STARTED.name();
        t.embedding = embedding;
        taskRepository.save(t);
        return t;
    }

    @Test
    @Timeout(180)
    void chineseQueryFindsEnglishTask() {
        saveTask("fix login bug", embeddingService.embed("fix login bug"));
        saveTask("add dark mode toggle", embeddingService.embed("add dark mode toggle"));
        saveTask("优化数据库查询", embeddingService.embed("优化数据库查询"));

        List<TaskEntity> results = service.searchProjectTasksSemantic(PROJECT_ID, "修复登录bug", 5);
        assertNotNull(results);
        assertTrue(!results.isEmpty(), "semantic search should return results");
        assertEquals("fix login bug", results.get(0).name,
                "Chinese query should surface the equivalent English task first");
    }

    @Test
    @Timeout(180)
    void englishQueryFindsChineseTask() {
        saveTask("fix login bug", embeddingService.embed("fix login bug"));
        saveTask("优化数据库查询", embeddingService.embed("优化数据库查询"));

        List<TaskEntity> results = service.searchProjectTasksSemantic(PROJECT_ID, "database query optimization", 5);
        assertNotNull(results);
        assertTrue(!results.isEmpty());
        assertEquals("优化数据库查询", results.get(0).name,
                "English query should surface the equivalent Chinese task first");
    }

    @Test
    @Timeout(180)
    void reembedStaleEmbeddingsFixesMissingAndWrongDim() {
        // Task with NO embedding (e.g. created before embeddings were enabled)
        saveTask("fix login bug", null);
        // Task with a stale 1536-dim embedding (pre-migration schema)
        saveTask("优化数据库查询", new float[1536]);

        int reembedded = service.reembedStaleEmbeddings();
        assertEquals(2, reembedded, "both stale tasks should be re-embedded");

        List<TaskEntity> all = taskRepository.findAll();
        for (TaskEntity t : all) {
            assertNotNull(t.embedding);
            assertEquals(TaskEntity.EMBEDDING_DIMENSIONS, t.embedding.length);
        }

        // After migration, semantic search must work on the re-embedded tasks.
        List<TaskEntity> results = service.searchProjectTasksSemantic(PROJECT_ID, "修复登录bug", 5);
        assertTrue(!results.isEmpty());
        assertEquals("fix login bug", results.get(0).name);
    }

    @Test
    @Timeout(180)
    void dimensionMismatchFallsBackToKeyword() throws Exception {
        saveTask("fix login bug", embeddingService.embed("fix login bug"));

        // Replace the real embedding service with one returning a wrong-dim vector.
        EmbeddingService wrongDim = org.mockito.Mockito.mock(EmbeddingService.class);
        when(wrongDim.embed(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new float[128]);
        setField(service, "embeddingService", wrongDim);

        List<TaskEntity> results = service.searchProjectTasksSemantic(PROJECT_ID, "login", 5);
        assertNotNull(results);
        // Keyword fallback matches by name containment.
        assertTrue(results.stream().anyMatch(t -> t.name.contains("login")),
                "dimension mismatch should fall back to keyword search instead of throwing");
    }
}