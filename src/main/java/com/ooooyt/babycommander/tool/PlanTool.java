package com.ooooyt.babycommander.tool;

import com.ooooyt.babycommander.ui.UiEvent;
import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;

import dev.langchain4j.agent.tool.Tool;
import io.quarkus.logging.Log;
import io.vertx.mutiny.core.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class PlanTool {

    /**
     * Structured input for a plan phase: a brief title (max 10 words) and an
     * optional fuller description (soft max 30 words). Titles within a task
     * must be unique so each phase can be referenced unambiguously.
     */
    public record PhaseInput(String title, String description) {
        public PhaseInput(String title) {
            this(title, null);
        }
    }

    private static final int MAX_TITLE_WORDS = 10;
    private static final int MAX_DESCRIPTION_WORDS = 30;

    private static final String UI_EVENT_ADDRESS = "ui.event";
    private static final AtomicReference<List<UiEvent.Phase>> latestSnapshot =
        new AtomicReference<>(List.of());
    private static final AtomicReference<String> latestTask =
        new AtomicReference<>("");

    private final List<UiEvent.Phase> phases = new CopyOnWriteArrayList<>();
    private final EventBus eventBus;

    public PlanTool(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public static List<UiEvent.Phase> getLatestSnapshot() {
        return latestSnapshot.get();
    }

    public static String getLatestTask() {
        return latestTask.get();
    }

    public boolean hasPhases() {
        return !phases.isEmpty();
    }

    /**
     * Returns true if there are any phases with status "active" or "pending",
     * meaning the current task is still in progress.
     */
    public boolean hasInProgressPhases() {
        return phases.stream().anyMatch(p ->
            "active".equals(p.status()) || "pending".equals(p.status())
        );
    }

    @Tool("Create a numbered plan with phases for the current task. The 'task' parameter "
        + "is REQUIRED: it must be a non-empty, concise natural-language description of "
        + "the task. You MUST always provide a meaningful task name and NEVER pass an "
        + "empty string. Each phase has a brief title (max 10 words) and an optional "
        + "fuller description (soft max 30 words). Titles within a task must be unique "
        + "so each phase can be referenced unambiguously.")
    public String createPlan(String task, PhaseInput[] phases) {
        // Guard: reject empty task names so a task is always created with a real name.
        if (task == null || task.isBlank()) {
            Log.warn("PlanTool.createPlan rejected: task name is empty");
            return "Error: 'task' must be a non-empty, descriptive summary of the task. "
                 + "Please provide a meaningful task name and try again.";
        }
        // Guard: if there is already an in-progress plan, reject replacement.
        // Allow replacement if the existing plan is a placeholder (single "active" phase
        // pre-populated by ChatEngine.prePopulatePlan). This ensures the agent can always
        // create its real plan as instructed by the system prompt.
        boolean isPlaceholder = this.phases.size() == 1
            && "active".equals(this.phases.getFirst().status());

        if (hasInProgressPhases() && !isPlaceholder) {
            Log.warnf("PlanTool.createPlan rejected: task still in progress (existing plan has %d phases)",
                this.phases.size());
            return "Cannot create a new plan: the current task with phases is still in progress. "
                 + "Complete or fail the current phases first before starting a new task.";
        }

        Log.infof("PlanTool.createPlan called: task='%s', %d phases", task, phases.length);
        latestTask.set(task != null ? task : "");
        this.phases.clear();
        for (int i = 0; i < phases.length; i++) {
            String status = i == 0 ? "active" : "pending";
            PhaseInput input = phases[i];
            String title = truncateToWords(input.title(), MAX_TITLE_WORDS);
            String desc = truncateToWords(input.description(), MAX_DESCRIPTION_WORDS);
            if (desc == null || desc.isBlank()) {
                desc = title; // fall back to title when no description provided
            }
            this.phases.add(new UiEvent.Phase(title, desc, status));
        }
        // Enforce title uniqueness within the task (disambiguate duplicates by index).
        enforceUniqueTitles();
        publishUpdate();
        return I18n.tr(MessageKey.PLAN_CREATED, phases.length);
    }

    /**
     * Backward-compatible overload for programmatic callers that pass single-string
     * phases (title and description both default to the given string).
     */
    public String createPlan(String task, String[] phases) {
        PhaseInput[] inputs = new PhaseInput[phases.length];
        for (int i = 0; i < phases.length; i++) {
            inputs[i] = new PhaseInput(phases[i]);
        }
        return createPlan(task, inputs);
    }

    /** Truncate a string to at most {@code maxWords} words. Returns null for null input. */
    private static String truncateToWords(String text, int maxWords) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        String[] words = trimmed.split("\\s+");
        if (words.length <= maxWords) {
            return trimmed;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxWords; i++) {
            if (i > 0) sb.append(' ');
            sb.append(words[i]);
        }
        return sb.toString();
    }

    /** Ensure all phase titles within the task are unique by disambiguating duplicates. */
    private void enforceUniqueTitles() {
        java.util.Map<String, Integer> seen = new java.util.HashMap<>();
        for (int i = 0; i < phases.size(); i++) {
            UiEvent.Phase p = phases.get(i);
            String title = p.title();
            Integer count = seen.get(title);
            if (count == null) {
                seen.put(title, 1);
            } else {
                seen.put(title, count + 1);
                String disambiguated = title + " (" + (count + 1) + ")";
                phases.set(i, new UiEvent.Phase(disambiguated, p.description(), p.status()));
            }
        }
    }

    @Tool("Mark phase N as completed")
    public String completePhase(int phaseNumber) {
        if (phaseNumber < 1 || phaseNumber > phases.size()) {
            String msg = I18n.tr(MessageKey.PLAN_ERROR_NOT_EXIST, phaseNumber, phases.size());
            Log.warnf("PlanTool.completePhase: %s", msg);
            return msg;
        }
        int idx = phaseNumber - 1;
        UiEvent.Phase current = phases.get(idx);
        if (!"active".equals(current.status())) {
            String msg = I18n.tr(MessageKey.PLAN_ERROR_NOT_ACTIVE, phaseNumber, current.status());
            Log.warnf("PlanTool.completePhase: %s", msg);
            return msg;
        }
        phases.set(idx, new UiEvent.Phase(current.title(), current.description(), "completed"));
        if (phaseNumber < phases.size()) {
            UiEvent.Phase next = phases.get(phaseNumber);
            phases.set(phaseNumber, new UiEvent.Phase(next.title(), next.description(), "active"));
        }
        publishUpdate();
        Log.infof("PlanTool.completePhase: phase %d completed", phaseNumber);
        if (phaseNumber < phases.size()) {
            return I18n.tr(MessageKey.PLAN_PHASE_COMPLETED, phaseNumber, phaseNumber + 1);
        }
        return I18n.tr(MessageKey.PLAN_ALL_COMPLETED, phaseNumber);
    }

    @Tool("Mark phase N as failed")
    public String failPhase(int phaseNumber) {
        if (phaseNumber < 1 || phaseNumber > phases.size()) {
            String msg = I18n.tr(MessageKey.PLAN_ERROR_NOT_EXIST_SIMPLE, phaseNumber);
            Log.warnf("PlanTool.failPhase: %s", msg);
            return msg;
        }
        int idx = phaseNumber - 1;
        UiEvent.Phase current = phases.get(idx);
        phases.set(idx, new UiEvent.Phase(current.title(), current.description(), "failed"));
        publishUpdate();
        Log.warnf("PlanTool.failPhase: phase %d failed", phaseNumber);
        return I18n.tr(MessageKey.PLAN_PHASE_FAILED, phaseNumber);
    }

    /**
     * Complete all remaining in-progress (active or pending) phases.
     * This is called by ChatEngine after the LLM finishes, as a safety net
     * in case the LLM forgot to call completePhase for the final phase(s).
     */
    public void completeAllRemainingPhases() {
        boolean changed = false;
        for (int i = 0; i < phases.size(); i++) {
            UiEvent.Phase p = phases.get(i);
            if ("active".equals(p.status()) || "pending".equals(p.status())) {
                phases.set(i, new UiEvent.Phase(p.title(), p.description(), "completed"));
                changed = true;
            }
        }
        if (changed) {
            publishUpdate();
            Log.infof("PlanTool.completeAllRemainingPhases: completed all remaining phases");
        }
    }

    /**
     * Fail all remaining in-progress (active or pending) phases.
     * This is called by ChatEngine when an exception occurs during LLM execution,
     * so the remaining phases honestly reflect the failure rather than being
     * incorrectly marked as completed.
     */
    public void failAllRemainingPhases() {
        boolean changed = false;
        for (int i = 0; i < phases.size(); i++) {
            UiEvent.Phase p = phases.get(i);
            if ("active".equals(p.status()) || "pending".equals(p.status())) {
                phases.set(i, new UiEvent.Phase(p.title(), p.description(), "failed"));
                changed = true;
            }
        }
        if (changed) {
            publishUpdate();
            Log.warnf("PlanTool.failAllRemainingPhases: failed all remaining phases due to error");
        }
    }

    List<UiEvent.Phase> getPhases() {
        return new ArrayList<>(phases);
    }

    private void publishUpdate() {
        List<UiEvent.Phase> snapshot = new ArrayList<>(phases);
        latestSnapshot.set(snapshot);
        if (eventBus != null) {
            eventBus.publish(UI_EVENT_ADDRESS, new UiEvent.PlanUpdate(snapshot, latestTask.get()));
        }
    }
}
