package com.ooooyt.babycommander.tool;

import com.ooooyt.babycommander.util.I18n;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class InternetToolTest {

    @BeforeAll
    static void setupLocale() {
        I18n.setLocale(Locale.ENGLISH);
    }

    @Test
    void testFetchUrlValid() {
        InternetTool tool = new InternetTool(null);
        String result = tool.fetchUrl("https://example.com");
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertFalse(result.startsWith("Error:"));
    }

    @Test
    void testFetchUrlInvalidReturnsError() {
        InternetTool tool = new InternetTool(null);
        String result = tool.fetchUrl("https://invalid.invalid.invalid/nonexistent");
        assertTrue(result.startsWith("Error"));
    }

    @Test
    void testSearchReturnsResults() {
        InternetTool tool = new InternetTool(null);
        String result = tool.search("Java programming");
        assertNotNull(result);
        assertFalse(result.startsWith("Error:"));
    }
}
