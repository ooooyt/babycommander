package com.ooooyt.babycommander.hook;

import com.ooooyt.babycommander.config.AgentConfig;
import com.ooooyt.babycommander.config.YamlConfigLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;

class HookManagerTest {

    private AgentConfig config;
    private SessionMemory sessionMemory;
    private HookManager hookManager;
    private TestConfirmationHandler testHandler;

    static class TestConfirmationHandler implements ConfirmationHandler {
        ConfirmationResult nextResult = ConfirmationResult.ALLOW;
        ToolCallInfo lastInfo;
        int callCount = 0;

        @Override
        public ConfirmationResult requestConfirmation(ToolCallInfo info) {
            lastInfo = info;
            callCount++;
            return nextResult;
        }
    }

    private YamlConfigLoader createLoader(AgentConfig cfg) {
        return new YamlConfigLoader("agents.yaml") {
            @Override
            public AgentConfig getConfig() { return cfg; }
        };
    }

    @BeforeEach
    void setup() {
        config = new AgentConfig();
        config.hooks = new AgentConfig.HookConfig();
        config.hooks.enabled = true;
        // These legacy tests exercise the prompt + path-grant mechanism for
        // in-scope operations, so pin the pre-auto behavior (strict).
        config.hooks.trustProject = "strict";

        AgentConfig.HookRule shellRule = new AgentConfig.HookRule();
        shellRule.tool = "ShellTool";
        shellRule.level = "dangerous";
        config.hooks.rules = List.of(shellRule);

        sessionMemory = new SessionMemory();
        testHandler = new TestConfirmationHandler();
        hookManager = new HookManager(createLoader(config), sessionMemory);
        hookManager.setConfirmationHandler(testHandler);
    }

    // ========== Helper Methods ==========

    private void setupRule(String tool, String level) {
        config.hooks.rules = new java.util.ArrayList<>();
        AgentConfig.HookRule rule = new AgentConfig.HookRule();
        rule.tool = tool;
        rule.level = level;
        config.hooks.rules = List.of(rule);
    }

    private void setupPattern(String groupName, String tool, String level, List<String> matches) {
        if (config.hooks.patterns == null) {
            config.hooks.patterns = new java.util.HashMap<>();
        }
        AgentConfig.PatternGroup pg = new AgentConfig.PatternGroup();
        pg.tool = tool;
        pg.level = level;
        pg.matches = matches;
        compilePatterns(pg);
        config.hooks.patterns.put(groupName, pg);
    }

    private void compilePatterns(AgentConfig.PatternGroup pg) {
        if (pg.matches == null) return;
        pg.compiledPatterns.clear();
        for (String regex : pg.matches) {
            pg.compiledPatterns.add(java.util.regex.Pattern.compile(regex));
        }
    }

    private Callable<String> okCall() {
        return () -> "ok";
    }

    // ========== Basic Tool Safety Tests ==========

    @Test
    void testSafeToolPassesWithoutConfirmation() throws Exception {
        String result = hookManager.onToolCall("InternetTool", "fetchUrl",
                new Object[]{"http://x.com"}, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(0, testHandler.callCount);
    }

    @Test
    void testDangerousToolCallsHandler() throws Exception {
        String result = hookManager.onToolCall("ShellTool", "execute",
                new Object[]{"rm -rf /"}, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(1, testHandler.callCount);
        assertEquals("ShellTool", testHandler.lastInfo.toolName());
        assertEquals("execute", testHandler.lastInfo.methodName());
        assertEquals(DangerLevel.DANGEROUS, testHandler.lastInfo.level());
    }

    @Test
    void testDenyPreventsExecution() {
        testHandler.nextResult = ConfirmationResult.DENY;
        assertThrows(ToolDeniedException.class, () ->
                hookManager.onToolCall("ShellTool", "execute",
                        new Object[]{"rm -rf /"}, "s1", okCall()));
        assertEquals(1, testHandler.callCount);
    }

    // ========== Ask Once Tests ==========

    @Test
    void testAskOnceFirstTimeConfirms() throws Exception {
        setupRule("FileSystemTool", "ask_once");

        String result = hookManager.onToolCall("FileSystemTool", "readFile",
                new Object[]{"file.txt"}, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(1, testHandler.callCount);
    }

    @Test
    void testAskOnceCallsHandlerEachTimeWithAllow() throws Exception {
        setupRule("FileSystemTool", "ask_once");

        hookManager.onToolCall("FileSystemTool", "readFile",
                new Object[]{"file.txt"}, "s1", okCall());
        assertEquals(1, testHandler.callCount);

        String result = hookManager.onToolCall("FileSystemTool", "readFile",
                new Object[]{"file.txt"}, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(2, testHandler.callCount);
    }

    // ========== ALLOW_ALWAYS Tests ==========

    @Test
    void testAllowAlwaysPersistsToSessionMemory() throws Exception {
        setupRule("FileSystemTool", "ask_once");
        testHandler.nextResult = ConfirmationResult.ALLOW_ALWAYS;

        Object[] args = new Object[]{"file.txt"};
        hookManager.onToolCall("FileSystemTool", "readFile",
                args, "s1", okCall());
        assertEquals(1, testHandler.callCount);
        assertTrue(sessionMemory.isAllowedAlways("s1", "FileSystemTool", args));

        // Second call with same args should skip handler
        String result = hookManager.onToolCall("FileSystemTool", "readFile",
                args, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(1, testHandler.callCount);
    }

    @Test
    void testAllowAlwaysExactMatchGranularity() throws Exception {
        // New semantics: an in-scope FileSystemTool 'a' stores a path-scoped
        // grant (tool name + first param) and NO method trust — a different
        // path on the same method must prompt again.
        setupRule("FileSystemTool", "ask_once");
        config.workspaceRoot = "/ws";
        hookManager.setProjectRoot("/proj");
        testHandler.nextResult = ConfirmationResult.ALLOW_ALWAYS;

        Object[] safeArgs = new Object[]{"/proj/safe-file.txt"};
        hookManager.onToolCall("FileSystemTool", "readFile",
                safeArgs, "s1", okCall());
        assertEquals(1, testHandler.callCount);
        assertFalse(sessionMemory.isMethodAllowed("s1", "FileSystemTool", "readFile"),
                "In-scope FileSystemTool 'a' must not record method trust");
        assertTrue(sessionMemory.isPathAllowedAlways("s1", "FileSystemTool", "/proj/safe-file.txt"));

        // Different path, same method — prompts again (no method trust)
        testHandler.callCount = 0;
        testHandler.nextResult = ConfirmationResult.ALLOW;
        Object[] otherArgs = new Object[]{"/proj/other.txt"};
        String result = hookManager.onToolCall("FileSystemTool", "readFile",
                otherArgs, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(1, testHandler.callCount, "Different path prompts — grant is path-scoped");
        assertFalse(sessionMemory.isPathAllowedAlways("s1", "FileSystemTool", "/proj/other.txt"));
    }

    @Test
    void testAllowAlwaysOutOfScopeKeepsLegacyMethodTrust() throws Exception {
        // Out-of-scope FileSystemTool 'a' falls back to the legacy stores:
        // method trust + dir-prefix trust + exact signature.
        setupRule("FileSystemTool", "ask_once");
        config.workspaceRoot = "/ws";
        hookManager.setProjectRoot("/proj");
        testHandler.nextResult = ConfirmationResult.ALLOW_ALWAYS;

        Object[] safeArgs = new Object[]{"/tmp/safe-file.txt"};
        hookManager.onToolCall("FileSystemTool", "readFile",
                safeArgs, "s1", okCall());
        assertEquals(1, testHandler.callCount);
        assertTrue(sessionMemory.isMethodAllowed("s1", "FileSystemTool", "readFile"));
        assertTrue(sessionMemory.isAllowedAlways("s1", "FileSystemTool", safeArgs));
        assertFalse(sessionMemory.isPathAllowedAlways("s1", "FileSystemTool", "/tmp/safe-file.txt"));

        // Different args, same method — skips due to legacy method trust
        testHandler.callCount = 0;
        Object[] otherArgs = new Object[]{"/etc/passwd"};
        String result = hookManager.onToolCall("FileSystemTool", "readFile",
                otherArgs, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(0, testHandler.callCount,
                "Legacy method trust still applies for out-of-scope grants");
        assertFalse(sessionMemory.isAllowedAlways("s1", "FileSystemTool", otherArgs));
    }

    @Test
    void testBeforeToolCallWithAllowAlways() {
        setupRule("FileSystemTool", "ask_once");
        config.workspaceRoot = "/ws";
        hookManager.setProjectRoot("/proj");
        testHandler.nextResult = ConfirmationResult.ALLOW_ALWAYS;

        Object[] args = new Object[]{"/proj/file.txt"};
        hookManager.enterSession("s1");
        try {
            // First call: handler invoked; in-scope 'a' stores a path grant
            assertDoesNotThrow(() ->
                    hookManager.beforeToolCall("FileSystemTool", "readFile", args));
            assertEquals(1, testHandler.callCount);
            assertTrue(sessionMemory.isPathAllowedAlways("s1", "FileSystemTool", "/proj/file.txt"));
            assertFalse(sessionMemory.isMethodAllowed("s1", "FileSystemTool", "readFile"));

            // Second call on the same path: skipped via path grant
            assertDoesNotThrow(() ->
                    hookManager.beforeToolCall("FileSystemTool", "readFile", args));
            assertEquals(1, testHandler.callCount);

            // Different path: prompts again (grant is path-scoped, not method-wide)
            testHandler.callCount = 0;
            assertDoesNotThrow(() ->
                    hookManager.beforeToolCall("FileSystemTool", "readFile",
                            new Object[]{"/proj/other.txt"}));
            assertEquals(1, testHandler.callCount, "Different path prompts — grant is path-scoped");
        } finally {
            hookManager.exitSession();
        }
    }

    // ========== Hooks Disabled Tests ==========

    @Test
    void testHooksDisabledBypassesEverything() throws Exception {
        config.hooks.enabled = false;
        testHandler.nextResult = ConfirmationResult.DENY;

        String result = hookManager.onToolCall("ShellTool", "execute",
                new Object[]{"rm -rf /"}, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(0, testHandler.callCount);
    }

    // ========== MCP Tool Tests ==========

    @Test
    void testMCPToolDefaultsToDangerous() throws Exception {
        String result = hookManager.onToolCall("McpToolAdapter-123", "invoke",
                new Object[]{}, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(1, testHandler.callCount);
        assertEquals(DangerLevel.DANGEROUS, testHandler.lastInfo.level());
    }

    // ========== beforeToolCall Tests ==========

    @Test
    void testBeforeToolCallThrowsOnDeny() {
        testHandler.nextResult = ConfirmationResult.DENY;
        hookManager.enterSession("s1");
        try {
            assertThrows(ToolDeniedException.class, () ->
                    hookManager.beforeToolCall("ShellTool", "execute", new Object[]{"rm"}));
        } finally {
            hookManager.exitSession();
        }
    }

    @Test
    void testBeforeToolCallSafe() {
        hookManager.enterSession("s1");
        try {
            assertDoesNotThrow(() ->
                    hookManager.beforeToolCall("InternetTool", "fetchUrl", new Object[]{"http://x.com"}));
        } finally {
            hookManager.exitSession();
        }
        assertEquals(0, testHandler.callCount);
    }

    @Test
    void testNoHandlerReturnsSilentlyInBeforeToolCall() {
        hookManager.setConfirmationHandler(null);
        hookManager.enterSession("s1");
        try {
            hookManager.beforeToolCall("ShellTool", "execute", new Object[]{"rm"});
        } finally {
            hookManager.exitSession();
        }
    }

    @Test
    void testNoHandlerReturnsSilentlyInOnToolCall() throws Exception {
        hookManager.setConfirmationHandler(null);
        String result = hookManager.onToolCall("ShellTool", "execute",
                new Object[]{"rm -rf /"}, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(0, testHandler.callCount);
    }

    // ========== Pattern Matching Tests ==========

    @Test
    void testAskOncePatternDoesNotForcePromptForSafeCommand() throws Exception {
        setupPattern("java", "ShellTool", "ask_once", List.of("^mvn\\s"));

        String result = hookManager.onToolCall("ShellTool", "execute",
                new Object[]{"mvn test"}, "s1", okCall());
        assertEquals("ok", result);
        // 'mvn test' is benign; token analysis classifies it SAFE even though an
        // 'ask_once' pattern matches, so no confirmation is requested.
        assertEquals(0, testHandler.callCount);
    }
    @Test
    void testDangerousStaysDangerousWhenCommandDoesNotMatchPattern() throws Exception {
        setupPattern("java", "ShellTool", "ask_once", List.of("^mvn\\s"));

        String result = hookManager.onToolCall("ShellTool", "execute",
                new Object[]{"rm -rf /"}, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(1, testHandler.callCount);
        assertEquals(DangerLevel.DANGEROUS, testHandler.lastInfo.level());
    }

    @Test
    void testNoPatternsFallsBackToTokenAnalysis() throws Exception {
        config.hooks.patterns = null;

        // 'mvn test' is a benign build command classified SAFE by token analysis,
        // so no confirmation is requested.
        String result = hookManager.onToolCall("ShellTool", "execute",
                new Object[]{"mvn test"}, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(0, testHandler.callCount);
    }

    @Test
    void testNoPatternsDangerousCommandStillPrompts() throws Exception {
        config.hooks.patterns = null;

        // 'rm -rf' is destructive, so token analysis flags it DANGEROUS and
        // confirmation is requested.
        String result = hookManager.onToolCall("ShellTool", "execute",
                new Object[]{"rm -rf /tmp/foo"}, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(1, testHandler.callCount);
        assertEquals(DangerLevel.DANGEROUS, testHandler.lastInfo.level());
    }

    /** Parameterized test for all language-specific pattern matches. */
    @ParameterizedTest
    @CsvSource({
        "python, ^pip\\s, pip install requests",
        "python, ^python\\s, python script.py",
        "node,   ^npm\\s,  npm install",
        "node,   ^npx\\s,  npx create-app",
        "go,     ^go\\s,   go build",
        "rust,   ^cargo\\s, cargo build",
        "ruby,   ^bundle\\s, bundle install",
        "ruby,   ^rake\\s,  rake db:migrate",
        "php,    ^composer\\s, composer install",
        "php,    ^php\\s,   php artisan serve",
        "common, ^cd\\s,    cd /home",
        "common, ^ls\\s,    ls -la",
        "common, ^grep\\s,  grep foo bar.txt"
    })
    void testCommonCommandsAreSafe(String groupName, String pattern, String command) throws Exception {
        setupPattern(groupName, "ShellTool", "ask_once", List.of(pattern));

        String result = hookManager.onToolCall("ShellTool", "execute",
                new Object[]{command}, "s1", okCall());
        assertEquals("ok", result);
        // These are benign commands; token analysis auto-allows them.
        assertEquals(0, testHandler.callCount);
    }

    @Test
    void testChainedSafeCommandIsSafe() throws Exception {
        setupPattern("common", "ShellTool", "ask_once", List.of("^cd\\s.*&&"));

        String result = hookManager.onToolCall("ShellTool", "execute",
                new Object[]{"cd /home && ls"}, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(0, testHandler.callCount);
    }

    // ========== Session / ThreadLocal Tests ==========

    @Test
    void testEnterAndExitSession() {
        hookManager.enterSession("session-1");
        assertDoesNotThrow(() ->
                hookManager.beforeToolCall("InternetTool", "fetchUrl", new Object[]{"http://x.com"}));
        hookManager.exitSession();
        assertEquals(0, testHandler.callCount);
    }

    @Test
    void testMultipleSessionsDoNotInterfere() throws Exception {
        setupRule("FileSystemTool", "ask_once");

        Object[] args = new Object[]{"file.txt"};

        // Session 1: ALLOW_ALWAYS for FileSystemTool with these args
        testHandler.nextResult = ConfirmationResult.ALLOW_ALWAYS;
        hookManager.onToolCall("FileSystemTool", "readFile",
                args, "session-1", okCall());
        assertTrue(sessionMemory.isAllowedAlways("session-1", "FileSystemTool", args));
        assertFalse(sessionMemory.isAllowedAlways("session-2", "FileSystemTool", args));

        // Session 2: should still need confirmation
        testHandler.nextResult = ConfirmationResult.ALLOW;
        testHandler.callCount = 0;
        hookManager.onToolCall("FileSystemTool", "readFile",
                args, "session-2", okCall());
        assertEquals(1, testHandler.callCount);
    }

    @Test
    void testClearSessionMemory() throws Exception {
        setupRule("FileSystemTool", "ask_once");
        testHandler.nextResult = ConfirmationResult.ALLOW_ALWAYS;

        Object[] args = new Object[]{"file.txt"};
        hookManager.onToolCall("FileSystemTool", "readFile",
                args, "s1", okCall());
        assertTrue(sessionMemory.isAllowedAlways("s1", "FileSystemTool", args));

        sessionMemory.clearSession("s1");
        assertFalse(sessionMemory.isAllowedAlways("s1", "FileSystemTool", args));

        // After clearing, handler should be called again
        testHandler.callCount = 0;
        hookManager.onToolCall("FileSystemTool", "readFile",
                args, "s1", okCall());
        assertEquals(1, testHandler.callCount);
    }

    // ========== Edge Case Tests ==========

    @Test
    void testOnToolCallWithNullArgs() throws Exception {
        String result = hookManager.onToolCall("ShellTool", "execute",
                null, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(1, testHandler.callCount);
        assertEquals(DangerLevel.DANGEROUS, testHandler.lastInfo.level());
    }

    @Test
    void testOnToolCallWithEmptyArgs() throws Exception {
        String result = hookManager.onToolCall("ShellTool", "execute",
                new Object[]{}, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(1, testHandler.callCount);
        assertEquals(DangerLevel.DANGEROUS, testHandler.lastInfo.level());
    }

    // ========== Method-Level Trust Tests ==========

    @Test
    void testMethodTrustSkipsPromptAfterAllowAlways() throws Exception {
        setupRule("FileSystemTool", "ask_once");
        testHandler.nextResult = ConfirmationResult.ALLOW_ALWAYS;

        Object[] args1 = new Object[]{"/ws/file1.txt"};
        hookManager.onToolCall("FileSystemTool", "readFile",
                args1, "s1", okCall());
        assertEquals(1, testHandler.callCount);

        // Different args, same method — should skip prompt due to method trust
        testHandler.callCount = 0;
        Object[] args2 = new Object[]{"/ws/file2.txt"};
        String result = hookManager.onToolCall("FileSystemTool", "readFile",
                args2, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(0, testHandler.callCount, "Handler should NOT be called — method is trusted");
    }

    @Test
    void testMethodTrustDoesNotSkipDangerous() throws Exception {
        setupRule("ShellTool", "dangerous");
        testHandler.nextResult = ConfirmationResult.ALLOW;

        Object[] args = new Object[]{"rm -rf /tmp/foo"};
        hookManager.enterSession("s1");
        try {
            sessionMemory.markMethodAllowed("s1", "ShellTool", "execute");

            testHandler.callCount = 0;
            assertDoesNotThrow(() ->
                    hookManager.beforeToolCall("ShellTool", "execute", args));
            assertEquals(1, testHandler.callCount,
                    "Handler should be called — DANGEROUS level bypasses method trust");
        } finally {
            hookManager.exitSession();
        }
    }

    @Test
    void testMethodTrustStoredOnAllowAlways() throws Exception {
        setupRule("FileSystemTool", "ask_once");
        testHandler.nextResult = ConfirmationResult.ALLOW_ALWAYS;

        Object[] args = new Object[]{"/ws/file.txt"};
        hookManager.onToolCall("FileSystemTool", "readFile",
                args, "s1", okCall());

        assertTrue(sessionMemory.isMethodAllowed("s1", "FileSystemTool", "readFile"));
    }

    @Test
    void testMethodTrustWorksInBeforeToolCall() {
        setupRule("FileSystemTool", "ask_once");
        testHandler.nextResult = ConfirmationResult.ALLOW_ALWAYS;

        Object[] args = new Object[]{"/ws/file.txt"};
        hookManager.enterSession("s1");
        try {
            hookManager.beforeToolCall("FileSystemTool", "readFile", args);
            assertEquals(1, testHandler.callCount);

            // Second call with different args — method trust skips handler
            testHandler.callCount = 0;
            Object[] args2 = new Object[]{"/ws/other.txt"};
            assertDoesNotThrow(() ->
                    hookManager.beforeToolCall("FileSystemTool", "readFile", args2));
            assertEquals(0, testHandler.callCount,
                    "beforeToolCall should skip handler — method is trusted");
        } finally {
            hookManager.exitSession();
        }
    }

    // ========== Path-Level Trust Tests ==========

    @Test
    void testPathTrustSkipsPromptForDescendantPaths() throws Exception {
        setupRule("FileSystemTool", "ask_once");
        config.workspaceRoot = "/ws";

        // Simulate previously stored path trust from an ALLOW_ALWAYS (trust a directory prefix)
        sessionMemory.addTrustedPaths("s1", List.of("src/main"));

        // Now read /ws/src/main/Bar.java — descendant of src/main, should skip via path trust
        testHandler.callCount = 0;
        Object[] args2 = new Object[]{"/ws/src/main/Bar.java"};
        String result = hookManager.onToolCall("FileSystemTool", "readFile",
                args2, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(0, testHandler.callCount,
                "Handler should NOT be called — path is trusted (descendant of confirmed parent)");
    }

    @Test
    void testPathTrustDoesNotSkipOutsideTrustedBranch() throws Exception {
        setupRule("FileSystemTool", "ask_once");
        config.workspaceRoot = "/ws";

        sessionMemory.addTrustedPaths("s1", List.of("src/main/Foo.java"));

        // Now read /ws/src/test/Baz.java — different branch, should prompt
        testHandler.nextResult = ConfirmationResult.ALLOW;
        testHandler.callCount = 0;
        Object[] args2 = new Object[]{"/ws/src/test/Baz.java"};
        String result = hookManager.onToolCall("FileSystemTool", "readFile",
                args2, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(1, testHandler.callCount,
                "Handler should be called — path is outside trusted branch");
    }

    @Test
    void testPathTrustWithShellToolCommand() throws Exception {
        setupRule("ShellTool", "ask_once");
        config.workspaceRoot = "/ws";

        sessionMemory.addTrustedPaths("s1", List.of("src"));

        // "touch /ws/src/main/Foo.java" — descendant of "src", should skip via path trust
        testHandler.callCount = 0;
        Object[] args = new Object[]{"touch /ws/src/main/Foo.java"};
        String result = hookManager.onToolCall("ShellTool", "execute",
                args, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(0, testHandler.callCount,
                "Handler should NOT be called — path is descendant of trusted prefix");
    }

    @Test
    void testPathTrustDoesNotSkipDangerousCommand() throws Exception {
        setupRule("ShellTool", "dangerous");
        setupPattern("dangerous_commands", "ShellTool", "dangerous", List.of("rm\\s+-rf"));
        config.workspaceRoot = "/ws";

        // Even with trusted paths and method trust, DANGEROUS always prompts
        sessionMemory.addTrustedPaths("s1", List.of("src"));
        sessionMemory.markMethodAllowed("s1", "ShellTool", "execute");

        // "rm -rf /ws/src" resolves to DANGEROUS by pattern — ALWAYS PROMPT
        testHandler.nextResult = ConfirmationResult.ALLOW;
        testHandler.callCount = 0;
        Object[] args2 = new Object[]{"rm -rf /ws/src"};
        String result = hookManager.onToolCall("ShellTool", "execute",
                args2, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(1, testHandler.callCount,
                "Handler should be called — DANGEROUS level always prompts regardless of trust");
    }

    @Test
    void testAllowAlwaysStoresBothMethodAndPaths() throws Exception {
        setupRule("ShellTool", "ask_once");
        config.workspaceRoot = "/ws";
        testHandler.nextResult = ConfirmationResult.ALLOW_ALWAYS;

        Object[] args = new Object[]{"touch /ws/src/main/Foo.java"};
        hookManager.onToolCall("ShellTool", "execute",
                args, "s1", okCall());

        assertTrue(sessionMemory.isMethodAllowed("s1", "ShellTool", "execute"));
        assertTrue(sessionMemory.isPathTrusted("s1", "src/main/Foo.java"));
        // Backward compat: exact signature also stored
        assertTrue(sessionMemory.isAllowedAlways("s1", "ShellTool", args));
    }

    @Test
    void testPathTrustWorksInBeforeToolCall() {
        setupRule("FileSystemTool", "ask_once");
        config.workspaceRoot = "/ws";

        sessionMemory.addTrustedPaths("s1", List.of("src/main"));

        hookManager.enterSession("s1");
        try {
            testHandler.callCount = 0;
            Object[] args2 = new Object[]{"/ws/src/main/Bar.java"};
            assertDoesNotThrow(() ->
                    hookManager.beforeToolCall("FileSystemTool", "readFile", args2));
            assertEquals(0, testHandler.callCount,
                    "beforeToolCall should skip handler — descendant path is trusted");
        } finally {
            hookManager.exitSession();
        }
    }

    @Test
    void testPathTrustSkipsSiblingFileAfterAllowAlways() throws Exception {
        setupRule("FileSystemTool", "ask_once");
        testHandler.nextResult = ConfirmationResult.ALLOW_ALWAYS;

        // User says 'a' for reading /ws/src/main/Foo.java
        Object[] args1 = new Object[]{"/ws/src/main/Foo.java"};
        hookManager.onToolCall("FileSystemTool", "readFile",
                args1, "s1", okCall());
        assertEquals(1, testHandler.callCount);

        // Now read /ws/src/main/Bar.java — sibling file in same directory
        // Should skip because parent directory "src/main" was stored
        testHandler.callCount = 0;
        Object[] args2 = new Object[]{"/ws/src/main/Bar.java"};
        String result = hookManager.onToolCall("FileSystemTool", "readFile",
                args2, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(0, testHandler.callCount,
                "Handler should NOT be called — parent directory was trusted");
    }

    // ========== Path-Scoped Grant Tests (FileSystemTool, in-scope paths) ==========

    @Test
    void testPathGrantAllowsSamePathDifferentContent() throws Exception {
        setupRule("FileSystemTool", "ask_once");
        config.workspaceRoot = "/ws";
        hookManager.setProjectRoot("/proj");
        testHandler.nextResult = ConfirmationResult.ALLOW_ALWAYS;

        // 'a' on write_file(/proj/src/Foo.java, content1)
        hookManager.onToolCall("FileSystemTool", "write_file",
                new Object[]{"/proj/src/Foo.java", "content1"}, "s1", okCall());
        assertEquals(1, testHandler.callCount);

        // Same path, different content — auto-allowed, no prompt
        testHandler.callCount = 0;
        String result = hookManager.onToolCall("FileSystemTool", "write_file",
                new Object[]{"/proj/src/Foo.java", "totally different content"}, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(0, testHandler.callCount, "Path grant covers same path with different content");
    }

    @Test
    void testPathGrantAllowsDifferentMethodSamePath() throws Exception {
        setupRule("FileSystemTool", "ask_once");
        config.workspaceRoot = "/ws";
        hookManager.setProjectRoot("/proj");
        testHandler.nextResult = ConfirmationResult.ALLOW_ALWAYS;

        hookManager.onToolCall("FileSystemTool", "write_file",
                new Object[]{"/proj/src/Foo.java", "content"}, "s1", okCall());
        assertEquals(1, testHandler.callCount);

        // delete_file on the same path — auto-allowed via path grant
        testHandler.callCount = 0;
        String result = hookManager.onToolCall("FileSystemTool", "delete_file",
                new Object[]{"/proj/src/Foo.java"}, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(0, testHandler.callCount, "Path grant covers same path, different method");
    }

    @Test
    void testPathGrantDoesNotCoverDifferentPath() throws Exception {
        setupRule("FileSystemTool", "ask_once");
        config.workspaceRoot = "/ws";
        hookManager.setProjectRoot("/proj");
        testHandler.nextResult = ConfirmationResult.ALLOW_ALWAYS;

        hookManager.onToolCall("FileSystemTool", "write_file",
                new Object[]{"/proj/src/Foo.java", "content"}, "s1", okCall());
        assertEquals(1, testHandler.callCount);

        // Different path — must prompt again
        testHandler.callCount = 0;
        hookManager.onToolCall("FileSystemTool", "write_file",
                new Object[]{"/proj/src/Bar.java", "content"}, "s1", okCall());
        assertEquals(1, testHandler.callCount, "Different path still prompts");
    }

    @Test
    void testPathGrantDoesNotCoverOtherTools() throws Exception {
        setupRule("FileSystemTool", "ask_once");
        setupRule2("ShellTool", "ask_once");
        config.workspaceRoot = "/ws";
        hookManager.setProjectRoot("/proj");
        testHandler.nextResult = ConfirmationResult.ALLOW_ALWAYS;

        hookManager.onToolCall("FileSystemTool", "write_file",
                new Object[]{"/proj/src/Foo.java", "content"}, "s1", okCall());
        assertEquals(1, testHandler.callCount);

        // ShellTool referencing the same path — tool name is part of the key.
        // (An unknown command is used: `touch /proj/src/Foo.java` is now auto-
        // safe under rule 2 as an in-project write, so it would not prompt.)
        testHandler.callCount = 0;
        hookManager.onToolCall("ShellTool", "execute",
                new Object[]{"some-custom-tool /proj/src/Foo.java"}, "s1", okCall());
        assertEquals(1, testHandler.callCount, "Other tool still prompts for same path");
    }

    private void setupRule2(String tool, String level) {
        java.util.ArrayList<AgentConfig.HookRule> rules = new java.util.ArrayList<>(config.hooks.rules);
        AgentConfig.HookRule rule = new AgentConfig.HookRule();
        rule.tool = tool;
        rule.level = level;
        rules.add(rule);
        config.hooks.rules = rules;
    }

    @Test
    void testRelativeAndAbsoluteShareOneGrant() throws Exception {
        setupRule("FileSystemTool", "ask_once");
        config.workspaceRoot = "/ws";
        hookManager.setProjectRoot("/proj");
        testHandler.nextResult = ConfirmationResult.ALLOW_ALWAYS;

        // Grant via absolute path
        hookManager.onToolCall("FileSystemTool", "write_file",
                new Object[]{"/proj/src/Foo.java", "content"}, "s1", okCall());
        assertEquals(1, testHandler.callCount);

        // Same file via relative path — auto-allowed
        testHandler.callCount = 0;
        String result = hookManager.onToolCall("FileSystemTool", "write_file",
                new Object[]{"src/Foo.java", "content"}, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(0, testHandler.callCount, "Relative form matches absolute grant");
    }

    @Test
    void testBareFilenameGrant() throws Exception {
        setupRule("FileSystemTool", "ask_once");
        config.workspaceRoot = "/ws";
        hookManager.setProjectRoot("/proj");
        testHandler.nextResult = ConfirmationResult.ALLOW_ALWAYS;

        hookManager.onToolCall("FileSystemTool", "write_file",
                new Object[]{"file.txt", "content"}, "s1", okCall());
        assertEquals(1, testHandler.callCount);

        // Same bare filename with different content — auto-allowed
        testHandler.callCount = 0;
        String result = hookManager.onToolCall("FileSystemTool", "write_file",
                new Object[]{"file.txt", "other"}, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(0, testHandler.callCount);
    }

    @Test
    void testOutOfScopePathFallsBackToLegacyBehavior() throws Exception {
        setupRule("FileSystemTool", "ask_once");
        config.workspaceRoot = "/ws";
        hookManager.setProjectRoot("/proj");
        testHandler.nextResult = ConfirmationResult.ALLOW_ALWAYS;

        // Path outside project root and not trusted — legacy stores apply
        hookManager.onToolCall("FileSystemTool", "write_file",
                new Object[]{"/tmp/other.txt", "content"}, "s1", okCall());
        assertEquals(1, testHandler.callCount);

        // No path grant recorded for the out-of-scope path
        assertFalse(sessionMemory.isPathAllowedAlways("s1", "FileSystemTool", "/tmp/other.txt"));
        // Legacy method trust recorded instead
        assertTrue(sessionMemory.isMethodAllowed("s1", "FileSystemTool", "write_file"));
    }

    @Test
    void testTrustedPathMakesScopeApply() throws Exception {
        setupRule("FileSystemTool", "ask_once");
        config.workspaceRoot = "/ws";
        hookManager.setProjectRoot("/proj");
        testHandler.nextResult = ConfirmationResult.ALLOW_ALWAYS;

        // Trust a directory outside the project root
        sessionMemory.addTrustedPaths("s1", List.of("/data/shared"));

        // Content mentions an untrusted path, so directory-prefix trust
        // (allMatch over all extracted paths) does NOT skip — the call prompts.
        hookManager.onToolCall("FileSystemTool", "write_file",
                new Object[]{"/data/shared/notes.txt", "see /elsewhere/x"}, "s1", okCall());
        assertEquals(1, testHandler.callCount);
        // args[0] is under a trusted path -> in scope -> path grant recorded
        assertTrue(sessionMemory.isPathAllowedAlways("s1", "FileSystemTool", "/data/shared/notes.txt"));
        assertFalse(sessionMemory.isMethodAllowed("s1", "FileSystemTool", "write_file"));

        // Same path, different content — auto-allowed via path grant
        testHandler.callCount = 0;
        String result = hookManager.onToolCall("FileSystemTool", "write_file",
                new Object[]{"/data/shared/notes.txt", "other /elsewhere/y"}, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(0, testHandler.callCount);
    }

    @Test
    void testPathGrantDoesNotBypassDangerous() throws Exception {
        setupRule("FileSystemTool", "ask_once");
        setupPattern("dangerous_files", "FileSystemTool", "dangerous", List.of("secrets"));
        config.workspaceRoot = "/ws";
        hookManager.setProjectRoot("/proj");
        testHandler.nextResult = ConfirmationResult.ALLOW_ALWAYS;

        // First: grant on a normal in-project path
        hookManager.onToolCall("FileSystemTool", "write_file",
                new Object[]{"/proj/secrets.txt", "content"}, "s1", okCall());
        assertEquals(1, testHandler.callCount);

        // DANGEROUS escalation via pattern — always prompts regardless of grant
        testHandler.nextResult = ConfirmationResult.ALLOW;
        testHandler.callCount = 0;
        String result = hookManager.onToolCall("FileSystemTool", "write_file",
                new Object[]{"/proj/secrets.txt", "content"}, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(1, testHandler.callCount, "DANGEROUS always prompts even with path grant");
    }

    @Test
    void testPathGrantIsSessionScoped() throws Exception {
        setupRule("FileSystemTool", "ask_once");
        config.workspaceRoot = "/ws";
        hookManager.setProjectRoot("/proj");
        testHandler.nextResult = ConfirmationResult.ALLOW_ALWAYS;

        hookManager.onToolCall("FileSystemTool", "write_file",
                new Object[]{"/proj/src/Foo.java", "content"}, "s1", okCall());
        assertEquals(1, testHandler.callCount);

        // Session 2 — no grant, must prompt
        testHandler.nextResult = ConfirmationResult.ALLOW;
        testHandler.callCount = 0;
        hookManager.onToolCall("FileSystemTool", "write_file",
                new Object[]{"/proj/src/Foo.java", "content"}, "s2", okCall());
        assertEquals(1, testHandler.callCount, "Grant is session-scoped");
    }

    @Test
    void testProjectRootSwitchChangesScope() throws Exception {
        setupRule("FileSystemTool", "ask_once");
        config.workspaceRoot = "/ws";
        hookManager.setProjectRoot("/proj");
        testHandler.nextResult = ConfirmationResult.ALLOW_ALWAYS;

        hookManager.onToolCall("FileSystemTool", "write_file",
                new Object[]{"/proj/src/Foo.java", "content"}, "s1", okCall());
        assertEquals(1, testHandler.callCount);

        // Switch project root — old path is now out of scope, grant no longer matches
        hookManager.setProjectRoot("/other");
        testHandler.nextResult = ConfirmationResult.ALLOW;
        testHandler.callCount = 0;
        hookManager.onToolCall("FileSystemTool", "write_file",
                new Object[]{"/proj/src/Foo.java", "content"}, "s1", okCall());
        assertEquals(1, testHandler.callCount, "Out-of-scope after project switch prompts again");
    }

    @Test
    void testPathGrantWorksInBeforeToolCall() {
        setupRule("FileSystemTool", "ask_once");
        config.workspaceRoot = "/ws";
        hookManager.setProjectRoot("/proj");
        testHandler.nextResult = ConfirmationResult.ALLOW_ALWAYS;

        hookManager.enterSession("s1");
        try {
            hookManager.beforeToolCall("FileSystemTool", "write_file",
                    new Object[]{"/proj/src/Foo.java", "content"});
            assertEquals(1, testHandler.callCount);

            // Same path, different content — skipped via path grant
            testHandler.callCount = 0;
            assertDoesNotThrow(() -> hookManager.beforeToolCall("FileSystemTool", "write_file",
                    new Object[]{"/proj/src/Foo.java", "different content"}));
            assertEquals(0, testHandler.callCount, "beforeToolCall honors path grant");
        } finally {
            hookManager.exitSession();
        }
    }

    // ========== Project-root-aware ShellTool classification (3 rules) ==========

    @Test
    void testShellToolInProjectWriteIsSafeNoPrompt() throws Exception {
        hookManager.setProjectRoot("/proj");
        String result = hookManager.onToolCall("ShellTool", "execute",
                new Object[]{"echo hello > notes.txt"}, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(0, testHandler.callCount, "in-project write must not prompt");
    }

    @Test
    void testShellToolHomeWritePromptsDangerous() throws Exception {
        hookManager.setProjectRoot("/proj");
        hookManager.onToolCall("ShellTool", "execute",
                new Object[]{"echo x > ~/.bashrc"}, "s1", okCall());
        assertEquals(1, testHandler.callCount, "~/.bashrc write must prompt");
        assertEquals(DangerLevel.DANGEROUS, testHandler.lastInfo.level());
    }

    @Test
    void testShellToolGitRevertIsSafeNoPrompt() throws Exception {
        hookManager.setProjectRoot("/proj");
        String result = hookManager.onToolCall("ShellTool", "execute",
                new Object[]{"git revert HEAD"}, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(0, testHandler.callCount, "git revert (no data loss) must not prompt");
    }

    @Test
    void testShellToolRmStillPromptsDangerous() throws Exception {
        hookManager.setProjectRoot("/proj");
        hookManager.onToolCall("ShellTool", "execute",
                new Object[]{"rm file.txt"}, "s1", okCall());
        assertEquals(1, testHandler.callCount, "rm must prompt");
        assertEquals(DangerLevel.DANGEROUS, testHandler.lastInfo.level());
    }

    @Test
    void testShellToolSedInProjectIsSafeNoPrompt() throws Exception {
        hookManager.setProjectRoot("/proj");
        String result = hookManager.onToolCall("ShellTool", "execute",
                new Object[]{"sed -i 's/x/y/' src/Foo.java"}, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(0, testHandler.callCount, "sed -i on a project file must not prompt");
    }

    @Test
    void testShellToolSedOnHomePromptsDangerous() throws Exception {
        hookManager.setProjectRoot("/proj");
        hookManager.onToolCall("ShellTool", "execute",
                new Object[]{"sed -i 's/x/y/' ~/.bashrc"}, "s1", okCall());
        assertEquals(1, testHandler.callCount, "sed -i on ~/.bashrc must prompt");
        assertEquals(DangerLevel.DANGEROUS, testHandler.lastInfo.level());
    }
}
