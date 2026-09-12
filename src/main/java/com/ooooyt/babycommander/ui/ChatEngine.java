package com.ooooyt.babycommander.ui;

import com.ooooyt.babycommander.agent.AgentContext;
import com.ooooyt.babycommander.agent.AgentFactory;
import com.ooooyt.babycommander.agent.memory.ToolCallAwareChatMemory;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.TimeoutException;
import com.ooooyt.babycommander.hook.HookManager;
import com.ooooyt.babycommander.hook.ToolDeniedException;
import com.ooooyt.babycommander.skill.SkillRegistry;
import com.ooooyt.babycommander.orchestrator.Orchestrator;
import com.ooooyt.babycommander.status.StatusEventContext;
import com.ooooyt.babycommander.tool.PlanTool;
import com.ooooyt.babycommander.tool.ToolRegistry;
import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;
import com.ooooyt.babycommander.util.WorkspaceDetectionService;
import com.ooooyt.babycommander.ui.UiEvent.MessageType;
import com.ooooyt.babycommander.ui.UiEvent.SessionState;
import com.ooooyt.babycommander.ui.engine.EngineContext;
import com.ooooyt.babycommander.ui.engine.GenerationRequestHandler;
import com.ooooyt.babycommander.ui.engine.CommandHandler;
import com.ooooyt.babycommander.ui.engine.RequestClassifier;
import com.ooooyt.babycommander.ui.engine.SessionManager;
import com.ooooyt.babycommander.tool.WorkspacePaths;
import com.ooooyt.babycommander.service.ProjectTaskService;
import io.quarkus.logging.Log;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ChatEngine {

    private static final String SYSTEM_PROMPT =
            """
                    You are a helpful coding assistant.
                    
                    ## MANDATORY: You MUST call `createPlan` tool FIRST
                    You have a tool called `createPlan`. You MUST call it before using any other tool.
                    - Call `createPlan(task, [ {title, description}, ... ])` as your very first action.
                    - The task describes the overall goal.
                    - Each phase is an object with a `title` (brief, at most 10 words) and a
                      `description` (at most 30 words).
                    - Titles must be unique across all phases.
                    - The phases are numbered steps you will follow.
                    - After creating the plan, work through each phase one at a time.
                    - After finishing each phase, call `completePhase(N)` where N is the phase number.
                    - If a phase cannot be completed, call `failPhase(N)`.
                    - After completing ALL phases, you MUST provide a brief summary describing
                      what was accomplished: list created/modified files and key changes made.
                    
                    ## TOKEN EFFICIENCY — Read these rules carefully
                    - PREFER searchPattern / search_files to find matches across the codebase before reading individual files.
                    - Use extractSkeleton / extract_skeleton for structural overview — NOT readFile for whole directories.
                    - Use shell (find, grep, rg) for filesystem navigation instead of recursive listDirectory.
                    - NEVER read the same file twice. If you already read it, reference from memory.
                    - Use readFileRange to read only relevant line ranges, not the entire file.
                    - Read files in batches — open 3-4 files at once rather than one-by-one.
                    - After finishing a phase, do NOT re-read files you already know about.
                    - When running build tool commands (mvn, npm, gradle, etc.), pipe the output through grep/findstr to extract only key information (errors, failures, results) instead of including the full output. This reduces unnecessary tokens in context/chat history.
                    
                    ## Task Classification
                    Also include the appropriate token below in your response for identification:
                    - New project from scratch: [GENERATE: brief task|new]
                    - Write a document: [DOCUMENT: brief description]
                    - Modify existing projects and all other tasks: no token needed""";

    public static boolean isGenerateRequest(String response) {
        return RequestClassifier.isGenerateRequest(response);
    }

    public static GenerateTask extractGenerateTask(String response) {
        return RequestClassifier.extractGenerateTask(response);
    }

    public static boolean shouldHandleGenerationRequest(String response) {
        return RequestClassifier.shouldHandleGenerationRequest(response);
    }

    public static boolean isDocumentRequest(String response) {
        return RequestClassifier.isDocumentRequest(response);
    }

    public static String extractDocumentTask(String response) {
        return RequestClassifier.extractDocumentTask(response);
    }

    public static final String UI_EVENT_ADDRESS = EngineContext.UI_EVENT_ADDRESS;
    public static final String UI_COMMAND_ADDRESS = EngineContext.UI_COMMAND_ADDRESS;

    private final EngineContext ctx;
    private final SessionManager sessionManager;
    private final CommandHandler commandHandler;
    private final GenerationRequestHandler generationRequestHandler;

    @Inject
    public ChatEngine(AgentFactory agentFactory, Orchestrator orchestrator, SkillRegistry skillRegistry,
                      HookManager hookManager, StatusEventContext statusContext,
                      ToolRegistry toolRegistry, EventBus eventBus, TaskPersistenceConsumer taskPersistenceConsumer,
                      ProjectTaskService projectTaskService) {
        this.ctx = new EngineContext(agentFactory, orchestrator, skillRegistry, hookManager, statusContext,
            toolRegistry, eventBus, taskPersistenceConsumer, projectTaskService);
        this.sessionManager = new SessionManager(ctx, SYSTEM_PROMPT);
        this.commandHandler = new CommandHandler(ctx, sessionManager);
        this.generationRequestHandler = new GenerationRequestHandler(ctx, sessionManager);
    }

    private void registerEventCodecs() {
        io.vertx.core.eventbus.EventBus delegate = ctx.eventBus().getDelegate();
        register(delegate, UiEvent.MessageOutput.class);
        register(delegate, UiEvent.StreamFragment.class);
        register(delegate, UiEvent.StatusMessage.class);
        register(delegate, UiEvent.ConfirmationRequest.class);
        register(delegate, UiEvent.ErrorEvent.class);
        register(delegate, UiEvent.SessionEvent.class);
        register(delegate, UiEvent.PlanUpdate.class);
        register(delegate, UiEvent.ClarificationRequest.class);
        register(delegate, CommandEvent.ClarificationResponse.class);
        register(delegate, CommandEvent.UserInput.class);
        register(delegate, CommandEvent.ConfirmationResponse.class);
        register(delegate, CommandEvent.Cancel.class);
        register(delegate, CommandEvent.SessionCommand.class);
    }

    private static <T> void register(io.vertx.core.eventbus.EventBus delegate, Class<T> type) {
        delegate.registerDefaultCodec(type, new LocalCodec<>(type));
    }

    public void start(UiAdapter adapter, String ws, String project) {
        ctx.setUiAdapter(adapter);
        String workspace = ws != null ? ws : System.getProperty("user.dir");
        ctx.setPaths(WorkspacePaths.of(workspace, project));

        registerEventCodecs();

        String defaultWs = ctx.workspace();
        WorkspaceDetectionService workspaceDetection = new WorkspaceDetectionService(defaultWs);
        workspaceDetection.confirmWorkspace(defaultWs, "yes");
        ctx.toolRegistry().updateToolPaths(ctx.paths());

        ctx.hookManager().setConfirmationHandler(adapter.getConfirmationHandler());

        ctx.taskPersistenceConsumer().start(ctx.projectFolder());

        // One-time migration: re-embed tasks whose stored embeddings are stale or
        // missing (e.g. after the HNSW 1536 -> 512 dimension change). Runs in a
        // background thread so startup is not blocked by the ONNX model load.
        Thread reembedThread = new Thread(() -> {
            try {
                ctx.projectTaskService().reembedStaleEmbeddings();
            } catch (Exception e) {
                Log.warnf("Task embedding migration failed: %s", e.getMessage());
            }
        }, "task-embedding-migration");
        reembedThread.setDaemon(true);
        reembedThread.start();

        ctx.eventBus().consumer(UI_COMMAND_ADDRESS, message -> {
            if (message.body() instanceof CommandEvent cmd) {
                handleCommand(cmd);
            }
        });

        ctx.eventBus().publish(UI_EVENT_ADDRESS, new UiEvent.SessionEvent(SessionState.STARTED));
        adapter.start();
        adapter.waitUntilStopped();
    }

    private void handleCommand(CommandEvent command) {
        try {
            switch (command) {
                case CommandEvent.UserInput input -> {
                    handleUserInput(input.text());
                }
                case CommandEvent.SessionCommand cmd -> handleSessionCommand(cmd.action());
                case CommandEvent.ClarificationResponse r -> {
                    AgentContext chatSession = ctx.chatSession();
                    if (chatSession != null && chatSession.chatMemory() instanceof ToolCallAwareChatMemory tcm) {
                        tcm.add(new UserMessage(r.question()));
                        tcm.add(new UserMessage(r.answer()));
                    }
                }
                default -> {}
            }
        } catch (Exception e) {
            Log.error("ChatEngine command handler failed", e);
            ctx.eventBus().publish(UI_EVENT_ADDRESS,
                new UiEvent.ErrorEvent(I18n.tr(MessageKey.CHAT_ERROR_INTERNAL), e.getMessage()));
            ctx.eventBus().publish(UI_EVENT_ADDRESS,
                new UiEvent.SessionEvent(SessionState.READY_FOR_INPUT));
        }
    }

    private void handleSessionCommand(CommandEvent.SessionAction action) {
        if (action == CommandEvent.SessionAction.STOP) {
            stop();
        }
    }

    public void stop() {
        commandHandler.stop();
    }

    private void handleUserInput(String input) {
        String trimmed = input.trim();

        if (trimmed.isEmpty()) {
            return;
        }

        if (isExitCommand(trimmed)) {
            ctx.eventBus().publish(UI_EVENT_ADDRESS,
                new UiEvent.MessageOutput(
                    I18n.tr(MessageKey.CHAT_GOODBYE), MessageType.PLAIN));
            stop();
            return;
        }

        if (trimmed.startsWith("/")) {
            commandHandler.handleSlashCommand(trimmed);
            return;
        }

        // The remaining inputs all engage the agent (LLM call and/or tool
        // execution), which can take a long time. Mark the session BUSY so
        // the UI disables its input box until the run finishes and
        // READY_FOR_INPUT is published from the completion paths below.
        ctx.eventBus().publish(UI_EVENT_ADDRESS, new UiEvent.SessionEvent(SessionState.BUSY));

        // Try to detect a new project creation request before engaging the agent
        GenerateTask preDetect = generationRequestHandler.detectNewProject(trimmed);
        if (preDetect != null) {
            generationRequestHandler.handleGenerationRequest(preDetect);
            // Dispose the old session (created during detection with the old projectFolder)
            // so getOrCreateSession below creates one with the updated projectFolder
            if (ctx.chatSession() != null) {
                ctx.agentFactory().disposeAgent(ctx.chatSession().sessionId());
                ctx.setChatSession(null);
            }
        }

        // Check if the input matches a skill with a workflow definition.
        // If so, route directly to Orchestrator for multi-agent workflow execution
        // with PlanTool phase progress displayed on the left panel.
        // Skill selection is now driven by the LLM in-context: it sees the skill
        // catalog in the system prompt and calls the `invoke_skill_workflow` tool
        // during the chat, so no deterministic pre-routing is needed here.

        AgentContext session = sessionManager.getOrCreateSession();
        if (session == null) {
            ctx.eventBus().publish(UI_EVENT_ADDRESS,
                new UiEvent.MessageOutput(
                    I18n.tr(MessageKey.CHAT_ERROR_GENERIC, "Failed to create chat session"),
                    MessageType.PLAIN));
            ctx.eventBus().publish(UI_EVENT_ADDRESS,
                new UiEvent.SessionEvent(SessionState.READY_FOR_INPUT));
            return;
        }

        long thinkStart = System.currentTimeMillis();
        try {
            ctx.hookManager().enterSession(session.sessionId());
            ctx.taskPersistenceConsumer().resetTaskId();
            sessionManager.prePopulatePlan(trimmed);

            if (session.chatMemory() instanceof ToolCallAwareChatMemory tcm) {
                Log.debugf("Chat memory before LLM call: %d messages, %d tool calls",
                    tcm.messages().size(), tcm.countToolCalls());
            }

            // Run the blocking LLM call on a dedicated worker thread so the Vert.x
            // event loop is free to process PlanUpdate events published by PlanTool
            // tool calls (completePhase, failPhase, createPlan) in real time.
            ctx.llmExecutor().submit(() -> {
                boolean completedSuccessfully = false;
                try {
                    ctx.statusContext().activate();
                    String response = session.agent().chat(trimmed);
                    long durationMs = System.currentTimeMillis() - thinkStart;

                    if (response == null || response.isBlank()) {
                        ctx.eventBus().publish(UI_EVENT_ADDRESS,
                            new UiEvent.MessageOutput(
                                I18n.tr(MessageKey.CHAT_ERROR_GENERIC, "Agent returned empty response"),
                                MessageType.PLAIN, durationMs));
                        return;
                    }

                    if (isGenerateRequest(response)) {
                        GenerateTask genTask = extractGenerateTask(response);
                        if (shouldHandleGenerationRequest(response)) {
                            generationRequestHandler.handleGenerationRequest(genTask);
                        } else {
                            ctx.eventBus().publish(UI_EVENT_ADDRESS,
                                new UiEvent.MessageOutput(response, MessageType.MARKDOWN, durationMs));
                        }
                        completedSuccessfully = true;
                    } else if (isDocumentRequest(response)) {
                        String task = extractDocumentTask(response);
                        if (task != null && !task.isBlank()) {
                            generationRequestHandler.handleDocumentRequest(task);
                        } else {
                            ctx.eventBus().publish(UI_EVENT_ADDRESS,
                                new UiEvent.MessageOutput(response, MessageType.MARKDOWN, durationMs));
                        }
                        completedSuccessfully = true;
                    } else {
                        ctx.eventBus().publish(UI_EVENT_ADDRESS,
                            new UiEvent.MessageOutput(response, MessageType.MARKDOWN, durationMs));
                        completedSuccessfully = true;
                    }
                } catch (dev.langchain4j.exception.TimeoutException e) {
                    long durationMs = System.currentTimeMillis() - thinkStart;
                    Log.errorf("Chat mode: LLM request timed out after %dms", durationMs);
                    ctx.eventBus().publish(UI_EVENT_ADDRESS,
                        new UiEvent.MessageOutput(
                            I18n.tr(MessageKey.CHAT_ERROR_TIMEOUT, durationMs / 1000),
                            MessageType.PLAIN, durationMs));
                } catch (ToolDeniedException e) {
                    long durationMs = System.currentTimeMillis() - thinkStart;
                    ctx.eventBus().publish(UI_EVENT_ADDRESS,
                        new UiEvent.MessageOutput(
                            I18n.tr(MessageKey.CHAT_ERROR_TOOL_DENIED, e.getMessage()),
                            MessageType.PLAIN, durationMs));
                } catch (Exception e) {
                    long durationMs = System.currentTimeMillis() - thinkStart;
                    Log.error("Chat mode: agent call failed", e);
                    ctx.eventBus().publish(UI_EVENT_ADDRESS,
                        new UiEvent.MessageOutput(
                            I18n.tr(MessageKey.CHAT_ERROR_GENERIC,
                                e.getMessage() != null ? e.getMessage() : I18n.tr(MessageKey.CHAT_ERROR_UNKNOWN)),
                            MessageType.PLAIN, durationMs));
                } finally {
                    // Safety net: if the LLM successfully completed but forgot to call
                    // completePhase for the final phase(s), mark all remaining in-progress
                    // phases as completed. If an exception occurred, fail the remaining phases
                    // so they honestly reflect the error rather than being incorrectly marked
                    // as completed.
                    try {
                        for (Object tool : ctx.toolRegistry().getAllTools()) {
                            if (tool instanceof PlanTool pt) {
                                if (completedSuccessfully) {
                                    pt.completeAllRemainingPhases();
                                } else {
                                    pt.failAllRemainingPhases();
                                }
                                break;
                            }
                        }
                    } finally {
                        ctx.hookManager().exitSession();
                        ctx.statusContext().deactivate();
                        ctx.eventBus().publish(UI_EVENT_ADDRESS,
                            new UiEvent.SessionEvent(SessionState.READY_FOR_INPUT));
                    }
                }
            });
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - thinkStart;
            Log.error("Chat mode: failed to submit LLM task", e);
            ctx.eventBus().publish(UI_EVENT_ADDRESS,
                new UiEvent.MessageOutput(
                    I18n.tr(MessageKey.CHAT_ERROR_GENERIC,
                        e.getMessage() != null ? e.getMessage() : I18n.tr(MessageKey.CHAT_ERROR_UNKNOWN)),
                    MessageType.PLAIN, durationMs));
            ctx.hookManager().exitSession();
            ctx.eventBus().publish(UI_EVENT_ADDRESS,
                new UiEvent.SessionEvent(SessionState.READY_FOR_INPUT));
        }
    }

    public static boolean isExitCommand(String input) {
        return RequestClassifier.isExitCommand(input);
    }
}
