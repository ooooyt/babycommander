package com.ooooyt.babycommander.status;

import com.ooooyt.babycommander.util.I18n;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class ConsoleStatusRendererTest {

    private static Method onEventMethod;
    private ConsoleStatusRenderer renderer;

    @BeforeAll
    static void setupLocale() throws Exception {
        I18n.setLocale(Locale.ENGLISH);
        onEventMethod = ConsoleStatusRenderer.class.getDeclaredMethod("onEvent", JsonObject.class);
        onEventMethod.setAccessible(true);
    }

    @BeforeEach
    void setUp() {
        renderer = new ConsoleStatusRenderer();
    }

    private String captureOutput(JsonObject event) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(baos));
        try {
            onEventMethod.invoke(renderer, event);
        } finally {
            System.setOut(original);
        }
        return baos.toString();
    }

    @Test
    @Timeout(5)
    void testWorkflowStarted() throws Exception {
        JsonObject event = new JsonObject()
            .put("eventType", "WORKFLOW_STARTED")
            .put("workflowType", "SEQUENTIAL")
            .put("taskDescription", "Build a calculator");

        String output = captureOutput(event);
        assertTrue(output.contains("Workflow Started") || output.contains("workflow"),
            "Output should contain workflow started message");
        assertTrue(output.contains("Build a calculator"),
            "Output should contain task description");
        assertTrue(output.contains("SEQUENTIAL"),
            "Output should contain workflow type");
    }

    @Test
    @Timeout(5)
    void testWorkflowStarted_missingFields() throws Exception {
        JsonObject event = new JsonObject()
            .put("eventType", "WORKFLOW_STARTED");

        String output = captureOutput(event);
        assertNotNull(output);
    }

    @Test
    @Timeout(5)
    void testWorkflowTypeChanged() throws Exception {
        JsonObject event = new JsonObject()
            .put("eventType", "WORKFLOW_TYPE_CHANGED")
            .put("workflowType", "SINGLE");

        String output = captureOutput(event);
        assertTrue(output.contains("SINGLE") || output.contains("Mode"),
            "Output should contain the workflow type");
    }

    @Test
    @Timeout(5)
    void testWorkflowTypeChanged_missingType() throws Exception {
        JsonObject event = new JsonObject()
            .put("eventType", "WORKFLOW_TYPE_CHANGED");

        String output = captureOutput(event);
        assertNotNull(output);
    }

    @Test
    @Timeout(5)
    void testStepStarted() throws Exception {
        JsonObject event = new JsonObject()
            .put("eventType", "STEP_STARTED")
            .put("stepId", "step-1")
            .put("agentRole", "planner")
            .put("instruction", "Design the solution");

        String output = captureOutput(event);
        assertTrue(output.contains("PLANNER") || output.contains("planner"),
            "Output should contain agent role");
        assertTrue(output.contains("step-1"),
            "Output should contain step ID");
    }

    @Test
    @Timeout(5)
    void testStepStarted_defaultFields() throws Exception {
        JsonObject event = new JsonObject()
            .put("eventType", "STEP_STARTED");

        String output = captureOutput(event);
        assertNotNull(output);
    }

    @Test
    @Timeout(5)
    void testStepCompleted() throws Exception {
        JsonObject event = new JsonObject()
            .put("eventType", "STEP_COMPLETED")
            .put("agentRole", "writer")
            .put("durationMs", 1500L);

        String output = captureOutput(event);
        assertTrue(output.contains("WRITER") || output.contains("writer"),
            "Output should contain agent role");
        assertTrue(output.contains("1.5") || output.contains("s"),
            "Output should contain formatted duration");
    }

    @Test
    @Timeout(5)
    void testStepCompleted_defaultFields() throws Exception {
        JsonObject event = new JsonObject()
            .put("eventType", "STEP_COMPLETED");

        String output = captureOutput(event);
        assertNotNull(output);
    }

    @Test
    @Timeout(5)
    void testStepFailed() throws Exception {
        JsonObject event = new JsonObject()
            .put("eventType", "STEP_FAILED")
            .put("agentRole", "writer")
            .put("durationMs", 500L)
            .put("errorMessage", "Something went wrong");

        String output = captureOutput(event);
        assertTrue(output.contains("WRITER") || output.contains("writer"),
            "Output should contain agent role");
        assertTrue(output.contains("went wrong") || output.contains("error") || output.contains("failed"),
            "Output should contain error information");
    }

    @Test
    @Timeout(5)
    void testStepFailed_defaultFields() throws Exception {
        JsonObject event = new JsonObject()
            .put("eventType", "STEP_FAILED");

        String output = captureOutput(event);
        assertNotNull(output);
    }

    @Test
    @Timeout(5)
    void testToolCallStart() throws Exception {
        JsonObject event = new JsonObject()
            .put("eventType", "TOOL_CALL_START")
            .put("stepId", "step-1")
            .put("toolName", "write_file")
            .put("toolInput", "output/main.py");

        String output = captureOutput(event);
        assertTrue(output.contains("write_file"),
            "Output should contain tool name");
        assertTrue(output.contains("main.py") || output.contains("output"),
            "Output should contain tool input");
    }

    @Test
    @Timeout(5)
    void testToolCallStart_defaultFields() throws Exception {
        JsonObject event = new JsonObject()
            .put("eventType", "TOOL_CALL_START");

        String output = captureOutput(event);
        assertNotNull(output);
    }

    @Test
    @Timeout(5)
    void testToolCallResult() throws Exception {
        JsonObject event = new JsonObject()
            .put("eventType", "TOOL_CALL_RESULT")
            .put("stepId", "step-1")
            .put("toolName", "read_file")
            .put("toolInput", "src/Main.java")
            .put("durationMs", 42L);

        String output = captureOutput(event);
        assertTrue(output.contains("read_file"),
            "Output should contain tool name");
        assertTrue(output.contains("42") || output.contains("ms"),
            "Output should contain duration");
    }

    @Test
    @Timeout(5)
    void testToolCallResult_defaultFields() throws Exception {
        JsonObject event = new JsonObject()
            .put("eventType", "TOOL_CALL_RESULT");

        String output = captureOutput(event);
        assertNotNull(output);
    }

    @Test
    @Timeout(5)
    void testToolCallError() throws Exception {
        JsonObject event = new JsonObject()
            .put("eventType", "TOOL_CALL_ERROR")
            .put("stepId", "step-1")
            .put("toolName", "shell")
            .put("toolInput", "rm -rf /")
            .put("errorMessage", "Permission denied");

        String output = captureOutput(event);
        assertTrue(output.contains("shell"),
            "Output should contain tool name");
        assertTrue(output.contains("Permission denied") || output.contains("denied"),
            "Output should contain error message");
    }

    @Test
    @Timeout(5)
    void testToolCallError_defaultFields() throws Exception {
        JsonObject event = new JsonObject()
            .put("eventType", "TOOL_CALL_ERROR");

        String output = captureOutput(event);
        assertNotNull(output);
    }

    @Test
    @Timeout(5)
    void testAgentResponse_small() throws Exception {
        JsonObject event = new JsonObject()
            .put("eventType", "AGENT_RESPONSE")
            .put("stepId", "step-1")
            .put("responseLength", 500);

        String output = captureOutput(event);
        assertTrue(output.contains("500") || output.contains("B") || output.contains("bytes"),
            "Output should contain response size");
    }

    @Test
    @Timeout(5)
    void testAgentResponse_large() throws Exception {
        JsonObject event = new JsonObject()
            .put("eventType", "AGENT_RESPONSE")
            .put("stepId", "step-1")
            .put("responseLength", 2500);

        String output = captureOutput(event);
        assertTrue(output.contains("2") || output.contains("KB") || output.contains("kB"),
            "Output should contain KB response size");
    }

    @Test
    @Timeout(5)
    void testAgentResponse_defaultFields() throws Exception {
        JsonObject event = new JsonObject()
            .put("eventType", "AGENT_RESPONSE");

        String output = captureOutput(event);
        assertNotNull(output);
    }

    @Test
    @Timeout(5)
    void testWorkflowCompleted() throws Exception {
        JsonObject event = new JsonObject()
            .put("eventType", "WORKFLOW_COMPLETED")
            .put("status", "SUCCESS")
            .put("totalDurationMs", 5000L);

        String output = captureOutput(event);
        assertTrue(output.contains("Completed") || output.contains("SUCCESS") || output.contains("5"),
            "Output should contain completion status and duration");
    }

    @Test
    @Timeout(5)
    void testWorkflowCompleted_defaultFields() throws Exception {
        JsonObject event = new JsonObject()
            .put("eventType", "WORKFLOW_COMPLETED");

        String output = captureOutput(event);
        assertNotNull(output);
    }

    @Test
    @Timeout(5)
    void testWorkflowCompleted_failure() throws Exception {
        JsonObject event = new JsonObject()
            .put("eventType", "WORKFLOW_COMPLETED")
            .put("status", "FAILED")
            .put("totalDurationMs", 3000L);

        String output = captureOutput(event);
        assertTrue(output.contains("Completed") || output.contains("FAILED") || output.contains("3"),
            "Output should contain failure status and duration");
    }

    @Test
    @Timeout(5)
    void testUnknownEventType() throws Exception {
        JsonObject event = new JsonObject()
            .put("eventType", "UNKNOWN_TYPE");

        String output = captureOutput(event);
        assertEquals("", output, "Unknown event type should produce no output");
    }

    @Test
    @Timeout(5)
    void testMissingEventType() throws Exception {
        JsonObject event = new JsonObject();

        String output = captureOutput(event);
        assertEquals("", output, "Missing event type should produce no output");
    }

    @Test
    @Timeout(5)
    void testToolCallInputTruncation() throws Exception {
        String longInput = "A".repeat(300);
        JsonObject event = new JsonObject()
            .put("eventType", "TOOL_CALL_START")
            .put("toolName", "test_tool")
            .put("toolInput", longInput);

        String output = captureOutput(event);
        assertTrue(output.contains("test_tool"),
            "Output should contain tool name");
        assertTrue(output.contains("...") || output.length() < 500,
            "Long input should be truncated");
    }

    @Test
    @Timeout(5)
    void testWorkflowStarted_longDescription() throws Exception {
        String longDesc = "A".repeat(200);
        JsonObject event = new JsonObject()
            .put("eventType", "WORKFLOW_STARTED")
            .put("workflowType", "SINGLE")
            .put("taskDescription", longDesc);

        String output = captureOutput(event);
        // Description should be truncated to 100 chars
        assertTrue(output.contains("...") || output.length() < 300,
            "Long description should be truncated");
    }

    @Test
    @Timeout(5)
    void testMultipleEvents() throws Exception {
        JsonObject stepStart = new JsonObject()
            .put("eventType", "STEP_STARTED")
            .put("stepId", "step-1")
            .put("agentRole", "planner");
        JsonObject toolStart = new JsonObject()
            .put("eventType", "TOOL_CALL_START")
            .put("toolName", "write_file")
            .put("toolInput", "test.txt");
        JsonObject stepComplete = new JsonObject()
            .put("eventType", "STEP_COMPLETED")
            .put("agentRole", "planner")
            .put("durationMs", 100L);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(baos));
        try {
            onEventMethod.invoke(renderer, stepStart);
            onEventMethod.invoke(renderer, toolStart);
            onEventMethod.invoke(renderer, stepComplete);
        } finally {
            System.setOut(original);
        }

        String output = baos.toString();
        assertTrue(output.contains("PLANNER") || output.contains("planner"));
        assertTrue(output.contains("write_file"));
    }
}
