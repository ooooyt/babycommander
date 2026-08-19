package com.ooooyt.babycommander.util;

import jakarta.enterprise.context.ApplicationScoped;

import java.nio.file.Files;
import java.nio.file.Path;

@ApplicationScoped
public class ProjectScanner {

    public record ProjectInfo(String folder, String language, String buildTool) {}

    public ProjectInfo scan(String workspace) {
        String ws = workspace != null ? workspace : System.getProperty("user.dir");
        Path root = Path.of(ws);
        String language = detectLanguage(root);
        String buildTool = detectBuildTool(root);
        return new ProjectInfo(ws, language, buildTool);
    }

    private String detectLanguage(Path root) {
        String[] patterns = {
            "pom.xml", "build.gradle", "build.gradle.kts",
            "go.mod", "Cargo.toml", "package.json",
            "requirements.txt", "Pipfile", "pyproject.toml"
        };
        String[] languages = {
            "Java", "Java", "Java",
            "Go", "Rust", "JavaScript/Node",
            "Python", "Python", "Python"
        };

        for (int i = 0; i < patterns.length; i++) {
            if (Files.exists(root.resolve(patterns[i]))) {
                return languages[i];
            }
        }

        if (Files.exists(root.resolve("src"))) return "Java";
        if (Files.exists(root.resolve("lib"))) return "C/C++";
        if (Files.exists(root.resolve("include"))) return "C/C++";
        if (Files.exists(root.resolve("public")) || Files.exists(root.resolve("app"))) return "JavaScript";

        return "Unknown";
    }

    private String detectBuildTool(Path root) {
        if (Files.exists(root.resolve("pom.xml"))) return "Maven";
        if (Files.exists(root.resolve("build.gradle")) || Files.exists(root.resolve("build.gradle.kts"))) return "Gradle";
        if (Files.exists(root.resolve("go.mod"))) return "Go build";
        if (Files.exists(root.resolve("Cargo.toml"))) return "Cargo";
        if (Files.exists(root.resolve("package.json"))) return "npm";
        if (Files.exists(root.resolve("CMakeLists.txt"))) return "CMake";
        if (Files.exists(root.resolve("Makefile"))) return "Make";
        return "None detected";
    }

    /**
     * Build the background section for the system prompt, clearly separating
     * the concepts of "workspace" and "project folder":
     * <ul>
     *   <li><b>Workspace</b> - the base directory used when creating a new project
     *       or scanning for existing projects. Not the primary working directory.</li>
     *   <li><b>Project folder</b> - the target root folder for most operations
     *       (code generation, file operations, building). This is where all
     *       tools operate unless the user explicitly asks to create/switch projects.</li>
     * </ul>
     *
     * @param info      the scanned project info (language, build tool)
     * @param workspace the current workspace path
     * @param projectFolder the current project folder path (may differ from workspace)
     * @return a formatted background section string
     */
    public String buildBackgroundSection(ProjectInfo info, String workspace, String projectFolder) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Background\n");
        sb.append("- Workspace: ").append(workspace != null ? workspace : "N/A").append("\n");
        sb.append("- Project folder: ").append(projectFolder != null ? projectFolder : info.folder()).append("\n");
        sb.append("- Language: ").append(info.language()).append("\n");
        sb.append("- Build tool: ").append(info.buildTool()).append("\n");
        sb.append("\n");
        sb.append("### Important: Workspace vs Project Folder\n");
        sb.append("- The **workspace** is only relevant when creating a NEW project from scratch\n");
        sb.append("  or scanning for existing projects. It is a container directory.\n");
        sb.append("- The **project folder** is the root directory for ALL operations including\n");
        sb.append("  code generation, file reading/writing, building, and testing.\n");
        sb.append("- All tools (file operations, shell commands) operate within the project folder.\n");
        sb.append("- To switch to a different project, the user must explicitly ask.\n");
        return sb.toString();
    }

    /**
     * Legacy overload for backward compatibility - treats workspace as project folder.
     */
    public String buildBackgroundSection(ProjectInfo info) {
        return buildBackgroundSection(info, info.folder(), info.folder());
    }
}
