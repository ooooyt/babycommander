package com.ooooyt.babycommander.service;

import com.ooooyt.babycommander.db.entity.ProjectEntity;
import com.ooooyt.babycommander.db.entity.TaskEntity;
import com.ooooyt.babycommander.db.entity.TaskToolExecutionEntity;
import com.ooooyt.babycommander.db.repository.ProjectRepository;
import com.ooooyt.babycommander.db.repository.TaskRepository;
import com.ooooyt.babycommander.db.repository.TaskToolExecutionRepository;
import io.objectbox.BoxStore;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing projects, tasks, and tool executions.
 * <p>
 * All data is inserted/updated in strict consistent order with the real time sequence.
 * Each method ensures the database state reflects the actual chronological
 * order of operations.
 */
@ApplicationScoped
public class ProjectTaskService {

    @Inject
    ProjectRepository projectRepository;

    @Inject
    TaskRepository taskRepository;

    @Inject
    TaskToolExecutionRepository taskToolExecutionRepository;

    @Inject
    EmbeddingService embeddingService;

    @Inject
    BoxStore boxStore;

    // ──────────────────────────────────────────────
    // Project operations
    // ──────────────────────────────────────────────

    public ProjectEntity createProject(String name, String path, String languages) {
        ProjectEntity project = new ProjectEntity();
        project.id = UUID.randomUUID().toString();
        project.name = name;
        project.path = path;
        project.languages = languages;
        project.createdDatetime = System.currentTimeMillis();
        boxStore.runInTx(() -> projectRepository.save(project));
        return project;
    }

    public ProjectEntity getProject(String id) {
        return projectRepository.findById(id);
    }

    public List<ProjectEntity> getAllProjects() {
        return projectRepository.findAll();
    }

    /**
     * Ensures a project record exists for the given folder path.
     * <p>
     * Logic:
     * <ol>
     *   <li>Look up active project records by path in ObjectBox.</li>
     *   <li>If none found, create a new project record.</li>
     *   <li>If found, compare the folder's creation time (from filesystem) with
     *       the record's createdDatetime. If the folder was created AFTER the
     *       record, deactivate the old record and insert a new one.</li>
     * </ol>
     *
     * @param projectFolderPath the absolute path of the project folder
     * @return the active (existing or newly created) ProjectEntity
     */
    public ProjectEntity ensureProjectRecord(String projectFolderPath) {
        if (projectFolderPath == null || projectFolderPath.isBlank()) {
            return null;
        }

        // Look up active projects by path
        List<ProjectEntity> activeByPath = projectRepository.findActiveByPath(projectFolderPath);

        if (activeByPath.isEmpty()) {
            // No active record — create one
            String folderName = Path.of(projectFolderPath).getFileName().toString();
            return createProject(folderName, projectFolderPath, "");
        }

        // There is at least one active record. Check if the folder's creation time
        // is newer than the record's createdDatetime.
        ProjectEntity existing = activeByPath.get(0);
        try {
            Path folderPath = Path.of(projectFolderPath);
            if (Files.exists(folderPath)) {
                BasicFileAttributes attrs = Files.readAttributes(folderPath, BasicFileAttributes.class);
                long folderCreationTime = attrs.creationTime().toMillis();

                if (folderCreationTime > existing.createdDatetime) {
                    // Folder was recreated after the record — deactivate old and create new
                    boxStore.runInTx(() -> {
                        projectRepository.deactivate(existing);
                    });
                    String folderName = folderPath.getFileName().toString();
                    return createProject(folderName, projectFolderPath, "");
                }
            }
        } catch (Exception e) {
            // If we can't read attributes, just keep the existing record
        }

        return existing;
    }

    // ──────────────────────────────────────────────
    // Task operations
    // ──────────────────────────────────────────────

    public TaskEntity createTask(String projectId, String name) {
        ProjectEntity project = projectRepository.findById(projectId);
        if (project == null) {
            throw new IllegalArgumentException("Project not found: " + projectId);
        }
        TaskEntity task = new TaskEntity();
        task.id = UUID.randomUUID().toString();
        task.projectId = projectId;
        task.name = name;
        task.createdDatetime = System.currentTimeMillis();
        task.status = TaskEntity.Status.STARTED.name();
        task.updatedDatetime = System.currentTimeMillis();

        // Generate embedding vector for semantic search
        float[] embedding = embeddingService.embed(name);
        if (embedding != null) {
            task.embedding = embedding;
            Log.debugf("Generated embedding for task '%s' (%d dimensions)", name, embedding.length);
        }

        boxStore.runInTx(() -> taskRepository.save(task));
        return task;
    }

    public TaskEntity updateTaskStatus(String taskId, TaskEntity.Status status) {
        TaskEntity task = taskRepository.findById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        task.status = status.name();
        task.updatedDatetime = System.currentTimeMillis();
        boxStore.runInTx(() -> taskRepository.update(task));
        return task;
    }

    /**
     * Renames an existing task and regenerates its semantic-search embedding.
     * Used when the LLM supplies a real task name that replaces the placeholder
     * name derived from the user's raw input.
     *
     * @param taskId  the business ID of the task to rename
     * @param newName the new task name
     * @return the updated TaskEntity
     */
    public TaskEntity renameTask(String taskId, String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("New task name must not be blank: " + taskId);
        }
        TaskEntity task = taskRepository.findById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        task.name = newName;
        task.updatedDatetime = System.currentTimeMillis();
        float[] embedding = embeddingService.embed(newName);
        if (embedding != null) {
            task.embedding = embedding;
        }
        boxStore.runInTx(() -> taskRepository.update(task));
        return task;
    }

    public List<TaskEntity> getProjectTasks(String projectId) {
        return taskRepository.findByProjectIdOrdered(projectId);
    }

    /**
     * Search project tasks by name keyword (case-insensitive).
     *
     * @param projectId the project business ID
     * @param keyword   the keyword to search in task names
     * @return matching tasks ordered by creation time descending
     */
    public List<TaskEntity> searchProjectTasks(String projectId, String keyword) {
        return taskRepository.findByProjectIdAndNameContaining(projectId, keyword);
    }

    /**
     * Semantic search of project tasks using vector similarity.
     * <p>
     * Embeds the query text and performs a nearest-neighbor search
     * on the HNSW vector index of task embeddings. Falls back to
     * keyword search if embedding generation fails.
     *
     * @param projectId the project business ID
     * @param query     the natural language query text
     * @param maxResults maximum number of results to return
     * @return tasks ordered by semantic similarity (most similar first)
     */
    public List<TaskEntity> searchProjectTasksSemantic(String projectId, String query, int maxResults) {
        float[] queryVector = embeddingService.embed(query);
        if (queryVector == null) {
            Log.warn("EmbeddingService returned null, falling back to keyword search");
            return searchProjectTasks(projectId, query);
        }
        return taskRepository.findNearestNeighbors(projectId, queryVector, maxResults);
    }

    // ──────────────────────────────────────────────
    // Tool execution operations
    // ──────────────────────────────────────────────

    public TaskToolExecutionEntity createToolExecution(String taskId, String toolInfo) {
        TaskEntity task = taskRepository.findById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        TaskToolExecutionEntity execution = new TaskToolExecutionEntity();
        execution.id = UUID.randomUUID().toString();
        execution.taskId = taskId;
        execution.toolInfo = toolInfo;
        execution.status = TaskToolExecutionEntity.Status.STARTED.name();
//        execution.createdDatetime = System.currentTimeMillis();
        execution.updatedDatetime = System.currentTimeMillis();
        boxStore.runInTx(() -> taskToolExecutionRepository.save(execution));
        return execution;
    }

    public TaskToolExecutionEntity updateToolExecutionStatus(String executionId, TaskToolExecutionEntity.Status status) {
        TaskToolExecutionEntity execution = taskToolExecutionRepository.findById(executionId);
        if (execution == null) {
            throw new IllegalArgumentException("Tool execution not found: " + executionId);
        }
        execution.status = status.name();
        execution.updatedDatetime = System.currentTimeMillis();
        boxStore.runInTx(() -> taskToolExecutionRepository.update(execution));
        return execution;
    }

    public TaskToolExecutionEntity updateToolExecution(String executionId, String toolInfo, TaskToolExecutionEntity.Status status) {
        TaskToolExecutionEntity execution = taskToolExecutionRepository.findById(executionId);
        if (execution == null) {
            throw new IllegalArgumentException("Tool execution not found: " + executionId);
        }
        execution.toolInfo = toolInfo;
        execution.status = status.name();
        execution.updatedDatetime = System.currentTimeMillis();
        boxStore.runInTx(() -> taskToolExecutionRepository.update(execution));
        return execution;
    }

    public List<TaskToolExecutionEntity> getTaskToolExecutions(String taskId) {
        return taskToolExecutionRepository.findByTaskId(taskId);
    }
}
