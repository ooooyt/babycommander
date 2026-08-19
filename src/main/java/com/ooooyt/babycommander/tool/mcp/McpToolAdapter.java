package com.ooooyt.babycommander.tool.mcp;

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

    public String callMcpTool(String input) {
        return executeFn.apply(input);
    }
}
