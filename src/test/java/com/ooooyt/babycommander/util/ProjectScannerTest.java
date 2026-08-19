package com.ooooyt.babycommander.util;

import com.ooooyt.babycommander.util.ProjectScanner.ProjectInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class ProjectScannerTest {

    private ProjectScanner scanner;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void setupLocale() {
        I18n.setLocale(Locale.ENGLISH);
    }

    @BeforeEach
    void setUp() {
        scanner = new ProjectScanner();
    }

    @Test
    void testScan_NullInput() {
        ProjectInfo info = scanner.scan((String) null);
        assertNotNull(info);
        assertNotNull(info.folder());
        assertNotNull(info.language());
        assertNotNull(info.buildTool());
    }

    @Test
    void testScan_EmptyDirectory() throws IOException {
        ProjectInfo info = scanner.scan(tempDir.toString());
        assertNotNull(info);
        assertEquals(tempDir.toString(), info.folder());
    }

    @Test
    void testScan_WithPomXml() throws IOException {
        Path pom = tempDir.resolve("pom.xml");
        Files.writeString(pom, "<project></project>");

        ProjectInfo info = scanner.scan(tempDir.toString());
        assertEquals("Java", info.language());
        assertEquals("Maven", info.buildTool());
    }

    @Test
    void testScan_WithPackageJson() throws IOException {
        Path pkg = tempDir.resolve("package.json");
        Files.writeString(pkg, "{}");

        ProjectInfo info = scanner.scan(tempDir.toString());
        assertEquals("JavaScript/Node", info.language());
        assertEquals("npm", info.buildTool());
    }

    @Test
    void testScan_WithGoMod() throws IOException {
        Path goMod = tempDir.resolve("go.mod");
        Files.writeString(goMod, "module example.com/test");

        ProjectInfo info = scanner.scan(tempDir.toString());
        assertEquals("Go", info.language());
        assertEquals("Go build", info.buildTool());
    }

    @Test
    void testScan_WithSrcDirectory() throws IOException {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);

        ProjectInfo info = scanner.scan(tempDir.toString());
        assertEquals("Java", info.language());
    }

    @Test
    void testScan_UnknownProject() throws IOException {
        ProjectInfo info = scanner.scan(tempDir.toString());
        assertEquals("Unknown", info.language());
        assertEquals("None detected", info.buildTool());
    }

    @Test
    void testBuildBackgroundSection_WithAllParams() {
        ProjectInfo info = new ProjectInfo("/test/workspace", "Java", "Maven");
        String section = scanner.buildBackgroundSection(info, "/test/workspace", "/test/project");
        assertNotNull(section);
        assertTrue(section.contains("Java"));
        assertTrue(section.contains("Maven"));
        assertTrue(section.contains("/test/workspace"));
        assertTrue(section.contains("/test/project"));
    }

    @Test
    void testBuildBackgroundSection_WithNullWorkspace() {
        ProjectInfo info = new ProjectInfo("/test/folder", "Python", "pip");
        String section = scanner.buildBackgroundSection(info, null, "/test/project");
        assertNotNull(section);
        assertTrue(section.contains("Python"));
        assertTrue(section.contains("N/A"));
    }

    @Test
    void testBuildBackgroundSection_WithNullProjectFolder() {
        ProjectInfo info = new ProjectInfo("/test/folder", "Go", "Go build");
        String section = scanner.buildBackgroundSection(info, "/test/ws", null);
        assertNotNull(section);
        assertTrue(section.contains("/test/folder"));
    }

    @Test
    void testBuildBackgroundSection_LegacyOverload() {
        ProjectInfo info = new ProjectInfo("/test/folder", "Rust", "Cargo");
        String section = scanner.buildBackgroundSection(info);
        assertNotNull(section);
        assertTrue(section.contains("Rust"));
        assertTrue(section.contains("Cargo"));
        assertTrue(section.contains("/test/folder"));
    }

    @Test
    void testProjectInfo_Record() {
        ProjectInfo info = new ProjectInfo("/path", "Java", "Maven");
        assertEquals("/path", info.folder());
        assertEquals("Java", info.language());
        assertEquals("Maven", info.buildTool());
    }
}
