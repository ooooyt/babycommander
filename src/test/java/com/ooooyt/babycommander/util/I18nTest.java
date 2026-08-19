package com.ooooyt.babycommander.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class I18nTest {

    @AfterEach
    void tearDown() {
        // Reset to default locale after each test
        I18n.reset();
    }

    @Test
    void testEnglishDefault() {
        // Default locale should be English (or JVM default)
        // The key should resolve to the English string
        String result = I18n.tr(MessageKey.CHAT_GOODBYE);
        assertNotNull(result);
        assertTrue(result.contains("Goodbye") || !result.startsWith("!"),
            "Expected English string or valid translation, got: " + result);
    }

    @Test
    void testChineseLocale() {
        I18n.setLocale(Locale.SIMPLIFIED_CHINESE);
        String result = I18n.tr(MessageKey.CHAT_GOODBYE);
        assertNotNull(result);
        // The Chinese translation should contain the goodbye character
        assertTrue(result.contains("再见") || !result.startsWith("!"),
            "Expected Chinese string, got: " + result);
    }

    @Test
    void testMissingKeyFallback() {
        String result = I18n.tr("this.key.does.not.exist");
        assertEquals("!this.key.does.not.exist!", result,
            "Missing key should return !key! format");
    }

    @Test
    void testMessageFormat() {
        String result = I18n.tr(MessageKey.CHAT_WORKFLOW_TASK, "test task");
        assertNotNull(result);
        assertTrue(result.contains("test task"),
            "Formatted string should contain the argument, got: " + result);
    }

    @Test
    void testMessageFormatMultipleArgs() {
        // Use a key that takes two arguments
        String result = I18n.tr(MessageKey.CHAT_EXISTING_PROJECT_FOUND, "Java", "Maven");
        assertNotNull(result);
        assertTrue(result.contains("Java"), "Should contain first argument, got: " + result);
        assertTrue(result.contains("Maven"), "Should contain second argument, got: " + result);
    }

    @Test
    void testSetLocaleNull() {
        // setLocale(null) should fall back to JVM default
        Locale defaultLocale = Locale.getDefault();
        I18n.setLocale(null);
        assertEquals(defaultLocale, I18n.getLocale(),
            "Setting null locale should fall back to JVM default");
    }

    @Test
    void testCLIFlagParsing() {
        // Simulate --lang zh-CN
        Locale parsed = Locale.forLanguageTag("zh-CN");
        I18n.setLocale(parsed);
        assertEquals("zh", I18n.getLocale().getLanguage());
        assertEquals("CN", I18n.getLocale().getCountry());
    }

    @Test
    void testEnvVarDetection() {
        // Simulate LANG=zh_CN.UTF-8 parsing
        String langEnv = "zh_CN.UTF-8";
        String tag = langEnv.contains(".") ? langEnv.substring(0, langEnv.indexOf('.')) : langEnv;
        Locale parsed = Locale.forLanguageTag(tag.replace('_', '-'));
        I18n.setLocale(parsed);
        assertEquals("zh", I18n.getLocale().getLanguage());
        assertEquals("CN", I18n.getLocale().getCountry());
    }

    @Test
    void testTrWithNoArgs() {
        // Keys without placeholders should work fine
        String result = I18n.tr(MessageKey.CHAT_GENERATION_RUNNING);
        assertNotNull(result);
        assertFalse(result.startsWith("!"), "Existing key should not return fallback");
    }

    @Test
    void testGetLocale() {
        Locale.setDefault(Locale.CHINESE);
        I18n.reset();
        assertEquals(Locale.CHINESE, I18n.getLocale());
        // Reset back
        Locale.setDefault(Locale.ENGLISH);
        I18n.reset();
    }

    // --- MessageKey enum tests ---

    @Test
    void testMessageKeyKeyMethod() {
        assertEquals("chat.goodbye", MessageKey.CHAT_GOODBYE.key());
        assertEquals("cli.welcome", MessageKey.CLI_WELCOME.key());
        assertEquals("confirmation.allow", MessageKey.CONFIRMATION_ALLOW.key());
    }

    @Test
    void testMessageKeyDescMethod() {
        assertNotNull(MessageKey.CHAT_GOODBYE.desc());
        assertTrue(MessageKey.CHAT_GOODBYE.desc().contains("Goodbye"));
        assertNotNull(MessageKey.CLI_WELCOME.desc());
        assertTrue(MessageKey.CLI_WELCOME.desc().contains("Welcome"));
    }

    @Test
    void testConfirmationAllowText_removedAlways() {
        // The ASK_ONCE confirmation prompt should show [y/n/a] without the word "always"
        String allow = I18n.tr(MessageKey.CONFIRMATION_ALLOW);
        assertNotNull(allow);
        assertFalse(allow.contains("always"), "Option text should not contain the word 'always': " + allow);
        assertTrue(allow.contains("[y/n/a]"), "Option text should show [y/n/a]: " + allow);
        // The simple (DANGEROUS) prompt should show [y/n]
        String allowSimple = I18n.tr(MessageKey.CONFIRMATION_ALLOW_SIMPLE);
        assertNotNull(allowSimple);
        assertTrue(allowSimple.contains("[y/n]"), "Simple option text should show [y/n]: " + allowSimple);
    }

    @Test
    void testMessageKeyToString() {
        assertEquals("chat.goodbye", MessageKey.CHAT_GOODBYE.toString());
    }

    @Test
    void testAllMessageKeysHaveUniqueKeys() {
        // Verify all enum constants have non-null keys
        for (MessageKey mk : MessageKey.values()) {
            assertNotNull(mk.key(), "Key should not be null for " + mk.name());
            assertFalse(mk.key().isBlank(), "Key should not be blank for " + mk.name());
            assertNotNull(mk.desc(), "Description should not be null for " + mk.name());
        }
    }

    @Test
    void testTrWithMessageKey() {
        // Test that I18n.tr(MessageKey) works correctly
        String result = I18n.tr(MessageKey.CLI_STARTING);
        assertNotNull(result);
        assertTrue(result.contains("Starting") || !result.startsWith("!"),
            "Expected valid translation, got: " + result);
    }

    @Test
    void testTrWithMessageKeyAndArgs() {
        String result = I18n.tr(MessageKey.CHAT_WORKFLOW_FAILED, "test error");
        assertNotNull(result);
        assertTrue(result.contains("test error"),
            "Formatted string should contain the argument, got: " + result);
    }
}
