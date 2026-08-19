package com.ooooyt.babycommander.workflow;

import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;
/**
 * Result of executing a single workflow step.
 * Replaces the fragile "STEP_FAILED" magic string approach with a proper typed result.
 */
public record StepResult(String output, boolean failed) {

    public static StepResult success(String output) {
        return new StepResult(output, false);
    }

    public static StepResult failure(String errorMessage) {
        return new StepResult(errorMessage, true);
    }

    public static StepResult skipped(String reason) {
        return new StepResult(I18n.tr(MessageKey.STEP_SKIPPED_PREFIX, reason), true);
    }
}
