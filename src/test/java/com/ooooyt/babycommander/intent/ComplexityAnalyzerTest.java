package com.ooooyt.babycommander.intent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ComplexityAnalyzer}.
 * ComplexityAnalyzer is a utility class with only static methods and a private constructor.
 */
class ComplexityAnalyzerTest {

    @Test
    void testScore_NullTask() {
        assertEquals(0, ComplexityAnalyzer.score(null));
    }

    @Test
    void testScore_EmptyTask() {
        assertEquals(0, ComplexityAnalyzer.score(""));
    }

    @Test
    void testScore_BlankTask() {
        assertEquals(0, ComplexityAnalyzer.score("   "));
    }

    @Test
    void testScore_SimpleTask() {
        // Simple greeting - no keywords
        int score = ComplexityAnalyzer.score("hello world");
        assertEquals(0, score); // no keywords, no structural elements
    }

    @Test
    void testScore_StructuralKeywords() {
        // "architecture" is a structural keyword (3 points)
        int score = ComplexityAnalyzer.score("design the system architecture");
        assertTrue(score >= 3);
    }

    @Test
    void testScore_TechnicalKeywords() {
        // "authentication" is a technical keyword (1 point)
        int score = ComplexityAnalyzer.score("implement authentication");
        assertTrue(score >= 1);
    }

    @Test
    void testScore_LongTask() {
        // Task longer than 200 chars gets +2
        String longTask = "a".repeat(201);
        int score = ComplexityAnalyzer.score(longTask);
        assertTrue(score >= 2);
    }

    @Test
    void testScore_MediumTask() {
        // Task between 100-200 chars gets +1
        String mediumTask = "a".repeat(150);
        int score = ComplexityAnalyzer.score(mediumTask);
        assertTrue(score >= 1);
    }

    @Test
    void testScore_MultipleRequirementsViaBullets() {
        // Task with multiple bullet points
        String task = """
                - Create database schema
                - Add authentication
                - Implement caching
                """;
        int score = ComplexityAnalyzer.score(task);
        assertTrue(score > 0);
    }

    @Test
    void testScore_Conjunctions() {
        // " and " is a conjunction
        int score = ComplexityAnalyzer.score("create and deploy and test");
        assertTrue(score >= 2);
    }

    @Test
    void testIsComplex_SimpleTask() {
        assertFalse(ComplexityAnalyzer.isComplex("hello world"));
    }

    @Test
    void testIsComplex_ComplexTask() {
        // Multiple structural keywords + technical keywords should exceed threshold
        String task = "Design a microservice architecture with distributed pipeline " +
                      "deployment and scalable infrastructure";
        assertTrue(ComplexityAnalyzer.isComplex(task));
    }
}
