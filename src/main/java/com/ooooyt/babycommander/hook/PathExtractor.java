package com.ooooyt.babycommander.hook;

import java.util.ArrayList;
import java.util.List;

public class PathExtractor {

    static List<String> extract(String toolName, Object[] args, String workspaceRoot) {
        if (args == null || args.length == 0) return List.of();
        if (toolName.equals("ShellTool")) {
            return extractFromShellCommand(args, workspaceRoot);
        }
        if (toolName.equals("FileSystemTool")) {
            return extractFromFileSystemArgs(args, workspaceRoot);
        }
        return List.of();
    }

    static List<String> extractFromShellCommand(Object[] args, String workspaceRoot) {
        if (args.length == 0 || !(args[0] instanceof String command)) return List.of();
        List<String> paths = new ArrayList<>();
        for (String token : tokenize(command)) {
            if (looksLikePath(token)) {
                String normalized = normalize(token, workspaceRoot);
                if (normalized != null) paths.add(normalized);
            }
        }
        return paths;
    }

    static List<String> extractFromFileSystemArgs(Object[] args, String workspaceRoot) {
        List<String> paths = new ArrayList<>();
        for (Object arg : args) {
            if (arg instanceof String s && looksLikePath(s)) {
                String normalized = normalize(s, workspaceRoot);
                if (normalized != null) paths.add(normalized);
            }
        }
        return paths;
    }

    /**
     * Returns the normalized first argument for FileSystemTool, else {@code null}.
     * Only {@code args[0]} is considered — for FileSystemTool it is always the
     * file path. Any non-blank string is accepted (no path-like gate), because
     * bare filenames such as {@code file.txt} are valid project-relative paths.
     * <p>
     * Relative paths are resolved against the current project root, mirroring
     * {@code FileSystemTool.resolvePath()}, so that {@code src/Foo.java} and
     * {@code <project>/src/Foo.java} produce the same key.
     *
     * @param toolName      the tool class name (must be "FileSystemTool")
     * @param args          the tool call arguments
     * @param workspaceRoot the configured workspace root (for normalization)
     * @param projectRoot   the current project folder root (for relative resolution)
     * @return the normalized first-argument path, or {@code null} if not applicable
     */
    static String firstPath(String toolName, Object[] args,
                            String workspaceRoot, String projectRoot) {
        if (!"FileSystemTool".equals(toolName) || args == null || args.length == 0) return null;
        if (!(args[0] instanceof String s) || s.isBlank()) return null;
        String resolved = s;
        if (projectRoot != null && !s.startsWith("~/")
                && !java.nio.file.Path.of(s).isAbsolute()) {
            resolved = java.nio.file.Path.of(projectRoot).resolve(s).toString();
        }
        return normalize(resolved, workspaceRoot);
    }

    static List<String> tokenize(String command) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                    if (!current.isEmpty()) { tokens.add(current.toString()); current.setLength(0); }
                } else {
                    current.append(c);
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (Character.isWhitespace(c)) {
                if (!current.isEmpty()) { tokens.add(current.toString()); current.setLength(0); }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) tokens.add(current.toString());
        return tokens;
    }

    static boolean looksLikePath(String s) {
        if (s == null || s.isBlank()) return false;
        if (s.startsWith("-")) return false;
        return s.startsWith("/") || s.startsWith("./") || s.startsWith("../") || s.startsWith("~/") || s.contains("/");
    }

    /**
     * Convert a normalized path to its parent directory.
     * Returns null for paths with no parent (e.g., single files at root).
     * Returns the path unchanged if it already looks like a directory.
     */
    static String toParentDirectory(String normalizedPath) {
        if (normalizedPath == null || normalizedPath.isBlank()) return null;
        boolean hadTrailingSlash = normalizedPath.endsWith("/");
        String stripped = hadTrailingSlash ? normalizedPath.substring(0, normalizedPath.length() - 1) : normalizedPath;
        int lastSlash = stripped.lastIndexOf('/');
        String lastPart = lastSlash < 0 ? stripped : stripped.substring(lastSlash + 1);
        if (hadTrailingSlash || !lastPart.contains(".")) {
            if (stripped.isEmpty()) return null;
            return stripped;
        }
        if (lastSlash < 0) return null;
        return stripped.substring(0, lastSlash);
    }

    static String normalize(String raw, String workspaceRoot) {
        String resolved = raw;
        if (resolved.startsWith("~/")) {
            resolved = System.getProperty("user.home") + "/" + resolved.substring(2);
        }
        if (resolved.startsWith("/")) {
            java.nio.file.Path p = java.nio.file.Path.of(resolved).normalize();
            resolved = p.toString();
            if (workspaceRoot != null && !workspaceRoot.isBlank()) {
                String ws = java.nio.file.Path.of(workspaceRoot).normalize().toString();
                if (resolved.equals(ws)) {
                    return "";
                }
                if (!ws.endsWith("/")) ws += "/";
                if (resolved.startsWith(ws)) {
                    return resolved.substring(ws.length());
                }
            }
            return resolved;
        }
        java.nio.file.Path p = java.nio.file.Path.of(resolved).normalize();
        return p.toString();
    }

    static boolean isDescendantOrSelf(String path, String prefix) {
        String p = path.replace('\\', '/');
        String pre = prefix.replace('\\', '/');
        if (p.equals(pre)) return true;
        if (!pre.endsWith("/")) pre += "/";
        return p.startsWith(pre);
    }
}
