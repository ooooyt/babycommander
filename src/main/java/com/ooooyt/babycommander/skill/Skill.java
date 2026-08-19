package com.ooooyt.babycommander.skill;

import java.util.List;

public record Skill(
    String name,
    String description,
    List<String> triggers,
    List<SkillWorkflowStep> workflowSteps,
    String instructionContent
) {

    /**
     * Returns true if this skill defines a multi-agent workflow (has workflow steps).
     */
    public boolean hasWorkflow() {
        return workflowSteps != null && !workflowSteps.isEmpty();
    }
}
