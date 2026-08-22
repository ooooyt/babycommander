package com.ooooyt.babycommander.tool;

/**
 * Lightweight holder for the currently-active task business ID.
 * <p>
 * The task ID is established by {@code TaskPersistenceConsumer} (on the event-bus
 * thread) when a task record is created from the first {@code PlanUpdate} event.
 * Tool execution persistence ({@link ResilientToolExecutor}) reads it on the LLM
 * worker thread so every tool call can be attributed to the current task.
 * <p>
 * A simple volatile field is sufficient here because only one interactive task is
 * active at a time within a session.
 */
public final class TaskContext {

    private static volatile String currentTaskId;

    private TaskContext() {
    }

    public static void setCurrentTaskId(String taskId) {
        currentTaskId = taskId;
    }

    public static String getCurrentTaskId() {
        return currentTaskId;
    }

    public static void clear() {
        currentTaskId = null;
    }
}
