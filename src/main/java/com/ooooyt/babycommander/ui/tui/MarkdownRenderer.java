package com.ooooyt.babycommander.ui.tui;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CommonMark-inspired Markdown renderer producing JLine AttributedString lines.
 *
 * Uses AttributedStringBuilder + AttributedStyle for 100% correct column-width
 * accounting — JLine's WCWidth handles CJK/wide chars, and AttributedString.columnLength()
 * returns the true terminal display width.
 *
 * Emoji are stripped before rendering (all chars in Unicode blocks Emoticons,
 * Supplemental Symbols, Misc Symbols, Dingbats, Transport/Map, etc.).
 *
 * Supported: H1-H4 headings, **bold**, *italic*, `inline code`,
 * ```fenced code blocks```, > blockquote, - /* unordered list,
 * 1. ordered list, --- horizontal rule, | tables.
 */
public class MarkdownRenderer {

    // Colour palette using 256-colour indices for wide terminal support
    private static final AttributedStyle S_DEFAULT  = AttributedStyle.DEFAULT;
    private static final AttributedStyle S_H1       = S_DEFAULT.bold().foreground(AttributedStyle.YELLOW);
    private static final AttributedStyle S_H2       = S_DEFAULT.bold().foreground(AttributedStyle.CYAN);
    private static final AttributedStyle S_H3       = S_DEFAULT.bold().foreground(AttributedStyle.MAGENTA);
    private static final AttributedStyle S_H4       = S_DEFAULT.italic().foreground(AttributedStyle.WHITE);
    private static final AttributedStyle S_RULE      = S_DEFAULT.faint().foreground(AttributedStyle.CYAN);
    private static final AttributedStyle S_QUOTE     = S_DEFAULT.italic().foreground(AttributedStyle.WHITE);
    private static final AttributedStyle S_QUOTE_BAR = S_DEFAULT.foreground(AttributedStyle.CYAN);
    private static final AttributedStyle S_LIST_BULLET = S_DEFAULT.foreground(AttributedStyle.CYAN);
    private static final AttributedStyle S_LIST_NUM   = S_DEFAULT.bold().foreground(AttributedStyle.YELLOW);
    private static final AttributedStyle S_CODE_BODY  = S_DEFAULT.foreground(AttributedStyle.GREEN);
    private static final AttributedStyle S_CODE_FENCE = S_DEFAULT.faint().foreground(AttributedStyle.GREEN);
    private static final AttributedStyle S_TABLE_SEP  = S_DEFAULT.faint().foreground(AttributedStyle.CYAN);
    private static final AttributedStyle S_TABLE_CELL = S_DEFAULT.foreground(AttributedStyle.WHITE);
    private static final AttributedStyle S_BOLD       = S_DEFAULT.bold();
    private static final AttributedStyle S_ITALIC     = S_DEFAULT.italic();
    private static final AttributedStyle S_BOLD_ITALIC= S_DEFAULT.bold().italic();
    private static final AttributedStyle S_CODE_INLINE= S_DEFAULT.foreground(AttributedStyle.GREEN);

    // Emoji regex: strip all emoji and pictographic symbols
    private static final Pattern EMOJI = Pattern.compile(
        "[\\x{1F600}-\\x{1F64F}" +  // Emoticons
        "\\x{1F300}-\\x{1F5FF}" +   // Misc symbols & pictographs
        "\\x{1F680}-\\x{1F6FF}" +   // Transport & map
        "\\x{1F700}-\\x{1F77F}" +   // Alchemical symbols
        "\\x{1F780}-\\x{1F7FF}" +   // Geometric shapes extended
        "\\x{1F800}-\\x{1F8FF}" +   // Supplemental arrows-C
        "\\x{1F900}-\\x{1F9FF}" +   // Supplemental symbols & pictographs
        "\\x{1FA00}-\\x{1FA6F}" +   // Chess symbols
        "\\x{1FA70}-\\x{1FAFF}" +   // Symbols & pictographs extended-A
        "\\x{2600}-\\x{26FF}" +     // Misc symbols
        "\\x{2700}-\\x{27BF}" +     // Dingbats
        "\\x{FE00}-\\x{FE0F}" +     // Variation selectors
        "\\x{1F1E0}-\\x{1F1FF}" +   // Flags
        "\\x{200D}" +               // Zero-width joiner
        "\\x{FE0F}]",               // Variation selector-16
        Pattern.UNICODE_CHARACTER_CLASS
    );

    /** Strip emoji from a string */
    public static String stripEmoji(String s) {
        return EMOJI.matcher(s).replaceAll("").trim();
    }

    /**
     * Render Markdown source into a list of AttributedString lines,
     * each soft-wrapped to at most {@code width} terminal columns.
     */
    public static List<AttributedString> render(String markdown, int width) {
        String[] rawLines = markdown.split("\n", -1);
        List<AttributedString> out = new ArrayList<>();
        boolean inFence = false;
        String fenceLang = "";
        List<String> tableBuf = new ArrayList<>();

        for (String raw : rawLines) {
            String line = stripEmoji(raw);

            // ── Fenced code block ─────────────────────────────────────────
            if (line.stripLeading().startsWith("```")) {
                flushTableBuf(tableBuf, out, width);
                if (!inFence) {
                    inFence = true;
                    fenceLang = line.stripLeading().substring(3).trim();
                    AttributedStringBuilder ab = new AttributedStringBuilder();
                    ab.style(S_CODE_FENCE);
                    ab.append(" ");
                    ab.append(fenceLang.isEmpty() ? "code" : fenceLang);
                    out.add(ab.toAttributedString());
                } else {
                    inFence = false;
                    AttributedStringBuilder ab = new AttributedStringBuilder();
                    ab.style(S_CODE_FENCE);
                    ab.append(" ");
                    out.add(ab.toAttributedString());
                }
                continue;
            }
            if (inFence) {
                flushTableBuf(tableBuf, out, width);
                AttributedStringBuilder ab = new AttributedStringBuilder();
                ab.style(S_CODE_BODY);
                ab.append("  ").append(line);
                out.addAll(wrapLine(ab.toAttributedString(), width));
                continue;
            }

            // ── Blank line ────────────────────────────────────────────────
            if (line.isBlank()) {
                flushTableBuf(tableBuf, out, width);
                out.add(AttributedString.EMPTY);
                continue;
            }

            // ── Horizontal rule ───────────────────────────────────────────
            if (line.matches("^[-*_]{3,}\\s*$")) {
                flushTableBuf(tableBuf, out, width);
                AttributedStringBuilder ab = new AttributedStringBuilder();
                ab.style(S_RULE);
                ab.append(" ".repeat(Math.max(0, width)));
                out.add(ab.toAttributedString());
                continue;
            }

            // ── Headings ──────────────────────────────────────────────────
            if (line.startsWith("# ")) {
                flushTableBuf(tableBuf, out, width);
                out.addAll(renderHeading(line.substring(2).trim(), 1, width));
                continue;
            }
            if (line.startsWith("## ")) {
                flushTableBuf(tableBuf, out, width);
                out.addAll(renderHeading(line.substring(3).trim(), 2, width));
                continue;
            }
            if (line.startsWith("### ")) {
                flushTableBuf(tableBuf, out, width);
                out.addAll(renderHeading(line.substring(4).trim(), 3, width));
                continue;
            }
            if (line.startsWith("#### ") || line.startsWith("##### ")) {
                flushTableBuf(tableBuf, out, width);
                int n = line.startsWith("#####") ? 5 : 4;
                out.addAll(renderHeading(line.substring(n + 1).trim(), 4, width));
                continue;
            }

            // ── Blockquote ────────────────────────────────────────────────
            if (line.startsWith("> ")) {
                flushTableBuf(tableBuf, out, width);
                String text = line.substring(2).trim();
                List<AttributedString> wrapped = wrapInline(text, width - 2, S_QUOTE);
                for (AttributedString ws : wrapped) {
                    AttributedStringBuilder ab = new AttributedStringBuilder();
                    ab.style(S_QUOTE_BAR).append(" ");
                    ab.style(S_DEFAULT).append(ws);
                    out.add(ab.toAttributedString());
                }
                continue;
            }

            // ── Unordered list ────────────────────────────────────────────
            Matcher ul = Pattern.compile("^(\\s*)[-*+] (.+)").matcher(line);
            if (ul.matches()) {
                flushTableBuf(tableBuf, out, width);
                int indent = ul.group(1).length();
                String text = ul.group(2).trim();
                String bullet = indent > 0 ? "   " : "  ";
                String cont   = indent > 0 ? "     " : "    ";
                List<AttributedString> wrapped = wrapInline(text, width - bullet.length(), S_DEFAULT);
                for (int i = 0; i < wrapped.size(); i++) {
                    AttributedStringBuilder ab = new AttributedStringBuilder();
                    if (i == 0) {
                        ab.style(S_LIST_BULLET).append(bullet).append(indent > 0 ? "- " : "* ");
                    } else {
                        ab.style(S_DEFAULT).append(cont + "  ");
                    }
                    ab.style(S_DEFAULT).append(wrapped.get(i));
                    out.add(ab.toAttributedString());
                }
                continue;
            }

            // ── Ordered list ──────────────────────────────────────────────
            Matcher ol = Pattern.compile("^(\\d+)\\. (.+)").matcher(line);
            if (ol.matches()) {
                flushTableBuf(tableBuf, out, width);
                String num  = ol.group(1);
                String text = ol.group(2);
                String prefix = "  ";
                String cont   = "     ";
                List<AttributedString> wrapped = wrapInline(text, width - prefix.length() - num.length() - 2, S_DEFAULT);
                for (int i = 0; i < wrapped.size(); i++) {
                    AttributedStringBuilder ab = new AttributedStringBuilder();
                    if (i == 0) {
                        ab.style(S_LIST_NUM).append(prefix + num + ". ");
                    } else {
                        ab.style(S_DEFAULT).append(cont);
                    }
                    ab.style(S_DEFAULT).append(wrapped.get(i));
                    out.add(ab.toAttributedString());
                }
                continue;
            }

            // ── Table ─────────────────────────────────────────────────────
            if (line.trim().startsWith("|")) {
                tableBuf.add(line);
                continue;
            }

            // Not a table line: flush buffer before rendering normal paragraph
            flushTableBuf(tableBuf, out, width);

            // ── Normal paragraph ──────────────────────────────────────────
            out.addAll(wrapInline(line, width, S_DEFAULT));
        }

        // Flush remaining table buffer
        flushTableBuf(tableBuf, out, width);

        return out;
    }

    /** Flush buffered table lines as a properly aligned table */
    private static void flushTableBuf(List<String> buf, List<AttributedString> out, int width) {
        if (buf.isEmpty()) return;
        out.addAll(renderTable(buf, width));
        buf.clear();
    }

    private static List<AttributedString> renderTable(List<String> lines, int width) {
        List<String[]> rows = new ArrayList<>();
        int sepIdx = -1;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.matches("^[|\\-: ]+$")) {
                sepIdx = i;
                continue;
            }
            String[] cells = line.replaceAll("^\\|", "").replaceAll("\\|$", "").split("\\|");
            for (int j = 0; j < cells.length; j++) {
                cells[j] = stripInline(cells[j].trim());
            }
            rows.add(cells);
        }

        if (rows.isEmpty()) return List.of();

        // Determine number of columns
        int numCols = 0;
        for (String[] r : rows) {
            numCols = Math.max(numCols, r.length);
        }

        // Calculate column widths
        int[] colWidths = new int[numCols];
        for (String[] r : rows) {
            for (int i = 0; i < r.length; i++) {
                colWidths[i] = Math.max(colWidths[i], displayWidth(r[i]));
            }
        }

        // Cap total width to fit terminal. When the table is wider than the
        // terminal we must shrink the column widths. The old code greedily
        // shrank the LAST column to a tiny width; that narrow column then
        // wrapped into many lines and forced every other (longer) column to pad
        // with blank lines, producing a tall table full of empty cells. Instead
        // we distribute the reduction across ALL columns proportionally to their
        // content width, so columns with more content give up more space. This
        // keeps every column comfortably wide and roughly balances their wrapped
        // heights, emitting far fewer empty lines.
        int minCellWidth = 3;
        int totalBorder = 2 + (numCols - 1);
        int totalContent = 0;
        for (int cw : colWidths) totalContent += cw;
        int totalWidth = totalBorder + totalContent + numCols * 2;
        if (totalWidth > width) {
            int excess = totalWidth - width;

            // Pass 1: reduce every column proportionally to its width, but never
            // below minCellWidth.
            int[] reducible = new int[numCols];
            int totalReducible = 0;
            for (int i = 0; i < numCols; i++) {
                reducible[i] = Math.max(0, colWidths[i] - minCellWidth);
                totalReducible += reducible[i];
            }
            int remaining = excess;
            for (int i = 0; i < numCols && remaining > 0; i++) {
                if (reducible[i] == 0) continue;
                int reduction = totalReducible == 0
                        ? 0
                        : (int) Math.round((double) excess * reducible[i] / totalReducible);
                reduction = Math.min(reduction, reducible[i]);
                reduction = Math.min(reduction, remaining);
                if (reduction > 0) {
                    colWidths[i] -= reduction;
                    remaining -= reduction;
                }
            }

            // Pass 2: if rounding left a little excess, trim it from the widest
            // columns first (still never below minCellWidth).
            if (remaining > 0) {
                int[] order = new int[numCols];
                for (int i = 0; i < numCols; i++) order[i] = i;
                for (int a = 0; a < numCols - 1; a++) {
                    for (int b = a + 1; b < numCols; b++) {
                        if (colWidths[order[b]] > colWidths[order[a]]) {
                            int t = order[a]; order[a] = order[b]; order[b] = t;
                        }
                    }
                }
                for (int idx : order) {
                    if (remaining <= 0) break;
                    int reduction = Math.min(remaining, colWidths[idx] - minCellWidth);
                    if (reduction > 0) {
                        colWidths[idx] -= reduction;
                        remaining -= reduction;
                    }
                }
            }

            // Pass 3: absolute last resort for a very narrow terminal - allow
            // going below minCellWidth (down to 1) so the table always fits.
            if (remaining > 0) {
                for (int i = numCols - 1; i >= 0 && remaining > 0; i--) {
                    int reduction = Math.min(remaining, colWidths[i] - 1);
                    if (reduction > 0) {
                        colWidths[i] -= reduction;
                        remaining -= reduction;
                    }
                }
            }
        }

        List<AttributedString> result = new ArrayList<>();

        int headerCount = sepIdx >= 0 ? Math.min(sepIdx, rows.size()) : 0;

        String topSep    = buildBorderLine(colWidths, "\u250C", "\u252C", "\u2510");
        String midSep    = buildBorderLine(colWidths, "\u251C", "\u253C", "\u2524");
        String bottomSep = buildBorderLine(colWidths, "\u2514", "\u2534", "\u2518");

        if (topSep.length() <= width) {
            AttributedStringBuilder ab = new AttributedStringBuilder();
            ab.style(S_TABLE_SEP).append(topSep);
            result.add(ab.toAttributedString());
        }

        for (int r = 0; r < rows.size(); r++) {
            String[] cells = rows.get(r);
            boolean isHeader = r < headerCount;

            // Wrap each cell's content to its column width
            List<List<String>> wrappedCells = new ArrayList<>();
            int maxLines = 1;
            for (int c = 0; c < numCols; c++) {
                String cellText = c < cells.length ? cells[c] : "";
                List<String> wrapped = wrapPlainText(cellText, colWidths[c]);
                wrappedCells.add(wrapped);
                maxLines = Math.max(maxLines, wrapped.size());
            }

            // Render maxLines physical lines, with cells padded to maintain alignment
            for (int line = 0; line < maxLines; line++) {
                AttributedStringBuilder ab = new AttributedStringBuilder();
                ab.style(S_TABLE_SEP).append("\u2502");
                for (int c = 0; c < numCols; c++) {
                    String cellLine = line < wrappedCells.get(c).size()
                            ? wrappedCells.get(c).get(line) : "";
                    ab.style(isHeader ? S_TABLE_CELL.bold() : S_TABLE_CELL);
                    ab.append(" ").append(padRight(cellLine, colWidths[c])).append(" ");
                    ab.style(S_TABLE_SEP).append("\u2502");
                }
                result.add(ab.toAttributedString());
            }

            if (r == headerCount - 1 && headerCount > 0 && midSep.length() <= width) {
                AttributedStringBuilder sepAb = new AttributedStringBuilder();
                sepAb.style(S_TABLE_SEP).append(midSep);
                result.add(sepAb.toAttributedString());
            }
        }

        if (bottomSep.length() <= width) {
            AttributedStringBuilder ab = new AttributedStringBuilder();
            ab.style(S_TABLE_SEP).append(bottomSep);
            result.add(ab.toAttributedString());
        }

        return result;
    }

    private static String buildBorderLine(int[] colWidths, String left, String mid, String right) {
        StringBuilder sb = new StringBuilder(left);
        for (int i = 0; i < colWidths.length; i++) {
            sb.append("\u2500".repeat(colWidths[i] + 2));
            if (i < colWidths.length - 1) sb.append(mid);
        }
        sb.append(right);
        return sb.toString();
    }

    private static String stripInline(String text) {
        return text.replaceAll("\\*\\*\\*|\\*\\*|\\*|__|_|`", "");
    }

    private static int displayWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            w += getDisplayWidth(cp);
            i += Character.charCount(cp);
        }
        return w;
    }

    private static String padRight(String s, int minWidth) {
        int current = displayWidth(s);
        int needed = minWidth - current;
        if (needed <= 0) return s;
        return s + " ".repeat(needed);
    }

    /**
     * Word-wrap a plain string into lines each at most {@code width} display columns.
     * Words are split at spaces; a single word longer than {@code width} is hard-split
     * by display width (code-point aware for CJK/wide chars).
     */
    private static List<String> wrapPlainText(String text, int width) {
        if (width <= 0) return List.of(text);
        if (text.isEmpty()) return List.of("");

        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentWidth = 0;

        for (String word : text.split(" ", -1)) {
            int wordWidth = displayWidth(word);

            if (currentWidth == 0) {
                if (wordWidth <= width) {
                    current.append(word);
                    currentWidth = wordWidth;
                } else {
                    String remaining = hardSplit(word, width, lines);
                    current.append(remaining);
                    currentWidth = displayWidth(remaining);
                }
            } else {
                int needed = 1 + wordWidth;
                if (currentWidth + needed <= width) {
                    current.append(" ").append(word);
                    currentWidth += needed;
                } else {
                    lines.add(current.toString());
                    current = new StringBuilder();
                    if (wordWidth <= width) {
                        current.append(word);
                        currentWidth = wordWidth;
                    } else {
                        String remaining = hardSplit(word, width, lines);
                        current.append(remaining);
                        currentWidth = displayWidth(remaining);
                    }
                }
            }
        }

        lines.add(current.toString());
        return lines;
    }

    /**
     * Hard-split a single long word, appending all chunks except the last to {@code lines}
     * and returning the remaining tail (which fits in {@code width} or is a single char).
     */
    private static String hardSplit(String word, int width, List<String> lines) {
        String remaining = word;
        while (displayWidth(remaining) > width) {
            int cut = charsForWidth(remaining, width);
            if (cut == 0) cut = 1; // force progress for wide chars in narrow columns
            lines.add(remaining.substring(0, cut));
            remaining = remaining.substring(cut);
        }
        return remaining;
    }

    /** Return the number of chars from the start of {@code s} that fit in {@code width} display columns. */
    private static int charsForWidth(String s, int width) {
        int cols = 0;
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            int cw = getDisplayWidth(cp);
            if (cols + cw > width) break;
            cols += cw;
            i += Character.charCount(cp);
        }
        return i;
    }

    /** Render a heading with underline */
    private static List<AttributedString> renderHeading(String text, int level, int width) {
        List<AttributedString> out = new ArrayList<>();
        AttributedStyle style = switch (level) {
            case 1 -> S_H1;
            case 2 -> S_H2;
            case 3 -> S_H3;
            default -> S_H4;
        };
        String prefix = level == 1 ? "" : level == 2 ? "" : "  ";
        // Build the heading line
        AttributedStringBuilder ab = new AttributedStringBuilder();
        ab.style(style).append(prefix + text);
        out.addAll(wrapLine(ab.toAttributedString(), width));
        // Underline for H1 and H2
        if (level == 1) {
            AttributedStringBuilder under = new AttributedStringBuilder();
            under.style(S_H1.faint());
            int len = Math.max(1, Math.min(width, text.length() + prefix.length()));
            under.append("=".repeat(len));
            out.add(under.toAttributedString());
        } else if (level == 2) {
            AttributedStringBuilder under = new AttributedStringBuilder();
            under.style(S_H2.faint());
            int len = Math.max(1, Math.min(width, text.length() + prefix.length()));
            under.append("-".repeat(len));
            out.add(under.toAttributedString());
        }
        return out;
    }

    /** Append inline-formatted text (bold/italic/code) to a builder */
    private static void appendInline(AttributedStringBuilder ab, String text) {
        // Parse inline markup: ***x***, **x**, *x*, _x_, `x`
        int i = 0;
        while (i < text.length()) {
            // Inline code: `...`
            if (text.charAt(i) == '`') {
                int end = text.indexOf('`', i + 1);
                if (end > i) {
                    ab.style(S_CODE_INLINE).append(text, i + 1, end);
                    ab.style(S_DEFAULT);
                    i = end + 1;
                    continue;
                }
            }
            // Bold+italic: ***...***
            if (i + 2 < text.length() && text.startsWith("***", i)) {
                int end = text.indexOf("***", i + 3);
                if (end > i) {
                    ab.style(S_BOLD_ITALIC).append(text, i + 3, end);
                    ab.style(S_DEFAULT);
                    i = end + 3;
                    continue;
                }
            }
            // Bold: **...**
            if (i + 1 < text.length() && text.startsWith("**", i)) {
                int end = text.indexOf("**", i + 2);
                if (end > i) {
                    ab.style(S_BOLD).append(text, i + 2, end);
                    ab.style(S_DEFAULT);
                    i = end + 2;
                    continue;
                }
            }
            // Italic: *...* or _..._
            char c = text.charAt(i);
            if (c == '*' || c == '_') {
                int end = text.indexOf(c, i + 1);
                if (end > i) {
                    ab.style(S_ITALIC).append(text, i + 1, end);
                    ab.style(S_DEFAULT);
                    i = end + 1;
                    continue;
                }
            }
            ab.append(c);
            i++;
        }
    }

    /** Wrap inline-formatted text to fit width */
    private static List<AttributedString> wrapInline(String text, int width, AttributedStyle base) {
        // Build full attributed string first, then wrap
        AttributedStringBuilder ab = new AttributedStringBuilder();
        ab.style(base);
        appendInline(ab, text);
        return wrapLine(ab.toAttributedString(), width);
    }

    /**
     * Soft-wrap an AttributedString to a list of lines each at most {@code width} columns.
     * Uses AttributedString.columnLength() for accurate display-width measurement.
     *
     * Embedded newline characters ({@code \n}) are treated as hard line-break boundaries:
     * the input is first split on them, and each newline-delimited segment is soft-wrapped
     * independently. This preserves the original multi-line structure of e.g. multi-line
     * shell commands instead of flattening them into a single wrapped paragraph.
     */
    public static List<AttributedString> wrapLine(AttributedString as, int width) {
        List<AttributedString> result = new ArrayList<>();
        if (width <= 0) { result.add(as); return result; }

        String plain = as.toString();
        if (plain.isEmpty()) { result.add(as); return result; }

        // Split on embedded newlines first: each newline-delimited segment is a logical
        // line that must remain separate, regardless of whether it fits in `width`.
        // This keeps multi-line shell commands intact even when the whole message fits.
        // The prefix length is tracked in chars because AttributedString.substring is
        // char-based while the segments are delimited by char positions.
        int segStart = 0;
        for (int i = 0; i <= plain.length(); i++) {
            if (i == plain.length() || plain.charAt(i) == '\n') {
                AttributedString seg = as.substring(segStart, i);
                if (!seg.toString().isEmpty()) {
                    result.addAll(wrapSegment(seg, width));
                }
                segStart = i + 1;
            }
        }
        if (result.isEmpty()) { result.add(as); }
        return result;
    }

    /** Soft-wrap a single newline-free segment, preserving its full content. */
    private static List<AttributedString> wrapSegment(AttributedString as, int width) {
        List<AttributedString> result = new ArrayList<>();
        if (width <= 0) { result.add(as); return result; }

        String plain = as.toString();
        if (plain.isEmpty() || as.columnLength() <= width) {
            result.add(as);
            return result;
        }

        // Word-wrap: find split points in the plain string
        int start = 0;
        while (start < plain.length()) {
            // Find the maximum char position that fits in `width` columns
            int end = start;
            int cols = 0;
            while (end < plain.length()) {
                int cp = plain.codePointAt(end);
                int charWidth = Character.charCount(cp);
                // Use WCWidth-like logic: most ASCII = 1, CJK = 2
                int displayW = getDisplayWidth(cp);
                if (cols + displayW > width) break;
                cols += displayW;
                end += charWidth;
            }
            if (end == start) { end = start + 1; } // avoid infinite loop

            // Try to break at a word boundary
            int breakAt = end;
            if (end < plain.length()) {
                int wb = plain.lastIndexOf(' ', end - 1);
                if (wb > start) {
                    // Compute the width of the line if we break at the word boundary.
                    int lineWidth = 0;
                    for (int k = start; k < wb + 1; ) {
                        int cp = plain.codePointAt(k);
                        lineWidth += getDisplayWidth(cp);
                        k += Character.charCount(cp);
                    }

                    // Measure the next token (from the space to the next space/end).
                    int nextStart = wb + 1;
                    while (nextStart < plain.length() && plain.charAt(nextStart) == ' ') nextStart++;
                    int nextWordCols = 0;
                    int i = nextStart;
                    while (i < plain.length() && plain.charAt(i) != ' ') {
                        nextWordCols += getDisplayWidth(plain.codePointAt(i));
                        i += Character.charCount(plain.codePointAt(i));
                    }
                    // Only hard-break (fill the line by splitting the next token) when
                    // that token is longer than a full line, i.e. it cannot fit on a line
                    // of its own and would need splitting anyway. For shorter tokens
                    // (normal words, short flags like "-la") just move them to the next
                    // line as a whole; splitting them mid-word looks broken.
                    if (nextWordCols > width) {
                        breakAt = end; // fill the line by hard-breaking the long token
                    } else {
                        breakAt = wb + 1; // break after space (whole token wraps cleanly)
                    }
                }
            }

            result.add(as.substring(start, Math.min(breakAt, plain.length())));
            start = breakAt;
            // Skip leading spaces on continuation
            while (start < plain.length() && plain.charAt(start) == ' ') start++;
        }
        return result;
    }

    /** Unicode display width matching JLine's wcwidth (1 for ASCII/Latin, 2 for CJK/wide, 0 for combining) */
    static int getDisplayWidth(int cp) {
        if (cp < 0x20) return 0;
        if (cp < 0x7F) return 1;
        // Explicitly-whitelisted symbols used in UI labels
        if (cp == 0x2AFF) return 1;    // ⿻ Open Toaster branding
        if (cp >= 0x0300 && cp <= 0x036F) return 0;
        if (cp >= 0x1DC0 && cp <= 0x1DE6) return 0;
        if (cp >= 0x20D0 && cp <= 0x20FF) return 0;
        if (cp >= 0xFE20 && cp <= 0xFE2F) return 0;
        // Wide characters matching JLine's wcwidth
        if ((cp >= 0x1100 && cp <= 0x115F) ||       // Hangul Jamo
            (cp >= 0x2300 && cp <= 0x23FF) ||       // Misc Technical (⏳ etc)
            (cp >= 0x2E80 && cp <= 0x303E) ||       // CJK Radicals
            (cp >= 0x3040 && cp <= 0x33BF) ||       // Hiragana/Katakana/Bopomofo/Hangul Compat
            (cp >= 0x3400 && cp <= 0x4DBF) ||       // CJK Extension A
            (cp >= 0x4E00 && cp <= 0xA4CF) ||       // CJK Unified + Yi
            (cp >= 0xA960 && cp <= 0xA97C) ||       // Hangul Jamo Extended-A
            (cp >= 0xAC00 && cp <= 0xD7AF) ||       // Hangul Syllables
            (cp >= 0xF900 && cp <= 0xFAFF) ||       // CJK Compatibility
            (cp >= 0xFE10 && cp <= 0xFE1F) ||       // Vertical Forms
            (cp >= 0xFE30 && cp <= 0xFE6F) ||       // CJK Compatibility Forms
            (cp >= 0xFF01 && cp <= 0xFF60) ||       // Fullwidth Forms
            (cp >= 0xFFE0 && cp <= 0xFFE6) ||       // Fullwidth Signs
            (cp >= 0x1F300 && cp <= 0x1F9FF) ||     // Emoji (SMP)
            (cp >= 0x2600 && cp <= 0x26FF) ||       // Misc symbols emoji
            (cp >= 0x2700 && cp <= 0x27BF)) return 2;  // Dingbats emoji
        return 1;
    }
}
