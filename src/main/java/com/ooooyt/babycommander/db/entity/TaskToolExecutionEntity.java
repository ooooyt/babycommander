package com.ooooyt.babycommander.db.entity;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Index;
import java.time.Instant;

@Entity
public class TaskToolExecutionEntity {

    public enum Status {
        STARTED, FAILED, COMPLETED
    }

    @Id
    public long objectBoxId;

    /** Business ID (UUID string) */
    @Index
    public String id;

    /** Foreign key: task business ID */
    @Index
    public String taskId;

    public String toolInfo;

    public String status;

    /** Epoch millisecond timestamp */
    public long updatedDatetime;

    public TaskToolExecutionEntity() {
    }

    public Instant getUpdatedDatetimeInstant() {
        return updatedDatetime != 0 ? Instant.ofEpochMilli(updatedDatetime) : null;
    }

    public void setUpdatedDatetimeInstant(Instant instant) {
        this.updatedDatetime = instant != null ? instant.toEpochMilli() : 0;
    }
}
