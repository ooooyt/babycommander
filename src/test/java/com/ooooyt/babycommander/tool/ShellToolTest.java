package com.ooooyt.babycommander.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ShellToolTest {

    @TempDir
    Path tempDir;

    @Test
    void testExecuteEchoCommand() {
        ShellTool tool = new ShellTool(tempDir.toString(), null);
        String result = tool.execute("echo hello");
        assertTrue(result.contains("hello"));
        assertTrue(result.contains("Exit Code: 0"));
    }

    @Test
    void testExecuteInDir() throws Exception {
        ShellTool tool = new ShellTool(tempDir.toString(), null);
        Path sub = tempDir.resolve("subdir");
        Files.createDirectory(sub);
        Files.writeString(sub.resolve("test.txt"), "content");

        String result = tool.executeInDir("cat test.txt", sub.toString());
        assertTrue(result.contains("content"));
    }

    @Test
    void testExecuteWithTimeout() {
        ShellTool tool = new ShellTool(tempDir.toString(), null);
        String result = tool.executeWithTimeout("echo fast", 5);
        assertTrue(result.contains("Exit Code: 0"));
        assertTrue(result.contains("Completed: true"));
    }

    @Test
    void testExecuteFailingCommand() {
        ShellTool tool = new ShellTool(tempDir.toString(), null);
        String result = tool.execute("exit 1");
        assertTrue(result.contains("Exit Code: 1"));
    }

    @Test
    void testLongRunningCommandTimeouts() {
        ShellTool tool = new ShellTool(tempDir.toString(), null);
        String result = tool.executeWithTimeout("sleep 10", 1);
        assertFalse(result.contains("Completed: true"));
        assertTrue(result.contains("Completed: false"));
    }

    @Test
    void testMultipleOutputLines() {
        ShellTool tool = new ShellTool(tempDir.toString(), null);
        String result = tool.execute("printf 'one\ntwo\nthree'");
        assertTrue(result.contains("one"));
        assertTrue(result.contains("two"));
        assertTrue(result.contains("three"));
    }

    @Test
    void testWorkingDirectorySet() {
        ShellTool tool = new ShellTool(tempDir.toString(), null);
        String result = tool.executeInDir("pwd", tempDir.toString());
        assertTrue(result.contains(tempDir.toString()));
    }
}
