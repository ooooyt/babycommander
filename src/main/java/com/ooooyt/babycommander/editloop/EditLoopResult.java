package com.ooooyt.babycommander.editloop;

import java.util.List;

/**
 * Result of an EditLoop execution.
 * success: whether the loop ultimately succeeded
 * testOutput: final test output (pass or fail)
 * attempts: number of fix attempts made
 * attemptSummaries: description of each fix attempt (for debugging/reporting)
 */
public record EditLoopResult(
    boolean success,
    String testOutput,
    int attempts,
    List<String> attemptSummaries
) {
    public static EditLoopResult success(String testOutput) {
        return new EditLoopResult(true, testOutput, 0, List.of());
    }

    public static EditLoopResult success(String testOutput, int attempts) {
        return new EditLoopResult(true, testOutput, attempts, List.of());
    }

    public static EditLoopResult failure(String testOutput, int attempts, List<String> attemptSummaries) {
        return new EditLoopResult(false, testOutput, attempts, attemptSummaries);
    }
}
