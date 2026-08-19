---
name: refactor-large-codebase
description: Refactor a large codebase with structural changes, module extraction, and migration planning
triggers: refactor large codebase, restructure project, extract module, split monolith, modularize codebase, reorganize packages, migrate to modules, decouple components, reduce technical debt, improve code architecture, large scale refactoring, codebase restructuring, refactor monolithic, break down monolith, extract service, split into modules, reorganize project structure, refactor package structure, clean up architecture, improve project structure
workflow_steps:
  - agentId: planner
    instruction: |
      You are a software architect. Analyze the codebase at ${project} and create a
      detailed refactoring plan for the following task:
      
      ${task}
      
      Your plan must include:
      1. Current architecture analysis — identify pain points and coupling
      2. Target architecture — describe the desired structure
      3. Migration steps — ordered list of incremental changes
      4. Risk assessment — identify breaking changes and mitigation strategies
      5. Testing strategy — how to verify each step
      
      Write the refactoring plan to ${project}/doc/refactoring-plan.md
  - agentId: writer
    instruction: |
      You are a senior developer. Read the refactoring plan at ${project}/doc/refactoring-plan.md
      and execute the migration steps one by one.
      
      For each step:
      1. Make the code changes as specified in the plan
      2. Ensure the project still compiles after each step
      3. Update imports, package declarations, and module descriptors
      4. Preserve existing functionality — do not change behavior
      
      If a step cannot be completed as planned, note the deviation and adapt.
  - agentId: tester
    instruction: |
      You are a QA engineer. Verify that the refactoring at ${project} was completed
      successfully and did not break any existing functionality.
      
      Tasks:
      1. Run the full test suite and report results
      2. Verify that package structure matches the target architecture
      3. Check for any remaining references to old package names
      4. Ensure build configuration (pom.xml/build.gradle) is consistent
      5. Report any issues found
---

# Refactor Large Codebase

This skill handles large-scale refactoring of existing codebases, including
module extraction, package restructuring, and monolith decomposition.

## When to Use

Use this skill when the user requests:
- Restructuring a large codebase
- Extracting modules or services from a monolith
- Reorganizing package structure
- Reducing technical debt at scale
- Migrating to a modular architecture

## Instructions

The skill workflow consists of three sequential steps:
1. **Planner** — Analyzes the codebase and creates a refactoring plan
2. **Writer** — Executes the refactoring steps incrementally
3. **Tester** — Verifies the refactoring didn't break anything
