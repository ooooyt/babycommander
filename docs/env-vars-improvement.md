# Environment Variable Improvements — Research & Design

Status: **Approved for implementation (P0 + P1)**
Date: 2025 (latest revision)

## 1. How env vars work today

- `YamlConfigLoader` reads the **raw YAML text** and regex-substitutes every
  `${VAR:default}` token (`resolveString`), then parses the result into `AgentConfig`.
- Lookup order: **system property → env var → inline default** (`lookup()`).
- A second pass (`resolveEnvVars`) re-resolves `apiKey`/`baseUrl`/`systemPrompt`/`trustProject`
  (edge cases).
- Config is loaded **once at startup** and cached.

## 2. Problems found

| # | Issue | Impact |
|---|-------|--------|
| 1 | **Inconsistent naming** — `opencode` uses `BABY_COMMANDER_OC_*` while others use full names; `ocdeepseek` shares `BABY_COMMANDER_DEEPSEEK_*` (baseUrl *and* model) with the `deepseek` provider | Cannot configure `ocdeepseek` independently; confusing |
| 2 | `qwen` uses `OPENAI_API_KEY`; `OLLAMA_KEY` vs `*_API_KEY` pattern mismatch | Surprising, undocumented |
| 3 | **Empty-string env var overrides default** — `VAR=""` yields `""`, not the fallback | Silent misconfiguration |
| 4 | **No `.env` file support** — must `export` in shell | Annoying for local dev |
| 5 | **No startup validation** — missing API key for the default provider fails later with a confusing auth error | Bad DX |
| 6 | **Secrets not guaranteed redacted** in logs/debug | Risk |
| 7 | **Regex substitution is fragile** — `\$\{([^}]+)\}` breaks on `}` inside defaults, no `${VAR:-default}` semantics, no escaping | Edge-case bugs |
| 8 | **No single source of truth** — vars scattered across YAML + README; README already stale (`BABY_COMMANDER_DEFAULT_MODEL` default says `openai`, YAML says `ocdeepseek`) | Docs drift |
| 9 | **No per-role provider override, no reload** | Restart required; roles hardcode providers |

## 3. Proposed solution

### A. Standardized `BCMD_` naming (backward compatible)

- Uniform pattern: `BCMD_<PROVIDER>_<FIELD>` where `<PROVIDER>` = exact key in `agents.yaml`.
- `opencode` → `BCMD_OPENCODE_*` (keep `OC_*` as deprecated alias + startup warning).
- **Decouple `ocdeepseek`** → own `BCMD_OCDEEPSEEK_BASE_URL/_MODEL/_TEMPERATURE/_MAX_TOKENS/_TIMEOUT_SECONDS`.
- API keys: `BCMD_<PROVIDER>_API_KEY` primary, fall back to conventional names
  (`OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `DEEPSEEK_API_KEY`, `OLLAMA_KEY`).
- Optional per-provider `BCMD_<PROVIDER>_MAX_RETRIES` (global `BCMD_MAX_RETRIES` stays as fallback).
- Deprecated aliases: old `BABY_COMMANDER_*` and `OC_*` names kept with a one-time
  startup warning, removed after a transition period.

### B. Typed binding in Java (replaces raw-string regex)

- YAML keeps **plain default values, no `${...}` at all** — it remains the human-readable
  source of defaults.
- After parse, `YamlConfigLoader` applies env overrides per field via a single `EnvKeys`
  class: typed (int/double/boolean), `${VAR:-default}` empty→default semantics, per-field
  policies (apiKey never logged, `~` expansion for paths, range validation), deprecated
  alias fallback with warnings.
- Single source of truth for keys + defaults + docs; unit-testable.

### C. `.env` file support (leak-safe)

- **Only `~/.babycommander/.env`** (home dir, outside any git repo). **Never** auto-load
  `<project>/.env`.
- Enforce **0600 permissions**; warn at startup if too open.
- **Startup warning** if a `.env` is detected inside the project directory → tell the user
  to move it to `~/.babycommander/`.
- Add `.env` to `.gitignore` as belt-and-suspenders.
- Precedence (low→high): YAML default → `~/.babycommander/.env` → process env → system property.
- README documents: *never commit `.env`; secrets live in `~/.babycommander/.env` only.*

### D. Startup validation & diagnostics

- Validate default provider + all providers: non-empty `apiKey` (unless local Ollama),
  warn clearly otherwise.
- Log effective config with `apiKey` redacted (`sk-***`).
- Add a `/config` slash command showing effective providers/roles (redacted).

### E. Docs

- Update README table from `EnvKeys` (fixes the stale `defaultModel` default).

### F. Optional (deferred)

- `BCMD_ROLE_<ROLE>_PROVIDER` to override role→provider mapping.
- `config:reload` slash command to re-read config without restart.

## 4. Confirmed decisions

1. Prefix: **`BCMD_`** (user approved over `BABY_COMMANDER_` and `BC_`).
2. Defaults move to Java (`EnvKeys`); YAML keeps plain defaults (user accepted the
   typed-binding rationale: type safety, empty-string semantics, single source of truth,
   no fragile regex, per-field policies, clean alias handling).
3. Empty-string env var → **use default** (`${VAR:-default}` semantics).
4. Old names (`BABY_COMMANDER_*`, `OC_*`) kept as **deprecated aliases with warnings**.
5. Scope: **P0 + P1** — naming + typed resolution + validation/redaction (P0),
   `~/.babycommander/.env` + README (P1).

## 5. Implementation plan

1. Add `EnvKeys` constants + typed resolver (empty→default, aliases, validation, redaction).
2. Rewire `YamlConfigLoader` to parse plain YAML then apply env overrides per field.
3. Load `~/.babycommander/.env` (0600 check, no project `.env`, repo `.env` warning).
4. Add `/config` slash command (redacted).
5. Update README env table; add unit tests; run `mvn test`; commit.