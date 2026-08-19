package com.ooooyt.babycommander.util;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class FilePatchUtilTest {

    private FilePatchUtil filePatchUtil;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void setupLocale() {
        I18n.setLocale(Locale.ENGLISH);
    }

    @BeforeEach
    void setUp() {
        filePatchUtil = new FilePatchUtil();
    }

    @Test
    void testApplyPatches_InsertBeforeLine() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "line1\nline2\nline3\n");

        String patch = "@@ -2 +2 I\ninserted1\ninserted2\n";
        String result = filePatchUtil.applyPatches(file.toString(), patch);

        assertNotNull(result);
        String content = Files.readString(file);
        assertTrue(content.contains("inserted1"));
        assertTrue(content.contains("inserted2"));
    }

    @Test
    void testApplyPatches_AppendAfterLine() throws IOException {
        Path file = tempDir.resolve("append.txt");
        Files.writeString(file, "a\nb\nc\n");

        String patch = "@@ -3 +3 A\nappended1\n";
        String result = filePatchUtil.applyPatches(file.toString(), patch);

        assertNotNull(result);
        String content = Files.readString(file);
        assertTrue(content.contains("appended1"));
    }

    @Test
    void testApplyPatches_ReplaceRange() throws IOException {
        Path file = tempDir.resolve("replace.txt");
        Files.writeString(file, "keep\nold1\nold2\nkeep\n");

        String patch = "@@ -2,2 +2,2 R\nnew1\nnew2\n";
        String result = filePatchUtil.applyPatches(file.toString(), patch);

        assertNotNull(result);
        String content = Files.readString(file);
        assertTrue(content.contains("new1"));
        assertTrue(content.contains("new2"));
        assertFalse(content.contains("old1"));
    }

    @Test
    void testApplyPatches_DeleteRange() throws IOException {
        Path file = tempDir.resolve("delete.txt");
        Files.writeString(file, "keep\ndelete1\ndelete2\nkeep\n");

        String patch = "@@ -2,2 +2,0 D\n";
        String result = filePatchUtil.applyPatches(file.toString(), patch);

        assertNotNull(result);
        String content = Files.readString(file);
        assertFalse(content.contains("delete1"));
        assertFalse(content.contains("delete2"));
    }

    @Test
    void testApplyPatches_FileNotFound() throws IOException {
        String result = filePatchUtil.applyPatches("/nonexistent/path.txt", "@@ -1 +1 I\ncontent\n");
        assertNotNull(result);
        // Should return an error message (locale-independent check)
        assertFalse(result.isEmpty());
    }

    @Test
    void testApplyPatches_InvalidHeader() throws IOException {
        Path file = tempDir.resolve("invalid.txt");
        Files.writeString(file, "line1\nline2\n");

        String result = filePatchUtil.applyPatches(file.toString(), "bad header");
        assertNotNull(result);
        // Should return an error message (locale-independent check)
        assertFalse(result.isEmpty());
    }

    @Test
    void testApplyPatches_EmptyPatch() throws IOException {
        Path file = tempDir.resolve("empty.txt");
        Files.writeString(file, "line1\n");

        String result = filePatchUtil.applyPatches(file.toString(), "");
        assertNotNull(result);
    }

    @Test
    void testApplyPatches_NullPatch() throws IOException {
        Path file = tempDir.resolve("nullpatch.txt");
        Files.writeString(file, "line1\n");

        String result = filePatchUtil.applyPatches(file.toString(), (String) null);
        assertNotNull(result);
    }

    @Test
    void testApplyPatches_MultiplePatches() throws IOException {
        Path file = tempDir.resolve("multi.txt");
        Files.writeString(file, "line1\nline2\nline3\n");

        String patch1 = "@@ -2 +2 I\ninserted\n";
        String patch2 = "@@ -4 +4 A\nappended\n";
        String result = filePatchUtil.applyPatches(file.toString(), patch1, patch2);

        assertNotNull(result);
        String content = Files.readString(file);
        assertTrue(content.contains("inserted"));
        assertTrue(content.contains("appended"));
    }

    @Test
    void testApplyUnifiedDiff_SimpleReplace() throws IOException {
        Path file = tempDir.resolve("unified.txt");
        Files.writeString(file, "aaa\nbbb\nccc\n");

        String diff = """
            --- a/unified.txt
            +++ b/unified.txt
            @@ -1,3 +1,3 @@
             aaa
            -bbb
            +xxx
             ccc
            """;

        String result = filePatchUtil.applyUnifiedDiff(file.toString(), diff);
        assertNotNull(result);
        String content = Files.readString(file);
        assertTrue(content.contains("xxx"));
    }

    @Test
    void testApplyUnifiedDiff_FileNotFound() throws IOException {
        String result = filePatchUtil.applyUnifiedDiff("/nonexistent/unified.txt", "@@ -1 +1 @@\n a\n");
        assertNotNull(result);
        // Should return an error message (locale-independent check)
        assertFalse(result.isEmpty());
    }

    @Test
    void testApplyUnifiedDiff_InvalidDiff() throws IOException {
        Path file = tempDir.resolve("invalid_diff.txt");
        Files.writeString(file, "line1\nline2\n");

        String result = filePatchUtil.applyUnifiedDiff(file.toString(), "invalid diff without hunks");
        assertNotNull(result);
    }

    @Test
    void testApplyUnifiedDiff_NoHunks() throws IOException {
        Path file = tempDir.resolve("nohunks.txt");
        Files.writeString(file, "line1\n");

        String result = filePatchUtil.applyUnifiedDiff(file.toString(), "--- a/nohunks.txt\n+++ b/nohunks.txt\n");
        assertNotNull(result);
    }

    @Test
    void testApplyPatches_StartOutOfRange() throws IOException {
        Path file = tempDir.resolve("outofrange.txt");
        Files.writeString(file, "only one line\n");

        String patch = "@@ -10 +10 I\ninserted\n";
        String result = filePatchUtil.applyPatches(file.toString(), patch);
        assertNotNull(result);
        // Should return an error message (locale-independent check)
        assertFalse(result.isEmpty());
    }

    @Test
    void testApplyPatches_DeleteOutOfRange() throws IOException {
        Path file = tempDir.resolve("delete_out.txt");
        Files.writeString(file, "line1\nline2\n");

        String patch = "@@ -1,10 +1,0 D\n";
        String result = filePatchUtil.applyPatches(file.toString(), patch);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }
}
