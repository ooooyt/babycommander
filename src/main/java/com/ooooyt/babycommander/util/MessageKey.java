package com.ooooyt.babycommander.util;

/**
 * Enumeration of all i18n message keys used in the application.
 * Provides type-safe reference to resource bundle keys with embedded descriptions
 * so developers can understand what each key represents without opening .properties files.
 */
public enum MessageKey {

    // --- Chat Mode ---
    CHAT_MODE_PROMPT("chat.mode.prompt", "[{0}]:"),
    CHAT_MODE_PROMPT_CHAT("chat.mode.prompt.chat", "\uD83E\uDD6A\uD83E\uDD6A:"),
    CHAT_GOODBYE("chat.goodbye", "\\nGoodbye!"),
    CHAT_ERROR_TOOL_DENIED("chat.error.tool_denied", "Tool denied: {0}"),
    CHAT_ERROR_GENERIC("chat.error.generic", "Error: {0}"),

    // --- Workflow ---
    CHAT_WORKFLOW_STARTED("chat.workflow.started", "\\n--- Mode: {0} ---"),
    CHAT_WORKFLOW_TASK("chat.workflow.task", "Task: {0}"),
    CHAT_WORKFLOW_PROCEED("chat.workflow.proceed", "Proceed? [(y)es/(N)o]:"),
    CHAT_WORKFLOW_RUNNING("chat.workflow.running", "Running {0} workflow..."),
    CHAT_WORKFLOW_FAILED("chat.workflow.failed", "Workflow execution failed: {0}"),
    CHAT_WORKFLOW_SKIPPED("chat.workflow.skipped", "Skipping."),
    CHAT_WORKFLOW_ENDED("chat.workflow.ended", "--- End ---"),

    // --- Workspace ---
    CHAT_WORKSPACE_CURRENT("chat.workspace.current", "Current workspace: {0}"),
    CHAT_WORKSPACE_CURRENT_PROJECT("chat.workspace.current_project", "Current project:  {0}"),
    CHAT_WORKSPACE_USAGE("chat.workspace.usage", "Usage: /workspace <path>"),
    CHAT_WORKSPACE_EXAMPLE("chat.workspace.example", "Example: /workspace /home/user/my-projects"),
    CHAT_WORKSPACE_ERROR_NOT_EXIST("chat.workspace.error.not_exist", "Error: Path does not exist: {0}"),
    CHAT_WORKSPACE_ERROR_NOT_DIR("chat.workspace.error.not_dir", "Error: Not a directory: {0}"),
    CHAT_WORKSPACE_NEW("chat.workspace.new", "New workspace:    {0}"),
    CHAT_WORKSPACE_CONFIRM("chat.workspace.confirm", "Change workspace? (project folder will stay as ''{0}'') [(y)es/(N)o]:"),
    CHAT_WORKSPACE_CHANGED("chat.workspace.changed", "Workspace changed to: {0}"),
    CHAT_WORKSPACE_PROJECT_UNCHANGED("chat.workspace.project_unchanged", "Project folder unchanged: {0}"),
    CHAT_WORKSPACE_UNCHANGED("chat.workspace.unchanged", "Workspace unchanged."),

    // --- Help ---
    CHAT_HELP_HEADER("chat.help.header", "\\n  Available slash commands:"),
    CHAT_HELP_FOOTER("chat.help.footer", "\\n  Or just describe your task naturally and the AI will handle it."),

    // --- Commands ---
    CHAT_COMMAND_UNKNOWN("chat.command.unknown", "Unknown command: {0}"),
    CHAT_COMMAND_USAGE("chat.command.usage", "Usage: {0} <task description>"),
    CHAT_COMMAND_EXAMPLE("chat.command.example", "Example: {0} Fix the NullPointerException in UserService.java"),

    // --- Language Switching ---
    CHAT_LANG_CURRENT("chat.lang.current", "Current output language: {0}"),
    CHAT_LANG_USAGE("chat.lang.usage", "Usage: /lang <locale>"),
    CHAT_LANG_EXAMPLE("chat.lang.example", "Example: /lang zh-CN, /lang en, /lang zh"),
    CHAT_LANG_CHANGED("chat.lang.changed", "Output language changed to: {0}"),
    CHAT_LANG_INVALID("chat.lang.invalid", "Invalid locale: {0}. Using English fallback."),

    // --- New Project ---
    CHAT_NEW_PROJECT("chat.new_project", "\\n--- New Project ---"),
    CHAT_NEW_PROJECT_CREATING("chat.new_project.creating", "Creating project in: {0}"),

    // --- Existing Project ---
    CHAT_EXISTING_PROJECT("chat.existing_project", "\\n--- Existing Project Detected ---"),
    CHAT_EXISTING_PROJECT_FOUND("chat.existing_project.found", "Found existing project: {0} ({1})"),
    CHAT_EXISTING_PROJECT_LOCATION("chat.existing_project.location", "Location: {0}"),
    CHAT_EXISTING_PROJECT_CHOICE("chat.existing_project.choice", "[(e)xisting project/(n)ew folder]:"),
    CHAT_EXISTING_PROJECT_CREATING_IN("chat.existing_project.creating_in", "Creating in: {0}"),

    // --- Code Generation ---
    CHAT_GENERATION_STARTED("chat.generation.started", "\\n--- Code Generation Request ---"),
    CHAT_GENERATION_TASK("chat.generation.task", "Task: {0}"),
    CHAT_GENERATION_PROCEED("chat.generation.proceed", "Proceed? [(y)es/(N)o]:"),
    CHAT_GENERATION_RUNNING("chat.generation.running", "Generating code..."),
    CHAT_GENERATION_COMPLETE("chat.generation.complete", "Code generation completed: {0}"),
    CHAT_GENERATION_FAILED("chat.generation.failed", "Code generation failed: {0}"),
    CHAT_GENERATION_SKIPPED("chat.generation.skipped", "Skipping."),
    CHAT_GENERATION_ENDED("chat.generation.ended", "--- End ---"),

    // --- Document ---
    CHAT_DOCUMENT_STARTED("chat.document.started", "\\n--- Document Request ---"),
    CHAT_DOCUMENT_TASK("chat.document.task", "Task: {0}"),
    CHAT_DOCUMENT_PROCEED("chat.document.proceed", "I'll write this document. Proceed? [y/N]:"),
    CHAT_DOCUMENT_RUNNING("chat.document.running", "Writing document..."),
    CHAT_DOCUMENT_WRITTEN("chat.document.written", "Document written:"),
    CHAT_DOCUMENT_SUCCESS("chat.document.success", "Document written successfully."),
    CHAT_DOCUMENT_FAILED("chat.document.failed", "Document writing failed: {0}"),
    CHAT_DOCUMENT_SKIPPED("chat.document.skipped", "Skipping."),
    CHAT_DOCUMENT_ENDED("chat.document.ended", "--- End ---"),

    // --- CLI ---
    CLI_WELCOME("cli.welcome", "Welcome to Baby Commander!"),
    CLI_EXIT("cli.exit", "Exiting..."),
    CLI_PROMPT("cli.prompt", "> "),
    CLI_EXAMPLES_HEADER("cli.examples.header", "\\n  Examples:"),
    CLI_STARTING("cli.starting", "Starting multi-agent code generation..."),
    CLI_TASK("cli.task", "Task: {0}"),

    // --- Console Status Renderer ---
    STATUS_WORKFLOW_STARTED("status.workflow.started", "\u2550\u2550\u2550 Workflow Started \u2550\u2550\u2550"),
    STATUS_WORKFLOW_TASK("status.workflow.task", "Task: {0}"),
    STATUS_WORKFLOW_TYPE("status.workflow.type", "Type: {0}"),
    STATUS_MODE("status.mode", "Mode: {0}"),
    STATUS_STEP_STARTED("status.step.started", "\u25b6 {0}"),
    STATUS_TOOL_CALL("status.tool.call", "\u23f3 {0} \u2192 {1}"),
    STATUS_TOOL_RESULT("status.tool.result", "\u2713 {0} \u2192 {1}"),
    STATUS_TOOL_ERROR("status.tool.error", "\u2717 {0} \u2192 {1}"),
    STATUS_TOOL_RESPONSE("status.tool.response", "\u2514 Agent response: {0}"),
    STATUS_TOOL_TEXT("status.tool.text", "  {0}"),
    STATUS_TOOL_TEXT_EMPTY("status.tool.text.empty", "I need to execute tools for analyzing."),
    STATUS_STEP_COMPLETE("status.step.complete", "\u2713 {0} Complete"),
    STATUS_STEP_FAILED("status.step.failed", "\u2717 {0} Failed"),
    STATUS_WORKFLOW_COMPLETED("status.workflow.completed", "\u2550\u2550\u2550 Workflow Completed: {0} ({1}) \u2550\u2550\u2550"),
    STATUS_THINKING("status.thinking", "  \u23F1  thinking..."),
    // --- TUI status lines ---
    TUI_STEP_COMPLETE("tui.step.complete", "\u2713 {0} Complete ({1}ms)"),
    TUI_STEP_FAILED("tui.step.failed", "\u2717 {0} Failed \u2014 {1}"),
    TUI_WORKFLOW_STARTED("tui.workflow.started", "\u2550\u2550\u2550 Workflow {0} \u2550\u2550\u2550"),
    TUI_WORKFLOW_STARTED_LABEL("tui.workflow.started_label", "Started"),
    TUI_WORKFLOW_COMPLETED("tui.workflow.completed", "\u2550\u2550\u2550 Workflow {0} ({1}ms) \u2550\u2550\u2550"),
    TUI_AGENT_RESPONSE("tui.agent.response", "\u2514 Agent response: {0} chars"),
    TUI_WORKFLOW_MODE("tui.workflow.mode", "\u2500\u2500\u2500 Mode: {0} \u2500\u2500\u2500"),
    TUI_TITLE("tui.title", " \u2ffb Open Toaster"),
    TUI_TITLE_LOADING("tui.title.loading", " \u2ffb Open Toaster (Loading...)"),
    TUI_INPUT_PLACEHOLDER("tui.input.placeholder", "Type anything  [\u2191\u2193] scroll  [PgUp/PgDn] page  [/quit or /exit] quit  [/help] help"),
    UI_WARN_PREFIX("ui.warn.prefix", "[Warn] "),
    STATUS_TOOL_CALLS("status.tool.calls", " tool calls"),
    // --- Confirmation Handler ---
    CONFIRMATION_ALLOW("confirmation.allow", " Allow? [y/n/a]:"),
    CONFIRMATION_ALLOW_SIMPLE("confirmation.allow_simple", " Allow? [y/n]:"),

    // --- Workspace Detection ---
    WORKSPACE_INVALID_PATH("workspace.invalid_path", "Invalid path. Keeping current workspace: {0}"),

    // --- Plan Tool ---
    PLAN_CREATED("plan.created", "Plan created with {0} phases. Phase 1 is now active."),
    PLAN_ERROR_NOT_EXIST("plan.error.not_exist", "Error: phase {0} does not exist. Current plan has {1} phases."),
    PLAN_ERROR_NOT_ACTIVE("plan.error.not_active", "Error: phase {0} is not active (current status: {1}). Only the active phase can be completed."),
    PLAN_PHASE_COMPLETED("plan.phase.completed", "Phase {0} completed. Phase {1} is now active."),
    PLAN_ALL_COMPLETED("plan.all_completed", "Phase {0} completed. All phases done!"),
    PLAN_ERROR_NOT_EXIST_SIMPLE("plan.error.not_exist_simple", "Error: phase {0} does not exist."),
    PLAN_PHASE_FAILED("plan.phase.failed", "Phase {0} marked as failed."),

    // --- File System Tool ---
    FS_WRITE_SUCCESS("fs.write.success", "Successfully wrote to: {0} ({1} bytes)"),
    FS_WRITE_ERROR("fs.write.error", "Error writing file {0}: {1}"),
    FS_READ_ERROR("fs.read.error", "Error reading file {0}: {1}"),
    FS_READ_RANGE_ERROR("fs.read.range.error", "Error reading file range {0}: {1}"),
    FS_LIST_DIR_ERROR("fs.list.dir.error", "Error listing directory {0}: {1}"),
    FS_DELETE_PARTIAL("fs.delete.partial", "Partially deleted: {0}"),
    FS_DELETE_SUCCESS("fs.delete.success", "Successfully deleted: {0}"),
    FS_DELETE_ERROR("fs.delete.error", "Error deleting {0}: {1}"),
    FS_SEARCH_NO_MATCHES("fs.search.no_matches", "No matches found for pattern: {0}"),
    FS_SEARCH_ERROR("fs.search.error", "Error searching files: {0}"),
    FS_SKELETON_ERROR("fs.skeleton.error", "Error extracting skeleton from {0}: {1}"),
    FS_FUNCTION_READ_ERROR("fs.function.read.error", "Error reading function from {0}: {1}"),
    FS_SCAN_NO_SOURCES("fs.scan.no_sources", "No source files found in: {0}"),
    FS_SCAN_ERROR("fs.scan.error", "Error scanning package {0}: {1}"),
    FS_PATCH_ERROR("fs.patch.error", "Error applying patch to {0}: {1}"),
    FS_DIFF_ERROR("fs.diff.error", "Error applying diff to {0}: {1}"),
    FS_PATH_ESCAPE("fs.path.escape", "Path escapes project folder: {0}"),
    // --- File System Tool (additional) ---
    FS_FILE_NOT_FOUND("fs.file.not_found", "Error: File not found: {0}"),
    FS_PATH_NOT_FOUND("fs.path.not_found", "Error: Path not found: {0}"),
    FS_DIR_NOT_FOUND("fs.dir.not_found", "Error: Directory not found: {0}"),
    FS_NOT_A_DIR("fs.not.a.dir", "Error: Not a directory: {0}"),
    FS_READ_RANGE_EXCEEDS("fs.read.range.exceeds", "Error: startLine {0} exceeds file length ({1} lines)"),
    FS_ERROR_GENERIC("fs.error.generic", "Error: {0}"),
    FS_EMPTY_DIR("fs.empty.dir", "(empty directory)"),
    FS_TRUNCATED_WARNING("fs.truncated.warning", "... (truncated to {0} chars)"),
    FS_TRUNCATED_FILE("fs.truncated.file", "(file truncated to {0} chars)"),
    FS_FUNCTION_HEADER("fs.function.header", "Language: {0}\n---\n{1}"),
    FS_LANGUAGE_LABEL("fs.language.label", "Language: {0}"),
    FS_FILE_LABEL("fs.file.label", "File: {0}"),
    FS_SEPARATOR("fs.separator", "---"),
    FS_SOURCE_FILES_FOUND("fs.source.files.found", "Source files found: {0}"),
    FS_PACKAGE_LABEL("fs.package.label", "Package: {0}"),
    FS_DIR_STRUCTURE("fs.dir.structure", "--- Directory Structure ---"),
    FS_FILE_HEADER("fs.file.header", "--- {0} ---"),
    FS_MORE_FILES("fs.more.files", "... and {0} more file(s)"),
    FS_PACKAGE_SKELETON_ERROR("fs.package.skeleton.error", "Error extracting skeleton: {0}"),
    FS_SEARCH_WARN_READ("fs.search.warn.read", "Warning: Could not read {0}: {1}"),
    FS_SEARCH_WARN_READ_MULTI("fs.search.warn.read.multi", "Warning: Could not read {0} files: {1}"),

    // --- File System Tool (binary) ---
    FS_BINARY_READ_SUCCESS("fs.binary.read.success", "Binary read of {0}: {1} bytes (offset {2}, total file size {3}). Base64:\n{4}"),
    FS_BINARY_READ_EMPTY("fs.binary.read.empty", "Binary read of {0}: 0 bytes at offset {1} (file size {2}). Nothing to return."),
    FS_BINARY_WRITE_SUCCESS("fs.binary.write.success", "Successfully wrote {0} binary bytes to {1} at offset {2} (new file size {3})"),
    FS_BINARY_WRITE_ERROR("fs.binary.write.error", "Error writing binary file {0}: {1}"),
    FS_BINARY_APPEND_SUCCESS("fs.binary.append.success", "Successfully appended {0} binary bytes to {1} (new file size {2})"),
    FS_BINARY_APPEND_ERROR("fs.binary.append.error", "Error appending binary file {0}: {1}"),
    FS_BINARY_INVALID_BASE64("fs.binary.invalid.base64", "Error: invalid Base64 data for {0}: {1}"),
    FS_BINARY_INVALID_OFFSET("fs.binary.invalid.offset", "Error: invalid offset {0} for {1} (must be >= 0)"),

    // --- Internet Tool ---
    NET_HTTP_ERROR("net.http.error", "Error: HTTP {0} for {1}"),
    NET_FETCH_EMPTY("net.fetch.empty", "Page fetched successfully but body text is empty (content may be loaded dynamically). Status: {0}"),
    NET_FETCH_ERROR("net.fetch.error", "Error fetching URL {0}: {1}"),
    NET_SEARCH_HEADER("net.search.header", "Search results for: {0}"),
    NET_SEARCH_NO_RESULTS("net.search.no_results", "No search results found for: {0}"),
    NET_SEARCH_ERROR("net.search.error", "Error searching: {0}"),
    NET_SEARCH_HTTP_ERROR("net.search.http_error", "Error: Search failed with HTTP {0}"),

    // --- Shell Tool ---
    SHELL_ERROR("shell.error", "Error: {0}"),
    SHELL_TIMEOUT("shell.timeout", " (timeout: {0}s)"),
    SHELL_DIR("shell.dir", " [dir: {0}]"),

    // --- Chat Engine ---
    CHAT_HELP_LINES("chat.help.lines", "Available commands:"),
    CHAT_HELP_LANG("chat.help.lang", "    /lang <locale>     Change the output language"),
    CHAT_HELP_HELP("chat.help.help", "    /help              Show this help message"),
    CHAT_HELP_EXIT("chat.help.exit", "    /exit              Exit the application"),
    CHAT_HELP_QUIT("chat.help.quit", "    /quit              Exit the application"),
    CHAT_HELP_HISTORY("chat.help.history", "    /history [<param>]  Show tasks (<number> = latest N, -<number> = earliest N, <text> = search by keyword)"),
    CHAT_HELP_CNC("chat.help.cnc", "    /cnc               Commit necessary changes"),
    CHAT_HISTORY_NO_PROJECT_FOLDER("chat.history.no_project_folder", "No project folder is set. Use /workspace to set a project first."),
    CHAT_HISTORY_NO_PROJECT("chat.history.no_project", "No project found in database for path: {0}"),
    CHAT_HISTORY_NO_TASKS("chat.history.no_tasks", "No tasks found for this project."),
    CHAT_HISTORY_HEADER("chat.history.header", "\n--- Tasks for project: {0} ---"),
    CHAT_HISTORY_FOOTER("chat.history.footer", "--- Total: {0} task(s) ---\n"),
    CHAT_HISTORY_INVALID_LIMIT("chat.history.invalid_limit", "Invalid limit value: {0}. Please provide a positive number."),
    CHAT_HISTORY_INVALID_PARAM("chat.history.invalid_param", "Invalid parameter: {0}. Use a positive number, negative number, or a search keyword."),
    CHAT_HISTORY_SEARCH_NO_RESULTS("chat.history.search.no_results", "No tasks found matching: {0}"),
    CHAT_HISTORY_SEARCH_HEADER("chat.history.search.header", "\n--- Tasks for project: {0} matching \"{1}\" ---"),
    CHAT_ERROR_INTERNAL("chat.error.internal", "Internal error"),
    CHAT_ERROR_UNKNOWN("chat.error.unknown", "Unknown error"),
    CHAT_GENERAL_OPERATION("chat.general.operation", "General operation"),
    CHAT_DETECT_NEW_PROJECT("chat.detect.new_project", "Classify this request. If it asks to create a NEW project from scratch, respond 'YES|<name>' where <name> is a short folder name (kebab-case). If NOT a new project, respond 'NO'."),
    CHAT_SESSION_PREFIX("chat.session.prefix", "chat-"),

    // --- Terminal UI ---
    UI_ERROR_PREFIX("ui.error.prefix", "Error: "),
    UI_GOODBYE("ui.goodbye", "Goodbye!"),
    UI_LABEL_TASK("ui.label.task", "Task"),
    UI_LABEL_PLAN("ui.label.plan", "Plan"),
    UI_INITIALIZING("ui.initializing", "[Initializing...] System is still starting up. Please wait."),
    UI_CONFIRM_HINT("ui.confirm.hint", "  (y/n{0})"),
    UI_INVALID_CHOICE("ui.invalid_choice", "Invalid choice {0}. Please enter a number between 1 and {1}."),
    UI_PLEASE_ENTER_NUMBER("ui.please_enter_number", "Please enter a number corresponding to one of the options."),

    // --- Orchestrator ---
    ORCH_EMPTY_RESPONSE("orch.empty.response", "Empty response from agent"),
    ORCH_AGENT_EMPTY_RESPONSE("orch.agent.empty.response", "Agent returned empty response"),
    ORCH_EXECUTION_ERROR("orch.execution.error", "Execution error: {0}"),
    ORCH_UNEXPECTED_ERROR("orch.unexpected.error", "Unexpected error - operation aborted after retries exhausted"),
    ORCH_TESTS_PASSING("orch.tests.passing", "All tests already passing:"),
    ORCH_BUGFIX_SUCCESS("orch.bugfix.success", "Bug fixed successfully after {0} attempt(s)."),
    ORCH_BUGFIX_FAILED("orch.bugfix.failed", "Bug fix failed after {0} attempts."),
    ORCH_REFACTOR_SUCCESS("orch.refactor.success", "Refactoring completed successfully."),
    ORCH_REFACTOR_REPAIRED("orch.refactor.repaired", "Refactoring completed (with {0} repair attempt(s))."),
    ORCH_REFACTOR_FAILED("orch.refactor.failed", "Refactoring broke tests and repair failed after {0} attempts."),
    ORCH_EXTENSION_SUCCESS("orch.extension.success", "Feature extension completed successfully."),
    ORCH_EXTENSION_REPAIRED("orch.extension.repaired", "Feature extension completed (with {0} repair attempt(s))."),
    ORCH_EXTENSION_FAILED("orch.extension.failed", "Extension broke tests and repair failed after {0} attempts."),
    ORCH_REFACTOR_FAILED_MSG("orch.refactor.failed_msg", "Refactoring failed: {0}"),
    ORCH_EXTENSION_FAILED_MSG("orch.extension.failed_msg", "Extension failed: {0}"),
    ORCH_DOCUMENT_FAILED_MSG("orch.document.failed_msg", "Document writing failed: {0}"),
    ORCH_MULTI_AGENT_DESIGN("orch.multi_agent.design", "Create a comprehensive design document for the following task:"),
    ORCH_MULTI_AGENT_READ_DESIGN("orch.multi_agent.read_design", "Read the design document at {0} and implement all source files, test files, and configuration files."),
    ORCH_MULTI_AGENT_REVIEW("orch.multi_agent.review", "Review the test plan in the design document at {0}. Execute all tests and report results."),
    ORCH_REFACTOR_PROMPT("orch.refactor.prompt", "Perform the following refactoring:"),
    ORCH_EXTENSION_PROMPT("orch.extension.prompt", "Implement the following feature extension:"),

    // --- Workflow Engine ---
    WF_STEPS_NULL("wf.steps.null", "Steps cannot be null"),
    WF_SKIPPED("wf.skipped", "SKIPPED (previous step failed)"),
    WF_COMPLETED("wf.completed", "--- Workflow Completed ---"),
    WF_FAILED_AT("wf.failed_at", "--- Workflow Failed at Step {0} ---"),
    WF_STEPS_EXECUTED("wf.steps.executed", "Steps executed: {0}"),
    WF_STEPS_COMPLETED("wf.steps.completed", "Steps completed: {0}/{1}"),
    WF_WORKSPACE_UNKNOWN("wf.workspace.unknown", "unknown"),

    // --- Step Result ---
    STEP_SKIPPED_PREFIX("step.skipped.prefix", "SKIPPED: {0}"),

    // --- Tool Denied ---
    TOOL_DENIED_MSG("tool.denied.msg", "Tool call denied by user: {0}"),

    // --- Agent Factory ---
    AGENT_SESSION_EXISTS("agent.session.exists", "Session already exists: {0}"),
    AGENT_ROLE_NOT_CONFIGURED("agent.role.not_configured", "Agent role not configured: {0}"),
    AGENT_PROMPT_NOT_CONFIGURED("agent.prompt.not_configured", "System prompt not configured for role: {0}"),
    AGENT_PROVIDER_NOT_CONFIGURED("agent.provider.not_configured", "Provider not configured: {0}"),
    AGENT_TOOL_NOT_FOUND("agent.tool.not_found", "Tool not found: {0}"),
    AGENT_CUSTOM_PROMPT_NULL("agent.custom_prompt.null", "Custom prompt must not be null or blank"),
    AGENT_DEFAULT_PROVIDER_NOT_CONFIGURED("agent.default_provider.not_configured", "Default provider not configured: {0}"),
    AGENT_NO_ACTIVE_SESSION("agent.no_active.session", "No active agent with session: {0}"),
    AGENT_UNSUPPORTED_PROVIDER("agent.unsupported.provider", "Unsupported provider: {0}"),

    // --- Summary Message ---
    SUMMARY_MESSAGE_TO_STRING("summary.message.to_string", "SummaryMessage '{ text = \"{0}\" }'"),

    // --- Boot Log ---
    BOOT_LOG_PREFIX("boot.log.prefix", "[boot] "),
    BOOT_LOG_FILE_FAILED("boot.log.file_failed", "[boot] log to file failed: {0}"),

    // --- CodeGen Resource ---
    RESOURCE_VERSION("resource.version", "2.0.0"),
    RESOURCE_N_A("resource.n_a", "N/A"),
    RESOURCE_NOT_INITIALIZED("resource.not_initialized", "not initialized"),
    RESOURCE_NONE("resource.none", "none"),
    RESOURCE_IDLE("resource.idle", "idle"),

    // --- Status Event Publisher ---
    STATUS_TRUNCATED("status.truncated", "... (truncated, {0} chars)"),

    // --- Console Status Renderer ---
    STATUS_KB_FORMAT("status.kb.format", "{0}KB"),
    STATUS_B_FORMAT("status.b.format", "{0}B"),
    STATUS_MS_FORMAT("status.ms.format", "{0}ms"),
    STATUS_S_FORMAT("status.s.format", "{0}s"),

    // --- Rest Status Tracker ---
    STATUS_IDLE("status.idle", "IDLE"),
    STATUS_RUNNING("status.running", "RUNNING"),
    STATUS_COMPLETED("status.completed", "COMPLETED"),
    STATUS_FAILED("status.failed", "FAILED"),
    STATUS_FAILURE("status.failure", "FAILURE"),

    // --- Config Loader ---
    CONFIG_FILE_NOT_FOUND("config.file.not_found", "Configuration file not found: {0}"),
    CONFIG_LOAD_FAILED("config.load.failed", "Failed to load agent configuration"),

    // --- Serializers ---
    SERIALIZER_MESSAGE_NULL("serializer.message.null", "message must not be null"),
    SERIALIZER_JSON_NULL("serializer.json.null", "json must not be null or blank"),
    SERIALIZER_SERIALIZE_FAILED("serializer.serialize.failed", "Failed to serialize {0}: {1}"),
    SERIALIZER_DESERIALIZE_FAILED("serializer.deserialize.failed", "Failed to deserialize ChatMessage from JSON: {0}"),
    SERIALIZER_UNKNOWN_MESSAGE_TYPE("serializer.unknown.message_type", "Unknown message type: {0}"),
    SERIALIZER_UNKNOWN_RECORD_TYPE("serializer.unknown.record_type", "Unknown record type: {0}"),

    // --- MCP ---
    MCP_UNSUPPORTED_TRANSPORT("mcp.unsupported.transport", "Unsupported transport: {0}. Only stdio is supported."),
    MCP_CONNECT_FAILED("mcp.connect.failed", "Failed to connect to MCP server: {0}"),
    // --- Miscellaneous ---
    SHELL_EXEC_ERROR("shell.exec.error", "Error executing command: {0}"),
    ASK_NO_UI("ask.no.ui", "Error: No user interface available to ask question"),
    ASK_TIMEOUT("ask.timeout", "No answer received within the time limit"),
    EDIT_TEST_ERROR("edit.test.error", "Test execution error: {0}"),
    MCP_TOOL_ERROR("mcp.tool.error", "Error calling MCP tool: {0}"),


    // --- Skeleton Extractor ---
    SKELETON_FILE_NOT_FOUND("skeleton.file.not_found", "File not found: {0}"),
    // --- Skeleton Extractor (additional) ---
    SKELETON_NO_FUNCTIONS("skeleton.no.functions", "No functions/methods found."),
    SKELETON_FUNCTIONS_FOUND("skeleton.functions.found", "Functions/methods found ({0}):"),

    SKELETON_FUNC_NOT_FOUND("skeleton.func.not_found", "Function '{0}'{1} not found in file"),
    SKELETON_FUNC_NOT_FOUND_SIMPLE("skeleton.func.not_found_simple", "Function '{0}' not found in file"),
    SKELETON_LANGUAGE_UNKNOWN("skeleton.language.unknown", "unknown"),
    SKELETON_SETTER_PREFIX("skeleton.setter.prefix", "set"),


    // --- File Patch Util ---
    PATCH_EMPTY("patch.empty", "Empty patch"),
    PATCH_INVALID_HEADER("patch.invalid.header", "Invalid patch header: {0}"),
    PATCH_START_OUT_OF_RANGE("patch.start.out_of_range", "Start line {0} is out of range (file has {1} lines)"),
    PATCH_END_OUT_OF_RANGE("patch.end.out_of_range", "End line {0} is out of range (file has {1} lines)"),
    PATCH_FILE_NOT_FOUND("patch.file.not_found", "Error: File not found: {0}"),
    PATCH_APPLY_ERROR("patch.apply.error", "Error applying patch {0}: {1}. File has been restored to original state."),
    PATCH_APPLY_SUCCESS("patch.apply.success", "Successfully applied {0} patch(es) to {1} (net {2} lines)."),
    // --- File Patch Util (additional) ---
    PATCH_APPLY_ERROR_SIMPLE("patch.apply.error.simple", "Error applying patch {0}: {1}"),
    PATCH_NO_HUNKS("patch.no.hunks", "Error: No valid hunks found in diff"),
    PATCH_HUNK_OUT_OF_RANGE("patch.hunk.out_of_range", "Error: Hunk at line {0} is out of range (file has {1} lines)"),
    PATCH_CONTEXT_MISMATCH("patch.context.mismatch", "Error: Context mismatch at line {0}: {1}"),
    PATCH_SUCCESS_HUNKS("patch.success.hunks", "Successfully applied {0} hunk(s) to {1}\n{2}"),
    PATCH_APPEND_DESC("patch.append.desc", "Appended {0} line(s) after line {1}"),
    PATCH_INSERT_DESC("patch.insert.desc", "Inserted {0} line(s) before line {1}"),
    PATCH_REPLACE_DESC("patch.replace.desc", "Replaced {0} line(s) at line {1} with {2} line(s)"),
    PATCH_DELETE_DESC("patch.delete.desc", "Deleted {0} line(s) at line {1}"),
    PATCH_HUNK_APPLIED("patch.hunk.applied", "Applied hunk at line {0} (-{1} +{2} lines)"),
    PATCH_UNKNOWN_OP("patch.unknown.op", "Unknown operation: {0}. Valid operations: A (append), I (insert), R (replace), D (delete)"),
    PATCH_SUMMARY_PREFIX("patch.summary.prefix", "Patch {0}:"),


    PATCH_SEGMENT_TOO_SHORT("patch.segment.too_short", "File segment too short, expected '{0}'"),
    PATCH_LINE_MISMATCH("patch.line.mismatch", "Expected '{0}' but found '{1}'"),


    // --- DeepSeek Chat Model ---
    DEEPSEEK_UNKNOWN_MESSAGE_TYPE("deepseek.unknown.message_type", "Unknown message type: {0}");

    private final String key;
    private final String description;

    MessageKey(String key, String description) {
        this.key = key;
        this.description = description;
    }

    /**
     * Returns the resource bundle key string.
     */
    public String key() {
        return key;
    }

    /**
     * Returns a brief English description of what this key represents.
     * Useful for IDE autocomplete and code browsing.
     */
    public String desc() {
        return description;
    }

    @Override
    public String toString() {
        return key;
    }
}
