package com.ooooyt.babycommander.tool;

import com.ooooyt.babycommander.hook.HookManager;
import com.ooooyt.babycommander.status.StatusEventContext;
import com.ooooyt.babycommander.status.StatusEventPublisher;

import java.util.function.Supplier;

/**
 * Abstract base class for all tools that publish status events.
 * <p>
 * Implements the Template Method pattern to eliminate duplicated boilerplate
 * in tool methods. Each concrete tool class only needs to:
 * <ul>
 *   <li>Call {@link #executeWithStatus(String, String, Object, Supplier)} inside its {@code @Tool} method</li>
 *   <li>Provide the actual business logic via the {@code Supplier} lambda</li>
 * </ul>
 * <p>
 * The template method handles:
 * <ul>
 *   <li>Calling {@link HookManager#beforeToolCall} for security hooks</li>
 *   <li>Publishing {@code toolCallStart}, {@code toolCallResult}, and {@code toolCallError} events</li>
 *   <li>Exception handling and error message formatting</li>
 * </ul>
 */
public abstract class BaseTool {

    protected final HookManager hookManager;

    protected BaseTool(HookManager hookManager) {
        this.hookManager = hookManager;
    }

    /**
     * Truncates a string to the specified maximum number of characters,
     * ensuring it does not split a Unicode surrogate pair (i.e. it cuts
     * at a valid code point boundary).
     *
     * @param str     the string to truncate
     * @param maxLen  the maximum number of characters (Java char units) to keep
     * @return the truncated string, or the original if {@code str.length() <= maxLen}
     */
    protected static String truncateSafely(String str, int maxLen) {
        if (str == null || str.length() <= maxLen) {
            return str;
        }
        // Ensure we don't split a surrogate pair: if the last char is a high surrogate,
        // back up by one to include the full pair (or exclude it entirely).
        int end = maxLen;
        if (end > 0 && Character.isHighSurrogate(str.charAt(end - 1))) {
            end--;
        }
        return str.substring(0, end);
    }

    /**
     * Truncates a StringBuilder to the specified maximum number of characters,
     * ensuring it does not split a Unicode surrogate pair.
     *
     * @param sb      the StringBuilder to truncate
     * @param maxLen  the maximum number of characters (Java char units) to keep
     */
    protected static void truncateSafely(StringBuilder sb, int maxLen) {
        if (sb == null || sb.length() <= maxLen) {
            return;
        }
        // Ensure we don't split a surrogate pair
        int end = maxLen;
        if (end > 0 && Character.isHighSurrogate(sb.charAt(end - 1))) {
            end--;
        }
        sb.setLength(end);
    }

    /**
     * Template method that wraps a tool execution with status event publishing.
     * <p>
     * The flow is:
     * <ol>
     *   <li>Call {@code beforeToolCall} for security/confirmation hooks</li>
     *   <li>If no {@link StatusEventPublisher} is available, execute directly</li>
     *   <li>Otherwise, publish start event, execute, publish result or error event</li>
     * </ol>
     *
     * @param toolName   the tool class name (e.g. "FileSystemTool")
     * @param actionName the action name for event publishing (e.g. "write_file")
     * @param toolInput  the input to record in events (typically the first argument)
     * @param action     the actual business logic to execute
     * @param <T>        the return type
     * @return the result of the action, or an error message string on failure
     */
    protected <T> T executeWithStatus(String toolName, String actionName, Object toolInput, Supplier<T> action) {
        beforeToolCall(toolName, actionName, new Object[]{toolInput});
        StatusEventPublisher pub = StatusEventContext.get();
        if (pub == null) {
            return action.get();
        }
        String inputStr = toolInput != null ? toolInput.toString() : "";
        pub.toolCallStart(Thread.currentThread().getName(), actionName, inputStr);
        long start = System.currentTimeMillis();
        try {
            T result = action.get();
            pub.toolCallResult(Thread.currentThread().getName(), actionName, inputStr,
                    result != null ? result.toString() : "", System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            pub.toolCallError(Thread.currentThread().getName(), actionName, inputStr, e.getMessage());
            @SuppressWarnings("unchecked")
            T errorResult = (T) formatError(e);
            return errorResult;
        }
    }

    /**
     * Overload for methods that take multiple arguments. The {@code toolInput} is used
     * for event publishing, while {@code args} is passed to {@code beforeToolCall}.
     */
    protected <T> T executeWithStatus(String toolName, String methodName, Object toolInput, Object[] args, Supplier<T> action) {
        beforeToolCall(toolName, methodName, args);
        StatusEventPublisher pub = StatusEventContext.get();
        if (pub == null) {
            return action.get();
        }
        String inputStr = toolInput != null ? toolInput.toString() : "";
        pub.toolCallStart(Thread.currentThread().getName(), methodName, inputStr);
        long start = System.currentTimeMillis();
        try {
            T result = action.get();
            pub.toolCallResult(Thread.currentThread().getName(), methodName, inputStr,
                    result != null ? result.toString() : "", System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            pub.toolCallError(Thread.currentThread().getName(), methodName, inputStr, e.getMessage());
            @SuppressWarnings("unchecked")
            T errorResult = (T) formatError(e);
            return errorResult;
        }
    }

    /**
     * Hook method called before each tool execution. Subclasses can override
     * to add pre-processing logic. The default implementation delegates to
     * {@link HookManager#beforeToolCall} if available.
     */
    protected void beforeToolCall(String toolName, String methodName, Object[] args) {
        if (hookManager != null) {
            hookManager.beforeToolCall(toolName, methodName, args);
        }
    }

    /**
     * Hook method to format an exception into an error string.
     * Subclasses can override to provide custom error formatting.
     */
    protected String formatError(Exception e) {
        return "Error: " + e.getMessage();
    }
}
