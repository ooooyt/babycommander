# Research Findings & Implementation Plan

**Enhancement 1:** Persist phases and update their statuses during a task
**Enhancement 2:** Persist all tools each phase uses

Date: 2025-08-22

**Implementation status: COMPLETED** — both enhancements were implemented and verified (full test suite: 910 tests passing).

---

## 1. Research Findings

### 1.1 Current State Summary

| Concern | Status |
|---|---|
| In-memory phases + status transitions | ✅ Already done (`PlanTool`) |
| Persist **task** (aggregate status) | ✅ Already done (`TaskPersistenceConsumer` + `TaskEntity`) |
| Persist **individual phases** | ❌ Not done (only aggregate task status is saved) |
| Tool-execution DB entity/repo/service | ✅ Already built (`TaskToolExecutionEntity`, repo, `ProjectTaskService`) |
| Tool-execution DB **wired into execution flow** | ❌ Not wired — `createToolExecution` is never called |
| Tool calls logged to file | ✅ Already done (`ToolCallLogger` in `ResilientToolExecutor`) |

### 1.2 Key Architectural Facts

- `PlanTool` stores phases **only in-memory** via `CopyOnWriteArrayList<UiEvent.Phase>`, with statuses (active/pending/completed/failed), and publishes `PlanUpdate` events on the EventBus.
- Phases have **no stable ID** — only index-based references within the list.
- Persistence already exists via `TaskPersistenceConsumer` (listens to `PlanUpdate` events, creates/updates `TaskEntity` in ObjectBox with STARTED/FAILED/COMPLETED status) and `ProjectTaskService`.
- `TaskToolExecutionEntity` + `TaskToolExecutionRepository` exist, and `ProjectTaskService` provides `createToolExecution` / `updateToolExecutionStatus` — but these are **never called** anywhere; the infrastructure is **unwired**.
- Tool execution flows through `ResilientToolExecutor` (the single choke point wrapping `DefaultToolExecutor`), which logs to a file via `ToolCallLogger` but does **not** persist to DB.
- `PlanTool`, `ResilientToolExecutor`, and `AgentFactory` are instantiated as plain objects (`new ...`), **not** CDI-managed, so injecting DB services requires manual wiring or CDI conversion.
- `UiEvent.Phase` is a simple record (title, description, status) with no phase ID or tool association.

### 1.3 Complexity Assessment

| Enhancement | Complexity | Effort | New files | Modified files |
|---|---|---|---|---|
| **1. Phases + statuses** | **LOW** | ~0.5–1 day | 2 (`PhaseEntity`, `PhaseRepository`) | ~5 (`TaskPersistenceConsumer` is core) |
| **2. Tools per phase** | **MEDIUM** | ~1–2 days | 0 (DB layer exists) | ~5 (`ResilientToolExecutor` + `AgentFactory` wiring is core) |
| **Both** | LOW–MEDIUM | ~1.5–3 days | 2 | ~8–10 |

**Key insight:** Neither enhancement requires significant new database work. Enhancement 1's core is in `TaskPersistenceConsumer` (phases already flow through it). Enhancement 2's core is in `ResilientToolExecutor` + `AgentFactory` (the DB service methods already exist but are unused — the effort is wiring + context propagation, not persistence).

---

## 2. Implementation Plan

### Phase A — Enhancement 1: Persist phases + statuses

**Goal:** Persist each phase (title, description, status, order) alongside the existing task record, and update phase statuses as `PlanTool` transitions them.

**Steps:**

1. **Add phase identity** — Add a stable `phaseId` (UUID) to the `UiEvent.Phase` record so async status updates map correctly. Update the UI rendering path accordingly.
2. **Create `PhaseEntity`** (new ObjectBox `@Entity`): `id`, `taskId` (FK), `phaseId`, `title`, `description`, `status`, `sortOrder`, timestamps. Register in `ObjectBoxStore` + `objectbox-model.json`.
3. **Create `PhaseRepository`** (new): find by taskId, save, update status — mirrors `TaskToolExecutionRepository`.
4. **Extend `ProjectTaskService`** — add `createPhases(taskId, phases)` and `updatePhaseStatus(phaseId, status)`.
5. **Wire into `TaskPersistenceConsumer`** — in `onPlanUpdate()`, persist each phase (title/desc/status) alongside the existing task record. This is the core change.
6. **Verify** — run a task end-to-end and confirm phase rows are created and statuses transition correctly.

**Files touched:**
- `ui/UiEvent.java` (add `phaseId`)
- `db/entity/PhaseEntity.java` (new)
- `db/repository/PhaseRepository.java` (new)
- `service/ProjectTaskService.java`
- `ui/TaskPersistenceConsumer.java` (core)
- `db/ObjectBoxStore.java` + `objectbox-model.json` (register entity)
- `tool/PlanTool.java` (optional: carry `phaseId`)

---

### Phase B — Enhancement 2: Persist all tools each phase uses

**Goal:** Record every tool call (name, args, result status) and attribute it to the current task/phase.

**Steps:**

1. **Add phase attribution to execution entity** — add `phaseId`/`phaseTitle` to `TaskToolExecutionEntity` so tools are grouped per phase. Register the column in `objectbox-model.json`.
2. **Establish task/phase context** — thread a "current taskId + active phase" context from the chat/agent pipeline into tool execution. This is the **bulk of the effort** (context propagation).
3. **Wire `ProjectTaskService` into `ResilientToolExecutor.execute()`** — on entry call `createToolExecution(taskId, toolInfo)` with tool name + arguments; on success `updateToolExecutionStatus(id, COMPLETED)`; on exception `updateToolExecutionStatus(id, FAILED)`. **Truncate stored args/result fields to a maximum of 200 characters** when persisting, to keep rows compact. **Do NOT truncate the tool name** — the correct tool name is important for accurate attribution.
4. **Update `AgentFactory.buildToolExecutorMap()`** — pass the task/phase context + `ProjectTaskService` (or a provider) when constructing `new ResilientToolExecutor(tool, request)`.
5. **Update `CodeGenLifecycle` wiring** — provide the service/context to `AgentFactory` (currently `new`-ed, not CDI).
6. **Handle DB-disabled case** — ensure tool execution does not break when `db.enabled=false`.
7. **Verify** — run a task; confirm tool execution rows are created, attributed to the correct phase, and statuses updated.

**Files touched:**
- `tool/ResilientToolExecutor.java` (core hook point)
- `agent/AgentFactory.java` (injection/wiring — core)
- `CodeGenLifecycle.java` (wiring)
- `db/entity/TaskToolExecutionEntity.java` (add `phaseId`/`phaseTitle`)
- `db/ObjectBoxStore.java` + `objectbox-model.json` (add column)

---

## 3. Risks & Notes

- **Async event-bus ordering** — phase status updates and task creation must be ordered correctly; the consumer already handles create-on-first-update.
- **Phase identity** — adding `phaseId` to `UiEvent.Phase` touches the UI path; needed for reliable async status mapping.
- **CDI wiring** — `PlanTool`, `ResilientToolExecutor`, and `AgentFactory` are `new`-ed; getting services into them requires constructor passing or CDI conversion.
- **Performance** — a DB write per tool call adds overhead; may need async/batched writes.
- **Field length** — stored tool-execution args/result fields are truncated to a maximum of 200 characters to keep rows compact; the **tool name is never truncated** since the correct tool name is important for accurate attribution.
- **Phase attribution accuracy** — at tool-execution time the LLM hasn't yet called `completePhase`, so tools can only be attributed to the *currently active* phase (approximation).
- **Suggested staging** — deliver Enhancement 1 first (lower risk, builds on existing pattern); for Enhancement 2, consider a "tools per task" milestone (without phase attribution) before adding phase attribution.
