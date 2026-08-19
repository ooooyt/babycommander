package com.ooooyt.babycommander.hook;

import com.ooooyt.babycommander.db.entity.HookAnswerEntity;
import com.ooooyt.babycommander.db.repository.HookAnswerRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class SessionMemory {

    private final Map<String, Set<String>> allowedSignatures = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> deniedSignatures = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> allowedMethods = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> trustedPaths = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> allowedPaths = new ConcurrentHashMap<>();

    @Inject
    HookAnswerRepository hookAnswerRepository;

    public static String buildSignature(String toolName, Object[] args) {
        StringBuilder sb = new StringBuilder(toolName);
        sb.append("::");
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if (i > 0) sb.append("|");
                sb.append(args[i] != null ? args[i].toString() : "null");
            }
        }
        return sb.toString();
    }

    /**
     * Load persisted answers from the database into memory for a session.
     * Called when a session starts.
     */
    public void loadSession(String sessionId) {
        if (hookAnswerRepository == null) return;
        List<HookAnswerEntity> answers = hookAnswerRepository.findBySession(sessionId);
        for (HookAnswerEntity answer : answers) {
            switch (answer.answerType) {
                case "ALLOW" -> {
                    allowedSignatures.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                            .add(answer.argsSignature);
                }
                case "DENY" -> {
                    deniedSignatures.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                            .add(answer.argsSignature);
                }
                case "ALLOW_ALWAYS" -> {
                    allowedSignatures.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                            .add(answer.argsSignature);
                    allowedMethods.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                            .add(answer.toolName + "::" + answer.methodName);
                }
                case "PATH_ALLOW_ALWAYS" ->
                    allowedPaths.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                            .add(answer.toolName + "::" + answer.argsSignature);
            }
        }
        if (!answers.isEmpty()) {
            Log.debugf("Loaded %d persisted hook answers for session %s", answers.size(), sessionId);
        }
    }

    /**
     * Check if a specific tool call was previously allowed (y) or always-allowed (a).
     */
    public boolean isAllowedAlways(String sessionId, String toolName, Object[] args) {
        Set<String> signatures = allowedSignatures.get(sessionId);
        if (signatures == null) return false;
        return signatures.contains(buildSignature(toolName, args));
    }

    /**
     * Check if a specific tool call was previously denied (n).
     */
    public boolean isDenied(String sessionId, String toolName, Object[] args) {
        Set<String> signatures = deniedSignatures.get(sessionId);
        if (signatures == null) return false;
        return signatures.contains(buildSignature(toolName, args));
    }

    /**
     * Mark a tool call as allowed (user answered 'y').
     * Persists to database for ASK_ONCE operations.
     */
    public void markAllowed(String sessionId, String toolName, Object[] args) {
        String signature = buildSignature(toolName, args);
        allowedSignatures.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                .add(signature);
        // Persist to database
        try {
            HookAnswerEntity entity = new HookAnswerEntity(sessionId, toolName, "", signature, "ALLOW");
            hookAnswerRepository.save(entity);
        } catch (Exception e) {
            Log.warnf("Failed to persist ALLOW answer: %s", e.getMessage());
        }
    }

    /**
     * Mark a tool call as always-allowed (user answered 'a').
     * Persists to database.
     */
    public void markAllowedAlways(String sessionId, String toolName, String methodName, Object[] args) {
        String signature = buildSignature(toolName, args);
        allowedSignatures.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                .add(signature);
        allowedMethods.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                .add(toolName + "::" + methodName);
        // Persist to database
        try {
            HookAnswerEntity entity = new HookAnswerEntity(sessionId, toolName, methodName, signature, "ALLOW_ALWAYS");
            hookAnswerRepository.save(entity);
        } catch (Exception e) {
            Log.warnf("Failed to persist ALLOW_ALWAYS answer: %s", e.getMessage());
        }
    }

    /**
     * Mark a tool call as denied (user answered 'n').
     * Persists to database for ASK_ONCE operations.
     */
    public void markDenied(String sessionId, String toolName, Object[] args) {
        String signature = buildSignature(toolName, args);
        deniedSignatures.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                .add(signature);
        // Persist to database
        try {
            HookAnswerEntity entity = new HookAnswerEntity(sessionId, toolName, "", signature, "DENY");
            hookAnswerRepository.save(entity);
        } catch (Exception e) {
            Log.warnf("Failed to persist DENY answer: %s", e.getMessage());
        }
    }

    /**
     * Mark method-level always-allow (legacy method for backward compatibility).
     * Delegates to the new method with null args.
     */
    public void markMethodAllowed(String sessionId, String toolName, String methodName) {
        allowedMethods.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                .add(toolName + "::" + methodName);
    }

    public boolean isMethodAllowed(String sessionId, String toolName, String methodName) {
        Set<String> methods = allowedMethods.get(sessionId);
        if (methods == null) return false;
        return methods.contains(toolName + "::" + methodName);
    }

    /**
     * Mark a path-scoped always-allow grant (user answered 'a' for an in-scope
     * FileSystemTool call). The grant is keyed by tool name + normalized path
     * only — other arguments are irrelevant. Persists to database.
     */
    public void markPathAllowedAlways(String sessionId, String toolName, String methodName, String normalizedPath) {
        allowedPaths.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                .add(toolName + "::" + normalizedPath);
        // Persist to database
        try {
            HookAnswerEntity entity = new HookAnswerEntity(sessionId, toolName, methodName != null ? methodName : "", normalizedPath, "PATH_ALLOW_ALWAYS");
            hookAnswerRepository.save(entity);
        } catch (Exception e) {
            Log.warnf("Failed to persist PATH_ALLOW_ALWAYS answer: %s", e.getMessage());
        }
    }

    /**
     * Check if a path-scoped always-allow grant exists for the given tool and
     * normalized path. Matching is exact on the pair (toolName, path).
     */
    public boolean isPathAllowedAlways(String sessionId, String toolName, String normalizedPath) {
        if (normalizedPath == null) return false;
        Set<String> paths = allowedPaths.get(sessionId);
        if (paths == null) return false;
        return paths.contains(toolName + "::" + normalizedPath);
    }

    public void addTrustedPaths(String sessionId, Collection<String> paths) {
        if (paths == null || paths.isEmpty()) return;
        trustedPaths.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                .addAll(paths);
    }

    public boolean isPathTrusted(String sessionId, String normalizedPath) {
        Set<String> paths = trustedPaths.get(sessionId);
        if (paths == null || paths.isEmpty()) return false;
        for (String prefix : paths) {
            if (PathExtractor.isDescendantOrSelf(normalizedPath, prefix)) {
                return true;
            }
        }
        return false;
    }

    public void clearSession(String sessionId) {
        allowedSignatures.remove(sessionId);
        deniedSignatures.remove(sessionId);
        allowedMethods.remove(sessionId);
        trustedPaths.remove(sessionId);
        allowedPaths.remove(sessionId);
        // Also clean up database
        try {
            hookAnswerRepository.deleteBySession(sessionId);
        } catch (Exception e) {
            Log.warnf("Failed to clear persisted answers for session %s: %s", sessionId, e.getMessage());
        }
    }
}
