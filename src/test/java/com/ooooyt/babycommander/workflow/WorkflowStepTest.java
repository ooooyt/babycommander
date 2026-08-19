package com.ooooyt.babycommander.workflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowStepTest {

    @Test
    void testRecordConstruction() {
        WorkflowStep step = new WorkflowStep("agent1", "instruction", false, true);
        assertEquals("agent1", step.agentId());
        assertEquals("instruction", step.instruction());
        assertFalse(step.skipOnError());
        assertTrue(step.required());
    }

    @Test
    void testSkipOnErrorTrue() {
        WorkflowStep step = new WorkflowStep("agent1", "instr", true, false);
        assertTrue(step.skipOnError());
        assertFalse(step.required());
    }

    @Test
    void testEquality() {
        WorkflowStep s1 = new WorkflowStep("a", "i", false, true);
        WorkflowStep s2 = new WorkflowStep("a", "i", false, true);
        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
    }

    @Test
    void testInequality() {
        WorkflowStep s1 = new WorkflowStep("a", "i", false, true);
        WorkflowStep s2 = new WorkflowStep("b", "i", false, true);
        assertNotEquals(s1, s2);
    }

    @Test
    void testToString() {
        WorkflowStep step = new WorkflowStep("agent1", "do work", true, false);
        assertNotNull(step.toString());
        assertTrue(step.toString().contains("agent1"));
    }
}
