package com.ooooyt.babycommander.workflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StepResultTest {

    @Test
    void testSuccess() {
        StepResult result = StepResult.success("completed");
        assertEquals("completed", result.output());
        assertFalse(result.failed());
    }

    @Test
    void testFailure() {
        StepResult result = StepResult.failure("error occurred");
        assertEquals("error occurred", result.output());
        assertTrue(result.failed());
    }

    @Test
    void testSkipped() {
        StepResult result = StepResult.skipped("dependency failed");
        assertTrue(result.failed());
        assertNotNull(result.output());
        assertTrue(result.output().contains("dependency failed"));
    }

    @Test
    void testSuccessWithEmptyOutput() {
        StepResult result = StepResult.success("");
        assertEquals("", result.output());
        assertFalse(result.failed());
    }

    @Test
    void testFailureWithEmptyMessage() {
        StepResult result = StepResult.failure("");
        assertEquals("", result.output());
        assertTrue(result.failed());
    }

    @Test
    void testRecordEquality() {
        StepResult r1 = new StepResult("output", false);
        StepResult r2 = new StepResult("output", false);
        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void testRecordInequality() {
        StepResult r1 = StepResult.success("ok");
        StepResult r2 = StepResult.failure("error");
        assertNotEquals(r1, r2);
    }

    @Test
    void testSuccessFactoryReturnsNotFailed() {
        StepResult r = StepResult.success("done");
        assertFalse(r.failed());
    }

    @Test
    void testFailureFactoryReturnsFailed() {
        StepResult r = StepResult.failure("fail");
        assertTrue(r.failed());
    }

    @Test
    void testSkippedFactoryReturnsFailed() {
        StepResult r = StepResult.skipped("reason");
        assertTrue(r.failed());
    }
}
