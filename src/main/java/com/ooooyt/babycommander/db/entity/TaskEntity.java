package com.ooooyt.babycommander.db.entity;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.HnswIndex;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Index;
import io.objectbox.annotation.VectorDistanceType;
import java.time.Instant;

@Entity
public class TaskEntity {

    public enum Status {
        STARTED, FAILED, COMPLETED
    }

    @Id
    public long objectBoxId;

    /** Business ID (UUID string) */
    @Index
    public String id;

    /** Foreign key: project business ID */
    @Index
    public String projectId;

    public String name;

    /** Epoch millisecond timestamp */
    public long createdDatetime;

    public String status;

    /** Epoch millisecond timestamp */
    public long updatedDatetime;

    /**
     * Vector embedding of the task name for semantic search.
     * Indexed with HNSW for fast approximate nearest neighbor search.
     * Using COSINE distance type for semantic similarity.
     */
    @HnswIndex(
        dimensions = 1536,
        distanceType = VectorDistanceType.COSINE,
        neighborsPerNode = 30,
        indexingSearchCount = 200
    )
    public float[] embedding;

    public TaskEntity() {
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
