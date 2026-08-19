package com.ooooyt.babycommander.ui.tui;

import org.jline.utils.AttributedStyle;

/**
 * A single chat/log line rendered in the right-hand pane.
 *
 * <p>{@code source} is the canonical text (used for matching/removal),
 * {@code rendered} is what is drawn (markdown-stripped), {@code style}
 * optionally overrides the default markdown styling, and
 * {@code defaultFg} is the fallback foreground colour used when
 * {@link DisplayTextUtils#stampBg} stamps a default-styled cell.
 */
record ChatMessage(String source, String rendered, AttributedStyle style, int defaultFg) {

    ChatMessage(String source, String rendered) {
        this(source, rendered, null, 235);
    }

    ChatMessage(String source, String rendered, AttributedStyle style) {
        this(source, rendered, style, 235);
    }
}
