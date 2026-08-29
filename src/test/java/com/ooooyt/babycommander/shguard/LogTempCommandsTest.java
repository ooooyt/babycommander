package com.ooooyt.babycommander.shguard;

import com.ooooyt.babycommander.hook.DangerLevel;
import com.ooooyt.babycommander.hook.ShellCommandAnalyzer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that every command recorded in {@code logs/temp.txt} is classified
 * as {@link DangerLevel#SAFE} by the current analyzer, i.e. no user
 * confirmation is needed for any of them.
 *
 * <p>The log shows these commands being confirmed with {@code y} prompts; that
 * was recorded against an older analyzer. Under the current AST-based
 * {@link ShGuard} (used via {@link ShellCommandAnalyzer}), all of them are
 * composed of whitelisted safe commands ({@code cd, ls, grep, find, cat, echo,
 * head, tail, git, mvn, java, ...}) with no destructive flags or system-path
 * redirects, so they must auto-allow.</p>
 */
class LogTempCommandsTest {

    /** Commands extracted verbatim from logs/temp.txt (heredoc/truncated ones reconstructed). */
    private static final List<String> LOG_COMMANDS = List.of(
            // 1. git log (read-only)
            "cd /Users/yangtao/local-repo/babycommander && git log --format=\"%h %ad %s\" --date=format:\"%Y-%m-%d %H:%M\" -8 2>/dev/null",
            // 2. ls (read-only)
            "cd /Users/yangtao/local-repo/babycommander && ls -la ~/.babycommander/db/objectbox/ 2>/dev/null",
            // 3. grep pipeline (read-only)
            "cd /Users/yangtao/local-repo/babycommander && grep -rn \"ls-files\" --include=\"*.md\" --include=\"*.txt\" --include=\"*.java\" --include=\"*.yaml\" --include=\"*.properties\" . 2>/dev/null | grep -v \"target/\\|build/\\|\\.git/\" | head -20",
            // 4. find + echo (read-only)
            "cd /Users/yangtao/local-repo/babycommander && find src -name \"MessageEntity.java\" -o -name \"ConversationEntity.java\" 2>/dev/null; echo \"---\"; find src -path \"*entity*\" -name \"*.java\" 2>/dev/null | head",
            // 5. find + ls in ~/.m2 (read-only)
            "find ~/.m2/repository/io/objectbox -name \"*.jar\" 2>/dev/null | grep -v sources | head; echo \"---\"; ls /Users/yangtao/local-repo/babycommander/objectbox-models/ 2>/dev/null",
            // 6. cat > /tmp/DumpHooks.java via heredoc (new file, no data loss)
            "cd /Users/yangtao/local-repo/babycommander && cat > /tmp/DumpHooks.java <<'EOF'\nimport io.objectbox.Box;\nimport io.objectbox.BoxStore;\nEOF",
            // 7. find + grep in ~/.m2 (read-only)
            "find ~/.m2/repository -name \"*.jar\" 2>/dev/null | grep -i \"essentials\\|greenrobot\" | grep -v sources | head; echo \"---\"; find ~/.m2/repository -name \"*.jar\" 2>/dev/null | grep -i \"slf4j-api\" | grep -v sources | head -3",
            // 8. ls (read-only)
            "ls src/main/java/com/ooooyt/babycommander/shguard/ src/main/java/com/ooooyt/babycommander/hook/ 2>/dev/null",
            // 9/10. CP=... && java -cp "$CP" ... (runs code, no data loss)
            "cd /Users/yangtao/local-repo/babycommander && CP=\"target/classes:$HOME/.m2/repository/io/objectbox/objectbox-java/4.3.0/objectbox-java-4.3.0.jar:$HOME/.m2/repository/io/objectbox/objectbox-java-api/4.3.0/objectbox-java-api-4.3.0.jar\" && java -cp \"$CP\" com.ooooyt.babycommander.DumpHooks",
            // 11. cat | grep | head (read-only)
            "cd /Users/yangtao/local-repo/babycommander && cat logs/app.log.5 2>/dev/null | grep -E \"PlanTool|TaskPersistence|TokenUsage|started in\" | head -30",
            // 12. git worktree add (new worktree, no data loss) + mvn compile (build under project)
            "cd /Users/yangtao/local-repo/babycommander && git worktree add /tmp/bc-f3971b1 f3971b1 2>&1 | tail -2 && cd /tmp/bc-f3971b1 && mvn -q compile -DskipTests 2>&1 | tail -3; echo \"BUILD EXIT: $?\""
    );

    @Test
    void allLogCommandsAreSafeUnderShGuard() {
        for (String cmd : LOG_COMMANDS) {
            DangerLevel level = ShGuard.analyze(cmd);
            assertEquals(DangerLevel.SAFE, level,
                    "expected SAFE for log command, got " + level + ": " + cmd);
        }
    }

    @Test
    void allLogCommandsAreSafeThroughFacade() {
        for (String cmd : LOG_COMMANDS) {
            DangerLevel level = ShellCommandAnalyzer.analyze(cmd);
            assertEquals(DangerLevel.SAFE, level,
                    "expected SAFE via ShellCommandAnalyzer, got " + level + ": " + cmd);
        }
    }
}