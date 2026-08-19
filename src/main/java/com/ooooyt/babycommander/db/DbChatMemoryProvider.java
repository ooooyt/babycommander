package com.ooooyt.babycommander.db;

import com.ooooyt.babycommander.db.entity.ConversationEntity;
import com.ooooyt.babycommander.db.entity.ConversationEntity_;
import com.ooooyt.babycommander.db.entity.MessageEntity;
import com.ooooyt.babycommander.db.repository.MessageRepository;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import io.objectbox.Box;
import io.objectbox.BoxStore;
import io.objectbox.query.QueryBuilder;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class DbChatMemoryProvider implements ChatMemoryProvider {

    private final Map<Object, DbChatMemory> activeMemories = new ConcurrentHashMap<>();

    @Inject
    DbConfig dbConfig;

    @Inject
    MessageRepository messageRepository;

    @Inject
    BoxStore boxStore;

    @Override
    public ChatMemory get(Object memoryId) {
        return activeMemories.computeIfAbsent(memoryId, id -> {
            Box<ConversationEntity> box = boxStore.boxFor(ConversationEntity.class);

            ConversationEntity conversation = box.query()
                    .equal(ConversationEntity_.sessionId, id.toString(), QueryBuilder.StringOrder.CASE_SENSITIVE)
                    .build()
                    .findUnique();

            if (conversation == null) {
                conversation = new ConversationEntity();
                conversation.id = UUID.randomUUID().toString();
                conversation.sessionId = id.toString();
                conversation.workspace = "";
                conversation.agentRole = "";
                conversation.provider = "";
                conversation.createdAt = System.currentTimeMillis();
                conversation.updatedAt = System.currentTimeMillis();
                box.put(conversation);
                Log.infof("Created new conversation: id=%s session=%s", conversation.id, id);
            } else {
                Log.infof("Restored existing conversation: id=%s session=%s", conversation.id, id);
            }

            List<MessageEntity> recent = messageRepository.findRecentRaw(conversation.id, dbConfig.memory().maxMessages());

            recent = new java.util.ArrayList<>(recent);
            java.util.Collections.reverse(recent);

            DbChatMemory memory = new DbChatMemory(
                id,
                conversation,
                messageRepository
            );
            memory.loadInitialMessages(recent);
            return memory;
        });
    }

    public void remove(Object memoryId) {
        activeMemories.remove(memoryId);
    }
}
