package com.ooooyt.babycommander.tool;

import com.ooooyt.babycommander.config.AgentConfig;
import com.ooooyt.babycommander.config.YamlConfigLoader;
import com.ooooyt.babycommander.hook.HookManager;
import com.ooooyt.babycommander.hook.SessionMemory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void testRegisterAndGetTools() {
        ToolRegistry registry = new ToolRegistry();
        FileSystemTool fsTool = new FileSystemTool(tempDir.toString(), null);
        ShellTool shellTool = new ShellTool(tempDir.toString(), null);

        registry.registerTool(fsTool);
        registry.registerTool(shellTool);

        List<Object> tools = registry.getAllTools();
        assertEquals(2, tools.size());
        assertTrue(tools.contains(fsTool));
        assertTrue(tools.contains(shellTool));
    }

    @Test
    void testDuplicateRegistrationIgnored() {
        ToolRegistry registry = new ToolRegistry();
        Object mockTool = new Object();

        registry.registerTool(mockTool);
        registry.registerTool(mockTool);
        registry.registerTool(mockTool);

        assertEquals(1, registry.size());
    }

    @Test
    void testRegisterMcpTools() {
        ToolRegistry registry = new ToolRegistry();
        Object mockMcpTool1 = new Object();
        Object mockMcpTool2 = new Object();

        registry.registerMcpTools(List.of(mockMcpTool1, mockMcpTool2));

        List<Object> tools = registry.getAllTools();
        assertEquals(2, tools.size());
    }

    @Test
    void testEmptyRegistry() {
        ToolRegistry registry = new ToolRegistry();
        assertTrue(registry.getAllTools().isEmpty());
        assertEquals(0, registry.size());
    }

    @Test
    void testClear() {
        ToolRegistry registry = new ToolRegistry();
        registry.registerTool(new Object());
        registry.registerTool(new Object());
        assertEquals(2, registry.size());

        registry.clear();

        assertEquals(0, registry.size());
        assertTrue(registry.getAllTools().isEmpty());
    }

    @Test
    void testGetToolsReturnsUnmodifiableList() {
        ToolRegistry registry = new ToolRegistry();
        registry.registerTool(new Object());

        List<Object> tools = registry.getAllTools();
        assertThrows(UnsupportedOperationException.class, () -> tools.add(new Object()));
    }

    @Test
    void testMixedBuiltInAndMcpTools() {
        ToolRegistry registry = new ToolRegistry();
        registry.registerTool(new FileSystemTool(tempDir.toString(), null));
        registry.registerMcpTools(List.of(new Object(), new Object()));

        assertEquals(3, registry.size());
    }

    @Test
    void testUpdateToolPathsPropagatesProjectRootToHookManager() {
        ToolRegistry registry = new ToolRegistry();
        YamlConfigLoader loader = new YamlConfigLoader("agents.yaml") {
            @Override
            public AgentConfig getConfig() { return new AgentConfig(); }
        };
        HookManager hookManager = new HookManager(loader, new SessionMemory());
        registry.hookManager = hookManager;

        assertNull(hookManager.getProjectRoot());

        registry.updateToolPaths(WorkspacePaths.of("/ws", "/ws/proj"));

        assertEquals("/ws/proj", hookManager.getProjectRoot());
    }

    @Test
    void testUpdateToolPathsWithoutHookManagerDoesNotThrow() {
        ToolRegistry registry = new ToolRegistry();
        // hookManager is null (not CDI-injected) — must not throw
        assertDoesNotThrow(() ->
                registry.updateToolPaths(WorkspacePaths.of("/ws", "/ws/proj")));
    }
}
