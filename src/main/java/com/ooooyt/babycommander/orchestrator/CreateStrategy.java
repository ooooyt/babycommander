package com.ooooyt.babycommander.orchestrator;

import com.ooooyt.babycommander.intent.IntentDetector;
import com.ooooyt.babycommander.intent.IntentDetector.Mode;
import com.ooooyt.babycommander.model.TaskResult;
import com.ooooyt.babycommander.status.StatusEventPublisher;

import java.util.Map;

/**
 * CREATE strategy: a CREATE (or undetectable) task is routed to the default
 * multi-agent pipeline via {@link SkillWorkflowExecutor#executeMultiAgent}.
 * If the task text actually indicates a different mode, it re-dispatches to
 * that mode's strategy.
 */
public class CreateStrategy implements ModeStrategy {

    private final SkillWorkflowExecutor skillWorkflowExecutor;
    private final StatusEventPublisher statusPublisher;
    private final Map<Mode, ModeStrategy> siblings;

    public CreateStrategy(SkillWorkflowExecutor skillWorkflowExecutor, StatusEventPublisher statusPublisher,
                          Map<Mode, ModeStrategy> siblings) {
        this.skillWorkflowExecutor = skillWorkflowExecutor;
        this.statusPublisher = statusPublisher;
        this.siblings = siblings;
    }

    @Override
    public TaskResult execute(String task, long workflowStart) {
        // Use IntentDetector to check if the task is clearly a simple task
        // that can be handled by a single agent
        Mode mode = IntentDetector.detect(task);
        if (mode == Mode.CREATE || mode == null) {
            // For CREATE tasks, use multi-agent (planner + writer + tester)
            TaskResult result = skillWorkflowExecutor.executeMultiAgent(task);
            statusPublisher.workflowCompleted(result.status().name(), System.currentTimeMillis() - workflowStart);
            return result;
        }
        // For other modes detected within CREATE, delegate to the mode-specific handler
        ModeStrategy delegate = siblings.get(mode);
        if (delegate != null) {
            return delegate.execute(task, workflowStart);
        }
        TaskResult r = skillWorkflowExecutor.executeMultiAgent(task);
        statusPublisher.workflowCompleted(r.status().name(), System.currentTimeMillis() - workflowStart);
        return r;
    }
}
