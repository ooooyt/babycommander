package com.ooooyt.babycommander.hook;

import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;
public class ToolDeniedException extends RuntimeException {
    public ToolDeniedException(String toolName) {
        super(I18n.tr(MessageKey.TOOL_DENIED_MSG, toolName));
    }
}
