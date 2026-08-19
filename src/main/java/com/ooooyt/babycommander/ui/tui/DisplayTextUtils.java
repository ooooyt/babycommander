package com.ooooyt.babycommander.ui.tui;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * Terminal display-width-aware string utilities.
 *
 * <p>Extracted verbatim from {@code TerminalUIAdapter}; these are pure
 * (stateless) helpers that depend only on {@link MarkdownRenderer} and
 * {@link TerminalTheme}. Centralising them here lets the renderer and any
 * other component reuse exact-width text handling.
 */
final class DisplayTextUtils {

    private DisplayTextUtils() {}

    static int displayWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            w += MarkdownRenderer.getDisplayWidth(cp);
            i += Character.charCount(cp);
        }
        return w;
    }

    static String displaySubstring(String s, int maxWidth) {
        int w = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int dw = MarkdownRenderer.getDisplayWidth(cp);
            if (w + dw > maxWidth) return s.substring(0, i);
            w += dw;
            i += Character.charCount(cp);
        }
        return s;
    }

    static String fill(String s, int w) {
        if (w <= 0) return "";
        AttributedStringBuilder ab = new AttributedStringBuilder();
        for (int i = 0; i < s.length() && ab.columnLength() < w; ) {
            int cp = s.codePointAt(i);
            int dw = MarkdownRenderer.getDisplayWidth(cp);
            if (ab.columnLength() + dw > w) break;
            ab.append(Character.toString(cp));
            i += Character.charCount(cp);
        }
        while (ab.columnLength() < w) { ab.append(' '); }
        return ab.toString();
    }

    static List<String> wrapText(String text, int maxW) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) return result;
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            int lineW = 0;
            for (int i = 0; i < line.length(); ) {
                int cp = line.codePointAt(i);
                lineW += MarkdownRenderer.getDisplayWidth(cp);
                i += Character.charCount(cp);
            }
            int wordW = 0;
            for (int i = 0; i < word.length(); ) {
                int cp = word.codePointAt(i);
                wordW += MarkdownRenderer.getDisplayWidth(cp);
                i += Character.charCount(cp);
            }
            if (lineW + (line.isEmpty() ? 0 : 1) + wordW > maxW) {
                if (!line.isEmpty()) {
                    result.add(line.toString());
                    line.setLength(0);
                }
                if (wordW > maxW) {
                    result.add(word);
                } else {
                    line.append(word);
                }
            } else {
                if (!line.isEmpty()) line.append(' ');
                line.append(word);
            }
        }
        if (!line.isEmpty()) result.add(line.toString());
        return result;
    }

    static AttributedString stampBg(AttributedString as, int w, int defaultFg) {
        AttributedStringBuilder ab = new AttributedStringBuilder();
        ab.style(TerminalTheme.ST_R_BG).append(" ");
        int cols = 1;
        // Iterate by code point, not UTF-16 char, so surrogate pairs (emoji,
        // CJK-ext, etc.) are never split and dropped at the column boundary.
        for (int i = 0; i < as.length() && cols < w; ) {
            int cp = as.codePointAt(i);
            int dw = MarkdownRenderer.getDisplayWidth(cp);
            if (cols + dw > w) break;
            AttributedStyle s = as.styleAt(i);
            if (s == AttributedStyle.DEFAULT) {
                s = AttributedStyle.DEFAULT.background(TerminalTheme.C_BG_RIGHT).foreground(defaultFg);
            } else {
                s = s.background(TerminalTheme.C_BG_RIGHT);
            }
            ab.style(s).append(Character.toString(cp));
            cols += dw;
            i += Character.charCount(cp);
        }
        if (cols < w && w - cols > 0) ab.style(TerminalTheme.ST_R_BG).append(" ".repeat(w - cols));
        return ab.toAttributedString();
    }
}
