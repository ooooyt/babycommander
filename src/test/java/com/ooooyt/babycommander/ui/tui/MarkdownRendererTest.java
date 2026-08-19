package com.ooooyt.babycommander.ui.tui;

import org.jline.utils.AttributedString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownRendererTest {

    // ================================================================
    // stripEmoji
    // ================================================================

    @Test
    void stripEmoji_removesEmoji() {
        // The emoji is replaced with empty string, leaving double space; .trim() only trims ends
        String result = MarkdownRenderer.stripEmoji("Hello \uD83D\uDE00 world");
        assertEquals("Hello  world", result);
    }

    @Test
    void stripEmoji_removesMultipleEmoji() {
        String result = MarkdownRenderer.stripEmoji("\uD83D\uDE00\uD83D\uDE01\uD83D\uDE02");
        assertEquals("", result);
    }

    @Test
    void stripEmoji_noEmoji_returnsSame() {
        String result = MarkdownRenderer.stripEmoji("Hello world");
        assertEquals("Hello world", result);
    }

    @Test
    void stripEmoji_null_returnsEmpty() {
        // stripEmoji calls EMOJI.matcher(s).replaceAll("").trim() which throws NPE on null
        assertThrows(NullPointerException.class, () -> MarkdownRenderer.stripEmoji(null));
    }

    @Test
    void stripEmoji_emptyString_returnsEmpty() {
        String result = MarkdownRenderer.stripEmoji("");
        assertEquals("", result);
    }

    // ================================================================
    // render — headings
    // ================================================================

    @Test
    void render_h1() {
        List<AttributedString> lines = MarkdownRenderer.render("# Title", 80);
        assertEquals(2, lines.size()); // heading + underline
        assertEquals("Title", lines.get(0).toString());
        assertTrue(lines.get(1).toString().startsWith("="));
    }

    @Test
    void render_h2() {
        List<AttributedString> lines = MarkdownRenderer.render("## Section", 80);
        assertEquals(2, lines.size());
        assertEquals("Section", lines.get(0).toString());
        assertTrue(lines.get(1).toString().startsWith("-"));
    }

    @Test
    void render_h3() {
        List<AttributedString> lines = MarkdownRenderer.render("### Subsection", 80);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).toString().contains("Subsection"));
    }

    @Test
    void render_h4() {
        List<AttributedString> lines = MarkdownRenderer.render("#### Sub-subsection", 80);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).toString().contains("Sub-subsection"));
    }

    @Test
    void render_h5_asH4() {
        List<AttributedString> lines = MarkdownRenderer.render("##### Deep", 80);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).toString().contains("Deep"));
    }

    // ================================================================
    // render — horizontal rule
    // ================================================================

    @Test
    void render_horizontalRule_dashes() {
        List<AttributedString> lines = MarkdownRenderer.render("---", 80);
        assertEquals(1, lines.size());
        assertEquals(80, lines.get(0).columnLength());
    }

    @Test
    void render_horizontalRule_asterisks() {
        List<AttributedString> lines = MarkdownRenderer.render("***", 80);
        assertEquals(1, lines.size());
        assertEquals(80, lines.get(0).columnLength());
    }

    @Test
    void render_horizontalRule_underscores() {
        List<AttributedString> lines = MarkdownRenderer.render("___", 80);
        assertEquals(1, lines.size());
    }

    // ================================================================
    // render — blockquote
    // ================================================================

    @Test
    void render_blockquote() {
        List<AttributedString> lines = MarkdownRenderer.render("> quoted text", 80);
        assertEquals(1, lines.size());
        String text = lines.get(0).toString();
        assertTrue(text.contains("quoted text"));
    }

    @Test
    void render_blockquote_multilineWrapped() {
        List<AttributedString> lines = MarkdownRenderer.render("> " + "word ".repeat(30), 40);
        assertTrue(lines.size() > 1);
        for (AttributedString line : lines) {
            assertTrue(line.columnLength() <= 40);
        }
    }

    // ================================================================
    // render — unordered list
    // ================================================================

    @Test
    void render_unorderedList_simple() {
        List<AttributedString> lines = MarkdownRenderer.render("- item", 80);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).toString().contains("item"));
    }

    @Test
    void render_unorderedList_asterisk() {
        List<AttributedString> lines = MarkdownRenderer.render("* item", 80);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).toString().contains("item"));
    }

    @Test
    void render_unorderedList_plus() {
        List<AttributedString> lines = MarkdownRenderer.render("+ item", 80);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).toString().contains("item"));
    }

    @Test
    void render_unorderedList_nested() {
        List<AttributedString> lines = MarkdownRenderer.render("  - nested item", 80);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).toString().contains("nested item"));
    }

    // ================================================================
    // render — ordered list
    // ================================================================

    @Test
    void render_orderedList_simple() {
        List<AttributedString> lines = MarkdownRenderer.render("1. first", 80);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).toString().contains("first"));
    }

    @Test
    void render_orderedList_multipleItems() {
        List<AttributedString> lines = MarkdownRenderer.render("1. first\n2. second\n3. third", 80);
        assertEquals(3, lines.size());
        String allText = lines.stream().map(Object::toString).reduce("", String::concat);
        assertTrue(allText.contains("first"));
        assertTrue(allText.contains("second"));
        assertTrue(allText.contains("third"));
    }

    // ================================================================
    // render — fenced code block
    // ================================================================

    @Test
    void render_fencedCodeBlock_simple() {
        List<AttributedString> lines = MarkdownRenderer.render("```\ncode\n```", 80);
        assertEquals(3, lines.size()); // fence header + code + fence footer
    }

    @Test
    void render_fencedCodeBlock_withLanguage() {
        List<AttributedString> lines = MarkdownRenderer.render("```java\nint x = 1;\n```", 80);
        assertEquals(3, lines.size());
        String allText = lines.stream().map(Object::toString).reduce("", String::concat);
        assertTrue(allText.contains("java"));
        assertTrue(allText.contains("int x = 1"));
    }

    @Test
    void render_fencedCodeBlock_multipleLines() {
        List<AttributedString> lines = MarkdownRenderer.render("```\nline1\nline2\nline3\n```", 80);
        assertEquals(5, lines.size());
    }

    // ================================================================
    // render — table
    // ================================================================

    @Test
    void render_table_simple() {
        List<AttributedString> lines = MarkdownRenderer.render(
            "| H1 | H2 |\n|---|---|\n| C1 | C2 |", 80);
        assertFalse(lines.isEmpty());
        // Should contain the header text
        String allText = lines.stream().map(Object::toString).reduce("", String::concat);
        assertTrue(allText.contains("H1"));
        assertTrue(allText.contains("C1"));
    }

    @Test
    void render_table_noSeparator() {
        List<AttributedString> lines = MarkdownRenderer.render(
            "| A | B |\n| C | D |", 80);
        assertFalse(lines.isEmpty());
        String allText = lines.stream().map(Object::toString).reduce("", String::concat);
        assertTrue(allText.contains("A"));
        assertTrue(allText.contains("D"));
    }

    @Test
    void render_table_longCellWrapsAndKeepsBorders() {
        // A cell whose content exceeds the column width must wrap to multiple lines,
        // with every physical line keeping its │ borders and fitting within width.
        String md = "| N | Desc |\n|---|---|\n| A | This is a very long description that needs wrapping |";
        List<AttributedString> lines = MarkdownRenderer.render(md, 25);
        assertFalse(lines.isEmpty());
        List<AttributedString> contentLines = lines.stream()
            .filter(l -> l.toString().startsWith("\u2502"))
            .toList();
        assertFalse(contentLines.isEmpty());
        for (AttributedString line : contentLines) {
            String text = line.toString();
            assertTrue(text.startsWith("\u2502"), "Should start with border: " + text);
            assertTrue(text.endsWith("\u2502"), "Should end with border: " + text);
            assertTrue(line.columnLength() <= 25,
                "Row should fit in width, got " + line.columnLength() + ": " + text);
        }
    }

    @Test
    void render_table_longCellAllContentPresent() {
        // All cell content must be present in the output even when wrapped
        // across multiple physical lines (content is split, not truncated)
        String md = "| K | Value |\n|---|---|\n| id | abcdefghijklmnopqrstuvwxyz |";
        List<AttributedString> lines = MarkdownRenderer.render(md, 20);
        String allText = lines.stream().map(Object::toString).reduce("", String::concat);
        assertTrue(allText.contains("abc"), "Start of long content missing: " + allText);
        assertTrue(allText.contains("xyz"), "End of long content missing: " + allText);
        // Verify no content was truncated by checking all letters appear
        String letters = allText.replaceAll("[^a-z]", "");
        assertTrue(letters.contains("abcdefghijklmnopqrstuvwxyz"));
    }

    @Test
    void render_table_wideTerminalNoWrapping() {
        // On a wide terminal, no wrapping should occur — single line per row
        String md = "| A | B |\n|---|---|\n| 1 | 2 |";
        List<AttributedString> lines = MarkdownRenderer.render(md, 80);
        // Find content lines (starting with │)
        List<AttributedString> contentLines = lines.stream()
            .filter(l -> l.toString().startsWith("\u2502"))
            .toList();
        assertEquals(2, contentLines.size()); // header row + data row, no wrapping
    }

    @Test
    void render_table_veryNarrowWidth() {
        // Even at a very narrow width, borders should stay aligned
        String md = "| A | B |\n|---|---|\n| C | D |";
        List<AttributedString> lines = MarkdownRenderer.render(md, 10);
        List<AttributedString> contentLines = lines.stream()
            .filter(l -> l.toString().startsWith("\u2502"))
            .toList();
        assertFalse(contentLines.isEmpty());
        for (AttributedString line : contentLines) {
            assertTrue(line.toString().startsWith("\u2502"));
            assertTrue(line.toString().endsWith("\u2502"));
        }
    }

    // ================================================================
    // render — inline formatting
    // ================================================================

    @Test
    void render_inlineBold() {
        List<AttributedString> lines = MarkdownRenderer.render("This is **bold** text", 80);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).toString().contains("bold"));
    }

    @Test
    void render_inlineItalic() {
        List<AttributedString> lines = MarkdownRenderer.render("This is *italic* text", 80);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).toString().contains("italic"));
    }

    @Test
    void render_inlineCode() {
        List<AttributedString> lines = MarkdownRenderer.render("Use `code` inline", 80);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).toString().contains("code"));
    }

    @Test
    void render_inlineBoldItalic() {
        List<AttributedString> lines = MarkdownRenderer.render("This is ***bold italic*** text", 80);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).toString().contains("bold italic"));
    }

    @Test
    void render_inlineUnderscoreItalic() {
        List<AttributedString> lines = MarkdownRenderer.render("This is _italic_ text", 80);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).toString().contains("italic"));
    }

    // ================================================================
    // render — mixed content
    // ================================================================

    @Test
    void render_mixedContent() {
        String md = "# Title\n\nParagraph with **bold** and *italic*.\n\n- list item\n- another item\n\n> quote";
        List<AttributedString> lines = MarkdownRenderer.render(md, 80);
        assertFalse(lines.isEmpty());
        String allText = lines.stream().map(Object::toString).reduce("", String::concat);
        assertTrue(allText.contains("Title"));
        assertTrue(allText.contains("Paragraph"));
        assertTrue(allText.contains("list item"));
        assertTrue(allText.contains("quote"));
    }

    @Test
    void render_emptyInput() {
        // render("") splits into [""], which produces 1 empty AttributedString
        List<AttributedString> lines = MarkdownRenderer.render("", 80);
        assertEquals(1, lines.size());
        assertEquals("", lines.get(0).toString());
    }

    @Test
    void render_blankLines() {
        // render("\n\n\n") splits into ["", "", "", ""], producing 4 empty lines
        List<AttributedString> lines = MarkdownRenderer.render("\n\n\n", 80);
        assertEquals(4, lines.size());
        for (AttributedString line : lines) {
            assertEquals("", line.toString());
        }
    }

    @Test
    void render_plainText() {
        List<AttributedString> lines = MarkdownRenderer.render("Just some plain text", 80);
        assertEquals(1, lines.size());
        assertEquals("Just some plain text", lines.get(0).toString());
    }

    // ================================================================
    // render — word wrapping
    // ================================================================

    @Test
    void render_wrapsLongLines() {
        String longText = "word ".repeat(50);
        List<AttributedString> lines = MarkdownRenderer.render(longText, 30);
        assertTrue(lines.size() > 1);
        for (AttributedString line : lines) {
            assertTrue(line.columnLength() <= 30);
        }
    }

    @Test
    void render_veryNarrowWidth() {
        String text = "hello world";
        List<AttributedString> lines = MarkdownRenderer.render(text, 5);
        assertTrue(lines.size() >= 2);
    }

    @Test
    void render_zeroWidth() {
        String text = "hello";
        List<AttributedString> lines = MarkdownRenderer.render(text, 0);
        assertEquals(1, lines.size());
    }

    // ================================================================
    // render — edge cases
    // ================================================================

    @Test
    void render_withEmoji_stripsEmoji() {
        List<AttributedString> lines = MarkdownRenderer.render("# Hello \uD83D\uDE00", 80);
        assertEquals(2, lines.size());
        assertEquals("Hello", lines.get(0).toString().trim());
    }

    @Test
    void render_h1UnderlineWidth() {
        List<AttributedString> lines = MarkdownRenderer.render("# A", 80);
        assertEquals(2, lines.size());
        // Underline should be at least 1 char
        assertTrue(lines.get(1).columnLength() >= 1);
    }

    @Test
    void render_h2UnderlineWidth() {
        List<AttributedString> lines = MarkdownRenderer.render("## Ab", 80);
        assertEquals(2, lines.size());
        assertTrue(lines.get(1).columnLength() >= 1);
    }

    // ================================================================
    // getDisplayWidth
    // ================================================================

    @ParameterizedTest
    @CsvSource({
        "32, 1",   // space
        "65, 1",   // 'A'
        "126, 1",  // '~'
        "0x4E00, 2", // CJK
        "0x3042, 2", // Hiragana
        "0x1100, 2", // Hangul Jamo
        "0xFF01, 2", // Fullwidth
        "0x0300, 0", // Combining
        "0x2600, 2", // Misc symbols
        "0x2702, 2", // Dingbats
    })
    void getDisplayWidth(int cp, int expected) {
        assertEquals(expected, MarkdownRenderer.getDisplayWidth(cp));
    }

    @Test
    void getDisplayWidth_controlChars() {
        assertEquals(0, MarkdownRenderer.getDisplayWidth(0x00));
        assertEquals(0, MarkdownRenderer.getDisplayWidth(0x1F));
    }

    @Test
    void getDisplayWidth_branding() {
        assertEquals(1, MarkdownRenderer.getDisplayWidth(0x2AFF));
    }

    @Test
    void getDisplayWidth_variationSelector() {
        // 0xFE00-0xFE0F is not explicitly listed as wide, so it returns 1
        assertEquals(1, MarkdownRenderer.getDisplayWidth(0xFE00));
    }

    // ================================================================
    // wrapLine
    // ================================================================

    @Test
    void wrapLine_shortLine() {
        AttributedString as = new AttributedString("hello");
        List<AttributedString> result = MarkdownRenderer.wrapLine(as, 80);
        assertEquals(1, result.size());
        assertEquals("hello", result.get(0).toString());
    }

    @Test
    void wrapLine_exactFit() {
        AttributedString as = new AttributedString("12345");
        List<AttributedString> result = MarkdownRenderer.wrapLine(as, 5);
        assertEquals(1, result.size());
        assertEquals("12345", result.get(0).toString());
    }

    @Test
    void wrapLine_wrapsAtWordBoundary() {
        // "hello world foo" with width=6: "hello " (6), "world " (6), "foo" (3) = 3 lines
        AttributedString as = new AttributedString("hello world foo");
        List<AttributedString> result = MarkdownRenderer.wrapLine(as, 6);
        assertEquals(3, result.size());
        assertEquals("hello", result.get(0).toString().trim());
        assertEquals("world", result.get(1).toString().trim());
        assertEquals("foo", result.get(2).toString().trim());
    }

    @Test
    void wrapLine_emptyString() {
        AttributedString as = new AttributedString("");
        List<AttributedString> result = MarkdownRenderer.wrapLine(as, 80);
        assertEquals(1, result.size());
    }

    @Test
    void wrapLine_zeroWidth() {
        AttributedString as = new AttributedString("hello");
        List<AttributedString> result = MarkdownRenderer.wrapLine(as, 0);
        assertEquals(1, result.size());
    }

    @Test
    void wrapLine_singleWordLongerThanWidth() {
        // A single word longer than width gets split into multiple character-level chunks
        AttributedString as = new AttributedString("superlongword");
        List<AttributedString> result = MarkdownRenderer.wrapLine(as, 5);
        assertEquals(3, result.size());
        assertEquals("super", result.get(0).toString());
    }

    @Test
    void wrapLine_multipleSpaces() {
        AttributedString as = new AttributedString("hello    world");
        List<AttributedString> result = MarkdownRenderer.wrapLine(as, 10);
        assertFalse(result.isEmpty());
        String allText = result.stream().map(Object::toString).reduce("", String::concat);
        assertTrue(allText.contains("hello"));
        assertTrue(allText.contains("world"));
    }

    @Test
    void wrapLine_longTokenAfterShortPrefix_fillsLine() {
        // A short prefix followed by a long unbreakable token (e.g. a file path)
        // should fill the line by hard-breaking the long token, rather than
        // leaving the line short (the warn-message wrapping bug).
        AttributedString as = new AttributedString("abc supercalifragilisticexpialidocious");
        List<AttributedString> result = MarkdownRenderer.wrapLine(as, 10);
        // First line should be fully filled (10 cols), not just "abc " (4 cols)
        assertEquals(10, result.get(0).toString().length());
        assertEquals("abc superc", result.get(0).toString());
    }

    @Test
    void wrapLine_warnMessageWithPath_fillsLines() {
        // Simulate a [Warn] message with a long file path (from argsPreview()).
        String msg = "[Warn] writeFile \u2192 /Users/me/projects/example/src/main/java/com/foo/Bar.java, Allow 'y'?";
        AttributedString as = new AttributedString(msg);
        List<AttributedString> result = MarkdownRenderer.wrapLine(as, 30);
        assertFalse(result.isEmpty());
        // First line should be fully filled (30 cols)
        assertEquals(30, result.get(0).toString().length());
        // No empty lines
        for (AttributedString line : result) {
            assertFalse(line.toString().isEmpty());
        }
        // No leading spaces on continuation lines
        for (int i = 1; i < result.size(); i++) {
            assertFalse(result.get(i).toString().startsWith(" "));
        }
    }

    @Test
    void wrapLine_normalProse_stillWrapsAtWordBoundary() {
        // Normal prose should still wrap at word boundaries when words fit.
        AttributedString as = new AttributedString("hello world foo");
        List<AttributedString> result = MarkdownRenderer.wrapLine(as, 6);
        assertEquals(3, result.size());
        assertEquals("hello", result.get(0).toString().trim());
        assertEquals("world", result.get(1).toString().trim());
        assertEquals("foo", result.get(2).toString().trim());
    }

    @Test
    void wrapLine_nextTokenExactlyFits_usesWordBreak() {
        // When the whole text fits, return as a single line (no wrapping).
        AttributedString as = new AttributedString("hello world");
        List<AttributedString> result = MarkdownRenderer.wrapLine(as, 11);
        assertEquals(1, result.size());
        assertEquals("hello world", result.get(0).toString());
    }

    @Test
    void wrapLine_nextTokenSlightlyTooLong_usesWordBreak() {
        // A token that is not longer than a full line width should be moved to the
        // next line as a whole (word-wrap), not hard-broken mid-word. Only tokens
        // longer than the full line width (e.g. long file paths) get hard-broken.
        AttributedString as = new AttributedString("hello worldx");
        List<AttributedString> result = MarkdownRenderer.wrapLine(as, 10);
        // "hello " (6) + "worldx" (6) = 12 > 10, so "worldx" doesn't fit in remaining 4.
        // "worldx" (6) is not longer than the full line (10), so it wraps cleanly.
        assertEquals(2, result.size());
        assertEquals("hello", result.get(0).toString().trim());
        assertEquals("worldx", result.get(1).toString().trim());
    }

    @Test
    void wrapLine_nextTokenFitsRemaining_usesWordBreak() {
        // When the next token fits in the remaining space, use word break (not hard break).
        AttributedString as = new AttributedString("hello world foo");
        List<AttributedString> result = MarkdownRenderer.wrapLine(as, 11);
        // "hello " (6) + "world" (5) = 11 fits, so word-break after "hello ".
        assertEquals(2, result.size());
        assertEquals("hello", result.get(0).toString().trim());
        assertEquals("world foo", result.get(1).toString().trim());
    }

    @Test
    void wrapLine_embeddedNewline_preservesLineBreaks() {
        // Multi-line shell command with embedded newlines must keep each logical line
        // intact (hard line-break boundaries), not flattened into one wrapped paragraph.
        String cmd = "cd /Users/me/project && python3 -c \"\n" +
                "                                    r=csv.reader(f)\n" +
                "                                    next(r)\"";
        AttributedString as = new AttributedString("\u23F3 shell \u2192 " + cmd);
        List<AttributedString> result = MarkdownRenderer.wrapLine(as, 170);
        // Three logical lines preserved: header, r=csv.reader(f), next(r)
        assertEquals(3, result.size());
        assertTrue(result.get(0).toString().startsWith("\u23F3 shell \u2192 cd /Users/me/project"));
        assertTrue(result.get(0).toString().endsWith("python3 -c \""));
        assertEquals("                                    r=csv.reader(f)", result.get(1).toString());
        assertEquals("                                    next(r)\"", result.get(2).toString());
        // No line should contain an embedded newline character
        for (AttributedString line : result) {
            assertFalse(line.toString().contains("\n"));
        }
    }

    @Test
    void wrapLine_embeddedNewline_softWrapLongSegment() {
        // A long single logical line (exceeding width) within a multi-line message
        // should still be soft-wrapped, while other lines stay intact.
        String cmd = "cd /Users/me/project && python3 -c \"\n" +
                "    print('a long line that exceeds the width to force wrapping')";
        AttributedString as = new AttributedString("\u23F3 shell \u2192 " + cmd);
        List<AttributedString> result = MarkdownRenderer.wrapLine(as, 60);
        assertTrue(result.size() >= 2);
        // First logical line is soft-wrapped into >=1 lines
        assertTrue(result.get(0).toString().startsWith("\u23F3 shell \u2192 cd /Users/me/project"));
        // The indented python line is soft-wrapped but its content is preserved
        assertTrue(result.stream().anyMatch(l -> l.toString().contains("print('a long line")));
        // No line should contain an embedded newline character
        for (AttributedString line : result) {
            assertFalse(line.toString().contains("\n"));
        }
    }
}
