package com.ooooyt.babycommander.tool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link ResilientToolExecutor} can repair common malformed JSON
 * that LLMs occasionally emit for tool-call arguments (e.g. a missing comma
 * between array elements), which previously surfaced as a JsonParseException
 * that aborted the whole agent session. It also verifies that semantically
 * wrong but syntactically valid arguments (e.g. a {@code String[]} element that
 * is an object) are caught and turned into a corrective message instead of a
 * MismatchedInputException that aborts the session.
 */
class ResilientToolExecutorTest {

    /**
     * A sample tool with a list-of-strings argument, matching the failure seen
     * in the logs where an array was missing a separating comma.
     */
    public static class SampleTool {
        public String process(String[] items) {
            return "ok:" + items.length;
        }
    }

    @Test
    void repairsMissingCommaInArray() {
        SampleTool tool = new SampleTool();
        // The LLM emitted an array with a missing comma: ["a" "b"] instead of ["a", "b"].
        ToolExecutionRequest malformed = ToolExecutionRequest.builder()
                .name("process")
                .arguments("{\"items\":[\"a\" \"b\"]}")
                .build();

        ResilientToolExecutor executor = new ResilientToolExecutor(tool, malformed);
        String result = executor.execute(malformed, null);

        // The executor should repair the JSON and delegate successfully.
        assertNotNull(result);
        assertTrue(result.startsWith("ok:"), "Expected a successful tool result, but got: " + result);
    }

    @Test
    void repairsTrailingCommaInArray() {
        SampleTool tool = new SampleTool();
        ToolExecutionRequest malformed = ToolExecutionRequest.builder()
                .name("process")
                .arguments("{\"items\":[\"a\", \"b\",]}")
                .build();

        ResilientToolExecutor executor = new ResilientToolExecutor(tool, malformed);
        String result = executor.execute(malformed, null);

        assertNotNull(result);
        assertTrue(result.startsWith("ok:"), "Expected a successful tool result, but got: " + result);
    }

    @Test
    void passesThroughValidJson() {
        SampleTool tool = new SampleTool();
        ToolExecutionRequest valid = ToolExecutionRequest.builder()
                .name("process")
                .arguments("{\"items\":[\"a\", \"b\", \"c\"]}")
                .build();

        ResilientToolExecutor executor = new ResilientToolExecutor(tool, valid);
        String result = executor.execute(valid, null);

        assertNotNull(result);
        assertTrue(result.startsWith("ok:"), "Expected a successful tool result, but got: " + result);
    }

    @Test
    void returnsCorrectiveErrorWhenArrayElementHasWrongType() {
        SampleTool tool = new SampleTool();
        // The LLM emitted a String[] whose first element is a JSON object
        // instead of a string. This JSON is syntactically valid, so the
        // executor delegates it to DefaultToolExecutor, which fails with a
        // MismatchedInputException. The executor must catch that and return a
        // corrective message instead of letting it abort the agent run.
        ToolExecutionRequest malformed = ToolExecutionRequest.builder()
                .name("process")
                .arguments("{\"items\":[{\"foo\":\"bar\"}, \"b\"]}")
                .build();

        ResilientToolExecutor executor = new ResilientToolExecutor(tool, malformed);
        String result = executor.execute(malformed, null);

        assertNotNull(result);
        assertTrue(result.startsWith("Error:"), "Expected a corrective error, but got: " + result);
        assertTrue(result.contains("process"), "Error should name the tool: " + result);
        assertTrue(result.contains("types"), "Error should mention the type mismatch: " + result);
    }
}
