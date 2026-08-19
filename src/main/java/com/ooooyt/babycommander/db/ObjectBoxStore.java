package com.ooooyt.babycommander.db;

import com.ooooyt.babycommander.db.entity.MyObjectBox;
import io.objectbox.BoxStore;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.nio.file.Paths;

@ApplicationScoped
public class ObjectBoxStore {

    private BoxStore store;

    @Inject
    DbConfig dbConfig;

    @PostConstruct
    void init() {
        String dbDir = dbConfig.directory();
        Path dbPath = Paths.get(dbDir);
        dbPath.toFile().mkdirs();

        store = MyObjectBox.builder()
                .directory(dbPath.toFile())
                .build();
    }

    @Produces
    @ApplicationScoped
    public BoxStore produceBoxStore() {
        return store;
    }

    @PreDestroy
    void destroy() {
        if (store != null) {
            store.close();
        }
    }
}
