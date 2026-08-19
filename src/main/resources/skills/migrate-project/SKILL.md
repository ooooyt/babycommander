---
name: migrate-project
description: Migrate a project between frameworks, languages, or platforms with planning, execution, and verification
triggers: migrate project, upgrade framework, migrate to spring boot, migrate to quarkus, migrate to microservices, change technology stack, rewrite in, port to, convert to, upgrade version, platform migration, framework migration, language migration, migrate from, upgrade to, move to, transition to, re-platform, technology upgrade, stack migration, version upgrade, dependency upgrade, migrate application, code migration, system migration, migrate codebase
workflow_steps:
  - agentId: planner
    instruction: |
      You are a software architect. Plan the migration of the project at ${project}
      for the following task:
      
      ${task}
      
      Create a detailed migration plan covering:
      1. Current state inventory — list all technologies, dependencies, and their versions
      2. Target state definition — describe the target framework/language/platform
      3. Gap analysis — identify incompatible APIs, libraries, and patterns
      4. Migration strategy — incremental vs big-bang, with rationale
      5. Migration steps — ordered list with dependencies between steps
      6. Rollback plan — how to revert if migration fails
      7. Testing strategy — how to verify correctness at each stage
      
      Write the migration plan to ${project}/doc/migration-plan.md
  - agentId: writer
    instruction: |
      You are a senior developer. Read the migration plan at ${project}/doc/migration-plan.md
      and execute the migration steps for the project at ${project}.
      
      For each step:
      1. Apply the changes specified in the plan
      2. Update build configuration (pom.xml, build.gradle, package.json, etc.)
      3. Rewrite or adapt source code to the target framework/language
      4. Update configuration files (application.yml, Dockerfile, etc.)
      5. Ensure the project compiles after each step
      
      If you encounter issues not covered by the plan, document them and adapt.
  - agentId: tester
    instruction: |
      You are a QA engineer. Verify that the migration of ${project} was completed
      successfully and the application works correctly in the new environment.
      
      Tasks:
      1. Run the full test suite and report results
      2. Verify that all dependencies are updated to target versions
      3. Check for any remaining references to old framework/language APIs
      4. Verify build configuration is consistent with target platform
      5. Run a smoke test to verify the application starts and responds
      6. Report any issues or incomplete migration items
---

# Migrate Project

This skill handles project migration between frameworks, languages, or platforms,
including technology stack upgrades and re-platforming efforts.

## When to Use

Use this skill when the user requests:
- Migrating from one framework to another (e.g., Spring to Quarkus)
- Upgrading to a new major version of a framework
- Porting code from one language to another
- Moving from monolith to microservices
- Changing technology stack (e.g., adding a new database, message broker)
- Platform migration (e.g., on-prem to cloud, VM to containers)

## Instructions

The skill workflow consists of three sequential steps:
1. **Planner** — Analyzes current state and creates a migration plan
2. **Writer** — Executes the migration steps incrementally
3. **Tester** — Verifies the migrated project works correctly
