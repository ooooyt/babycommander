package com.ooooyt.babycommander.ui.engine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.ooooyt.babycommander.db.entity.ProjectEntity;
import com.ooooyt.babycommander.db.entity.TaskEntity;
import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;
import com.ooooyt.babycommander.ui.UiEvent;
import com.ooooyt.babycommander.ui.tui.SlashCommandRegistry;

/**
 * Handles slash commands entered by the user (workspace, lang, history, help).
 * <p>
 * Extracted from {@code ChatEngine}.
 */
public class CommandHandler {

    private final EngineContext ctx;
    private final SessionManager sessionManager;

    public CommandHandler(EngineContext ctx, SessionManager sessionManager) {
        this.ctx = ctx;
        this.sessionManager = sessionManager;
    }

    public void handleSlashCommand(String input) {
        String[] parts = input.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String task = parts.length > 1 ? parts[1].trim() : "";

        // Handle exit/quit commands
        if (command.equals("/exit") || command.equals("/quit")) {
            ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
                new UiEvent.MessageOutput(
                    I18n.tr(MessageKey.CHAT_GOODBYE), UiEvent.MessageType.PLAIN));
            stop();
            return;
        }

        if (command.equals("/help")) {
            printSlashHelp();
            return;
        }

        if (command.equals("/workspace")) {
            handleWorkspaceCommand(task);
            return;
        }

        if (command.equals("/lang")) {
            handleLangCommand(task);
            return;
        }

        if (command.equals("/history")) {
            handleHistoryCommand(task);
            return;
        }

        ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
            new UiEvent.MessageOutput(
                I18n.tr(MessageKey.CHAT_COMMAND_UNKNOWN, command), UiEvent.MessageType.PLAIN));
        printSlashHelp();
    }

    public void stop() {
        sessionManager.disposeChatSession();
        ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
            new UiEvent.SessionEvent(UiEvent.SessionState.STOPPED));
        ctx.llmExecutor().shutdown();
        if (ctx.uiAdapter() != null) {
            ctx.uiAdapter().stop();
        }
    }

    private void handleWorkspaceCommand(String pathArg) {
        if (pathArg.isEmpty()) {
            ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
                new UiEvent.MessageOutput(
                    I18n.tr(MessageKey.CHAT_WORKSPACE_CURRENT, ctx.workspace()), UiEvent.MessageType.PLAIN));
            ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
                new UiEvent.MessageOutput(
                    I18n.tr(MessageKey.CHAT_WORKSPACE_CURRENT_PROJECT, ctx.projectFolder()), UiEvent.MessageType.PLAIN));
            ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
                new UiEvent.MessageOutput(
                    I18n.tr(MessageKey.CHAT_WORKSPACE_USAGE), UiEvent.MessageType.PLAIN));
            return;
        }

        // Resolve tilde to user home directory
        String resolvedUserPath = pathArg.startsWith("~")
            ? pathArg.replaceFirst("^~", System.getProperty("user.home"))
            : pathArg;
        Path newPath = Path.of(resolvedUserPath).normalize();

        if (!Files.exists(newPath)) {
            ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
                new UiEvent.MessageOutput(
                    I18n.tr(MessageKey.CHAT_WORKSPACE_ERROR_NOT_EXIST, pathArg), UiEvent.MessageType.PLAIN));
            return;
        }
        if (!Files.isDirectory(newPath)) {
            ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
                new UiEvent.MessageOutput(
                    I18n.tr(MessageKey.CHAT_WORKSPACE_ERROR_NOT_DIR, pathArg), UiEvent.MessageType.PLAIN));
            return;
        }

        String resolvedPath = newPath.toAbsolutePath().toString();
        ctx.setWorkspace(resolvedPath);
        ctx.toolRegistry().updateToolPaths(ctx.paths());
        if (ctx.chatSession() != null) {
            ctx.agentFactory().disposeAgent(ctx.chatSession().sessionId());
            ctx.setChatSession(null);
        }
        ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
            new UiEvent.MessageOutput(
                I18n.tr(MessageKey.CHAT_WORKSPACE_CHANGED, ctx.workspace()), UiEvent.MessageType.PLAIN));
    }

    private void handleLangCommand(String localeArg) {
        if (localeArg.isEmpty()) {
            ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
                new UiEvent.MessageOutput(
                    I18n.tr(MessageKey.CHAT_LANG_CURRENT, I18n.getLocale().toLanguageTag()),
                    UiEvent.MessageType.PLAIN));
            ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
                new UiEvent.MessageOutput(
                    I18n.tr(MessageKey.CHAT_LANG_USAGE), UiEvent.MessageType.PLAIN));
            return;
        }

        Locale parsed = Locale.forLanguageTag(localeArg);
        if (parsed.getLanguage().isEmpty() && parsed.getCountry().isEmpty()) {
            ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
                new UiEvent.MessageOutput(
                    I18n.tr(MessageKey.CHAT_LANG_INVALID, localeArg), UiEvent.MessageType.PLAIN));
            I18n.setLocale(Locale.ENGLISH);
            return;
        }

        I18n.setLocale(parsed);
        ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
            new UiEvent.MessageOutput(
                I18n.tr(MessageKey.CHAT_LANG_CHANGED, I18n.getLocale().toLanguageTag()),
                UiEvent.MessageType.PLAIN));
    }

    private void handleHistoryCommand(String limitStr) {
        if (ctx.projectFolder() == null || ctx.projectFolder().isBlank()) {
            ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
                new UiEvent.MessageOutput(
                    I18n.tr(MessageKey.CHAT_HISTORY_NO_PROJECT_FOLDER), UiEvent.MessageType.PLAIN));
            ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
                new UiEvent.SessionEvent(UiEvent.SessionState.READY_FOR_INPUT));
            return;
        }

        // Find project by path
        List<ProjectEntity> projects = ctx.projectTaskService().getAllProjects();
        ProjectEntity matchedProject = null;
        for (ProjectEntity p : projects) {
            if (ctx.projectFolder().equals(p.path)) {
                matchedProject = p;
                break;
            }
        }

        if (matchedProject == null) {
            ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
                new UiEvent.MessageOutput(
                    I18n.tr(MessageKey.CHAT_HISTORY_NO_PROJECT, ctx.projectFolder()), UiEvent.MessageType.PLAIN));
            ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
                new UiEvent.SessionEvent(UiEvent.SessionState.READY_FOR_INPUT));
            return;
        }

        // Determine mode and fetch tasks accordingly
        List<TaskEntity> displayTasks;
        String headerProjectName = matchedProject.name;
        boolean isSearchMode = false;
        String searchKeyword = null;

        if (limitStr != null && !limitStr.isEmpty()) {
            try {
                int num = Integer.parseInt(limitStr);
                List<TaskEntity> allTasks = ctx.projectTaskService().getProjectTasks(matchedProject.id);
                if (allTasks.isEmpty()) {
                    ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
                        new UiEvent.MessageOutput(
                            I18n.tr(MessageKey.CHAT_HISTORY_NO_TASKS), UiEvent.MessageType.PLAIN));
                    ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
                        new UiEvent.SessionEvent(UiEvent.SessionState.READY_FOR_INPUT));
                    return;
                }
                if (num > 0) {
                    // Positive number: latest N tasks (most recent first)
                    if (num < allTasks.size()) {
                        displayTasks = allTasks.subList(0, num);
                    } else {
                        displayTasks = allTasks;
                    }
                } else if (num < 0) {
                    // Negative number: earliest |num| tasks (from the tail, reversed to show oldest first)
                    int absNum = Math.abs(num);
                    if (absNum < allTasks.size()) {
                        displayTasks = allTasks.subList(allTasks.size() - absNum, allTasks.size());
                    } else {
                        displayTasks = allTasks;
                    }
                } else {
                    // Zero is invalid
                    ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
                        new UiEvent.MessageOutput(
                            I18n.tr(MessageKey.CHAT_HISTORY_INVALID_PARAM, limitStr), UiEvent.MessageType.PLAIN));
                    ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
                        new UiEvent.SessionEvent(UiEvent.SessionState.READY_FOR_INPUT));
                    return;
                }
            } catch (NumberFormatException e) {
                // Non-numeric: use as search keyword for semantic search
                isSearchMode = true;
                searchKeyword = limitStr.trim();
                displayTasks = ctx.projectTaskService().searchProjectTasksSemantic(
                    matchedProject.id, searchKeyword, 10);
                if (displayTasks.isEmpty()) {
                    ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
                        new UiEvent.MessageOutput(
                            I18n.tr(MessageKey.CHAT_HISTORY_SEARCH_NO_RESULTS, searchKeyword), UiEvent.MessageType.PLAIN));
                    ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
                        new UiEvent.SessionEvent(UiEvent.SessionState.READY_FOR_INPUT));
                    return;
                }
            }
        } else {
            // No parameter: show all tasks
            displayTasks = ctx.projectTaskService().getProjectTasks(matchedProject.id);
            if (displayTasks.isEmpty()) {
                ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
                    new UiEvent.MessageOutput(
                        I18n.tr(MessageKey.CHAT_HISTORY_NO_TASKS), UiEvent.MessageType.PLAIN));
                ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
                    new UiEvent.SessionEvent(UiEvent.SessionState.READY_FOR_INPUT));
                return;
            }
        }

        // Publish header
        if (isSearchMode) {
            ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
                new UiEvent.MessageOutput(
                    I18n.tr(MessageKey.CHAT_HISTORY_SEARCH_HEADER, headerProjectName, searchKeyword), UiEvent.MessageType.PLAIN));
        } else {
            ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
                new UiEvent.MessageOutput(
                    I18n.tr(MessageKey.CHAT_HISTORY_HEADER, headerProjectName), UiEvent.MessageType.PLAIN));
        }

        for (int i = 0; i < displayTasks.size(); i++) {
            TaskEntity t = displayTasks.get(i);
            String line = String.format("  %d. %s [%s] (%s)",
                i + 1,
                t.name,
                t.status,
                t.getCreatedDatetimeInstant() != null ? t.getCreatedDatetimeInstant().toString() : "N/A");
            ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
                new UiEvent.MessageOutput(line, UiEvent.MessageType.PLAIN));
        }

        ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
            new UiEvent.MessageOutput(
                I18n.tr(MessageKey.CHAT_HISTORY_FOOTER, displayTasks.size()), UiEvent.MessageType.PLAIN));
        ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
            new UiEvent.SessionEvent(UiEvent.SessionState.READY_FOR_INPUT));
    }

    /**
     * Performs semantic search across task names using the local ObjectBox vector index.
     * <p>
     * This replaces the previous LLM-based approach. Tasks are pre-embedded at creation
     * time, and the query is embedded on-the-fly to perform a nearest-neighbor search
     * via the HNSW vector index.
     */
    private List<TaskEntity> semanticSearchTasks(List<TaskEntity> tasks, String query) {
        // This method is kept for backward compatibility but no longer called directly.
        // The caller now uses ProjectTaskService.searchProjectTasksSemantic() instead.
        // If called, it delegates to the service using the first task's project ID.
        if (tasks == null || tasks.isEmpty() || query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        String projectId = tasks.get(0).projectId;
        return ctx.projectTaskService().searchProjectTasksSemantic(projectId, query, 10);
    }

    private void printSlashHelp() {
        ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
            new UiEvent.MessageOutput("", UiEvent.MessageType.PLAIN));
        ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
            new UiEvent.MessageOutput(I18n.tr(MessageKey.CHAT_HELP_LINES), UiEvent.MessageType.PLAIN));
        for (SlashCommandRegistry.SlashCommand cmd : SlashCommandRegistry.commands()) {
            ctx.eventBus().publish(EngineContext.UI_EVENT_ADDRESS,
                new UiEvent.MessageOutput(cmd.description(), UiEvent.MessageType.PLAIN));
        }
    }
}
