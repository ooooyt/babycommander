package com.ooooyt.babycommander.orchestrator;

import com.ooooyt.babycommander.editloop.EditLoopResult;

/**
 * Builds a human-readable failure report from an {@link EditLoopResult},
 * shared by the bugfix, refactor and extension strategies.
 */
public final class FailureReport {

    private FailureReport() {
    }

    public static String build(EditLoopResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Final test output:\n").append(truncate(result.testOutput(), 3000)).append("\n\n");
        sb.append("Attempt summaries:\n");
        for (String summary : result.attemptSummaries()) {
            sb.append("  - ").append(summary).append("\n");
        }
        return sb.toString();
    }

    public static String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "... [truncated]";
    }
}
