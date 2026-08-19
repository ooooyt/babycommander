package com.ooooyt.babycommander.db.repository;

import com.ooooyt.babycommander.db.entity.TaskToolExecutionEntity;
import com.ooooyt.babycommander.db.entity.TaskToolExecutionEntity_;
import io.objectbox.Box;
import io.objectbox.BoxStore;
import io.objectbox.query.QueryBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class TaskToolExecutionRepository {

    @Inject
    BoxStore boxStore;

    private Box<TaskToolExecutionEntity> box() {
        return boxStore.boxFor(TaskToolExecutionEntity.class);
    }

    public TaskToolExecutionEntity findById(String id) {
        return box().query()
                .equal(TaskToolExecutionEntity_.id, id, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build()
                .findUnique();
    }

    public List<TaskToolExecutionEntity> findByTaskId(String taskId) {
        return box().query()
                .equal(TaskToolExecutionEntity_.taskId, taskId, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build()
                .find();
    }

    public List<TaskToolExecutionEntity> findByTaskIdOrdered(String taskId) {
        return box().query()
                .equal(TaskToolExecutionEntity_.taskId, taskId, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .order(TaskToolExecutionEntity_.updatedDatetime)
                .build()
                .find();
    }

    public List<TaskToolExecutionEntity> findAll() {
        return box().getAll();
    }

    public void save(TaskToolExecutionEntity entity) {
        box().put(entity);
    }

    public void update(TaskToolExecutionEntity entity) {
        box().put(entity);
    }

    public void delete(TaskToolExecutionEntity entity) {
        box().remove(entity);
    }
}
