package com.ooooyt.babycommander.orchestrator;

import com.ooooyt.babycommander.model.TaskResult;

/**
 * A single execution-mode strategy (bugfix, refactor, extension, create,
 * document, single-agent). Each implementation owns its mode-specific
 * orchestration and publishes its own {@code workflowCompleted} status; the
 * caller ({@link Orchestrator}) wraps the call with {@code workflowStarted}
 * and status-context activation.
 */
public interface ModeStrategy {

    /**
     * Execute the task in this strategy's mode.
     *
     * @param task           the task description
     * @param workflowStart  the timestamp at which the enclosing workflow started
     * @return the result of the mode-specific execution
     */
    TaskResult execute(String task, long workflowStart);
}
