package com.ooooyt.babycommander.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.ooooyt.babycommander.db.entity.TaskToolExecutionEntity;
import com.ooooyt.babycommander.service.ProjectTaskService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import io.quarkus.logging.Log;

/**
 * A {@link ToolExecutor} that wraps langchain4j's default executor and makes it
 * resilient to malformed tool-call arguments that LLMs occasionally produce.
 *
 * <p>Two distinct failure modes are handled:</p>
 * <ol>
 *   <li><b>Syntax errors</b> — the arguments JSON is not valid (for example a
 *   missing comma between array elements, which surfaces as
 *   {@code JsonParseException: Unexpected character ... was expecting comma}).
 *   The wrapper attempts to repair common, well-defined JSON syntax mistakes
 *   before delegating to the underlying executor.</li>
 *   <li><b>Semantic/type errors</b> — the arguments JSON is syntactically valid
 *   but does not match the tool parameter types (for example a {@code String[]}
 *   element that is a JSON object, which surfaces as
 *   {@code MismatchedInputException: Cannot deserialize value of type
 *   java.lang.String from Object value}). langchain4j's {@link Json} codec wraps
 *   this checked exception in a {@link RuntimeException}, which would otherwise
 *   abort the whole agent session. The wrapper catches that and returns a
 *   descriptive, corrective message to the LLM so it can re-issue the call with
 *   correctly typed values.</li>
 * </ol>
 *
 * <p>In both cases the default executor would otherwise abort the entire agent
 * session. By returning an error string (the tool-result channel) instead of
 * throwing, the agent loop can continue and let the model self-correct.</p>
 *
 * <p>When a {@link ProjectTaskService} is available and a current task is active
 * (see {@link TaskContext}), every tool call is also persisted to the database as
 * a {@link TaskToolExecutionEntity}. The tool name is stored verbatim (never
 * truncated, since it is important for accurate attribution), while the arguments
 * and result are truncated to {@link #MAX_STORED_FIELD_LENGTH} characters.</p>
 */
public class ResilientToolExecutor implements ToolExecutor {

    /** Maximum length of stored tool-arguments/result fields (tool name is not truncated). */
    static final int MAX_STORED_FIELD_LENGTH = 200;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ToolExecutor delegate;

    /** Optional service used to persist tool executions; null when unavailable. */
    private final ProjectTaskService projectTaskService;

    /**
     * Builds a resilient executor for the given tool instance and the request
     * that produced it. The delegate is a {@link DefaultToolExecutor} bound to
     * the same request so tool execution semantics are unchanged.
     * <p>
     * Tool execution persistence is disabled (no {@link ProjectTaskService}).
     */
    public ResilientToolExecutor(Object tool, ToolExecutionRequest request) {
        this(tool, request, null);
    }

    /**
     * Builds a resilient executor with optional tool-execution persistence.
     *
     * @param tool                 the tool instance
     * @param request              the request that produced the tool
     * @param projectTaskService   service used to persist tool executions, or null to disable
     */
    public ResilientToolExecutor(Object tool, ToolExecutionRequest request,
                                 ProjectTaskService projectTaskService) {
        this.delegate = new DefaultToolExecutor(tool, request);
        this.projectTaskService = projectTaskService;
    }

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        // Every tool call (with complete arguments and complete result) is
        // recorded to the dedicated tool-calls log file for debugging.
        long start = System.currentTimeMillis();

        // Persist the tool execution (STARTED) if a task is active and a service
        // is available. The tool name is never truncated.
        String executionId = recordStart(request);

        String result;
        try {
            result = executeInternal(request, memoryId);
        } catch (RuntimeException e) {
            ToolCallLogger.log(request.name(), request.arguments(),
                    "EXCEPTION: " + e, System.currentTimeMillis() - start);
            recordEnd(executionId, request.name(), "EXCEPTION: " + e,
                    TaskToolExecutionEntity.Status.FAILED);
            throw e;
        }
        ToolCallLogger.log(request.name(), request.arguments(), result,
                System.currentTimeMillis() - start);
        recordEnd(executionId, request.name(), result,
                TaskToolExecutionEntity.Status.COMPLETED);
        return result;
    }

    /**
     * Creates a STARTED tool-execution record for the given tool call, returning
     * its business ID (or null if persistence is disabled or no task is active).
     */
    private String recordStart(ToolExecutionRequest request) {
        if (projectTaskService == null) {
            return null;
        }
        String taskId = TaskContext.getCurrentTaskId();
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        try {
            String toolInfo = buildToolInfo(request.name(), request.arguments(), null);
            return projectTaskService.createToolExecution(taskId, toolInfo).id;
        } catch (Exception e) {
            Log.debugf("ResilientToolExecutor: failed to record tool execution start: %s", e.getMessage());
            return null;
        }
    }

    /**
     * Updates a tool-execution record to its final status and result.
     */
    private void recordEnd(String executionId, String toolName, String result,
                           TaskToolExecutionEntity.Status status) {
        if (executionId == null || projectTaskService == null) {
            return;
        }
        try {
            String toolInfo = buildToolInfo(toolName, null, result);
            projectTaskService.updateToolExecution(executionId, toolInfo, status);
        } catch (Exception e) {
            Log.debugf("ResilientToolExecutor: failed to record tool execution end: %s", e.getMessage());
        }
    }

    /**
     * Builds the stored {@code toolInfo} string. The tool name is stored verbatim;
     * the arguments and result are truncated to {@link #MAX_STORED_FIELD_LENGTH}.
     */
    private static String buildToolInfo(String toolName, String arguments, String result) {
        StringBuilder sb = new StringBuilder("tool=").append(toolName);
        if (arguments != null) {
            sb.append(" args=").append(truncate(arguments));
        }
        if (result != null) {
            sb.append(" result=").append(truncate(result));
        }
        return sb.toString();
    }

    /** Truncates the given string to {@link #MAX_STORED_FIELD_LENGTH} characters. */
    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_STORED_FIELD_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_STORED_FIELD_LENGTH);
    }

    private String executeInternal(ToolExecutionRequest request, Object memoryId) {
        // Try to parse the arguments as-is; if valid, delegate without touching them.
        if (isValidJson(request.arguments())) {
            return executeSafely(request, memoryId);
        }

        // Attempt to repair common LLM JSON mistakes.
        String repaired = repairJson(request.arguments());
        if (repaired != null && isValidJson(repaired)) {
            Log.warnf("Repaired malformed tool-call arguments for tool '%s': %s",
                    request.name(), request.arguments());
            ToolExecutionRequest repairedRequest = ToolExecutionRequest.builder()
                    .id(request.id())
                    .name(request.name())
                    .arguments(repaired)
                    .build();
            return executeSafely(repairedRequest, memoryId);
        }

        // Cannot repair — return a clear, actionable error to the LLM so it can
        // retry with valid JSON rather than crashing the whole agent session.
        String message = "Tool call arguments for tool '%s' are not valid JSON and could not be "
                + "auto-repaired. Please re-issue the tool call with valid JSON arguments. "
                + "Received: %s";
        Log.errorf("Unrepairable tool-call arguments for tool '%s': %s", request.name(), request.arguments());
        return "Error: " + String.format(message, request.name(), request.arguments());
    }

    /**
     * Delegates to the underlying executor but intercepts argument-coercion
     * failures. langchain4j's {@link DefaultToolExecutor} coerces each JSON
     * argument to the tool parameter's declared type. When the JSON is
     * syntactically valid but semantically wrong (for example a {@code String[]}
     * element that is a JSON object), Jackson's {@link MismatchedInputException}
     * is thrown and then wrapped in a {@link RuntimeException} by langchain4j's
     * {@code Json} codec; enum/numeric range violations throw
     * {@link IllegalArgumentException}. All of these are unchecked exceptions
     * that would otherwise propagate out of {@code ToolExecutor.execute()} and
     * abort the whole agent session. Instead we return a descriptive, corrective
     * message to the LLM so it can re-issue the tool call with correct types.
     */
    private String executeSafely(ToolExecutionRequest request, Object memoryId) {
        try {
            return delegate.execute(request, memoryId);
        } catch (RuntimeException e) {
            // Unwrap the cause chain to find a Jackson type-mismatch, which is
            // the most actionable signal for the LLM.
            Throwable cause = e;
            while (cause != null) {
                if (cause instanceof MismatchedInputException mie) {
                    Log.warnf("Tool call arguments for tool '%s' had a type mismatch: %s. Arguments: %s",
                            request.name(), mie.getOriginalMessage(), request.arguments());
                    return "Error: The arguments provided for tool '" + request.name()
                            + "' did not match the expected parameter types. "
                            + "Please re-issue the tool call with correctly typed values. "
                            + "Details: " + mie.getOriginalMessage()
                            + ". Received: " + request.arguments();
                }
                cause = cause.getCause();
            }

            // Other coercion failures (enum/numeric bounds, method-not-found, etc.)
            Log.warnf("Tool call arguments for tool '%s' were rejected: %s. Arguments: %s",
                    request.name(), e.getMessage(), request.arguments());
            return "Error: The arguments provided for tool '" + request.name()
                    + "' were rejected during execution. "
                    + "Please re-issue the tool call with valid arguments. "
                    + "Details: " + e.getMessage()
                    + ". Received: " + request.arguments();
        }
    }

    private boolean isValidJson(String json) {
        if (json == null || json.isBlank()) {
            return true;
        }
        try {
            MAPPER.readTree(json);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    private String repairJson(String json) {
        // Simple heuristic repairs for common LLM JSON mistakes:
        // 1. Missing comma between array/object elements.
        // 2. Trailing comma before closing bracket.
        if (json == null) {
            return null;
        }
        String repaired = json;
        // Insert missing commas between a closing quote and an opening quote/brace/bracket.
        repaired = repaired.replaceAll("(\\S)(\\s+)([\\[\\{])", "$1,$2$3");
        repaired = repaired.replaceAll("([\"'])\\s+([\"'\\[\\{])", "$1,$2");
        // Remove trailing commas before } or ].
        repaired = repaired.replaceAll(",\\s*([}\\]])", "$1");
        return repaired;
    }
}
