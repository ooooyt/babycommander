package com.ooooyt.babycommander.db.repository;

import com.ooooyt.babycommander.db.entity.TaskEntity;
import com.ooooyt.babycommander.db.entity.TaskEntity_;
import io.objectbox.Box;
import io.objectbox.BoxStore;
import io.objectbox.query.QueryBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class TaskRepository {

    @Inject
    BoxStore boxStore;

    private Box<TaskEntity> box() {
        return boxStore.boxFor(TaskEntity.class);
    }

    public TaskEntity findById(String id) {
        return box().query()
                .equal(TaskEntity_.id, id, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build()
                .findUnique();
    }

    public List<TaskEntity> findByProjectId(String projectId) {
        return box().query()
                .equal(TaskEntity_.projectId, projectId, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build()
                .find();
    }

    public List<TaskEntity> findByProjectIdOrdered(String projectId) {
        return box().query()
                .equal(TaskEntity_.projectId, projectId, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .orderDesc(TaskEntity_.createdDatetime)
                .build()
                .find();
    }

    /**
     * Find tasks by project ID, ordered by creation time descending,
     * with name containing the given keyword (case-insensitive).
     */
    public List<TaskEntity> findByProjectIdAndNameContaining(String projectId, String keyword) {
        return box().query()
                .equal(TaskEntity_.projectId, projectId, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .contains(TaskEntity_.name, keyword, QueryBuilder.StringOrder.CASE_INSENSITIVE)
                .orderDesc(TaskEntity_.createdDatetime)
                .build()
                .find();
    }

    /**
     * Find tasks by project ID, ordered by cosine similarity to the given query vector
     * using ObjectBox's built-in HNSW vector index.
     *
     * @param projectId  the project ID to scope the search
     * @param queryVector the float array embedding vector to search by
     * @param maxResults  maximum number of results to return
     * @return tasks ordered by similarity (most similar first)
     */
    public List<TaskEntity> findNearestNeighbors(String projectId, float[] queryVector, int maxResults) {
        return box().query()
                .equal(TaskEntity_.projectId, projectId, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .apply(TaskEntity_.embedding.nearestNeighbors(queryVector, maxResults))
                .build()
                .find();
    }

    public List<TaskEntity> findAll() {
        return box().getAll();
    }

    public void save(TaskEntity entity) {
        box().put(entity);
    }

    public void update(TaskEntity entity) {
        box().put(entity);
    }

    public void delete(TaskEntity entity) {
        box().remove(entity);
    }
}
