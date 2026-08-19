package com.ooooyt.babycommander.db.serializer;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessageRecord(
    String type,
    String text,
    String thinking,
    List<ToolRequestRecord> toolExecutionRequests,
    String toolCallId,
    String toolName
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolRequestRecord(
        String id,
        String name,
        String arguments
    ) {}
}
