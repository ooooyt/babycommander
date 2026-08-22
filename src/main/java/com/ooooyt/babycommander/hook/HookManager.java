package com.ooooyt.babycommander.hook;

import com.ooooyt.babycommander.config.AgentConfig;
import com.ooooyt.babycommander.config.YamlConfigLoader;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

@ApplicationScoped
public class HookManager {

    private final YamlConfigLoader configLoader;
    private final SessionMemory sessionMemory;
    private volatile ConfirmationHandler handler;
    private volatile String currentProjectRoot;
    private final ThreadLocal<String> currentSessionId = new ThreadLocal<>();

    @Inject
    public HookManager(YamlConfigLoader configLoader, SessionMemory sessionMemory) {
        this.configLoader = configLoader;
        this.sessionMemory = sessionMemory;
    }

    public void setConfirmationHandler(ConfirmationHandler handler) {
        this.handler = handler;
    }

    /**
     * Set the current project folder root. Propagated from
     * {@code ToolRegistry.updateToolPaths(...)} whenever the active
     * project folder changes. Used by the in-scope gate for path-scoped
     * FileSystemTool trust.
     */
    public void setProjectRoot(String projectRoot) {
        this.currentProjectRoot = projectRoot;
    }

    public String getProjectRoot() {
        return currentProjectRoot;
    }

    public void enterSession(String sessionId) {
        currentSessionId.set(sessionId);
        // Load persisted answers from database into memory
        sessionMemory.loadSession(sessionId);
    }

    public void exitSession() {
        currentSessionId.remove();
    }

    private String currentSession() {
        String sid = currentSessionId.get();
        return sid != null ? sid : "unknown";
    }

    /**
     * Called by tools before executing. Throws ToolDeniedException if denied.
     */
    public void beforeToolCall(String toolName, String methodName, Object[] args) {
        if (!isEnabled()) return;

        DangerLevel level = resolveLevel(toolName, methodName, args);
        if (level == DangerLevel.SAFE) return;

        // In-scope auto-trust: operations confined to the current project
        // folder are safe unless external resources are impacted (dangerous).
        if (level == DangerLevel.ASK_ONCE && trustProjectEnabled()
                && isInScopeOperation(toolName, methodName, args)) {
            return;
        }

        String sessionId = currentSession();

        // Method-level trust: skip if this tool+method was previously ALLOW_ALWAYS'd
        if (level == DangerLevel.ASK_ONCE && sessionMemory.isMethodAllowed(sessionId, toolName, methodName)) return;

        // Path-scoped trust (FileSystemTool, in-scope paths): tool name + first param only
        if (level == DangerLevel.ASK_ONCE && isPathGrantAllowed(sessionId, toolName, args)) return;

        // Path-level trust: skip if all extracted paths are under trusted prefixes
        if (level == DangerLevel.ASK_ONCE) {
            List<String> paths = PathExtractor.extract(toolName, args, workspaceRoot());
            if (!paths.isEmpty() && paths.stream().allMatch(p -> sessionMemory.isPathTrusted(sessionId, p))) return;
        }

        // Existing exact-match check (backward compat)
        if (level == DangerLevel.ASK_ONCE && sessionMemory.isAllowedAlways(sessionId, toolName, args)) return;

        // Denied check: if user previously denied this exact call, auto-deny
        if (level == DangerLevel.ASK_ONCE && sessionMemory.isDenied(sessionId, toolName, args)) {
            throw new ToolDeniedException(toolName);
        }

        ConfirmationHandler h = this.handler;
        if (h == null) return;

        ToolCallInfo info = new ToolCallInfo(toolName, methodName, args, level, sessionId);
        ConfirmationResult result = h.requestConfirmation(info);

        switch (result) {
            case ALLOW -> {
                // For ASK_ONCE: don't persist, user must confirm each time
                // For DANGEROUS: no persistence, user must type every time
            }
            case DENY -> {
                // Persist 'n' answer for ASK_ONCE operations only
                if (level == DangerLevel.ASK_ONCE) {
                    sessionMemory.markDenied(sessionId, toolName, args);
                }
                throw new ToolDeniedException(toolName);
            }
            case ALLOW_ALWAYS -> {
                if (!storePathGrantIfApplicable(sessionId, toolName, methodName, args)) {
                    sessionMemory.markMethodAllowed(sessionId, toolName, methodName);
                    List<String> extractedPaths = PathExtractor.extract(toolName, args, workspaceRoot());
                    if (!extractedPaths.isEmpty()) {
                        List<String> dirPrefixes = new java.util.ArrayList<>();
                        for (String p : extractedPaths) {
                            String dir = PathExtractor.toParentDirectory(p);
                            if (dir != null && !dir.isEmpty()) {
                                dirPrefixes.add(dir);
                            }
                        }
                        if (!dirPrefixes.isEmpty()) {
                            sessionMemory.addTrustedPaths(sessionId, dirPrefixes);
                        }
                    }
                    sessionMemory.markAllowedAlways(sessionId, toolName, methodName, args);
                }
            }
        }
    }

    /**
     * Called for direct tool invocations (e.g. Orchestrator). Wraps the call in hook logic.
     */
    @SuppressWarnings("unchecked")
    public <T> T onToolCall(String toolName, String methodName, Object[] args,
                             String sessionId, Callable<T> realCall) throws Exception {
        if (!isEnabled()) return realCall.call();

        DangerLevel level = resolveLevel(toolName, methodName, args);
        if (level == DangerLevel.SAFE) return realCall.call();

        // In-scope auto-trust: operations confined to the current project
        // folder are safe unless external resources are impacted (dangerous).
        if (level == DangerLevel.ASK_ONCE && trustProjectEnabled()
                && isInScopeOperation(toolName, methodName, args)) {
            return realCall.call();
        }

        // Method-level trust
        if (level == DangerLevel.ASK_ONCE && sessionMemory.isMethodAllowed(sessionId, toolName, methodName)) return realCall.call();

        // Path-scoped trust (FileSystemTool, in-scope paths): tool name + first param only
        if (level == DangerLevel.ASK_ONCE && isPathGrantAllowed(sessionId, toolName, args)) return realCall.call();

        // Path-level trust
        if (level == DangerLevel.ASK_ONCE) {
            List<String> paths = PathExtractor.extract(toolName, args, workspaceRoot());
            if (!paths.isEmpty() && paths.stream().allMatch(p -> sessionMemory.isPathTrusted(sessionId, p))) return realCall.call();
        }

        // Existing exact-match check
        if (level == DangerLevel.ASK_ONCE && sessionMemory.isAllowedAlways(sessionId, toolName, args)) return realCall.call();

        // Denied check
        if (level == DangerLevel.ASK_ONCE && sessionMemory.isDenied(sessionId, toolName, args)) {
            throw new ToolDeniedException(toolName);
        }

        ConfirmationHandler h = this.handler;
        if (h == null) return realCall.call();

        ToolCallInfo info = new ToolCallInfo(toolName, methodName, args, level, sessionId);
        ConfirmationResult result = h.requestConfirmation(info);

        return switch (result) {
            case ALLOW -> {
                // For ASK_ONCE: don't persist, user must confirm each time
                yield realCall.call();
            }
            case DENY -> {
                // Persist 'n' answer for ASK_ONCE operations only
                if (level == DangerLevel.ASK_ONCE) {
                    sessionMemory.markDenied(sessionId, toolName, args);
                }
                throw new ToolDeniedException(toolName);
            }
            case ALLOW_ALWAYS -> {
                if (!storePathGrantIfApplicable(sessionId, toolName, methodName, args)) {
                    sessionMemory.markMethodAllowed(sessionId, toolName, methodName);
                    List<String> extractedPaths = PathExtractor.extract(toolName, args, workspaceRoot());
                    if (!extractedPaths.isEmpty()) {
                        List<String> dirPrefixes = new java.util.ArrayList<>();
                        for (String p : extractedPaths) {
                            String dir = PathExtractor.toParentDirectory(p);
                            if (dir != null && !dir.isEmpty()) {
                                dirPrefixes.add(dir);
                            }
                        }
                        if (!dirPrefixes.isEmpty()) {
                            sessionMemory.addTrustedPaths(sessionId, dirPrefixes);
                        }
                    }
                    sessionMemory.markAllowedAlways(sessionId, toolName, methodName, args);
                }
                yield realCall.call();
            }
        };
    }

    /**
     * Path-scoped trust check for FileSystemTool: if the call's first argument
     * (the file path) is in scope — under the current project folder or under a
     * session-trusted path — and a (toolName, path) grant exists, the call is
     * allowed regardless of the remaining arguments.
     *
     * @return {@code true} if a path grant covers this call
     */
    private boolean isPathGrantAllowed(String sessionId, String toolName, Object[] args) {
        String fp = PathExtractor.firstPath(toolName, args, workspaceRoot(), currentProjectRoot);
        if (fp == null || args == null || !(args[0] instanceof String raw)) return false;
        if (!inScope(sessionId, raw)) return false;
        return sessionMemory.isPathAllowedAlways(sessionId, toolName, fp);
    }

    /**
     * On ALLOW_ALWAYS, store a path-scoped grant instead of the legacy
     * method/dir/signature trust when the call is an in-scope FileSystemTool
     * invocation. This confines the simplified (toolName, path) matching to
     * paths under the current project folder or already-trusted directories.
     *
     * @return {@code true} if a path grant was stored (legacy stores skipped)
     */
    private boolean storePathGrantIfApplicable(String sessionId, String toolName,
                                               String methodName, Object[] args) {
        String fp = PathExtractor.firstPath(toolName, args, workspaceRoot(), currentProjectRoot);
        if (fp == null || args == null || !(args[0] instanceof String raw)) return false;
        if (!inScope(sessionId, raw)) return false;
        sessionMemory.markPathAllowedAlways(sessionId, toolName, methodName, fp);
        return true;
    }

    /**
     * In-scope gate for path-scoped FileSystemTool trust. A path is in scope if
     * it is located under the current project folder, or under any
     * session-trusted path prefix.
     */
    private boolean inScope(String sessionId, String rawPath) {
        String root = currentProjectRoot;
        if (root != null && !root.isBlank()) {
            java.nio.file.Path abs = java.nio.file.Path.of(rawPath).isAbsolute()
                    ? java.nio.file.Path.of(rawPath).normalize()
                    : java.nio.file.Path.of(root).resolve(rawPath).normalize();
            if (abs.startsWith(java.nio.file.Path.of(root).normalize())) return true;
        }
        String wsKey = PathExtractor.normalize(rawPath, workspaceRoot());
        return sessionMemory.isPathTrusted(sessionId, wsKey);
    }

    private static String extractFirstStringArg(Object[] args) {
        if (args != null && args.length > 0 && args[0] instanceof String s) {
            return s;
        }
        return null;
    }

    /**
     * Returns {@code true} when the configured trust mode permits automatic
     * trust of operations confined to the current project folder. Only
     * {@code STRICT} disables in-scope auto-trust; {@code AUTO} and
     * {@code ALWAYS} both enable it (they differ only in documentation of
     * intent). This never affects {@code dangerous} operations, which are
     * always gated.
     */
    private boolean trustProjectEnabled() {
        AgentConfig config = configLoader.getConfig();
        if (config == null || config.hooks == null) return true;
        AgentConfig.TrustProjectMode mode =
                AgentConfig.TrustProjectMode.fromString(config.hooks.trustProject);
        return mode != AgentConfig.TrustProjectMode.STRICT;
    }

    /**
     * Decides whether a tool call operates entirely within the current project
     * folder, with no impact on external resources.
     * <ul>
     *   <li><b>FileSystemTool</b>: the target path ({@code args[0]}) must resolve
     *   under the current project root.</li>
     *   <li><b>ShellTool</b>: every path extracted from the command must be
     *   in-scope. If the command matches a {@code dangerous} pattern it is
     *   never considered in-scope (dangerous always wins).</li>
     *   <li>All other tools: {@code false} (conservative).</li>
     * </ul>
     */
    private boolean isInScopeOperation(String toolName, String methodName, Object[] args) {
        String root = currentProjectRoot;
        String ws = workspaceRoot();

        if ("FileSystemTool".equals(toolName)) {
            String fp = PathExtractor.firstPath(toolName, args, ws, root);
            if (fp == null) return false;
            return isPathUnderRoot(fp, root);
        }

        if ("ShellTool".equals(toolName)) {
            if (matchesDangerousPattern(toolName, args)) return false;
            List<String> paths = PathExtractor.extract(toolName, args, ws);
            if (paths.isEmpty()) return false;
            for (String p : paths) {
                if (!isPathUnderRoot(p, root)) return false;
            }
            return true;
        }

        return false;
    }

    /**
     * Returns {@code true} if {@code normalizedPath} (already normalized against
     * the workspace root) is located under {@code root}.
     */
    private static boolean isPathUnderRoot(String normalizedPath, String root) {
        if (root == null || root.isBlank()) return false;
        if (normalizedPath == null || normalizedPath.isBlank()) return false;
        java.nio.file.Path abs = java.nio.file.Path.of(root).normalize();
        java.nio.file.Path target;
        try {
            target = java.nio.file.Path.of(normalizedPath).normalize();
        } catch (Exception e) {
            return false;
        }
        // If the normalized path is relative (resolved against workspace root),
        // resolve it against the project root for a reliable prefix check.
        if (!target.isAbsolute()) {
            target = abs.resolve(target).normalize();
        }
        return target.startsWith(abs);
    }

    /**
     * Returns {@code true} if the shell command matches any configured
     * {@code dangerous} pattern for the tool. Dangerous patterns always win,
     * so such commands are never auto-trusted.
     */
    private boolean matchesDangerousPattern(String toolName, Object[] args) {
        AgentConfig config = configLoader.getConfig();
        if (config == null || config.hooks == null || config.hooks.patterns == null) return false;
        String command = extractFirstStringArg(args);
        if (command == null) return false;
        for (AgentConfig.PatternGroup pg : config.hooks.patterns.values()) {
            if (pg.tool != null && pg.tool.equals(toolName)
                    && "dangerous".equalsIgnoreCase(pg.level)
                    && pg.compiledPatterns != null) {
                for (Pattern p : pg.compiledPatterns) {
                    if (p.matcher(command).find()) return true;
                }
            }
        }
        return false;
    }

    private boolean isEnabled() {
        AgentConfig config = configLoader.getConfig();
        return config != null && config.hooks != null && config.hooks.enabled;
    }

    private String workspaceRoot() {
        AgentConfig config = configLoader.getConfig();
        if (config == null) return null;
        return config.workspaceRoot;
    }

    /**
     * Resolve the danger level for a tool invocation.
     *
     * <p>Uses <strong>most-restrictive-wins</strong> semantics:
     * <ol>
     *   <li>Determine the tool-level baseline from hook rules (or SAFE if no rule matches).</li>
     *   <li>Check all command-content patterns — any matching pattern contributes its level.</li>
     *   <li>Return the maximum (most restrictive) level across baseline and all matching patterns.</li>
     * </ol>
     *
     * This means a {@code dangerous} pattern match <strong>escalates</strong> to DANGEROUS even if
     * the tool baseline is lower (e.g. {@code ask_once}), while a {@code safe} or {@code ask_once}
     * pattern can still <strong>downgrade</strong> from a more restrictive baseline.
     */
    private DangerLevel resolveLevel(String toolName, String methodName, Object[] args) {
        AgentConfig config = configLoader.getConfig();
        if (config == null || config.hooks == null || config.hooks.rules == null) {
            return DangerLevel.SAFE;
        }

        // Read-only FileSystemTool operations are inherently safe and should never
        // trigger a confirmation prompt. Prompting on these breaks the OpenAI
        // tool-call/tool-message pairing (an assistant toolcall must be answered by
        // a tool message, not a user-facing confirmation), causing an invalid
        // request error. See doc/temp.txt.
        if (isSafeFileSystemMethod(toolName, methodName)) {
            return DangerLevel.SAFE;
        }

        // Step 1: Determine tool-level baseline
        DangerLevel baseline = DangerLevel.SAFE;
        for (AgentConfig.HookRule rule : config.hooks.rules) {
            if (rule.tool != null && toolName.equals(rule.tool)) {
                if (rule.level == null) {
                    baseline = DangerLevel.SAFE;
                } else {
                    try {
                        baseline = DangerLevel.valueOf(rule.level.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        Log.warnf("Unknown danger level '%s' for tool '%s', defaulting to SAFE", rule.level, toolName);
                        baseline = DangerLevel.SAFE;
                    }
                }
                break;
            }
        }

        if (toolName.startsWith("McpTool")) {
            baseline = DangerLevel.DANGEROUS;
        }

        // Step 2: Check command-content patterns. If any pattern matches, its
        // level OVERRIDES the tool baseline (it may escalate or downgrade).
        // Among multiple matching patterns, the most restrictive wins.
        DangerLevel matched = null;
        String command = extractFirstStringArg(args);
        if (command != null && config.hooks.patterns != null) {
            for (AgentConfig.PatternGroup pg : config.hooks.patterns.values()) {
                if (pg.tool != null && pg.tool.equals(toolName) && pg.compiledPatterns != null) {
                    for (Pattern p : pg.compiledPatterns) {
                        if (p.matcher(command).find()) {
                            try {
                                DangerLevel pl = DangerLevel.valueOf(pg.level.toUpperCase());
                                if (matched == null || pl.ordinal() < matched.ordinal()) {
                                    matched = pl;
                                }
                            } catch (IllegalArgumentException e) {
                                Log.warnf("Unknown pattern level '%s', skipping pattern group", pg.level);
                                break;
                            }
                        }
                    }
                }
            }
        }

        return matched != null ? matched : baseline;
    }

    /**
     * Returns {@code true} for read-only FileSystemTool operations that should
     * never trigger a confirmation prompt. These operations only inspect the
     * filesystem (read / list / search / getters) and cannot modify anything.
     *
     * @param toolName   the tool class name (e.g. "FileSystemTool")
     * @param methodName the method/action name passed to {@code executeWithStatus}
     * @return {@code true} if the operation is a safe, read-only filesystem operation
     */
    private static boolean isSafeFileSystemMethod(String toolName, String methodName) {
        if (!"FileSystemTool".equals(toolName) || methodName == null) {
            return false;
        }
        return switch (methodName) {
            case "read_file", "read_file_range", "list_directory",
                    "search_files", "extract_skeleton", "extract_package_skeleton",
                    "read_function", "read_function_by_param_count",
                    "pathExists", "isDirectory" -> true;
            default -> false;
        };
    }
}
