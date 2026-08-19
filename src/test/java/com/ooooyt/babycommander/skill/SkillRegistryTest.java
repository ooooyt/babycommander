package com.ooooyt.babycommander.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SkillRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void testLoadSingleSkill() throws Exception {
        Path skillDir = tempDir.resolve("test-skill");
        Files.createDirectories(skillDir);
        String frontmatter = """
            ---
            name: test-skill
            description: A test skill
            triggers: test, unit, junit
            ---

            # Test Skill Instructions

            Use this skill when writing unit tests.
            """;
        Files.writeString(skillDir.resolve("SKILL.md"), frontmatter);

        SkillRegistry registry = new SkillRegistry(tempDir.toString());
        assertEquals(1, registry.loadAllSkills().size());

        Skill skill = registry.loadSkill("test-skill");
        assertNotNull(skill);
        assertEquals("test-skill", skill.name());
        assertTrue(skill.instructionContent().contains("Test Skill Instructions"));
    }

    @Test
    void testGetSkillCatalogListsWorkflowSkillsOnly() throws Exception {
        // Skill WITH a workflow definition — should appear in the catalog.
        Path wfDir = tempDir.resolve("tdd-skill");
        Files.createDirectories(wfDir);
        Files.writeString(wfDir.resolve("SKILL.md"), """
            ---
            name: tdd-skill
            description: TDD workflow
            triggers: test, TDD
            workflow_steps:
              - agentId: planner
                instruction: Design the test cases
              - agentId: writer
                instruction: Implement the code
            ---

            # TDD Instructions
            """);

        // Skill WITHOUT a workflow definition — should NOT appear in the catalog.
        Path plainDir = tempDir.resolve("css-skill");
        Files.createDirectories(plainDir);
        Files.writeString(plainDir.resolve("SKILL.md"), """
            ---
            name: css-skill
            description: CSS styling
            triggers: css, style
            ---

            # CSS Instructions
            """);

        SkillRegistry registry = new SkillRegistry(tempDir.toString());
        registry.loadAllSkills();

        String catalog = registry.getSkillCatalog();

        assertTrue(catalog.contains("tdd-skill"), "workflow skill should be listed");
        assertTrue(catalog.contains("TDD workflow"), "description should be included");
        assertFalse(catalog.contains("css-skill"), "non-workflow skill should be omitted");
    }

    @Test
    void testGetSkillCatalogEmptyWhenNoWorkflowSkills() throws Exception {
        Path plainDir = tempDir.resolve("css-skill");
        Files.createDirectories(plainDir);
        Files.writeString(plainDir.resolve("SKILL.md"), """
            ---
            name: css-skill
            description: CSS styling
            triggers: css, style
            ---

            # CSS Instructions
            """);

        SkillRegistry registry = new SkillRegistry(tempDir.toString());
        registry.loadAllSkills();

        assertTrue(registry.getSkillCatalog().isBlank());
    }

    @Test
    void testEmptySkillsDirectory() {
        SkillRegistry registry = new SkillRegistry(tempDir.toString());
        assertTrue(registry.loadAllSkills().isEmpty());
    }

    @Test
    void testNullSkillsDirectory() {
        SkillRegistry registry = new SkillRegistry();
        assertTrue(registry.loadAllSkills().isEmpty());
    }

    @Test
    void testGetSkillPromptReturnsMarkdown() throws Exception {
        Path skillDir = tempDir.resolve("my-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
            ---
            name: my-skill
            description: My skill
            triggers: my
            ---

            # My Skill

            Do thing X, then thing Y.
            """);

        SkillRegistry registry = new SkillRegistry(tempDir.toString());
        registry.loadAllSkills();

        String prompt = registry.getSkillPrompt("my-skill");
        assertNotNull(prompt);
        assertTrue(prompt.contains("my-skill"));
        assertTrue(prompt.contains("Do thing X"));
    }

    @Test
    void testGetSkillPromptForUnknownSkill() {
        SkillRegistry registry = new SkillRegistry(tempDir.toString());
        assertEquals("", registry.getSkillPrompt("unknown"));
    }

    @Test
    void testSize() throws Exception {
        Path d1 = tempDir.resolve("skill-a");
        Path d2 = tempDir.resolve("skill-b");
        Files.createDirectories(d1);
        Files.createDirectories(d2);
        Files.writeString(d1.resolve("SKILL.md"), "---\nname: skill-a\n---\nA");
        Files.writeString(d2.resolve("SKILL.md"), "---\nname: skill-b\n---\nB");

        SkillRegistry registry = new SkillRegistry(tempDir.toString());
        registry.loadAllSkills();

        assertEquals(2, registry.size());
    }

    @Test
    void testRelativeSkillsDirResolvesUnderDataDir() {
        // A relative path must be anchored to ~/.babycommander so it does not
        // depend on the current working directory.
        SkillRegistry registry = new SkillRegistry("./skills");
        Path expected = Path.of(
            System.getProperty("user.home"),
            ".babycommander",
            "skills"
        ).toAbsolutePath().normalize();
        assertEquals(expected, registry.getSkillsDir().toAbsolutePath().normalize());
    }

    @Test
    void testTildeSkillsDirExpandsToHome() {
        SkillRegistry registry = new SkillRegistry("~/.babycommander/skills");
        Path expected = Path.of(
            System.getProperty("user.home"),
            ".babycommander",
            "skills"
        ).toAbsolutePath().normalize();
        assertEquals(expected, registry.getSkillsDir().toAbsolutePath().normalize());
    }
}
