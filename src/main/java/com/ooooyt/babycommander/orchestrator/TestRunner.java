package com.ooooyt.babycommander.orchestrator;

import com.ooooyt.babycommander.hook.HookManager;

import java.util.UUID;

/**
 * Detects the project's test command, runs it through the {@link HookManager},
 * and classifies the output as successful or not. Extracted so the bugfix,
 * refactor and extension strategies can share one implementation.
 */
public class TestRunner {

    private final ToolLocator toolLocator;
    private final HookManager hookManager;
    private final OrchestratorContext context;

    public TestRunner(ToolLocator toolLocator, HookManager hookManager, OrchestratorContext context) {
        this.toolLocator = toolLocator;
        this.hookManager = hookManager;
        this.context = context;
    }

    /**
     * Detect the appropriate test command based on project folder contents.
     * Checks for common test frameworks in priority order.
     */
    public String detectTestCommand() {
        String[] commands = {
            "mvn test",
            "gradle test",
            "npm test",
            "yarn test",
            "python -m pytest",
            "pytest",
            "go test ./...",
            "cargo test"
        };
        String toolWorkDir = context.getProjectFolder();

        for (String cmd : commands) {
            String result;
            try {
                result = toolLocator.getShellTool().executeInDirWithTimeout(cmd, toolWorkDir, 10);
            } catch (Exception e) {
                continue;
            }
            if (result != null && !result.toLowerCase().contains("command not found")
                && !result.toLowerCase().contains("is not recognized")) {
                return cmd;
            }
        }

        return "mvn test";
    }

    public String runTests(String testCommand) {
        try {
            return hookManager.onToolCall("ShellTool", "executeInDir",
                    new Object[]{testCommand, context.getProjectFolder()},
                    UUID.randomUUID().toString(),
                    () -> toolLocator.getShellTool().executeInDir(testCommand, context.getProjectFolder()));
        } catch (Exception e) {
            return "Test execution error: " + e.getMessage();
        }
    }

    public boolean isSuccessful(String testOutput) {
        if (testOutput == null || testOutput.isBlank()) return false;
        String lower = testOutput.toLowerCase();
        return lower.contains("all tests passed")
            || (lower.contains("passed") && !lower.contains("failed") && !lower.contains("error"));
    }
}
