package com.ooooyt.babycommander.tool.mcp;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.function.Function;

@Getter
@AllArgsConstructor
public class McpToolAdapter {

    private final String serverId;
    private final String toolName;
    private final String description;
    @Getter(AccessLevel.NONE) private final Function<String, String> executeFn;

    /**
     * Entry point invoked by the LLM. The {@link Tool} annotation is what makes
     * this adapter discoverable: {@code AgentFactory.buildToolExecutorMap()}
     * discovers tools via {@code ToolSpecifications.toolSpecificationsFrom(tool)},
     * which only picks up {@code @Tool}-annotated methods.
     */
    @Tool("Call an MCP tool from the configured MCP server")
    public String callMcpTool(@P("The JSON-RPC request payload to send to the MCP tool") String input) {
        return executeFn.apply(input);
    }
}