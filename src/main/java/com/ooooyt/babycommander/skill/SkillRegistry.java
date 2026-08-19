package com.ooooyt.babycommander.skill;

import com.ooooyt.babycommander.config.YamlConfigLoader;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class SkillRegistry {

    private final Map<String, Skill> skills = new ConcurrentHashMap<>();
    private final SkillLoader loader = new SkillLoader();
    private final Path skillsDir;

    /**
     * CDI constructor — reads skillsDir from the YAML config.
     */
    @Inject
    public SkillRegistry(YamlConfigLoader configLoader) {
        String configuredDir = configLoader.getConfig().skillsDir;
        this.skillsDir = (configuredDir != null && !configuredDir.isBlank())
                ? resolvePath(configuredDir)
                : null;
    }

    /**
     * Programmatic constructor for testing or manual setup.
     */
    public SkillRegistry(String skillsDir) {
        this.skillsDir = (skillsDir != null && !skillsDir.isBlank())
                ? resolvePath(skillsDir)
                : null;
    }

    /**
     * Default constructor for CDI proxy support.
     */
    protected SkillRegistry() {
        this.skillsDir = null;
    }

    /**
     * Returns the resolved skill directory (with any leading "~" expanded and
     * relative paths anchored under the user's home directory), or null if no
     * skill directory is configured.
     */
    public Path getSkillsDir() {
        return skillsDir;
    }

    /**
     * Resolve a configured skill directory to an absolute {@link Path}.
     * <ul>
     *   <li>A leading "~" is expanded to the user's home directory.</li>
     *   <li>A relative path is anchored to the application data directory,
     *       {@code ~/.babycommander}, so that a relative value such as
     *       {@code ./skills} does not depend on the current working directory.</li>
     * </ul>
     */
    private static Path resolvePath(String dir) {
        String expanded = dir;
        if (dir.equals("~")) {
            expanded = System.getProperty("user.home");
        } else if (dir.startsWith("~/")) {
            expanded = System.getProperty("user.home") + dir.substring(1);
        } else if (!dir.startsWith("/")) {
            // Relative path: anchor it under ~/.babycommander so it is stable
            // regardless of the current working directory.
            String home = System.getProperty("user.home");
            String dataDir = home + java.io.File.separator + ".babycommander";
            expanded = dataDir + java.io.File.separator + dir;
        }
        return Path.of(expanded);
    }

    public List<Skill> loadAllSkills() {
        if (skillsDir == null || !Files.exists(skillsDir)) {
            return List.of();
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(skillsDir, Files::isDirectory)) {
            List<Skill> loaded = new ArrayList<>();
            for (Path dir : stream) {
                Skill skill = loader.load(dir);
                if (skill != null && skill.name() != null) {
                    skills.put(skill.name(), skill);
                    loaded.add(skill);
                }
            }
            return loaded;
        } catch (IOException e) {
            return List.of();
        }
    }

    public Skill loadSkill(String skillName) {
        return skills.get(skillName);
    }

    /**
     * Build a catalog of skills that define a multi-agent workflow, for injection
     * into the system prompt so the LLM can decide which (if any) skill workflow
     * to invoke semantically. Returns an empty string when no workflow skills
     * are registered.
     */
    public String getSkillCatalog() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Skill> entry : skills.entrySet()) {
            Skill skill = entry.getValue();
            if (skill == null || !skill.hasWorkflow()) {
                continue;
            }
            sb.append("- ").append(entry.getKey());
            String desc = skill.description();
            if (desc != null && !desc.isBlank()) {
                sb.append(": ").append(desc);
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * Get the workflow steps for a named skill.
     * Returns an empty list if the skill doesn't exist or has no workflow steps defined.
     */
    public List<SkillWorkflowStep> getSkillWorkflow(String skillName) {
        Skill skill = skills.get(skillName);
        if (skill == null || !skill.hasWorkflow()) {
            return Collections.emptyList();
        }
        return skill.workflowSteps();
    }

    /**
     * Return all skill names that define a multi-agent workflow.
     */
    public List<String> getSkillsWithWorkflow() {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Skill> entry : skills.entrySet()) {
            if (entry.getValue().hasWorkflow()) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public String getSkillPrompt(String skillName) {
        Skill skill = skills.get(skillName);
        if (skill == null) {
            return "";
        }
        return "### Skill: " + skill.name() + "\n\n" + skill.instructionContent();
    }

    public int size() {
        return skills.size();
    }
}
