package com.ooooyt.babycommander.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentRoleTest {

    @Test
    void testAllRolesPresent() {
        AgentRole[] roles = AgentRole.values();
        assertEquals(8, roles.length);
    }

    @Test
    void testValues() {
        assertEquals("orchestrator", AgentRole.ORCHESTRATOR.getValue());
        assertEquals("planner", AgentRole.PLANNER.getValue());
        assertEquals("writer", AgentRole.WRITER.getValue());
        assertEquals("reviewer", AgentRole.REVIEWER.getValue());
        assertEquals("tester", AgentRole.TESTER.getValue());
        assertEquals("router", AgentRole.ROUTER.getValue());
        assertEquals("fixer", AgentRole.FIXER.getValue());
        assertEquals("document-writer", AgentRole.DOCUMENT_WRITER.getValue());
    }

    @Test
    void testFromValue() {
        assertEquals(AgentRole.ORCHESTRATOR, AgentRole.fromValue("orchestrator"));
        assertEquals(AgentRole.PLANNER, AgentRole.fromValue("planner"));
        assertEquals(AgentRole.WRITER, AgentRole.fromValue("writer"));
        assertEquals(AgentRole.REVIEWER, AgentRole.fromValue("reviewer"));
        assertEquals(AgentRole.TESTER, AgentRole.fromValue("tester"));
        assertEquals(AgentRole.ROUTER, AgentRole.fromValue("router"));
        assertEquals(AgentRole.FIXER, AgentRole.fromValue("fixer"));
        assertEquals(AgentRole.DOCUMENT_WRITER, AgentRole.fromValue("document-writer"));
    }

    @Test
    void testFromValueUnknown() {
        assertThrows(IllegalArgumentException.class, () -> AgentRole.fromValue("unknown"));
    }

    @Test
    void testFromValueNull() {
        assertThrows(IllegalArgumentException.class, () -> AgentRole.fromValue(null));
    }

    @Test
    void testFromValueEmpty() {
        assertThrows(IllegalArgumentException.class, () -> AgentRole.fromValue(""));
    }

    @Test
    void testLombokGetter() {
        assertEquals("orchestrator", AgentRole.ORCHESTRATOR.getValue());
    }

    @Test
    void testToString() {
        // enum toString should return the enum constant name
        assertEquals("ORCHESTRATOR", AgentRole.ORCHESTRATOR.toString());
    }
}
