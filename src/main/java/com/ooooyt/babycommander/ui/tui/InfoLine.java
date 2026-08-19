package com.ooooyt.babycommander.ui.tui;

import org.jline.utils.AttributedStyle;

/**
 * A single line of "plan/info" text drawn in the left-hand pane.
 * The one-argument convenience constructor applies the default body style.
 */
record InfoLine(String text, AttributedStyle style) {

    InfoLine(String text) {
        this(text, TerminalTheme.ST_L_BODY);
    }
}
