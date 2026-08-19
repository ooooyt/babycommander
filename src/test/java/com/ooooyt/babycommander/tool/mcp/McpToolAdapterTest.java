package com.ooooyt.babycommander.tool.mcp;

import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class McpToolAdapterTest {

    @Test
    void testConstructorAndGetters() {
        Function<String, String> fn = input -> "result: " + input;
        McpToolAdapter adapter = new McpToolAdapter("server1", "myTool", "Does something", fn);

        assertEquals("server1", adapter.getServerId());
        assertEquals("myTool", adapter.getToolName());
        assertEquals("Does something", adapter.getDescription());
    }

    @Test
    void testCallMcpToolReturnsExecuteFnResult() {
        Function<String, String> fn = input -> "processed: " + input;
        McpToolAdapter adapter = new McpToolAdapter("s1", "t1", "desc", fn);

        String result = adapter.callMcpTool("hello");
        assertEquals("processed: hello", result);
    }

    @Test
    void testCallMcpToolWithEmptyInput() {
        Function<String, String> fn = input -> "ok";
        McpToolAdapter adapter = new McpToolAdapter("s1", "t1", "desc", fn);

        assertEquals("ok", adapter.callMcpTool(""));
    }

    @Test
    void testCallMcpToolWithNullInput() {
        Function<String, String> fn = input -> "null received";
        McpToolAdapter adapter = new McpToolAdapter("s1", "t1", "desc", fn);

        assertEquals("null received", adapter.callMcpTool(null));
    }

    @Test
    void testCallMcpToolWithLongInput() {
        Function<String, String> fn = input -> input;
        McpToolAdapter adapter = new McpToolAdapter("s1", "t1", "desc", fn);
        String longInput = "a".repeat(10000);

        String result = adapter.callMcpTool(longInput);

        assertEquals(longInput, result);
    }

    @Test
    void testDifferentServerIdsAreIndependent() {
        Function<String, String> fn1 = input -> "from fn1";
        Function<String, String> fn2 = input -> "from fn2";

        McpToolAdapter adapter1 = new McpToolAdapter("serverA", "tool1", "desc1", fn1);
        McpToolAdapter adapter2 = new McpToolAdapter("serverB", "tool2", "desc2", fn2);

        assertEquals("serverA", adapter1.getServerId());
        assertEquals("serverB", adapter2.getServerId());
        assertEquals("from fn1", adapter1.callMcpTool("x"));
        assertEquals("from fn2", adapter2.callMcpTool("x"));
    }

    @Test
    void testToolNameAndDescriptionCanContainSpecialChars() {
        Function<String, String> fn = input -> "ok";
        McpToolAdapter adapter = new McpToolAdapter("s1", "read-file", "Reads a file from the filesystem", fn);

        assertEquals("read-file", adapter.getToolName());
        assertEquals("Reads a file from the filesystem", adapter.getDescription());
    }

    @Test
    void testExecuteFnThrowsExceptionPropagates() {
        Function<String, String> fn = input -> {
            throw new RuntimeException("execution failed");
        };
        McpToolAdapter adapter = new McpToolAdapter("s1", "t1", "desc", fn);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> adapter.callMcpTool("input"));
        assertEquals("execution failed", ex.getMessage());
    }

    @Test
    void testCallMcpToolReturnsFunctionOutputDirectly() {
        // Verify that callMcpTool delegates directly to the executeFn without modification
        Function<String, String> fn = Function.identity();
        McpToolAdapter adapter = new McpToolAdapter("s1", "t1", "desc", fn);

        String result = adapter.callMcpTool("some input");
        assertSame("some input", result);
    }
}
