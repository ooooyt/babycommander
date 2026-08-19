package com.ooooyt.babycommander.util;

import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.ext.gfm.tables.*;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;

import java.util.*;

/**
 * Renders Markdown to ANSI-formatted terminal output using CommonMark and Jansi.
 * Supports: headings, bold, italic, code, code blocks, lists, blockquotes,
 * horizontal rules, links, images, and GFM tables.
 *
 * The output width adapts to the actual terminal size, capped at 160 chars.
 */
public class MarkdownRenderer {

    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(TablesExtension.create()))
            .build();

    /** Default width if terminal size cannot be detected. */
    private static final int DEFAULT_TERMINAL_WIDTH = 100;

    /** Maximum width for text wrapping (don't go beyond this even on ultra-wide screens). */
    private static final int MAX_WRAP_WIDTH = 160;

    /** Cached terminal width, detected once. */
    private static int terminalWidth = -1;

    static {
        AnsiConsole.systemInstall();
        detectTerminalWidth();
    }

    /**
     * Detect the terminal width using JLine or fallback to COLUMNS env / stty.
     */
    private static void detectTerminalWidth() {
        // Try JLine's TerminalBuilder first
        try {
            var terminal = org.jline.terminal.TerminalBuilder.builder()
                    .jna(true)
                    .system(true)
                    .streams(System.in, System.out)
                    .build();
            int w = terminal.getWidth();
            if (w > 0) {
                terminalWidth = w;
                terminal.close();
                return;
            }
            terminal.close();
        } catch (Exception ignored) {
            // Fall through
        }

        // Try COLUMNS environment variable
        String columns = System.getenv("COLUMNS");
        if (columns != null) {
            try {
                int w = Integer.parseInt(columns.trim());
                if (w > 0) {
                    terminalWidth = w;
                    return;
                }
            } catch (NumberFormatException ignored) {}
        }

        // Try `stty size` on Unix-like systems
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", "stty size < /dev/tty"});
            process.waitFor();
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length == 2) {
                        int w = Integer.parseInt(parts[1]);
                        if (w > 0) {
                            terminalWidth = w;
                            return;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        // Fallback to default
        terminalWidth = DEFAULT_TERMINAL_WIDTH;
    }

    /**
     * Get the effective terminal width for rendering.
     * Uses a small right margin (2 chars) so content doesn't touch the edge,
     * and caps at MAX_WRAP_WIDTH (160) so ultra-wide screens don't produce
     * hard-to-read lines. No minimum is enforced — if the terminal is narrower
     * than 40 columns, content wraps at the actual terminal width.
     */
    private static int getWidth() {
        int w = terminalWidth > 0 ? terminalWidth : DEFAULT_TERMINAL_WIDTH;
        w = Math.min(w, MAX_WRAP_WIDTH);
        return w - 2;
    }

    /**
     * Render markdown text to ANSI-formatted output string.
     */
    public static String render(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }

        Node document = PARSER.parse(markdown);
        StringBuilder sb = new StringBuilder();
        renderNode(document, sb, 0);
        return sb.toString();
    }

    /**
     * Render markdown and print directly to System.out.
     */
    public static void print(String markdown) {
        String rendered = render(markdown);
        System.out.println(rendered);
    }

    /**
     * For testing: allow overriding the terminal width.
     */
    static void setTerminalWidth(int width) {
        terminalWidth = width;
    }

    private static void renderNode(Node node, StringBuilder sb, int indent) {
        if (node instanceof Heading heading) {
            renderHeading(heading, sb);
        } else if (node instanceof Paragraph paragraph) {
            renderParagraph(paragraph, sb);
        } else if (node instanceof BulletList bulletList) {
            renderBulletList(bulletList, sb, indent);
        } else if (node instanceof OrderedList orderedList) {
            renderOrderedList(orderedList, sb, indent);
        } else if (node instanceof FencedCodeBlock fencedCodeBlock) {
            renderFencedCodeBlock(fencedCodeBlock, sb);
        } else if (node instanceof IndentedCodeBlock indentedCodeBlock) {
            renderIndentedCodeBlock(indentedCodeBlock, sb);
        } else if (node instanceof BlockQuote blockQuote) {
            renderBlockQuote(blockQuote, sb);
        } else if (node instanceof ThematicBreak) {
            renderThematicBreak(sb);
        } else if (node instanceof Code code) {
            renderInlineCode(code, sb);
        } else if (node instanceof Emphasis emphasis) {
            renderEmphasis(emphasis, sb);
        } else if (node instanceof StrongEmphasis strongEmphasis) {
            renderStrongEmphasis(strongEmphasis, sb);
        } else if (node instanceof Link link) {
            renderLink(link, sb);
        } else if (node instanceof Image image) {
            renderImage(image, sb);
        } else if (node instanceof Text text) {
            sb.append(escapeAnsi(text.getLiteral()));
        } else if (node instanceof SoftLineBreak) {
            sb.append('\n');
        } else if (node instanceof HardLineBreak) {
            sb.append('\n');
        } else if (node instanceof Document) {
            renderChildren(node, sb, indent);
        } else if (node instanceof ListItem listItem) {
            renderListItem(listItem, sb, indent);
        } else if (node instanceof TableBlock tableBlock) {
            renderTable(tableBlock, sb);
        } else if (node instanceof TableHead || node instanceof TableBody ||
                   node instanceof TableRow || node instanceof TableCell) {
            renderChildren(node, sb, indent);
        } else {
            renderChildren(node, sb, indent);
        }
    }

    private static void renderChildren(Node parent, StringBuilder sb, int indent) {
        Node child = parent.getFirstChild();
        while (child != null) {
            renderNode(child, sb, indent);
            child = child.getNext();
        }
    }

    private static void renderHeading(Heading heading, StringBuilder sb) {
        int level = heading.getLevel();
        String text = collectInlineText(heading);
        int width = getWidth();

        if (level > 1) {
            sb.append('\n');
        }

        String prefix = level == 1 ? "═══ " : level == 2 ? "─── " : "• ";
        Ansi.Color color = switch (level) {
            case 1 -> Ansi.Color.CYAN;
            case 2 -> Ansi.Color.BLUE;
            case 3 -> Ansi.Color.GREEN;
            default -> Ansi.Color.YELLOW;
        };

        String formatted = Ansi.ansi()
                .fgBright(color)
                .bold()
                .a(prefix)
                .a(text)
                .boldOff()
                .reset()
                .toString();

        sb.append(formatted).append('\n');

        if (level <= 2) {
            String underline = "─".repeat(Math.min(text.length() + 4, width));
            sb.append(Ansi.ansi().fgBright(color).a(underline).reset().toString()).append('\n');
        }

        sb.append('\n');
    }

    private static void renderParagraph(Paragraph paragraph, StringBuilder sb) {
        String text = collectInlineText(paragraph);
        if (text.isBlank()) return;

        wrapText(sb, text, 0);
        sb.append('\n');
    }

    private static void renderBulletList(BulletList bulletList, StringBuilder sb, int indent) {
        sb.append('\n');
        Node child = bulletList.getFirstChild();
        while (child != null) {
            if (child instanceof ListItem) {
                renderListItemContent((ListItem) child, sb, indent, "•", null);
            }
            child = child.getNext();
        }
        sb.append('\n');
    }

    private static void renderOrderedList(OrderedList orderedList, StringBuilder sb, int indent) {
        sb.append('\n');
        int startNum = orderedList.getStartNumber();
        int itemIndex = startNum;
        Node child = orderedList.getFirstChild();
        while (child != null) {
            if (child instanceof ListItem) {
                String number = itemIndex + ".";
                renderListItemContent((ListItem) child, sb, indent, number, null);
            }
            child = child.getNext();
            itemIndex++;
        }
        sb.append('\n');
    }

    private static void renderListItem(ListItem listItem, StringBuilder sb, int indent) {
        renderListItemContent(listItem, sb, indent, "•", null);
    }

    private static void renderListItemContent(ListItem listItem, StringBuilder sb, int indent, String bullet, String delimiter) {
        String indentStr = "  ".repeat(indent);
        String bulletStr = indentStr + Ansi.ansi().fg(Ansi.Color.YELLOW).a(bullet).reset().toString() + " ";

        sb.append(bulletStr);

        Node child = listItem.getFirstChild();
        boolean first = true;
        while (child != null) {
            if (child instanceof Paragraph || child instanceof Text || child instanceof Code ||
                child instanceof Emphasis || child instanceof StrongEmphasis || child instanceof Link) {
                if (!first) {
                    sb.append('\n').append("  ".repeat(indent + 1));
                }
                renderNode(child, sb, indent + 1);
                first = false;
            } else if (child instanceof BulletList || child instanceof OrderedList) {
                sb.append('\n');
                renderNode(child, sb, indent + 1);
            } else {
                renderNode(child, sb, indent + 1);
            }
            child = child.getNext();
        }
        sb.append('\n');
    }

    private static void renderFencedCodeBlock(FencedCodeBlock codeBlock, StringBuilder sb) {
        String info = codeBlock.getInfo();
        String language = info != null && !info.isBlank() ? info : "";
        String code = codeBlock.getLiteral();
        int width = getWidth();
        int innerWidth = width - 4;

        sb.append('\n');

        if (!language.isBlank()) {
            sb.append(Ansi.ansi()
                    .fg(Ansi.Color.MAGENTA).bold()
                    .a("┌─ ").a(language).a(" ")
                    .a("─".repeat(Math.max(2, innerWidth - language.length() - 1)))
                    .reset().toString())
              .append('\n');
        } else {
            sb.append(Ansi.ansi()
                    .fg(Ansi.Color.MAGENTA)
                    .a("┌" + "─".repeat(innerWidth + 2))
                    .reset().toString())
              .append('\n');
        }

        String[] lines = code.split("\n", -1);
        for (String line : lines) {
            sb.append(Ansi.ansi()
                    .fg(Ansi.Color.CYAN)
                    .a("│ ")
                    .reset()
                    .a(escapeAnsi(line))
                    .toString())
              .append('\n');
        }

        sb.append(Ansi.ansi()
                .fg(Ansi.Color.MAGENTA)
                .a("└" + "─".repeat(innerWidth + 2))
                .reset().toString())
          .append('\n').append('\n');
    }

    private static void renderIndentedCodeBlock(IndentedCodeBlock codeBlock, StringBuilder sb) {
        String code = codeBlock.getLiteral();
        sb.append('\n');

        String[] lines = code.split("\n", -1);
        for (String line : lines) {
            sb.append(Ansi.ansi()
                    .fg(Ansi.Color.CYAN)
                    .a("  ")
                    .reset()
                    .a(escapeAnsi(line))
                    .toString())
              .append('\n');
        }
        sb.append('\n');
    }

    private static void renderBlockQuote(BlockQuote blockQuote, StringBuilder sb) {
        sb.append('\n');
        String text = collectInlineText(blockQuote);
        if (text.isBlank()) return;

        String[] lines = text.split("\n");
        for (String line : lines) {
            sb.append(Ansi.ansi()
                    .fg(Ansi.Color.YELLOW)
                    .a("▎ ")
                    .reset()
                    .fg(Ansi.Color.YELLOW)
                    .a(escapeAnsi(line.trim()))
                    .reset()
                    .toString())
              .append('\n');
        }
        sb.append('\n');
    }

    private static void renderThematicBreak(StringBuilder sb) {
        int width = getWidth();
        sb.append('\n');
        sb.append(Ansi.ansi()
                .fg(Ansi.Color.WHITE).bold()
                .a("─".repeat(width))
                .reset().toString())
          .append('\n').append('\n');
    }

    private static void renderInlineCode(Code code, StringBuilder sb) {
        sb.append(Ansi.ansi()
                .fg(Ansi.Color.CYAN).bold()
                .a(escapeAnsi(code.getLiteral()))
                .reset()
                .toString());
    }

    private static void renderEmphasis(Emphasis emphasis, StringBuilder sb) {
        sb.append(Ansi.ansi().a(Ansi.Attribute.ITALIC).a(collectInlineText(emphasis)).reset().toString());
    }

    private static void renderStrongEmphasis(StrongEmphasis strongEmphasis, StringBuilder sb) {
        sb.append(Ansi.ansi().bold().a(collectInlineText(strongEmphasis)).reset().toString());
    }

    private static void renderLink(Link link, StringBuilder sb) {
        String text = collectInlineText(link);
        String url = link.getDestination();

        sb.append(Ansi.ansi()
                .fg(Ansi.Color.BLUE).a(Ansi.Attribute.UNDERLINE)
                .a(escapeAnsi(text))
                .reset()
                .toString());

        if (url != null && !url.isBlank() && !url.equals(text)) {
            sb.append(Ansi.ansi()
                    .fg(Ansi.Color.WHITE)
                    .a(" [")
                    .fg(Ansi.Color.BLUE)
                    .a(escapeAnsi(url))
                    .fg(Ansi.Color.WHITE)
                    .a("]")
                    .reset()
                    .toString());
        }
    }

    private static void renderImage(Image image, StringBuilder sb) {
        String alt = image.getTitle() != null ? image.getTitle() : "image";
        sb.append(Ansi.ansi()
                .fg(Ansi.Color.MAGENTA).a(Ansi.Attribute.ITALIC)
                .a("[")
                .a(escapeAnsi(alt))
                .a("]")
                .reset()
                .toString());
    }

    private static void renderTable(TableBlock tableBlock, StringBuilder sb) {
        sb.append('\n');
        int width = getWidth();

        // Collect table data
        List<List<String>> rows = new ArrayList<>();
        List<Integer> colWidths = new ArrayList<>();

        Node child = tableBlock.getFirstChild();
        while (child != null) {
            if (child instanceof TableHead || child instanceof TableBody) {
                Node row = child.getFirstChild();
                while (row != null) {
                    if (row instanceof TableRow tableRow) {
                        List<String> cells = new ArrayList<>();
                        Node cell = tableRow.getFirstChild();
                        while (cell != null) {
                            String cellText = collectInlineText(cell).trim();
                            cells.add(cellText);
                            if (colWidths.size() < cells.size()) {
                                colWidths.add(0);
                            }
                            colWidths.set(cells.size() - 1,
                                Math.max(colWidths.get(cells.size() - 1), cellText.length() + 2));
                            cell = cell.getNext();
                        }
                        rows.add(cells);
                    }
                    row = row.getNext();
                }
            }
            child = child.getNext();
        }

        if (rows.isEmpty()) return;

        // Calculate column widths (capped to terminal width)
        int maxTotalWidth = width - 4;
        int totalCols = colWidths.size();
        int totalWidth = colWidths.stream().mapToInt(Integer::intValue).sum() + (totalCols - 1) * 3 + 2;

        if (totalWidth > maxTotalWidth) {
            double ratio = (double) (maxTotalWidth - (totalCols - 1) * 3 - 2) / (double) (totalWidth - (totalCols - 1) * 3 - 2);
            for (int i = 0; i < colWidths.size(); i++) {
                colWidths.set(i, Math.max(3, (int) (colWidths.get(i) * ratio)));
            }
        }

        // Render table
        for (int r = 0; r < rows.size(); r++) {
            List<String> cells = rows.get(r);

            // Draw separator after header row (row 0)
            // Use simple ASCII to avoid Unicode width issues in different terminals
            if (r == 0) {
                sb.append("  ");
                for (int c = 0; c < colWidths.size(); c++) {
                    int w = colWidths.get(c);
                    // Match the cell format: " " + padRight(text, w-1) → total w+1 chars including the trailing space?
                    // Actually cells are: " " + padRight(text, w-1) which is exactly w chars.
                    // So separator should be: "+" + "-".repeat(w-2) + "+" = w chars.
                    // But padRight pads to (w-1), and prepends " ", so total = w.
                    // Hmm, let's just use "-".repeat(w) for simplicity.
                    sb.append(Ansi.ansi().fg(Ansi.Color.WHITE)
                            .a("+" + "-".repeat(Math.max(0, w - 2)) + "+")
                            .reset().toString());
                }
                sb.append('\n');
            }

            sb.append("  ");
            for (int c = 0; c < colWidths.size(); c++) {
                int w = colWidths.get(c);
                String cellText = c < cells.size() ? cells.get(c) : "";
                boolean isHeader = r == 0;

                if (isHeader) {
                    sb.append(Ansi.ansi()
                            .fg(Ansi.Color.WHITE).bold()
                            .a(" ").a(padRight(cellText, w - 1))
                            .reset().toString());
                } else {
                    sb.append(" ").append(padRight(cellText, w - 1));
                }
            }
            sb.append('\n');
        }
        sb.append('\n');
    }

    // ---- Helpers ----

    private static String collectInlineText(Node parent) {
        StringBuilder sb = new StringBuilder();
        collectInlineTextRecursive(parent, sb);
        return sb.toString();
    }

    private static void collectInlineTextRecursive(Node node, StringBuilder sb) {
        if (node instanceof Text text) {
            sb.append(text.getLiteral());
        } else if (node instanceof Code code) {
            sb.append(code.getLiteral());
        } else if (node instanceof Link link) {
            Node child = link.getFirstChild();
            while (child != null) {
                collectInlineTextRecursive(child, sb);
                child = child.getNext();
            }
        } else if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
            sb.append(' ');
        } else {
            Node child = node.getFirstChild();
            while (child != null) {
                collectInlineTextRecursive(child, sb);
                child = child.getNext();
            }
        }
    }

    private static void wrapText(StringBuilder sb, String text, int indent) {
        String indentStr = "  ".repeat(indent);
        int maxWidth = getWidth() - indent * 2;
        int indentLen = indentStr.length();

        if (maxWidth <= 0) {
            sb.append(indentStr).append(text);
            return;
        }

        if (maxWidth < indentLen) {
            maxWidth = indentLen;
        }

        String[] words = text.split(" ");
        int currentLineLen = indentLen;

        for (String word : words) {
            if (currentLineLen == indentLen) {
                sb.append(indentStr);
                sb.append(word);
                currentLineLen += word.length();
            } else if (currentLineLen + 1 + word.length() <= maxWidth) {
                sb.append(' ').append(word);
                currentLineLen += 1 + word.length();
            } else {
                sb.append('\n').append(indentStr);
                sb.append(word);
                currentLineLen = indentLen + word.length();
            }
        }
    }

    private static String padRight(String s, int len) {
        if (s == null) return " ".repeat(len);
        if (s.length() >= len) return s.substring(0, len);
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < len) sb.append(' ');
        return sb.toString();
    }

    private static String escapeAnsi(String s) {
        if (s == null) return "";
        return s.replace("\u001B", "\\e");
    }
}
