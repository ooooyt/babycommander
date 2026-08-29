package com.ooooyt.babycommander.editloop;

import com.ooooyt.babycommander.agent.AgentContext;
import com.ooooyt.babycommander.agent.CodegenAgent;
import com.ooooyt.babycommander.tool.ShellTool;

import dev.langchain4j.memory.ChatMemory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EditLoop")
class EditLoopTest {

    private static final String SUCCESS_OUTPUT = "All tests passed (5 tests)";
    private static final String FAILURE_OUTPUT = "FAILURE: Test still failing";

    private final String workspace = "/tmp/test-workspace";

    @Test
    @DisplayName("should return success when tests pass on first attempt")
    void testRunReturnsSuccessWhenTestsPassOnFirstAttempt() {
        ShellTool mockShell = new ShellTool(workspace, null) {
            @Override
            public String executeInDir(String command, String dir) {
                return SUCCESS_OUTPUT;
            }
        };

        AgentContext mockCtx = createMockAgentContext("Tests will pass now");
        EditLoop loop = new EditLoop(mockShell, workspace);

        EditLoopResult result = loop.run(mockCtx, "mvn test", "Some failure", 3);

        assertAll("first attempt success",
            () -> assertTrue(result.success()),
            () -> assertEquals(1, result.attempts()),
            () -> assertEquals(SUCCESS_OUTPUT, result.testOutput())
        );
    }

    @Test
    @DisplayName("should retry until tests pass")
    void testRunRetriesUntilTestsPass() {
        final int[] callCount = {0};

        ShellTool mockShell = new ShellTool(workspace, null) {
            @Override
            public String executeInDir(String command, String dir) {
                callCount[0]++;
                if (callCount[0] == 1) return "FAILURE: NullPointer in UserService.java:42";
                if (callCount[0] == 2) return "FAILURE: NullPointerException fixed but ArrayIndexOutOfBounds in UserService.java:55";
                return SUCCESS_OUTPUT;
            }
        };

        AgentContext mockCtx = createMockAgentContext("Fix applied");
        EditLoop loop = new EditLoop(mockShell, workspace);

        EditLoopResult result = loop.run(mockCtx, "mvn test", "Initial failure", 3);

        assertAll("retry until pass",
            () -> assertTrue(result.success()),
            () -> assertEquals(3, result.attempts()),
            () -> assertEquals(3, callCount[0])
        );
    }

    @Test
    @DisplayName("should return failure when max retries exhausted")
    void testRunReturnsFailureWhenMaxRetriesExhausted() {
        ShellTool mockShell = new ShellTool(workspace, null) {
            @Override
            public String executeInDir(String command, String dir) {
                return FAILURE_OUTPUT;
            }
        };

        AgentContext mockCtx = createMockAgentContext("Tried to fix");
        EditLoop loop = new EditLoop(mockShell, workspace);

        EditLoopResult result = loop.run(mockCtx, "mvn test", "Initial failure", 2);

        assertAll("exhaustion failure",
            () -> assertFalse(result.success()),
            () -> assertEquals(2, result.attempts()),
            () -> assertEquals(2, result.attemptSummaries().size())
        );
    }

    @Test
    @DisplayName("should handle agent exception gracefully")
    void testRunHandlesAgentException() {
        ShellTool mockShell = new ShellTool(workspace, null) {
            @Override
            public String executeInDir(String command, String dir) {
                return FAILURE_OUTPUT;
            }
        };

        AgentContext mockCtx = createMockAgentException(new RuntimeException("Agent error"));
        EditLoop loop = new EditLoop(mockShell, workspace);

        EditLoopResult result = loop.run(mockCtx, "mvn test", "failure", 1);

        assertAll("agent exception handling",
            () -> assertFalse(result.success()),
            () -> assertEquals(1, result.attempts()),
            () -> assertTrue(result.attemptSummaries().get(0).contains("FIXER call failed"))
        );
    }

    @Test
    @DisplayName("should stop retrying when user says no")
    void testRunWithUserRetryStopsWhenUserSaysNo() {
        ShellTool mockShell = new ShellTool(workspace, null) {
            @Override
            public String executeInDir(String command, String dir) {
                return FAILURE_OUTPUT;
            }
        };

        AgentContext mockCtx = createMockAgentContext("Tried");
        EditLoop loop = new EditLoop(mockShell, workspace);

        EditLoopResult result = loop.runWithUserRetry(mockCtx, "mvn test", "failure", 1, () -> false);

        assertAll("user stops retry",
            () -> assertFalse(result.success()),
            () -> assertEquals(1, result.attempts())
        );
    }

    @Test
    @DisplayName("should continue retrying when user says yes")
    void testRunWithUserRetryContinuesWhenUserSaysYes() {
        final int[] retryDecision = {1};

        ShellTool mockShell = new ShellTool(workspace, null) {
            @Override
            public String executeInDir(String command, String dir) {
                return FAILURE_OUTPUT;
            }
        };

        AgentContext mockCtx = createMockAgentContext("Tried");
        EditLoop loop = new EditLoop(mockShell, workspace);

        EditLoopResult result = loop.runWithUserRetry(mockCtx, "mvn test", "failure", 2, () -> {
            return retryDecision[0]-- > 0;
        });

        assertAll("user continues retry",
            () -> assertFalse(result.success()),
            () -> assertEquals(4, result.attempts())
        );
    }

    private AgentContext createMockAgentContext(String response) {
        return createMockAgentContext(response, false);
    }

    private AgentContext createMockAgentException(Exception e) {
        return createMockAgentContext(e.getMessage(), true);
    }

    private AgentContext createMockAgentContext(String response, boolean throwException) {
        CodegenAgent mockAgent = new CodegenAgent() {
            @Override
            public String chat(String userMessage) {
                if (throwException) throw new RuntimeException(response);
                return response;
            }
            @Override
            public String chatWithSystemPrompt(String systemPrompt, String userMessage) {
                if (throwException) throw new RuntimeException(response);
                return response;
            }
        };
        return new AgentContext("test-session", mockAgent, workspace, "fixer", List.of(), createMockChatMemory());
    }

    private static ChatMemory createMockChatMemory() {
        return new ChatMemory() {
            @Override
            public void add(dev.langchain4j.data.message.ChatMessage message) {
                // no-op
            }
            @Override
            public List<dev.langchain4j.data.message.ChatMessage> messages() {
                return List.of();
            }
            @Override
            public void clear() {
                // no-op
            }
            @Override
            public String id() {
                return "test";
            }
        };
    }
}
