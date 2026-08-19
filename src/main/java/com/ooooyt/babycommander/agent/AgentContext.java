package com.ooooyt.babycommander.agent;

import dev.langchain4j.memory.ChatMemory;

import java.util.List;

public record AgentContext(
    String sessionId,
    CodegenAgent agent,
    String projectFolder,
    String role,
    List<Object> tools,
    ChatMemory chatMemory
) {
    public static AgentContext of(String sessionId, CodegenAgent agent, String projectFolder, String role, List<Object> tools, ChatMemory chatMemory) {
        return new AgentContext(sessionId, agent, projectFolder, role, tools, chatMemory);
    }
}
