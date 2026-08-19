package com.ooooyt.babycommander.ui.engine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.ooooyt.babycommander.agent.AgentContext;
import com.ooooyt.babycommander.agent.AgentFactory;
import com.ooooyt.babycommander.hook.HookManager;
import com.ooooyt.babycommander.orchestrator.Orchestrator;
import com.ooooyt.babycommander.skill.SkillRegistry;
import com.ooooyt.babycommander.status.StatusEventContext;
import com.ooooyt.babycommander.tool.ToolRegistry;
import com.ooooyt.babycommander.tool.WorkspacePaths;
import com.ooooyt.babycommander.ui.TaskPersistenceConsumer;
import com.ooooyt.babycommander.ui.UiAdapter;
import com.ooooyt.babycommander.util.ProjectScanner;
import com.ooooyt.babycommander.service.ProjectTaskService;
import io.vertx.mutiny.core.eventbus.EventBus;
import lombok.Setter;

/**
 * Holds the shared mutable state and injected dependencies used across the
 * split {@code ChatEngine} components. Extracted from {@code ChatEngine}.
 */
public class EngineContext {

    public static final String UI_EVENT_ADDRESS = "ui.event";
    public static final String UI_COMMAND_ADDRESS = "ui.command";

    private final AgentFactory agentFactory;
    private final Orchestrator orchestrator;
    private final SkillRegistry skillRegistry;
    private final HookManager hookManager;
    private final StatusEventContext statusContext;
    private final ToolRegistry toolRegistry;
    private final ProjectScanner projectScanner;
    private final EventBus eventBus;
    private final ProjectTaskService projectTaskService;
    private final TaskPersistenceConsumer taskPersistenceConsumer;

    private final ExecutorService llmExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "llm-worker"));

    @Setter
    private volatile AgentContext chatSession;
    private volatile WorkspacePaths paths;
    @Setter
    private UiAdapter uiAdapter;

    public EngineContext(AgentFactory agentFactory, Orchestrator orchestrator, SkillRegistry skillRegistry,
                         HookManager hookManager, StatusEventContext statusContext,
                         ToolRegistry toolRegistry, EventBus eventBus, TaskPersistenceConsumer taskPersistenceConsumer,
                         ProjectTaskService projectTaskService) {
        this.agentFactory = agentFactory;
        this.orchestrator = orchestrator;
        this.skillRegistry = skillRegistry;
        this.hookManager = hookManager;
        this.statusContext = statusContext;
        this.toolRegistry = toolRegistry;
        this.taskPersistenceConsumer = taskPersistenceConsumer;
        this.projectTaskService = projectTaskService;
        this.projectScanner = new ProjectScanner();
        this.eventBus = eventBus;
    }

    public AgentFactory agentFactory() { return agentFactory; }
    public Orchestrator orchestrator() { return orchestrator; }
    public SkillRegistry skillRegistry() { return skillRegistry; }
    public HookManager hookManager() { return hookManager; }
    public StatusEventContext statusContext() { return statusContext; }
    public ToolRegistry toolRegistry() { return toolRegistry; }
    public ProjectScanner projectScanner() { return projectScanner; }
    public EventBus eventBus() { return eventBus; }
    public ProjectTaskService projectTaskService() { return projectTaskService; }
    public TaskPersistenceConsumer taskPersistenceConsumer() { return taskPersistenceConsumer; }
    public ExecutorService llmExecutor() { return llmExecutor; }

    public AgentContext chatSession() { return chatSession; }

    public WorkspacePaths paths() { return paths; }

    public void setPaths(WorkspacePaths paths) { this.paths = paths; }

    /** Merge a new workspace value into the current paths. */
    public void setWorkspace(String workspace) {
        this.paths = WorkspacePaths.of(workspace, projectFolder());
    }

    /** Merge a new project-folder value into the current paths. */
    public void setProjectFolder(String projectFolder) {
        this.paths = WorkspacePaths.of(workspace(), projectFolder);
    }

    public String workspace() {
        WorkspacePaths p = paths;
        return p != null ? p.workspaceString() : null;
    }

    public String projectFolder() {
        WorkspacePaths p = paths;
        return p != null ? p.projectFolderString() : null;
    }

    public UiAdapter uiAdapter() { return uiAdapter; }
}
