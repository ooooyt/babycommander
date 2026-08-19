package com.ooooyt.babycommander.ui.engine;

import java.nio.file.Files;
import java.nio.file.Path;

import com.ooooyt.babycommander.agent.AgentContext;
import com.ooooyt.babycommander.agent.memory.ToolCallAwareChatMemory;
import com.ooooyt.babycommander.ui.GenerateTask;
import com.ooooyt.babycommander.ui.UiEvent;
import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;
import com.ooooyt.babycommander.util.ProjectScanner;
import io.quarkus.logging.Log;

/**
 * Handles generation requests (new project creation) and document requests,
 * including new-project detection. Extracted from {@code ChatEngine}.
 */
public class GenerationRequestHandler {

    private final EngineContext ctx;
    private final SessionManager sessionManager;

    public GenerationRequestHandler(EngineContext ctx, SessionManager sessionManager) {
        this.ctx = ctx;
        this.sessionManager = sessionManager;
    }

    public void handleGenerationRequest(GenerateTask genTask) {
        String task = genTask.task() != null ? genTask.task() : "";
        boolean isNewProject = genTask.isNewProject();
        String effectiveTask = task;

        if (isNewProject) {
            ProjectScanner.ProjectInfo info = ctx.projectScanner().scan(ctx.workspace());
            boolean hasExistingProject = info != null && info.language() != null && !info.language().equals("Unknown");

            if (!hasExistingProject) {
                String newProjectFolder = createNewProjectFolder(ctx.workspace(), genTask.projectName());
                ctx.setProjectFolder(newProjectFolder);
                ctx.orchestrator().initialize(ctx.workspace(), newProjectFolder);
                ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
                    new UiEvent.MessageOutput(
                        I18n.tr(MessageKey.CHAT_NEW_PROJECT), UiEvent.MessageType.PLAIN));
                ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
                    new UiEvent.MessageOutput(
                        I18n.tr(MessageKey.CHAT_NEW_PROJECT_CREATING, ctx.projectFolder()),
                        UiEvent.MessageType.PLAIN));
            } else {
                ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
                    new UiEvent.MessageOutput(
                        I18n.tr(MessageKey.CHAT_EXISTING_PROJECT), UiEvent.MessageType.PLAIN));
                ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
                    new UiEvent.MessageOutput(
                        I18n.tr(MessageKey.CHAT_EXISTING_PROJECT_FOUND, info.language(), info.buildTool()),
                        UiEvent.MessageType.PLAIN));
                ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
                    new UiEvent.MessageOutput(
                        I18n.tr(MessageKey.CHAT_EXISTING_PROJECT_LOCATION, ctx.workspace()),
                        UiEvent.MessageType.PLAIN));
                effectiveTask = task + " [PROJECT_PATH: " + ctx.workspace() + "]";
            }
        } else {
            effectiveTask = task + " [PROJECT_PATH: "
                + (ctx.projectFolder() != null ? ctx.projectFolder() : ctx.workspace()) + "]";
        }

        ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
            new UiEvent.MessageOutput(
                I18n.tr(MessageKey.CHAT_GENERATION_STARTED), UiEvent.MessageType.PLAIN));
        ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
            new UiEvent.MessageOutput(
                I18n.tr(MessageKey.CHAT_GENERATION_TASK, effectiveTask), UiEvent.MessageType.PLAIN));
        ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
            new UiEvent.MessageOutput(
                I18n.tr(MessageKey.CHAT_GENERATION_PROCEED), UiEvent.MessageType.PLAIN));
    }

    public void handleDocumentRequest(String task) {
        ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
            new UiEvent.MessageOutput(
                I18n.tr(MessageKey.CHAT_DOCUMENT_STARTED), UiEvent.MessageType.PLAIN));
        ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
            new UiEvent.MessageOutput(
                I18n.tr(MessageKey.CHAT_DOCUMENT_TASK, task), UiEvent.MessageType.PLAIN));
        ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
            new UiEvent.MessageOutput(
                I18n.tr(MessageKey.CHAT_DOCUMENT_PROCEED), UiEvent.MessageType.PLAIN));
    }

    private String createNewProjectFolder(String defaultWorkspace, String name) {
        String folderName = (name != null && !name.isBlank()) ? name : "project";
        Path newProjectPath = Path.of(defaultWorkspace, folderName);
        try {
            Files.createDirectories(newProjectPath);
            return newProjectPath.toAbsolutePath().toString();
        } catch (Exception e) {
            Log.warnf("Failed to create project folder: %s", e.getMessage());
            return defaultWorkspace;
        }
    }

    /**
     * Detects whether the given input looks like a request to create a new
     * project from scratch. Returns the extracted {@link GenerateTask} when
     * detected, otherwise {@code null}.
     */
    public GenerateTask detectNewProject(String input) {
        String lower = input.toLowerCase();
        // Quick keyword check to avoid unnecessary LLM calls
        if (!lower.contains("create") && !lower.contains("build")
            && !lower.contains(" new ") && !lower.startsWith("new ")
            && !lower.contains("start") && !lower.contains("generate")
            && !lower.contains("scaffold")) {
            return null;
        }
        try {
            AgentContext session = sessionManager.getOrCreateSession();
            if (session == null) return null;
            String reply = session.agent().chat(
                I18n.tr(MessageKey.CHAT_DETECT_NEW_PROJECT) + "\n\nRequest: " + input
            );
            // Clean tool-call pairs from detection call so they don't accumulate
            if (session.chatMemory() instanceof ToolCallAwareChatMemory tcm) {
                tcm.compact();
            }
            if (reply != null && reply.trim().toUpperCase().startsWith("YES")) {
                int pipe = reply.indexOf('|');
                if (pipe > 0 && pipe + 1 < reply.length()) {
                    String name = reply.substring(pipe + 1).trim().toLowerCase()
                        .replaceAll("[^a-z0-9-]", "").replaceAll("-+", "-").replaceAll("^-|-$", "");
                    if (!name.isEmpty() && name.length() < 40) {
                        return new GenerateTask(input.trim(), true, name);
                    }
                }
            }
        } catch (Exception e) {
            // LLM call failed, fall through to null
        }
        return null;
    }
}
