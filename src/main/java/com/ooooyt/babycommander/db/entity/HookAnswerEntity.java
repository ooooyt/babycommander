package com.ooooyt.babycommander.db.entity;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Index;
import java.time.Instant;

/**
 * Stores user's answer to hook (security check) confirmation prompts.
 * Only persists answers for DangerLevel.ASK_ONCE operations where the user
 * responded with 'y' (ALLOW), 'n' (DENY), or 'a' (ALLOW_ALWAYS).
 * <p>
 * For DANGEROUS operations, no record is stored — the user must confirm every time.
 */
@Entity
public class HookAnswerEntity {

    @Id
    public long objectBoxId;

    /** Session ID associated with this answer */
    @Index
    public String sessionId;

    /** Tool name (e.g. "FileSystemTool") */
    @Index
    public String toolName;

    /** Method name (e.g. "writeFile") */
    @Index
    public String methodName;

    /** Full argument signature built by SessionMemory.buildSignature() */
    public String argsSignature;

    /** The user's answer: ALLOW, DENY, or ALLOW_ALWAYS */
    public String answerType;

    /** Epoch millisecond timestamp */
    public long createdDatetime;

    public HookAnswerEntity() {
    }

    public HookAnswerEntity(String sessionId, String toolName, String methodName,
                            String argsSignature, String answerType) {
        this.sessionId = sessionId;
        this.toolName = toolName;
        this.methodName = methodName;
        this.argsSignature = argsSignature;
        this.answerType = answerType;
        this.createdDatetime = Instant.now().toEpochMilli();
    }

    public Instant getCreatedDatetimeInstant() {
        return createdDatetime != 0 ? Instant.ofEpochMilli(createdDatetime) : null;
    }

    public void setCreatedDatetimeInstant(Instant instant) {
        this.createdDatetime = instant != null ? instant.toEpochMilli() : 0;
    }
}
