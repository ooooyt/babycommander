package com.ooooyt.babycommander.db.repository;

import com.ooooyt.babycommander.db.entity.HookAnswerEntity;
import com.ooooyt.babycommander.db.entity.HookAnswerEntity_;
import io.objectbox.Box;
import io.objectbox.BoxStore;
import io.objectbox.query.QueryBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * Repository for persisting and querying user's hook confirmation answers.
 * Used by SessionMemory to persist decisions across application restarts.
 */
@ApplicationScoped
public class HookAnswerRepository {

    @Inject
    BoxStore boxStore;

    private Box<HookAnswerEntity> box() {
        return boxStore.boxFor(HookAnswerEntity.class);
    }

    /**
     * Find all answers for a given session.
     */
    public List<HookAnswerEntity> findBySession(String sessionId) {
        return box().query()
                .equal(HookAnswerEntity_.sessionId, sessionId, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build()
                .find();
    }

    /**
     * Find a specific answer by exact signature match.
     */
    public HookAnswerEntity findBySignature(String sessionId, String argsSignature) {
        return box().query()
                .equal(HookAnswerEntity_.sessionId, sessionId, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .equal(HookAnswerEntity_.argsSignature, argsSignature, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build()
                .findUnique();
    }

    /**
     * Find answers for a specific tool+method in a session.
     */
    public List<HookAnswerEntity> findByToolAndMethod(String sessionId, String toolName, String methodName) {
        return box().query()
                .equal(HookAnswerEntity_.sessionId, sessionId, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .equal(HookAnswerEntity_.toolName, toolName, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .equal(HookAnswerEntity_.methodName, methodName, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build()
                .find();
    }

    /**
     * Find ALLOW_ALWAYS answers for a specific tool+method in a session.
     */
    public HookAnswerEntity findAllowAlwaysByMethod(String sessionId, String toolName, String methodName) {
        return box().query()
                .equal(HookAnswerEntity_.sessionId, sessionId, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .equal(HookAnswerEntity_.toolName, toolName, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .equal(HookAnswerEntity_.methodName, methodName, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .equal(HookAnswerEntity_.answerType, "ALLOW_ALWAYS", QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build()
                .findUnique();
    }

    /**
     * Save a hook answer entity (insert or update).
     */
    public void save(HookAnswerEntity entity) {
        box().put(entity);
    }

    /**
     * Delete all answers for a session.
     */
    public void deleteBySession(String sessionId) {
        box().query()
                .equal(HookAnswerEntity_.sessionId, sessionId, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build()
                .remove();
    }

    /**
     * Delete a specific answer.
     */
    public void delete(HookAnswerEntity entity) {
        box().remove(entity);
    }
}
