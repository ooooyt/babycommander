package com.ooooyt.babycommander.intent;

import org.junit.jupiter.api.Test;

class DebugComplexityTest {
    @Test
    void debug() {
        System.out.println("=== DEBUG ===");
        System.out.println("Score for 'api rest': " + ComplexityAnalyzer.score("api rest"));
        System.out.println("Score for 'api rest database': " + ComplexityAnalyzer.score("api rest database"));
        System.out.println("Score for 'hello world': " + ComplexityAnalyzer.score("hello world"));
        System.out.println("Score for 'database': " + ComplexityAnalyzer.score("database"));
        System.out.println("Score for 'api': " + ComplexityAnalyzer.score("api"));
        System.out.println("Score for 'rest': " + ComplexityAnalyzer.score("rest"));
        System.out.println("Score for 'schema model config': " + ComplexityAnalyzer.score("schema model config"));
    }
}
