package com.ooooyt.babycommander.hook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallInfoTest {

    @Test
    void testRecordConstruction() {
        ToolCallInfo info = new ToolCallInfo(
            "FileSystemTool", "writeFile",
            new Object[]{"test.txt", "content"},
            DangerLevel.DANGEROUS, "session1"
        );
        assertEquals("FileSystemTool", info.toolName());
        assertEquals("writeFile", info.methodName());
        assertArrayEquals(new Object[]{"test.txt", "content"}, info.args());
        assertEquals(DangerLevel.DANGEROUS, info.level());
        assertEquals("session1", info.sessionId());
    }

    @Test
    void testArgsPreviewWithArgs() {
        ToolCallInfo info = new ToolCallInfo(
            "FileSystemTool", "writeFile",
            new Object[]{"test.txt", "hello world"},
            DangerLevel.ASK_ONCE, "session1"
        );
        assertEquals("test.txt, hello world", info.argsPreview());
    }

    @Test
    void testArgsPreviewEmpty() {
        ToolCallInfo info = new ToolCallInfo(
            "FileSystemTool", "listFiles",
            new Object[]{},
            DangerLevel.SAFE, "session1"
        );
        assertEquals("", info.argsPreview());
    }

    @Test
    void testArgsPreviewNull() {
        ToolCallInfo info = new ToolCallInfo(
            "FileSystemTool", "listFiles",
            null,
            DangerLevel.SAFE, "session1"
        );
        assertEquals("", info.argsPreview());
    }

    @Test
    void testArgsPreviewWithNullElement() {
        ToolCallInfo info = new ToolCallInfo(
            "FileSystemTool", "writeFile",
            new Object[]{null, "content"},
            DangerLevel.SAFE, "session1"
        );
        assertEquals("null, content", info.argsPreview());
    }

    @Test
    void testFullArgsPreview() {
        ToolCallInfo info = new ToolCallInfo(
            "FileSystemTool", "writeFile",
            new Object[]{"path", "data"},
            DangerLevel.DANGEROUS, "session1"
        );
        assertEquals(info.argsPreview(), info.fullArgsPreview());
    }

    @Test
    void testArgsPreviewSanitizesNewlines() {
        ToolCallInfo info = new ToolCallInfo(
            "FileSystemTool", "writeFile",
            new Object[]{"test.txt", "package com.x;\n\nimport java.util.List;\n"},
            DangerLevel.ASK_ONCE, "session1"
        );
        assertEquals("test.txt, package com.x;  import java.util.List; ", info.argsPreview());
    }

    @Test
    void testArgsPreviewSanitizesTabsAndCarriageReturns() {
        ToolCallInfo info = new ToolCallInfo(
            "FileSystemTool", "writeFile",
            new Object[]{"test.txt", "line1\r\n\tline2"},
            DangerLevel.ASK_ONCE, "session1"
        );
        assertEquals("test.txt, line1   line2", info.argsPreview());
    }

    @Test
    void testArgsPreviewStaysOnOneLine() {
        ToolCallInfo info = new ToolCallInfo(
            "FileSystemTool", "writeFile",
            new Object[]{"test.txt", "\n\n\n"},
            DangerLevel.ASK_ONCE, "session1"
        );
        assertFalse(info.argsPreview().contains("\n"));
    }

    @Test
    void testEquality() {
        ToolCallInfo info1 = new ToolCallInfo("Tool", "method", new Object[]{"a"}, DangerLevel.SAFE, "s1");
        ToolCallInfo info2 = new ToolCallInfo("Tool", "method", new Object[]{"a"}, DangerLevel.SAFE, "s1");
        // records with array fields use reference equality for arrays
        assertNotEquals(info1, info2);
    }

    @Test
    void testDangerLevelValues() {
        assertEquals("DANGEROUS", DangerLevel.DANGEROUS.name());
        assertEquals("ASK_ONCE", DangerLevel.ASK_ONCE.name());
        assertEquals("SAFE", DangerLevel.SAFE.name());
    }

    @Test
    void testDangerLevelOrdinals() {
        assertEquals(0, DangerLevel.DANGEROUS.ordinal());
        assertEquals(1, DangerLevel.ASK_ONCE.ordinal());
        assertEquals(2, DangerLevel.SAFE.ordinal());
    }

    @Test
    void testConfirmationResultValues() {
        assertEquals(3, ConfirmationResult.values().length);
        assertEquals("ALLOW", ConfirmationResult.ALLOW.name());
        assertEquals("DENY", ConfirmationResult.DENY.name());
        assertEquals("ALLOW_ALWAYS", ConfirmationResult.ALLOW_ALWAYS.name());
    }
}
