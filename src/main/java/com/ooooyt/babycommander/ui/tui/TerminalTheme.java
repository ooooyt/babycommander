package com.ooooyt.babycommander.ui.tui;

import org.jline.utils.AttributedStyle;

/**
 * Centralised terminal colour palette and {@link AttributedStyle} definitions
 * plus small formatting helpers shared across the TUI components.
 *
 * <p>Extracted from {@code TerminalUIAdapter} to keep style configuration in a
 * single place. All members are package-private and stateless.
 */
final class TerminalTheme {

    private TerminalTheme() {}

    static final int C_BG_LEFT  = 236;
    static final int C_BG_RIGHT = 240;
    static final int C_BG_INPUT = 238;

    static final AttributedStyle ST_L_TITLE = AttributedStyle.DEFAULT
            .bold().foreground(AttributedStyle.CYAN).background(C_BG_LEFT);
    static final AttributedStyle ST_L_BODY  = AttributedStyle.DEFAULT
            .foreground(252).background(C_BG_LEFT);
    static final AttributedStyle ST_L_SEPARATOR = AttributedStyle.DEFAULT
            .foreground(252).background(C_BG_LEFT + 1);

    static final AttributedStyle ST_R_BG = AttributedStyle.DEFAULT
        .foreground(235).background(C_BG_RIGHT);
    static final AttributedStyle ST_R_TITLE = AttributedStyle.DEFAULT
            .bold().foreground(AttributedStyle.YELLOW).background(C_BG_RIGHT);
    static final AttributedStyle ST_INPUT   = AttributedStyle.DEFAULT
            .foreground(231).background(C_BG_INPUT);
    static final AttributedStyle ST_PROMPT  = AttributedStyle.DEFAULT
            .bold().foreground(AttributedStyle.CYAN).background(C_BG_INPUT);

    /** Dim cyan background for the selected row in the slash-command popup. */
    static final int C_BG_POPUP_SELECTED = 24; // 256-color darker dim cyan
    /** Highlight style for the selected row in the slash-command popup. */
    static final AttributedStyle ST_POPUP_SELECTED = AttributedStyle.DEFAULT
            .bold().foreground(AttributedStyle.BLACK).background(C_BG_POPUP_SELECTED);
    /** Highlight style for the selected command name in the popup. */
    static final AttributedStyle ST_POPUP_SELECTED_NAME = AttributedStyle.DEFAULT
            .bold().foreground(AttributedStyle.YELLOW).background(C_BG_POPUP_SELECTED);

    static final AttributedStyle ST_USER_INPUT    = AttributedStyle.DEFAULT
            .foreground(33);
    static final AttributedStyle ST_TOOL          = AttributedStyle.DEFAULT
            .foreground(AttributedStyle.CYAN);
    static final AttributedStyle ST_TOOL_SUCCESS  = AttributedStyle.DEFAULT
            .foreground(AttributedStyle.GREEN);
    static final AttributedStyle ST_TOOL_ERROR    = AttributedStyle.DEFAULT
            .foreground(3);   // yellow/brown
    static final AttributedStyle ST_WARN          = AttributedStyle.DEFAULT
            .foreground(3);   // yellow/brown (ASK_ONCE)
    static final AttributedStyle ST_DANGER        = AttributedStyle.DEFAULT
            .foreground(AttributedStyle.RED);

    static final AttributedStyle ST_PLAN_ACTIVE    = AttributedStyle.DEFAULT
            .foreground(208).background(C_BG_LEFT);
    static final AttributedStyle ST_PLAN_COMPLETED = AttributedStyle.DEFAULT
            .foreground(AttributedStyle.GREEN).background(C_BG_LEFT);
    static final AttributedStyle ST_PLAN_FAILED    = AttributedStyle.DEFAULT
            .foreground(AttributedStyle.RED).background(C_BG_LEFT);
    static final AttributedStyle ST_PLAN_PENDING   = AttributedStyle.DEFAULT
            .foreground(252).background(C_BG_LEFT);
    static final AttributedStyle ST_PLAN_HEADER    = AttributedStyle.DEFAULT
            .bold().foreground(252).background(C_BG_LEFT);

    /** Orange highlight for the active dot in the thinking in-progress bar. */
    static final AttributedStyle ST_DOT_ACTIVE = AttributedStyle.DEFAULT
            .foreground(208).background(C_BG_LEFT + 1);
    /** Dim colour for the inactive dots in the thinking in-progress bar. */
    static final AttributedStyle ST_DOT_PENDING = AttributedStyle.DEFAULT
            .foreground(244).background(C_BG_LEFT + 1);

    /** Grey style for the time lines (thinking... / thought in Xs / done in Xs).
     *  The grey is deliberately darker than the chat panel background (240) so
     *  the time lines stay clearly visible against it. */
    static final AttributedStyle ST_TIME = AttributedStyle.DEFAULT
            .foreground(102);

    static String formatDuration(long ms) {
        if (ms < 1000) {
            return String.format("  \u23F1  done in 0.%ds", ms / 100);
        }
        long sec = ms / 1000;
        long millis = ms % 1000;
        if (sec < 60) {
            return String.format("  \u23F1  done in %d.%ds", sec, millis / 100);
        }
        long min = sec / 60;
        sec = sec % 60;
        return String.format("  \u23F1  done in %dm %ds", min, sec);
    }

    static String formatThinking(long ms) {
        if (ms < 1000) {
            return String.format("  \u23F1  thought in 0.%ds", ms / 100);
        }
        long sec = ms / 1000;
        long millis = ms % 1000;
        if (sec < 60) {
            return String.format("  \u23F1  thought in %d.%ds", sec, millis / 100);
        }
        long min = sec / 60;
        sec = sec % 60;
        return String.format("  \u23F1  thought in %dm %ds", min, sec);
    }
}
