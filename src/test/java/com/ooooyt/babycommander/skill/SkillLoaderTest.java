package com.ooooyt.babycommander.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkillLoaderTest {

    private final SkillLoader skillLoader = new SkillLoader();

    @Test
    void testLoad_NoSkillMd_ReturnsNull(@TempDir Path tempDir) throws IOException {
        Skill skill = skillLoader.load(tempDir);
        assertNull(skill);
    }

    @Test
    void testLoad_ValidSkillMd(@TempDir Path tempDir) throws IOException {
        String content = """
                ---
                name: test-skill
                description: A test skill
                triggers: deploy,release
                workflow_steps:
                  - agentId: planner
                    instruction: Design the plan
                  - agentId: writer
                    instruction: Write the code
                ---
                This is the instruction body.
                """;
        Path skillMd = tempDir.resolve("SKILL.md");
        Files.writeString(skillMd, content);

        Skill skill = skillLoader.load(tempDir);
        assertNotNull(skill);
        assertEquals("test-skill", skill.name());
        assertEquals("A test skill", skill.description());
        assertEquals(List.of("deploy", "release"), skill.triggers());
        assertEquals("This is the instruction body.", skill.instructionContent());
        assertTrue(skill.hasWorkflow());
        assertEquals(2, skill.workflowSteps().size());
        assertEquals("planner", skill.workflowSteps().get(0).agentId());
        assertEquals("writer", skill.workflowSteps().get(1).agentId());
    }

    @Test
    void testLoad_NoWorkflowSteps(@TempDir Path tempDir) throws IOException {
        String content = """
                ---
                name: simple-skill
                description: A simple skill without workflow
                triggers: simple
                ---
                Just a simple instruction.
                """;
        Path skillMd = tempDir.resolve("SKILL.md");
        Files.writeString(skillMd, content);

        Skill skill = skillLoader.load(tempDir);
        assertNotNull(skill);
        assertEquals("simple-skill", skill.name());
        assertFalse(skill.hasWorkflow());
        assertTrue(skill.workflowSteps().isEmpty());
    }

    @Test
    void testLoad_NoTriggers(@TempDir Path tempDir) throws IOException {
        String content = """
                ---
                name: no-trigger-skill
                description: A skill without triggers
                ---
                Instruction without triggers.
                """;
        Path skillMd = tempDir.resolve("SKILL.md");
        Files.writeString(skillMd, content);

        Skill skill = skillLoader.load(tempDir);
        assertNotNull(skill);
        assertTrue(skill.triggers().isEmpty());
    }

    @Test
    void testLoad_MissingName(@TempDir Path tempDir) throws IOException {
        String content = """
                ---
                description: A skill without name
                ---
                Instruction without name.
                """;
        Path skillMd = tempDir.resolve("SKILL.md");
        Files.writeString(skillMd, content);

        Skill skill = skillLoader.load(tempDir);
        assertNotNull(skill);
        assertNull(skill.name());
    }

    @Test
    void testLoad_EmptySkillMd(@TempDir Path tempDir) throws IOException {
        Path skillMd = tempDir.resolve("SKILL.md");
        Files.writeString(skillMd, "");

        Skill skill = skillLoader.load(tempDir);
        // Empty file - frontmatter extraction will handle it
        assertNotNull(skill);
        assertNull(skill.name());
    }

    @Test
    void testLoad_InvalidDirectory(@TempDir Path tempDir) throws IOException {
        Path nonExistent = tempDir.resolve("nonexistent");
        Skill skill = skillLoader.load(nonExistent);
        assertNull(skill);
    }
}
