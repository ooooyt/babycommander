package com.ooooyt.babycommander.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceDetectionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void testDetectPath_fromAbsolutePath() throws Exception {
        Path testPath = tempDir.resolve("myproject");
        Files.createDirectory(testPath);
        String workspace = tempDir.resolve("default").toString();
        WorkspaceDetectionService service = new WorkspaceDetectionService(workspace);

        String detected = service.detectPathFromMessage("Build a new project in " + testPath);
        assertEquals(testPath.toString(), detected);
    }

    @Test
    void testDetectPath_fromNaturalLanguage() throws Exception {
        Path testPath = tempDir.resolve("projects");
        Files.createDirectory(testPath);
        String workspace = tempDir.resolve("default").toString();
        WorkspaceDetectionService service = new WorkspaceDetectionService(workspace);

        String detected = service.detectPathFromMessage("Create a new app in the " + testPath + " directory");
        assertEquals(testPath.toString(), detected);
    }

    @Test
    void testNoPathInMessage_returnsNull() {
        String workspace = tempDir.resolve("default").toString();
        WorkspaceDetectionService service = new WorkspaceDetectionService(workspace);

        String detected = service.detectPathFromMessage("What is the current time?");
        assertNull(detected);
    }

    @Test
    void testConfirmWorkspace_acceptsPath() {
        String workspace = tempDir.resolve("default").toString();
        String detectedPath = tempDir.resolve("myproject").toString();
        WorkspaceDetectionService service = new WorkspaceDetectionService(workspace);

        String result = service.confirmWorkspace(detectedPath, "yes");
        assertEquals(detectedPath, result);
        assertTrue(service.isConfirmed());
    }

    @Test
    void testConfirmWorkspace_rejectsAndUsesFallback() {
        String workspace = tempDir.resolve("default").toString();
        String detectedPath = tempDir.resolve("myproject").toString();
        WorkspaceDetectionService service = new WorkspaceDetectionService(workspace);

        String result = service.confirmWorkspace(detectedPath, "no");
        assertEquals(workspace, result);
        assertTrue(service.isConfirmed());
    }

    @Test
    void testConfirmWorkspace_providesNewPath() throws Exception {
        String workspace = tempDir.resolve("default").toString();
        String detectedPath = tempDir.resolve("myproject").toString();
        Path newPath = tempDir.resolve("newproject");
        Files.createDirectory(newPath);
        WorkspaceDetectionService service = new WorkspaceDetectionService(workspace);

        String result = service.confirmWorkspace(detectedPath, newPath.toString());
        assertEquals(newPath.toString(), result);
        assertTrue(service.isConfirmed());
    }

    @Test
    void testGetCurrentWorkspace_initiallyReturnsDefault() {
        String workspace = tempDir.resolve("default").toString();
        WorkspaceDetectionService service = new WorkspaceDetectionService(workspace);

        assertEquals(workspace, service.getCurrentWorkspace());
        assertFalse(service.isConfirmed());
    }
}