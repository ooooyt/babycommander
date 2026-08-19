package com.ooooyt.babycommander.intent;

import com.ooooyt.babycommander.agent.AgentContext;
import com.ooooyt.babycommander.agent.AgentFactory;
import com.ooooyt.babycommander.model.AgentRole;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Router agent that uses an LLM-based classification to decide whether a
 * user request is complex enough to warrant a multi-agent workflow.
 *
 * <p>This is used as a fallback when the {@link ComplexityAnalyzer} heuristic
 * is inconclusive — i.e., the score is in the borderline range. The router
 * agent analyzes the task semantically and returns a structured decision.</p>
 *
 * <p>The router is invoked with a dedicated prompt that asks it to classify
 * the request as COMPLEX or SIMPLE, along with a brief rationale. This
 * provides better accuracy than keyword matching alone for ambiguous tasks.</p>
 */
@ApplicationScoped
public class ComplexityRouter {

    /** Score threshold below which we always route to single-agent (no router needed). */
    static final int SIMPLE_THRESHOLD = 3;

    /** Score threshold above which we always route to multi-agent (no router needed). */
    static final int COMPLEX_THRESHOLD = 6;

    private final AgentFactory agentFactory;

    public ComplexityRouter(AgentFactory agentFactory) {
        this.agentFactory = agentFactory;
    }

    /**
     * Determine whether a task is complex enough for multi-agent workflow.
     *
     * <p>Decision logic:</p>
     * <ul>
     *   <li>If {@link ComplexityAnalyzer#score(String)} &lt; {@value #SIMPLE_THRESHOLD} → {@code false}</li>
     *   <li>If {@link ComplexityAnalyzer#score(String)} &gt;= {@value #COMPLEX_THRESHOLD} → {@code true}</li>
     *   <li>Otherwise → consult the router agent via LLM</li>
     * </ul>
     *
     * @param task the user's input prompt
     * @param sessionId the current session ID
     * @param projectFolder the project folder path
     * @return true if the task should use a multi-agent workflow
     */
    public boolean isComplex(String task, String sessionId, String projectFolder) {
        int heuristicScore = ComplexityAnalyzer.score(task);
        Log.debugf("ComplexityRouter: heuristic score=%d for task: %.60s", heuristicScore, task);

        // Fast path: clearly simple
        if (heuristicScore < SIMPLE_THRESHOLD) {
            Log.debug("ComplexityRouter: heuristic says SIMPLE, skipping router agent");
            return false;
        }

        // Fast path: clearly complex
        if (heuristicScore >= COMPLEX_THRESHOLD) {
            Log.debug("ComplexityRouter: heuristic says COMPLEX, skipping router agent");
            return true;
        }

        // Borderline: consult the router agent
        Log.infof("ComplexityRouter: borderline score=%d, consulting router agent", heuristicScore);
        return consultRouterAgent(task, sessionId, projectFolder);
    }

    /**
     * Consult the router agent via LLM to classify task complexity.
     *
     * @param task the user's input prompt
     * @param sessionId the current session ID
     * @param projectFolder the project folder path
     * @return true if the router classifies the task as COMPLEX
     */
    private boolean consultRouterAgent(String task, String sessionId, String projectFolder) {
        AgentContext router = agentFactory.createAgent(AgentRole.ROUTER.getValue(), sessionId, projectFolder);
        try {
            String systemPrompt = buildRouterSystemPrompt();
            String response = router.agent().chatWithSystemPrompt(systemPrompt, task);
            return parseRouterResponse(response);
        } catch (Exception e) {
            Log.warnf(e, "ComplexityRouter: router agent call failed, defaulting to single-agent");
            return false;
        } finally {
            agentFactory.disposeAgent(router.sessionId());
        }
    }

    /**
     * Build the system prompt for the router agent.
     * This overrides the generic router prompt from agents.yaml.
     */
    private static String buildRouterSystemPrompt() {
        return """
You are a complexity classifier for a code generation system.
Your job is to determine whether a user request is complex enough to require
a multi-agent workflow (planner → writer → tester) or simple enough for a
single agent to handle.

A request is COMPLEX if it involves ANY of:
- Multiple components, modules, or services
- Architectural design decisions
- Database schema changes or data migration
- Cross-cutting concerns (auth, caching, messaging)
- Multiple API endpoints or integration points
- Framework or technology stack changes
- Large-scale refactoring or restructuring
- More than 3 distinct requirements or changes
- Coordination between different parts of the system

A request is SIMPLE if it:
- Involves a single file or small change
- Is a straightforward bug fix or typo correction
- Adds a single method or small utility
- Updates documentation or comments
- Changes configuration values

Respond with EXACTLY one line:
COMPLEX: <brief reason>
or
SIMPLE: <brief reason>
""".trim();
    }

    /**
     * Parse the router agent's response to extract the classification.
     * Looks for "COMPLEX" or "SIMPLE" at the start of the response.
     */
    static boolean parseRouterResponse(String response) {
        if (response == null || response.isBlank()) {
            return false;
        }
        String upper = response.trim().toUpperCase();
        if (upper.startsWith("COMPLEX")) {
            return true;
        }
        if (upper.startsWith("SIMPLE")) {
            return false;
        }
        // Fallback: if neither keyword found, default to simple
        Log.warnf("ComplexityRouter: ambiguous router response: %.100s", response);
        return false;
    }
}
