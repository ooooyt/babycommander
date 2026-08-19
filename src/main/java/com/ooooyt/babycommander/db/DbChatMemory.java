package com.ooooyt.babycommander.db;

import com.ooooyt.babycommander.db.entity.ConversationEntity;
import com.ooooyt.babycommander.db.entity.MessageEntity;
import com.ooooyt.babycommander.db.repository.MessageRepository;
import com.ooooyt.babycommander.db.serializer.ChatMessageSerializer;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import io.quarkus.logging.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class DbChatMemory implements ChatMemory {

    private final Object memoryId;
    private final ConversationEntity conversation;
    private final MessageRepository messageRepository;

    private final List<ChatMessage> messageBuffer = new ArrayList<>();

    public DbChatMemory(Object memoryId, ConversationEntity conversation,
                        MessageRepository messageRepository) {
        this.memoryId = memoryId;
        this.conversation = conversation;
        this.messageRepository = messageRepository;
    }

    public void loadInitialMessages(List<MessageEntity> entities) {
        messageBuffer.clear();
        for (MessageEntity e : entities) {
            try {
                messageBuffer.add(ChatMessageSerializer.fromJson(e.content));
            } catch (RuntimeException ex) {
                Log.errorf("Corrupt message entity %s (conv=%s seq=%d): %s",
                    e.id, e.conversationId, e.sequenceNumber, ex.getMessage());
                Log.debugf("Corrupt content (first 200 chars): %s",
                    e.content != null ? e.content.substring(0, Math.min(200, e.content.length())) : "null");
                // Replace corrupt message with a placeholder to preserve conversation continuity
                String placeholder = String.format(
                    "[corrupt message %s at sequence %d - content lost due to deserialization error]",
                    e.id, e.sequenceNumber);
                messageBuffer.add(dev.langchain4j.data.message.SystemMessage.systemMessage(placeholder));
                // Repair the corrupt entity in the database so it won't fail on subsequent loads
                repairCorruptEntity(e);
            }
        }
    }

    private void repairCorruptEntity(MessageEntity e) {
        try {
            e.content = ChatMessageSerializer.toJson(
                SystemMessage.systemMessage(
                    "[repaired-corrupt] " + e.id + " at sequence " + e.sequenceNumber));
            messageRepository.save(e);
            Log.infof("Repaired corrupt message entity %s in database", e.id);
        } catch (Exception repairEx) {
            Log.errorf("Failed to repair corrupt message entity %s: %s", e.id, repairEx.getMessage());
        }
    }

    @Override
    public Object id() {
        return memoryId;
    }

    @Override
    public void add(ChatMessage message) {
        // Serialize to JSON first so we can validate it before persisting
        String json = ChatMessageSerializer.toJson(message);

        // Validate that the JSON can be round-tripped before storing
        if (!ChatMessageSerializer.isValidJson(json)) {
            Log.errorf("Refusing to store message that produces invalid JSON: type=%s, jsonPreview=%s",
                message.getClass().getSimpleName(),
                json.substring(0, Math.min(200, json.length())));
            // Still add to in-memory buffer so the conversation can continue,
            // but don't persist corrupt data to the database
            messageBuffer.add(message);
            return;
        }

        MessageEntity entity = toEntity(message);
        entity.conversationId = conversation.id;
        entity.sequenceNumber = messageRepository.nextSequenceNumber(conversation.id);
        entity.timestamp = System.currentTimeMillis();
        entity.summarized = false;
        entity.content = json;

        messageRepository.save(entity);
        messageBuffer.add(message);
    }

    @Override
    public List<ChatMessage> messages() {
        removeOrphanedToolResults();

        return new ArrayList<>(messageBuffer);
    }

    @Override
    public void clear() {
        messageBuffer.clear();
        List<MessageEntity> all = messageRepository.findByConversation(conversation.id);
        List<String> ids = all.stream().map(e -> e.id).toList();
        if (!ids.isEmpty()) {
            messageRepository.markAsSummarized(ids);
        }
    }

    public int countToolCalls() {
        int count = 0;
        for (ChatMessage msg : messageBuffer) {
            if (msg instanceof AiMessage aiMsg && aiMsg.hasToolExecutionRequests()) {
                count += aiMsg.toolExecutionRequests().size();
            }
        }
        return count;
    }

    private MessageEntity toEntity(ChatMessage message) {
        MessageEntity entity = new MessageEntity();
        entity.id = UUID.randomUUID().toString();

        switch (message) {
            case dev.langchain4j.data.message.UserMessage _ -> entity.role = "USER";
            case AiMessage _ -> entity.role = "ASSISTANT";
            case ToolExecutionResultMessage _ -> entity.role = "TOOL";
            case null, default -> entity.role = "SYSTEM";
        }

        return entity;
    }

    private void removeOrphanedToolResults() {
        Set<String> activeToolCallIds = new HashSet<>();
        for (ChatMessage msg : messageBuffer) {
            if (msg instanceof AiMessage aiMsg && aiMsg.hasToolExecutionRequests()) {
                for (var req : aiMsg.toolExecutionRequests()) {
                    activeToolCallIds.add(req.id());
                }
            }
        }
        List<Integer> toRemove = new ArrayList<>();
        for (int i = 0; i < messageBuffer.size(); i++) {
            ChatMessage msg = messageBuffer.get(i);
            if (msg instanceof ToolExecutionResultMessage trm
                    && !activeToolCallIds.contains(trm.id())) {
                toRemove.add(i);
            }
        }
        for (int i = toRemove.size() - 1; i >= 0; i--) {
            messageBuffer.remove((int) toRemove.get(i));
        }
    }
}
