package com.ooooyt.babycommander.db.serializer;

import com.ooooyt.babycommander.db.serializer.ChatMessageRecord.ToolRequestRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import java.util.List;
import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;

public final class ChatMessageSerializer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ChatMessageSerializer() {}

    public static String toJson(ChatMessage message) {
        if (message == null) {
            throw new IllegalArgumentException(I18n.tr(MessageKey.SERIALIZER_MESSAGE_NULL));
        }
        ChatMessageRecord record = toRecord(message);
        try {
            return MAPPER.writeValueAsString(record);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(I18n.tr(MessageKey.SERIALIZER_SERIALIZE_FAILED, message.getClass().getSimpleName(), e.getMessage()), e);
        }
    }

    public static ChatMessage fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException(I18n.tr(MessageKey.SERIALIZER_JSON_NULL));
        }
        try {
            ChatMessageRecord record = MAPPER.readValue(json, ChatMessageRecord.class);
            return toMessage(record);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(I18n.tr(MessageKey.SERIALIZER_DESERIALIZE_FAILED, e.getMessage()), e);
        }
    }

    /**
     * Validates that the given JSON string can be successfully deserialized back to a ChatMessage.
     * This is a round-trip validation to catch corrupt/truncated JSON before it gets stored.
     *
     * @param json the JSON string to validate
     * @return true if the JSON is valid and can be deserialized
     */
    public static boolean isValidJson(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            ChatMessageRecord record = MAPPER.readValue(json, ChatMessageRecord.class);
            return record != null;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    private static ChatMessageRecord toRecord(ChatMessage message) {
        return switch (message) {
            case UserMessage um -> new ChatMessageRecord("user", um.singleText(), null, null, null, null);
            case AiMessage ai -> {
                List<ToolRequestRecord> requests = null;
                if (ai.hasToolExecutionRequests()) {
                    requests = ai.toolExecutionRequests().stream()
                        .map(req -> new ToolRequestRecord(req.id(), req.name(), req.arguments()))
                        .toList();
                }
                yield new ChatMessageRecord("ai", ai.text(), ai.thinking(), requests, null, null);
            }
            case ToolExecutionResultMessage t ->
                new ChatMessageRecord("tool_result", t.text(), null, null, t.id(), t.toolName());
            case SystemMessage sm -> new ChatMessageRecord("system", sm.text(), null, null, null, null);
            default -> throw new IllegalArgumentException(I18n.tr(MessageKey.SERIALIZER_UNKNOWN_MESSAGE_TYPE, message.getClass().getName()));
        };
    }

    private static AiMessage toAiMessage(ChatMessageRecord record) {
        AiMessage.Builder builder = AiMessage.builder().text(record.text());
        if (record.thinking() != null && !record.thinking().isBlank()) {
            builder.thinking(record.thinking());
        }
        if (record.toolExecutionRequests() != null && !record.toolExecutionRequests().isEmpty()) {
            List<ToolExecutionRequest> requests = record.toolExecutionRequests().stream()
                .map(r -> ToolExecutionRequest.builder()
                    .id(r.id())
                    .name(r.name())
                    .arguments(r.arguments())
                    .build())
                .toList();
            builder.toolExecutionRequests(requests);
        }
        return builder.build();
    }

    private static ChatMessage toMessage(ChatMessageRecord record) {
        return switch (record.type()) {
            case "user" -> UserMessage.userMessage(record.text());
            case "ai" -> toAiMessage(record);
            case "tool_result" ->
                ToolExecutionResultMessage.from(record.toolCallId(), record.toolName(), record.text());
            case "system" -> SystemMessage.systemMessage(record.text());
            default -> throw new IllegalArgumentException(I18n.tr(MessageKey.SERIALIZER_UNKNOWN_RECORD_TYPE, record.type()));
        };
    }
}
