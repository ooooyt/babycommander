package com.ooooyt.babycommander.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowTypeTest {

    @Test
    void testValues() {
        assertEquals(3, WorkflowType.values().length);
    }

    @Test
    void testSingle() {
        assertEquals(WorkflowType.SINGLE, WorkflowType.valueOf("SINGLE"));
    }

    @Test
    void testSequential() {
        assertEquals(WorkflowType.SEQUENTIAL, WorkflowType.valueOf("SEQUENTIAL"));
    }

    @Test
    void testParallel() {
        assertEquals(WorkflowType.PARALLEL, WorkflowType.valueOf("PARALLEL"));
    }

    @Test
    void testOrdinals() {
        assertEquals(0, WorkflowType.SINGLE.ordinal());
        assertEquals(1, WorkflowType.SEQUENTIAL.ordinal());
        assertEquals(2, WorkflowType.PARALLEL.ordinal());
    }
}
