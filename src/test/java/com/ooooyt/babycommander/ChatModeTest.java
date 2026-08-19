package com.ooooyt.babycommander;

import com.ooooyt.babycommander.ui.ChatEngine;
import com.ooooyt.babycommander.ui.GenerateTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class ChatModeTest {

    // ---- isGenerateRequest tests ----

    @Test
    void isGenerateRequestTrueForGenerateToken() {
        assertTrue(ChatEngine.isGenerateRequest("[GENERATE: Build a TODO app]"));
    }

    @Test
    void isGenerateRequestFalseForNormalText() {
        assertFalse(ChatEngine.isGenerateRequest("Java is a programming language"));
    }

    @Test
    void testIsGenerateRequestMidText() {
        assertTrue(ChatEngine.isGenerateRequest("Some thinking text\n[GENERATE: Build a TODO app]"));
    }

    // ---- extractGenerateTask tests ----

    @Test
    void extractGenerateTaskExtractsCorrectly() {
        assertEquals("Build a TODO app", ChatEngine.extractGenerateTask("[GENERATE: Build a TODO app]").task());
    }

    @Test
    void extractGenerateTaskReturnsNullForMalformed() {
        assertNull(ChatEngine.extractGenerateTask("No bracket here"));
    }

    @Test
    void extractGenerateTaskHandlesWhitespace() {
        assertEquals("Build a calculator", ChatEngine.extractGenerateTask("[GENERATE:  Build a calculator  ]").task());
    }

    @Test
    void testExtractGenerateTaskMidText() {
        assertEquals("Build a TODO app",
                ChatEngine.extractGenerateTask("Some thinking text\n[GENERATE: Build a TODO app]").task());
    }

    @Test
    @DisplayName("extractGenerateTask should detect new project flag")
    void testExtractGenerateTaskNewProjectFlag() {
        GenerateTask task = ChatEngine.extractGenerateTask("[GENERATE: Build a TODO app|new]");
        assertNotNull(task);
        assertTrue(task.isNewProject());
        assertEquals("Build a TODO app", task.task());
    }

    @Test
    @DisplayName("extractGenerateTask should detect existing project (no flag)")
    void testExtractGenerateTaskExistingProject() {
        GenerateTask task = ChatEngine.extractGenerateTask("[GENERATE: Add login feature]");
        assertNotNull(task);
        assertFalse(task.isNewProject());
        assertEquals("Add login feature", task.task());
    }

    @Test
    @DisplayName("extractGenerateTask should return null for null input")
    void testExtractGenerateTaskNullInput() {
        assertNull(ChatEngine.extractGenerateTask(null));
    }

    @Test
    @DisplayName("extractGenerateTask should handle GenerateTask record fields")
    void testGenerateTaskRecord() {
        GenerateTask task = new GenerateTask("test task", true, "my-project");
        assertEquals("test task", task.task());
        assertTrue(task.isNewProject());
        assertEquals("my-project", task.projectName());
    }

    @Test
    @DisplayName("GenerateTask with null projectName should be handled")
    void testGenerateTaskNullProjectName() {
        GenerateTask task = new GenerateTask("test task", false);
        assertEquals("test task", task.task());
        assertFalse(task.isNewProject());
        assertNull(task.projectName());
    }

    // ---- isExitCommand tests ----

    @Test
    void isExitCommandTrueForQuit() {
        assertTrue(ChatEngine.isExitCommand("quit"));
    }

    @Test
    void isExitCommandTrueForExit() {
        assertTrue(ChatEngine.isExitCommand("exit"));
    }

    @Test
    void isExitCommandCaseInsensitive() {
        assertTrue(ChatEngine.isExitCommand("QUIT"));
        assertTrue(ChatEngine.isExitCommand("Exit"));
        assertTrue(ChatEngine.isExitCommand("eXiT"));
    }

    @Test
    void isExitCommandFalseForNonExit() {
        assertFalse(ChatEngine.isExitCommand("continue"));
        assertFalse(ChatEngine.isExitCommand("help"));
    }

    // ---- isDocumentRequest tests ----

    @Test
    void testIsDocumentRequest() {
        assertTrue(ChatEngine.isDocumentRequest("[DOCUMENT: write a spec]"));
        assertTrue(ChatEngine.isDocumentRequest("Let me explore first.\n[DOCUMENT: write a spec]"));
        assertFalse(ChatEngine.isDocumentRequest("[GENERATE: code]"));
        assertFalse(ChatEngine.isDocumentRequest(null));
        assertFalse(ChatEngine.isDocumentRequest("not a document request"));
    }

    // ---- extractDocumentTask tests ----

    @Test
    void testExtractDocumentTask() {
        assertEquals("write a spec", ChatEngine.extractDocumentTask("[DOCUMENT: write a spec]"));
        assertEquals("write README at docs/readme.md",
                ChatEngine.extractDocumentTask("[DOCUMENT: write README at docs/readme.md]"));
        assertEquals("write a spec",
                ChatEngine.extractDocumentTask("Let me explore first.\n[DOCUMENT: write a spec]"));
        assertNull(ChatEngine.extractDocumentTask(null));
        assertNull(ChatEngine.extractDocumentTask("[DOCUMENT:")); // no closing bracket
    }

    @Test
    @DisplayName("extractDocumentTask should handle empty content")
    void testExtractDocumentTaskEmpty() {
        assertNotNull(ChatEngine.extractDocumentTask("[DOCUMENT:]"));
        assertEquals("", ChatEngine.extractDocumentTask("[DOCUMENT:]"));
    }

    // ---- GenerateTask record tests ----

    @Test
    @DisplayName("GenerateTask toString should work")
    void testGenerateTaskToString() {
        GenerateTask task = new GenerateTask("fix bug", false);
        String str = task.toString();
        assertTrue(str.contains("fix bug"));
        assertTrue(str.contains("false"));
    }

    @Test
    @DisplayName("GenerateTask equals and hashCode should work")
    void testGenerateTaskEquals() {
        GenerateTask t1 = new GenerateTask("task", true, "proj");
        GenerateTask t2 = new GenerateTask("task", true, "proj");
        assertEquals(t1, t2);
        assertEquals(t1.hashCode(), t2.hashCode());
    }

    @Test
    @DisplayName("shouldHandleGenerationRequest should only route new project tokens")
    void testShouldHandleGenerationRequestOnlyForNewProjects() {
        assertTrue(ChatEngine.shouldHandleGenerationRequest("[GENERATE: Build a TODO app|new]"));
        assertFalse(ChatEngine.shouldHandleGenerationRequest("[GENERATE: Add login feature]"));
        assertFalse(ChatEngine.shouldHandleGenerationRequest("Final report\n[GENERATE: Add login feature]"));
        assertFalse(ChatEngine.shouldHandleGenerationRequest("[GENERATE: |new]"));
        assertFalse(ChatEngine.shouldHandleGenerationRequest(null));
    }

    // ---- Cross-method interaction tests ----

    @Test
    @DisplayName("isGenerateRequest and isDocumentRequest should not overlap")
    void testNoOverlapBetweenGenerateAndDocument() {
        assertFalse(ChatEngine.isGenerateRequest("[DOCUMENT: write a spec]"));
        assertFalse(ChatEngine.isDocumentRequest("[GENERATE: Build a TODO app]"));
    }
}
