package com.ooooyt.babycommander.tool;

import org.jboss.logging.Logger;

/**
 * Dedicated logger that writes the <b>complete</b> tool-call arguments and
 * tool execution results to a separate log file ({@code logs/tool-calls.log}),
 * keeping them out of the main {@code app.log}.
 *
 * <p>Routing is configured in {@code application.properties}: the log category
 * {@code tool-calls} is bound to the {@code TOOL_CALLS} file handler with
 * {@code use-parent-handlers=false}, so these records never propagate to the
 * root logger (and therefore never reach {@code app.log} or the console).
 *
 * <p>Unlike the status events shown to the user (which truncate long output),
 * this logger records the full, untruncated arguments and result of every
 * tool invocation, which is invaluable for debugging agent behavior.
 */
public final class ToolCallLogger {

    /** Log category routed to the dedicated tool-calls file handler. */
    public static final String CATEGORY = "tool-calls";

    private static final Logger LOG = Logger.getLogger(CATEGORY);
    private static final String LS = System.lineSeparator();

    private ToolCallLogger() {
    }

    /**
     * Logs one complete tool-call record: tool name, full arguments JSON,
     * full result text, and execution duration.
     *
     * @param toolName   the name of the tool that was invoked
     * @param arguments  the complete arguments JSON as produced by the LLM
     * @param result     the complete tool result (or error message)
     * @param durationMs wall-clock execution time in milliseconds
     */
    public static void log(String toolName, String arguments, String result, long durationMs) {
        if (!LOG.isInfoEnabled()) {
            return;
        }
        StringBuilder sb = new StringBuilder(256 + safeLen(arguments) + safeLen(result));
        sb.append("==== TOOL CALL: ").append(toolName).append(" ====").append(LS);
        sb.append("Arguments: ").append(arguments).append(LS);
        sb.append("---- Result (").append(durationMs).append(" ms) ----").append(LS);
        sb.append(result).append(LS);
        sb.append("==== END ").append(toolName).append(" ====");
        LOG.info(sb.toString());
    }

    private static int safeLen(String s) {
        return s == null ? 4 : s.length();
    }
}
