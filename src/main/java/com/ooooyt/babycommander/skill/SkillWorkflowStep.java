package com.ooooyt.babycommander.skill;

/**
 * A single step in a skill-defined workflow.
 * Each step specifies an agent role and the instruction to execute.
 */
public record SkillWorkflowStep(
    String agentId,
    String instruction
) {}
