package com.ooooyt.babycommander;

import com.ooooyt.babycommander.config.YamlConfigLoader;
import com.ooooyt.babycommander.hook.HookManager;
import com.ooooyt.babycommander.skill.SkillRegistry;
import com.ooooyt.babycommander.tool.FileSystemTool;
import com.ooooyt.babycommander.tool.InternetTool;
import com.ooooyt.babycommander.tool.AskUserTool;
import com.ooooyt.babycommander.tool.InvokeSkillWorkflowTool;
import com.ooooyt.babycommander.tool.PlanTool;
import com.ooooyt.babycommander.tool.ShellTool;
import com.ooooyt.babycommander.tool.ToolRegistry;
import com.ooooyt.babycommander.tool.mcp.McpClientManager;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.logging.Log;
import io.vertx.mutiny.core.eventbus.EventBus;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
public class CodeGenLifecycle {

    @Inject
    ToolRegistry toolRegistry;

    @Inject
    YamlConfigLoader configLoader;

    @Inject
    McpClientManager mcpClientManager;

    @Inject
    HookManager hookManager;

    @Inject
    SkillRegistry skillRegistry;

    @Inject
    EventBus eventBus;

    private volatile boolean initialized = false;

    /**
     * Lazily initialize tools, MCP connections, and skills on first access.
     * This avoids blocking Quarkus startup with tool registration and network calls.
     */
    @PostConstruct
    void ensureInitialized() {
        // Ensure the ~/.babycommander/ and ~/.babycommander/db/ directories exist
        ensureDataDirectories();
    }

    /**
     * Creates the ~/.babycommander/ and ~/.babycommander/db/ directories
     * for storing user preferences, history, and the ObjectBox database file.
     * Also ensures the configured skill directory exists and seeds it with the
     * bundled classpath skills when it does not exist yet.
     */
    private void ensureDataDirectories() {
        try {
            Path dataDir = Paths.get(System.getProperty("user.home"), ".babycommander");
            Path dbDir = dataDir.resolve("db");
            Files.createDirectories(dbDir);
            Log.infof("Ensured data directories exist: %s", dataDir.toAbsolutePath());
        } catch (Exception e) {
            Log.warnf("Failed to create data directories: %s", e.getMessage());
        }

        ensureSkillsDirectory();
    }

    /**
     * Ensures the configured skill directory exists, creating it if necessary.
     * If the directory does not exist, copies the bundled classpath skills
     * (from the "skills" classpath resource) into it so that a fresh install has
     * the default skills available. If the directory already exists, it is left
     * untouched so that a user's skill folder is never overwritten or merged.
     */
    private void ensureSkillsDirectory() {
        Path skillsDir = skillRegistry.getSkillsDir();
        if (skillsDir == null) {
            Log.warnf("No skill directory configured; skipping skill bootstrap");
            return;
        }

        try {
            // Copy the default skills ONLY when the directory does not exist yet.
            // If it already exists (even if empty), it is left untouched so a
            // user's skill folder is never overwritten.
            if (Files.exists(skillsDir)) {
                Log.infof("Skill directory already exists; skipping default bootstrap: %s", skillsDir.toAbsolutePath());
                return;
            }
            Files.createDirectories(skillsDir);
            int copied = copyClasspathSkills(skillsDir);
            Log.infof("Seeded %d skill(s) into %s", copied, skillsDir.toAbsolutePath());
        } catch (Exception e) {
            Log.warnf("Failed to bootstrap skill directory %s: %s", skillsDir, e.getMessage());
        }
    }


    /**
     * Copies the bundled classpath skills (under the "skills" resource path) into
     * the given destination directory. Each skill lives in its own subdirectory
     * containing a SKILL.md file. Handles both filesystem (dev) and jar (packaged)
     * classpath layouts. Returns the number of skill directories copied.
     */
    private int copyClasspathSkills(Path destination) throws IOException {
        int copied = 0;
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = getClass().getClassLoader();
        }

        Enumeration<URL> resources = classLoader.getResources("skills");
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            if ("file".equals(url.getProtocol())) {
                copied += copyFromFileSystem(url, destination);
            } else {
                copied += copyFromJar(url, classLoader, destination);
            }
        }
        return copied;
    }

    /**
     * Copies skill directories from a filesystem classpath location.
     */
    private int copyFromFileSystem(URL url, Path destination) throws IOException {
        int copied = 0;
        try {
            Path sourceRoot = Paths.get(url.toURI());
            if (!Files.isDirectory(sourceRoot)) {
                return copied;
            }
            try (Stream<Path> skillDirs = Files.list(sourceRoot)) {
                for (Path skillDir : skillDirs.toList()) {
                    if (!Files.isDirectory(skillDir)) {
                        continue;
                    }
                    Path target = destination.resolve(skillDir.getFileName().toString());
                    copyDirectory(skillDir, target);
                    copied++;
                }
            }
        } catch (URISyntaxException e) {
            Log.warnf("Invalid classpath skill URL %s: %s", url, e.getMessage());
        }
        return copied;
    }

    /**
     * Copies skill directories from a jar classpath location by enumerating the
     * jar entries under "skills/<skillName>/".
     */
    private int copyFromJar(URL url, ClassLoader classLoader, Path destination) throws IOException {
        int copied = 0;
        String dirPath = url.getPath();
        int bang = dirPath.indexOf("!/");
        if (bang < 0) {
            return copied;
        }
        // The part before "!/" is a URL (e.g. "file:/path/app.jar") that may carry
        // a scheme prefix. Convert it to a filesystem Path so JarFile can open it.
        String jarUrl = dirPath.substring(0, bang);
        Path jarPath;
        try {
            jarPath = Paths.get(new java.net.URI(jarUrl));
        } catch (URISyntaxException e) {
            Log.warnf("Invalid classpath skill jar URL %s: %s", url, e.getMessage());
            return copied;
        }
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarPath.toFile())) {
            Enumeration<java.util.jar.JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith("skills/") || name.equals("skills/")) {
                    continue;
                }
                // A skill directory is "skills/<skillName>/SKILL.md"
                if (name.endsWith("/SKILL.md")) {
                    String skillName = name.substring("skills/".length(), name.lastIndexOf('/'));
                    Path target = destination.resolve(skillName);
                    Files.createDirectories(target);
                    try (InputStream in = classLoader.getResourceAsStream(name)) {
                        if (in != null) {
                            Files.copy(in, target.resolve("SKILL.md"), StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                    copied++;
                }
            }
        }
        return copied;
    }

    /**
     * Recursively copies a directory tree from source to target.
     */
    private void copyDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (Stream<Path> entries = Files.walk(source)) {
            for (Path entry : entries.toList()) {
                Path relative = source.relativize(entry);
                Path dest = target.resolve(relative.toString());
                if (Files.isDirectory(entry)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(entry, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /**
     * Initialize built-in tools, MCP connections, and skills.
     * Called lazily before the first agent interaction.
     * <p>
     * Tools are created with the current working directory (user.dir) as their
     * project folder root. The workspace path is set separately on FileSystemTool
     * so that getWorkspaceFolder() returns the correct workspace container path.
     */
    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        String workspaceRoot = configLoader.getConfig().workspaceRoot;
        // Use user.dir as the initial project folder for tools
        // (ChatMode/Orchestrator will update tools with the correct project folder later)
        String initialProjectFolder = System.getProperty("user.dir", ".");

        int maxOutputLength = configLoader.getConfig().agentDefaults.toolOutputTruncationKB * 1024;

        FileSystemTool fileSystemTool = new FileSystemTool(initialProjectFolder, hookManager);
        fileSystemTool.setWorkspace(workspaceRoot);
        fileSystemTool.setMaxOutputLength(maxOutputLength);
        toolRegistry.registerTool(fileSystemTool);

        ShellTool shellTool = new ShellTool(initialProjectFolder, hookManager);
        shellTool.setMaxOutputLength(maxOutputLength);
        toolRegistry.registerTool(shellTool);

        InternetTool internetTool = new InternetTool(hookManager);
        internetTool.setMaxOutputLength(maxOutputLength);
        toolRegistry.registerTool(internetTool);

        PlanTool planTool = new PlanTool(eventBus);
        toolRegistry.registerTool(planTool);

        AskUserTool askUserTool = new AskUserTool(eventBus);
        toolRegistry.registerTool(askUserTool);

        Log.infof("Registered %d built-in tools", toolRegistry.getAllTools().size());

        var mcpConfig = configLoader.getConfig().mcp;
        if (mcpConfig != null && mcpConfig.servers != null) {
            mcpConfig.servers.forEach((id, serverConfig) -> {
                try {
                    mcpClientManager.connect(id, serverConfig);
                    var mcpTools = mcpClientManager.getTools(id);
                    List<Object> mcpToolsAsObjects = mcpTools.stream().collect(Collectors.toList());
                    toolRegistry.registerMcpTools(mcpToolsAsObjects);
                    Log.infof("Connected to MCP server '%s' with %d tools", id, mcpTools.size());
                } catch (Exception e) {
                    Log.warnf(e, "Failed to connect to MCP server '%s'", id);
                }
            });
        }

        // SkillRegistry is CDI-injected and reads skillsDir from config automatically
        var loaded = skillRegistry.loadAllSkills();
        var skillsDir = configLoader.getConfig().skillsDir;
        if (skillsDir != null && !loaded.isEmpty()) {
            Log.infof("Loaded %d skills from %s", loaded.size(), skillsDir);
        }

        // Expose the skill-workflow invocation tool only when at least one skill
        // defines a multi-agent workflow. Skill selection is driven in-context:
        // the LLM sees the skill catalog in the system prompt and calls this
        // tool when the user's task semantically matches a listed skill.
        if (!skillRegistry.getSkillsWithWorkflow().isEmpty()) {
            toolRegistry.registerTool(new InvokeSkillWorkflowTool());
            Log.infof("Registered InvokeSkillWorkflowTool (%d skill workflows available)",
                skillRegistry.getSkillsWithWorkflow().size());
        }
    }
}
