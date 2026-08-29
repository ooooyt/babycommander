package com.ooooyt.babycommander.shguard;

/**
 * Classification reasons produced by the SH-GUARD semantic analysis pass.
 *
 * <p>Each reason maps to a human-readable explanation and is attached to a
 * {@link ShViolation} so callers (and future UI) can explain <em>why</em> a
 * command was classified the way it was.</p>
 */
public enum ShReason {
    /** Command name is in the dangerous-command policy set (e.g. {@code rm}, {@code dd}). */
    DANGEROUS_COMMAND,
    /** {@code sudo} escalation detected in the command. */
    SUDO_ESCALATION,
    /** Destructive git operation (force push, hard reset, clean, branch delete, ...). */
    DANGEROUS_GIT_OP,
    /** Destructive package-manager operation (apt remove/purge, dpkg -r, rpm -e, ...). */
    DANGEROUS_PACKAGE_OP,
    /** Dangerous flag combination on an otherwise-common command (chmod 777, chown -R, ...). */
    DANGEROUS_FLAG,
    /** Write redirection targeting a system path (e.g. {@code > /etc/passwd}). */
    WRITE_REDIRECT_SYSTEM_PATH,
    /** Write target (redirect or command argument) resolving outside the project folder. */
    WRITE_OUTSIDE_PROJECT,
    /** Database destruction (drop table, truncate, DELETE FROM, ...). */
    DB_DESTRUCTION,
    /** Remote code execution: a fetch command piped into a shell (curl | bash). */
    REMOTE_CODE_EXECUTION,
    /** Fork bomb: a function whose body pipes its own name to itself. */
    FORK_BOMB,
    /** Command name is not in the safe or dangerous policy sets. */
    UNKNOWN_COMMAND,
    /** Command name contains an unresolvable variable or command substitution. */
    VARIABLE_COMMAND_NAME,
    /** The command could not be parsed; classified conservatively. */
    PARSE_FAILURE
}