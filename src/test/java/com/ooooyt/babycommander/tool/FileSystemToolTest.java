package com.ooooyt.babycommander.tool;

import com.ooooyt.babycommander.util.I18n;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class FileSystemToolTest {

    @TempDir
    Path tempDir;

    private FileSystemTool tool;

    @BeforeAll
    static void setupLocale() {
        I18n.setLocale(Locale.ENGLISH);
    }

    @BeforeEach
    void setUp() {
        tool = new FileSystemTool(tempDir.toString(), null);
    }

    // ===== Existing tests (kept) =====

    @Test
    void testWriteAndReadFile() {
        String result = tool.writeFile("test.txt", "hello world");
        assertTrue(result.contains("Successfully wrote"));

        String content = tool.readFile("test.txt");
        assertEquals("hello world", content);
    }

    @Test
    void testReadFileRange() {
        String content = "line1\nline2\nline3\nline4\nline5";
        tool.writeFile("large.txt", content);

        String range = tool.readFileRange("large.txt", 2, 4);
        assertTrue(range.contains("line2"));
        assertTrue(range.contains("line3"));
        assertTrue(range.contains("line4"));
        assertFalse(range.contains("line5"));
    }

    @Test
    void testReadFileRangeOutOfRange() {
        tool.writeFile("small.txt", "line1\nline2");

        String range = tool.readFileRange("small.txt", 1, 100);
        assertTrue(range.contains("line1"));
        assertTrue(range.contains("line2"));
    }

    @Test
    void testListDirectory() throws Exception {
        tool.writeFile("a.txt", "one");
        tool.writeFile("b.txt", "two");
        Files.createDirectory(tempDir.resolve("subdir"));

        String listing = tool.listDirectory(".");
        assertTrue(listing.contains("a.txt"));
        assertTrue(listing.contains("b.txt"));
        assertTrue(listing.contains("subdir"));
    }

    @Test
    void testDeleteFile() {
        tool.writeFile("to_delete.txt", "gone");
        assertTrue(tool.pathExists("to_delete.txt"));

        String result = tool.deleteFile("to_delete.txt");
        assertTrue(result.contains("Successfully deleted"));
        assertFalse(tool.pathExists("to_delete.txt"));
    }

    @Test
    void testPathExists() {
        tool.writeFile("exists.txt", "yes");
        assertTrue(tool.pathExists("exists.txt"));
        assertFalse(tool.pathExists("nope.txt"));
    }

    @Test
    void testIsDirectory() throws Exception {
        Files.createDirectory(tempDir.resolve("mydir"));
        tool.writeFile("myfile.txt", "test");

        assertTrue(tool.isDirectory("mydir"));
        assertFalse(tool.isDirectory("myfile.txt"));
        assertFalse(tool.isDirectory("nonexistent"));
    }

    @Test
    void testSearchFiles() {
        tool.writeFile("src/Main.java", "public class Main {\n    public static void main(String[] args) {\n    }\n}");
        tool.writeFile("src/Util.java", "class Util {\n}");

        String results = tool.searchFiles(".", "public class");
        assertTrue(results.contains("Main.java"));
    }

    @Test
    void testWriteFileCreatesParentDirectories() {
        String result = tool.writeFile("deep/nested/dir/file.txt", "content");
        assertTrue(result.contains("Successfully wrote"));
        assertTrue(tool.pathExists("deep/nested/dir/file.txt"));
    }

    @Test
    void testReadNonExistentFile() {
        String result = tool.readFile("nonexistent_file_xyz.txt");
        assertTrue(result.contains("Error"), "Should return error for non-existent file");
    }

    @Test
    void testGetProjectFolder() {
        String projectFolder = tool.getProjectFolder();
        assertNotNull(projectFolder);
        assertEquals(tempDir.toString(), projectFolder);
    }

    // ===== New tests for uncovered methods =====

    // --- setMaxOutputLength ---

    @Test
    void testSetMaxOutputLength() {
        tool.setMaxOutputLength(10);
        String content = "This is a long file content that should be truncated";
        tool.writeFile("truncated.txt", content);
        String result = tool.readFile("truncated.txt");
        assertTrue(result.contains("truncated"), "Output should indicate truncation");
    }

    // --- setProjectFolder / setWorkspace / getProjectRoot / getWorkspaceFolder ---

    @Test
    void testSetProjectFolder() {
        tool.setProjectFolder("/tmp/new-workspace");
        assertEquals("/tmp/new-workspace", tool.getProjectFolder());
    }

    @Test
    void testSetWorkspace() {
        tool.setWorkspace("/custom/workspace");
        assertEquals("/custom/workspace", tool.getWorkspaceFolder());
    }

    @Test
    void testGetProjectRoot() {
        assertEquals(tempDir, tool.getProjectRoot());
    }

    @Test
    void testGetWorkspaceFolder() {
        assertEquals(tempDir.toString(), tool.getWorkspaceFolder());
    }

    @Test
    void testGetWorkspaceFolderAfterSetWorkspace() {
        tool.setWorkspace("/other/path");
        assertEquals("/other/path", tool.getWorkspaceFolder());
    }

    // --- readFileRange edge cases ---

    @Test
    void testReadFileRange_NonExistentFile() {
        String result = tool.readFileRange("nonexistent.txt", 1, 10);
        assertTrue(result.contains("Error"), "Should return error for non-existent file");
    }

    @Test
    void testReadFileRange_StartLineLessThanOne() {
        tool.writeFile("test.txt", "line1\nline2");
        String result = tool.readFileRange("test.txt", 0, 2);
        assertTrue(result.contains("Error"), "Should return error when startLine < 1");
    }

    @Test
    void testReadFileRange_StartLineExceedsFile() {
        tool.writeFile("test.txt", "line1");
        String result = tool.readFileRange("test.txt", 5, 10);
        assertTrue(result.contains("Error"), "Should return error when startLine exceeds file length");
    }

    @Test
    void testReadFileRange_EndLineLessThanStartLine() {
        tool.writeFile("test.txt", "line1\nline2\nline3");
        String result = tool.readFileRange("test.txt", 3, 1);
        assertTrue(result.contains("Error"), "Should return error when endLine < startLine");
    }

    @Test
    void testReadFileRange_SingleLine() {
        tool.writeFile("test.txt", "only line");
        String result = tool.readFileRange("test.txt", 1, 1);
        assertEquals("only line", result);
    }

    // --- listDirectory edge cases ---

    @Test
    void testListDirectory_NonExistent() {
        String result = tool.listDirectory("nonexistent");
        assertTrue(result.contains("Error"), "Should return error for non-existent directory");
    }

    @Test
    void testListDirectory_FileNotDirectory() {
        tool.writeFile("afile.txt", "content");
        String result = tool.listDirectory("afile.txt");
        assertTrue(result.contains("Error"), "Should return error when path is a file, not directory");
    }

    @Test
    void testListDirectory_EmptyDirectory() throws Exception {
        Files.createDirectory(tempDir.resolve("emptydir"));
        String result = tool.listDirectory("emptydir");
        assertTrue(result.contains("empty") || result.contains("Empty"),
            "Should indicate directory is empty");
    }

    // --- deleteFile edge cases ---

    @Test
    void testDeleteFile_NonExistent() {
        String result = tool.deleteFile("nonexistent.txt");
        assertTrue(result.contains("Error"), "Should return error for non-existent file");
    }

    @Test
    void testDeleteFile_Directory() throws Exception {
        Files.createDirectory(tempDir.resolve("mydir"));
        tool.writeFile("mydir/inside.txt", "content");
        String result = tool.deleteFile("mydir");
        assertTrue(result.contains("Successfully deleted") || result.contains("deleted"),
            "Should successfully delete directory");
        assertFalse(tool.pathExists("mydir/inside.txt"));
    }

    @Test
    void testDeleteFile_PathEscapeAttempt() {
        String result = tool.deleteFile("../outside.txt");
        assertTrue(result.contains("Error"), "Should reject path escape attempts");
    }

    // --- searchFiles edge cases ---

    @Test
    void testSearchFiles_NoMatch() {
        tool.writeFile("file.txt", "hello world");
        String result = tool.searchFiles(".", "nonexistent_pattern_xyz");
        assertTrue(result.contains("No matches") || result.contains("no matches"),
            "Should indicate no matches found");
    }

    @Test
    void testSearchFiles_NonExistentPath() {
        String result = tool.searchFiles("nonexistent", "pattern");
        assertTrue(result.contains("Error"), "Should return error for non-existent search path");
    }

    @Test
    void testSearchFiles_MultipleFiles() {
        tool.writeFile("a.txt", "common pattern here");
        tool.writeFile("b.txt", "also has common pattern");
        tool.writeFile("c.txt", "no match");
        String result = tool.searchFiles(".", "common pattern");
        assertTrue(result.contains("a.txt"));
        assertTrue(result.contains("b.txt"));
        assertFalse(result.contains("c.txt"));
    }

    @Test
    void testSearchFiles_MultipleMatchesInOneFile() {
        tool.writeFile("multi.txt", "pattern\nother\npattern\nend");
        String result = tool.searchFiles(".", "pattern");
        long count = result.lines().filter(l -> l.contains("multi.txt")).count();
        assertEquals(2, count, "Should find both occurrences");
    }

    // --- extractSkeleton ---

    @Test
    void testExtractSkeleton() {
        tool.writeFile("Demo.java", "public class Demo {\n    public void foo() { }\n    private int bar() { return 1; }\n}");
        String result = tool.extractSkeleton("Demo.java");
        assertNotNull(result);
        assertTrue(result.contains("Demo") || result.contains("foo"),
            "Skeleton should contain class or method names");
    }

    @Test
    void testExtractSkeleton_NonExistentFile() {
        String result = tool.extractSkeleton("nonexistent.java");
        assertTrue(result.contains("Error"), "Should return error for non-existent file");
    }

    // --- extractPackageSkeleton ---

    @Test
    void testExtractPackageSkeleton() {
        tool.writeFile("pkg/Main.java", "class Main { }");
        tool.writeFile("pkg/Util.java", "class Util { }");
        String result = tool.extractPackageSkeleton("pkg");
        assertNotNull(result);
        assertTrue(result.contains("Main.java") || result.contains("Main"),
            "Should contain source file names");
    }

    @Test
    void testExtractPackageSkeleton_NonExistentDir() {
        String result = tool.extractPackageSkeleton("nonexistent");
        assertTrue(result.contains("Error"), "Should return error for non-existent directory");
    }

    @Test
    void testExtractPackageSkeleton_FileNotDir() {
        tool.writeFile("afile.txt", "content");
        String result = tool.extractPackageSkeleton("afile.txt");
        assertTrue(result.contains("Error"), "Should return error when path is a file");
    }

    @Test
    void testExtractPackageSkeleton_EmptyDir() throws Exception {
        Files.createDirectory(tempDir.resolve("emptypkg"));
        String result = tool.extractPackageSkeleton("emptypkg");
        assertTrue(result.contains("No source") || result.contains("no source") || result.contains("empty"),
            "Should indicate no source files found");
    }

    // --- readFunction ---

    @Test
    void testReadFunction() {
        tool.writeFile("Calc.java", "class Calc {\n    int add(int a, int b) { return a + b; }\n}");
        String result = tool.readFunction("Calc.java", "add");
        assertNotNull(result);
        assertTrue(result.contains("add") || result.contains("return"),
            "Should contain function body");
    }

    @Test
    void testReadFunction_NonExistentFile() {
        String result = tool.readFunction("nonexistent.java", "foo");
        assertTrue(result.contains("Error"), "Should return error for non-existent file");
    }

    // --- readFunctionByParamCount ---

    @Test
    void testReadFunctionByParamCount() {
        tool.writeFile("Overload.java", "class Overload {\n    int calc(int a) { return a; }\n    int calc(int a, int b) { return a + b; }\n}");
        String result = tool.readFunctionByParamCount("Overload.java", "calc", 2);
        assertNotNull(result);
        assertTrue(result.contains("a + b") || result.contains("calc"),
            "Should find the 2-parameter overload");
    }

    @Test
    void testReadFunctionByParamCount_NonExistentFile() {
        String result = tool.readFunctionByParamCount("nonexistent.java", "foo", 0);
        assertTrue(result.contains("Error"), "Should return error for non-existent file");
    }

    // --- applyFilePatch ---

    @Test
    void testApplyFilePatch_DeleteLine() {
        tool.writeFile("patch.txt", "line1\nline2\nline3");
        String[] patches = new String[]{"@@ -2,1 +2,0 D"};
        String result = tool.applyFilePatch("patch.txt", patches);
        assertNotNull(result);
        String content = tool.readFile("patch.txt");
        assertFalse(content.contains("line2"), "line2 should have been removed");
    }

    @Test
    void testApplyFilePatch_NonExistentFile() {
        String[] patches = new String[]{"@@ -1 +2 A\nnew line"};
        String result = tool.applyFilePatch("nonexistent.txt", patches);
        assertTrue(result.contains("Error") || result.contains("not found"), "Should return error for non-existent file");
    }

    @Test
    void testApplyFilePatch_AppendLine() {
        tool.writeFile("append.txt", "line1\nline2");
        String[] patches = new String[]{"@@ -2 +3 A\nline3"};
        String result = tool.applyFilePatch("append.txt", patches);
        assertNotNull(result);
        String content = tool.readFile("append.txt");
        assertTrue(content.contains("line3"), "line3 should have been appended");
    }

    @Test
    void testApplyFilePatch_InsertLine() {
        tool.writeFile("insert.txt", "line1\nline3");
        String[] patches = new String[]{"@@ -2 +2 I\nline2"};
        String result = tool.applyFilePatch("insert.txt", patches);
        assertNotNull(result);
        String content = tool.readFile("insert.txt");
        assertTrue(content.contains("line2"), "line2 should have been inserted");
    }

    @Test
    void testApplyFilePatch_ReplaceLine() {
        tool.writeFile("replace.txt", "line1\nold\nline3");
        String[] patches = new String[]{"@@ -2,1 +2,1 R\nnew"};
        String result = tool.applyFilePatch("replace.txt", patches);
        assertNotNull(result);
        String content = tool.readFile("replace.txt");
        assertTrue(content.contains("new"), "new content should be present");
        assertFalse(content.contains("old"), "old content should be gone");
    }

    // --- applyUnifiedDiffPatch ---

    @Test
    void testApplyUnifiedDiffPatch() {
        tool.writeFile("diff.txt", "hello\nworld");
        String diff = "--- a/diff.txt\n+++ b/diff.txt\n@@ -1,2 +1,2 @@\n hello\n-world\n+earth\n";
        String result = tool.applyUnifiedDiffPatch("diff.txt", diff);
        assertNotNull(result);
        String content = tool.readFile("diff.txt");
        assertTrue(content.contains("earth"), "earth should replace world");
        assertFalse(content.contains("world"), "world should be replaced");
    }

    @Test
    void testApplyUnifiedDiffPatch_NonExistentFile() {
        String result = tool.applyUnifiedDiffPatch("nonexistent.txt", "--- a/nonexistent.txt\n+++ b/nonexistent.txt\n@@ -0,0 +1 @@\n+new\n");
        assertTrue(result.contains("Error") || result.contains("not found"), "Should return error for non-existent file");
    }

    // --- path escape attempts ---

    @Test
    void testReadFile_PathEscapeAttempt() {
        String result = tool.readFile("../etc/passwd");
        assertTrue(result.contains("Error"), "Should reject path escape attempts");
    }

    @Test
    void testWriteFile_PathEscapeAttempt() {
        String result = tool.writeFile("../../outside.txt", "content");
        assertTrue(result.contains("Error"), "Should reject path escape attempts");
    }

    @Test
    void testListDirectory_PathEscapeAttempt() {
        String result = tool.listDirectory("..");
        assertTrue(result.contains("Error"), "Should reject path escape attempts");
    }

    @Test
    void testSearchFiles_PathEscapeAttempt() {
        String result = tool.searchFiles("../", "pattern");
        assertTrue(result.contains("Error"), "Should reject path escape attempts");
    }

    @Test
    void testExtractSkeleton_PathEscapeAttempt() {
        String result = tool.extractSkeleton("../outside.java");
        assertTrue(result.contains("Error"), "Should reject path escape attempts");
    }

    @Test
    void testReadFunction_PathEscapeAttempt() {
        String result = tool.readFunction("../outside.java", "main");
        assertTrue(result.contains("Error"), "Should reject path escape attempts");
    }

    @Test
    void testApplyFilePatch_PathEscapeAttempt() {
        String[] patches = new String[]{"@@ -1 +2 A\nnew"};
        String result = tool.applyFilePatch("../outside.txt", patches);
        assertTrue(result.contains("Error"), "Should reject path escape attempts");
    }

    @Test
    void testApplyUnifiedDiffPatch_PathEscapeAttempt() {
        String result = tool.applyUnifiedDiffPatch("../outside.txt", "--- a/outside.txt\n+++ b/outside.txt\n@@ -0,0 +1 @@\n+new\n");
        assertTrue(result.contains("Error"), "Should reject path escape attempts");
    }

    // --- readFile with truncation ---

    @Test
    void testReadFile_TruncatedAtMaxOutputLength() {
        tool.setMaxOutputLength(50);
        String longContent = "A".repeat(200);
        tool.writeFile("long.txt", longContent);
        String result = tool.readFile("long.txt");
        assertTrue(result.contains("truncated"), "Should indicate truncation when content exceeds maxOutputLength");
        assertTrue(result.length() < 200, "Truncated content should be shorter than original");
    }

    @Test
    void testReadFile_NotTruncatedWhenWithinLimit() {
        tool.setMaxOutputLength(5000);
        String content = "Short content";
        tool.writeFile("short.txt", content);
        String result = tool.readFile("short.txt");
        assertFalse(result.contains("truncated"), "Should not truncate when within limit");
        assertEquals(content, result);
    }

    // --- writeFile with path escape ---

    @Test
    void testWriteFile_AbsolutePathWithinProject() {
        String result = tool.writeFile(tempDir.resolve("sub/ok.txt").toString(), "content");
        assertTrue(result.contains("Successfully wrote"), "Absolute path within project should work");
    }

    // --- delete directory with files ---

    @Test
    void testDeleteFile_EmptyDirectory() throws Exception {
        Files.createDirectory(tempDir.resolve("emptydir"));
        String result = tool.deleteFile("emptydir");
        assertTrue(result.contains("Successfully deleted") || result.contains("deleted"),
            "Should successfully delete empty directory");
        assertFalse(tool.pathExists("emptydir"));
    }

    // --- isDirectory with path escape ---

    @Test
    void testIsDirectory_PathEscapeAttempt() {
        assertFalse(tool.isDirectory("../etc"));
    }

    // --- pathExists with path escape ---

    @Test
    void testPathExists_PathEscapeAttempt() {
        assertFalse(tool.pathExists("../etc/passwd"));
    }

    // --- extractPackageSkeleton with mixed content ---

    @Test
    void testExtractPackageSkeleton_WithNonSourceFiles() throws Exception {
        Files.createDirectory(tempDir.resolve("mixed"));
        tool.writeFile("mixed/Main.java", "class Main { }");
        tool.writeFile("mixed/data.json", "{}");
        tool.writeFile("mixed/notes.txt", "hello");
        String result = tool.extractPackageSkeleton("mixed");
        assertNotNull(result);
        assertTrue(result.contains("Main.java"), "Should include Java files");
    }

    // ===== Binary file tools =====

    @Test
    void testWriteAndReadBinaryFile() {
        byte[] data = new byte[]{0, 1, 2, (byte) 0xFF, 0x7F, (byte) 0x80};
        String base64 = java.util.Base64.getEncoder().encodeToString(data);

        String writeResult = tool.writeBinaryFile("data.bin", base64, 0);
        assertTrue(writeResult.contains("Successfully wrote"), writeResult);

        String readResult = tool.readBinaryFile("data.bin", 0, 0);
        assertTrue(readResult.contains(base64), readResult);
        assertTrue(readResult.contains("6 bytes"), readResult);
    }

    @Test
    void testReadBinaryFilePartial() throws Exception {
        byte[] data = "0123456789".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(tempDir.resolve("num.bin"), data);

        // Read 4 bytes starting at offset 3 -> "3456"
        String result = tool.readBinaryFile("num.bin", 3, 4);
        String expected = java.util.Base64.getEncoder().encodeToString("3456".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(result.contains(expected), result);
        assertTrue(result.contains("4 bytes"), result);
        assertTrue(result.contains("offset 3"), result);
    }

    @Test
    void testReadBinaryFileOffsetBeyondSize() throws Exception {
        Files.write(tempDir.resolve("small.bin"), "ab".getBytes());
        String result = tool.readBinaryFile("small.bin", 100, 0);
        assertTrue(result.contains("0 bytes"), result);
    }

    @Test
    void testReadBinaryFileNotFound() {
        String result = tool.readBinaryFile("missing.bin", 0, 0);
        assertTrue(result.contains("not found"), result);
    }

    @Test
    void testReadBinaryFileInvalidOffset() throws Exception {
        Files.write(tempDir.resolve("x.bin"), "abc".getBytes());
        String result = tool.readBinaryFile("x.bin", -5, 0);
        assertTrue(result.contains("invalid offset"), result);
    }

    @Test
    void testWriteBinaryFileAtOffsetOverwrites() throws Exception {
        Files.write(tempDir.resolve("ow.bin"), "AAAAAA".getBytes());
        // "QkM=" is Base64 for "BC"
        String result = tool.writeBinaryFile("ow.bin", "QkM=", 2);
        assertTrue(result.contains("Successfully wrote"), result);
        byte[] content = Files.readAllBytes(tempDir.resolve("ow.bin"));
        assertEquals("AABCAA", new String(content, java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void testWriteBinaryFileCreatesParentDirs() {
        String result = tool.writeBinaryFile("deep/nested/dir/f.bin", "AQID", 0);
        assertTrue(result.contains("Successfully wrote"), result);
        assertTrue(tool.pathExists("deep/nested/dir/f.bin"));
    }

    @Test
    void testWriteBinaryFileInvalidBase64() {
        String result = tool.writeBinaryFile("bad.bin", "!!!not-base64!!!", 0);
        assertTrue(result.contains("invalid Base64"), result);
    }

    @Test
    void testAppendBinaryFile() throws Exception {
        Files.write(tempDir.resolve("app.bin"), "hello".getBytes());
        // "IHdvcmxk" is Base64 for " world"
        String result = tool.appendBinaryFile("app.bin", "IHdvcmxk");
        assertTrue(result.contains("Successfully appended"), result);
        byte[] content = Files.readAllBytes(tempDir.resolve("app.bin"));
        assertEquals("hello world", new String(content, java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void testAppendBinaryFileCreatesNewFile() {
        String result = tool.appendBinaryFile("new.bin", "AQID");
        assertTrue(result.contains("Successfully appended"), result);
        assertTrue(tool.pathExists("new.bin"));
    }

    @Test
    void testAppendBinaryFileInvalidBase64() {
        String result = tool.appendBinaryFile("bad2.bin", "%%%");
        assertTrue(result.contains("invalid Base64"), result);
    }

    @Test
    void testBinaryRoundTripWithNullBytes() throws Exception {
        // Binary data containing all byte values 0-255
        byte[] data = new byte[256];
        for (int i = 0; i < 256; i++) data[i] = (byte) i;
        String base64 = java.util.Base64.getEncoder().encodeToString(data);

        tool.writeBinaryFile("round.bin", base64, 0);
        String readResult = tool.readBinaryFile("round.bin", 0, 0);
        assertTrue(readResult.contains(base64), "Base64 round-trip should preserve all bytes");
    }
}
