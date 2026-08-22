package com.ooooyt.babycommander.db.entity;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Index;
import java.time.Instant;

/**
 * A single phase of a task plan, persisted alongside the owning {@link TaskEntity}.
 * <p>
 * Phases carry the same lifecycle statuses used in-memory by {@code PlanTool}
 * ({@code active}, {@code pending}, {@code completed}, {@code failed}). Because
 * in-memory phases are identified only by their position within the plan, the
 * {@code phaseIndex} (0-based) is used to map a persisted phase back to its
 * in-memory counterpart when status updates arrive on the event bus.
 */
@Entity
public class PhaseEntity {

    @Id
    public long objectBoxId;

    /** Business ID (UUID string) */
    @Index
    public String id;

    /** Foreign key: task business ID */
    @Index
    public String taskId;

    /** 0-based position of the phase within the plan */
    public int phaseIndex;

    public String title;

    public String description;

    /** One of: active, pending, completed, failed */
    public String status;

    /** Epoch millisecond timestamp */
    public long createdDatetime;

    /** Epoch millisecond timestamp */
    public long updatedDatetime;

    public PhaseEntity() {
    }

    public Instant getCreatedDatetimeInstant() {
        return createdDatetime != 0 ? Instant.ofEpochMilli(createdDatetime) : null;
    }

    public void setCreatedDatetimeInstant(Instant instant) {
        this.createdDatetime = instant != null ? instant.toEpochMilli() : 0;
    }

    public Instant getUpdatedDatetimeInstant() {
        return updatedDatetime != 0 ? Instant.ofEpochMilli(updatedDatetime) : null;
    }

    public void setUpdatedDatetimeInstant(Instant instant) {
        this.updatedDatetime = instant != null ? instant.toEpochMilli() : 0;
    }
}
