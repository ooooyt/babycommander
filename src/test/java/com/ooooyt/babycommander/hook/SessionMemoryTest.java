package com.ooooyt.babycommander.hook;

import java.util.List;

import com.ooooyt.babycommander.db.entity.HookAnswerEntity;
import com.ooooyt.babycommander.db.repository.HookAnswerRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class SessionMemoryTest {

    private static final Object[] EMPTY_ARGS = new Object[0];

    @Test
    void testNotAllowedByDefault() {
        SessionMemory memory = new SessionMemory();
        assertFalse(memory.isAllowedAlways("session-1", "ShellTool", EMPTY_ARGS));
    }

    @Test
    void testMarkAndCheckAllowed() {
        SessionMemory memory = new SessionMemory();
        memory.markAllowedAlways("session-1", "ShellTool", null, EMPTY_ARGS);
        assertTrue(memory.isAllowedAlways("session-1", "ShellTool", EMPTY_ARGS));
    }

    @Test
    void testSessionsAreIsolated() {
        SessionMemory memory = new SessionMemory();
        memory.markAllowedAlways("session-1", "ShellTool", null, EMPTY_ARGS);
        assertFalse(memory.isAllowedAlways("session-2", "ShellTool", EMPTY_ARGS));
    }

    @Test
    void testMultipleToolsPerSession() {
        SessionMemory memory = new SessionMemory();
        memory.markAllowedAlways("session-1", "ShellTool", null, EMPTY_ARGS);
        memory.markAllowedAlways("session-1", "FileSystemTool", null, EMPTY_ARGS);
        assertTrue(memory.isAllowedAlways("session-1", "ShellTool", EMPTY_ARGS));
        assertTrue(memory.isAllowedAlways("session-1", "FileSystemTool", EMPTY_ARGS));
        assertFalse(memory.isAllowedAlways("session-1", "InternetTool", EMPTY_ARGS));
    }

    @Test
    void testClearSession() {
        SessionMemory memory = new SessionMemory();
        memory.markAllowedAlways("session-1", "ShellTool", null, EMPTY_ARGS);
        memory.clearSession("session-1");
        assertFalse(memory.isAllowedAlways("session-1", "ShellTool", EMPTY_ARGS));
    }

    @Test
    void testClearNonexistentSessionDoesNotThrow() {
        SessionMemory memory = new SessionMemory();
        assertDoesNotThrow(() -> memory.clearSession("nonexistent"));
    }

    @Test
    void testExactMatchGranularity() {
        SessionMemory memory = new SessionMemory();
        Object[] args1 = new Object[]{"ls -la"};
        Object[] args2 = new Object[]{"rm -rf /"};

        memory.markAllowedAlways("s1", "ShellTool", null, args1);
        assertTrue(memory.isAllowedAlways("s1", "ShellTool", args1));
        assertFalse(memory.isAllowedAlways("s1", "ShellTool", args2));
    }

    @Test
    void testNullArgs() {
        SessionMemory memory = new SessionMemory();
        memory.markAllowedAlways("s1", "ShellTool", null, null);
        assertTrue(memory.isAllowedAlways("s1", "ShellTool", null));
        // null args and empty args produce the same signature "ShellTool::"
        assertTrue(memory.isAllowedAlways("s1", "ShellTool", new Object[0]));
    }

    @Test
    void testBuildSignature() {
        String sig = SessionMemory.buildSignature("ShellTool", new Object[]{"mvn test", "src"});
        assertEquals("ShellTool::mvn test|src", sig);
    }

    @Test
    void testBuildSignatureWithNullArgs() {
        String sig = SessionMemory.buildSignature("ShellTool", null);
        assertEquals("ShellTool::", sig);
    }

    @Test
    void testBuildSignatureWithEmptyArgs() {
        String sig = SessionMemory.buildSignature("ShellTool", new Object[0]);
        assertEquals("ShellTool::", sig);
    }

    // ========== Method-Level Trust Tests ==========

    @Test
    void testMethodTrustedAfterMark() {
        SessionMemory memory = new SessionMemory();
        memory.markMethodAllowed("s1", "ShellTool", "execute");
        assertTrue(memory.isMethodAllowed("s1", "ShellTool", "execute"));
    }

    @Test
    void testMethodNotTrustedByDefault() {
        SessionMemory memory = new SessionMemory();
        assertFalse(memory.isMethodAllowed("s1", "ShellTool", "execute"));
    }

    @Test
    void testMethodTrustIsSessionIsolated() {
        SessionMemory memory = new SessionMemory();
        memory.markMethodAllowed("s1", "ShellTool", "execute");
        assertFalse(memory.isMethodAllowed("s2", "ShellTool", "execute"));
        assertTrue(memory.isMethodAllowed("s1", "ShellTool", "execute"));
    }

    @Test
    void testMethodTrustDifferentMethods() {
        SessionMemory memory = new SessionMemory();
        memory.markMethodAllowed("s1", "ShellTool", "execute");
        assertFalse(memory.isMethodAllowed("s1", "ShellTool", "otherMethod"));
        assertFalse(memory.isMethodAllowed("s1", "FileSystemTool", "execute"));
    }

    // ========== Path-Level Trust Tests ==========

    @Test
    void testPathTrustedDirectChild() {
        SessionMemory memory = new SessionMemory();
        memory.addTrustedPaths("s1", List.of("src/main"));
        assertTrue(memory.isPathTrusted("s1", "src/main/Foo.java"));
    }

    @Test
    void testPathTrustedGrandchild() {
        SessionMemory memory = new SessionMemory();
        memory.addTrustedPaths("s1", List.of("src"));
        assertTrue(memory.isPathTrusted("s1", "src/main/com/foo/Bar.java"));
    }

    @Test
    void testPathNotTrustedOutsidePrefix() {
        SessionMemory memory = new SessionMemory();
        memory.addTrustedPaths("s1", List.of("src/main"));
        assertFalse(memory.isPathTrusted("s1", "src/test/Foo.java"));
    }

    @Test
    void testPathTrustedExactMatch() {
        SessionMemory memory = new SessionMemory();
        memory.addTrustedPaths("s1", List.of("src/main"));
        assertTrue(memory.isPathTrusted("s1", "src/main"));
    }

    @Test
    void testPathNotTrustedByDefault() {
        SessionMemory memory = new SessionMemory();
        assertFalse(memory.isPathTrusted("s1", "src/main/Foo.java"));
    }

    @Test
    void testPathTrustSessionIsolated() {
        SessionMemory memory = new SessionMemory();
        memory.addTrustedPaths("s1", List.of("src"));
        assertFalse(memory.isPathTrusted("s2", "src/Foo.java"));
        assertTrue(memory.isPathTrusted("s1", "src/Foo.java"));
    }

    @Test
    void testAddTrustedPathsNullCollection() {
        SessionMemory memory = new SessionMemory();
        assertDoesNotThrow(() -> memory.addTrustedPaths("s1", null));
        assertFalse(memory.isPathTrusted("s1", "anything"));
    }

    @Test
    void testAddTrustedPathsEmptyCollection() {
        SessionMemory memory = new SessionMemory();
        assertDoesNotThrow(() -> memory.addTrustedPaths("s1", List.of()));
        assertFalse(memory.isPathTrusted("s1", "anything"));
    }

    // ========== ClearSession Clears All Maps ==========

    @Test
    void testClearSessionClearsAllTrustMaps() {
        SessionMemory memory = new SessionMemory();
        memory.markAllowedAlways("s1", "ShellTool", null, new Object[]{"ls"});
        memory.markMethodAllowed("s1", "ShellTool", "execute");
        memory.addTrustedPaths("s1", List.of("src/main"));

        memory.clearSession("s1");

        assertFalse(memory.isAllowedAlways("s1", "ShellTool", new Object[]{"ls"}));
        assertFalse(memory.isMethodAllowed("s1", "ShellTool", "execute"));
        assertFalse(memory.isPathTrusted("s1", "src/main/Foo.java"));
    }

    // ========== Path-Scoped Grant (PATH_ALLOW_ALWAYS) Tests ==========

    @Test
    void testPathGrantRoundTrip() {
        SessionMemory memory = new SessionMemory();
        memory.markPathAllowedAlways("s1", "FileSystemTool", "write_file", "proj/src/Foo.java");
        assertTrue(memory.isPathAllowedAlways("s1", "FileSystemTool", "proj/src/Foo.java"));
    }

    @Test
    void testPathGrantKeyIncludesToolName() {
        SessionMemory memory = new SessionMemory();
        memory.markPathAllowedAlways("s1", "FileSystemTool", "write_file", "proj/src/Foo.java");
        // Same path, different tool — must NOT match
        assertFalse(memory.isPathAllowedAlways("s1", "ShellTool", "proj/src/Foo.java"));
    }

    @Test
    void testPathGrantSessionsAreIsolated() {
        SessionMemory memory = new SessionMemory();
        memory.markPathAllowedAlways("s1", "FileSystemTool", "write_file", "proj/src/Foo.java");
        assertFalse(memory.isPathAllowedAlways("s2", "FileSystemTool", "proj/src/Foo.java"));
    }

    @Test
    void testPathGrantNullPathReturnsFalse() {
        SessionMemory memory = new SessionMemory();
        assertFalse(memory.isPathAllowedAlways("s1", "FileSystemTool", null));
    }

    @Test
    void testClearSessionClearsPathGrants() {
        SessionMemory memory = new SessionMemory();
        memory.markPathAllowedAlways("s1", "FileSystemTool", "write_file", "proj/src/Foo.java");
        memory.clearSession("s1");
        assertFalse(memory.isPathAllowedAlways("s1", "FileSystemTool", "proj/src/Foo.java"));
    }

    @Test
    void testLoadSessionRestoresPathGrants() {
        SessionMemory memory = new SessionMemory();
        HookAnswerRepository repo = Mockito.mock(HookAnswerRepository.class);
        HookAnswerEntity entity = new HookAnswerEntity(
                "s1", "FileSystemTool", "write_file", "proj/src/Foo.java", "PATH_ALLOW_ALWAYS");
        Mockito.when(repo.findBySession("s1")).thenReturn(List.of(entity));
        memory.hookAnswerRepository = repo;

        memory.loadSession("s1");

        assertTrue(memory.isPathAllowedAlways("s1", "FileSystemTool", "proj/src/Foo.java"));
        // Tool name is part of the key
        assertFalse(memory.isPathAllowedAlways("s1", "ShellTool", "proj/src/Foo.java"));
    }

    @Test
    void testMarkPathAllowedAlwaysPersistsEntity() {
        SessionMemory memory = new SessionMemory();
        HookAnswerRepository repo = Mockito.mock(HookAnswerRepository.class);
        memory.hookAnswerRepository = repo;

        memory.markPathAllowedAlways("s1", "FileSystemTool", "write_file", "proj/src/Foo.java");

        org.mockito.ArgumentCaptor<HookAnswerEntity> captor =
                org.mockito.ArgumentCaptor.forClass(HookAnswerEntity.class);
        Mockito.verify(repo).save(captor.capture());
        HookAnswerEntity saved = captor.getValue();
        assertEquals("PATH_ALLOW_ALWAYS", saved.answerType);
        assertEquals("FileSystemTool", saved.toolName);
        assertEquals("write_file", saved.methodName);
        assertEquals("proj/src/Foo.java", saved.argsSignature);
        assertEquals("s1", saved.sessionId);
    }
}
