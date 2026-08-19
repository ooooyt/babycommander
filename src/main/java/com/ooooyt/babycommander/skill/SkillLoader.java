package com.ooooyt.babycommander.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SkillLoader {

    public Skill load(Path skillDirectory) throws IOException {
        Path skillMd = skillDirectory.resolve("SKILL.md");
        if (!Files.exists(skillMd)) {
            return null;
        }

        String content = Files.readString(skillMd);
        String frontmatter = extractFrontmatter(content);
        String body = extractBody(content);

        String name = extractField(frontmatter, "name");
        String description = extractField(frontmatter, "description");
        String triggersStr = extractField(frontmatter, "triggers");

        List<String> triggers = triggersStr != null
                ? List.of(triggersStr.split(",")).stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList()
                : Collections.emptyList();

        List<SkillWorkflowStep> workflowSteps = parseWorkflowSteps(frontmatter);

        return new Skill(name, description, triggers, workflowSteps, body);
    }

    /**
     * Parse workflow_steps from frontmatter.
     * Expected format:
     * <pre>
     * workflow_steps:
     *   - agentId: planner
     *     instruction: Design the architecture
     *   - agentId: writer
     *     instruction: Implement the code
     * </pre>
     */
    private List<SkillWorkflowStep> parseWorkflowSteps(String frontmatter) {
        if (frontmatter == null || frontmatter.isBlank()) {
            return Collections.emptyList();
        }

        String[] lines = frontmatter.split("\n");
        List<SkillWorkflowStep> steps = new ArrayList<>();
        boolean inWorkflowSection = false;
        String currentAgentId = null;
        StringBuilder currentInstruction = new StringBuilder();
        boolean inInstruction = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.equals("workflow_steps:") || trimmed.startsWith("workflow_steps:")) {
                inWorkflowSection = true;
                continue;
            }

            if (!inWorkflowSection) {
                continue;
            }

            // Check for a new list item (starts with "- ")
            if (trimmed.startsWith("- ")) {
                // Save previous step if we were building one
                if (currentAgentId != null) {
                    String instruction = currentInstruction.toString().trim();
                    steps.add(new SkillWorkflowStep(currentAgentId, instruction));
                    currentInstruction = new StringBuilder();
                    inInstruction = false;
                }

                // Parse the first field of the new item (agentId)
                String itemContent = trimmed.substring(2).trim();
                if (itemContent.startsWith("agentId:")) {
                    currentAgentId = itemContent.substring("agentId:".length()).trim();
                }
                continue;
            }

            // Check for continuation fields (indented, no "- ")
            if (inWorkflowSection && currentAgentId != null) {
                if (trimmed.startsWith("agentId:") && !trimmed.startsWith("- ")) {
                    // This is a continuation of a previous item or a new item without "-"
                    // If we were in instruction mode, finalize the current step
                    if (inInstruction) {
                        String instruction = currentInstruction.toString().trim();
                        steps.add(new SkillWorkflowStep(currentAgentId, instruction));
                        currentInstruction = new StringBuilder();
                        inInstruction = false;
                    }
                    currentAgentId = trimmed.substring("agentId:".length()).trim();
                } else if (trimmed.startsWith("instruction:")) {
                    inInstruction = true;
                    currentInstruction = new StringBuilder();
                    String instrValue = trimmed.substring("instruction:".length()).trim();
                    if (!instrValue.isEmpty()) {
                        currentInstruction.append(instrValue);
                    }
                } else if (inInstruction && !trimmed.isEmpty() && !trimmed.startsWith("-") && !trimmed.startsWith("agentId:")) {
                    // Continuation of instruction text (multi-line instruction)
                    if (currentInstruction.length() > 0) {
                        currentInstruction.append(" ");
                    }
                    currentInstruction.append(trimmed);
                }
            }
        }

        // Don't forget the last step
        if (currentAgentId != null) {
            String instruction = currentInstruction.toString().trim();
            steps.add(new SkillWorkflowStep(currentAgentId, instruction));
        }

        return steps;
    }

    private String extractFrontmatter(String content) {
        int start = content.indexOf("---");
        if (start == -1) return "";
        int end = content.indexOf("---", start + 3);
        if (end == -1) return "";
        return content.substring(start + 3, end).trim();
    }

    private String extractBody(String content) {
        int firstEnd = content.indexOf("---", content.indexOf("---") + 3);
        if (firstEnd == -1) return content.trim();
        String after = content.substring(firstEnd + 3);
        return after.trim();
    }

    private String extractField(String frontmatter, String fieldName) {
        String prefix = fieldName + ":";
        for (String line : frontmatter.split("\n")) {
            if (line.trim().startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        return null;
    }
}
