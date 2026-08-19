package com.ooooyt.babycommander.model;

import java.time.Duration;
import java.util.List;

public record TaskResult(
    Status status,
    String result,
    String workspace,
    List<String> outputFiles,
    List<AgentExecution> agentExecutions
) {

    public enum Status {
        SUCCESS,
        FAILURE,
        TIMEOUT
    }

    public record AgentExecution(
        String agentId,
        String role,
        String input,
        String output,
        Duration duration
    ) {
        public static AgentExecution of(String agentId, String role, String input, String output, Duration duration) {
            return new AgentExecution(agentId, role, input, output, duration);
        }
    }

    public static TaskResult success(String workspace, String result) {
        return new TaskResult(Status.SUCCESS, result, workspace, List.of(), List.of());
    }

    public static TaskResult failure(String workspace, String error) {
        return new TaskResult(Status.FAILURE, error, workspace, List.of(), List.of());
    }
}
