package com.ooooyt.babycommander.db.repository;

import com.ooooyt.babycommander.db.entity.MessageEntity;
import com.ooooyt.babycommander.db.entity.MessageEntity_;
import io.objectbox.Box;
import io.objectbox.BoxStore;
import io.objectbox.query.QueryBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class MessageRepository {

    @Inject
    BoxStore boxStore;

    private Box<MessageEntity> box() {
        return boxStore.boxFor(MessageEntity.class);
    }

    public MessageEntity findById(String id) {
        return box().query()
                .equal(MessageEntity_.id, id, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build()
                .findUnique();
    }

    public List<MessageEntity> findByConversation(String conversationId) {
        return box().query()
                .equal(MessageEntity_.conversationId, conversationId, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .order(MessageEntity_.sequenceNumber)
                .build()
                .find();
    }

    public List<MessageEntity> findRecentRaw(String conversationId, int limit) {
        return box().query()
                .equal(MessageEntity_.conversationId, conversationId, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .equal(MessageEntity_.summarized, false)
                .orderDesc(MessageEntity_.sequenceNumber)
                .build()
                .find(0, limit);
    }

    public List<MessageEntity> findOldestUnsummarized(String conversationId, int limit) {
        return box().query()
                .equal(MessageEntity_.conversationId, conversationId, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .equal(MessageEntity_.summarized, false)
                .order(MessageEntity_.sequenceNumber)
                .build()
                .find(0, limit);
    }

    public long countRawUnsummarized(String conversationId) {
        return box().query()
                .equal(MessageEntity_.conversationId, conversationId, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .equal(MessageEntity_.summarized, false)
                .build()
                .count();
    }

    public int nextSequenceNumber(String conversationId) {
        MessageEntity last = box().query()
                .equal(MessageEntity_.conversationId, conversationId, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .orderDesc(MessageEntity_.sequenceNumber)
                .build()
                .findFirst();
        return last == null ? 1 : last.sequenceNumber + 1;
    }

    public void save(MessageEntity entity) {
        box().put(entity);
    }

    public void saveAll(List<MessageEntity> entities) {
        box().put(entities);
    }

    public void markAsSummarized(List<String> ids) {
        if (ids.isEmpty()) return;
        for (String id : ids) {
            MessageEntity entity = findById(id);
            if (entity != null) {
                entity.summarized = true;
                box().put(entity);
            }
        }
    }
}
