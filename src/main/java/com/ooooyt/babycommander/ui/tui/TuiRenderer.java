package com.ooooyt.babycommander.ui.tui;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure terminal painting: the two-pane display region and the multi-line
 * input box.
 *
 * <p>Extracted verbatim from {@code TerminalUIAdapter}. All reads go through
 * the shared {@link TuiModel} (snapshots taken under the same monitors as
 * before) and all layout helpers ({@code leftW}/{@code rightW}/{@code bodyH}/
 * {@code inputRow}/{@code tw}) are delegated to the model so geometry stays
 * single-sourced.
 */
final class TuiRenderer {

    private final TuiModel model;

    TuiRenderer(TuiModel model) {
        this.model = model;
    }

    void fullRedraw(PrintWriter pw) throws Exception {
        drawDisplayRegion(pw);
        drawSlashPopup(pw);
        drawInputLine(pw);
        pw.flush();
    }
    void drawDisplayRegion(PrintWriter pw) throws Exception {
        int lw = model.leftW();
        int rw = model.rightW();
        int bh = model.bodyH();

        List<InfoLine> infoSnapshot;
        synchronized (model.infoLines) {
            infoSnapshot = List.copyOf(model.infoLines);
        }

        List<AttributedString> renderedLines = new ArrayList<>();
        List<ChatMessage> snapshot;
        synchronized (model.chatMessages) {
            snapshot = List.copyOf(model.chatMessages);
        }
        for (ChatMessage msg : snapshot) {
            if (msg.style() != null) {
                AttributedStringBuilder ab = new AttributedStringBuilder();
                ab.style(msg.style()).append(msg.rendered());
                for (var line : MarkdownRenderer.wrapLine(ab.toAttributedString(), rw - 1)) {
                    renderedLines.add(DisplayTextUtils.stampBg(line, rw, msg.defaultFg()));
                }
            } else {
                for (var line : MarkdownRenderer.render(msg.rendered(), rw - 1)) {
                    renderedLines.add(DisplayTextUtils.stampBg(line, rw, msg.defaultFg()));
                }
            }
        }

        if (model.autoScroll) {
            model.chatScroll = Integer.MAX_VALUE;
            model.autoScroll = false;
        }
        int maxScroll = Math.max(0, renderedLines.size() - (bh + 1));
        model.chatScroll = Math.max(0, Math.min(model.chatScroll, maxScroll));

        List<AttributedString> rows = new ArrayList<>(bh + 2);

        // Row 1: Title bar
        {
            AttributedStringBuilder ab = new AttributedStringBuilder();
            String title = model.chatEngineReady ? I18n.tr(MessageKey.TUI_TITLE) : I18n.tr(MessageKey.TUI_TITLE_LOADING);
            ab.style(TerminalTheme.ST_L_TITLE).append(DisplayTextUtils.fill(title, lw));
            ab.style(TerminalTheme.ST_R_TITLE).append(" ".repeat(Math.max(0, rw)));
            rows.add(ab.toAttributedString());
        }

        // Row 2: Left separator (lighter bg). When the LLM is thinking, show the
        // animated in-progress dot bar under the logo; otherwise it stays blank
        // (replacing any previously shown dots with spaces once the thought shows).
        {
            AttributedStringBuilder ab = new AttributedStringBuilder();
            if (model.thinking) {
                appendThinkingBar(ab, lw);
            } else {
                ab.style(TerminalTheme.ST_L_SEPARATOR).append(" ".repeat(Math.max(0, lw)));
            }
            ab.style(AttributedStyle.DEFAULT);
            int lineIdx = 0 + model.chatScroll;
            if (lineIdx < renderedLines.size()) {
                ab.append(renderedLines.get(lineIdx));
            } else {
                ab.style(TerminalTheme.ST_R_BG).append(" ".repeat(Math.max(0, rw)));
            }
            rows.add(ab.toAttributedString());
        }

        // Rows 3+: Content (left info starting from 0, right content starting from 1)
        for (int r = 0; r < bh; r++) {
            AttributedStringBuilder ab = new AttributedStringBuilder();

            if (r < infoSnapshot.size()) {
                // Plan info section
                InfoLine info = infoSnapshot.get(r);
                ab.style(info.style()).append(DisplayTextUtils.fill(" " + info.text(), lw));
            } else {
                ab.style(TerminalTheme.ST_L_BODY).append(" ".repeat(Math.max(0, lw)));
            }

            ab.style(AttributedStyle.DEFAULT);
            int lineIdx = r + 1 + model.chatScroll;
            if (lineIdx < renderedLines.size()) {
                ab.append(renderedLines.get(lineIdx));
            } else {
                ab.style(TerminalTheme.ST_R_BG).append(" ".repeat(Math.max(0, rw)));
            }

            rows.add(ab.toAttributedString());
        }

        while (rows.size() < bh + 2) {
            AttributedStringBuilder ab = new AttributedStringBuilder();
            ab.style(TerminalTheme.ST_L_BODY).append(" ".repeat(Math.max(0, lw)));
            ab.style(TerminalTheme.ST_R_BG  ).append(" ".repeat(Math.max(0, rw)));
            rows.add(ab.toAttributedString());
        }

        for (int i = 0; i < rows.size(); i++) {
            // Position cursor, reset style, and erase to end of line so no
            // stale character from a previous frame survives at the last column
            // when a row's rendered width is off by one (e.g. wide/emoji chars).
            // Erase with the right panel background (not default) so any tail
            // not covered by the row content stays the panel colour instead of
            // showing a dark "pit".
            pw.print("\033[" + (i + 1) + ";1H\033[0m\033[48;5;" + TerminalTheme.C_BG_RIGHT + "m\033[K");
            pw.print(rows.get(i).toAnsi());
        }
    }

    /**
     * Number of dots in the thinking in-progress bar. Fixed at 5; the bar is
     * right-aligned against the left panel's right edge.
     */
    static int thinkingBarStars() {
        return 5;
    }

    private void appendThinkingBar(AttributedStringBuilder ab, int lw) {
        int barLen = thinkingBarStars();
        // Right-align the dots: leading spaces fill the rest of the left panel.
        // One extra space is reserved after the last dot so the symbols don't
        // sit flush against the right border.
        int lead = Math.max(0, lw - barLen - 1);
        ab.style(TerminalTheme.ST_L_SEPARATOR).append(" ".repeat(lead));
        int active = model.dotIndex.get() % barLen;
        for (int i = 0; i < barLen; i++) {
            if (i == active) {
                ab.style(TerminalTheme.ST_DOT_ACTIVE).append("\u25AA");
            } else {
                ab.style(TerminalTheme.ST_DOT_PENDING).append("\u25AA");
            }
        }
        ab.style(TerminalTheme.ST_L_SEPARATOR).append(" ");
    }

    /**
     * Paints the slash-command popup: a small framed list of the commands
     * whose name starts with the current input buffer, rendered as an overlay
     * that sits just above the input box.
     *
     * <p>The popup is only drawn when {@link TuiModel#slashPopupVisible} is
     * true; the read loop sets that flag whenever the input starts with
     * {@code '/'} and at least one command matches. Each row shows the
     * command token (e.g. {@code /lang}) followed by its localised
     * description, sourced live from {@link SlashCommandRegistry} so newly
     * registered commands appear automatically.
     *
     * <p>The popup grows upward from the line immediately above the input
     * box, capped at {@code maxRows} entries (plus a header) so it never
     * overflows the display region on small terminals.
     */
    void drawSlashPopup(PrintWriter pw) {
        if (!model.slashPopupVisible) {
            return;
        }
        String text = model.inputBuf.toString();
        var matches = SlashCommandRegistry.filter(text);
        if (matches.isEmpty()) {
            return;
        }

        int maxRows = Math.min(matches.size(), Math.max(1, model.bodyH() - 1));
        int popupH = maxRows + 1; // +1 for the header line
        int bottomRow = model.inputRow() - 1;   // line just above the input box
        int topRow = Math.max(1, bottomRow - popupH + 1);
        int rw = model.rightW();
        int width = Math.max(20, Math.min(rw, model.tw));

        String bgSet = "\033[48;5;" + TerminalTheme.C_BG_INPUT + "m";

        // Header line: show the filter prefix so the user knows what is being matched.
        String headerLabel = " commands (" + matches.size() + ")";
        {
            AttributedStringBuilder ab = new AttributedStringBuilder();
            ab.style(TerminalTheme.ST_PROMPT).append(headerLabel);
            int hdrWidth = DisplayTextUtils.displayWidth(headerLabel);
            ab.style(TerminalTheme.ST_INPUT).append(" ".repeat(Math.max(0, width - hdrWidth)));
            pw.print("\033[" + bottomRow + ";1H" + bgSet + "\033[K" + ab.toAnsi());
        }

        // Command rows, drawn from the bottom of the popup upward so the
        // first (best) match sits directly under the header and higher
        // indices go down — matching natural UP/DOWN semantics. The row at
        // slashPopupSelectedIndex is highlighted with the selection style.
        // Compute the longest command name so all descriptions align in a
        // single column regardless of individual name lengths.
        int maxNameLen = 0;
        for (var c : matches) {
            maxNameLen = Math.max(maxNameLen, c.name().length());
        }
        int selIdx = Math.min(model.slashPopupSelectedIndex, matches.size() - 1);
        for (int i = 0; i < maxRows; i++) {
            int row = topRow + i;
            if (row > bottomRow - 1) {
                break;
            }
            SlashCommandRegistry.SlashCommand cmd = matches.get(i);
            boolean selected = (i == selIdx);
            String namePadded = cmd.name()
                    + " ".repeat(maxNameLen - cmd.name().length());
            AttributedStringBuilder ab = new AttributedStringBuilder();
            if (selected) {
                ab.style(TerminalTheme.ST_POPUP_SELECTED_NAME).append(" " + namePadded);
            } else {
                ab.style(TerminalTheme.ST_PROMPT).append(" " + namePadded);
            }
            String rest = "  " + cmd.description().strip();
            int used = 1 + maxNameLen;
            int remaining = Math.max(0, width - used);
            String desc = DisplayTextUtils.displaySubstring(rest, remaining);
            if (selected) {
                ab.style(TerminalTheme.ST_POPUP_SELECTED).append(desc);
            } else {
                ab.style(TerminalTheme.ST_INPUT).append(desc);
            }
            int descWidth = DisplayTextUtils.displayWidth(desc);
            AttributedStyle fillStyle = selected
                ? TerminalTheme.ST_POPUP_SELECTED : TerminalTheme.ST_INPUT;
            ab.style(fillStyle).append(" ".repeat(Math.max(0, width - used - descWidth)));
            pw.print("\033[" + row + ";1H" + bgSet + "\033[K" + ab.toAnsi());
        }
    }

    void drawInputLine(PrintWriter pw) {
        String prompt = ">> ";
        int promptLen = prompt.length();
        String text = model.inputBuf.toString();
        boolean showPlaceholder = text.isEmpty() && model.chatEngineReady;
        if (showPlaceholder) {
            text = I18n.tr(MessageKey.TUI_INPUT_PLACEHOLDER);
        }
        int maxLen = model.tw - promptLen;
        int textWidth = DisplayTextUtils.displayWidth(text);

        String l1 = DisplayTextUtils.displaySubstring(text, maxLen);
        int l1chars = l1.length();
        String rest1 = text.substring(l1chars);
        String l2 = DisplayTextUtils.displaySubstring(rest1, maxLen);
        int l2chars = l2.length();
        String rest2 = rest1.substring(l2chars);
        String l3 = DisplayTextUtils.displaySubstring(rest2, model.tw);

        String bgSet = "\033[48;5;" + TerminalTheme.C_BG_INPUT + "m";

        // Line above the input box — fill with input background to avoid transparency
        pw.print("\033[" + (model.inputRow() - 1) + ";1H" + bgSet + "\033[K");

        {
            AttributedStringBuilder ab = new AttributedStringBuilder();
            ab.style(TerminalTheme.ST_PROMPT).append(prompt);
            if (showPlaceholder) {
                AttributedStyle placeholderStyle = AttributedStyle.DEFAULT
                    .foreground(244).background(TerminalTheme.C_BG_INPUT);
                ab.style(placeholderStyle).append(text);
                ab.style(placeholderStyle).append(" ".repeat(maxLen - textWidth));
            } else if (textWidth <= maxLen) {
                ab.style(TerminalTheme.ST_INPUT).append(text);
                ab.style(TerminalTheme.ST_INPUT).append(" ".repeat(maxLen - textWidth));
            } else {
                ab.style(TerminalTheme.ST_INPUT).append(l1);
            }
            pw.print("\033[" + model.inputRow() + ";1H" + bgSet + "\033[K" + ab.toAnsi());
        }

        {
            AttributedStringBuilder ab = new AttributedStringBuilder();
            ab.style(TerminalTheme.ST_INPUT);
            if (textWidth > maxLen) {
                ab.append(l2);
                int l2width = DisplayTextUtils.displayWidth(l2);
                ab.append(" ".repeat(Math.max(0, model.tw - l2width)));
            } else {
                ab.append(" ".repeat(Math.max(0, model.tw)));
            }
            pw.print("\033[" + (model.inputRow() + 1) + ";1H" + bgSet + "\033[K" + ab.toAnsi());
        }

        {
            AttributedStringBuilder ab = new AttributedStringBuilder();
            ab.style(TerminalTheme.ST_INPUT);
            if (textWidth > maxLen * 2) {
                ab.append(l3);
                int l3width = DisplayTextUtils.displayWidth(l3);
                ab.append(" ".repeat(Math.max(0, model.tw - l3width)));
            } else {
                ab.append(" ".repeat(Math.max(0, model.tw)));
            }
            pw.print("\033[" + (model.inputRow() + 2) + ";1H" + bgSet + "\033[K" + ab.toAnsi());
        }

        int cursorCol;
        int cursorRow;
        int beforeCursor = Math.min(model.cursorPos, l1chars);
        int cursorDisplayWidth = DisplayTextUtils.displayWidth(text.substring(0, beforeCursor));

        if (model.cursorPos <= l1chars) {
            cursorRow = model.inputRow();
            cursorCol = promptLen + 1 + cursorDisplayWidth;
        } else if (model.cursorPos <= l1chars + l2chars) {
            cursorRow = model.inputRow() + 1;
            cursorCol = 1 + DisplayTextUtils.displayWidth(text.substring(l1chars, model.cursorPos));
        } else {
            cursorRow = model.inputRow() + 2;
            cursorCol = 1 + DisplayTextUtils.displayWidth(text.substring(l1chars + l2chars, model.cursorPos));
        }
        pw.print("\033[" + cursorRow + ";" + cursorCol + "H");
    }
}
