package com.ooooyt.babycommander.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnvTest {

    @BeforeEach
    @AfterEach
    void reset() {
        Env.resetForTests();
        System.clearProperty("BCMD_TEST_KEY");
        System.clearProperty("BCMD_TEST_INT");
        System.clearProperty("BCMD_TEST_BOOL");
        System.clearProperty("BCMD_TEST_DOUBLE");
        System.clearProperty("BABY_COMMANDER_TEST_ALIAS");
    }

    @Test
    void emptyValueIsTreatedAsUnset() {
        System.setProperty("BCMD_TEST_KEY", "");
        assertNull(Env.get("BCMD_TEST_KEY"));
        assertEquals("fallback", Env.getOr("BCMD_TEST_KEY", "fallback"));
    }

    @Test
    void systemPropertyWinsOverDefault() {
        System.setProperty("BCMD_TEST_KEY", "from-prop");
        assertEquals("from-prop", Env.getOr("BCMD_TEST_KEY", "default"));
    }

    @Test
    void unsetFallsBackToDefault() {
        assertEquals("default", Env.getOr("BCMD_TEST_KEY", "default"));
    }

    @Test
    void deprecatedAliasIsHonoredWithWarning() {
        System.setProperty("BABY_COMMANDER_TEST_ALIAS", "legacy");
        assertEquals("legacy", Env.getWithAliases("BCMD_TEST_KEY",
                new String[]{"BABY_COMMANDER_TEST_ALIAS"}, "default"));
        // Primary key wins over alias
        System.setProperty("BCMD_TEST_KEY", "new");
        assertEquals("new", Env.getWithAliases("BCMD_TEST_KEY",
                new String[]{"BABY_COMMANDER_TEST_ALIAS"}, "default"));
    }

    @Test
    void invalidIntFallsBackToDefault() {
        System.setProperty("BCMD_TEST_INT", "abc");
        assertEquals(42, Env.getInt("BCMD_TEST_INT", 42));
        System.setProperty("BCMD_TEST_INT", "7");
        assertEquals(7, Env.getInt("BCMD_TEST_INT", 42));
    }

    @Test
    void emptyIntFallsBackToDefault() {
        System.setProperty("BCMD_TEST_INT", "");
        assertEquals(42, Env.getInt("BCMD_TEST_INT", 42));
    }

    @Test
    void invalidDoubleFallsBackToDefault() {
        System.setProperty("BCMD_TEST_DOUBLE", "x");
        assertEquals(0.7, Env.getDouble("BCMD_TEST_DOUBLE", 0.7));
        System.setProperty("BCMD_TEST_DOUBLE", "0.5");
        assertEquals(0.5, Env.getDouble("BCMD_TEST_DOUBLE", 0.7));
    }

    @Test
    void booleanVariantsAreAccepted() {
        System.setProperty("BCMD_TEST_BOOL", "true");
        assertTrue(Env.getBoolean("BCMD_TEST_BOOL", false));
        System.setProperty("BCMD_TEST_BOOL", "1");
        assertTrue(Env.getBoolean("BCMD_TEST_BOOL", false));
        System.setProperty("BCMD_TEST_BOOL", "yes");
        assertTrue(Env.getBoolean("BCMD_TEST_BOOL", false));
        System.setProperty("BCMD_TEST_BOOL", "off");
        assertFalse(Env.getBoolean("BCMD_TEST_BOOL", true));
        System.setProperty("BCMD_TEST_BOOL", "garbage");
        assertTrue(Env.getBoolean("BCMD_TEST_BOOL", true));
    }

    @Test
    void redactMasksSecrets() {
        assertEquals("(unset)", Env.redact(null));
        assertEquals("(unset)", Env.redact(""));
        assertEquals("***", Env.redact("abc"));
        assertEquals("sk-1***", Env.redact("sk-1234567890"));
    }
}