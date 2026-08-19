package com.ooooyt.babycommander.db.entity;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Index;
import java.time.Instant;

@Entity
public class MessageEntity {

    @Id
    public long objectBoxId;

    /** Business ID (UUID string) */
    @Index
    public String id;

    /** Foreign key: conversation business ID */
    @Index
    public String conversationId;

    public int sequenceNumber;

    public String role;

    public String content;

    public String toolCallsJson;

    public String toolCallExecutionJson;

    /** Epoch millisecond timestamp */
    public long timestamp;

    public boolean summarized;

    public Integer tokenCount;

    public MessageEntity() {
    }

    public Instant getTimestampInstant() {
        return timestamp != 0 ? Instant.ofEpochMilli(timestamp) : null;
    }

    public void setTimestampInstant(Instant instant) {
        this.timestamp = instant != null ? instant.toEpochMilli() : 0;
    }
}
