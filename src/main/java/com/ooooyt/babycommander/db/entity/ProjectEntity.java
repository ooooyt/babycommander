package com.ooooyt.babycommander.db.entity;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Index;
import java.time.Instant;

@Entity
public class ProjectEntity {

    @Id
    public long objectBoxId;

    /** Business ID (UUID string) */
    @Index
    public String id;

    public String name;

    public String path;

    public String languages;

    public boolean active = true;

    /** Epoch millisecond timestamp */
    public long createdDatetime;

    public ProjectEntity() {
    }

    public Instant getCreatedDatetimeInstant() {
        return createdDatetime != 0 ? Instant.ofEpochMilli(createdDatetime) : null;
    }

    public void setCreatedDatetimeInstant(Instant instant) {
        this.createdDatetime = instant != null ? instant.toEpochMilli() : 0;
    }
}
