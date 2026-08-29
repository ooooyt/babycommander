package com.ooooyt.babycommander.shguard;

import java.util.Set;

/**
 * Configurable policy for the SH-GUARD shell command safety analyzer.
 *
 * <p>Ships with defaults that are <strong>identical</strong> to the hard-coded
 * sets previously used by {@code ShellCommandAnalyzer}, so behavior is
 * unchanged out of the box. Instances are immutable; use
 * {@link #withDangerousCommands(Set)} etc. to build customized policies.</p>
 */
public final class ShGuardPolicy {

    /** Command names always considered dangerous regardless of arguments. */
    private final Set<String> dangerousCommands;

    /** Command names treated as safe (read-only / benign) when invoked without dangerous flags. */
    private final Set<String> safeCommands;

    /** Absolute path prefixes considered "system paths" (write redirects to these are dangerous). */
    private final Set<String> systemPathPrefixes;

    /** Write-redirect targets that are explicitly allowed (e.g. {@code /dev/null}). */
    private final Set<String> writeRedirectAllowlist;

    /** Commands that fetch remote content (used for curl|bash detection). */
    private final Set<String> remoteFetchCommands;

    /** Commands that execute shell code (used for curl|bash detection). */
    private final Set<String> shellExecutorCommands;

    /** Whether any {@code sudo} occurrence escalates to DANGEROUS. */
    private final boolean flagSudoAlways;

    /** Whether {@code NAME=value} assignments are folded for command-name resolution. */
    private final boolean foldAssignments;

    /** Whether unknown commands are DANGEROUS (default {@code false} → ASK_ONCE). */
    private final boolean strictUnknownCommand;

    /** Whether write redirection to {@code /} or system prefixes is blocked. */
    private final boolean blockWriteRedirectToRoot;

    private ShGuardPolicy(Builder b) {
        this.dangerousCommands = Set.copyOf(b.dangerousCommands);
        this.safeCommands = Set.copyOf(b.safeCommands);
        this.systemPathPrefixes = Set.copyOf(b.systemPathPrefixes);
        this.writeRedirectAllowlist = Set.copyOf(b.writeRedirectAllowlist);
        this.remoteFetchCommands = Set.copyOf(b.remoteFetchCommands);
        this.shellExecutorCommands = Set.copyOf(b.shellExecutorCommands);
        this.flagSudoAlways = b.flagSudoAlways;
        this.foldAssignments = b.foldAssignments;
        this.strictUnknownCommand = b.strictUnknownCommand;
        this.blockWriteRedirectToRoot = b.blockWriteRedirectToRoot;
    }

    public Set<String> dangerousCommands() {
        return dangerousCommands;
    }

    public Set<String> safeCommands() {
        return safeCommands;
    }

    public Set<String> systemPathPrefixes() {
        return systemPathPrefixes;
    }

    public Set<String> writeRedirectAllowlist() {
        return writeRedirectAllowlist;
    }

    public Set<String> remoteFetchCommands() {
        return remoteFetchCommands;
    }

    public Set<String> shellExecutorCommands() {
        return shellExecutorCommands;
    }

    public boolean flagSudoAlways() {
        return flagSudoAlways;
    }

    public boolean foldAssignments() {
        return foldAssignments;
    }

    public boolean strictUnknownCommand() {
        return strictUnknownCommand;
    }

    public boolean blockWriteRedirectToRoot() {
        return blockWriteRedirectToRoot;
    }

    /** Default policy — mirrors the legacy {@code ShellCommandAnalyzer} sets exactly. */
    public static ShGuardPolicy defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public ShGuardPolicy withDangerousCommands(Set<String> extra) {
        Set<String> merged = new java.util.HashSet<>(dangerousCommands);
        merged.addAll(extra);
        return builder()
                .dangerousCommands(merged)
                .safeCommands(safeCommands)
                .systemPathPrefixes(systemPathPrefixes)
                .writeRedirectAllowlist(writeRedirectAllowlist)
                .remoteFetchCommands(remoteFetchCommands)
                .shellExecutorCommands(shellExecutorCommands)
                .flagSudoAlways(flagSudoAlways)
                .foldAssignments(foldAssignments)
                .strictUnknownCommand(strictUnknownCommand)
                .blockWriteRedirectToRoot(blockWriteRedirectToRoot)
                .build();
    }

    public ShGuardPolicy withSafeCommands(Set<String> extra) {
        Set<String> merged = new java.util.HashSet<>(safeCommands);
        merged.addAll(extra);
        return builder()
                .dangerousCommands(dangerousCommands)
                .safeCommands(merged)
                .systemPathPrefixes(systemPathPrefixes)
                .writeRedirectAllowlist(writeRedirectAllowlist)
                .remoteFetchCommands(remoteFetchCommands)
                .shellExecutorCommands(shellExecutorCommands)
                .flagSudoAlways(flagSudoAlways)
                .foldAssignments(foldAssignments)
                .strictUnknownCommand(strictUnknownCommand)
                .blockWriteRedirectToRoot(blockWriteRedirectToRoot)
                .build();
    }

    public static final class Builder {
        private Set<String> dangerousCommands = Set.of(
                "rm", "dd", "mkfs", "mkfs.ext2", "mkfs.ext3", "mkfs.ext4", "mkfs.xfs",
                "fdisk", "parted", "shutdown", "reboot", "poweroff", "halt",
                "killall", "pkill", "kill", "iptables", "ufw", "route",
                "mkswap", "swapoff", "swapon", "init", "telinit",
                "drop", "truncate", "wipefs", "blkdiscard"
        );

        private Set<String> safeCommands = Set.of(
                "ls", "pwd", "cd", "echo", "cat", "head", "tail", "wc", "diff", "sort",
                "uniq", "grep", "find", "which", "whereis", "type", "man", "help",
                "date", "env", "whoami", "id", "hostname", "uname", "printf",
                "git", "mvn", "gradle", "npm", "npx", "yarn", "pnpm", "node",
                "python", "python3", "pip", "pip3", "java", "javac", "jar", "go", "gofmt",
                "cargo", "rustc", "rustup", "bundle", "rake", "gem", "ruby",
                "composer", "php", "tsc", "bun", "curl", "wget",
                // Read-only filters / pipeline utilities
                "tee", "sed", "awk", "gawk", "mawk", "jq", "less", "more", "cut",
                "column", "paste", "tr", "nl", "od", "hexdump", "xxd", "strings",
                "comm", "join", "look", "ptx", "tsort", "pr", "fmt", "numfmt",
                "factor", "tac", "rev", "shuf", "split", "csplit", "sum", "expand",
                "unexpand", "fold", "xargs", "timeout", "stdbuf", "envsubst",
                "watch", "time",
                // Read-only system info
                "du", "df", "stat", "file", "basename", "dirname", "realpath",
                "readlink", "md5sum", "sha1sum", "sha256sum", "sha512sum", "cksum",
                "uptime", "free", "ps", "top", "htop", "lsof", "ss", "netstat",
                "nproc", "getconf",
                // Logic / benign utilities
                "expr", "test", "true", "false", "sleep", "seq", "yes",
                // Build tools
                "make", "cmake", "ninja", "meson", "sbt", "dotnet"
        );

        private Set<String> systemPathPrefixes = Set.of(
                "/", "/etc", "/boot", "/dev", "/proc", "/sys", "/usr", "/bin",
                "/sbin", "/var", "/lib", "/opt", "/root",
                // $HOME: dotfiles and user config (e.g. ~/.bashrc) are gated.
                System.getProperty("user.home", "/root")
        );

        private Set<String> writeRedirectAllowlist = Set.of(
                "/dev/null", "/dev/stdout", "/dev/stderr", "/dev/tty"
        );

        private Set<String> remoteFetchCommands = Set.of("curl", "wget");

        private Set<String> shellExecutorCommands = Set.of(
                "bash", "sh", "dash", "zsh", "ksh", "fish"
        );

        private boolean flagSudoAlways = true;
        private boolean foldAssignments = true;
        private boolean strictUnknownCommand = false;
        private boolean blockWriteRedirectToRoot = true;

        public Builder dangerousCommands(Set<String> v) { this.dangerousCommands = v; return this; }
        public Builder safeCommands(Set<String> v) { this.safeCommands = v; return this; }
        public Builder systemPathPrefixes(Set<String> v) { this.systemPathPrefixes = v; return this; }
        public Builder writeRedirectAllowlist(Set<String> v) { this.writeRedirectAllowlist = v; return this; }
        public Builder remoteFetchCommands(Set<String> v) { this.remoteFetchCommands = v; return this; }
        public Builder shellExecutorCommands(Set<String> v) { this.shellExecutorCommands = v; return this; }
        public Builder flagSudoAlways(boolean v) { this.flagSudoAlways = v; return this; }
        public Builder foldAssignments(boolean v) { this.foldAssignments = v; return this; }
        public Builder strictUnknownCommand(boolean v) { this.strictUnknownCommand = v; return this; }
        public Builder blockWriteRedirectToRoot(boolean v) { this.blockWriteRedirectToRoot = v; return this; }

        public ShGuardPolicy build() {
            return new ShGuardPolicy(this);
        }
    }
}
