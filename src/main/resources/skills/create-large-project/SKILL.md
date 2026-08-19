---
name: create-large-project
description: Create a large multi-module project with architecture design, implementation, and testing
triggers: create large project, new project, multi-module project, spring boot project, full-stack project, build a system, microservice architecture, enterprise application, distributed system, cloud-native application, project scaffolding, monorepo setup, modular architecture, multi-tier application, set up a project, bootstrap a project, generate a project, initialize a project, scaffold a project, create a microservice, build an application, design a system, create an enterprise app, full stack application, multi-service architecture, event-driven system, cqrs project, hexagonal architecture, clean architecture project, ddd project, domain-driven design project, soa project, service-oriented architecture
workflow_steps:
  - agentId: planner
    instruction: |
      You are a software architect. Design a comprehensive architecture for the following project:
      
      ${task}
      
      Analyze the requirements and produce a detailed design document covering:
      1. Overall architecture and component diagram
      2. Module structure (multi-module layout)
      3. Data models and relationships
      4. API design (REST endpoints, request/response formats)
      5. Technology stack decisions
      6. Build configuration
      
      Write the design document to ${project}/doc/design.md
  - agentId: writer
    instruction: |
      You are a senior developer. Read the design document at ${project}/doc/design.md
      and implement the project according to the architecture.
      
      Create all necessary:
      - Maven/Gradle build files with proper module structure
      - Java/Kotlin source files for all components
      - Configuration files (application.yml, etc.)
      - Database migration scripts if needed
      
      Follow the design document exactly. Do not deviate from the specified architecture.
  - agentId: tester
    instruction: |
      You are a QA engineer. Review the implementation in ${project} against the
      design document at ${project}/doc/design.md.
      
      Write comprehensive tests:
      1. Unit tests for all service classes
      2. Integration tests for API endpoints
      3. Verify the build compiles successfully
      
      Run the tests and fix any failures. Ensure all tests pass before finishing.
---

# Create Large Project

This skill handles the creation of large, multi-module projects by orchestrating
a planner agent, a writer agent, and a tester agent in sequence.

## When to Use

Use this skill when the user requests creating a new project that:
- Has multiple modules or components
- Requires architectural design before implementation
- Needs comprehensive test coverage
- Is a standard project type (Spring Boot, full-stack, microservices, etc.)

## Instructions

The skill workflow consists of three sequential steps:
1. **Planner** — Designs the architecture and writes a design document
2. **Writer** — Implements the project based on the design document
3. **Tester** — Writes and runs tests, verifying the implementation
