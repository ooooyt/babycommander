---
name: add-complex-feature
description: Add a complex feature spanning multiple components with design, implementation, and integration testing
triggers: add complex feature, implement new feature, add new functionality, cross-cutting concern, multi-component feature, end-to-end feature, implement module, add service, add api endpoint, implement use case, add business logic, feature implementation, new capability, add functionality, implement integration, add new module, extend with feature, implement new module, add cross-cutting feature, add new api, implement new endpoint, add new service, implement new use case
workflow_steps:
  - agentId: planner
    instruction: |
      You are a software architect. Design the implementation for the following
      feature request in the project at ${project}:
      
      ${task}
      
      Produce a detailed feature design covering:
      1. Components affected — which modules/classes need changes
      2. Data model changes — new entities, fields, relationships
      3. API contract — new endpoints, request/response schemas
      4. Business logic — service layer design and algorithms
      5. Integration points — how this connects to existing systems
      6. Testing strategy — unit, integration, and end-to-end tests
      
      Write the feature design to ${project}/doc/feature-design.md
  - agentId: writer
    instruction: |
      You are a senior developer. Read the feature design at ${project}/doc/feature-design.md
      and implement the feature in the project at ${project}.
      
      For each component:
      1. Create or modify source files according to the design
      2. Update configuration files if needed
      3. Ensure backward compatibility with existing code
      4. Run compilation to verify no build errors
      
      Follow the design document exactly. Do not add scope beyond the design.
  - agentId: tester
    instruction: |
      You are a QA engineer. Verify that the new feature at ${project} works
      correctly and integrates properly with existing functionality.
      
      Tasks:
      1. Write unit tests for all new service classes
      2. Write integration tests for new API endpoints
      3. Run existing tests to ensure no regressions
      4. Verify the feature end-to-end with a smoke test
      5. Report any issues or gaps in test coverage
---

# Add Complex Feature

This skill handles the implementation of complex, cross-cutting features that
span multiple components of an existing codebase.

## When to Use

Use this skill when the user requests:
- Adding a new feature that affects multiple modules
- Implementing cross-cutting concerns (auth, logging, caching)
- Adding new API endpoints with business logic
- Extending the system with new capabilities
- Features requiring database changes, new services, and tests

## Instructions

The skill workflow consists of three sequential steps:
1. **Planner** — Designs the feature and identifies affected components
2. **Writer** — Implements the feature across all components
3. **Tester** — Validates the feature with comprehensive tests
