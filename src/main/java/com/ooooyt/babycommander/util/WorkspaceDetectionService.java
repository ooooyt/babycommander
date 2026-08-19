package com.ooooyt.babycommander.util;

import lombok.Getter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
public class WorkspaceDetectionService {

    private static final Pattern ABSOLUTE_PATH_PATTERN = Pattern.compile("(/[a-zA-Z0-9._/-]+)+");
    private static final Pattern NATURAL_LANG_PATTERN = Pattern.compile(
        "(?:in|at|from|to)\\s+(?:the\\s+)?(/[a-zA-Z0-9._/-]+)", Pattern.CASE_INSENSITIVE);

    private final String defaultWorkspace;
    private String currentWorkspace;
    private boolean confirmed = false;

    public WorkspaceDetectionService(String defaultWorkspace) {
        this.defaultWorkspace = defaultWorkspace;
        this.currentWorkspace = defaultWorkspace;
    }

    public String detectPathFromMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }

        Matcher naturalMatcher = NATURAL_LANG_PATTERN.matcher(message);
        if (naturalMatcher.find()) {
            String path = naturalMatcher.group(1);
            if (Files.exists(Path.of(path))) {
                return path;
            }
        }

        Matcher absoluteMatcher = ABSOLUTE_PATH_PATTERN.matcher(message);
        while (absoluteMatcher.find()) {
            String path = absoluteMatcher.group();
            Path p = Path.of(path);
            if (Files.exists(p) && !path.equals("/tmp") && !path.equals("/home")) {
                return path;
            }
        }

        return null;
    }

    public String confirmWorkspace(String detectedPath, String userResponse) {
        if (userResponse == null || userResponse.trim().isEmpty()) {
            return currentWorkspace;
        }

        String trimmed = userResponse.trim().toLowerCase();

        if (trimmed.equals("yes") || trimmed.equals("y")) {
            currentWorkspace = detectedPath;
            confirmed = true;
            return currentWorkspace;
        }

        if (trimmed.equals("no") || trimmed.equals("n")) {
            currentWorkspace = defaultWorkspace;
            confirmed = true;
            return currentWorkspace;
        }

        if (trimmed.equals("a") || trimmed.equals("another")) {
            return currentWorkspace;
        }

        Path newPath = Path.of(userResponse.trim());
        if (Files.exists(newPath)) {
            currentWorkspace = newPath.toAbsolutePath().toString();
            confirmed = true;
            return currentWorkspace;
        }

        System.out.println(I18n.tr(MessageKey.WORKSPACE_INVALID_PATH, currentWorkspace));
        return currentWorkspace;
    }

}
