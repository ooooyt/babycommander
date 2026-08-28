package com.ooooyt.babycommander.shguard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ShWord} dequoting and static-analysis.
 */
class ShWordTest {

    @Test
    void testDequoteQuotedCommandName() {
        assertEquals("rm", ShWord.parse("r\"m\"").dequoted());
        assertEquals("rm", ShWord.parse("\\rm").dequoted());
        assertEquals("rm", ShWord.parse("'rm'").dequoted());
        assertEquals("rm", ShWord.parse("r'm'").dequoted());
        assertEquals("rm", ShWord.parse("\\r\\m").dequoted());
    }

    @Test
    void testDequoteDoubleQuotedWithExpansion() {
        ShWord w = ShWord.parse("\"a$VAR b\"");
        assertEquals("a$VAR b", w.dequoted());
        assertFalse(w.isFullyStatic());
        assertTrue(w.anyPartQuoted());
    }

    @Test
    void testSingleQuotedIsFullyStatic() {
        // 'rm -rf /' is one literal word (not split), fully static.
        ShWord w = ShWord.parse("'rm -rf /'");
        assertEquals("rm -rf /", w.dequoted());
        assertTrue(w.isFullyStatic());
        assertTrue(w.anyPartQuoted());
    }

    @Test
    void testVariableNotStatic() {
        ShWord w = ShWord.parse("$VAR");
        assertFalse(w.isFullyStatic());
        assertTrue(w.hasVariable());
        assertEquals("$VAR", w.dequoted());
    }

    @Test
    void testCommandSubstitutionDetected() {
        ShWord w = ShWord.parse("$(rm -rf /)");
        assertFalse(w.isFullyStatic());
        assertTrue(w.hasCommandSubstitution());
        assertEquals("rm -rf /", w.commandSubstitutionBody());
    }

    @Test
    void testBacktickSubstitutionDetected() {
        ShWord w = ShWord.parse("`rm -rf /`");
        assertTrue(w.hasCommandSubstitution());
        assertEquals("rm -rf /", w.commandSubstitutionBody());
    }

    @Test
    void testPlainWord() {
        ShWord w = ShWord.parse("git");
        assertEquals("git", w.dequoted());
        assertTrue(w.isFullyStatic());
        assertFalse(w.anyPartQuoted());
    }

    @Test
    void testAnsiCQuote() {
        ShWord w = ShWord.parse("$'rm'");
        assertEquals("rm", w.dequoted());
        assertTrue(w.isFullyStatic());
    }

    @Test
    void testEscapedSpaceInsideWord() {
        // A space escaped with backslash is part of the word.
        ShWord w = ShWord.parse("foo\\ bar");
        assertEquals("foo bar", w.dequoted());
        assertTrue(w.isFullyStatic());
    }

    @Test
    void testArithmeticExpansionNotStatic() {
        ShWord w = ShWord.parse("$((1+1))");
        assertFalse(w.isFullyStatic());
    }
}