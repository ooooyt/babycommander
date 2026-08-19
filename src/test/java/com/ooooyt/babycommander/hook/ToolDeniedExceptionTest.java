package com.ooooyt.babycommander.hook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolDeniedExceptionTest {

    @Test
    void testExceptionMessage() {
        ToolDeniedException ex = new ToolDeniedException("FileSystemTool");
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("FileSystemTool"));
    }

    @Test
    void testExceptionIsRuntimeException() {
        ToolDeniedException ex = new ToolDeniedException("ShellTool");
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void testExceptionWithDifferentTool() {
        ToolDeniedException ex = new ToolDeniedException("InternetTool");
        assertTrue(ex.getMessage().contains("InternetTool"));
    }
}
