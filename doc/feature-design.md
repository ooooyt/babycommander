# SH-GUARD — AST-Based Shell Command Safety Analyzer (Pure Java)

**Project:** `babycommander` (`/Users/yangtao/local-repo/babycommander`)
**Author:** Software Architect (Planning phase)
**Status:** Draft — feature design
**Target module:** `com.ooooyt.babycommander.hook` (currently home of `ShellCommandAnalyzer`)
**Language / constraints:** Java 21+, Quarkus, LangChain4j; **zero new runtime dependencies** (hand-written lexer + recursive-descent parser in pure Java).

> This file is a copy of the authoritative design document at
> `/Users/yangtao/local-repo/doc/design.md` (see Plan Status: Phase 1 planner).

---

## 0. Executive Summary

Baby Commander's shell safety gate currently uses a **token-based** classifier
(`com.ooooyt.babycommander.hook.ShellCommandAnalyzer`) that splits a command
string on shell operators and inspects flattened tokens. This approach is
vulnerable to a large class of **obfuscation / evasion** techniques and mis-parses
several legitimate shell constructs:

- Command substitution `$(...)` / backticks are invisible to the tokenizer;
- Quoted/escaped words (`r"m"`, `\rm`, `'rm'`) defeat command-name matching;
- Heredocs, subshells `(...)`, brace groups `{ ...; }`, and control structures
  (`if`/`while`/`for`/`case`) are not parsed;
- `VAR=rm; $VAR -rf /` indirection is not tracked;
- Redirection forms such as `2>>`, `&>`, `<>`, fd-number redirects (`1>`, `2>`),
  here-strings (`<<<`) and process substitution `<(...)` are mishandled.

This design introduces **SH-GUARD**: a complete, **pure-Java** shell lexer and
recursive-descent parser that builds an **Abstract Syntax Tree (AST)** for a
POSIX-ish shell grammar, followed by a **semantic safety analysis** pass that
classifies the command as `DANGEROUS`, `ASK_ONCE`, or `SAFE` — with
parse-aware handling of quoting, escapes, expansions, redirections, pipelines,
control flow, and known-variable constant folding.

The public entry points remain backward compatible: `ShellCommandAnalyzer` is
kept as a thin, deprecated facade delegating to `ShGuard`, so `HookManager`,
`hooks.yaml` semantics, and the entire existing test suite keep working while
the new engine is adopted.

---

## 1. Components Affected

### 1.1 New components (new package `com.ooooyt.babycommander.shguard`)

| File (class) | Role |
|---|---|
| `ShToken.java` | Value object: token type, lexeme, source span, quoting flags. |
| `ShTokenType.java` | Enum: `WORD`, `ASSIGNMENT_WORD`, `IO_NUMBER`, operators (`&&`, `||`, `|`, `|&`, `;`, `;;`, `&`, `(`, `)`, `{`, `}`, `<`, `>`, `>>`, `>|`, `<<`, `<<-`, `<<<`, `<&`, `>&`, `<>`, `NEWLINE`, `EOF`). |
| `ShLexer.java` | Hand-written tokenizer for shell words (quoting, escapes, expansions, heredoc bodies, comments `#`). |
| `ShLexException.java` | Thrown on malformed input (unterminated quote/heredoc, illegal char); callers treat as *parse failure* → conservative result. |
| `ShParser.java` | Recursive-descent parser producing the AST. Handles malformed input by returning a partial AST + error flag (does **not** throw for ordinary user mistakes). |
| `ShAst.java` | Root node of a parsed command list. |
| AST node types (`ShNode.java` hierarchy): `ShCommandList`, `ShAndOr`, `ShPipeline`, `ShSimpleCommand`, `ShCompoundCommand` (subshell / brace group / if / while / until / for / case), `ShFunctionDef`, `ShRedirect`, `ShWord`, `ShWordPart` (`ShLiteralPart`, `ShVariablePart`, `ShCommandSubstPart`, `ShArithmeticPart`), `ShAssignment`, `ShHereDoc`. | Typed AST model. |
| `ShWord.java` | Model of a shell word: ordered list of parts (literal / variable / command-substitution / arithmetic) plus **dequoted text** and **`anyPartQuoted`** flag. |
| `ShGuard.java` | **Public facade**. `analyze(String): DangerLevel` and `analyzeDetailed(String): ShGuardReport`. |
| `ShGuardReport.java` | `record` — `level`, `violations: List<ShViolation>`, `parseError: String`, `astSummary`. |
| `ShViolation.java` | `record` — `reason: ShReason`, `detail: String`, `subCommand: String`, `span` (start/end offsets). |
| `ShReason.java` | Enum of classification reasons (`DANGEROUS_COMMAND`, `SUDO_ESCALATION`, `DANGEROUS_GIT_OP`, `DANGEROUS_PACKAGE_OP`, `DANGEROUS_FLAG`, `WRITE_REDIRECT_SYSTEM_PATH`, `DB_DESTRUCTION`, `REMOTE_CODE_EXECUTION`, `FORK_BOMB`, `UNKNOWN_COMMAND`, `VARIABLE_COMMAND_NAME`, …). |
| `ShGuardPolicy.java` | Data model of configurable policy (dangerous/safe command sets, system-path prefixes, write-redirect allowlist like `/dev/null`, dangerous git/package ops, option toggles). Ships with defaults **identical** to today's hard-coded sets. |
| `ShGuardConfig.java` | Reads optional overrides from `hooks.yaml`/`agents.yaml` (`shGuard:` block) and env vars. |
| `ShellCommandAnalyzer` (modified) | Deprecated facade: `analyze(String)` delegates to `ShGuard.analyze(...)`; Javadoc points to `ShGuard`. |

### 1.2 Modified components

| Component | Change |
|---|---|
| `com.ooooyt.babycommander.hook.ShellCommandAnalyzer` | Become a delegation wrapper (see §1.1). All `private static` logic (token splitting, rule tables, git/package/flag checks) moves into `shguard` package. |
| `com.ooooyt.babycommander.hook.HookManager` | Call site in `resolveLevel(...)` (~line 470) now reaches `ShGuard` through the wrapper. Add DEBUG-level logging of `ShGuardReport` (reason + sub-command) for observability. Behavior/order of `dangerous`/`safe` pattern overrides unchanged. |
| `com.ooooyt.babycommander.hook.DangerLevel` | **Unchanged** — public contract stays stable (`DANGEROUS, ASK_ONCE, SAFE`). |
| `src/main/resources/hooks.yaml` | No schema change required. Optional new `shGuard:` section for policy overrides (see §3.3). |
| `com.ooooyt.babycommander.config.AgentConfig` | Optional: add `AgentConfig.ShGuardConfig` record mirroring `shGuard:` YAML block (nullable). |
| `pom.xml` | **No new dependencies.** (ANTLR is already present for `CodeSkeletonLexer` but is deliberately *not* used for SH-GUARD — see §5.1.) |
| `README.md` / `docs/` | Document the new analyzer, its guarantees, and its known limitations (§8). |

### 1.3 Unaffected components

`ShellTool`, `FileSystemTool`, `InternetTool`, `ToolRegistry`, `ToolCallLogger`,
`ResilientToolExecutor`, `PathExtractor`, the ObjectBox persistence layer, and
the TUI/REST layers — none need changes. `ShellTool` continues to pass the raw
command string; all analysis happens in the guard.

---

## 2. Data Model Changes

### 2.1 New AST model (in-memory, no persistence)

```
ShAst
└── root: ShCommandList
    └── commands: List<ShAndOr>
        └── pipelines: List<ShPipeline>
            └── cmds: List<ShCommand>          // simple | compound | functionDef
                ├── ShSimpleCommand
                │   ├── prefix: List<ShAssignment | ShRedirect>
                │   ├── words: List<ShWord>    // [0] = command name after dequote
                │   └── redirects: List<ShRedirect>
                ├── ShCompoundCommand
                │   ├── kind: SUBSHELL | BRACE_GROUP | IF | WHILE | UNTIL | FOR | CASE
                │   ├── branches/body: nested ShCommandList / case items
                │   └── redirects: List<ShRedirect>
                └── ShFunctionDef
                    ├── name: ShWord
                    └── body: ShCompoundCommand
ShWord
├── parts: List<ShWordPart>
│   ├── ShLiteralPart  (text, wasQuoted: boolean, wasEscaped: boolean)
│   ├── ShVariablePart (name, braced: boolean, defaultValue?: String, isSpecial: boolean)
│   └── ShCommandSubstPart / ShArithmeticPart (inner: ShAst)
└── dequoted(): String                     // concatenation of literal parts,
                                           // quotes/escapes removed, expansions retained
    isFullyStatic(): boolean               // no variable/substitution/arithmetic parts
```

Key modeling decisions:

1. **Quoting is first-class data.** Every literal part records whether it was
   quoted (`'…'`, `"…"`) or escaped (`\c`). `r"m"` therefore dequotes to `rm`
   and is classified exactly like `rm`. This single change closes most of the
   evasion surface the token-based analyzer had.
2. **Expansions are nodes, not text.** `$(…)`, `` `…` ``, `${VAR}`, `$VAR`,
   `$((…))` remain structured so the semantic pass can (a) recursively analyze
   command substitution bodies, and (b) constant-fold simple `VAR=value`
   assignments for known-command detection.
3. **Spans everywhere.** Every node carries `start`/`end` offsets into the raw
   command, enabling precise violation reporting and future UI highlighting.

### 2.2 `ShGuardReport` / `ShViolation` / `ShReason`

```java
public record ShViolation(ShReason reason, String detail,
                          String subCommand, int start, int end) {}
public record ShGuardReport(DangerLevel level,
                            List<ShViolation> violations,
                            String parseError,      // null when parse succeeded
                            boolean parsed) {}
```

`ShReason` values (initial set):

`DANGEROUS_COMMAND`, `SUDO_ESCALATION`, `DANGEROUS_GIT_OP`,
`DANGEROUS_PACKAGE_OP`, `DANGEROUS_FLAG`, `WRITE_REDIRECT_SYSTEM_PATH`,
`DB_DESTRUCTION`, `REMOTE_CODE_EXECUTION`, `FORK_BOMB`,
`UNKNOWN_COMMAND`, `VARIABLE_COMMAND_NAME`, `PARSE_FAILURE`.

### 2.3 Policy model (`ShGuardPolicy`)

```java
public final class ShGuardPolicy {
    Set<String> dangerousCommands;      // defaults = today's DANGEROUS_COMMANDS
    Set<String> safeCommands;           // defaults = today's SAFE_COMMANDS
    Set<String> systemPathPrefixes;     // /, /etc, /boot, /dev, /proc, /sys,
                                        // /usr, /bin, /sbin, /var, /lib, /opt, /root
    Set<String> writeRedirectAllowlist; // /dev/null, /dev/stdout, /dev/stderr, /dev/tty
    Set<String> remoteFetchCommands;    // curl, wget (pipe-to-shell detection)
    Set<String> shellExecutorCommands;  // bash, sh, dash, zsh, ksh, fish
    boolean flagSudoAlways;             // default true
    boolean foldAssignments;            // default true (constant folding of VAR=val)
    boolean strictUnknownCommand;       // default false (unknown → ASK_ONCE)
    boolean blockWriteRedirectToRoot;   // default true ('>' or '>>' to '/' prefix)
}
```

### 2.4 Persistence / DB impact

**None.** SH-GUARD is stateless and in-memory. No ObjectBox entities, no new
tables, no migration. (Tool-execution audit rows already written by
`TaskToolExecutionRepository` are unaffected.)

---

## 3. API Contract

### 3.1 Public API (stable, backward compatible)

```java
package com.ooooyt.babycommander.hook;          // existing, deprecated facade
public final class ShellCommandAnalyzer {
    public static DangerLevel analyze(String command);  // UNCHANGED signature
}

package com.ooooyt.babycommander.shguard;        // new primary API
public final class ShGuard {
    public static DangerLevel analyze(String command);            // same semantics as today
    public static ShGuardReport analyzeDetailed(String command);  // new, enriched
    public static ShGuard withPolicy(ShGuardPolicy policy);       // instance API for tests/config
}
```

**Semantics (must match today's contract):**

| Input | Result |
|---|---|
| `null`, blank, whitespace-only | `ASK_ONCE` |
| Any sub-command dangerous | `DANGEROUS` (most-restrictive-wins across the whole AST) |
| All sub-commands recognized-safe | `SAFE` |
| Any sub-command unknown / unclassifiable | `ASK_ONCE` |
| Parse failure (unterminated quote, heredoc, `)` etc.) | `ASK_ONCE` + `parseError` set (conservative; never `SAFE`) |

### 3.2 Callers / consumers of the API

1. `HookManager.resolveLevel(...)` — primary caller (ShellTool path).
2. `ShellCommandAnalyzerTest` (regression — all existing cases must pass unchanged).
3. New `ShGuardTest`, `ShLexerTest`, `ShParserTest`, `ShGuardEvasionTest`,
   `ShGuardPolicyTest`, and an integration test through `HookManager`.
4. Future consumers (e.g., a `/guard explain` slash command or TUI rendering of
   violation reasons) can use `analyzeDetailed`.

### 3.3 Optional configuration surface (new, non-breaking)

`hooks.yaml` may gain an optional top-level section; `AgentConfig` gains a
nullable matching record. All keys optional; defaults preserve today's behavior.

```yaml
# hooks.yaml — optional
shGuard:
  foldAssignments: true          # VAR=rm; $VAR -rf /  → detected
  flagSudoAlways: true
  strictUnknownCommand: false
  writeRedirectAllowlist: ["/dev/null", "/dev/stdout", "/dev/stderr", "/dev/tty"]
  dangerousCommands: []          # additive overrides
  safeCommands: []               # additive overrides
  systemPathPrefixes: []         # additive overrides
```

Env-var equivalents (optional): `BABY_COMMANDER_SHGUARD_FOLD_ASSIGNMENTS`,
`BABY_COMMANDER_SHGUARD_STRICT_UNKNOWN_COMMAND`, etc.

---

## 4. Business Logic — Service Layer Design and Algorithms

### 4.1 Pipeline (three stages)

```
raw command string
   │
   ▼
┌──────────────────────┐   ┌──────────────────────┐   ┌─────────────────────────────┐
│ 1. ShLexer           │──▶│ 2. ShParser           │──▶│ 3. ShGuard semantic pass    │
│    tokens + spans,   │   │    recursive descent  │   │    AST walk + policy lookup │
│    quote/escape info │   │    → ShAst            │   │    → ShGuardReport          │
└──────────────────────┘   └──────────────────────┘   └─────────────────────────────┘
        throws only on         never throws: returns         never throws:
        ILLEGAL state           partial AST + error flag      conservative ASK_ONCE on
        (→ ASK_ONCE)            (→ ASK_ONCE)                  unexpected AST shapes
```

**Failure philosophy:** the guard must **never crash** the agent loop and must
**never under-classify on ambiguity**. Every failure path resolves to at least
`ASK_ONCE` (a human confirmation), and any *recognized* danger is `DANGEROUS`.

### 4.2 Lexer algorithm (`ShLexer`)

State machine over the raw string. Token kinds and handling:

1. **Whitespace** separates `WORD`s; `NEWLINE` is a list separator.
2. **`#` comment** when at word start → skip to newline.
3. **Quoting:**
   - `'…'` single quotes: literal (no expansion), all parts marked `wasQuoted`.
   - `"…"` double quotes: literal + expansions allowed; `\` escapes only
     `$ ` " \ newline`; parts marked `wasQuoted`.
   - `\c` outside quotes: literal `c`, marked `wasEscaped`.
   - `$'…'` ANSI-C quoting: decode `\n`, `\t`, `\xHH`, etc., marked quoted.
4. **Expansions (tokenized as structured parts):**
   - `$NAME`, `${NAME}`, `$?`, `$#`, `$@`, `$*`, `$$`, `$!`, `$0`…`$9`.
   - `$( … )` and `` ` … ` `` **command substitution** — balanced-paren aware;
     inner text is re-lexed/parsed recursively in stage 2.
   - `$(( … ))` arithmetic expansion — balanced-aware; inner `$(…)` handled.
5. **Operators** (longest match first): `<<-`, `<<<`, `<<`, `>>`, `>|`, `>&`, `<>`,
   `<&`, `&&`, `||`, `|&`, `;;`, `;`, `|`, `&`, `(`, `)`, `{`, `}`, `<`, `>`, `=`
   (only at word start → `ASSIGNMENT_WORD`).
6. **`IO_NUMBER`**: leading digits immediately before a redirect operator
   (`2>`, `1>>`, `3>&1`).
7. **Heredocs (`<<EOF`, `<<-EOF`, `<<'EOF'`, `<<\EOF`, `<<<"str"`):**
   - Quoted/escaped delimiter → body is literal.
   - Unquoted delimiter → body may contain `$(…)` and `` `…` `` which are
     lexed/parsed as expansions; `$VAR` kept as variable parts.
   - Delimiter match: line-stripped (`<<-` strips leading tabs), trailing `\r`
     tolerated on CRLF input.
8. **Lexical errors** (unterminated quote, unterminated heredoc, stray `)`,
   unmatched `}`) → record error, stop; parser returns partial AST + error flag.

### 4.3 Parser algorithm (`ShParser` — recursive descent)

Grammar (subset of POSIX shell, sufficient for classification):

```
program        := list EOF
list           := and_or ( ( ';' | '&' | NEWLINE ) and_or )*
and_or         := pipeline ( ( '&&' | '||' ) pipeline )*
pipeline       := ( '!' pipeline )?
                  command ( ( '|' | '|&' ) command )*
command        := simple_command
                | compound_command redirect*
                | function_def
simple_command := ( assignment | io_redirect )* ( WORD )* ( io_redirect )*
compound_command := '(' list ')'
                 | '{' list ';' '}'
                 | 'if' list 'then' list ( 'elif' list 'then' list )* ('else' list)? 'fi'
                 | 'while'|'until' list 'do' list 'done'
                 | 'for' WORD ( 'in' WORD* )? ';'? 'do' list 'done'
                 | 'case' WORD 'in' ( pattern ')' list ';;' )* 'esac'
function_def   := WORD '(' ')' ( compound_command | simple_command )
io_redirect    := ( IO_NUMBER )? ( op ) WORD
                | heredoc ( body consumed in lexer )
```

Recursive-descent over the token stream with **one-token lookahead**. The
`case` patterns are kept as `ShWord` lists (globbing patterns are not evaluated
by the guard — only the branch bodies are classified). Reserved-word handling
(`if/`then/`fi`, `while`, `for`, `case`, `{`, `(`, `function`) uses the shell
rule that a reserved word is recognized only in command position.

### 4.4 Semantic classification pass (`ShGuard`)

Walk the AST and compute the most restrictive `DangerLevel`, following rules
(ordered; most restrictive wins):

1. **Recursive closure:** classify *every* `ShSimpleCommand` reachable from:
   the top list, every pipeline stage, every compound-command branch,
   every function body, **every command-substitution AST** (`$(…)`, backticks),
   and every **unquoted heredoc body** (where expansions are executed).
2. **Command-name resolution (`resolveCommandName`):**
   - Skip leading assignments (`FOO=bar`) and redirects in a simple command.
   - Dequote the first word; if it is **fully static** → use it for rule lookup.
   - If the first word contains a **variable part** and constant folding
     resolves it (see rule 3) → use the folded value.
   - If it contains **command substitution** or an **unresolvable variable** →
     reason `VARIABLE_COMMAND_NAME`, level `ASK_ONCE` (unless a static prefix
     matches a dangerous command, e.g. `$PREFIX rm` still contains a literal
     dangerous word later — but the *name position* is unresolvable → ASK_ONCE).
3. **Constant folding of assignments (`foldAssignments`):** within one
   `ShCommandList` scope, collect `NAME=staticWord` assignments and substitute
   their dequoted values into subsequent `$NAME`/`${NAME}` variable parts
   (best-effort, no transitive cycles). This detects `CMD=rm; $CMD -rf /`.
4. **Command rules:**
   - `dangerousCommands.contains(name)` → `DANGEROUS_COMMAND`.
   - `SUDO_ESCALATION`: any `sudo` word (dequoted) anywhere in a simple command
     (including `sudo` as prefix, `sudo -u root …`, `sudo -i`), per policy flag.
   - `DANGEROUS_GIT_OP`: `git push --force|-f`, `git reset --hard`,
     `git rebase --onto`, `git clean -f…`, `git checkout --`, `git branch -D|-d`,
     `git rm`, `git filter-branch` (ported from today's logic, now parsed rather
     than scanned).
   - `DANGEROUS_PACKAGE_OP`: `apt/apt-get remove|purge|autoremove`,
     `dpkg -r|--remove|-P|--purge`, `rpm -e|--erase`.
   - `DANGEROUS_FLAG`: `chmod/chown/chgrp` with `777`, `-R`, or system-path
     target; `rm` with `-rf`/`-r`/`-f` (already covered by `dangerousCommands`,
     kept for flag-level detail).
   - `DB_DESTRUCTION`: `drop database|table|schema`, `truncate table`,
     `DELETE FROM …` (case-insensitive SQL keywords, found anywhere in args).
5. **Redirection rules:** for each `ShRedirect`:
   - Op in `>`, `>>`, `>|`, `&>`, `<>`, `2>`, `2>>`, `1>`, `1>>`, `3>` … → write.
   - If the **dequoted static target** is under a `systemPathPrefix` AND not in
     `writeRedirectAllowlist` → `WRITE_REDIRECT_SYSTEM_PATH` (`DANGEROUS`).
   - Target containing command substitution in a *write* redirect →
     `ASK_ONCE` (unresolvable target, conservative).
   - Read redirects (`<`, `<<`, `<<<`, `<&`) to system paths are **allowed**
     (matches today's behavior — reading `/etc/passwd` is benign).
6. **Remote code execution across pipeline:** a pipeline stage whose command is
   in `remoteFetchCommands` (`curl`, `wget`) immediately followed by a stage in
   `shellExecutorCommands` (`bash`, `sh`, `dash`, `zsh`, `ksh`, `fish`) →
   `REMOTE_CODE_EXECUTION`. Detected on the **AST** (a real `|` between stages),
   which also covers `curl … | sudo bash`, `wget -qO- … | sh -s`.
7. **Fork-bomb detection on the AST:**
   - `name() { … }` whose body is a **pipeline that pipes the function's own
     name to itself** (`:(){ :|:& };:`), including `|&`; or
   - recursion through `$0`/self-invocation inside a function body.
   - (The current string check `command.contains(":(){")` is replaced by the
     structural check, which is far harder to evade via spacing/quotes.)
8. **Safe rule:** every simple command is `safeCommands`-member **and** no
   dangerous argument/flag/redirect applies → `SAFE`.
9. **Unknown rule:** any simple command whose static name is in neither set and
   matches no dangerous pattern → `ASK_ONCE` (`UNKNOWN_COMMAND`); in
   `strictUnknownCommand` mode it would be `DANGEROUS` (default off).

Result aggregation: `DANGEROUS` if **any** node is dangerous; else `SAFE` if
**all** nodes are safe; else `ASK_ONCE`. Parse error → `ASK_ONCE`.

### 4.5 Complexity & performance

- Lexer O(n), parser O(n), semantic walk O(n) with small constant factors
  (constant folding uses a single-pass scope table).
- Commands are typically < 1 KB; worst-case budget `<< 1 ms`. The guard runs on
  the tool-call path only (before process spawn), so it does not affect the TUI
  or streaming latency. No new threads, no I/O, no external processes.

---

## 5. Integration Points

### 5.1 Why "pure Java" (and not ANTLR)

The repo already ships ANTLR (`antlr4-runtime` + `CodeSkeletonLexer.g4`), so an
ANTLR grammar is feasible. **Decision: hand-written lexer + recursive-descent
parser.** Rationale:

- Shell lexical rules (quote-context-dependent operators, `$'…'`, heredoc body
  scanning, command-substitution nesting) are notoriously awkward in context-free
  grammar tools and are cleaner as a hand state machine;
- A pure-Java implementation has **zero codegen step**, keeps the build simple,
  and is easier to test/debug incrementally;
- The classification pass needs rich per-token metadata (quoting flags, spans)
  that is more natural to attach in a hand-written lexer;
- Keeps the "SH-GUARD" feature self-contained — exactly the "pure Java" ask.

### 5.2 HookManager wiring

`resolveLevel(...)` currently ends with:

```java
if ("ShellTool".equals(toolName) && command != null) {
    if (matched == DangerLevel.DANGEROUS || matched == DangerLevel.SAFE) {
        return matched;                       // hooks.yaml stays authoritative
    }
    return ShellCommandAnalyzer.analyze(command);
}
```

Change is minimal:
- `ShellCommandAnalyzer.analyze(command)` → delegates to `ShGuard.analyze(command)`.
- Add before the return: `LOG.debugf("SH-GUARD: level=%s violations=%s", …)`
  using `analyzeDetailed` only when DEBUG is enabled (avoid double parse).
- `hooks.yaml` `safe`/`dangerous` pattern overrides **remain authoritative**;
  `ask_once` patterns continue to be superseded by SH-GUARD classification
  (unchanged UX — safe commands stay silent, destructive commands prompt/deny).

### 5.3 Config integration

`AgentConfig.HookConfig` gains an optional `ShGuardConfig` sub-record
(§3.3), parsed from the `shGuard:` block if present; `ShGuard.withPolicy(...)`
is constructed once in `HookManager` (or lazily per-call, since construction is
cheap). No YAML loader changes beyond the new field.

### 5.4 Diagnostics / observability

- `ShGuardReport` feeds DEBUG logs; violation reasons map to human-readable
  strings (i18n-ready via the existing `I18n`/`MessageKey` infrastructure).
- No changes to REST status endpoint or UI required; a future
  "why was this blocked?" feature can consume `analyzeDetailed`.

### 5.5 Compatibility and rollback

- `ShellCommandAnalyzer` signature is frozen; all existing callers/tests compile
  unchanged.
- A single system property `BABY_COMMANDER_SHGUARD_DISABLED=true` can revert
  `ShellCommandAnalyzer` to the legacy implementation (kept temporarily in
  `ShellCommandAnalyzerLegacy`, deleted after a release) for hot rollback.

---

## 6. Testing Strategy

### 6.1 Unit tests (new)

| Test class | Covers |
|---|---|
| `ShLexerTest` | Quoting (`'…'`, `"…"`, `\x`, `$'…'`), escapes, operators (all 2-char forms), `IO_NUMBER`, comments, heredoc bodies (quoted vs unquoted, `<<-`), command-substitution nesting, backticks, unterminated quote/heredoc → error. |
| `ShParserTest` | AST structure for representative commands: simple, pipelines (`a \| b \| c`), `&&`/`\|\|` chains, `;`/`&` lists, subshells, brace groups, `if/elif/else`, `while/until/for`, `case`, function defs, redirects (fd forms), parse-error partial ASTs. |
| `ShWordTest` | `dequoted()` and `isFullyStatic()`: `r"m"`→`rm`, `\rm`→`rm`, `'rm -rf /'`→single literal `rm -rf /` (**not** split), `$VAR`, `$(…)`. |
| `ShGuardTest` | Port every existing `ShellCommandAnalyzerTest` case (safe list, dangerous list, redirection, RCE, DB destruction, chains, edge cases) — **must all still pass with identical results**. |
| `ShGuardEvasionTest` | Obfuscation: `r"m" -rf /`, `\r\m -rf /`, `'rm' -rf /`, `${r}m -rf /`, `r${x}m -rf /`, `rm$() -rf /`…, `$(rm -rf /)`, `` `rm -rf /` ``, `echo "$(rm -rf /)"`, `CMD=rm; $CMD -rf /`, `cmd=rm; ${cmd} -rf /`, nested `$($(…))`, `curl … \| bash`, `wget -O- … \| sh -s`, `curl … \| sudo bash`, fork bombs: `:(){ :|:& };:`, `f(){ f|f& };f`, `:(){ :|: };:`, heredoc `cat <<EOF\n$(rm -rf /)\nEOF`, `rm -rf "$(echo /)"`, `git push --force-with-lease` (should be `ASK_ONCE` — not matched today, documented), `sudo` in substitution. |
| `ShGuardPolicyTest` | Config overrides: additive dangerous/safe sets, `writeRedirectAllowlist` (`> /dev/null` safe), `strictUnknownCommand`, `foldAssignments` on/off. |
| `ShGuardReportTest` | Violation reasons, spans, sub-command text, `parseError` on malformed input, `level` fallbacks. |

### 6.2 Integration tests

- `HookManagerShGuardIntegrationTest`: run `resolveLevel` for `ShellTool` with a
  representative `hooks.yaml`; assert:
  - `dangerous` pattern → `DANGEROUS` (override still wins),
  - `safe` pattern → `SAFE`,
  - no-pattern benign command (`git status`, `mvn test`) → `SAFE` (auto-allowed),
  - no-pattern destructive command (`rm -rf /`) → `DANGEROUS` (blocked/confirmation),
  - `trustProject: auto` in-scope `ask_once` behavior unchanged.
- `ShGuardHookEndToEndTest` (optional, slow): boot a minimal Quarkus test profile,
  invoke `ShellTool.execute("git status")` via an `Agent` stub and assert the
  hook path permits execution; invoke `ShellTool.execute("rm -rf /")` and assert
  `ToolDeniedException`/confirmation path engages.

### 6.3 Regression & quality gates

- `mvn test` — the **entire** existing suite (including `ShellCommandAnalyzerTest`,
  `HookManagerTest`, `HookManagerTrustProjectTest`, `ShellToolTest`) must pass
  unmodified.
- `mvn package` (Quarkus build) must pass; no new dependencies means no lockfile
  changes.
- Optional fuzz-lite: feed randomized quote/operator strings; assert the parser
  never throws and never returns `SAFE` on input containing `rm`, `sudo`,
  `$(`, `` ` ``, or `| bash`.

### 6.4 Acceptance criteria

1. All 100+ existing analyzer cases produce identical `DangerLevel`.
2. ≥ 90% of the evasion corpus (targeted obfuscation list) is classified
   `DANGEROUS` (the remainder must be `ASK_ONCE`, **never** `SAFE`).
3. Parser never throws on any input; parse failures yield `ASK_ONCE`.
4. `analyzeDetailed` reports at least one `ShViolation` with a precise reason
   for every `DANGEROUS` classification.
5. No behavioral change to `hooks.yaml` overrides or `trustProject` modes.

---

## 7. Implementation Plan (for the writer phase)

| Step | Deliverable |
|---|---|
| 1 | `ShToken`, `ShTokenType`, `ShLexer`, `ShLexException` + `ShLexerTest`. |
| 2 | AST: `ShNode` hierarchy, `ShWord`/`ShWordPart`, `ShAst` + `ShParser` + `ShParserTest`/`ShWordTest`. |
| 3 | `ShReason`, `ShViolation`, `ShGuardReport`, `ShGuardPolicy`, `ShGuard` semantic pass + `ShGuardTest` (ported cases). |
| 4 | `ShGuardEvasionTest` + `ShGuardPolicyTest`; iterate on classifier. |
| 5 | Rewire `ShellCommandAnalyzer` as facade (keep legacy under `ShellCommandAnalyzerLegacy`); update `HookManager` logging; optional config (`hooks.yaml` + `AgentConfig.ShGuardConfig`). |
| 6 | Integration tests; full `mvn test` + `mvn package`; README/docs update. |

---

## 8. Scope, Risks, and Out of Scope

**Out of scope (explicit):**
- Executing or validating commands against a real shell (`bash -n`), invoking
  `shellcheck`, or any external process — the guard is purely static.
- Full POSIX shell semantics (aliases, `eval`, `trap`, `export` propagation,
  `source`/`.` file contents, interactive features, `PROMPT_COMMAND`). Commands
  that *must* be dynamically resolved (`eval "$x"`, `source file`) are treated
  conservatively: `eval`/`source`/`.` with non-static arguments → `ASK_ONCE`;
  `eval` containing a statically-dangerous string → `DANGEROUS`.
- Runtime taint tracking of actual variable values from the environment.

**Known residual risks / mitigations:**
| Risk | Mitigation |
|---|---|
| New parser mis-parses a legit command → wrong `ASK_ONCE` prompt | Parser is lenient (partial AST + error flag); on error the guard returns `ASK_ONCE`, never `DANGEROUS` by mistake; error paths covered by tests. |
| Under-classification via clever obfuscation not covered by the corpus | All unresolvable/expanded command names default to `ASK_ONCE`; policy sets are centralized for fast additions; evasion corpus is extensible. |
| Behavior drift vs. legacy analyzer | Golden parity test ports the full legacy corpus verbatim. |
| Performance on huge inputs | O(n) algorithms, size cap (e.g. reject > 64 KB with `ASK_ONCE`) to bound worst case. |
| Regression risk to hook UX | `dangerous`/`safe` pattern overrides and `trustProject` logic are untouched and integration-tested. |

**Backward compatibility guarantees:** `DangerLevel` values, `ShellCommandAnalyzer.analyze`,
`hooks.yaml` schema (additive-only), and `HookManager` semantics are unchanged.

---

## 9. Summary of Changes

| Kind | File(s) |
|---|---|
| NEW | `shguard/ShToken.java`, `ShTokenType.java`, `ShLexer.java`, `ShLexException.java`, `ShParser.java`, `ShAst.java`, `ShNode.java` (+ ~14 node types), `ShWord.java` (+ part types), `ShGuard.java`, `ShGuardReport.java`, `ShViolation.java`, `ShReason.java`, `ShGuardPolicy.java`, `ShGuardConfig.java` |
| MOD | `hook/ShellCommandAnalyzer.java` (facade), `hook/HookManager.java` (logging + delegation), `config/AgentConfig.java` (optional `ShGuardConfig`), `src/main/resources/hooks.yaml` (optional `shGuard:` block), `README.md` |
| TEST (NEW) | `ShLexerTest`, `ShParserTest`, `ShWordTest`, `ShGuardTest`, `ShGuardEvasionTest`, `ShGuardPolicyTest`, `ShGuardReportTest`, `HookManagerShGuardIntegrationTest` |
| TEST (MOD) | `ShellCommandAnalyzerTest` (extend with parity note only) |
| BUILD | `pom.xml` — **no changes** |
| DB | **none** |
