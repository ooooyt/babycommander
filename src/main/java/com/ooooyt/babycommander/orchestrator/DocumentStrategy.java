package com.ooooyt.babycommander.orchestrator;

import com.ooooyt.babycommander.agent.AgentContext;
import com.ooooyt.babycommander.agent.AgentFactory;
import com.ooooyt.babycommander.model.AgentRole;
import com.ooooyt.babycommander.model.TaskResult;
import com.ooooyt.babycommander.status.StatusEventPublisher;
import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;

import io.quarkus.logging.Log;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * WRITE-document strategy: runs a document-writer agent and, if the task names
 * a target file, writes the output to disk under {@code docs/} (or an absolute
 * path).
 */
public class DocumentStrategy implements ModeStrategy {

    private static final Pattern TARGET_PATH = Pattern.compile(
        "([\\w./-]+\\.(?:md|txt|yaml|json|xml|html|adoc))");

    private final AgentFactory agentFactory;
    private final StatusEventPublisher statusPublisher;
    private final OrchestratorContext context;

    public DocumentStrategy(AgentFactory agentFactory, StatusEventPublisher statusPublisher,
                            OrchestratorContext context) {
        this.agentFactory = agentFactory;
        this.statusPublisher = statusPublisher;
        this.context = context;
    }

    @Override
    public TaskResult execute(String task, long workflowStart) {
        String sessionId = UUID.randomUUID().toString();
        statusPublisher.stepStarted(sessionId, "document-writer", task);

        AgentContext ctx = agentFactory.createAgent(
            AgentRole.DOCUMENT_WRITER.getValue(), sessionId, context.getProjectFolder()
        );

        int retryCount = 0;
        int maxRetries = 3;

        while (retryCount <= maxRetries) {
            try {
                String output = ctx.agent().chat(task);
                if (output == null || output.isBlank()) {
                    agentFactory.disposeAgent(sessionId);
                    statusPublisher.workflowCompleted("FAILURE", System.currentTimeMillis() - workflowStart);
                    return TaskResult.failure(context.getWorkspace(), I18n.tr(MessageKey.ORCH_AGENT_EMPTY_RESPONSE));
                }
                statusPublisher.agentResponse(sessionId, "document-writer", output.length());
                agentFactory.disposeAgent(sessionId);

                List<String> outputFiles = List.of();
                String targetPath = extractTargetPath(task);
                if (targetPath != null) {
                    writeDocumentFile(targetPath, output);
                    outputFiles = List.of(targetPath);
                }

                long elapsed = System.currentTimeMillis() - workflowStart;
                statusPublisher.stepCompleted(sessionId, "document-writer", elapsed);
                statusPublisher.workflowCompleted("SUCCESS", elapsed);

                return new TaskResult(
                    TaskResult.Status.SUCCESS, output, context.getWorkspace(),
                    outputFiles, List.of()
                );
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                boolean isTextError = errorMsg != null && errorMsg.contains("text cannot be null or blank");
                if (isTextError && ctx.chatMemory() != null) {
                    Log.warnf("Agent error (attempt %d/%d): %s, retrying",
                        retryCount + 1, maxRetries, errorMsg);
                    retryCount++;
                    if (retryCount > maxRetries) {
                        agentFactory.disposeAgent(sessionId);
                        Log.error("Document writing failed", e);
                        statusPublisher.workflowCompleted("FAILURE", System.currentTimeMillis() - workflowStart);
                        return TaskResult.failure(context.getWorkspace(),
                            I18n.tr(MessageKey.ORCH_DOCUMENT_FAILED_MSG, errorMsg));
                    }
                } else {
                    agentFactory.disposeAgent(sessionId);
                    Log.error("Document writing failed", e);
                    statusPublisher.workflowCompleted("FAILURE", System.currentTimeMillis() - workflowStart);
                    return TaskResult.failure(context.getWorkspace(),
                        I18n.tr(MessageKey.ORCH_DOCUMENT_FAILED_MSG, errorMsg));
                }
            }
        }

        agentFactory.disposeAgent(sessionId);
        statusPublisher.workflowCompleted("FAILURE", System.currentTimeMillis() - workflowStart);
        return TaskResult.failure(context.getWorkspace(), I18n.tr(MessageKey.ORCH_UNEXPECTED_ERROR));
    }

    static String extractTargetPath(String task) {
        if (task == null) return null;
        var m = TARGET_PATH.matcher(task);
        if (m.find()) {
            String path = m.group(1);
            if (path.startsWith("/")) return path;
            return "docs/" + path;
        }
        return null;
    }

    void writeDocumentFile(String path, String content) {
        if (path.startsWith("/") || path.startsWith("docs/")) {
            String fullPath = path.startsWith("/") ? path : context.getProjectFolder() + "/" + path;
            try {
                Path docPath = Path.of(fullPath);
                Files.createDirectories(docPath.getParent());
                Files.writeString(docPath, content);
                Log.infof("Document written to: %s", fullPath);
            } catch (java.io.IOException e) {
                Log.errorf("Failed to write document: %s", e.getMessage());
            }
        }
    }
}
