package com.ooooyt.babycommander.intent;

import java.util.List;

/**
 * Lightweight heuristic analyzer that scores a task string to determine
 * if it represents a complex request warranting multi-agent workflow execution.
 *
 * <p>Scoring dimensions:
 * <ul>
 *   <li>Structural keywords (microservice, architecture, multi-module, etc.)</li>
 *   <li>Requirement count (sentences, bullet points, comma-separated items)</li>
 *   <li>Task length</li>
 *   <li>Conjunction count (and, with, plus, also)</li>
 *   <li>Technical depth (API, REST, schema, test, security, etc.)</li>
 * </ul>
 *
 * <p>A score ≥ {@link #COMPLEX_THRESHOLD} indicates a complex request.
 */
public final class ComplexityAnalyzer {

    private static final int COMPLEX_THRESHOLD = 6;

    // Structural keywords — indicate multi-component or architectural work
    private static final List<String> STRUCTURAL_KEYWORDS = List.of(
        "microservice", "distributed", "multi-module", "multi module",
        "architecture", "pipeline", "deploy", "infrastructure",
        "enterprise", "production", "scalable", "high-availability",
        "load balancing", "service mesh", "containerized"
    );

    // Technical depth keywords — indicate non-trivial implementation
    private static final List<String> TECHNICAL_KEYWORDS = List.of(
        "api", "rest", "schema", "model", "config",
        "security", "authentication", "authorization",
        "database", "integration", "middleware",
        "ci/cd", "continuous integration", "testing strategy"
    );

    // Conjunctions that suggest multiple requirements
    private static final List<String> CONJUNCTIONS = List.of(
        " and ", " with ", " plus ", " also ", " as well as "
    );

    private ComplexityAnalyzer() {}

    /**
     * Returns true if the task is complex enough to warrant multi-agent execution.
     */
    public static boolean isComplex(String task) {
        return score(task) >= COMPLEX_THRESHOLD;
    }

    /**
     * Compute a complexity score for the given task string.
     * Higher score = more complex.
     */
    public static int score(String task) {
        if (task == null || task.isBlank()) {
            return 0;
        }

        String lower = task.toLowerCase();
        int score = 0;

        // 1. Structural keywords (up to 9 points)
        score += countMatches(lower, STRUCTURAL_KEYWORDS) * 3;

        // 2. Technical depth keywords (up to 3 points)
        score += Math.min(countMatches(lower, TECHNICAL_KEYWORDS), 3);

        // 3. Requirement count (sentences / bullet points / comma items)
        int requirementCount = countRequirements(task);
        if (requirementCount >= 5) {
            score += 5;
        } else if (requirementCount >= 3) {
            score += requirementCount;
        }

        // 4. Length heuristic
        if (task.length() > 200) {
            score += 2;
        } else if (task.length() > 100) {
            score += 1;
        }

        // 5. Conjunction count (up to 3 points)
        score += Math.min(countMatches(lower, CONJUNCTIONS), 3);

        return score;
    }

    /**
     * Count how many of the given keywords appear in the input text.
     */
    private static int countMatches(String lowerInput, List<String> keywords) {
        int count = 0;
        for (String keyword : keywords) {
            if (lowerInput.contains(keyword)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Estimate the number of distinct requirements in the task.
     * Counts sentences (split by . ! ?), bullet points, and comma-separated
     * items after common introductory phrases.
     */
    private static int countRequirements(String task) {
        // Count bullet points (lines starting with - or * or number.)
        int bullets = 0;
        String[] lines = task.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")
                || trimmed.matches("^\\d+\\.\\s.*")) {
                bullets++;
            }
        }
        if (bullets > 0) {
            return bullets;
        }

        // Count sentences split by . ! ?
        String[] sentences = task.split("[.!?]+");
        int meaningfulSentences = 0;
        for (String s : sentences) {
            String trimmed = s.trim();
            if (trimmed.length() > 10) {
                meaningfulSentences++;
            }
        }
        return Math.max(meaningfulSentences, 1);
    }
}
