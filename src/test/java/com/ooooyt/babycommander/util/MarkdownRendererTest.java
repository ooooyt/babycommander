package com.ooooyt.babycommander.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownRendererTest {

    @Test
    @DisplayName("render should return empty string for null input")
    void testRenderNullReturnsEmpty() {
        assertEquals("", MarkdownRenderer.render(null));
    }

    @Test
    @DisplayName("render should return empty string for blank input")
    void testRenderBlankReturnsEmpty() {
        assertEquals("", MarkdownRenderer.render(""));
        assertEquals("", MarkdownRenderer.render("   "));
        assertEquals("", MarkdownRenderer.render("\n\t "));
    }

    @Test
    @DisplayName("render should handle plain text")
    void testRenderPlainText() {
        String result = MarkdownRenderer.render("Hello, world!");
        assertNotNull(result);
        assertTrue(result.contains("Hello, world!"));
    }

    @Test
    @DisplayName("render should handle headings level 1")
    void testRenderHeading1() {
        String result = MarkdownRenderer.render("# Title");
        assertNotNull(result);
        assertTrue(result.contains("Title"));
    }

    @Test
    @DisplayName("render should handle headings level 2")
    void testRenderHeading2() {
        String result = MarkdownRenderer.render("## Subtitle");
        assertNotNull(result);
        assertTrue(result.contains("Subtitle"));
    }

    @Test
    @DisplayName("render should handle headings level 3")
    void testRenderHeading3() {
        String result = MarkdownRenderer.render("### Section");
        assertNotNull(result);
        assertTrue(result.contains("Section"));
    }

    @Test
    @DisplayName("render should handle bold text")
    void testRenderBold() {
        String result = MarkdownRenderer.render("This is **bold** text");
        assertNotNull(result);
        assertTrue(result.contains("bold"));
    }

    @Test
    @DisplayName("render should handle italic text")
    void testRenderItalic() {
        String result = MarkdownRenderer.render("This is *italic* text");
        assertNotNull(result);
        assertTrue(result.contains("italic"));
    }

    @Test
    @DisplayName("render should handle inline code")
    void testRenderInlineCode() {
        String result = MarkdownRenderer.render("Use `code` inline");
        assertNotNull(result);
        assertTrue(result.contains("code"));
    }

    @Test
    @DisplayName("render should handle fenced code blocks")
    void testRenderFencedCodeBlock() {
        String result = MarkdownRenderer.render("```java\nSystem.out.println(\"hello\");\n```");
        assertNotNull(result);
        assertTrue(result.contains("System.out.println"));
    }

    @Test
    @DisplayName("render should handle fenced code blocks with language")
    void testRenderFencedCodeBlockWithLanguage() {
        String result = MarkdownRenderer.render("```python\nprint('hello')\n```");
        assertNotNull(result);
        assertTrue(result.contains("python"));
        assertTrue(result.contains("print"));
    }

    @Test
    @DisplayName("render should handle bullet lists")
    void testRenderBulletList() {
        String result = MarkdownRenderer.render("- item1\n- item2\n- item3");
        assertNotNull(result);
        assertTrue(result.contains("item1"));
        assertTrue(result.contains("item2"));
        assertTrue(result.contains("item3"));
    }

    @Test
    @DisplayName("render should handle ordered lists")
    void testRenderOrderedList() {
        String result = MarkdownRenderer.render("1. first\n2. second\n3. third");
        assertNotNull(result);
        assertTrue(result.contains("first"));
        assertTrue(result.contains("second"));
        assertTrue(result.contains("third"));
    }

    @Test
    @DisplayName("render should handle blockquotes")
    void testRenderBlockquote() {
        String result = MarkdownRenderer.render("> This is a quote");
        assertNotNull(result);
        assertTrue(result.contains("quote"));
    }

    @Test
    @DisplayName("render should handle horizontal rules")
    void testRenderThematicBreak() {
        String result = MarkdownRenderer.render("---");
        assertNotNull(result);
    }

    @Test
    @DisplayName("render should handle links")
    void testRenderLink() {
        String result = MarkdownRenderer.render("[click here](https://example.com)");
        assertNotNull(result);
        assertTrue(result.contains("click here"));
    }

    @Test
    @DisplayName("render should handle images")
    void testRenderImage() {
        String result = MarkdownRenderer.render("![alt](image.png)");
        assertNotNull(result);
    }

    @Test
    @DisplayName("render should handle GFM tables")
    void testRenderTable() {
        String markdown = "| Col1 | Col2 |\n|------|------|\n| A    | B    |";
        String result = MarkdownRenderer.render(markdown);
        assertNotNull(result);
        assertTrue(result.contains("Col1"));
        assertTrue(result.contains("Col2"));
        assertTrue(result.contains("A"));
        assertTrue(result.contains("B"));
    }

    @Test
    @DisplayName("render should handle mixed content")
    void testRenderMixedContent() {
        String markdown =
            "# Document\n\n" +
            "This is a **paragraph** with `code`.\n\n" +
            "- list item 1\n" +
            "- list item 2\n\n" +
            "```\ncode block\n```";
        String result = MarkdownRenderer.render(markdown);
        assertNotNull(result);
        assertTrue(result.contains("Document"));
        assertTrue(result.contains("paragraph"));
        assertTrue(result.contains("code"));
    }

    @Test
    @DisplayName("print should not throw exception for valid input")
    void testPrintDoesNotThrow() {
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(new ByteArrayOutputStream()));
            assertDoesNotThrow(() -> MarkdownRenderer.print("Hello, world!"));
        } finally {
            System.setOut(original);
        }
    }

    @Test
    @DisplayName("print should not throw exception for null input")
    void testPrintNullDoesNotThrow() {
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(new ByteArrayOutputStream()));
            assertDoesNotThrow(() -> MarkdownRenderer.print(null));
        } finally {
            System.setOut(original);
        }
    }

    @Test
    @DisplayName("print should not throw exception for blank input")
    void testPrintBlankDoesNotThrow() {
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(new ByteArrayOutputStream()));
            assertDoesNotThrow(() -> MarkdownRenderer.print(""));
            assertDoesNotThrow(() -> MarkdownRenderer.print("   "));
        } finally {
            System.setOut(original);
        }
    }

    @Test
    @DisplayName("render should handle code block with special characters")
    void testRenderCodeBlockWithSpecialChars() {
        String result = MarkdownRenderer.render("```\n<tag> & \"quote\"\n```");
        assertNotNull(result);
        assertTrue(result.contains("<tag>"));
    }

    @Test
    @DisplayName("render should handle nested emphasis")
    void testRenderNestedEmphasis() {
        String result = MarkdownRenderer.render("***bold and italic***");
        assertNotNull(result);
    }

    @Test
    @DisplayName("render should handle multiple paragraphs")
    void testRenderMultipleParagraphs() {
        String result = MarkdownRenderer.render("First paragraph.\n\nSecond paragraph.");
        assertNotNull(result);
        assertTrue(result.contains("First paragraph"));
        assertTrue(result.contains("Second paragraph"));
    }

    @Test
    @DisplayName("render should handle soft line breaks")
    void testRenderSoftLineBreak() {
        String result = MarkdownRenderer.render("line1\nline2");
        assertNotNull(result);
        assertTrue(result.contains("line1"));
        assertTrue(result.contains("line2"));
    }
}
