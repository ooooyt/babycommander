# Bug Scan Report

**Project:** babycommander (Quarkus 3.20.6.2, Java 21/22, Maven)
**Date:** 2026-08-28
**Scope:** Static review of all 139 source files — findings only, no code changes made.
**Revision:** Re-verified against the current source tree — all 16 findings confirmed; corrected Quarkus version (3.20.6.2) and file/line references.

---

## 1. Summary of Findings

| # | Severity | Component | Issue |
|---|----------|-----------|-------|
| 1 | 🔴 HIGH | `SessionMemory.buildSignature` | Non-deterministic signatures for array arguments break trust/deny persistence |
| 2 | 🔴 HIGH | `McpToolAdapter` | MCP tools are never exposed to the LLM (missing `@Tool` annotation) |
| 3 | 🔴 HIGH | `EditLoop.run` | Off-by-one error in reported fix-attempt count |
| 4 | 🟠 MEDIUM | `HookManager.inScope` | Uncaught `InvalidPathException` aborts tool calls |
| 5 | 🟠 MEDIUM | `TestRunner.detectTestCommand` | Timeout misdetected as a successful command probe |
| 6 | 🟠 MEDIUM | `IntentDetector.detect` | `DOCUMENT` intent never returned; doc tasks misrouted to CREATE |
| 7 | 🟠 MEDIUM | `TokenCounter.reset()` | No-op reset leaves stale calibration state |
| 8 | 🟠 MEDIUM | `TerminalUIAdapter` | Constructs a second `YamlConfigLoader` instead of reusing CDI bean |
| 9 | 🟡 LOW | `ShGuard.checkWriteRedirects` | `$`-prefixed redirect targets skipped (e.g. `> $HOME/.bashrc`) |
| 10 | 🟡 LOW | `PathExtractor.tokenize` | Quote handling splits `"a b"c` into `a b` + `c` |
| 11 | 🟡 LOW | `ShellTool.doExecuteWithTimeout` | `join()` after `destroyForcibly()` can block a full extra timeout |
| 12 | 🟡 LOW | `ResilientToolExecutor.repairJson` | Comma-insertion regex corrupts `: [` patterns |
| 13 | 🟡 LOW | `SessionMemory.markAllowed` | Dead code — ALLOW is never persisted by `HookManager` |
| 14 | 🟡 LOW | `ShGuard.isDangerousGit` | Non-destructive `git checkout -- file` / `git rm` always flagged DANGEROUS |
| 15 | 🟡 LOW | `FileSystemTool.doDeleteFile` | Deletes files while `Files.walk` stream still open |
| 16 | 🟡 LOW | `ConversationCompactor.findBoundary` | Boundary can be -1 when tail is a tool-result run → compaction skipped |

---

## 2. HIGH — Functional Bugs

### 2.1 `SessionMemory.buildSignature` — non-deterministic signatures for array arguments
**File:** `src/main/java/com/ooooyt/babycommander/hook/SessionMemory.java:27-38`

```java
sb.append(args[i] != null ? args[i].toString() : "null");
```

`Object[].toString()` on an array returns the **identity hashcode** (`[Ljava.lang.String;@6d06d69c`), which differs on every call. This breaks exact-match trust/deny for any tool with array/varargs parameters — notably `FileSystemTool.applyFilePatch(String filePath, String[] patches)`:

- `markAllowedAlways(...)` stores a signature with a random hashcode → `isAllowedAlways(...)` **never matches** on the next call, so "always allow" silently fails.
- `markDenied(...)` → `isDenied(...)` never matches, so a denied patch call is **never auto-denied**.

**Fix:** use `java.util.Arrays.deepToString(args[i])` for array elements.

### 2.2 `McpToolAdapter` — MCP tools are never exposed to the LLM
**File:** `src/main/java/com/ooooyt/babycommander/tool/mcp/McpToolAdapter.java`

`callMcpTool(...)` has **no `@Tool` annotation**. `AgentFactory.buildToolExecutorMap()` discovers tools via `ToolSpecifications.toolSpecificationsFrom(tool)`, which only picks up `@Tool`-annotated methods → MCP tools registered via `registerMcpTools()` produce **zero tool specs** and are invisible to agents. Additionally, `McpClientManager.discoverToolsFromProcess()` reads only one 4096-byte chunk from the process — the stdio JSON-RPC protocol is never actually spoken (it is a stub, not a working MCP client).

**Fix:** annotate `callMcpTool` with `@Tool` (with `@P` params) and implement a real JSON-RPC read loop.

### 2.3 `EditLoop.run` — off-by-one in reported attempts
**File:** `src/main/java/com/ooooyt/babycommander/editloop/EditLoop.java:91`

```java
return EditLoopResult.success(testOutput, attempt - 1);
```

`attempt` starts at 1, so a fix that succeeds on the **first** attempt reports `attempts = 0`, contradicting the field's documented meaning ("number of fix attempts made"). This number is surfaced to the user in `BugfixStrategy`/`RefactorStrategy`/`ExtensionStrategy` success messages.

**Fix:** `return EditLoopResult.success(testOutput, attempt);`

---

## 3. MEDIUM — Robustness / Behavior Bugs

### 3.1 `HookManager.inScope` — uncaught `InvalidPathException`
**File:** `src/main/java/com/ooooyt/babycommander/hook/HookManager.java:256-266`

`java.nio.file.Path.of(rawPath)` throws an unchecked `InvalidPathException` for malformed paths (e.g. containing NUL). It propagates out of `beforeToolCall` and aborts the tool call with an unexpected exception instead of a clean denial.

**Fix:** wrap in try/catch and return `false` on invalid paths.

### 3.2 `TestRunner.detectTestCommand` — timeout misdetected as success
**File:** `src/main/java/com/ooooyt/babycommander/orchestrator/TestRunner.java:28-50`

A candidate command that **times out** (10s) returns `"Exit Code: -1"` which contains neither `"command not found"` nor `"is not recognized"`, so it is selected as the test command even though it never ran. On a slow project where `mvn` exists but takes >10s, `mvn test` is "detected" even when the project actually uses Gradle.

**Fix:** also skip candidates whose output indicates a timeout/exit code -1.

### 3.3 `IntentDetector.detect` never returns `DOCUMENT`
**File:** `src/main/java/com/ooooyt/babycommander/intent/IntentDetector.java:32-51`

`detect()` only returns BUGFIX / REFACTOR / EXTENSION / CREATE — `DOCUMENT` is reachable only via the `--mode` flag. A user task like *"write documentation for X"* is routed to the CREATE multi-agent pipeline instead of `DocumentStrategy`.

**Fix:** add a DOCUMENT keyword check (using `KEY_DOCUMENT` + fallback keywords) before the CREATE fallback.

### 3.4 `TokenCounter.reset()` is a no-op
**File:** `src/main/java/com/ooooyt/babycommander/util/TokenCounter.java:111`

`reset()` has an empty body but is called from `ToolCallAwareChatMemory.clear()`. The global calibration state (`globalCharsPerToken`, `globalCalibrationFactor`, `globalSampleCount`) is never reset — misleading API and stale calibration after a session clear.

### 3.5 `TerminalUIAdapter` constructs a second `YamlConfigLoader`
**File:** `src/main/java/com/ooooyt/babycommander/ui/tui/TerminalUIAdapter.java:54`

```java
boolean showToolCallPairs = new YamlConfigLoader().getConfig().showToolCallPairs;
```

Re-parses `agents.yaml` + `hooks.yaml` on every TUI construction instead of using the CDI-injected instance — wasteful and can produce a divergent config view.

---

## 4. LOW — Code Smells / Edge Cases

| # | Location | Issue |
|---|----------|-------|
| 9 | `ShGuard.java:429` | `checkWriteRedirects` skips `$`-prefixed targets, so `echo x > $HOME/.bashrc` is not flagged (inconsistent with "dangerous always wins") |
| 10 | `PathExtractor.java:tokenize` | Quote handling flushes the token at the closing quote; `"a b"c` splits into `a b` + `c` |
| 11 | `ShellTool.java:doExecuteWithTimeout` | `outputThread.join(timeoutSeconds * 1000L)` after `destroyForcibly()` can block a full extra timeout |
| 12 | `ResilientToolExecutor.java:repairJson` | Comma-insertion regex `(\S)(\s+)([\[\{])` corrupts `: [` patterns (re-validated afterward, so safe but poor repair quality) |
| 13 | `SessionMemory.markAllowed` | Dead code — `HookManager` never calls it (ALLOW is deliberately not persisted) |
| 14 | `ShGuard.isDangerousGit` | `git checkout -- file` and `git rm` (common, non-destructive ops) are always DANGEROUS — policy too strict |
| 15 | `FileSystemTool.doDeleteFile` | Deletes files while the `Files.walk` stream is still open — can throw `NoSuchFileException` on some platforms |
| 16 | `ConversationCompactor.findBoundary` | If the tail is a tool-result run with no user message, boundary can be -1 → compaction silently skipped |

---

## 5. What Looks Solid

- **ShGuard AST analyzer** — robust handling of quoting/escapes/command-substitution/fork bombs; the `classify()` parse-error path correctly never downgrades a real danger.
- **HookManager** most-restrictive-wins resolution and in-scope auto-trust gating are well-designed.
- **ResilientToolExecutor** correctly repairs malformed LLM JSON and converts type-mismatch exceptions into corrective tool messages.
- **ToolCallAwareChatMemory** — dangling-tool-call stripping and compaction boundaries are carefully handled.

---

## 6. Recommended Fix Order

1. **`SessionMemory.buildSignature`** array handling — silently breaks trust/deny persistence (HIGH).
2. **`McpToolAdapter`** missing `@Tool` — MCP feature is non-functional (HIGH).
3. **`EditLoop`** attempts off-by-one — wrong user-facing reporting (HIGH).
4. **`HookManager.inScope`** — uncaught `InvalidPathException` (MEDIUM).
5. **`TestRunner.detectTestCommand`** — timeout misdetection (MEDIUM).
6. **`IntentDetector`** — DOCUMENT intent unreachable (MEDIUM).
7. Remaining MEDIUM items (TokenCounter reset, TerminalUIAdapter config) and LOW items.
