package com.ooooyt.babycommander.db.repository;

import com.ooooyt.babycommander.db.entity.ProjectEntity;
import com.ooooyt.babycommander.db.entity.ProjectEntity_;
import io.objectbox.Box;
import io.objectbox.BoxStore;
import io.objectbox.query.QueryBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class ProjectRepository {

    @Inject
    BoxStore boxStore;

    private Box<ProjectEntity> box() {
        return boxStore.boxFor(ProjectEntity.class);
    }

    public ProjectEntity findById(String id) {
        return box().query()
                .equal(ProjectEntity_.id, id, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build()
                .findUnique();
    }

    public List<ProjectEntity> findAll() {
        return box().getAll();
    }

    public ProjectEntity findByName(String name) {
        return box().query()
                .equal(ProjectEntity_.name, name, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build()
                .findFirst();
    }

    public List<ProjectEntity> findByPath(String path) {
        return box().query()
                .equal(ProjectEntity_.path, path, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build()
                .find();
    }

    public List<ProjectEntity> findActiveByPath(String path) {
        return box().query()
                .equal(ProjectEntity_.path, path, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .equal(ProjectEntity_.active, true)
                .build()
                .find();
    }

    public void save(ProjectEntity entity) {
        box().put(entity);
    }

    public void update(ProjectEntity entity) {
        box().put(entity);
    }

    public void deactivate(ProjectEntity entity) {
        entity.active = false;
        box().put(entity);
    }

    public void delete(ProjectEntity entity) {
        box().remove(entity);
    }
}
