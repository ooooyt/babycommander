package com.ooooyt.babycommander.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
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
 */
public class ResilientToolExecutor implements ToolExecutor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ToolExecutor delegate;

    /**
     * Builds a resilient executor for the given tool instance and the request
     * that produced it. The delegate is a {@link DefaultToolExecutor} bound to
     * the same request so tool execution semantics are unchanged.
     */
    public ResilientToolExecutor(Object tool, ToolExecutionRequest request) {
        this.delegate = new DefaultToolExecutor(tool, request);
    }

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
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

    private static boolean isValidJson(String json) {
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

    /**
     * Repairs common, well-defined JSON syntax errors produced by LLMs:
     * <ul>
     *   <li>missing commas between array/object entries (e.g. {@code ["a" "b"]})</li>
     *   <li>trailing commas before a closing bracket</li>
     * </ul>
     * Returns the repaired JSON string, or {@code null} if it cannot be repaired.
     */
    private static String repairJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        String candidate = fixMissingCommas(json);
        if (candidate == null) {
            return null;
        }
        candidate = removeTrailingCommas(candidate);
        return candidate;
    }

    /**
     * Inserts a comma where two values are adjacent inside an array or object
     * without a separating comma. This is done by scanning for a value that
     * terminates with a closing quote ({@code "}), {@code ]}, or {@code }} and
     * is immediately followed (ignoring whitespace) by the start of another
     * value ({@code "}, {@code [}, or {@code {}) — but not when the trailing
     * quote is an object key (followed by {@code :}).
     */
    private static String fixMissingCommas(String json) {
        StringBuilder sb = new StringBuilder(json.length() + 16);
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                sb.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                    // A string just closed. If the next non-whitespace char is the
                    // start of another value (not a key separator ':' and not a
                    // comma/closing bracket), a separating comma is missing.
                    int j = i + 1;
                    while (j < json.length() && Character.isWhitespace(json.charAt(j))) {
                        j++;
                    }
                    if (j < json.length()) {
                        char next = json.charAt(j);
                        if (next == '"' || next == '[' || next == '{') {
                            sb.append(',');
                        }
                    }
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                sb.append(c);
                continue;
            }
            sb.append(c);
            if (c == ']' || c == '}') {
                // Look ahead, skipping whitespace, for the start of another value.
                int j = i + 1;
                while (j < json.length() && Character.isWhitespace(json.charAt(j))) {
                    j++;
                }
                if (j < json.length()) {
                    char next = json.charAt(j);
                    if (next == '"' || next == '[' || next == '{') {
                        // Two values are adjacent without a comma — insert one.
                        sb.append(',');
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * Removes commas that appear immediately before a closing bracket, which is
     * invalid JSON but commonly emitted by LLMs (e.g. {@code [1, 2,]}).
     */
    private static String removeTrailingCommas(String json) {
        StringBuilder sb = new StringBuilder(json.length());
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                sb.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                sb.append(c);
                continue;
            }
            if (c == ',') {
                // Look ahead, skipping whitespace, for a closing bracket.
                int j = i + 1;
                while (j < json.length() && Character.isWhitespace(json.charAt(j))) {
                    j++;
                }
                if (j < json.length() && (json.charAt(j) == ']' || json.charAt(j) == '}')) {
                    // Skip this trailing comma.
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
