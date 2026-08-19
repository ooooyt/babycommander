package com.ooooyt.babycommander.editloop;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EditLoopResultTest {

    @Test
    void successWithTestOutputOnly() {
        EditLoopResult result = EditLoopResult.success("All tests passed (5 tests)");

        assertTrue(result.success());
        assertEquals("All tests passed (5 tests)", result.testOutput());
        assertEquals(0, result.attempts());
        assertTrue(result.attemptSummaries().isEmpty());
    }

    @Test
    void successWithTestOutputAndAttempts() {
        EditLoopResult result = EditLoopResult.success("All tests passed (3 tests)", 2);

        assertTrue(result.success());
        assertEquals("All tests passed (3 tests)", result.testOutput());
        assertEquals(2, result.attempts());
        assertTrue(result.attemptSummaries().isEmpty());
    }

    @Test
    void failureWithAllFields() {
        List<String> summaries = List.of(
            "Attempt 1: Fixed NullPointerException",
            "Attempt 2: Fixed ArrayIndexOutOfBounds"
        );
        EditLoopResult result = EditLoopResult.failure("FAILURE: Test still failing", 2, summaries);

        assertFalse(result.success());
        assertEquals("FAILURE: Test still failing", result.testOutput());
        assertEquals(2, result.attempts());
        assertEquals(2, result.attemptSummaries().size());
        assertEquals("Attempt 1: Fixed NullPointerException", result.attemptSummaries().get(0));
        assertEquals("Attempt 2: Fixed ArrayIndexOutOfBounds", result.attemptSummaries().get(1));
    }

    @Test
    void failureWithEmptySummaries() {
        EditLoopResult result = EditLoopResult.failure("Build error", 1, List.of());

        assertFalse(result.success());
        assertEquals("Build error", result.testOutput());
        assertEquals(1, result.attempts());
        assertTrue(result.attemptSummaries().isEmpty());
    }

    @Test
    void failureWithNullTestOutput() {
        List<String> summaries = List.of("Attempt 1: Fixed");
        EditLoopResult result = EditLoopResult.failure(null, 3, summaries);

        assertFalse(result.success());
        assertNull(result.testOutput());
        assertEquals(3, result.attempts());
        assertEquals(1, result.attemptSummaries().size());
    }

    @Test
    void recordComponentsAreAccessible() {
        List<String> summaries = List.of("summary1", "summary2");
        EditLoopResult result = new EditLoopResult(true, "output", 1, summaries);

        assertTrue(result.success());
        assertEquals("output", result.testOutput());
        assertEquals(1, result.attempts());
        assertEquals(summaries, result.attemptSummaries());
    }

    @Test
    void recordEqualsAndHashCode() {
        EditLoopResult r1 = EditLoopResult.success("passed", 1);
        EditLoopResult r2 = EditLoopResult.success("passed", 1);
        EditLoopResult r3 = EditLoopResult.success("different", 1);

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
        assertNotEquals(r1, r3);
    }

    @Test
    void recordToString() {
        List<String> summaries = List.of("fix attempt");
        EditLoopResult result = EditLoopResult.failure("error", 1, summaries);

        String str = result.toString();
        assertTrue(str.contains("success=false"));
        assertTrue(str.contains("testOutput=error"));
        assertTrue(str.contains("attempts=1"));
        assertTrue(str.contains("attemptSummaries"));
    }

    @Test
    void successWithZeroAttemptsAndEmptyOutput() {
        EditLoopResult result = EditLoopResult.success("", 0);

        assertTrue(result.success());
        assertEquals("", result.testOutput());
        assertEquals(0, result.attempts());
        assertTrue(result.attemptSummaries().isEmpty());
    }
}
