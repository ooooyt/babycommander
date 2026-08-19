package com.ooooyt.babycommander.ui.engine;

import com.ooooyt.babycommander.ui.GenerateTask;

/**
 * Pure static helpers for classifying user/agent responses into request types
 * (generate new project, write a document, exit). Extracted from
 * {@code ChatEngine}.
 */
public final class RequestClassifier {

    private static final String GENERATE_PREFIX = "[GENERATE:";
    private static final String DOCUMENT_PREFIX = "[DOCUMENT:";

    private RequestClassifier() {
    }

    public static boolean isGenerateRequest(String response) {
        return response != null && response.contains(GENERATE_PREFIX);
    }

    public static GenerateTask extractGenerateTask(String response) {
        if (response == null) return null;
        int start = response.indexOf(GENERATE_PREFIX);
        if (start < 0) return null;
        int end = response.indexOf(']', start + GENERATE_PREFIX.length());
        if (end < 0) return null;
        String content = response.substring(start + GENERATE_PREFIX.length(), end).trim();
        boolean isNewProject = content.contains("|new");
        String task = content.replace("|new", "").trim();
        return new GenerateTask(task, isNewProject);
    }

    public static boolean shouldHandleGenerationRequest(String response) {
        GenerateTask task = extractGenerateTask(response);
        return task != null && task.isNewProject() && !task.task().isBlank();
    }

    public static boolean isDocumentRequest(String response) {
        return response != null && response.contains(DOCUMENT_PREFIX);
    }

    public static String extractDocumentTask(String response) {
        if (response == null) return null;
        int start = response.indexOf(DOCUMENT_PREFIX);
        if (start < 0) return null;
        int end = response.indexOf(']', start + DOCUMENT_PREFIX.length());
        if (end < 0) return null;
        return response.substring(start + DOCUMENT_PREFIX.length(), end).trim();
    }

    public static boolean isExitCommand(String input) {
        return input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("exit");
    }
}
