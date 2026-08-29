package com.ooooyt.babycommander.editloop;

import com.ooooyt.babycommander.agent.AgentContext;
import com.ooooyt.babycommander.agent.memory.SummaryMessage;
import com.ooooyt.babycommander.agent.memory.ToolCallAwareChatMemory;
import com.ooooyt.babycommander.tool.ShellTool;

import io.quarkus.logging.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared edit loop component: runs test -> analyze failure -> fix -> re-run cycle.
 * Used by bugfix, refactor, and extension modes.
 */
public class EditLoop {

    private final ShellTool shellTool;
    private final String projectFolder;

    public EditLoop(ShellTool shellTool, String projectFolder) {
        this.shellTool = shellTool;
        this.projectFolder = projectFolder;
    }

    /**
     * Run the edit loop until tests pass or maxRetries is exhausted.
     *
     * @param fixerContext    The FIXER agent context (has chat memory and agent reference)
     * @param testCommand     The shell command to run tests (e.g. "mvn test" or "pytest tests/")
     * @param failureContext  The initial failure output or description of what needs fixing
     * @param maxRetries      Maximum number of fix attempts
     * @return EditLoopResult with success/failure and details
     */
    public EditLoopResult run(
            AgentContext fixerContext,
            String testCommand,
            String failureContext,
            int maxRetries) {

        List<String> attemptSummaries = new ArrayList<>();

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            Log.infof("EditLoop: attempt %d/%d", attempt, maxRetries);

            // Build the prompt for the FIXER agent
            String fixPrompt = buildFixPrompt(failureContext, attempt, attemptSummaries);

            // Ask FIXER to analyze and apply fixes
            try {
                fixerContext.agent().chat(fixPrompt);
            } catch (Exception e) {
                Log.errorf(e, "EditLoop: FIXER agent call failed on attempt %d", attempt);
                attemptSummaries.add(String.format("Attempt %d: FIXER call failed - %s", attempt, e.getMessage()));
                if (fixerContext.chatMemory() instanceof ToolCallAwareChatMemory tcm) {
                    String summaryText = String.format("EditLoop: attempt %d/%d - FIXER call failed", attempt, maxRetries);
                    if (tcm.getSummary() == null) {
                        tcm.setSummary(new SummaryMessage(summaryText));
                    } else {
                        tcm.updateSummary(summaryText);
                    }
                }
                continue;
            }

            // Clean spent tool-call pairs to prevent accumulation across iterations
            if (fixerContext.chatMemory() instanceof ToolCallAwareChatMemory tcm) {
                tcm.compact();
            }

            // Run tests after the fix
            String testOutput = runTests(testCommand);
            attemptSummaries.add(String.format("Attempt %d: FIXER applied changes. Test output: %s", attempt, truncate(testOutput, 500)));

            // Update ChatMemory summary to persist context across iterations
            if (fixerContext.chatMemory() instanceof ToolCallAwareChatMemory tcm) {
                String summaryText = String.format(
                        "EditLoop: attempt %d/%d - %s",
                        attempt, maxRetries,
                        isSuccessful(testOutput) ? "tests passing" : truncate(testOutput, 300));
                if (tcm.getSummary() == null) {
                    tcm.setSummary(new SummaryMessage(summaryText));
                } else {
                    tcm.updateSummary(summaryText);
                }
            }

            if (isSuccessful(testOutput)) {
                Log.infof("EditLoop: tests passed after %d attempt(s)", attempt);
                return EditLoopResult.success(testOutput, attempt);
            }

            // Update failure context for next iteration
            failureContext = testOutput;
        }

        Log.warnf("EditLoop: all %d attempts exhausted", maxRetries);
        String finalTestOutput = runTests(testCommand);
        return EditLoopResult.failure(finalTestOutput, maxRetries, attemptSummaries);
    }

    /**
     * Run the edit loop with user interaction on exhaustion (bugfix mode).
     * When max retries are exhausted, returns the result so the caller can ask the user.
     */
    public EditLoopResult runWithUserRetry(
            AgentContext fixerContext,
            String testCommand,
            String failureContext,
            int maxRetries,
            java.util.function.BooleanSupplier shouldRetry) {

        EditLoopResult result = run(fixerContext, testCommand, failureContext, maxRetries);
        int totalAttempts = result.attempts();
        List<String> allSummaries = new ArrayList<>(result.attemptSummaries());

        // Keep retrying if user says yes
        while (!result.success() && shouldRetry.getAsBoolean()) {
            Log.info("EditLoop: user requested additional retry");
            result = run(fixerContext, testCommand, result.testOutput(), maxRetries);
            totalAttempts += result.attempts();
            allSummaries.addAll(result.attemptSummaries());
        }

        return new EditLoopResult(result.success(), result.testOutput(), totalAttempts, List.copyOf(allSummaries));
    }

    private String buildFixPrompt(String failureContext, int attempt, List<String> attemptSummaries) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tests are failing. Analyze the error output below and apply a fix.\n\n");
        sb.append("FAILURE OUTPUT:\n");
        sb.append(truncate(failureContext, 5000));
        sb.append("\n\n");

        if (attempt > 1) {
            sb.append("Previous attempts:\n");
            for (String summary : attemptSummaries) {
                sb.append("- ").append(summary).append("\n");
            }
            sb.append("\nEach previous attempt failed. Try a different approach.\n");
        }

        sb.append("Use your available tools (readFile, writeFile, execute shell commands, etc.) to inspect the failing files and apply precise fixes.\n");
        sb.append("After applying fixes, the test will be re-run automatically.\n");
        return sb.toString();
    }

    private String runTests(String testCommand) {
        try {
            return shellTool.executeInDir(testCommand, projectFolder);
        } catch (Exception e) {
            return "Test execution error: " + e.getMessage();
        }
    }

    private boolean isSuccessful(String testOutput) {
        if (testOutput == null || testOutput.isBlank()) {
            return false;
        }
        String lower = testOutput.toLowerCase();
        // Common success indicators across test frameworks
        return lower.contains("all tests passed")
            || lower.contains("all test") && lower.contains("pass")
            || lower.contains("tests passed")
            || lower.contains("passed:") && !lower.contains("failed:")
            || lower.contains("success") && !lower.contains("failure")
            || (lower.contains("passed") && !lower.contains("failed") && !lower.contains("error"));
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "... [truncated]";
    }
}
