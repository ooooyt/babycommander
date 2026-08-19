package com.ooooyt.babycommander.db.entity;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Index;
import java.time.Instant;

@Entity
public class ConversationEntity {

    @Id
    public long objectBoxId;

    /** Business ID (UUID string) */
    @Index
    public String id;

    @Index
    public String sessionId;

    public String workspace;

    public String agentRole;

    public String provider;

    /** Epoch millisecond timestamp */
    public long createdAt;

    /** Epoch millisecond timestamp */
    public long updatedAt;

    public ConversationEntity() {
    }

    public Instant getCreatedAtInstant() {
        return createdAt != 0 ? Instant.ofEpochMilli(createdAt) : null;
    }

    public void setCreatedAtInstant(Instant instant) {
        this.createdAt = instant != null ? instant.toEpochMilli() : 0;
    }

    public Instant getUpdatedAtInstant() {
        return updatedAt != 0 ? Instant.ofEpochMilli(updatedAt) : null;
    }

    public void setUpdatedAtInstant(Instant instant) {
        this.updatedAt = instant != null ? instant.toEpochMilli() : 0;
    }
}
