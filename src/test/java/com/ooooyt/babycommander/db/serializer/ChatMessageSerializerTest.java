package com.ooooyt.babycommander.db.serializer;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChatMessageSerializerTest {

    @Test
    void serializeAndDeserializeUserMessage() {
        UserMessage original = UserMessage.userMessage("Hello, world!");
        String json = ChatMessageSerializer.toJson(original);
        ChatMessage restored = ChatMessageSerializer.fromJson(json);
        assertTrue(restored instanceof UserMessage);
        assertEquals("Hello, world!", ((UserMessage) restored).singleText());
    }

    @Test
    void serializeAndDeserializeSystemMessage() {
        SystemMessage original = SystemMessage.systemMessage("You are helpful");
        String json = ChatMessageSerializer.toJson(original);
        ChatMessage restored = ChatMessageSerializer.fromJson(json);
        assertTrue(restored instanceof SystemMessage);
        assertEquals("You are helpful", ((SystemMessage) restored).text());
    }

    @Test
    void serializeAndDeserializeAiMessageWithoutToolCalls() {
        AiMessage original = AiMessage.aiMessage("I can help with that");
        String json = ChatMessageSerializer.toJson(original);
        ChatMessage restored = ChatMessageSerializer.fromJson(json);
        assertTrue(restored instanceof AiMessage);
        assertEquals("I can help with that", ((AiMessage) restored).text());
        assertFalse(((AiMessage) restored).hasToolExecutionRequests());
    }

    @Test
    void serializeAndDeserializeAiMessageWithToolCalls() {
        ToolExecutionRequest req = ToolExecutionRequest.builder()
            .id("call-1")
            .name("read_file")
            .arguments("{\"path\":\"test.txt\"}")
            .build();
        AiMessage original = AiMessage.aiMessage("Let me read the file", List.of(req));
        String json = ChatMessageSerializer.toJson(original);
        ChatMessage restored = ChatMessageSerializer.fromJson(json);
        assertTrue(restored instanceof AiMessage);
        AiMessage restoredAi = (AiMessage) restored;
        assertEquals("Let me read the file", restoredAi.text());
        assertTrue(restoredAi.hasToolExecutionRequests());
        assertEquals(1, restoredAi.toolExecutionRequests().size());
        assertEquals("call-1", restoredAi.toolExecutionRequests().get(0).id());
        assertEquals("read_file", restoredAi.toolExecutionRequests().get(0).name());
    }

    @Test
    void serializeAndDeserializeToolExecutionResultMessage() {
        ToolExecutionResultMessage original =
            ToolExecutionResultMessage.from("call-1", "read_file", "file content here");
        String json = ChatMessageSerializer.toJson(original);
        ChatMessage restored = ChatMessageSerializer.fromJson(json);
        assertTrue(restored instanceof ToolExecutionResultMessage);
        ToolExecutionResultMessage restoredTool = (ToolExecutionResultMessage) restored;
        assertEquals("call-1", restoredTool.id());
        assertEquals("read_file", restoredTool.toolName());
        assertEquals("file content here", restoredTool.text());
    }

    @Test
    void roundTripMultipleMessages() {
        ChatMessage[] originals = new ChatMessage[]{
            SystemMessage.systemMessage("prompt"),
            UserMessage.userMessage("do something"),
            AiMessage.aiMessage("calling tool"),
            ToolExecutionResultMessage.from("c1", "tool", "result"),
            AiMessage.aiMessage("done"),
        };
        for (ChatMessage original : originals) {
            String json = ChatMessageSerializer.toJson(original);
            ChatMessage restored = ChatMessageSerializer.fromJson(json);
            assertEquals(original.type(), restored.type(),
                "Type mismatch for " + original.getClass().getSimpleName());
        }
    }
}
