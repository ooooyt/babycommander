package com.ooooyt.babycommander.shguard;

import com.ooooyt.babycommander.hook.DangerLevel;
import com.ooooyt.babycommander.util.antlr.ShellCommandLexer;
import com.ooooyt.babycommander.util.antlr.ShellCommandParser;
import com.ooooyt.babycommander.util.antlr.ShellCommandParserBaseVisitor;
import com.ooooyt.babycommander.util.antlr.ShellCommandParser.*;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SH-GUARD — AST-based shell command safety analyzer.
 *
 * <p>Parses a shell command with the ANTLR-generated {@link ShellCommandLexer}
 * / {@link ShellCommandParser} into a parse tree (the AST), then runs a
 * semantic safety-analysis pass over the tree. The pass classifies the command
 * as {@link DangerLevel#DANGEROUS}, {@link DangerLevel#ASK_ONCE}, or
 * {@link DangerLevel#SAFE}, with parse-aware handling of quoting, escapes,
 * expansions, redirections, pipelines, control flow, and constant folding of
 * known-variable assignments.</p>
 *
 * <p>This replaces the legacy token-based {@code ShellCommandAnalyzer} logic
 * with a strictly more robust AST-based analysis: quoted/escaped command names
 * ({@code r"m"}), command substitution ({@code $(rm -rf /)}), variable
 * indirection ({@code CMD=rm; $CMD -rf /}), and structural fork bombs are all
 * detected.</p>
 */
public final class ShGuard {

    private final ShGuardPolicy policy;

    private ShGuard(ShGuardPolicy policy) {
        this.policy = policy;
    }

    /** Create a guard with a custom policy. */
    public static ShGuard withPolicy(ShGuardPolicy policy) {
        return new ShGuard(policy);
    }

    /** Create a guard with the default policy. */
    public static ShGuard defaults() {
        return new ShGuard(ShGuardPolicy.defaults());
    }

    /** Classify a command; same semantics as the legacy {@code ShellCommandAnalyzer.analyze}. */
    public static DangerLevel analyze(String command) {
        return withPolicy(ShGuardPolicy.defaults()).classify(command).level();
    }

    /**
     * Classify a command with project-root context. Write targets (redirects
     * and write-command arguments) are resolved against the project folder:
     * writes confined to the project are safe (rule 2), writes outside are
     * gated. A {@code null}/{@code blank} root falls back to the legacy
     * conservative classification.
     */
    public static DangerLevel analyze(String command, String projectRoot) {
        return withPolicy(ShGuardPolicy.defaults()).classify(command, projectRoot).level();
    }

    /** Classify a command and return the enriched report. */
    public static ShGuardReport analyzeDetailed(String command) {
        return withPolicy(ShGuardPolicy.defaults()).classify(command);
    }

    /** Classify a command with project-root context and return the enriched report. */
    public static ShGuardReport analyzeDetailed(String command, String projectRoot) {
        return withPolicy(ShGuardPolicy.defaults()).classify(command, projectRoot);
    }

    /** Classify a command with this guard's policy. */
    public ShGuardReport classify(String command) {
        return classify(command, null);
    }

    /** Classify a command with this guard's policy and an optional project root. */
    public ShGuardReport classify(String command, String projectRoot) {
        if (command == null || command.isBlank()) {
            return ShGuardReport.of(DangerLevel.ASK_ONCE, List.of());
        }
        if (command.length() > 64 * 1024) {
            // Size cap: bound worst-case parse time; conservative.
            return ShGuardReport.of(DangerLevel.ASK_ONCE, List.of());
        }

        ShellCommandLexer lexer = new ShellCommandLexer(CharStreams.fromString(command));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        ShellCommandParser parser = new ShellCommandParser(tokens);
        parser.removeErrorListeners();
        List<String> errors = new ArrayList<>();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                    int line, int charPositionInLine, String msg,
                                    RecognitionException e) {
                errors.add(msg);
            }
        });

        ProgramContext tree;
        try {
            tree = parser.program();
        } catch (RuntimeException e) {
            return ShGuardReport.parseError("parse exception: " + e.getMessage());
        }

        Analyzer analyzer = new Analyzer(policy, command, projectRoot);
        analyzer.visit(tree);

        if (!errors.isEmpty()) {
            // Parse failure: conservative ASK_ONCE, but keep any DANGEROUS
            // findings already collected (never downgrade a real danger).
            if (analyzer.analysis.anyDangerous) {
                return new ShGuardReport(DangerLevel.DANGEROUS,
                        analyzer.analysis.violations, String.join("; ", errors), false);
            }
            return new ShGuardReport(DangerLevel.ASK_ONCE,
                    analyzer.analysis.violations, String.join("; ", errors), false);
        }

        DangerLevel level = analyzer.analysis.level();
        return new ShGuardReport(level, analyzer.analysis.violations, null, true);
    }

    // ====================================================================
    // Analysis accumulator
    // ====================================================================

    private static final class Analysis {
        boolean anyDangerous = false;
        boolean anyUnknown = false;
        final List<ShViolation> violations = new ArrayList<>();
        final Map<String, String> assignments = new HashMap<>();

        DangerLevel level() {
            if (anyDangerous) {
                return DangerLevel.DANGEROUS;
            }
            return anyUnknown ? DangerLevel.ASK_ONCE : DangerLevel.SAFE;
        }

        void dangerous(ShReason reason, String detail, String subCommand, int start, int end) {
            anyDangerous = true;
            violations.add(new ShViolation(reason, detail, subCommand, start, end));
        }

        void unknown(ShReason reason, String detail, String subCommand, int start, int end) {
            anyUnknown = true;
            violations.add(new ShViolation(reason, detail, subCommand, start, end));
        }
    }

    // ====================================================================
    // Parse-tree visitor (the semantic pass)
    // ====================================================================

    private static final class Analyzer extends ShellCommandParserBaseVisitor<Void> {
        private final ShGuardPolicy policy;
        private final String command;
        private final String projectRoot;
        private final Analysis analysis = new Analysis();
        private final List<int[]> heredocBodyRanges;
        private final List<int[]> unquotedHeredocBodies;

        Analyzer(ShGuardPolicy policy, String command, String projectRoot) {
            this.policy = policy;
            this.projectRoot = projectRoot;
            this.command = command;
            this.heredocBodyRanges = findHeredocBodyRanges(command);
            this.unquotedHeredocBodies = findUnquotedHeredocBodies(command);
        }

        /** True when the source range [start, end) lies inside a heredoc body. */
        private boolean isInHeredocBody(int start, int end) {
            for (int[] r : heredocBodyRanges) {
                if (start >= r[0] && end <= r[1]) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Locates heredoc bodies in the raw command text. Each returned range
         * [start, end) covers the body lines (after the delimiter line) up to
         * and including the terminator line. The body is stdin data, so the
         * semantic pass must not treat it as shell commands.
         */
        private static List<int[]> findHeredocBodyRanges(String command) {
            List<int[]> ranges = new ArrayList<>();
            java.util.regex.Pattern heredocStart = java.util.regex.Pattern.compile(
                    "<<-?\\s*(?:'([^']*)'|\"([^\"]*)\"|\\\\([A-Za-z_][A-Za-z0-9_]*)|([A-Za-z_][A-Za-z0-9_]*))");
            java.util.regex.Matcher m = heredocStart.matcher(command);
            while (m.find()) {
                String delim = m.group(1) != null ? m.group(1)
                        : m.group(2) != null ? m.group(2)
                        : m.group(3) != null ? m.group(3) : m.group(4);
                if (delim == null || delim.isEmpty()) {
                    continue;
                }
                int bodyStart = command.indexOf('\n', m.end());
                if (bodyStart < 0) {
                    continue;
                }
                bodyStart++;
                java.util.regex.Matcher term = java.util.regex.Pattern.compile(
                                "(?m)^[ \\t]*" + java.util.regex.Pattern.quote(delim) + "[ \\t]*\\r?\\n?")
                        .matcher(command);
                term.region(bodyStart, command.length());
                int bodyEnd = term.find() ? term.end() : command.length();
                ranges.add(new int[]{bodyStart, bodyEnd});
            }
            return ranges;
        }

        /**
         * Locates heredoc bodies whose delimiter is <em>unquoted</em>
         * ({@code <<EOF}). Unlike quoted heredocs, these bodies undergo shell
         * expansion, so command substitutions inside them would execute and
         * must be analyzed (see {@link #checkHeredocCommandSubstitutions()}).
         */
        private static List<int[]> findUnquotedHeredocBodies(String command) {
            List<int[]> ranges = new ArrayList<>();
            java.util.regex.Pattern heredocStart = java.util.regex.Pattern.compile(
                    "<<-?\\s*(?:'([^']*)'|\"([^\"]*)\"|\\\\([A-Za-z_][A-Za-z0-9_]*)|([A-Za-z_][A-Za-z0-9_]*))");
            java.util.regex.Matcher m = heredocStart.matcher(command);
            while (m.find()) {
                if (m.group(1) != null || m.group(2) != null || m.group(3) != null) {
                    continue; // quoted/escaped delimiter: body is literal data
                }
                String delim = m.group(4);
                if (delim == null || delim.isEmpty()) {
                    continue;
                }
                int bodyStart = command.indexOf('\n', m.end());
                if (bodyStart < 0) {
                    continue;
                }
                bodyStart++;
                java.util.regex.Matcher term = java.util.regex.Pattern.compile(
                                "(?m)^[ \t]*" + java.util.regex.Pattern.quote(delim) + "[ \t]*\r?\n?")
                        .matcher(command);
                term.region(bodyStart, command.length());
                int bodyEnd = term.find() ? term.end() : command.length();
                ranges.add(new int[]{bodyStart, bodyEnd});
            }
            return ranges;
        }

        /**
         * Scans unquoted heredoc bodies for command substitutions
         * ({@code $(...)} / backticks) and classifies each recursively.
         * Quoted heredoc bodies are literal data and are never scanned.
         */
        private void checkHeredocCommandSubstitutions() {
            for (int[] r : unquotedHeredocBodies) {
                String body = command.substring(r[0], r[1]);
                for (String sub : extractCommandSubstitutions(body)) {
                    ShGuardReport subReport = new ShGuard(policy).classify(sub);
                    if (subReport.level() == DangerLevel.DANGEROUS) {
                        analysis.dangerous(ShReason.REMOTE_CODE_EXECUTION,
                                "heredoc command substitution: " + sub,
                                body, r[0], r[1]);
                    } else if (subReport.level() == DangerLevel.ASK_ONCE) {
                        analysis.unknown(ShReason.VARIABLE_COMMAND_NAME,
                                "heredoc command substitution: " + sub,
                                body, r[0], r[1]);
                    }
                }
            }
        }

        /** Extracts {@code $(...)} and backtick command substitutions from text. */
        private static List<String> extractCommandSubstitutions(String text) {
            List<String> out = new ArrayList<>();
            int i = 0;
            int n = text.length();
            while (i < n) {
                char c = text.charAt(i);
                if (c == '$' && i + 1 < n && text.charAt(i + 1) == '(') {
                    int depth = 1;
                    int j = i + 2;
                    while (j < n && depth > 0) {
                        if (text.charAt(j) == '(') {
                            depth++;
                        } else if (text.charAt(j) == ')') {
                            depth--;
                        }
                        j++;
                    }
                    if (depth == 0) {
                        out.add(text.substring(i + 2, j - 1));
                        i = j;
                        continue;
                    }
                } else if (c == '`') {
                    int end = text.indexOf('`', i + 1);
                    if (end >= 0) {
                        out.add(text.substring(i + 1, end));
                        i = end + 1;
                        continue;
                    }
                }
                i++;
            }
            return out;
        }

        // ---- Program / list structure ---------------------------------

        @Override
        public Void visitProgram(ProgramContext ctx) {
            // Pre-pass: collect constant assignments for folding.
            collectAssignments(ctx);
            visitChildren(ctx);
            // Unquoted heredoc bodies undergo expansion: command substitutions
            // inside them would execute, so analyze them after the main pass.
            checkHeredocCommandSubstitutions();
            return null;
        }

        @Override
        public Void visitPipeline(PipelineContext ctx) {
            List<CommandContext> stages = ctx.command();
            checkRemoteCodeExecution(stages);
            for (CommandContext stage : stages) {
                visit(stage);
            }
            return null;
        }

        @Override
        public Void visitFunction_def(Function_defContext ctx) {
            String fnName = dequoteFirstWord(ctx.word());
            visitChildren(ctx);
            if (fnName != null && isForkBomb(ctx, fnName)) {
                analysis.dangerous(ShReason.FORK_BOMB, "fork bomb: function '" + fnName
                                + "' invokes itself in a pipeline",
                        command.substring(ctx.start.getStartIndex(), ctx.stop.getStopIndex() + 1),
                        ctx.start.getStartIndex(), ctx.stop.getStopIndex() + 1);
            }
            return null;
        }

        // ---- Simple command classification -----------------------------

        @Override
        public Void visitSimple_command(Simple_commandContext ctx) {
            // Heredoc bodies are data, not commands: `cat > f <<'EOF' ... EOF`
            // must not classify the body lines as shell commands.
            if (isInHeredocBody(ctx.start.getStartIndex(), ctx.stop.getStopIndex() + 1)) {
                return null;
            }
            List<ShWord> words = collectWords(ctx);
            List<RedirectContext> redirects = new ArrayList<>(ctx.redirect());
            // Redirects consumed as prefixes (e.g. "> /etc/crontab" with no
            // command word) are children of PrefixContext, not direct children.
            for (PrefixContext pfx : ctx.prefix()) {
                if (pfx.redirect() != null) {
                    redirects.add(pfx.redirect());
                }
            }

            // Redirection safety (applies even with no command words).
            if (checkWriteRedirects(redirects, ctx)) {
                return null;
            }

            if (words.isEmpty()) {
                // Pure assignment / redirect command (e.g. "FOO=bar").
                return null;
            }

            // Sudo escalation anywhere in the command.
            if (policy.flagSudoAlways() && containsDequotedWord(words, "sudo")) {
                analysis.dangerous(ShReason.SUDO_ESCALATION, "sudo escalation",
                        subCommandText(ctx), ctx.start.getStartIndex(), ctx.stop.getStopIndex() + 1);
                return null;
            }

            ShWord cmdWord = words.get(0);
            String cmdName = resolveCommandName(cmdWord);

            // Recursively analyze command substitutions inside every word.
            for (ShWord w : words) {
                String body = w.commandSubstitutionBody();
                if (body != null) {
                    ShGuardReport sub = new ShGuard(policy).classify(body);
                    if (sub.level() == DangerLevel.DANGEROUS) {
                        analysis.dangerous(ShReason.REMOTE_CODE_EXECUTION,
                                "command substitution: " + body,
                                subCommandText(ctx),
                                ctx.start.getStartIndex(), ctx.stop.getStopIndex() + 1);
                    } else if (sub.level() == DangerLevel.ASK_ONCE) {
                        analysis.unknown(ShReason.VARIABLE_COMMAND_NAME,
                                "command substitution: " + body,
                                subCommandText(ctx),
                                ctx.start.getStartIndex(), ctx.stop.getStopIndex() + 1);
                    }
                }
            }

            if (cmdName == null) {
                analysis.unknown(ShReason.VARIABLE_COMMAND_NAME,
                        "command name not statically resolvable: " + cmdWord.dequoted(),
                        subCommandText(ctx), ctx.start.getStartIndex(), ctx.stop.getStopIndex() + 1);
                return null;
            }

            if (policy.dangerousCommands().contains(cmdName)) {
                analysis.dangerous(ShReason.DANGEROUS_COMMAND, cmdName,
                        subCommandText(ctx), ctx.start.getStartIndex(), ctx.stop.getStopIndex() + 1);
                return null;
            }

            if ("git".equals(cmdName) && isDangerousGit(words)) {
                analysis.dangerous(ShReason.DANGEROUS_GIT_OP, "destructive git operation",
                        subCommandText(ctx), ctx.start.getStartIndex(), ctx.stop.getStopIndex() + 1);
                return null;
            }

            if (isDangerousPackageOp(cmdName, words)) {
                analysis.dangerous(ShReason.DANGEROUS_PACKAGE_OP, "destructive package operation",
                        subCommandText(ctx), ctx.start.getStartIndex(), ctx.stop.getStopIndex() + 1);
                return null;
            }

            if (hasDangerousFlags(cmdName, words)) {
                analysis.dangerous(ShReason.DANGEROUS_FLAG, "dangerous flag combination",
                        subCommandText(ctx), ctx.start.getStartIndex(), ctx.stop.getStopIndex() + 1);
                return null;
            }

            if (isDatabaseDestruction(words)) {
                analysis.dangerous(ShReason.DB_DESTRUCTION, "database destruction",
                        subCommandText(ctx), ctx.start.getStartIndex(), ctx.stop.getStopIndex() + 1);
                return null;
            }

            // Rule 2 (path-aware writes): commands whose arguments are write
            // targets (sed -i, tee, touch, mkdir, cp/mv/ln dest, git add, ...)
            // are safe when every target resolves inside the project folder
            // (or /tmp); any target outside the project is gated. Without a
            // project root, sed -i and tee keep their legacy conservative
            // classification (any in-place edit / system-path tee is dangerous).
            if (isWriteArgCommand(cmdName)) {
                List<String> targets = writeTargets(cmdName, dequotedWords(words));
                if (projectRoot == null || projectRoot.isBlank()) {
                    if (cmdName.equals("sed") && hasInPlaceFlag(dequotedWords(words))) {
                        analysis.dangerous(ShReason.DANGEROUS_FLAG, "sed in-place edit",
                                subCommandText(ctx), ctx.start.getStartIndex(), ctx.stop.getStopIndex() + 1);
                        return null;
                    }
                    if (cmdName.equals("tee") && hasSystemPathArg(dequotedWords(words))) {
                        analysis.dangerous(ShReason.DANGEROUS_FLAG, "tee to system path",
                                subCommandText(ctx), ctx.start.getStartIndex(), ctx.stop.getStopIndex() + 1);
                        return null;
                    }
                } else if (!targets.isEmpty()) {
                    for (String t : targets) {
                        if (!isWriteTargetInScope(expandHome(t))) {
                            analysis.dangerous(ShReason.WRITE_OUTSIDE_PROJECT,
                                    "write target outside project: " + t,
                                    subCommandText(ctx),
                                    ctx.start.getStartIndex(), ctx.stop.getStopIndex() + 1);
                            return null;
                        }
                    }
                    // All write targets are inside the project folder: safe.
                    return null;
                }
            }

            if (policy.safeCommands().contains(cmdName)) {
                return null; // safe
            }

            if (policy.strictUnknownCommand()) {
                analysis.dangerous(ShReason.UNKNOWN_COMMAND, "unknown command: " + cmdName,
                        subCommandText(ctx), ctx.start.getStartIndex(), ctx.stop.getStopIndex() + 1);
            } else {
                analysis.unknown(ShReason.UNKNOWN_COMMAND, "unknown command: " + cmdName,
                        subCommandText(ctx), ctx.start.getStartIndex(), ctx.stop.getStopIndex() + 1);
            }
            return null;
        }

        // ---- Helpers ---------------------------------------------------

        private void checkRemoteCodeExecution(List<CommandContext> stages) {
            for (int i = 0; i + 1 < stages.size(); i++) {
                String cur = firstCommandName(stages.get(i));
                List<ShWord> nextWords = collectWords(stages.get(i + 1));
                if (cur != null && policy.remoteFetchCommands().contains(cur)
                        && containsAnyDequotedWord(nextWords, policy.shellExecutorCommands())) {
                    CommandContext c = stages.get(i + 1);
                    analysis.dangerous(ShReason.REMOTE_CODE_EXECUTION,
                            "remote content piped to shell: " + cur + " | ...",
                            command.substring(c.start.getStartIndex(), c.stop.getStopIndex() + 1),
                            c.start.getStartIndex(), c.stop.getStopIndex() + 1);
                }
            }
        }

        private boolean isForkBomb(Function_defContext ctx, String fnName) {
            // The function body must contain a pipeline whose stage invokes the
            // function's own name (structural fork-bomb detection).
            List<PipelineContext> pipelines = new ArrayList<>();
            collectPipelines(ctx, pipelines);
            for (PipelineContext p : pipelines) {
                for (CommandContext stage : p.command()) {
                    if (fnName.equals(firstCommandName(stage))) {
                        return true;
                    }
                }
            }
            return false;
        }

        private void collectPipelines(ParseTree node, List<PipelineContext> out) {
            if (node instanceof PipelineContext p) {
                out.add(p);
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                collectPipelines(node.getChild(i), out);
            }
        }

        private void collectAssignments(ParseTree node) {
            if (node instanceof AssignmentContext a) {
                String name = dequoteFirstWord(a.word(0));
                String value = dequoteFirstWord(a.word(1));
                if (name != null && value != null) {
                    analysis.assignments.put(name, value);
                }
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                collectAssignments(node.getChild(i));
            }
        }

        private String resolveCommandName(ShWord word) {
            if (word.isFullyStatic()) {
                return word.dequoted();
            }
            if (policy.foldAssignments()) {
                // Attempt constant folding: replace $NAME / ${NAME} with known values.
                String folded = fold(word.dequoted());
                if (folded != null && !folded.isEmpty()) {
                    return folded;
                }
            }
            // Not fully static: fall back to the static skeleton (literal parts
            // only). If the skeleton is a known dangerous or safe command, use
            // it (rm$() -> rm); otherwise the name is unresolvable.
            String skeleton = staticSkeleton(word);
            if (!skeleton.isEmpty()) {
                if (policy.dangerousCommands().contains(skeleton)
                        || policy.safeCommands().contains(skeleton)) {
                    return skeleton;
                }
            }
            return null;
        }

        /** Concatenation of literal parts only (expansions dropped). */
        private static String staticSkeleton(ShWord word) {
            StringBuilder sb = new StringBuilder();
            for (ShWord.Part p : word.parts()) {
                if (p.kind() == ShWord.PartKind.LITERAL) {
                    sb.append(p.text());
                }
            }
            return sb.toString();
        }

        /** Best-effort constant folding of a dequoted word. Returns null when unresolvable. */
        private String fold(String dequoted) {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            int n = dequoted.length();
            while (i < n) {
                char c = dequoted.charAt(i);
                if (c == '$' && i + 1 < n) {
                    char nxt = dequoted.charAt(i + 1);
                    if (nxt == '{') {
                        int end = dequoted.indexOf('}', i + 2);
                        if (end >= 0) {
                            String name = dequoted.substring(i + 2, end);
                            String val = analysis.assignments.get(name);
                            if (val == null) {
                                return null;
                            }
                            sb.append(val);
                            i = end + 1;
                            continue;
                        }
                        return null;
                    }
                    if (Character.isLetter(nxt) || nxt == '_') {
                        int j = i + 1;
                        while (j < n && (Character.isLetterOrDigit(dequoted.charAt(j))
                                || dequoted.charAt(j) == '_')) {
                            j++;
                        }
                        String name = dequoted.substring(i + 1, j);
                        String val = analysis.assignments.get(name);
                        if (val == null) {
                            return null;
                        }
                        sb.append(val);
                        i = j;
                        continue;
                    }
                    return null;
                }
                sb.append(c);
                i++;
            }
            return sb.toString();
        }

        private boolean checkWriteRedirects(List<RedirectContext> redirects, Simple_commandContext ctx) {
            for (RedirectContext r : redirects) {
                if (r.heredoc() != null) {
                    continue; // heredocs are read-only
                }
                String op = r.redirect_op().getText();
                if (isWriteOp(op)) {
                    String target = r.word() == null ? "" : dequoteFirstWord(r.word());
                    if (target == null || target.isEmpty() || target.startsWith("$")) {
                        continue;
                    }
                    String expanded = expandHome(target);
                    // Rule 2: writes confined to the project folder (or /tmp,
                    // or the allowlist) are safe — checked before system paths
                    // so in-project writes under $HOME are not mis-flagged.
                    if (projectRoot != null && !projectRoot.isBlank()
                            && isWriteTargetInScope(expanded)) {
                        continue;
                    }
                    if (isSystemPathWriteTarget(expanded)) {
                        analysis.dangerous(ShReason.WRITE_REDIRECT_SYSTEM_PATH,
                                "write redirect to system path: " + op + " " + target,
                                subCommandText(ctx),
                                ctx.start.getStartIndex(), ctx.stop.getStopIndex() + 1);
                        return true;
                    }
                    if (projectRoot != null && !projectRoot.isBlank()) {
                        analysis.dangerous(ShReason.WRITE_OUTSIDE_PROJECT,
                                "write redirect outside project: " + op + " " + target,
                                subCommandText(ctx),
                                ctx.start.getStartIndex(), ctx.stop.getStopIndex() + 1);
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean isWriteOp(String op) {
            return switch (op) {
                case ">", ">>", ">|", "&>", "<>", "2>", "2>>", "1>", "1>>",
                        "3>", "3>>", "4>", "5>", "6>", "7>", "8>", "9>" -> true;
                default -> false;
            };
        }

        private boolean isSystemPathWriteTarget(String target) {
            if (policy.writeRedirectAllowlist().contains(target)) {
                return false;
            }
            for (String prefix : policy.systemPathPrefixes()) {
                if (target.equals(prefix) || target.startsWith(prefix + "/")) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Rule 2 scope check: a write target is in scope when it resolves
         * inside the project folder, is under {@code /tmp} (temp data, no data
         * loss), or is on the write-redirect allowlist. Relative targets are
         * resolved against the project root (the shell working directory).
         */
        private boolean isWriteTargetInScope(String target) {
            if (target == null || target.isEmpty()) {
                return false;
            }
            if (policy.writeRedirectAllowlist().contains(target)) {
                return true;
            }
            if (isTempPath(target)) {
                return true;
            }
            if (projectRoot == null || projectRoot.isBlank()) {
                // No project context: only relative paths are unverifiable
                // (treated as in-scope by the caller's legacy path).
                return !java.nio.file.Path.of(target).isAbsolute();
            }
            try {
                java.nio.file.Path root = java.nio.file.Path.of(projectRoot).normalize();
                java.nio.file.Path t = java.nio.file.Path.of(target);
                if (!t.isAbsolute()) {
                    t = root.resolve(t);
                }
                return t.normalize().startsWith(root);
            } catch (Exception e) {
                return false; // unresolvable target: gate conservatively
            }
        }

        /** {@code /tmp} writes are temp data: no data loss, so in scope. */
        private static boolean isTempPath(String target) {
            return target.equals("/tmp") || target.startsWith("/tmp/");
        }

        /** Expand a leading {@code ~} to the user home directory. */
        private static String expandHome(String target) {
            if (target == null) {
                return null;
            }
            String home = System.getProperty("user.home");
            if (home == null) {
                return target;
            }
            if (target.equals("~")) {
                return home;
            }
            if (target.startsWith("~/")) {
                return home + target.substring(1);
            }
            return target;
        }

        /** Commands whose non-flag arguments are (or include) write targets. */
        private static boolean isWriteArgCommand(String cmdName) {
            return switch (cmdName) {
                case "sed", "tee", "touch", "mkdir", "cp", "mv", "ln", "install", "git" -> true;
                default -> false;
            };
        }

        /**
         * Extract the write-target arguments of a write command. Flags and
         * option values are skipped; for {@code cp}/{@code mv}/{@code ln}/
         * {@code install} only the destination (last non-flag arg) is checked.
         */
        private List<String> writeTargets(String cmdName, List<String> deq) {
            List<String> targets = new ArrayList<>();
            switch (cmdName) {
                case "sed" -> {
                    for (int i = 1; i < deq.size(); i++) {
                        String t = deq.get(i);
                        if (t.startsWith("-i") || t.equals("--in-place")
                                || t.startsWith("--in-place=")) {
                            if ((t.equals("-i") || t.equals("--in-place")) && i + 1 < deq.size()) {
                                i++; // skip the sed script argument
                            }
                            continue;
                        }
                        if (t.startsWith("-")) {
                            continue;
                        }
                        targets.add(t);
                    }
                }
                case "tee", "touch", "mkdir" -> {
                    for (int i = 1; i < deq.size(); i++) {
                        String t = deq.get(i);
                        if (t.startsWith("-")) {
                            continue;
                        }
                        targets.add(t);
                    }
                }
                case "cp", "mv", "ln", "install" -> {
                    String last = null;
                    for (int i = 1; i < deq.size(); i++) {
                        String t = deq.get(i);
                        if (t.startsWith("-")) {
                            continue;
                        }
                        last = t;
                    }
                    if (last != null) {
                        targets.add(last);
                    }
                }
                case "git" -> {
                    if (deq.size() > 1 && deq.get(1).equals("add")) {
                        for (int i = 2; i < deq.size(); i++) {
                            String t = deq.get(i);
                            if (t.startsWith("-")) {
                                continue;
                            }
                            targets.add(t);
                        }
                    }
                }
                default -> { }
            }
            return targets;
        }

        /** True when the dequoted tokens contain a {@code sed -i} in-place flag. */
        private static boolean hasInPlaceFlag(List<String> deq) {
            for (String t : deq) {
                if (t.startsWith("-i") || t.equals("--in-place") || t.startsWith("--in-place=")) {
                    return true;
                }
            }
            return false;
        }

        /** True when any dequoted token (with {@code ~} expanded) is a system path. */
        private boolean hasSystemPathArg(List<String> deq) {
            for (String t : deq) {
                if (isSystemPath(expandHome(t))) {
                    return true;
                }
            }
            return false;
        }

        private boolean isDangerousGit(List<ShWord> words) {
            List<String> deq = dequotedWords(words);
            for (int i = 0; i < deq.size(); i++) {
                String t = deq.get(i);
                if (t.equals("push")) {
                    // Force push (any spelling) is destructive.
                    if (hasFlagAfter(deq, i, "--force", "-f", "--force-with-lease")) {
                        return true;
                    }
                    // Deleting a remote branch: `git push --delete` / `-d`,
                    // or a delete refspec like `git push origin :branch`.
                    if (hasFlagAfter(deq, i, "--delete", "-d")) {
                        return true;
                    }
                    for (int j = i + 1; j < deq.size(); j++) {
                        if (deq.get(j).startsWith(":")) {
                            return true;
                        }
                    }
                }
                if (t.equals("reset") && hasFlagAfter(deq, i, "--hard")) {
                    return true;
                }
                if (t.equals("rebase") && hasFlagAfter(deq, i, "--onto")) {
                    return true;
                }
                if (t.equals("clean") && (hasFlagAfter(deq, i, "-f", "--force", "-fd", "-df", "-x")
                        || hasShortFlagCharAfter(deq, i, 'f'))) {
                    return true;
                }
                if (t.equals("checkout") && hasFlagAfter(deq, i, "--")) {
                    return true;
                }
                if (t.equals("branch") && hasFlagAfter(deq, i, "-D", "-d")) {
                    return true;
                }
                if (t.equals("stash") && hasFlagAfter(deq, i, "drop", "clear")) {
                    return true;
                }
                if (t.equals("tag") && hasFlagAfter(deq, i, "-d", "--delete")) {
                    return true;
                }
                if (t.equals("update-ref") && hasFlagAfter(deq, i, "-d", "--delete")) {
                    return true;
                }
                if (t.equals("rm") || t.equals("filter-branch")) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasFlagAfter(List<String> tokens, int index, String... flags) {
            for (int j = index + 1; j < tokens.size(); j++) {
                for (String f : flags) {
                    if (tokens.get(j).equals(f)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * True when any token after {@code index} is a combined short option
         * (e.g. {@code -fdx}) containing {@code flagChar}. Catches compact
         * spellings like {@code git clean -fdx} that exact-token matching misses.
         */
        private boolean hasShortFlagCharAfter(List<String> tokens, int index, char flagChar) {
            for (int j = index + 1; j < tokens.size(); j++) {
                String t = tokens.get(j);
                if (t.length() > 1 && t.charAt(0) == '-' && t.charAt(1) != '-') {
                    for (int k = 1; k < t.length(); k++) {
                        if (t.charAt(k) == flagChar) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private boolean isDangerousPackageOp(String command, List<ShWord> words) {
            List<String> deq = dequotedWords(words);
            if ("apt".equals(command) || "apt-get".equals(command)) {
                for (String t : deq) {
                    if (t.equals("remove") || t.equals("purge") || t.equals("autoremove")) {
                        return true;
                    }
                }
            }
            if ("dpkg".equals(command)) {
                for (String t : deq) {
                    if (t.equals("-r") || t.equals("--remove") || t.equals("-P") || t.equals("--purge")) {
                        return true;
                    }
                }
            }
            if ("rpm".equals(command)) {
                for (String t : deq) {
                    if (t.equals("-e") || t.equals("--erase")) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean hasDangerousFlags(String command, List<ShWord> words) {
            List<String> deq = dequotedWords(words);
            if (command.equals("chmod") || command.equals("chown") || command.equals("chgrp")) {
                for (String t : deq) {
                    if (t.equals("777") || t.equals("-R") || t.equals("--recursive")
                            || isSystemPath(t)) {
                        return true;
                    }
                }
            }
            // NOTE: sed -i and tee are handled by the path-aware write-target
            // check in visitSimple_command (rule 2): in-project edits are safe,
            // edits outside the project folder are gated. Without a project
            // root they keep their legacy conservative classification there.
            // xargs executing a dangerous command (e.g. xargs rm -rf, xargs sudo).
            if (command.equals("xargs")) {
                for (int i = 1; i < deq.size(); i++) {
                    String t = deq.get(i);
                    if (policy.dangerousCommands().contains(t) || t.equals("sudo")
                            || policy.shellExecutorCommands().contains(t)) {
                        return true;
                    }
                }
            }
            // awk/gawk/mawk with system() code execution.
            if (command.equals("awk") || command.equals("gawk") || command.equals("mawk")) {
                for (String t : deq) {
                    if (t.contains("system(")) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean isSystemPath(String token) {
            if (token == null || token.isEmpty()) {
                return false;
            }
            if (token.equals("/")) {
                return true;
            }
            for (String dir : policy.systemPathPrefixes()) {
                if (token.equals(dir) || token.startsWith(dir + "/")) {
                    return true;
                }
            }
            return false;
        }

        private boolean isDatabaseDestruction(List<ShWord> words) {
            List<String> deq = dequotedWords(words);
            for (int i = 0; i < deq.size(); i++) {
                String t = deq.get(i);
                if (t.equalsIgnoreCase("drop") || t.equalsIgnoreCase("truncate")) {
                    for (int j = i + 1; j < deq.size(); j++) {
                        if (deq.get(j).equalsIgnoreCase("database")
                                || deq.get(j).equalsIgnoreCase("table")
                                || deq.get(j).equalsIgnoreCase("schema")) {
                            return true;
                        }
                    }
                }
                if (t.equalsIgnoreCase("DELETE") && i + 1 < deq.size()
                        && deq.get(i + 1).equalsIgnoreCase("FROM")) {
                    return true;
                }
            }
            return false;
        }

        // ---- Word collection -------------------------------------------

        /**
         * Collect the logical words of a simple command, merging adjacent
         * word fragments (no whitespace gap) into single {@link ShWord}s.
         */
        private List<ShWord> collectWords(Simple_commandContext ctx) {
            List<ShWord> result = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            int prevStop = -1;
            boolean have = false;

            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);
                if (child instanceof WordContext w) {
                    Token tok = w.getStart();
                    if (have && tok.getStartIndex() != prevStop + 1) {
                        result.add(ShWord.parse(current.toString()));
                        current.setLength(0);
                        have = false;
                    }
                    current.append(tok.getText());
                    prevStop = tok.getStopIndex();
                    have = true;
                } else if (child instanceof Command_wordContext cw) {
                    // Command name (first word of a simple command).
                    Token tok = cw.getStart();
                    if (have && tok.getStartIndex() != prevStop + 1) {
                        result.add(ShWord.parse(current.toString()));
                        current.setLength(0);
                        have = false;
                    }
                    current.append(tok.getText());
                    prevStop = tok.getStopIndex();
                    have = true;
                } else {
                    // Non-word child (prefix / redirect) breaks adjacency.
                    if (have) {
                        result.add(ShWord.parse(current.toString()));
                        current.setLength(0);
                        have = false;
                    }
                }
            }
            if (have) {
                result.add(ShWord.parse(current.toString()));
            }
            return result;
        }

        /** Collect words of a command context (handles simple and compound). */
        private List<ShWord> collectWords(CommandContext ctx) {
            if (ctx.simple_command() != null) {
                return collectWords(ctx.simple_command());
            }
            return List.of();
        }

        private String firstCommandName(CommandContext ctx) {
            if (ctx.simple_command() != null) {
                List<ShWord> words = collectWords(ctx.simple_command());
                if (words.isEmpty()) {
                    return null;
                }
                return resolveCommandName(words.get(0));
            }
            return null;
        }

        private String dequoteFirstWord(WordContext ctx) {
            if (ctx == null || ctx.getChildCount() == 0) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);
                if (child instanceof org.antlr.v4.runtime.tree.TerminalNode tn) {
                    sb.append(tn.getText());
                }
            }
            return ShWord.parse(sb.toString()).dequoted();
        }

        private List<String> dequotedWords(List<ShWord> words) {
            List<String> out = new ArrayList<>(words.size());
            for (ShWord w : words) {
                out.add(w.dequoted());
            }
            return out;
        }

        private boolean containsDequotedWord(List<ShWord> words, String value) {
            for (ShWord w : words) {
                if (value.equals(w.dequoted())) {
                    return true;
                }
            }
            return false;
        }

        private boolean containsAnyDequotedWord(List<ShWord> words, java.util.Set<String> values) {
            for (ShWord w : words) {
                if (values.contains(w.dequoted())) {
                    return true;
                }
            }
            return false;
        }

        private String subCommandText(Simple_commandContext ctx) {
            return command.substring(ctx.start.getStartIndex(), ctx.stop.getStopIndex() + 1);
        }
    }
}
