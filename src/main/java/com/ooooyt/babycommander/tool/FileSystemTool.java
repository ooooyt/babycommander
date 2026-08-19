package com.ooooyt.babycommander.tool;

import com.ooooyt.babycommander.config.YamlConfigLoader;
import com.ooooyt.babycommander.hook.HookManager;
import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;
import com.ooooyt.babycommander.util.FilePatchUtil;
import com.ooooyt.babycommander.util.SkeletonExtractor;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;

public class FileSystemTool extends BaseTool implements WorkspaceAware {

    /** The project folder root - where all file operations and analysis happen. */
    private Path projectRoot;
    /** The workspace path - used for creating new projects or scanning existing ones. */
    private Path workspace;
    private final FilePatchUtil filePatchUtil;
    private final SkeletonExtractor skeletonExtractor;
    private int maxOutputLength = 20 * 1024;

    public void setMaxOutputLength(int bytes) {
        this.maxOutputLength = bytes;
    }

    @Inject
    YamlConfigLoader configLoader;

    public FileSystemTool(String projectFolder, HookManager hookManager) {
        super(hookManager);
        this.projectRoot = normalize(projectFolder);
        this.workspace = this.projectRoot;
        this.filePatchUtil = new FilePatchUtil();
        this.skeletonExtractor = new SkeletonExtractor();
    }

    @Override
    public void updatePaths(WorkspacePaths paths) {
        if (paths.projectFolder() != null) {
            this.projectRoot = paths.projectFolder();
        }
        if (paths.workspace() != null) {
            this.workspace = paths.workspace();
        }
    }

    /**
     * Set the project folder (where file operations take place).
     */
    public void setProjectFolder(String projectFolder) {
        this.projectRoot = normalize(projectFolder);
    }

    /**
     * Set the workspace path (for creating/scanning projects) separately from the project folder.
     */
    public void setWorkspace(String workspace) {
        this.workspace = normalize(workspace);
    }

    public Path getProjectRoot() {
        return projectRoot;
    }

    private static Path normalize(String path) {
        return (path == null || path.isBlank()) ? null : Paths.get(path).toAbsolutePath().normalize();
    }

    private Path resolvePath(String filePath) {
        Path path = Paths.get(filePath);
        if (path.isAbsolute()) {
            path = path.normalize();
        } else {
            path = projectRoot.resolve(path).normalize();
        }
        if (!path.startsWith(projectRoot)) {
            throw new SecurityException(I18n.tr(MessageKey.FS_PATH_ESCAPE, filePath));
        }
        return path;
    }

    // ========================================================================
    // @Tool methods — each delegates to executeWithStatus + a private "do*" method
    // ========================================================================

    @Tool("Write file contents, creating parent directories")
    public String writeFile(String filePath, String content) {
        return executeWithStatus("FileSystemTool", "write_file", filePath,
                new Object[]{filePath, content},
                () -> doWriteFile(filePath, content));
    }

    private String doWriteFile(String filePath, String content) {
        try {
            Path path = resolvePath(filePath);
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
            return I18n.tr(MessageKey.FS_WRITE_SUCCESS, path, content.length());
        } catch (SecurityException e) {
            return I18n.tr(MessageKey.FS_ERROR_GENERIC, e.getMessage());
        } catch (IOException e) {
            return I18n.tr(MessageKey.FS_WRITE_ERROR, filePath, e.getMessage());
        }
    }

    @Tool("Read full file contents")
    public String readFile(String filePath) {
        return executeWithStatus("FileSystemTool", "read_file", filePath,
                () -> doReadFile(filePath));
    }

    private String doReadFile(String filePath) {
        try {
            Path path = resolvePath(filePath);
            if (!Files.exists(path)) {
                return I18n.tr(MessageKey.FS_READ_ERROR, filePath, "File not found");
            }
            String content = Files.readString(path);
            if (content.length() > maxOutputLength) {
                content = truncateSafely(content, maxOutputLength)
                    + "\n... [truncated at " + (maxOutputLength / 1024) + "KB] ...\n";
            }
            return content;
        } catch (SecurityException e) {
            return I18n.tr(MessageKey.FS_ERROR_GENERIC, e.getMessage());
        } catch (IOException e) {
            return I18n.tr(MessageKey.FS_READ_ERROR, filePath, e.getMessage());
        }
    }

    @Tool("Read file contents by line range (1-indexed, inclusive)")
    public String readFileRange(String filePath, int startLine, int endLine) {
        return executeWithStatus("FileSystemTool", "read_file_range", filePath,
                new Object[]{filePath, startLine, endLine},
                () -> doReadFileRange(filePath, startLine, endLine));
    }

    private String doReadFileRange(String filePath, int startLine, int endLine) {
        try {
            Path path = resolvePath(filePath);
            if (!Files.exists(path)) {
                return I18n.tr(MessageKey.FS_READ_ERROR, filePath, "File not found");
            }
            List<String> lines = Files.readAllLines(path);
            if (startLine < 1 || startLine > lines.size()) {
                return I18n.tr(MessageKey.FS_READ_RANGE_EXCEEDS, startLine, lines.size());
            }
            if (endLine < startLine) {
                return I18n.tr(MessageKey.FS_READ_ERROR, filePath, "endLine must be >= startLine");
            }
            int toIndex = Math.min(endLine, lines.size());
            return String.join("\n", lines.subList(startLine - 1, toIndex));
        } catch (SecurityException e) {
            return I18n.tr(MessageKey.FS_ERROR_GENERIC, e.getMessage());
        } catch (IOException e) {
            return I18n.tr(MessageKey.FS_READ_RANGE_ERROR, filePath, e.getMessage());
        }
    }

    @Tool("List directory contents with type markers")
    public String listDirectory(String dirPath) {
        return executeWithStatus("FileSystemTool", "list_directory", dirPath,
                () -> doListDirectory(dirPath));
    }

    private String doListDirectory(String dirPath) {
        try {
            Path path = resolvePath(dirPath);
            if (!Files.exists(path)) {
                return I18n.tr(MessageKey.FS_PATH_NOT_FOUND, path);
            }
            if (!Files.isDirectory(path)) {
                return I18n.tr(MessageKey.FS_NOT_A_DIR, path);
            }
            try (Stream<Path> stream = Files.list(path)) {
                List<Path> entries = stream.sorted().toList();
                if (entries.isEmpty()) {
                    return I18n.tr(MessageKey.FS_EMPTY_DIR, path);
                }
                StringBuilder sb = new StringBuilder();
                for (Path entry : entries) {
                    String name = entry.getFileName().toString();
                    if (Files.isDirectory(entry)) {
                        sb.append("[dir]  ").append(name).append("/\n");
                    } else {
                        sb.append("[file] ").append(name).append("\n");
                    }
                }
                return sb.toString().trim();
            }
        } catch (SecurityException e) {
            return I18n.tr(MessageKey.FS_ERROR_GENERIC, e.getMessage());
        } catch (IOException e) {
            return I18n.tr(MessageKey.FS_LIST_DIR_ERROR, dirPath, e.getMessage());
        }
    }

    @Tool("Delete file or directory")
    public String deleteFile(String filePath) {
        return executeWithStatus("FileSystemTool", "delete_file", filePath,
                () -> doDeleteFile(filePath));
    }

    private String doDeleteFile(String filePath) {
        try {
            Path path = resolvePath(filePath);
            if (!Files.exists(path)) {
                return I18n.tr(MessageKey.FS_PATH_NOT_FOUND, path);
            }
            if (Files.isDirectory(path)) {
                try (Stream<Path> stream = Files.walk(path)) {
                    List<Path> failed = stream
                        .sorted((a, b) -> -a.compareTo(b))
                        .filter(p -> {
                            try { Files.delete(p); return false; }
                            catch (IOException e) { return true; }
                        })
                        .toList();
                    if (!failed.isEmpty()) {
                        return I18n.tr(MessageKey.FS_DELETE_PARTIAL, path + ". Failed to delete: " + failed);
                    }
                }
            } else {
                Files.delete(path);
            }
            return I18n.tr(MessageKey.FS_DELETE_SUCCESS, path);
        } catch (SecurityException e) {
            return I18n.tr(MessageKey.FS_ERROR_GENERIC, e.getMessage());
        } catch (IOException e) {
            return I18n.tr(MessageKey.FS_DELETE_ERROR, filePath, e.getMessage());
        }
    }

    @Tool("Check if file or directory exists")
    public boolean pathExists(String filePath) {
        // No status event for simple existence check
        beforeToolCall("FileSystemTool", "pathExists", new Object[]{filePath});
        try {
            Path path = resolvePath(filePath);
            return Files.exists(path);
        } catch (SecurityException e) {
            return false;
        }
    }

    @Tool("Check if path is a directory")
    public boolean isDirectory(String filePath) {
        // No status event for simple directory check
        beforeToolCall("FileSystemTool", "isDirectory", new Object[]{filePath});
        try {
            Path path = resolvePath(filePath);
            return Files.isDirectory(path);
        } catch (SecurityException e) {
            return false;
        }
    }

    @Tool("Search for text pattern across files, returns paths and line numbers")
    public String searchFiles(String searchPath, String pattern) {
        return executeWithStatus("FileSystemTool", "search_files", searchPath + " for \"" + pattern + "\"",
                new Object[]{searchPath, pattern},
                () -> doSearchFiles(searchPath, pattern));
    }

    private String doSearchFiles(String searchPath, String pattern) {
        try {
            Path root = resolvePath(searchPath);
            if (!Files.exists(root)) {
                return I18n.tr(MessageKey.FS_PATH_NOT_FOUND, root);
            }
            StringBuilder results = new StringBuilder();
            List<String> unreadable = new java.util.ArrayList<>();
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            List<String> lines = Files.readAllLines(path);
                            for (int i = 0; i < lines.size(); i++) {
                                if (lines.get(i).contains(pattern)) {
                                    results.append(path.getFileName())
                                        .append(":")
                                        .append(i + 1)
                                        .append("\n");
                                }
                            }
                        } catch (IOException e) {
                            unreadable.add(path.getFileName().toString());
                        }
                    });
            }
            if (results.isEmpty() && unreadable.isEmpty()) {
                return I18n.tr(MessageKey.FS_SEARCH_NO_MATCHES, pattern);
            }
            if (!unreadable.isEmpty()) {
                results.append("\n[Warning: could not read: ").append(String.join(", ", unreadable)).append("]");
            }
            return results.toString().trim();
        } catch (SecurityException e) {
            return I18n.tr(MessageKey.FS_ERROR_GENERIC, e.getMessage());
        } catch (IOException e) {
            return I18n.tr(MessageKey.FS_SEARCH_ERROR, searchPath, e.getMessage());
        }
    }

    @Tool("Get current project folder path")
    public String getProjectFolder() {
        // Simple getter — no status event needed
        return projectRoot != null ? projectRoot.toString() : null;
    }

    @Tool("Get workspace root path")
    public String getWorkspaceFolder() {
        // Simple getter — no status event needed
        return workspace != null ? workspace.toString() : null;
    }

    @Tool("Extract source file skeleton (declarations without bodies, any language)")
    public String extractSkeleton(String filePath) {
        return executeWithStatus("FileSystemTool", "extract_skeleton", filePath,
                () -> doExtractSkeleton(filePath));
    }

    private String doExtractSkeleton(String filePath) {
        try {
            Path path = resolvePath(filePath);
            if (!Files.exists(path)) {
                return I18n.tr(MessageKey.FS_READ_ERROR, filePath, "File not found");
            }
            SkeletonExtractor.SkeletonResult result = skeletonExtractor.extractSkeleton(path.toString());
            return result.getSkeleton();
        } catch (SecurityException e) {
            return I18n.tr(MessageKey.FS_ERROR_GENERIC, e.getMessage());
        } catch (Exception e) {
            return I18n.tr(MessageKey.FS_SKELETON_ERROR, filePath, e.getMessage());
        }
    }

    @Tool("Extract skeletons of all source files in a directory recursively")
    public String extractPackageSkeleton(String dirPath) {
        return executeWithStatus("FileSystemTool", "extract_package_skeleton", dirPath,
                () -> doExtractPackageSkeleton(dirPath));
    }

    private String doExtractPackageSkeleton(String dirPath) {
        try {
            Path root = resolvePath(dirPath);
            if (!Files.exists(root)) {
                return I18n.tr(MessageKey.FS_PATH_NOT_FOUND, root);
            }
            if (!Files.isDirectory(root)) {
                return I18n.tr(MessageKey.FS_NOT_A_DIR, root);
            }

            // Collect all source files recursively
            List<Path> sourceFiles;
            try (Stream<Path> stream = Files.walk(root)) {
                sourceFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> isSourceFile(p.toString()))
                    .sorted()
                    .toList();
            }

            if (sourceFiles.isEmpty()) {
                return I18n.tr(MessageKey.FS_SCAN_NO_SOURCES, root);
            }

            StringBuilder result = new StringBuilder();
            result.append(I18n.tr(MessageKey.FS_PACKAGE_LABEL, root)).append("\n");
            result.append(I18n.tr(MessageKey.FS_SOURCE_FILES_FOUND, sourceFiles.size())).append("\n");
            result.append("\n");

            // Build a directory tree first
            result.append(I18n.tr(MessageKey.FS_DIR_STRUCTURE)).append("\n");
            result.append(buildDirectoryTree(root, root));
            result.append("\n");

            // Extract skeleton for each source file
            int fileIndex = 0;
            int totalSkeletonChars = 0;
            for (Path file : sourceFiles) {
                fileIndex++;
                String relativePath = root.relativize(file).toString();
                String language = SkeletonExtractor.detectLanguage(file.toString());

                try {
                    SkeletonExtractor.SkeletonResult skelResult = skeletonExtractor.extractSkeleton(file.toString());
                    String skeleton = skelResult.getSkeleton();
                    String functionSummary = skelResult.getFunctionSummary();

                    result.append(I18n.tr(MessageKey.FS_FILE_HEADER, "File " + fileIndex + ": " + relativePath));
                    result.append(" (").append(language).append(")").append(" ---\n");
                    result.append(skeleton).append("\n");
                    if (!functionSummary.isEmpty()) {
                        result.append(functionSummary).append("\n");
                    }
                    result.append("\n");
                    totalSkeletonChars += skeleton.length();

                    if (totalSkeletonChars > maxOutputLength && fileIndex < sourceFiles.size()) {
                        result.append(I18n.tr(MessageKey.FS_MORE_FILES, sourceFiles.size() - fileIndex));
                        break;
                    }
                } catch (Exception e) {
                    result.append(I18n.tr(MessageKey.FS_FILE_HEADER, "File " + fileIndex + ": " + relativePath));
                    result.append(" (").append(language).append(")").append(" ---\n");
                    result.append(I18n.tr(MessageKey.FS_PACKAGE_SKELETON_ERROR, e.getMessage())).append("\n\n");
                }
            }

            return result.toString().trim();
        } catch (SecurityException e) {
            return I18n.tr(MessageKey.FS_ERROR_GENERIC, e.getMessage());
        } catch (Exception e) {
            return I18n.tr(MessageKey.FS_SCAN_ERROR, dirPath, e.getMessage());
        }
    }

    @Tool("Read full body of a function/method by name, any language")
    public String readFunction(String filePath, String functionName) {
        return executeWithStatus("FileSystemTool", "read_function", filePath,
                new Object[]{filePath, functionName},
                () -> doReadFunction(filePath, functionName));
    }

    private String doReadFunction(String filePath, String functionName) {
        try {
            Path path = resolvePath(filePath);
            if (!Files.exists(path)) {
                return I18n.tr(MessageKey.FS_READ_ERROR, filePath, "File not found");
            }
            return skeletonExtractor.readFunction(path.toString(), functionName);
        } catch (SecurityException e) {
            return I18n.tr(MessageKey.FS_ERROR_GENERIC, e.getMessage());
        } catch (Exception e) {
            return I18n.tr(MessageKey.FS_FUNCTION_READ_ERROR, filePath, e.getMessage());
        }
    }

    @Tool("Read full body of a function/method by name and param count")
    public String readFunctionByParamCount(String filePath, String functionName, int paramCount) {
        return executeWithStatus("FileSystemTool", "read_function_by_param_count", filePath,
                new Object[]{filePath, functionName, paramCount},
                () -> doReadFunctionByParamCount(filePath, functionName, paramCount));
    }

    private String doReadFunctionByParamCount(String filePath, String functionName, int paramCount) {
        try {
            Path path = resolvePath(filePath);
            if (!Files.exists(path)) {
                return I18n.tr(MessageKey.FS_READ_ERROR, filePath, "File not found");
            }
            return skeletonExtractor.readFunction(path.toString(), functionName, paramCount);
        } catch (SecurityException e) {
            return I18n.tr(MessageKey.FS_ERROR_GENERIC, e.getMessage());
        } catch (Exception e) {
            return I18n.tr(MessageKey.FS_FUNCTION_READ_ERROR, filePath, e.getMessage());
        }
    }

    @Tool("Apply line-based patch (A/I/R/D) to a file")
    public String applyFilePatch(String filePath, String[] patches) {
        return executeWithStatus("FileSystemTool", "apply_file_patch", filePath,
                new Object[]{filePath, patches},
                () -> doApplyFilePatch(filePath, patches));
    }

    private String doApplyFilePatch(String filePath, String[] patches) {
        try {
            Path path = resolvePath(filePath);
            return filePatchUtil.applyPatches(path.toString(), patches);
        } catch (SecurityException e) {
            return I18n.tr(MessageKey.FS_ERROR_GENERIC, e.getMessage());
        } catch (IOException e) {
            return I18n.tr(MessageKey.FS_PATCH_ERROR, filePath, e.getMessage());
        } catch (Exception e) {
            return I18n.tr(MessageKey.FS_PATCH_ERROR, filePath, e.getMessage());
        }
    }

    @Tool("Apply unified diff format patch to a file")
    public String applyUnifiedDiffPatch(String filePath, String unifiedDiff) {
        return executeWithStatus("FileSystemTool", "apply_unified_diff_patch", filePath,
                new Object[]{filePath, unifiedDiff},
                () -> doApplyUnifiedDiffPatch(filePath, unifiedDiff));
    }

    private String doApplyUnifiedDiffPatch(String filePath, String unifiedDiff) {
        try {
            Path path = resolvePath(filePath);
            return filePatchUtil.applyUnifiedDiff(path.toString(), unifiedDiff);
        } catch (SecurityException e) {
            return I18n.tr(MessageKey.FS_ERROR_GENERIC, e.getMessage());
        } catch (IOException e) {
            return I18n.tr(MessageKey.FS_DIFF_ERROR, filePath, e.getMessage());
        } catch (Exception e) {
            return I18n.tr(MessageKey.FS_DIFF_ERROR, filePath, e.getMessage());
        }
    }


    // ========================================================================
    // Binary file tools — read/write/append raw bytes via Base64 encoding
    // ========================================================================

    @Tool("Read binary file contents (returns Base64). Supports partial reading via offset and length. "
        + "offset is the 0-based byte position; length <= 0 reads to end of file.")
    public String readBinaryFile(String filePath, long offset, long length) {
        return executeWithStatus("FileSystemTool", "read_binary_file", filePath,
                new Object[]{filePath, offset, length},
                () -> doReadBinaryFile(filePath, offset, length));
    }

    private String doReadBinaryFile(String filePath, long offset, long length) {
        try {
            Path path = resolvePath(filePath);
            if (!Files.exists(path)) {
                return I18n.tr(MessageKey.FS_FILE_NOT_FOUND, filePath);
            }
            if (offset < 0) {
                return I18n.tr(MessageKey.FS_BINARY_INVALID_OFFSET, offset, filePath);
            }
            long fileSize = Files.size(path);
            if (offset >= fileSize) {
                return I18n.tr(MessageKey.FS_BINARY_READ_EMPTY, filePath, offset, fileSize);
            }
            long toRead = (length <= 0) ? (fileSize - offset) : Math.min(length, fileSize - offset);
            // Cap the number of bytes returned so tool output stays bounded
            long maxBytes = Math.max(1, (maxOutputLength * 3L) / 4L); // Base64 expands ~4/3
            boolean truncated = toRead > maxBytes;
            if (truncated) {
                toRead = maxBytes;
            }
            byte[] buffer = new byte[(int) toRead];
            int totalRead = 0;
            try (var in = Files.newInputStream(path)) {
                long skipped = in.skip(offset);
                if (skipped < offset) {
                    return I18n.tr(MessageKey.FS_READ_ERROR, filePath,
                            "Could not skip to offset " + offset);
                }
                while (totalRead < buffer.length) {
                    int n = in.read(buffer, totalRead, buffer.length - totalRead);
                    if (n < 0) break;
                    totalRead += n;
                }
            }
            if (totalRead == 0) {
                return I18n.tr(MessageKey.FS_BINARY_READ_EMPTY, filePath, offset, fileSize);
            }
            byte[] data = (totalRead == buffer.length) ? buffer : java.util.Arrays.copyOf(buffer, totalRead);
            String base64 = java.util.Base64.getEncoder().encodeToString(data);
            String result = I18n.tr(MessageKey.FS_BINARY_READ_SUCCESS, filePath, totalRead, offset, fileSize, base64);
            if (truncated) {
                result += "\n... [output truncated at " + (maxBytes / 1024) + "KB; use offset/length to read more] ...";
            }
            return result;
        } catch (SecurityException e) {
            return I18n.tr(MessageKey.FS_ERROR_GENERIC, e.getMessage());
        } catch (IOException e) {
            return I18n.tr(MessageKey.FS_READ_ERROR, filePath, e.getMessage());
        }
    }

    @Tool("Write binary data (Base64-encoded) to a file at a given byte offset, creating parent directories. "
        + "offset is the 0-based byte position; data beyond current file size zero-fills the gap.")
    public String writeBinaryFile(String filePath, String base64Data, long offset) {
        return executeWithStatus("FileSystemTool", "write_binary_file", filePath,
                new Object[]{filePath, base64Data, offset},
                () -> doWriteBinaryFile(filePath, base64Data, offset));
    }

    private String doWriteBinaryFile(String filePath, String base64Data, long offset) {
        try {
            Path path = resolvePath(filePath);
            if (offset < 0) {
                return I18n.tr(MessageKey.FS_BINARY_INVALID_OFFSET, offset, filePath);
            }
            byte[] data;
            try {
                data = java.util.Base64.getDecoder().decode(base64Data == null ? "" : base64Data.trim());
            } catch (IllegalArgumentException e) {
                return I18n.tr(MessageKey.FS_BINARY_INVALID_BASE64, filePath, e.getMessage());
            }
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw")) {
                raf.seek(offset);
                raf.write(data);
            }
            long newSize = Files.size(path);
            return I18n.tr(MessageKey.FS_BINARY_WRITE_SUCCESS, data.length, filePath, offset, newSize);
        } catch (SecurityException e) {
            return I18n.tr(MessageKey.FS_ERROR_GENERIC, e.getMessage());
        } catch (IOException e) {
            return I18n.tr(MessageKey.FS_BINARY_WRITE_ERROR, filePath, e.getMessage());
        }
    }

    @Tool("Append binary data (Base64-encoded) to the end of a file, creating it (and parent directories) if missing.")
    public String appendBinaryFile(String filePath, String base64Data) {
        return executeWithStatus("FileSystemTool", "append_binary_file", filePath,
                new Object[]{filePath, base64Data},
                () -> doAppendBinaryFile(filePath, base64Data));
    }

    private String doAppendBinaryFile(String filePath, String base64Data) {
        try {
            Path path = resolvePath(filePath);
            byte[] data;
            try {
                data = java.util.Base64.getDecoder().decode(base64Data == null ? "" : base64Data.trim());
            } catch (IllegalArgumentException e) {
                return I18n.tr(MessageKey.FS_BINARY_INVALID_BASE64, filePath, e.getMessage());
            }
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.write(path, data,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND);
            long newSize = Files.size(path);
            return I18n.tr(MessageKey.FS_BINARY_APPEND_SUCCESS, data.length, filePath, newSize);
        } catch (SecurityException e) {
            return I18n.tr(MessageKey.FS_ERROR_GENERIC, e.getMessage());
        } catch (IOException e) {
            return I18n.tr(MessageKey.FS_BINARY_APPEND_ERROR, filePath, e.getMessage());
        }
    }

    // ========================================================================
    // Helper methods
    // ========================================================================

    private boolean isSourceFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".java") || lower.endsWith(".kt") || lower.endsWith(".kts")
            || lower.endsWith(".py") || lower.endsWith(".js") || lower.endsWith(".ts")
            || lower.endsWith(".go") || lower.endsWith(".rs") || lower.endsWith(".rb")
            || lower.endsWith(".php") || lower.endsWith(".swift") || lower.endsWith(".c")
            || lower.endsWith(".cpp") || lower.endsWith(".h") || lower.endsWith(".hpp")
            || lower.endsWith(".cs") || lower.endsWith(".scala") || lower.endsWith(".clj")
            || lower.endsWith(".groovy") || lower.endsWith(".sql") || lower.endsWith(".xml")
            || lower.endsWith(".yaml") || lower.endsWith(".yml") || lower.endsWith(".json")
            || lower.endsWith(".properties") || lower.endsWith(".md") || lower.endsWith(".txt")
            || lower.endsWith(".sh") || lower.endsWith(".bat") || lower.endsWith(".ps1");
    }

    private String buildDirectoryTree(Path root, Path current) {
        StringBuilder sb = new StringBuilder();
        try (Stream<Path> entries = Files.list(current)) {
            List<Path> sorted = entries.sorted().toList();
            for (Path entry : sorted) {
                String indent = current.equals(root) ? "" : "  ";
                String prefix = current.equals(root) ? "" : "  ";
                if (Files.isDirectory(entry)) {
                    sb.append(indent).append("[dir]  ").append(entry.getFileName()).append("/\n");
                    sb.append(buildDirectoryTree(root, entry));
                } else {
                    sb.append(indent).append("[file] ").append(entry.getFileName()).append("\n");
                }
            }
        } catch (IOException ignored) {
        }
        return sb.toString();
    }
}
