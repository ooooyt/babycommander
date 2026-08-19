package com.ooooyt.babycommander.db.entity;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Index;
import java.time.Instant;

@Entity
public class SummaryEntity {

    @Id
    public long objectBoxId;

    /** Business ID (UUID string) */
    @Index
    public String id;

    /** Foreign key: conversation business ID */
    @Index
    public String conversationId;

    public int messageRangeStart;

    public int messageRangeEnd;

    public String summaryText;

    /** Epoch millisecond timestamp */
    public long createdAt;

    public int messageCount;

    public SummaryEntity() {
    }

    public Instant getCreatedAtInstant() {
        return createdAt != 0 ? Instant.ofEpochMilli(createdAt) : null;
    }

    public void setCreatedAtInstant(Instant instant) {
        this.createdAt = instant != null ? instant.toEpochMilli() : 0;
    }
}
