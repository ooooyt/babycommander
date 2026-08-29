package com.ooooyt.babycommander.hook;

import com.ooooyt.babycommander.shguard.ShGuard;

/**
 * Facade for shell command safety analysis.
 *
 * <p><strong>Deprecated:</strong> the implementation has moved to the AST-based
 * {@link ShGuard} analyzer ({@code com.ooooyt.babycommander.shguard}), which
 * parses shell commands with ANTLR into a parse tree and runs a semantic
 * safety-analysis pass over it. This class is kept as a thin, backward
 * compatible facade so {@link HookManager}, {@code hooks.yaml} semantics, and
 * the existing test suite keep working unchanged.</p>
 *
 * <p>The legacy token-based implementation is preserved in
 * {@link ShellCommandAnalyzerLegacy} and can be re-enabled for hot rollback by
 * setting the system property {@code BCMD_SHGUARD_DISABLED=true} (the legacy
 * {@code BABY_COMMANDER_SHGUARD_DISABLED} name is still honored).</p>
 */
public final class ShellCommandAnalyzer {

    private static final String DISABLE_PROPERTY = "BCMD_SHGUARD_DISABLED";
    private static final String DISABLE_PROPERTY_LEGACY = "BABY_COMMANDER_SHGUARD_DISABLED";

    private ShellCommandAnalyzer() {
    }

    /**
     * Analyze a shell command and return the most restrictive danger level.
     *
     * <p>Classification rules (most restrictive wins):</p>
     * <ol>
     *   <li>If the command is a fork bomb or performs remote code execution
     *       (e.g. {@code curl ... | bash}) → {@link DangerLevel#DANGEROUS}.</li>
     *   <li>If any sub-command is dangerous (destructive command, {@code sudo}
     *       escalation, dangerous git operation, system redirection, database
     *       destruction) → {@link DangerLevel#DANGEROUS}.</li>
     *   <li>If every sub-command is a recognized safe command with benign
     *       arguments → {@link DangerLevel#SAFE}.</li>
     *   <li>Otherwise → {@link DangerLevel#ASK_ONCE}.</li>
     * </ol>
     *
     * @param command the full shell command string
     * @return the computed danger level
     */
    public static DangerLevel analyze(String command) {
        return analyze(command, null);
    }

    /**
     * Analyze a shell command with project-root context. The project folder is
     * used for path-scoped write classification (rule 2): writes confined to
     * the project are safe, writes outside are gated. A {@code null} root
     * falls back to the legacy conservative classification.
     */
    public static DangerLevel analyze(String command, String projectRoot) {
        if (isDisabled()) {
            return ShellCommandAnalyzerLegacy.analyze(command);
        }
        return ShGuard.analyze(command, projectRoot);
    }

    private static boolean isDisabled() {
        return Boolean.getBoolean(DISABLE_PROPERTY)
                || "true".equalsIgnoreCase(System.getenv(DISABLE_PROPERTY))
                || Boolean.getBoolean(DISABLE_PROPERTY_LEGACY)
                || "true".equalsIgnoreCase(System.getenv(DISABLE_PROPERTY_LEGACY));
    }
}