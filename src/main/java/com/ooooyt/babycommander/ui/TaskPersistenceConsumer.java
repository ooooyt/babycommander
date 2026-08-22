package com.ooooyt.babycommander.ui;

import com.ooooyt.babycommander.db.entity.ProjectEntity;
import com.ooooyt.babycommander.db.entity.TaskEntity;
import com.ooooyt.babycommander.service.ProjectTaskService;
import com.ooooyt.babycommander.tool.PlanTool;
import com.ooooyt.babycommander.tool.TaskContext;
import io.quarkus.logging.Log;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

/**
 * Consumes {@link UiEvent.PlanUpdate} events from the event bus and persists
 * task information into ObjectBox via {@link ProjectTaskService}.
 * <p>
 * This consumer mirrors the pattern used by {@code TerminalUIAdapter}, which
 * also listens to the same events for UI rendering.
 * <ul>
 *   <li>On the first {@code PlanUpdate} event, it creates a new
 *       {@code TaskEntity} record (status {@code STARTED}) linked to the
 *       current project. (CREATE)</li>
 *   <li>On every subsequent {@code PlanUpdate} event, it updates the task
 *       record to reflect the latest phase statuses. (UPDATE)</li>
 *   <li>Individual phases are persisted alongside the task, and their statuses
 *       are updated as the plan progresses.</li>
 *   <li>When all phases are completed → status {@code COMPLETED}.</li>
 *   <li>When any phase has failed → status {@code FAILED}.</li>
 *   <li>Otherwise → status remains {@code STARTED} (updatedDatetime refreshed).</li>
 * </ul>
 */
@ApplicationScoped
public class TaskPersistenceConsumer {

    private static final String UI_EVENT_ADDRESS = "ui.event";

    @Inject
    EventBus eventBus;

    @Inject
    ProjectTaskService projectTaskService;

    /**
     * The absolute path of the project folder, set by {@link ChatEngine#start}
     * before the consumer is registered.
     */
    private volatile String projectFolder;

    /**
     * The business ID of the created task, so we can update its status later.
     */
    private volatile String taskId;

    /**
     * The last task name persisted for the current task, so we can detect when
     * the LLM replaces the placeholder name with a real one and rename the
     * database record accordingly.
     */
    private volatile String taskName;

    /**
     * Whether phases have been persisted for the current task. Used to decide
     * between a full replace (first snapshot / plan change) and incremental
     * status updates on subsequent events.
     */
    private volatile boolean phasesInitialized;

    /** The number of phases last persisted, to detect plan changes. */
    private volatile int persistedPhaseCount;

    /**
     * Registers the event bus consumer. Must be called after {@code projectFolder}
     * has been set.
     */
    public void start(String projectFolder) {
        this.projectFolder = projectFolder;
        this.taskId = null;
        this.taskName = null;
        this.phasesInitialized = false;
        this.persistedPhaseCount = 0;

        eventBus.consumer(UI_EVENT_ADDRESS, message -> {
            if (message.body() instanceof UiEvent.PlanUpdate update) {
                onPlanUpdate(update);
            }
        });

        Log.infof("TaskPersistenceConsumer started for project folder: %s", projectFolder);
    }

    /**
     * Resets the tracked task ID so that the next PlanUpdate event will create
     * a new task record instead of updating the previous one.
     * <p>
     * This must be called by {@link ChatEngine} before {@code prePopulatePlan()}
     * when a new user interaction begins, to ensure each conversation turn
     * produces its own task entry in ObjectBox.
     */
    public void resetTaskId() {
        this.taskId = null;
        this.taskName = null;
        this.phasesInitialized = false;
        this.persistedPhaseCount = 0;
        TaskContext.clear();
    }

    private void onPlanUpdate(UiEvent.PlanUpdate update) {
        if (projectFolder == null || projectFolder.isBlank()) {
            return;
        }

        List<UiEvent.Phase> phases = update.phases();
        if (phases == null || phases.isEmpty()) {
            return;
        }

        // ── CREATE: Create task on first PlanUpdate ──────────────────────
        if (taskId == null) {
            String taskName = PlanTool.getLatestTask();
            if (taskName == null || taskName.isBlank()) {
                Log.warn("TaskPersistenceConsumer: task name is empty, skipping task creation");
                return;
            }

            // Ensure project record exists and get its ID
            ProjectEntity project = projectTaskService.ensureProjectRecord(projectFolder);
            if (project == null) {
                Log.errorf("TaskPersistenceConsumer: failed to get/create project record for path '%s'",
                    projectFolder);
                return;
            }

            try {
                TaskEntity task = projectTaskService.createTask(project.id, taskName);
                taskId = task.id;
                this.taskName = taskName;
                TaskContext.setCurrentTaskId(taskId);
                Log.infof("TaskPersistenceConsumer: created task '%s' (id=%s) for project '%s'",
                    taskName, taskId, project.name);
            } catch (Exception e) {
                Log.errorf("TaskPersistenceConsumer: failed to create task '%s': %s", taskName, e.getMessage());
                return;
            }
        }

        // ── RENAME: Reflect a real task name from the LLM ────────────────
        // The placeholder name (derived from the user's raw input) is replaced
        // once the LLM calls createPlan with a meaningful task name. Detect the
        // change and update the persisted record so history shows real names.
        String newTaskName = update.taskName();
        if (taskId != null && newTaskName != null && !newTaskName.isBlank()
                && !newTaskName.equals(this.taskName)) {
            try {
                projectTaskService.renameTask(taskId, newTaskName);
                this.taskName = newTaskName;
                Log.infof("TaskPersistenceConsumer: renamed task %s to '%s'", taskId, newTaskName);
            } catch (Exception e) {
                Log.errorf("TaskPersistenceConsumer: failed to rename task %s to '%s': %s",
                    taskId, newTaskName, e.getMessage());
            }
        }

        // ── PHASES: Persist phases and update their statuses ─────────────
        persistPhases(taskId, phases);

        // ── UPDATE: Check for terminal states ────────────────────────────
        boolean allCompleted = true;
        boolean anyFailed = false;

        for (UiEvent.Phase phase : phases) {
            String status = phase.status();
            if (!"completed".equals(status)) {
                allCompleted = false;
            }
            if ("failed".equals(status)) {
                anyFailed = true;
            }
        }

        if (anyFailed) {
            try {
                projectTaskService.updateTaskStatus(taskId, TaskEntity.Status.FAILED);
                Log.infof("TaskPersistenceConsumer: task %s marked as FAILED", taskId);
            } catch (Exception e) {
                Log.errorf("TaskPersistenceConsumer: failed to update task %s to FAILED: %s",
                    taskId, e.getMessage());
            }
        } else if (allCompleted && phases.size() > 0) {
            try {
                projectTaskService.updateTaskStatus(taskId, TaskEntity.Status.COMPLETED);
                Log.infof("TaskPersistenceConsumer: task %s marked as COMPLETED", taskId);
            } catch (Exception e) {
                Log.errorf("TaskPersistenceConsumer: failed to update task %s to COMPLETED: %s",
                    taskId, e.getMessage());
            }
        } else {
            // ── UPDATE: Persist progress on every intermediate PlanUpdate ──
            // This refreshes updatedDatetime so the database reflects that the
            // task is still in progress.
            try {
                projectTaskService.updateTaskStatus(taskId, TaskEntity.Status.STARTED);
                Log.debugf("TaskPersistenceConsumer: task %s updated (still in progress)", taskId);
            } catch (Exception e) {
                Log.errorf("TaskPersistenceConsumer: failed to update task %s: %s",
                    taskId, e.getMessage());
            }
        }
    }

    /**
     * Persists the given phases for the task.
     * <p>
     * On the first snapshot, or whenever the number of phases changes (e.g. the
     * placeholder plan is replaced by the LLM's real plan), the phase set is
     * fully replaced. Otherwise only phase statuses are updated incrementally.
     *
     * @param taskId the task business ID
     * @param phases the phase snapshot from the PlanUpdate event
     */
    private void persistPhases(String taskId, List<UiEvent.Phase> phases) {
        try {
            if (!phasesInitialized || phases.size() != persistedPhaseCount) {
                List<ProjectTaskService.PhaseData> data = new ArrayList<>();
                for (UiEvent.Phase phase : phases) {
                    data.add(new ProjectTaskService.PhaseData(
                        phase.title(), phase.description(), phase.status()));
                }
                projectTaskService.replacePhases(taskId, data);
                phasesInitialized = true;
                persistedPhaseCount = phases.size();
                Log.debugf("TaskPersistenceConsumer: persisted %d phases for task %s", phases.size(), taskId);
            } else {
                for (int i = 0; i < phases.size(); i++) {
                    projectTaskService.updatePhaseStatus(taskId, i, phases.get(i).status());
                }
            }
        } catch (Exception e) {
            Log.errorf("TaskPersistenceConsumer: failed to persist phases for task %s: %s",
                taskId, e.getMessage());
        }
    }
}
