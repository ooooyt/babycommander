package com.ooooyt.babycommander.tool;

/**
 * Interface implemented by tools that need to track the active
 * workspace/project-folder paths (e.g. {@link FileSystemTool},
 * {@link ShellTool}).
 * <p>
 * Centralizing path updates behind this interface removes the duplicated
 * {@code instanceof} sync loops that previously lived in both
 * {@code ToolWorkspaceManager} and {@code OrchestratorContext}. A single
 * call to {@code ToolRegistry.updateToolPaths(WorkspacePaths)} now fans the
 * update out to every interested tool.
 */
public interface WorkspaceAware {

    /**
     * Update this tool's workspace and project-folder paths.
     *
     * @param paths the new paths (never {@code null}; individual path
     *              components may be {@code null} when not yet resolved)
     */
    void updatePaths(WorkspacePaths paths);
}
