package com.ooooyt.babycommander.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EnvFileLoaderTest {

    @TempDir
    Path tempDir;

    private String originalUserHome;

    @BeforeEach
    void setUp() {
        originalUserHome = System.getProperty("user.home");
        Env.resetForTests();
    }

    @AfterEach
    void tearDown() {
        System.setProperty("user.home", originalUserHome);
        System.clearProperty("BCMD_DEFAULT_MODEL");
        Env.resetForTests();
    }

    private void writeDotEnv(String content) throws IOException {
        Path dir = tempDir.resolve(".babycommander");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(".env"), content);
        System.setProperty("user.home", tempDir.toString());
    }

    @Test
    void loadsVariablesFromDotEnv() throws IOException {
        writeDotEnv("# comment\nBCMD_OPENAI_API_KEY=sk-secret\nBCMD_DEFAULT_MODEL=deepseek\n\nEMPTY=\n");
        Env.loadDotEnv();
        assertEquals("sk-secret", Env.get("BCMD_OPENAI_API_KEY"));
        assertEquals("deepseek", Env.get("BCMD_DEFAULT_MODEL"));
        // Empty value in .env is treated as unset
        assertNull(Env.get("EMPTY"));
    }

    @Test
    void stripsQuotes() throws IOException {
        writeDotEnv("BCMD_DEFAULT_MODEL=\"quoted\"\nBCMD_MAX_RETRIES='3'\n");
        Env.loadDotEnv();
        assertEquals("quoted", Env.get("BCMD_DEFAULT_MODEL"));
        assertEquals("3", Env.get("BCMD_MAX_RETRIES"));
    }

    @Test
    void missingDotEnvLoadsNothing() {
        System.setProperty("user.home", tempDir.toString());
        Env.loadDotEnv();
        assertNull(Env.get("BCMD_DEFAULT_MODEL"));
    }

    @Test
    void processEnvWinsOverDotEnv() throws IOException {
        writeDotEnv("BCMD_DEFAULT_MODEL=from-dotenv\n");
        Env.loadDotEnv();
        // Simulate process env by setting a system property (highest precedence)
        System.setProperty("BCMD_DEFAULT_MODEL", "from-prop");
        assertEquals("from-prop", Env.get("BCMD_DEFAULT_MODEL"));
    }

    @Test
    void resetClearsDotEnvState() throws IOException {
        writeDotEnv("BCMD_DEFAULT_MODEL=deepseek\n");
        Env.loadDotEnv();
        assertEquals("deepseek", Env.get("BCMD_DEFAULT_MODEL"));
        Env.resetForTests();
        assertNull(Env.get("BCMD_DEFAULT_MODEL"));
    }
}