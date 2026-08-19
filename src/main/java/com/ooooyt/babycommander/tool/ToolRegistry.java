package com.ooooyt.babycommander.tool;

import com.ooooyt.babycommander.hook.HookManager;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@ApplicationScoped
public class ToolRegistry {

    private final CopyOnWriteArrayList<Object> tools = new CopyOnWriteArrayList<>();

    /**
     * Optional reference to the hook manager, injected by CDI. May be
     * {@code null} when the registry is constructed directly (e.g. unit tests).
     * Used to propagate the current project folder root so that path-scoped
     * FileSystemTool trust can apply its in-scope gate.
     */
    @Inject
    HookManager hookManager;

    public void registerTool(Object tool) {
        tools.addIfAbsent(tool);
    }

    public void registerMcpTools(List<Object> mcpTools) {
        tools.addAll(mcpTools);
    }

    public List<Object> getAllTools() {
        return Collections.unmodifiableList(new ArrayList<>(tools));
    }

    /**
     * Fan out the active workspace/project-folder paths to every registered
     * tool that implements {@link WorkspaceAware}. This is the single sync
     * site that replaces the duplicated {@code instanceof} loops that
     * previously lived in both {@code ToolWorkspaceManager} and
     * {@code OrchestratorContext}.
     *
     * @param paths the new paths; a {@code null} reference is ignored, but
     *              individual path components may be {@code null}
     */
    public void updateToolPaths(WorkspacePaths paths) {
        if (paths == null) {
            return;
        }
        for (Object tool : tools) {
            if (tool instanceof WorkspaceAware wa) {
                wa.updatePaths(paths);
            }
        }
        // Propagate the current project folder root to the hook manager so the
        // path-scoped FileSystemTool trust can apply its in-scope gate.
        if (hookManager != null) {
            hookManager.setProjectRoot(paths.projectFolderString());
        }
    }

    public void clear() {
        tools.clear();
    }

    public int size() {
        return tools.size();
    }
}
