package com.ooooyt.babycommander.db.repository;

import com.ooooyt.babycommander.db.entity.PhaseEntity;
import com.ooooyt.babycommander.db.entity.PhaseEntity_;
import io.objectbox.Box;
import io.objectbox.BoxStore;
import io.objectbox.query.QueryBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * Repository for {@link PhaseEntity}, mirroring the pattern used by
 * {@link TaskToolExecutionRepository}.
 */
@ApplicationScoped
public class PhaseRepository {

    @Inject
    BoxStore boxStore;

    private Box<PhaseEntity> box() {
        return boxStore.boxFor(PhaseEntity.class);
    }

    public PhaseEntity findById(String id) {
        return box().query()
                .equal(PhaseEntity_.id, id, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build()
                .findUnique();
    }

    public List<PhaseEntity> findByTaskId(String taskId) {
        return box().query()
                .equal(PhaseEntity_.taskId, taskId, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build()
                .find();
    }

    public List<PhaseEntity> findByTaskIdOrdered(String taskId) {
        return box().query()
                .equal(PhaseEntity_.taskId, taskId, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .order(PhaseEntity_.phaseIndex)
                .build()
                .find();
    }

    public List<PhaseEntity> findAll() {
        return box().getAll();
    }

    public void save(PhaseEntity entity) {
        box().put(entity);
    }

    public void update(PhaseEntity entity) {
        box().put(entity);
    }

    public void delete(PhaseEntity entity) {
        box().remove(entity);
    }
}
