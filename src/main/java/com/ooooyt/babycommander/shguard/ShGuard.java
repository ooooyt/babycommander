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

    /** Classify a command and return the enriched report. */
    public static ShGuardReport analyzeDetailed(String command) {
        return withPolicy(ShGuardPolicy.defaults()).classify(command);
    }

    /** Classify a command with this guard's policy. */
    public ShGuardReport classify(String command) {
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

        Analyzer analyzer = new Analyzer(policy, command);
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
        private final Analysis analysis = new Analysis();

        Analyzer(ShGuardPolicy policy, String command) {
            this.policy = policy;
            this.command = command;
        }

        // ---- Program / list structure ---------------------------------

        @Override
        public Void visitProgram(ProgramContext ctx) {
            // Pre-pass: collect constant assignments for folding.
            collectAssignments(ctx);
            return visitChildren(ctx);
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
            List<ShWord> words = collectWords(ctx);
            List<RedirectContext> redirects = ctx.redirect();

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
                    if (target != null && !target.isEmpty() && !target.startsWith("$")
                            && isSystemPathWriteTarget(target)) {
                        analysis.dangerous(ShReason.WRITE_REDIRECT_SYSTEM_PATH,
                                "write redirect to system path: " + op + " " + target,
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

        private boolean isDangerousGit(List<ShWord> words) {
            List<String> deq = dequotedWords(words);
            for (int i = 0; i < deq.size(); i++) {
                String t = deq.get(i);
                if (t.equals("push") && hasFlagAfter(deq, i, "--force", "-f")) {
                    return true;
                }
                if (t.equals("push") && hasFlagAfter(deq, i, "--force-with-lease")) {
                    // Force-with-lease is still a force push — flag as dangerous.
                    return true;
                }
                if (t.equals("reset") && hasFlagAfter(deq, i, "--hard")) {
                    return true;
                }
                if (t.equals("rebase") && hasFlagAfter(deq, i, "--onto")) {
                    return true;
                }
                if (t.equals("clean") && hasFlagAfter(deq, i, "-f", "--force", "-fd", "-df", "-x")) {
                    return true;
                }
                if (t.equals("checkout") && hasFlagAfter(deq, i, "--")) {
                    return true;
                }
                if (t.equals("branch") && hasFlagAfter(deq, i, "-D", "-d")) {
                    return true;
                }
                if (t.equals("rm")) {
                    return true;
                }
                if (t.equals("filter-branch")) {
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