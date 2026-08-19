package com.ooooyt.babycommander.hook;

public record ToolCallInfo(
    String toolName,
    String methodName,
    Object[] args,
    DangerLevel level,
    String sessionId
) {
    /**
     * Returns a preview of the arguments.
     * Used for display in the confirmation prompt.
     */
    public String argsPreview() {
        if (args == null || args.length == 0) return "";
        return java.util.Arrays.stream(args)
                .map(a -> a == null ? "null" : sanitize(a.toString()))
                .collect(java.util.stream.Collectors.joining(", "));
    }

    /**
     * Replaces newlines, tabs, carriage returns, and other control characters
     * with a single space so the preview stays on one logical line and can be
     * soft-wrapped by the TUI renderer without leaking the argument's internal
     * line structure into the confirmation message.
     */
    private static String sanitize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t' || c == '\f') {
                sb.append(' ');
            } else if (c < 0x20 || c == 0x7f) {
                // strip other control characters entirely
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Returns the full, un-truncated arguments preview.
     * Alias for argsPreview() — both now return the full text.
     */
    public String fullArgsPreview() {
        return argsPreview();
    }
}
