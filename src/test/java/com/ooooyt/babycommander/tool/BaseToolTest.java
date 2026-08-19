package com.ooooyt.babycommander.tool;

import com.ooooyt.babycommander.hook.HookManager;
import com.ooooyt.babycommander.util.I18n;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class BaseToolTest {

    @Mock
    HookManager hookManager;

    private TestTool testTool;

    @BeforeAll
    static void setupLocale() {
        I18n.setLocale(Locale.ENGLISH);
    }

    @BeforeEach
    void setUp() {
        testTool = new TestTool(hookManager);
    }

    @Test
    void testTruncateSafely_NullInput() {
        String result = BaseTool.truncateSafely((String) null, 100);
        assertNull(result);
    }

    @Test
    void testTruncateSafely_ShortString() {
        String result = BaseTool.truncateSafely("short", 100);
        assertEquals("short", result);
    }

    @Test
    void testTruncateSafely_ExactLength() {
        String input = "exact";
        String result = BaseTool.truncateSafely(input, 5);
        assertEquals("exact", result);
    }

    @Test
    void testTruncateSafely_LongString() {
        String input = "a".repeat(1000);
        String result = BaseTool.truncateSafely(input, 500);
        assertNotNull(result);
        assertTrue(result.length() <= 500);
    }

    @Test
    void testTruncateSafely_ZeroMaxLength() {
        String result = BaseTool.truncateSafely("test", 0);
        assertEquals("", result);
    }

    @Test
    void testTruncateSafely_StringBuilder_Null() {
        BaseTool.truncateSafely((StringBuilder) null, 100);
        // No exception expected
        assertTrue(true);
    }

    @Test
    void testTruncateSafely_StringBuilder_Short() {
        StringBuilder sb = new StringBuilder("short");
        BaseTool.truncateSafely(sb, 100);
        assertEquals("short", sb.toString());
    }

    @Test
    void testTruncateSafely_StringBuilder_Long() {
        StringBuilder sb = new StringBuilder("a".repeat(100));
        BaseTool.truncateSafely(sb, 10);
        assertEquals(10, sb.length());
    }

    @Test
    void testConstructor() {
        assertNotNull(testTool);
    }

    // A concrete implementation of BaseTool for testing
    static class TestTool extends BaseTool {
        TestTool(HookManager hookManager) {
            super(hookManager);
        }
    }
}
