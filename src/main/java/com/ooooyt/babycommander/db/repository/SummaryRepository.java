package com.ooooyt.babycommander.db.repository;

import com.ooooyt.babycommander.db.entity.SummaryEntity;
import com.ooooyt.babycommander.db.entity.SummaryEntity_;
import io.objectbox.Box;
import io.objectbox.BoxStore;
import io.objectbox.query.QueryBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class SummaryRepository {

    @Inject
    BoxStore boxStore;

    private Box<SummaryEntity> box() {
        return boxStore.boxFor(SummaryEntity.class);
    }

    public List<SummaryEntity> findByConversationOrdered(String conversationId) {
        return box().query()
                .equal(SummaryEntity_.conversationId, conversationId, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .order(SummaryEntity_.messageRangeStart)
                .build()
                .find();
    }

    public void save(SummaryEntity entity) {
        box().put(entity);
    }

    public long countByConversation(String conversationId) {
        return box().query()
                .equal(SummaryEntity_.conversationId, conversationId, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build()
                .count();
    }
}
