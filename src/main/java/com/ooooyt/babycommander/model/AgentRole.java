package com.ooooyt.babycommander.model;

import lombok.Getter;

@Getter
public enum AgentRole {
    ORCHESTRATOR("orchestrator"),
    PLANNER("planner"),
    WRITER("writer"),
    REVIEWER("reviewer"),
    TESTER("tester"),
    ROUTER("router"),
    FIXER("fixer"),
    DOCUMENT_WRITER("document-writer");

    private final String value;

    AgentRole(String value) {
        this.value = value;
    }

    public static AgentRole fromValue(String value) {
        for (AgentRole role : values()) {
            if (role.value.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown agent role: " + value);
    }
}
