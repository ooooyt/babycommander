package com.ooooyt.babycommander.shguard;

import com.ooooyt.babycommander.hook.DangerLevel;

import java.util.List;

/**
 * Result of the SH-GUARD semantic analysis of a shell command.
 *
 * @param level       the computed danger level (never {@code null})
 * @param violations  zero or more violations explaining the classification
 * @param parseError  non-null when the command could not be fully parsed
 * @param parsed      {@code true} when the command parsed without errors
 */
public record ShGuardReport(
        DangerLevel level,
        List<ShViolation> violations,
        String parseError,
        boolean parsed) {

    public static ShGuardReport of(DangerLevel level, List<ShViolation> violations) {
        return new ShGuardReport(level, violations, null, true);
    }

    public static ShGuardReport parseError(String message) {
        return new ShGuardReport(DangerLevel.ASK_ONCE, List.of(), message, false);
    }
}