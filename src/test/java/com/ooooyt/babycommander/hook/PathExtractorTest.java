package com.ooooyt.babycommander.hook;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PathExtractorTest {

    @Test
    void testTokenizeSimple() {
        List<String> tokens = PathExtractor.tokenize("ls -la /home/user");
        assertEquals(List.of("ls", "-la", "/home/user"), tokens);
    }

    @Test
    void testTokenizeWithQuotes() {
        List<String> tokens = PathExtractor.tokenize("grep \"hello world\" /tmp/file.txt");
        assertEquals(List.of("grep", "hello world", "/tmp/file.txt"), tokens);
    }

    @Test
    void testTokenizeWithSingleQuotes() {
        List<String> tokens = PathExtractor.tokenize("find /src -name '*.java'");
        assertEquals(List.of("find", "/src", "-name", "*.java"), tokens);
    }

    @Test
    void testLooksLikePathTrue() {
        assertTrue(PathExtractor.looksLikePath("/home/user/file.txt"));
        assertTrue(PathExtractor.looksLikePath("./relative"));
        assertTrue(PathExtractor.looksLikePath("../parent"));
        assertTrue(PathExtractor.looksLikePath("~/docs"));
        assertTrue(PathExtractor.looksLikePath("src/main/Foo.java"));
    }

    @Test
    void testLooksLikePathFalse() {
        assertFalse(PathExtractor.looksLikePath("ls"));
        assertFalse(PathExtractor.looksLikePath("-la"));
        assertFalse(PathExtractor.looksLikePath("--name"));
        assertFalse(PathExtractor.looksLikePath(""));
        assertFalse(PathExtractor.looksLikePath("  "));
    }

    @Test
    void testNormalizeAbsolutePathWithinWorkspace() {
        String normalized = PathExtractor.normalize("/ws/src/main/Foo.java", "/ws");
        assertEquals("src/main/Foo.java", normalized);
    }

    @Test
    void testNormalizeAbsolutePathOutsideWorkspace() {
        String normalized = PathExtractor.normalize("/etc/passwd", "/ws");
        assertEquals("/etc/passwd", normalized);
    }

    @Test
    void testNormalizeRelativePath() {
        String normalized = PathExtractor.normalize("src/main/../test/Bar.java", "/ws");
        assertEquals("src/test/Bar.java", normalized);
    }

    @Test
    void testNormalizeWorkspaceRootWithoutTrailingSlash() {
        String normalized = PathExtractor.normalize("/ws/src/file.txt", "/ws");
        assertEquals("src/file.txt", normalized);
    }

    @Test
    void testNormalizeNullWorkspace() {
        String normalized = PathExtractor.normalize("/some/path", null);
        assertEquals("/some/path", normalized);
    }

    @Test
    void testNormalizeEmptyWorkspace() {
        String normalized = PathExtractor.normalize("/some/path", "");
        assertEquals("/some/path", normalized);
    }

    @Test
    void testNormalizeExactWorkspaceRootMatch() {
        String normalized = PathExtractor.normalize("/ws", "/ws");
        assertEquals("", normalized);
    }

    @Test
    void testNormalizeNonNormalizedWorkspaceRoot() {
        String normalized = PathExtractor.normalize("/ws/src/file.txt", "/ws/../ws");
        assertEquals("src/file.txt", normalized);
    }

    @Test
    void testIsDescendantOrSelfDirectChild() {
        assertTrue(PathExtractor.isDescendantOrSelf("src/main/Foo.java", "src/main"));
    }

    @Test
    void testIsDescendantOrSelfGrandchild() {
        assertTrue(PathExtractor.isDescendantOrSelf("src/main/com/foo/Bar.java", "src"));
    }

    @Test
    void testIsDescendantOrSelfExactMatch() {
        assertTrue(PathExtractor.isDescendantOrSelf("src/main", "src/main"));
    }

    @Test
    void testIsDescendantOrSelfNotChild() {
        assertFalse(PathExtractor.isDescendantOrSelf("src/test/Foo.java", "src/main"));
    }

    @Test
    void testIsDescendantOrSelfPrefixCollision() {
        assertFalse(PathExtractor.isDescendantOrSelf("src/main2/Foo.java", "src/main"));
    }

    @Test
    void testExtractShellCommandPaths() {
        List<String> paths = PathExtractor.extractFromShellCommand(
            new Object[]{"ls -la /ws/src/main /ws/tests"}, "/ws");
        assertEquals(2, paths.size());
        assertTrue(paths.contains("src/main"));
        assertTrue(paths.contains("tests"));
    }

    @Test
    void testExtractShellCommandNoPaths() {
        List<String> paths = PathExtractor.extractFromShellCommand(
            new Object[]{"mvn clean install"}, "/ws");
        assertTrue(paths.isEmpty());
    }

    @Test
    void testExtractShellCommandMixedArgs() {
        List<String> paths = PathExtractor.extractFromShellCommand(
            new Object[]{"grep -r TODO /ws/src"}, "/ws");
        assertEquals(1, paths.size());
        assertEquals("src", paths.get(0));
    }

    @Test
    void testExtractShellCommandRelativePath() {
        List<String> paths = PathExtractor.extractFromShellCommand(
            new Object[]{"cat ./src/main/Foo.java"}, "/ws");
        assertEquals(1, paths.size());
        assertEquals("src/main/Foo.java", paths.get(0));
    }

    @Test
    void testExtractShellCommandEmptyArgs() {
        List<String> paths = PathExtractor.extractFromShellCommand(new Object[]{}, "/ws");
        assertTrue(paths.isEmpty());
    }

    @Test
    void testExtractShellCommandNonStringArg() {
        List<String> paths = PathExtractor.extractFromShellCommand(new Object[]{42}, "/ws");
        assertTrue(paths.isEmpty());
    }

    @Test
    void testExtractFileSystemToolArgs() {
        List<String> paths = PathExtractor.extractFromFileSystemArgs(
            new Object[]{"/ws/src/main/Foo.java", "content"}, "/ws");
        assertEquals(1, paths.size());
        assertEquals("src/main/Foo.java", paths.get(0));
    }

    @Test
    void testExtractFileSystemToolMultiplePaths() {
        List<String> paths = PathExtractor.extractFromFileSystemArgs(
            new Object[]{"/ws/file1.txt", "/ws/file2.txt"}, "/ws");
        assertEquals(2, paths.size());
    }

    @Test
    void testExtractFileSystemToolNoPaths() {
        List<String> paths = PathExtractor.extractFromFileSystemArgs(
            new Object[]{"not a path", 42}, "/ws");
        assertTrue(paths.isEmpty());
    }

    @Test
    @DisplayName("Top-level extract dispatches to tool-specific extractors")
    void testExtractTopLevel() {
        List<String> paths = PathExtractor.extract(
            "ShellTool", new Object[]{"cat /ws/src/file.txt"}, "/ws");
        assertEquals(List.of("src/file.txt"), paths);
    }

    @Test
    void testExtractTopLevelFileSystem() {
        List<String> paths = PathExtractor.extract(
            "FileSystemTool", new Object[]{"/ws/doc/readme.md", "text"}, "/ws");
        assertEquals(List.of("doc/readme.md"), paths);
    }

    @Test
    void testExtractTopLevelUnknownTool() {
        List<String> paths = PathExtractor.extract(
            "InternetTool", new Object[]{"http://example.com"}, "/ws");
        assertTrue(paths.isEmpty());
    }

    @Test
    void testExtractTopLevelNullArgs() {
        List<String> paths = PathExtractor.extract("ShellTool", null, "/ws");
        assertTrue(paths.isEmpty());
    }

    @Test
    void testExtractTopLevelEmptyArgs() {
        List<String> paths = PathExtractor.extract("ShellTool", new Object[0], "/ws");
        assertTrue(paths.isEmpty());
    }

    @Test
    void testToParentDirectoryFromFile() {
        assertEquals("src/main", PathExtractor.toParentDirectory("src/main/Foo.java"));
    }

    @Test
    void testToParentDirectoryFromDirectory() {
        assertEquals("src/main", PathExtractor.toParentDirectory("src/main/"));
    }

    @Test
    void testToParentDirectoryFromDirectoryNoTrailingSlash() {
        assertEquals("src/main", PathExtractor.toParentDirectory("src/main"));
    }

    @Test
    void testToParentDirectoryFromRootFile() {
        assertNull(PathExtractor.toParentDirectory("README.md"));
    }

    @Test
    void testToParentDirectoryNull() {
        assertNull(PathExtractor.toParentDirectory(null));
    }

    @Test
    void testToParentDirectoryBlank() {
        assertNull(PathExtractor.toParentDirectory("  "));
    }

    @Test
    void testLooksLikePathExcludesFlags() {
        assertFalse(PathExtractor.looksLikePath("--prefix=/usr/local"));
        assertFalse(PathExtractor.looksLikePath("-I/usr/include"));
        assertFalse(PathExtractor.looksLikePath("-o/tmp/out"));
    }

    // ========== firstPath Tests ==========

    @Test
    void testFirstPathAbsoluteInsideWorkspace() {
        assertEquals("proj/src/Foo.java",
                PathExtractor.firstPath("FileSystemTool", new Object[]{"/ws/proj/src/Foo.java"}, "/ws", "/ws/proj"));
    }

    @Test
    void testFirstPathRelativeResolvedAgainstProjectRoot() {
        assertEquals("proj/src/Foo.java",
                PathExtractor.firstPath("FileSystemTool", new Object[]{"src/Foo.java"}, "/ws", "/ws/proj"));
    }

    @Test
    void testFirstPathBareFilenameResolvedAgainstProjectRoot() {
        assertEquals("proj/file.txt",
                PathExtractor.firstPath("FileSystemTool", new Object[]{"file.txt"}, "/ws", "/ws/proj"));
    }

    @Test
    void testFirstPathRelativeAndAbsoluteProduceSameKey() {
        String abs = PathExtractor.firstPath("FileSystemTool", new Object[]{"/ws/proj/src/Foo.java"}, "/ws", "/ws/proj");
        String rel = PathExtractor.firstPath("FileSystemTool", new Object[]{"src/Foo.java"}, "/ws", "/ws/proj");
        assertEquals(abs, rel);
    }

    @Test
    void testFirstPathHomePathNotResolvedAgainstProjectRoot() {
        String expected = java.nio.file.Path.of(System.getProperty("user.home"), "x.txt").normalize().toString();
        assertEquals(expected,
                PathExtractor.firstPath("FileSystemTool", new Object[]{"~/x.txt"}, "/ws", "/ws/proj"));
    }

    @Test
    void testFirstPathOutsideWorkspaceKeptAbsolute() {
        assertEquals("/tmp/other.txt",
                PathExtractor.firstPath("FileSystemTool", new Object[]{"/tmp/other.txt"}, "/ws", "/ws/proj"));
    }

    @Test
    void testFirstPathOtherToolReturnsNull() {
        assertNull(PathExtractor.firstPath("ShellTool", new Object[]{"cat /ws/proj/src/Foo.java"}, "/ws", "/ws/proj"));
        assertNull(PathExtractor.firstPath("InternetTool", new Object[]{"/ws/proj/src/Foo.java"}, "/ws", "/ws/proj"));
    }

    @Test
    void testFirstPathNullOrEmptyArgsReturnsNull() {
        assertNull(PathExtractor.firstPath("FileSystemTool", null, "/ws", "/ws/proj"));
        assertNull(PathExtractor.firstPath("FileSystemTool", new Object[]{}, "/ws", "/ws/proj"));
    }

    @Test
    void testFirstPathBlankOrNonStringFirstArgReturnsNull() {
        assertNull(PathExtractor.firstPath("FileSystemTool", new Object[]{"  "}, "/ws", "/ws/proj"));
        assertNull(PathExtractor.firstPath("FileSystemTool", new Object[]{42}, "/ws", "/ws/proj"));
        assertNull(PathExtractor.firstPath("FileSystemTool", new Object[]{null}, "/ws", "/ws/proj"));
    }

    @Test
    void testFirstPathWithoutProjectRootKeepsRelative() {
        assertEquals("src/Foo.java",
                PathExtractor.firstPath("FileSystemTool", new Object[]{"src/Foo.java"}, "/ws", null));
    }
}
