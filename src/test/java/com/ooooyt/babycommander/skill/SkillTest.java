package com.ooooyt.babycommander.skill;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkillTest {

    @Test
    void testRecordConstruction() {
        Skill skill = new Skill("test", "description", List.of("trigger1"), List.of(), "instruction");
        assertEquals("test", skill.name());
        assertEquals("description", skill.description());
        assertEquals(List.of("trigger1"), skill.triggers());
        assertEquals(List.of(), skill.workflowSteps());
        assertEquals("instruction", skill.instructionContent());
    }

    @Test
    void testHasWorkflowWithSteps() {
        Skill skill = new Skill("test", "desc", List.of(), 
            List.of(new SkillWorkflowStep("agent1", "do something")), "instr");
        assertTrue(skill.hasWorkflow());
    }

    @Test
    void testHasWorkflowWithoutSteps() {
        Skill skill = new Skill("test", "desc", List.of(), List.of(), "instr");
        assertFalse(skill.hasWorkflow());
    }

    @Test
    void testHasWorkflowWithNullSteps() {
        Skill skill = new Skill("test", "desc", List.of(), null, "instr");
        assertFalse(skill.hasWorkflow());
    }

    @Test
    void testHasWorkflowWithMultipleSteps() {
        Skill skill = new Skill("test", "desc", List.of(),
            List.of(
                new SkillWorkflowStep("agent1", "step1"),
                new SkillWorkflowStep("agent2", "step2")
            ), "instr");
        assertTrue(skill.hasWorkflow());
    }

    @Test
    void testEmptyTriggers() {
        Skill skill = new Skill("test", "desc", List.of(), List.of(), "instr");
        assertTrue(skill.triggers().isEmpty());
    }

    @Test
    void testMultipleTriggers() {
        Skill skill = new Skill("test", "desc", List.of("trigger1", "trigger2"), List.of(), "instr");
        assertEquals(2, skill.triggers().size());
    }

    @Test
    void testEquality() {
        Skill s1 = new Skill("name", "desc", List.of("t1"), List.of(), "instr");
        Skill s2 = new Skill("name", "desc", List.of("t1"), List.of(), "instr");
        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
    }

    @Test
    void testInequality() {
        Skill s1 = new Skill("name1", "desc", List.of(), List.of(), "instr");
        Skill s2 = new Skill("name2", "desc", List.of(), List.of(), "instr");
        assertNotEquals(s1, s2);
    }
}
