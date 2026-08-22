package com.ooooyt.babycommander.hook;

import com.ooooyt.babycommander.config.AgentConfig;
import com.ooooyt.babycommander.config.YamlConfigLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the in-scope auto-trust feature ({@code BABY_COMMANDER_TRUST_PROJECT}
 * / {@code trustProject} in {@code hooks.yaml}).
 *
 * <p>When the trust mode is {@code auto} (default) or {@code always}, operations
 * confined to the current project folder run without confirmation for
 * {@code ask_once} operations, unless they match a {@code dangerous} pattern.
 * In {@code strict} mode, in-scope {@code ask_once} operations still prompt.</p>
 */
class HookManagerTrustProjectTest {

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
        config.hooks.trustProject = "auto";
        config.workspaceRoot = "/ws";

        AgentConfig.HookRule fsRule = new AgentConfig.HookRule();
        fsRule.tool = "FileSystemTool";
        fsRule.level = "ask_once";
        config.hooks.rules = List.of(fsRule);

        AgentConfig.HookRule shellRule = new AgentConfig.HookRule();
        shellRule.tool = "ShellTool";
        shellRule.level = "ask_once";
        config.hooks.rules = List.of(fsRule, shellRule);

        sessionMemory = new SessionMemory();
        testHandler = new TestConfirmationHandler();
        hookManager = new HookManager(createLoader(config), sessionMemory);
        hookManager.setConfirmationHandler(testHandler);
        hookManager.setProjectRoot("/proj");
    }

    private void setupPattern(String groupName, String tool, String level, List<String> matches) {
        if (config.hooks.patterns == null) {
            config.hooks.patterns = new java.util.HashMap<>();
        }
        AgentConfig.PatternGroup pg = new AgentConfig.PatternGroup();
        pg.tool = tool;
        pg.level = level;
        pg.matches = matches;
        pg.compiledPatterns = new java.util.ArrayList<>();
        for (String regex : matches) {
            pg.compiledPatterns.add(java.util.regex.Pattern.compile(regex));
        }
        config.hooks.patterns.put(groupName, pg);
    }

    private Callable<String> okCall() {
        return () -> "ok";
    }

    // ========== FileSystemTool in-scope auto-trust ==========

    @Test
    void testInScopeFileSystemWriteNoPromptInAutoMode() throws Exception {
        // writeFile under /proj is in-scope -> no confirmation in auto mode
        String result = hookManager.onToolCall("FileSystemTool", "writeFile",
                new Object[]{"/proj/src/Foo.java", "content"}, "s1", okCall());
        assertEquals("ok", result);
        assertEquals(0, testHandler.callCount);
    }

    @Test
    void testOutOfScopeFileSystemWritePromptsInAutoMode() throws Exception {
        // /etc/x is outside the project folder -> still prompts
        hookManager.onToolCall("FileSystemTool", "writeFile",
                new Object[]{"/etc/x.conf", "content"}, "s1", okCall());
        assertEquals(1, testHandler.callCount);
        assertEquals(DangerLevel.ASK_ONCE, testHandler.lastInfo.level());
    }

    @Test
    void testRelativeInScopeFileSystemWriteNoPrompt() throws Exception {
        // Relative path resolves under project root -> in-scope
        hookManager.onToolCall("FileSystemTool", "writeFile",
                new Object[]{"src/Bar.java", "content"}, "s1", okCall());
        assertEquals(0, testHandler.callCount);
    }

    @Test
    void testNoProjectRootSetMeansOutOfScope() throws Exception {
        // Without a project root, nothing is in-scope -> prompts
        hookManager.setProjectRoot(null);
        hookManager.onToolCall("FileSystemTool", "writeFile",
                new Object[]{"/proj/x.txt", "c"}, "s1", okCall());
        assertEquals(1, testHandler.callCount);
    }

    // ========== ShellTool in-scope auto-trust ==========

    @Test
    void testInScopeShellCommandNoPromptInAutoMode() throws Exception {
        // mvn test with only in-scope paths -> no prompt
        setupPattern("java", "ShellTool", "ask_once", List.of("^mvn\\s"));
        hookManager.onToolCall("ShellTool", "execute",
                new Object[]{"mvn test -f /proj/pom.xml"}, "s1", okCall());
        assertEquals(0, testHandler.callCount);
    }

    @Test
    void testShellCommandNoPathsIsOutOfScope() throws Exception {
        // A command with no extractable paths is conservatively out-of-scope
        setupPattern("java", "ShellTool", "ask_once", List.of("^mvn\\s"));
        // mvn test has no path tokens -> out-of-scope -> prompt
        hookManager.onToolCall("ShellTool", "execute",
                new Object[]{"mvn test"}, "s1", okCall());
        assertEquals(1, testHandler.callCount);
    }

    @Test
    void testDangerousShellNeverAutoTrusted() throws Exception {
        // sudo rm -rf matches a dangerous pattern -> always prompts
        setupPattern("dangerous_commands", "ShellTool", "dangerous", List.of("sudo\\s+"));
        hookManager.onToolCall("ShellTool", "execute",
                new Object[]{"sudo rm -rf /proj"}, "s1", okCall());
        assertEquals(1, testHandler.callCount);
        assertEquals(DangerLevel.DANGEROUS, testHandler.lastInfo.level());
    }

    @Test
    void testDangerousFileSystemPatternStillPromptsEvenIfInScope() throws Exception {
        // A dangerous FileSystemTool operation should still prompt even in-scope.
        // (FileSystemTool has no dangerous patterns in default config, but the
        // gate only applies to ASK_ONCE, so a DANGEROUS level still prompts.)
        setupRule("FileSystemTool", "dangerous");
        hookManager.onToolCall("FileSystemTool", "deleteFile",
                new Object[]{"/proj/file.txt"}, "s1", okCall());
        assertEquals(1, testHandler.callCount);
        assertEquals(DangerLevel.DANGEROUS, testHandler.lastInfo.level());
    }

    private void setupRule(String tool, String level) {
        config.hooks.rules = new java.util.ArrayList<>();
        AgentConfig.HookRule rule = new AgentConfig.HookRule();
        rule.tool = tool;
        rule.level = level;
        config.hooks.rules = List.of(rule);
    }

    // ========== Mode toggling ==========

    @Test
    void testStrictModePromptsForInScope() throws Exception {
        config.hooks.trustProject = "strict";
        hookManager.onToolCall("FileSystemTool", "writeFile",
                new Object[]{"/proj/src/Foo.java", "content"}, "s1", okCall());
        assertEquals(1, testHandler.callCount);
    }

    @Test
    void testAlwaysModeNoPromptForInScope() throws Exception {
        config.hooks.trustProject = "always";
        hookManager.onToolCall("FileSystemTool", "writeFile",
                new Object[]{"/proj/src/Foo.java", "content"}, "s1", okCall());
        assertEquals(0, testHandler.callCount);
    }

    @Test
    void testUnknownModeDefaultsToAuto() throws Exception {
        config.hooks.trustProject = "bogus";
        hookManager.onToolCall("FileSystemTool", "writeFile",
                new Object[]{"/proj/src/Foo.java", "content"}, "s1", okCall());
        assertEquals(0, testHandler.callCount);
    }

    // ========== Read-only FileSystemTool methods ==========

    @Test
    void testReadOnlyMethodsNeverPromptInAnyMode() throws Exception {
        // read_file is inherently safe -> no prompt even in strict mode
        config.hooks.trustProject = "strict";
        hookManager.onToolCall("FileSystemTool", "read_file",
                new Object[]{"/proj/file.txt"}, "s1", okCall());
        assertEquals(0, testHandler.callCount);
    }

    // ========== beforeToolCall gate ==========

    @Test
    void testBeforeToolCallInScopeNoPrompt() {
        hookManager.enterSession("s1");
        try {
            assertDoesNotThrow(() ->
                    hookManager.beforeToolCall("FileSystemTool", "writeFile",
                            new Object[]{"/proj/src/Foo.java", "content"}));
            assertEquals(0, testHandler.callCount);
        } finally {
            hookManager.exitSession();
        }
    }

    @Test
    void testBeforeToolCallOutOfScopePrompts() {
        hookManager.enterSession("s1");
        try {
            assertDoesNotThrow(() ->
                    hookManager.beforeToolCall("FileSystemTool", "writeFile",
                            new Object[]{"/etc/x.conf", "content"}));
            assertEquals(1, testHandler.callCount);
        } finally {
            hookManager.exitSession();
        }
    }
}
