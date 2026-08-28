package com.ooooyt.babycommander.hook;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Token-based analyzer for shell commands.
 *
 * <p>Instead of relying solely on whole-command regular expressions, this class
 * tokenizes a shell command, splits it into individual sub-commands at shell
 * operators ({@code &&}, {@code ||}, {@code ;}, {@code |}, {@code &}, newline),
 * and inspects the command name and arguments of each sub-command to classify the
 * overall command as {@link DangerLevel#SAFE}, {@link DangerLevel#ASK_ONCE}, or
 * {@link DangerLevel#DANGEROUS}.</p>
 *
 * <p>The goal is to reduce confirmation fatigue during continued shell executions:
 * benign, read-only commands (e.g. {@code git status}, {@code cat file},
 * {@code mvn test}) are auto-allowed, while commands that contain any destructive
 * or system-modifying token are always flagged {@code DANGEROUS}.</p>
 */
public final class ShellCommandAnalyzerLegacy {

    /**
     * Command names that are always considered dangerous regardless of arguments,
     * because they can destroy data, modify the system, or escalate privileges.
     */
    private static final Set<String> DANGEROUS_COMMANDS = Set.of(
            "rm", "dd", "mkfs", "mkfs.ext2", "mkfs.ext3", "mkfs.ext4", "mkfs.xfs",
            "fdisk", "parted", "shutdown", "reboot", "poweroff", "halt",
            "killall", "pkill", "kill", "iptables", "ufw", "route",
            "mkswap", "swapoff", "swapon", "init", "telinit",
            "drop", "truncate", "wipefs", "blkdiscard"
    );

    /**
     * Command names that are treated as safe (read-only / benign) when invoked
     * without dangerous flags or arguments. These are auto-allowed to reduce
     * confirmation fatigue.
     */
    private static final Set<String> SAFE_COMMANDS = Set.of(
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

    /**
     * Shells that execute code (used by the xargs guard: piping into a shell
     * via xargs is treated as dangerous).
     */
    private static final Set<String> SHELL_EXECUTORS = Set.of(
            "bash", "sh", "dash", "zsh", "ksh", "fish"
    );

    private ShellCommandAnalyzerLegacy() {
    }

    /**
     * Analyze a shell command and return the most restrictive danger level.
     *
     * <p>Classification rules (most restrictive wins):</p>
     * <ol>
     *   <li>If the command is a fork bomb or performs remote code execution
     *       (e.g. {@code curl ... | bash}) → {@link DangerLevel#DANGEROUS}.</li>
     *   <li>If any sub-command is dangerous (destructive command, {@code sudo}
     *       escalation, dangerous git operation, system redirection, database
     *       destruction) → {@link DangerLevel#DANGEROUS}.</li>
     *   <li>If every sub-command is a recognized safe command with benign
     *       arguments → {@link DangerLevel#SAFE}.</li>
     *   <li>Otherwise → {@link DangerLevel#ASK_ONCE}.</li>
     * </ol>
     *
     * @param command the full shell command string
     * @return the computed danger level
     */
    public static DangerLevel analyze(String command) {
        if (command == null || command.isBlank()) {
            return DangerLevel.ASK_ONCE;
        }

        // Whole-command dangerous patterns that span sub-command boundaries.
        if (isForkBomb(command)) {
            return DangerLevel.DANGEROUS;
        }

        List<List<String>> subCommands = splitSubCommands(command);
        if (subCommands.isEmpty()) {
            return DangerLevel.ASK_ONCE;
        }

        // Remote code execution: curl|bash, wget|sh (pipe splits sub-commands).
        if (hasRemoteCodeExecutionAcrossSubCommands(subCommands)) {
            return DangerLevel.DANGEROUS;
        }

        boolean allSafe = true;
        for (List<String> tokens : subCommands) {
            DangerLevel level = classifySubCommand(tokens);
            if (level == DangerLevel.DANGEROUS) {
                return DangerLevel.DANGEROUS;
            }
            if (level != DangerLevel.SAFE) {
                allSafe = false;
            }
        }
        return allSafe ? DangerLevel.SAFE : DangerLevel.ASK_ONCE;
    }

    /**
     * Split a command into sub-commands at shell operators, preserving token
     * boundaries. Each returned list is the ordered tokens of one sub-command.
     */
    private static List<List<String>> splitSubCommands(String command) {
        List<List<String>> result = new ArrayList<>();
        List<String> current = new ArrayList<>();
        boolean inSingle = false;
        boolean inDouble = false;

        StringBuilder token = new StringBuilder();
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);

            if (inSingle) {
                if (c == '\'') {
                    inSingle = false;
                } else {
                    token.append(c);
                }
                continue;
            }
            if (inDouble) {
                if (c == '"') {
                    inDouble = false;
                } else {
                    token.append(c);
                }
                continue;
            }

            if (c == '\'') {
                inSingle = true;
                continue;
            }
            if (c == '"') {
                inDouble = true;
                continue;
            }

            // Check for two-char operators first
            if (i + 1 < command.length()) {
                String two = command.substring(i, i + 2);
                if (two.equals("&&") || two.equals("||")) {
                    flushToken(current, token);
                    result.add(current);
                    current = new ArrayList<>();
                    i++;
                    continue;
                }
            }

            if (c == '|' || c == ';' || c == '&' || c == '\n') {
                flushToken(current, token);
                result.add(current);
                current = new ArrayList<>();
                continue;
            }

            if (Character.isWhitespace(c)) {
                flushToken(current, token);
            } else {
                token.append(c);
            }
        }
        flushToken(current, token);
        result.add(current);

        // Drop empty sub-commands
        result.removeIf(List::isEmpty);
        return result;
    }

    private static void flushToken(List<String> tokens, StringBuilder token) {
        if (token.length() > 0) {
            tokens.add(token.toString());
            token.setLength(0);
        }
    }

    /**
     * Classify a single sub-command's tokens.
     */
    private static DangerLevel classifySubCommand(List<String> tokens) {
        if (tokens.isEmpty()) {
            return DangerLevel.SAFE;
        }

        // Detect sudo escalation anywhere in the sub-command.
        if (containsToken(tokens, "sudo")) {
            return DangerLevel.DANGEROUS;
        }

        String command = commandName(tokens);

        // Dangerous command names.
        if (DANGEROUS_COMMANDS.contains(command)) {
            return DangerLevel.DANGEROUS;
        }

        // Dangerous git operations.
        if ("git".equals(command) && isDangerousGit(tokens)) {
            return DangerLevel.DANGEROUS;
        }

        // Dangerous package-manager operations.
        if (isDangerousPackageOp(command, tokens)) {
            return DangerLevel.DANGEROUS;
        }

        // Dangerous flag combinations on otherwise-common commands.
        if (hasDangerousFlags(command, tokens)) {
            return DangerLevel.DANGEROUS;
        }

        // Redirection to system paths (e.g. > /etc/, > /boot/, > /dev/).
        if (redirectsToSystemPath(tokens)) {
            return DangerLevel.DANGEROUS;
        }

        // Database destruction.
        if (isDatabaseDestruction(tokens)) {
            return DangerLevel.DANGEROUS;
        }

        // Safe command names with benign arguments.
        if (SAFE_COMMANDS.contains(command)) {
            return DangerLevel.SAFE;
        }

        return DangerLevel.ASK_ONCE;
    }

    /** Returns the command name, skipping leading env assignments and flags. */
    private static String commandName(List<String> tokens) {
        for (String t : tokens) {
            if (t.contains("=") && !t.startsWith("-")) {
                continue; // env assignment like FOO=bar
            }
            if (t.startsWith("-")) {
                continue; // flag
            }
            return t;
        }
        return tokens.get(0);
    }

    private static boolean containsToken(List<String> tokens, String value) {
        for (String t : tokens) {
            if (t.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDangerousGit(List<String> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if (t.equals("push") && hasFlagAfter(tokens, i, "--force", "-f")) {
                return true;
            }
            if (t.equals("reset") && hasFlagAfter(tokens, i, "--hard")) {
                return true;
            }
            if (t.equals("rebase") && hasFlagAfter(tokens, i, "--onto")) {
                return true;
            }
            if (t.equals("clean") && hasFlagAfter(tokens, i, "-f", "--force", "-fd", "-df", "-x")) {
                return true;
            }
            if (t.equals("checkout") && hasFlagAfter(tokens, i, "--")) {
                return true; // discard working-tree changes
            }
            if (t.equals("branch") && hasFlagAfter(tokens, i, "-D", "-d")) {
                // deleting a branch is destructive
                return true;
            }
            if (t.equals("rm")) {
                return true;
            }
            if (t.equals("filter-branch")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasFlagAfter(List<String> tokens, int index, String... flags) {
        for (int j = index + 1; j < tokens.size(); j++) {
            for (String f : flags) {
                if (tokens.get(j).equals(f)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isDangerousPackageOp(String command, List<String> tokens) {
        if ("apt".equals(command) || "apt-get".equals(command)) {
            for (String t : tokens) {
                if (t.equals("remove") || t.equals("purge") || t.equals("autoremove")) {
                    return true;
                }
            }
        }
        if ("dpkg".equals(command)) {
            for (String t : tokens) {
                if (t.equals("-r") || t.equals("--remove") || t.equals("-P") || t.equals("--purge")) {
                    return true;
                }
            }
        }
        if ("rpm".equals(command)) {
            for (String t : tokens) {
                if (t.equals("-e") || t.equals("--erase")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasDangerousFlags(String command, List<String> tokens) {
        // chmod / chown / chgrp with dangerous targets (recursive, world-writable,
        // or targeting a system path).
        if (command.equals("chmod") || command.equals("chown") || command.equals("chgrp")) {
            for (String t : tokens) {
                if (t.equals("777") || t.equals("-R") || t.equals("--recursive")
                        || isSystemPath(t)) {
                    return true;
                }
            }
        }
        // rm with -rf / -r on root.
        if (command.equals("rm")) {
            for (String t : tokens) {
                if (t.equals("-rf") || t.equals("-fr") || t.equals("-r") || t.equals("-f")) {
                    return true;
                }
            }
        }
        // sed in-place edits modify files.
        if (command.equals("sed")) {
            for (String t : tokens) {
                if (t.equals("-i") || t.equals("--in-place")) {
                    return true;
                }
            }
        }
        // tee writing to a system path (e.g. tee /etc/passwd).
        if (command.equals("tee")) {
            for (String t : tokens) {
                if (isSystemPath(t)) {
                    return true;
                }
            }
        }
        // xargs executing a dangerous command (e.g. xargs rm -rf, xargs sudo).
        if (command.equals("xargs")) {
            for (int i = 1; i < tokens.size(); i++) {
                String t = tokens.get(i);
                if (DANGEROUS_COMMANDS.contains(t) || t.equals("sudo")
                        || SHELL_EXECUTORS.contains(t)) {
                    return true;
                }
            }
        }
        // awk/gawk/mawk with system() code execution.
        if (command.equals("awk") || command.equals("gawk") || command.equals("mawk")) {
            for (String t : tokens) {
                if (t.contains("system(")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if the token is an absolute path under a system
     * directory (e.g. {@code /etc}, {@code /usr}, {@code /bin}, {@code /}).
     */
    private static boolean isSystemPath(String token) {
        if (token == null) {
            return false;
        }
        if (token.equals("/")) {
            return true;
        }
        String[] systemDirs = {
            "/etc", "/boot", "/dev", "/proc", "/sys", "/usr", "/bin",
            "/sbin", "/var", "/lib", "/opt", "/root"
        };
        for (String dir : systemDirs) {
            if (token.equals(dir) || token.startsWith(dir + "/")) {
                return true;
            }
        }
        return false;
    }

    private static boolean redirectsToSystemPath(List<String> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if (t.equals(">") || t.equals(">>") || t.equals("2>") || t.equals("1>")) {
                if (i + 1 < tokens.size()) {
                    String target = tokens.get(i + 1);
                    if (target.startsWith("/etc/") || target.startsWith("/boot/")
                            || target.startsWith("/dev/") || target.startsWith("/proc/")
                            || target.startsWith("/sys/") || target.startsWith("/usr/")
                            || target.startsWith("/bin/") || target.startsWith("/sbin/")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Detect remote code execution across sub-commands: a {@code curl}/{@code wget}
     * sub-command piped into {@code bash}/{@code sh}.
     */
    private static boolean hasRemoteCodeExecutionAcrossSubCommands(List<List<String>> subCommands) {
        for (int i = 0; i + 1 < subCommands.size(); i++) {
            String curCmd = commandName(subCommands.get(i));
            String nextCmd = commandName(subCommands.get(i + 1));
            if ((curCmd.equals("curl") || curCmd.equals("wget"))
                    && (nextCmd.equals("bash") || nextCmd.equals("sh"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isForkBomb(String command) {
        return command.contains(":(){") || command.contains("|&");
    }

    private static boolean isDatabaseDestruction(List<String> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if (t.equals("drop") || t.equals("truncate")) {
                for (int j = i + 1; j < tokens.size(); j++) {
                    if (tokens.get(j).equals("database") || tokens.get(j).equals("table")
                            || tokens.get(j).equals("schema")) {
                        return true;
                    }
                }
            }
            if (t.equalsIgnoreCase("DELETE") && i + 1 < tokens.size()
                    && tokens.get(i + 1).equalsIgnoreCase("FROM")) {
                return true;
            }
        }
        return false;
    }
}
