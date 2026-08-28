# SH-GUARD — ANTLR-Based Implementation Notes

**Status:** Implemented
**Package:** `com.ooooyt.babycommander.shguard`
**Grammars:** `src/main/antlr4/com/ooooyt/babycommander/util/antlr/`

This document records how the SH-GUARD feature was actually implemented, which
differs from the original design in `feature-design.md` in one important way:
the parser is **ANTLR-based** (as requested) rather than a hand-written lexer +
recursive-descent parser.

## Why ANTLR

The repo already ships ANTLR 4 (`antlr4-runtime` dependency, the
`antlr4-maven-plugin`, the Gradle `antlr` plugin, and a working
`CodeSkeletonLexer.g4`). Reusing the toolchain means:

- No new runtime dependencies.
- A declarative grammar that is easier to review and extend.
- The parse tree *is* the AST — no separate node hierarchy to maintain.

## Components

| File | Role |
|---|---|
| `util/antlr/ShellCommandLexer.g4` | Lexer: words, quoting (`'…'`, `"…"`, `$'…'`), escapes, expansions (`$VAR`, `${VAR}`, `$(…)`, `` `…` ``, `$((…))`), operators (longest-match), IO numbers, unterminated-quote error tokens. |
| `util/antlr/ShellCommandParser.g4` | Parser: `program → list → and_or → pipeline → command`, simple commands (prefix assignments/redirects + words), compound commands (subshell, brace group, if/while/until/for/case), function defs, redirects. |
| `shguard/ShGuard.java` | Public facade (`analyze`, `analyzeDetailed`, `withPolicy`) + the semantic pass (`Analyzer` inner class extending `ShellCommandParserBaseVisitor`). |
| `shguard/ShWord.java` | Dequoting + expansion-part analysis of a logical shell word (the obfuscation-resistance core). |
| `shguard/ShGuardPolicy.java` | Immutable policy: dangerous/safe command sets, system-path prefixes, write-redirect allowlist, toggles (`flagSudoAlways`, `foldAssignments`, `strictUnknownCommand`, `blockWriteRedirectToRoot`). Defaults identical to the legacy `ShellCommandAnalyzer` sets. |
| `shguard/ShGuardReport.java` | `record(level, violations, parseError, parsed)`. |
| `shguard/ShViolation.java` | `record(reason, detail, subCommand, start, end)`. |
| `shguard/ShReason.java` | Enum of classification reasons. |
| `hook/ShellCommandAnalyzer.java` | Deprecated facade → delegates to `ShGuard.analyze` (rollback via `BABY_COMMANDER_SHGUARD_DISABLED=true`). |
| `hook/ShellCommandAnalyzerLegacy.java` | Original token-based implementation, preserved for rollback. |
| `hook/HookManager.java` | `resolveLevel` now logs the `ShGuardReport` at DEBUG. |

## Grammar location note

All grammars live in **one package directory**
(`com/ooooyt/babycommander/util/antlr`) so that:

- Maven's `antlr4-maven-plugin` infers the same package for every grammar
  (package = directory structure).
- Gradle's built-in `antlr` plugin passes a single `-package` argument (it
  applies to all grammars in one invocation).

The only build changes required were `<visitor>true</visitor>` in `pom.xml` and
`arguments += ['-visitor']` in `build.gradle` (the semantic pass uses the
generated `ShellCommandParserBaseVisitor`).

## Semantic analysis (the sh-guard pass)

The `Analyzer` visitor walks the parse tree and, for every simple command:

1. Collects logical words (adjacent fragments merged by source position —
   `r"m"` → `rm`).
2. Checks `sudo` escalation (policy toggle).
3. Resolves the command name: fully static words dequote directly; words with
   expansions use constant folding (`CMD=rm; $CMD -rf /`) or a static-skeleton
   fallback (`rm$()` → `rm`).
4. Applies the policy rules: dangerous commands, dangerous git ops, dangerous
   package ops, dangerous flags, DB destruction, write-redirects to system
   paths (with `/dev/null` allowlist).
5. Recursively analyzes command substitutions (`$(rm -rf /)`, backticks).
6. Pipeline analysis detects remote code execution (`curl … | bash`,
   `wget … | sh -s`, `curl … | sudo bash`).
7. Function definitions are checked for structural fork bombs
   (`:(){ :|:& };:` — a pipeline invoking the function's own name).

Aggregation: any `DANGEROUS` wins; else `SAFE` if every node is safe; else
`ASK_ONCE`. Parse failures and unterminated quotes are conservative
(`ASK_ONCE`, never `SAFE`).

## Tests

- `shguard/ShGuardTest` — full port of the legacy `ShellCommandAnalyzerTest`
  corpus (all 78 cases produce identical results) + report/parse-error checks.
- `shguard/ShGuardEvasionTest` — quoted/escaped names, command substitution,
  variable indirection, RCE variants, fork bombs, heredocs, "never SAFE on
  dangerous content".
- `shguard/ShGuardPolicyTest` — policy overrides (additive sets, allowlist,
  `strictUnknownCommand`, `foldAssignments`, `flagSudoAlways`).
- `shguard/ShWordTest` — dequoting and static-analysis unit tests.

All 1115 tests in the project pass (`mvn test`).