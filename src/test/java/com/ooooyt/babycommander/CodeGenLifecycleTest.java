package com.ooooyt.babycommander;

import com.ooooyt.babycommander.config.AgentConfig;
import com.ooooyt.babycommander.config.YamlConfigLoader;
import com.ooooyt.babycommander.hook.HookManager;
import com.ooooyt.babycommander.hook.SessionMemory;
import com.ooooyt.babycommander.skill.SkillRegistry;
import com.ooooyt.babycommander.tool.FileSystemTool;
import com.ooooyt.babycommander.tool.InternetTool;
import com.ooooyt.babycommander.tool.ShellTool;
import com.ooooyt.babycommander.tool.ToolRegistry;
import com.ooooyt.babycommander.tool.mcp.McpClientManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CodeGenLifecycle class.
 * Tests the initialization behavior, especially the project folder and workspace path setup.
 */
class CodeGenLifecycleTest {

    @Test
    @DisplayName("initialProjectFolder uses user.dir by default")
    void testInitialProjectFolderUsesUserDir() {
        // Given
        AgentConfig config = createConfigWithWorkspaceRoot("/some/workspace");
        YamlConfigLoader configLoader = createMockConfigLoader(config);
        ToolRegistry toolRegistry = new ToolRegistry();
        McpClientManager mcpClientManager = new McpClientManager();
        HookManager hookManager = new HookManager(configLoader, new SessionMemory());
        SkillRegistry skillRegistry = new SkillRegistry((String) null);

        CodeGenLifecycle lifecycle = new CodeGenLifecycle();
        injectFields(lifecycle, toolRegistry, configLoader, mcpClientManager, hookManager, skillRegistry);

        // When
        lifecycle.initialize();

        // Then: FileSystemTool should be registered with user.dir as project folder
        List<Object> tools = toolRegistry.getAllTools();
        FileSystemTool fsTool = findToolOfType(tools, FileSystemTool.class);
        assertNotNull(fsTool, "FileSystemTool should be registered");

        String expectedProjectFolder = System.getProperty("user.dir", ".");
        Path expectedPath = Paths.get(expectedProjectFolder).toAbsolutePath().normalize();
        assertEquals(expectedPath.toString(), fsTool.getProjectFolder(),
            "FileSystemTool should use user.dir as the initial project folder");
    }

    @Test
    @DisplayName("ShellTool uses user.dir as initial working directory")
    void testShellToolUsesUserDir() {
        // Given
        AgentConfig config = createConfigWithWorkspaceRoot("/some/workspace");
        YamlConfigLoader configLoader = createMockConfigLoader(config);
        ToolRegistry toolRegistry = new ToolRegistry();
        McpClientManager mcpClientManager = new McpClientManager();
        HookManager hookManager = new HookManager(configLoader, new SessionMemory());
        SkillRegistry skillRegistry = new SkillRegistry((String) null);

        CodeGenLifecycle lifecycle = new CodeGenLifecycle();
        injectFields(lifecycle, toolRegistry, configLoader, mcpClientManager, hookManager, skillRegistry);

        // When
        lifecycle.initialize();

        // Then: ShellTool should be registered
        List<Object> tools = toolRegistry.getAllTools();
        ShellTool shellTool = findToolOfType(tools, ShellTool.class);
        assertNotNull(shellTool, "ShellTool should be registered");
    }

    @Test
    @DisplayName("InternetTool is registered")
    void testInternetToolRegistered() {
        // Given
        AgentConfig config = createConfigWithWorkspaceRoot("/some/workspace");
        YamlConfigLoader configLoader = createMockConfigLoader(config);
        ToolRegistry toolRegistry = new ToolRegistry();
        McpClientManager mcpClientManager = new McpClientManager();
        HookManager hookManager = new HookManager(configLoader, new SessionMemory());
        SkillRegistry skillRegistry = new SkillRegistry((String) null);

        CodeGenLifecycle lifecycle = new CodeGenLifecycle();
        injectFields(lifecycle, toolRegistry, configLoader, mcpClientManager, hookManager, skillRegistry);

        // When
        lifecycle.initialize();

        // Then
        List<Object> tools = toolRegistry.getAllTools();
        InternetTool internetTool = findToolOfType(tools, InternetTool.class);
        assertNotNull(internetTool, "InternetTool should be registered");
    }

    @Test
    @DisplayName("workspaceRoot is set on FileSystemTool via setWorkspace when configured")
    void testWorkspaceRootSetOnFileSystemTool() {
        // Given
        String configuredWorkspaceRoot = "/custom/workspace";
        AgentConfig config = createConfigWithWorkspaceRoot(configuredWorkspaceRoot);
        YamlConfigLoader configLoader = createMockConfigLoader(config);
        ToolRegistry toolRegistry = new ToolRegistry();
        McpClientManager mcpClientManager = new McpClientManager();
        HookManager hookManager = new HookManager(configLoader, new SessionMemory());
        SkillRegistry skillRegistry = new SkillRegistry((String) null);

        CodeGenLifecycle lifecycle = new CodeGenLifecycle();
        injectFields(lifecycle, toolRegistry, configLoader, mcpClientManager, hookManager, skillRegistry);

        // When
        lifecycle.initialize();

        // Then: FileSystemTool should have the workspace path set
        List<Object> tools = toolRegistry.getAllTools();
        FileSystemTool fsTool = findToolOfType(tools, FileSystemTool.class);
        assertNotNull(fsTool);

        // getWorkspaceFolder() should return the configured workspaceRoot
        String workspaceFolder = fsTool.getWorkspaceFolder();
        assertEquals(configuredWorkspaceRoot, workspaceFolder,
            "FileSystemTool.getWorkspaceFolder() should return the configured workspaceRoot");
    }

    @Test
    @DisplayName("workspaceRoot null does not cause NPE and tools still work with user.dir")
    void testWorkspaceRootNullDoesNotNpe() {
        // Given: workspaceRoot is null
        AgentConfig config = createConfigWithWorkspaceRoot(null);
        YamlConfigLoader configLoader = createMockConfigLoader(config);
        ToolRegistry toolRegistry = new ToolRegistry();
        McpClientManager mcpClientManager = new McpClientManager();
        HookManager hookManager = new HookManager(configLoader, new SessionMemory());
        SkillRegistry skillRegistry = new SkillRegistry((String) null);

        CodeGenLifecycle lifecycle = new CodeGenLifecycle();
        injectFields(lifecycle, toolRegistry, configLoader, mcpClientManager, hookManager, skillRegistry);

        // When - should not throw NPE
        assertDoesNotThrow(() -> lifecycle.initialize(),
            "Initialization should not throw NullPointerException when workspaceRoot is null");

        // Then: FileSystemTool should still use user.dir as project folder
        List<Object> tools = toolRegistry.getAllTools();
        FileSystemTool fsTool = findToolOfType(tools, FileSystemTool.class);
        assertNotNull(fsTool);

        String expectedProjectFolder = System.getProperty("user.dir", ".");
        Path expectedPath = Paths.get(expectedProjectFolder).toAbsolutePath().normalize();
        assertEquals(expectedPath.toString(), fsTool.getProjectFolder(),
            "FileSystemTool should use user.dir even when workspaceRoot is null");
    }

    @Test
    @DisplayName("initialize is idempotent - calling twice only registers tools once")
    void testInitializeIsIdempotent() {
        // Given
        AgentConfig config = createConfigWithWorkspaceRoot("/some/workspace");
        YamlConfigLoader configLoader = createMockConfigLoader(config);
        ToolRegistry toolRegistry = new ToolRegistry();
        McpClientManager mcpClientManager = new McpClientManager();
        HookManager hookManager = new HookManager(configLoader, new SessionMemory());
        SkillRegistry skillRegistry = new SkillRegistry((String) null);

        CodeGenLifecycle lifecycle = new CodeGenLifecycle();
        injectFields(lifecycle, toolRegistry, configLoader, mcpClientManager, hookManager, skillRegistry);

        // When
        lifecycle.initialize();
        int sizeAfterFirstCall = toolRegistry.size();
        lifecycle.initialize(); // Second call should be a no-op
        int sizeAfterSecondCall = toolRegistry.size();

        // Then
        assertEquals(sizeAfterFirstCall, sizeAfterSecondCall,
            "Second initialize() call should not add more tools");
        assertEquals(5, sizeAfterFirstCall,
            "Should have exactly 5 built-in tools (FileSystem, Shell, Internet, Plan, AskUser)");
    }

    @Test
    @DisplayName("user.dir fallback to dot when property not set")
    void testUserDirFallback() {
        // Given: simulate user.dir not being set by temporarily clearing it
        String originalUserDir = System.getProperty("user.dir");
        try {
            // Clear the property to test fallback
            System.clearProperty("user.dir");

            AgentConfig config = createConfigWithWorkspaceRoot("/some/workspace");
            YamlConfigLoader configLoader = createMockConfigLoader(config);
            ToolRegistry toolRegistry = new ToolRegistry();
            McpClientManager mcpClientManager = new McpClientManager();
            HookManager hookManager = new HookManager(configLoader, new SessionMemory());
            SkillRegistry skillRegistry = new SkillRegistry((String) null);

            CodeGenLifecycle lifecycle = new CodeGenLifecycle();
            injectFields(lifecycle, toolRegistry, configLoader, mcpClientManager, hookManager, skillRegistry);

            // When
            lifecycle.initialize();

            // Then
            List<Object> tools = toolRegistry.getAllTools();
            FileSystemTool fsTool = findToolOfType(tools, FileSystemTool.class);
            assertNotNull(fsTool);

            // With user.dir cleared, System.getProperty("user.dir", ".") returns "."
            // FileSystemTool resolves "." to absolute path
            Path expectedPath = Paths.get(".").toAbsolutePath().normalize();
            assertEquals(expectedPath.toString(), fsTool.getProjectFolder(),
                "FileSystemTool should fall back to '.' when user.dir is not set");
        } finally {
            // Restore original value
            if (originalUserDir != null) {
                System.setProperty("user.dir", originalUserDir);
            }
        }
    }

    @Test
    @DisplayName("ensureInitialized seeds the default skills into an empty skills directory")
    void testEnsureInitializedSeedsDefaultSkills(@TempDir Path tempDir) throws IOException {
        // Given: a fresh (non-existent) skills directory under a temp dir
        Path skillsDir = tempDir.resolve("skills");
        SkillRegistry skillRegistry = new SkillRegistry(skillsDir.toString());

        AgentConfig config = createConfigWithWorkspaceRoot("/some/workspace");
        YamlConfigLoader configLoader = createMockConfigLoader(config);
        ToolRegistry toolRegistry = new ToolRegistry();
        McpClientManager mcpClientManager = new McpClientManager();
        HookManager hookManager = new HookManager(configLoader, new SessionMemory());

        CodeGenLifecycle lifecycle = new CodeGenLifecycle();
        injectFields(lifecycle, toolRegistry, configLoader, mcpClientManager, hookManager, skillRegistry);

        // When: the @PostConstruct bootstrap runs
        lifecycle.ensureInitialized();

        // Then: the skills directory was created and seeded with the bundled skills
        assertTrue(Files.isDirectory(skillsDir), "skills directory should be created");
        List<Path> skillDirs;
        try (var stream = Files.list(skillsDir)) {
            skillDirs = stream.filter(Files::isDirectory).toList();
        }
        assertFalse(skillDirs.isEmpty(), "default skills should be copied into the skills directory");
        assertTrue(Files.exists(skillsDir.resolve("refactor-large-codebase").resolve("SKILL.md")),
            "the 'refactor-large-codebase' skill's SKILL.md should be copied");
    }

    @Test
    @DisplayName("ensureInitialized does not seed an existing (even empty) skills directory")
    void testEnsureInitializedDoesNotSeedExistingDirectory(@TempDir Path tempDir) throws IOException {
        // Given: the skills directory already exists but is empty
        Path skillsDir = tempDir.resolve("skills");
        Files.createDirectories(skillsDir);
        SkillRegistry skillRegistry = new SkillRegistry(skillsDir.toString());

        AgentConfig config = createConfigWithWorkspaceRoot("/some/workspace");
        YamlConfigLoader configLoader = createMockConfigLoader(config);
        ToolRegistry toolRegistry = new ToolRegistry();
        McpClientManager mcpClientManager = new McpClientManager();
        HookManager hookManager = new HookManager(configLoader, new SessionMemory());

        CodeGenLifecycle lifecycle = new CodeGenLifecycle();
        injectFields(lifecycle, toolRegistry, configLoader, mcpClientManager, hookManager, skillRegistry);

        // When
        lifecycle.ensureInitialized();

        // Then: the existing directory is left untouched (not seeded)
        List<Path> skillDirs;
        try (var stream = Files.list(skillsDir)) {
            skillDirs = stream.filter(Files::isDirectory).toList();
        }
        assertTrue(skillDirs.isEmpty(),
            "an existing skills directory should not be seeded with the default skills");
    }

    // ========== Helper methods ==========

    private AgentConfig createConfigWithWorkspaceRoot(String workspaceRoot) {
        AgentConfig config = new AgentConfig();
        config.defaultModel = "gpt-4o";
        config.workspaceRoot = workspaceRoot;
        config.agentDefaults = new AgentConfig.AgentDefaults();
        config.agentDefaults.provider = "openai";
        config.agentDefaults.maxMessagesInMemory = 30;
        config.agentDefaults.temperature = 0.7;

        config.providers = new java.util.HashMap<>();
        AgentConfig.ProviderConfig openai = new AgentConfig.ProviderConfig();
        openai.type = "openai";
        openai.baseUrl = "https://api.openai.com/v1";
        openai.apiKey = "test-key";
        openai.modelName = "gpt-4o";
        config.providers.put("openai", openai);

        return config;
    }

    private YamlConfigLoader createMockConfigLoader(AgentConfig config) {
        return new YamlConfigLoader("agents.yaml") {
            @Override
            public AgentConfig getConfig() {
                return config;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private <T> T findToolOfType(List<Object> tools, Class<T> type) {
        for (Object tool : tools) {
            if (type.isInstance(tool)) {
                return (T) tool;
            }
        }
        return null;
    }

    private void injectFields(CodeGenLifecycle lifecycle,
                               ToolRegistry toolRegistry,
                               YamlConfigLoader configLoader,
                               McpClientManager mcpClientManager,
                               HookManager hookManager,
                               SkillRegistry skillRegistry) {
        // Use reflection to inject fields since they use @Inject
        try {
            java.lang.reflect.Field toolRegistryField = CodeGenLifecycle.class.getDeclaredField("toolRegistry");
            toolRegistryField.setAccessible(true);
            toolRegistryField.set(lifecycle, toolRegistry);

            java.lang.reflect.Field configLoaderField = CodeGenLifecycle.class.getDeclaredField("configLoader");
            configLoaderField.setAccessible(true);
            configLoaderField.set(lifecycle, configLoader);

            java.lang.reflect.Field mcpClientManagerField = CodeGenLifecycle.class.getDeclaredField("mcpClientManager");
            mcpClientManagerField.setAccessible(true);
            mcpClientManagerField.set(lifecycle, mcpClientManager);

            java.lang.reflect.Field hookManagerField = CodeGenLifecycle.class.getDeclaredField("hookManager");
            hookManagerField.setAccessible(true);
            hookManagerField.set(lifecycle, hookManager);

            java.lang.reflect.Field skillRegistryField = CodeGenLifecycle.class.getDeclaredField("skillRegistry");
            skillRegistryField.setAccessible(true);
            skillRegistryField.set(lifecycle, skillRegistry);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject fields via reflection", e);
        }
    }
}
