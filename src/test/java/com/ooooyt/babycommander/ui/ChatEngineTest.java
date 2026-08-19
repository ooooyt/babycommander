package com.ooooyt.babycommander.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ChatEngine static helper methods.
 */
class ChatEngineTest {

    @Test
    @DisplayName("isExitCommand should return true for quit")
    void testIsExitCommandQuit() {
        assertTrue(ChatEngine.isExitCommand("quit"));
    }

    @Test
    @DisplayName("isExitCommand should return true for exit")
    void testIsExitCommandExit() {
        assertTrue(ChatEngine.isExitCommand("exit"));
    }

    @Test
    @DisplayName("isExitCommand should be case insensitive")
    void testIsExitCommandCaseInsensitive() {
        assertTrue(ChatEngine.isExitCommand("QUIT"));
        assertTrue(ChatEngine.isExitCommand("Exit"));
    }

    @Test
    @DisplayName("isExitCommand should return false for other commands")
    void testIsExitCommandFalse() {
        assertFalse(ChatEngine.isExitCommand("help"));
        assertFalse(ChatEngine.isExitCommand("run"));
    }

    @Test
    @DisplayName("isGenerateRequest should detect GENERATE prefix")
    void testIsGenerateRequest() {
        assertTrue(ChatEngine.isGenerateRequest("[GENERATE: build something]"));
        assertFalse(ChatEngine.isGenerateRequest("normal text"));
    }

    @Test
    @DisplayName("isDocumentRequest should detect DOCUMENT prefix")
    void testIsDocumentRequest() {
        assertTrue(ChatEngine.isDocumentRequest("[DOCUMENT: write docs]"));
        assertFalse(ChatEngine.isDocumentRequest("normal text"));
    }

    @Test
    @DisplayName("extractGenerateTask should parse task correctly")
    void testExtractGenerateTask() {
        GenerateTask task = ChatEngine.extractGenerateTask("[GENERATE: Build a calculator]");
        assertNotNull(task);
        assertEquals("Build a calculator", task.task());
        assertFalse(task.isNewProject());
    }

    @Test
    @DisplayName("extractGenerateTask should detect new project flag")
    void testExtractGenerateTaskNewProject() {
        GenerateTask task = ChatEngine.extractGenerateTask("[GENERATE: Build a calculator|new]");
        assertNotNull(task);
        assertEquals("Build a calculator", task.task());
        assertTrue(task.isNewProject());
    }

    @Test
    @DisplayName("extractDocumentTask should parse document task")
    void testExtractDocumentTask() {
        assertEquals("write README", ChatEngine.extractDocumentTask("[DOCUMENT: write README]"));
    }

    @Test
    @DisplayName("extractDocumentTask should return null for invalid input")
    void testExtractDocumentTaskInvalid() {
        assertNull(ChatEngine.extractDocumentTask(null));
        assertNull(ChatEngine.extractDocumentTask("no brackets"));
    }
}
