package com.ooooyt.babycommander.tool;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Immutable, normalized value object holding the two path roots used across
 * the system:
 * <ul>
 *   <li>{@code workspace} &mdash; the container directory for creating/scanning
 *       projects</li>
 *   <li>{@code projectFolder} &mdash; the specific project directory where file
 *       operations, builds and tests run</li>
 * </ul>
 * <p>
 * Both paths are normalized to absolute form at construction time so that
 * equality checks and path comparisons are consistent across components. This
 * is the single source of truth for workspace/project state, replacing the
 * scattered {@code String} fields that previously had to be kept in sync by
 * hand.
 */
public record WorkspacePaths(Path workspace, Path projectFolder) {

    public WorkspacePaths {
        workspace = normalize(workspace);
        projectFolder = normalize(projectFolder);
    }

    /**
     * Build a {@link WorkspacePaths} from raw path strings. {@code null}/blank
     * values are preserved as {@code null} paths (the caller is expected to
     * resolve a default before use).
     */
    public static WorkspacePaths of(String workspace, String projectFolder) {
        return new WorkspacePaths(toPath(workspace), toPath(projectFolder));
    }

    /**
     * Build a {@link WorkspacePaths} where the workspace and project folder are
     * the same path. Used when no separate project folder has been determined.
     */
    public static WorkspacePaths unified(String path) {
        Path p = toPath(path);
        return new WorkspacePaths(p, p);
    }

    /** Accessor returning the workspace path as a string (may be {@code null}). */
    public String workspaceString() {
        return workspace != null ? workspace.toString() : null;
    }

    /** Accessor returning the project-folder path as a string (may be {@code null}). */
    public String projectFolderString() {
        return projectFolder != null ? projectFolder.toString() : null;
    }

    private static Path toPath(String s) {
        return (s == null || s.isBlank()) ? null : Paths.get(s);
    }

    private static Path normalize(Path p) {
        return (p == null) ? null : p.toAbsolutePath().normalize();
    }
}
