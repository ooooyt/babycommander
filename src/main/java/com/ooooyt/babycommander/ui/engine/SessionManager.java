package com.ooooyt.babycommander.ui.engine;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import com.ooooyt.babycommander.agent.AgentContext;
import com.ooooyt.babycommander.agent.memory.ToolCallAwareChatMemory;
import com.ooooyt.babycommander.tool.PlanTool;
import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;
import com.ooooyt.babycommander.util.ProjectScanner;
import io.quarkus.logging.Log;

/**
 * Manages the lifecycle of the chat {@link AgentContext} session: creation,
 * disposal, system-prompt building, and plan pre-population. Extracted from
 * {@code ChatEngine}.
 */
public class SessionManager {

    private final EngineContext ctx;
    private final String systemPrompt;

    /** Format for the time-stamped placeholder task name, e.g. "New Task 0815-214530". */
    private static final DateTimeFormatter PLACEHOLDER_TIME_FORMAT =
        DateTimeFormatter.ofPattern("MMdd-HHmmss");

    public SessionManager(EngineContext ctx, String systemPrompt) {
        this.ctx = ctx;
        this.systemPrompt = systemPrompt;
    }

    public AgentContext getOrCreateSession() {
        if (ctx.chatSession() == null) {
            String sessionId = I18n.tr(MessageKey.CHAT_SESSION_PREFIX) + UUID.randomUUID();
            String projectPath = ctx.projectFolder() != null ? ctx.projectFolder() : ctx.workspace();
            ProjectScanner.ProjectInfo info = ctx.projectScanner().scan(projectPath);
            String backgroundSection = ctx.projectScanner().buildBackgroundSection(info, ctx.workspace(), projectPath);
            String systemPrompt = buildSystemPrompt();
            AgentContext session = ctx.agentFactory().createAgentWithCustomPrompt(systemPrompt, sessionId, projectPath);
            if (session != null && session.chatMemory() instanceof ToolCallAwareChatMemory tcm) {
                tcm.setBackground(backgroundSection);
            }
            ctx.setChatSession(session);
        }
        if (ctx.chatSession() != null) {
            ctx.statusContext().activate();
        }
        return ctx.chatSession();
    }

    /**
     * Build the system prompt, appending a catalog of available skill workflows
     * (name + description) so the LLM can decide semantically which skill to
     * invoke via the {@code invoke_skill_workflow} tool. Skills are loaded first
     * to guarantee the catalog is populated even before the lazy
     * {@code CodeGenLifecycle.initialize()} call inside agent creation.
     */
    private String buildSystemPrompt() {
        ctx.skillRegistry().loadAllSkills();
        String catalog = ctx.skillRegistry().getSkillCatalog();
        if (catalog == null || catalog.isBlank()) {
            return systemPrompt;
        }
        return systemPrompt
            + "\n## Available Skill Workflows\n"
            + "The following multi-agent skill workflows are available. Invoke one by calling "
            + "the `invoke_skill_workflow(skillName, task)` tool when — and only when — the user's "
            + "task semantically matches a listed skill. If you invoke a skill workflow, do so as "
            + "your FIRST action INSTEAD of `createPlan` — the workflow manages its own plan and "
            + "phases. For ordinary tasks you can handle directly, do NOT invoke a skill workflow.\n"
            + catalog;
    }

    public void disposeChatSession() {
        if (ctx.chatSession() != null) {
            try {
                ctx.agentFactory().disposeAgent(ctx.chatSession().sessionId());
            } catch (Exception e) {
                Log.warn("Failed to dispose chat session: " + e.getMessage());
            }
            ctx.setChatSession(null);
        }
        ctx.statusContext().deactivate();
    }

    public void prePopulatePlan(String userInput) {
        for (Object tool : ctx.toolRegistry().getAllTools()) {
            if (tool instanceof PlanTool pt) {
                // Pre-populate a placeholder plan with a non-empty task name so that the
                // plan tool and TaskPersistenceConsumer always have a valid task name.
                // The LLM's own createPlan call replaces this with a better, real name.
                if (!pt.hasInProgressPhases()) {
                    String placeholderName = deriveTaskName();
                    pt.createPlan(placeholderName, new PlanTool.PhaseInput[]{
                        new PlanTool.PhaseInput(placeholderName)
                    });
                }
                break;
            }
        }
    }

    /**
     * Derives a time-stamped placeholder task name in the form
     * "New Task MMDD-HHmmSS" (e.g. "New Task 0815-214530"). This placeholder is
     * used only transiently before the LLM's real {@code createPlan} call
     * replaces it, so a unique, self-explanatory label is preferred over
     * deriving from user input.
     */
    private static String deriveTaskName() {
        return "New Task " + LocalDateTime.now().format(PLACEHOLDER_TIME_FORMAT);
    }
}
