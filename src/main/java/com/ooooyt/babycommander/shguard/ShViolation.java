package com.ooooyt.babycommander.shguard;

/**
 * A single safety violation found by the SH-GUARD analysis pass.
 *
 * @param reason      the classification reason
 * @param detail      human-readable detail (e.g. {@code rm -rf /})
 * @param subCommand  the offending sub-command text, or {@code null} if not applicable
 * @param start       start offset of the offending region in the raw command
 * @param end         end offset (exclusive) of the offending region
 */
public record ShViolation(ShReason reason, String detail, String subCommand, int start, int end) {
}