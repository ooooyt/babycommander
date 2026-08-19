package com.ooooyt.babycommander.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillWorkflowStepTest {

    @Test
    void testRecordConstruction() {
        SkillWorkflowStep step = new SkillWorkflowStep("agent1", "write code");
        assertEquals("agent1", step.agentId());
        assertEquals("write code", step.instruction());
    }

    @Test
    void testEquality() {
        SkillWorkflowStep s1 = new SkillWorkflowStep("a", "instr");
        SkillWorkflowStep s2 = new SkillWorkflowStep("a", "instr");
        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
    }

    @Test
    void testInequality() {
        SkillWorkflowStep s1 = new SkillWorkflowStep("a", "instr1");
        SkillWorkflowStep s2 = new SkillWorkflowStep("a", "instr2");
        assertNotEquals(s1, s2);
    }

    @Test
    void testToString() {
        SkillWorkflowStep step = new SkillWorkflowStep("agent1", "do work");
        assertNotNull(step.toString());
        assertTrue(step.toString().contains("agent1"));
    }
}
