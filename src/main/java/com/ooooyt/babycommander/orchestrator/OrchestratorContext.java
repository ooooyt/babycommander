package com.ooooyt.babycommander.orchestrator;

import com.ooooyt.babycommander.config.YamlConfigLoader;
import com.ooooyt.babycommander.tool.ToolRegistry;
import com.ooooyt.babycommander.tool.WorkspacePaths;

import io.quarkus.logging.Log;
import lombok.Getter;

import java.util.UUID;

/**
 * Holds the orchestrator's mutable workspace/project/session state and resolves
 * default paths from config when the orchestrator has not been explicitly
 * initialized. Registered tools are kept aligned with the active paths through
 * the single {@link ToolRegistry#updateToolPaths(WorkspacePaths)} sync site.
 * <p>
 * The mutable state is {@code volatile} and {@code initialize} is synchronized
 * so that concurrent callers (e.g. the REST API) cannot observe a torn update
 * of the workspace/project pair.
 */
public class OrchestratorContext {

    private final YamlConfigLoader configLoader;
    private final ToolRegistry toolRegistry;

    private volatile WorkspacePaths paths;
    @Getter
    private volatile String currentSessionId;
    @Getter
    private volatile boolean initialized = false;

    public OrchestratorContext(YamlConfigLoader configLoader, ToolRegistry toolRegistry) {
        this.configLoader = configLoader;
        this.toolRegistry = toolRegistry;
    }

    public synchronized void initialize(WorkspacePaths paths) {
        this.paths = paths;
        this.currentSessionId = UUID.randomUUID().toString();
        this.initialized = true;
        toolRegistry.updateToolPaths(paths);
        Log.infof("Orchestrator initialized: workspace=%s, project=%s",
            paths != null ? paths.workspaceString() : null,
            paths != null ? paths.projectFolderString() : null);
    }

    public void initialize(String workspace, String projectFolder) {
        initialize(WorkspacePaths.of(workspace, projectFolder));
    }

    public String getWorkspace() {
        WorkspacePaths p = paths;
        return p != null ? p.workspaceString() : null;
    }

    public String getProjectFolder() {
        WorkspacePaths p = paths;
        return p != null ? p.projectFolderString() : null;
    }

    /**
     * Determine the default workspace when the orchestrator has not been
     * explicitly initialized. Reads the configured workspaceRoot from config
     * before falling back to user.dir.
     */
    public String determineDefaultWorkspace() {
        WorkspacePaths p = paths;
        if (p != null && p.workspace() != null) {
            return p.workspaceString();
        }
        String configuredWorkspace = configLoader.getConfig().workspaceRoot;
        if (configuredWorkspace != null && !configuredWorkspace.isBlank()) {
            return configuredWorkspace;
        }
        return System.getProperty("user.dir");
    }

    /**
     * Determine the default project folder when the orchestrator has not been
     * explicitly initialized. Falls back to the current workspace if set, then
     * to config, then to user.dir.
     */
    public String determineDefaultProject() {
        WorkspacePaths p = paths;
        if (p != null) {
            if (p.projectFolder() != null) {
                return p.projectFolderString();
            }
            if (p.workspace() != null) {
                return p.workspaceString();
            }
        }
        String configuredWorkspace = configLoader.getConfig().workspaceRoot;
        if (configuredWorkspace != null && !configuredWorkspace.isBlank()) {
            return configuredWorkspace;
        }
        return System.getProperty("user.dir");
    }
}
