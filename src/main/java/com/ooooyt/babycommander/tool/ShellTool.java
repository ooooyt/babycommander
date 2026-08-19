package com.ooooyt.babycommander.tool;

import com.ooooyt.babycommander.hook.HookManager;
import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;
import dev.langchain4j.agent.tool.Tool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class ShellTool extends BaseTool implements WorkspaceAware {

    private String workingDir;
    private final String[] shellCommand;
    private int maxOutputLength = 20 * 1024;

    public void setMaxOutputLength(int bytes) {
        this.maxOutputLength = bytes;
    }

    public ShellTool(String projectFolder, HookManager hookManager) {
        super(hookManager);
        this.workingDir = normalize(projectFolder);
        this.shellCommand = detectShell();
    }

    @Override
    public void updatePaths(WorkspacePaths paths) {
        if (paths.projectFolder() != null) {
            this.workingDir = paths.projectFolder().toString();
        }
    }

    /**
     * Set the project folder (working directory for shell commands).
     */
    public void setProjectFolder(String projectFolder) {
        this.workingDir = normalize(projectFolder);
    }

    private static String normalize(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return java.nio.file.Paths.get(path).toAbsolutePath().normalize().toString();
    }

    private static String[] detectShell() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return new String[]{"cmd.exe", "/c"};
        } else {
            return new String[]{"bash", "-c"};
        }
    }

    @Tool("Execute a shell command, returns stdout and stderr")
    public String execute(String command) {
        return executeWithStatus("ShellTool", "shell", command, () -> executeInternal(command));
    }

    private String executeInternal(String command) {
        return doExecuteWithTimeout(command, workingDir, 300);
    }

    // Convenience wrapper (LLM should use execute + executeInDirWithTimeout instead)
    public String executeWithTimeout(String command, int timeoutSeconds) {
        String toolInput = command + I18n.tr(MessageKey.SHELL_TIMEOUT, timeoutSeconds);
        return executeWithStatus("ShellTool", "shell", toolInput,
                new Object[]{command, timeoutSeconds},
                () -> doExecuteWithTimeout(command, workingDir, timeoutSeconds));
    }

    // Convenience wrapper (LLM should use execute + executeInDirWithTimeout instead)
    public String executeInDir(String command, String workingDir) {
        String toolInput = command + I18n.tr(MessageKey.SHELL_DIR, workingDir);
        return executeWithStatus("ShellTool", "shell", toolInput,
                new Object[]{command, workingDir},
                () -> doExecuteInDir(command, workingDir));
    }

    private String doExecuteInDir(String command, String workingDir) {
        return doExecuteWithTimeout(command, workingDir, 300);
    }

    @Tool("Execute a shell command in a specific directory with timeout")
    public String executeInDirWithTimeout(String command, String workingDir, int timeoutSeconds) {
        return executeWithStatus("ShellTool", "shell",
                command + I18n.tr(MessageKey.SHELL_DIR, workingDir) + I18n.tr(MessageKey.SHELL_TIMEOUT, timeoutSeconds),
                new Object[]{command, workingDir, timeoutSeconds},
                () -> doExecuteWithTimeout(command, workingDir, timeoutSeconds));
    }

    private String doExecuteWithTimeout(String command, String workingDir, int timeoutSeconds) {
        try {
            String[] cmd;
            if (shellCommand.length == 2) {
                cmd = new String[]{shellCommand[0], shellCommand[1], command};
            } else {
                cmd = new String[]{shellCommand[0], "-c", command};
            }
            ProcessBuilder pb = new ProcessBuilder(cmd)
                    .directory(new java.io.File(workingDir))
                    .redirectErrorStream(true);
            Process process = pb.start();

            final Object outputLock = new Object();
            final StringBuilder output = new StringBuilder();
            Thread outputThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        synchronized (outputLock) {
                            output.append(line).append("\n");
                        }
                    }
                } catch (IOException e) {
                    synchronized (outputLock) {
                        output.append("\n[Error reading output: ").append(e.getMessage()).append("]\n");
                    }
                }
            });
            outputThread.start();

            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
            }
            int exitCode = completed ? process.exitValue() : -1;

            outputThread.join(timeoutSeconds * 1000L);

            boolean truncated;
            synchronized (outputLock) {
                truncated = output.length() > maxOutputLength;
                if (truncated) {
                    truncateSafely(output, maxOutputLength);
                }
            }

            StringBuilder result = new StringBuilder();
            result.append("Exit Code: ").append(exitCode).append("\n");
            result.append("Completed: ").append(completed).append("\n");
            result.append("--- Output ---\n");
            synchronized (outputLock) {
                result.append(output);
            }
            if (outputThread.isAlive()) {
                result.append("\n[Warning: output read may be incomplete]\n");
            }
            if (truncated) {
                result.append("\n... [truncated at ").append(maxOutputLength / 1024).append("KB] ...\n");
            }

            return result.toString().trim();
        } catch (IOException e) {
            return "Error executing command: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Command was interrupted: " + e.getMessage();
        }
    }
}
