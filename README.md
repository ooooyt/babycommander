# babycommander

An AI-powered coding assistant that runs in your terminal. babycommander combines a
multi-agent LLM orchestration engine with a rich terminal UI, letting you generate,
modify, refactor, debug, and document code projects through natural-language chat.

Built with **Java 21+**, **Quarkus**, and **LangChain4j**.

---

## Features

- **Interactive terminal chat UI** — JLine-based TUI with streaming responses,
  Markdown rendering (tables, task lists, strikethrough), syntax-aware output,
  history scrolling, and slash commands.
- **Multi-agent orchestration** — an `Orchestrator` detects the intent of your
  request and routes it to a mode-specific strategy:
  - `BUGFIX` — analyze failures and apply surgical fixes, then re-run tests
  - `REFACTOR` — structural changes with test verification
  - `EXTENSION` — add features to existing projects
  - `CREATE` — scaffold new projects (delegates to skill workflows)
  - `DOCUMENT` — generate documentation
  - Falls back to a single-agent conversational mode when intent is inconclusive.
- **Plan-first execution** — agents create a numbered plan (`createPlan`) before
  acting and report phase completion, keeping long tasks transparent and resumable.
- **Rich toolset for agents** — file read/write/range-read, line & unified-diff
  patching, directory listing, text search, skeleton extraction (ANTLR-based),
  function-body lookup, shell execution, internet fetch, user prompts, and more.
- **Skill workflows** — reusable multi-agent playbooks defined in `SKILL.md`
  files (Claude Code / OpenCode compatible format). Bundled skills:
  `create-large-project`, `refactor-large-codebase`, `migrate-project`,
  `add-complex-feature`.
- **Safety hooks** — configurable allow/ask/deny rules for tool calls
  (`hooks.yaml`): read-only shell commands run silently, build commands ask once,
  destructive commands (`rm -rf`, `sudo`, `DROP TABLE`, force-push, …) are blocked
  or require explicit confirmation.
- **Multiple LLM providers** — OpenAI-compatible, Anthropic, Ollama, DeepSeek,
  and Qwen endpoints, each configurable per agent role (router, orchestrator,
  planner, writer, fixer, …).
- **Persistent memory** — conversations, messages, tasks, tool executions, and
  summaries stored locally in an embedded **ObjectBox** database
  (`~/.babycommander/db/objectbox`), with automatic conversation compaction.
- **MCP support** — connect external Model Context Protocol servers via config.
- **i18n** — English and Simplified Chinese UI messages (`/lang` to switch).
- **REST status endpoint** — `GET /api/status` for monitoring task progress.

## Architecture

```
src/main/java/com/ooooyt/babycommander/
├── CodeGenApp / CodeGenLifecycle   # Quarkus entry point, TUI bootstrap
├── CodeGenResource                 # REST API (/api/status)
├── agent/                          # Agent factory, chat models, token usage,
│   └── memory/                     #   chat memory + summarization/compaction
├── config/                         # agents.yaml loading (AgentConfig)
├── db/                             # ObjectBox store, entities, repositories,
│   ├── entity/                     #   chat-memory persistence
│   └── repository/
├── editloop/                       # Iterative edit/verify loop
├── hook/                           # Tool-call safety rules (hooks.yaml)
├── intent/                         # Intent detection & complexity routing
├── orchestrator/                   # Orchestrator + per-mode strategies
│                                   #   (bugfix/refactor/extension/create/document),
│                                   #   skill workflow executor, test runner
├── service/                        # Project/task services, embeddings
├── skill/                          # SKILL.md loader, registry, workflow steps
├── status/                         # Status events (console + REST trackers)
├── tool/                           # FileSystemTool, ShellTool, InternetTool,
│   └── mcp/                        #   PlanTool, AskUserTool, MCP adapters
├── ui/                             # ChatEngine (event-bus driven)
│   ├── engine/                     #   session/command/request handlers
│   └── tui/                        #   JLine terminal UI, Markdown renderer,
│                                   #   slash commands, themes
├── util/                           # Skeleton extractor, patch utils, token
│                                   #   counter, i18n, project scanner
└── workflow/                       # Generic workflow engine & steps
```

Key flow: **TUI → ChatEngine → Orchestrator → mode strategy / skill workflow →
agents (LangChain4j) → tools → status events → TUI/REST**.

## Requirements

- **JDK 21+** (compiler release 22)
- **Maven 3.9+**
- API key(s) for at least one configured provider
- macOS, Linux, or Windows (ObjectBox native libraries are bundled for all three)

## Build & Run

```bash
# Build
mvn clean package

# Run in dev mode
mvn quarkus:dev

# Run the packaged app
java -jar target/quarkus-app/quarkus-run.jar
```

> The Quarkus build passes JVM args required by ObjectBox:
> `--add-opens=java.base/sun.misc=ALL-UNNAMED --enable-native-access=ALL-UNNAMED`.

## Configuration

### `agents.yaml` (classpath: `src/main/resources/agents.yaml`)

Central config: default model, workspace root, provider endpoints, per-role agent
prompts, MCP servers, and hook settings. Most values can be overridden with
environment variables:

| Variable | Purpose | Default |
|---|---|---|
| `OPEN_TOASTER_DEFAULT_MODEL` | Default provider key | `openai` |
| `OPEN_TOASTER_WORKSPACE_ROOT` | Workspace root directory | — |
| `OPEN_TOASTER_SKIP_CONFIRM_WORKSPACE` | Skip workspace confirmation | `false` |
| `OPENAI_API_KEY` | OpenAI/Qwen-compatible key | — |
| `ANTHROPIC_API_KEY` | Anthropic key | — |
| `DEEPSEEK_API_KEY` | DeepSeek key | — |
| `OLLAMA_KEY` | Ollama/NVIDIA endpoint key | — |
| `OPEN_TOASTER_<PROVIDER>_BASE_URL` / `_MODEL` / `_TEMPERATURE` / `_MAX_TOKENS` / `_TIMEOUT_SECONDS` | Per-provider overrides | see file |
| `OPEN_TOASTER_MAX_MESSAGES_IN_MEMORY`, `OPEN_TOASTER_MAX_TOOL_CALLS`, `OPEN_TOASTER_MAX_TOKENS_IN_MEMORY`, `OPEN_TOASTER_TOOL_OUTPUT_TRUNCATION_KB` | Memory/token limits | see file |

### `application.properties`

| Property | Purpose | Default |
|---|---|---|
| `db.directory` | ObjectBox data dir | `~/.babycommander/db/objectbox` |
| `db.enabled` | Enable persistence | `true` |
| `db.memory.max-messages` / `max-tool-calls` / `summary-batch-size` | Chat-memory limits | 200 / 80 / 20 |
| `summarizer.provider` / `model-name` / `temperature` | Conversation summarizer | `default` |
| `babycommander.data-dir` | App data root | `~/.babycommander` |

### `hooks.yaml` — tool safety levels

Each tool call is classified by regex pattern matching:

- **safe** — read-only commands (`ls`, `pwd`, `head`, `wc`, …) execute silently
- **ask_once** — build/dev commands (`mvn`, `npm`, `git`, `mkdir`, …) require one-time confirmation
- **dangerous** — destructive operations (`rm -rf`, `sudo`, `dd`, `shutdown`, `DROP TABLE`, …) are gated

Custom rules and patterns can be added under `rules:` and `patterns:`.

### Skills

Drop a `SKILL.md` file into `~/.babycommander/skills/<skill-name>/` (or the
configured `skillsDir`) to register a custom multi-agent workflow. See
`src/main/resources/skills/example/SKILL.md` for the format.

## Usage

Start the app and chat:

```
> add input validation to the user registration endpoint
```

The assistant plans the work, executes it phase by phase with tools, and reports
a summary. Slash commands available in the TUI:

| Command | Description |
|---|---|
| `/help` | Show help |
| `/lang` | Switch UI language (en / zh) |
| `/workspace` | Show or change workspace/project folder |
| `/history` | Browse conversation history |
| `/cnc` | Cancel current operation |
| `/exit`, `/quit` | Exit |

REST status (random port by default, see `quarkus.http.port`):

```bash
curl http://localhost:<port>/api/status
```

## Development

```bash
# Run unit tests
mvn test

# Skip integration tests by default (skipITs=true); enable with:
mvn verify -DskipITs=false
```

Notable test areas: orchestrator strategies, edit loop, intent/complexity routing,
skill loading, TUI rendering, hook rules, chat memory compaction.

### Generated sources

- **ANTLR4** grammars in `src/main/antlr4` → `target/generated-sources/antlr4`
  (used by the skeleton extractor)
- **ObjectBox** annotation processor → `target/generated-sources/annotations`
  (model file: `objectbox-models/default.json`)

## Project Layout

```
babycommander/
├── pom.xml                  # Quarkus + LangChain4j + ObjectBox + ANTLR build
├── src/main/java/…          # Application sources (see Architecture)
├── src/main/antlr4/         # Code-skeleton grammar
├── src/main/resources/
│   ├── agents.yaml          # Agent/provider configuration
│   ├── hooks.yaml           # Tool safety rules
│   ├── i18n/                # messages_en / messages_zh bundles
│   └── skills/              # Bundled SKILL.md workflows
├── src/test/java/…          # Unit tests
└── objectbox-models/        # ObjectBox data model
```
