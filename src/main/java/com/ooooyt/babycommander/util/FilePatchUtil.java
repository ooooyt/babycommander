package com.ooooyt.babycommander.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for applying line-based patches to files.
 * Supports adding, replacing, and deleting lines without rewriting the entire file.
 * This is designed to save tokens compared to writing the full file content.
 * <p>
 * Patch format (each operation is one or more lines):
 * <pre>
 * @@ -startLine[,count] [+startLine[,count]] [operation]
 *   content
 * </pre>
 * Where operation is one of:
 * <ul>
 *   <li>{@code A} — Append after the given line</li>
 *   <li>{@code I} — Insert before the given line</li>
 *   <li>{@code R} — Replace the given range of lines</li>
 *   <li>{@code D} — Delete the given range of lines</li>
 * </ul>
 * <p>
 * Examples:
 * <pre>
 * // Replace lines 10-12 with new content
 * @@ -10,3 +10,3 R
 * new line 10 content
 * new line 11 content
 * new line 12 content
 *
 * // Insert before line 5
 * @@ -5 +5 I
 * inserted line 1
 * inserted line 2
 *
 * // Append after line 20
 * @@ -20 +20 A
 * appended line 1
 *
 * // Delete lines 15-18
 * @@ -15,4 +15,0 D
 * </pre>
 */
public class FilePatchUtil {

    private static final Pattern PATCH_HEADER = Pattern.compile(
            "^@@\\s+-(\\d+)(?:,(\\d+))?\\s+\\+(\\d+)(?:,(\\d+))?\\s+([A-Z])\\s*$"
    );

    /**
     * Apply a series of patch operations to a file.
     * The file is modified in-place.
     *
     * @param filePath path to the file to patch
     * @param patches  one or more patch operation strings
     * @return a summary of what was changed
     * @throws IOException if the file cannot be read or written
     */
    public String applyPatches(String filePath, String... patches) throws IOException {
        Path path = Paths.get(filePath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            return I18n.tr(MessageKey.FS_FILE_NOT_FOUND, path);
        }

        List<String> lines = Files.readAllLines(path);
        List<String> originalLines = new ArrayList<>(lines);
        StringBuilder summary = new StringBuilder();

        for (int i = 0; i < patches.length; i++) {
            String patch = patches[i];
            if (patch == null || patch.isBlank()) {
                continue;
            }

            try {
                PatchResult result = applySinglePatch(lines, patch);
                lines = result.lines;
                if (summary.length() > 0) {
                    summary.append("\n");
                }
                summary.append(I18n.tr(MessageKey.PATCH_SUMMARY_PREFIX, (i + 1))).append(" ").append(result.description);
            } catch (Exception e) {
                // Restore original lines on failure
                return I18n.tr(MessageKey.PATCH_APPLY_ERROR_SIMPLE, (i + 1), e.getMessage()) + ". File has been restored to original state.";
            }
        }

        // Write the modified lines back
        Files.write(path, lines);

        int linesChanged = Math.abs(lines.size() - originalLines.size());
        summary.insert(0, I18n.tr(MessageKey.PATCH_APPLY_SUCCESS, patches.length, filePath, (linesChanged >= 0 ? "+" : "") + linesChanged) + "\n");

        return summary.toString();
    }

    /**
     * Apply a single patch operation to a list of lines.
     */
    private PatchResult applySinglePatch(List<String> lines, String patch) {
        String[] patchLines = patch.split("\n", -1);
        if (patchLines.length == 0) {
            throw new IllegalArgumentException(I18n.tr(MessageKey.PATCH_EMPTY));
        }

        // Parse the header line
        Matcher matcher = PATCH_HEADER.matcher(patchLines[0].trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(I18n.tr(MessageKey.PATCH_INVALID_HEADER, patchLines[0]));
        }

        int oldStart = Integer.parseInt(matcher.group(1));
        int oldCount = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 1;
        int newStart = Integer.parseInt(matcher.group(3));
        int newCount = matcher.group(4) != null ? Integer.parseInt(matcher.group(4)) : 1;
        String operation = matcher.group(5);

        // Collect content lines (skip the header)
        List<String> content = new ArrayList<>();
        for (int i = 1; i < patchLines.length; i++) {
            content.add(patchLines[i]);
        }

        // Validate line numbers (1-indexed in patch, convert to 0-indexed)
        int oldStartIdx = oldStart - 1;
        int oldEndIdx = oldStartIdx + oldCount;

        if (oldStartIdx < 0 || oldStartIdx > lines.size()) {
            throw new IllegalArgumentException(I18n.tr(MessageKey.PATCH_START_OUT_OF_RANGE, oldStart, lines.size()));
        }
        if (oldEndIdx > lines.size()) {
            throw new IllegalArgumentException(I18n.tr(MessageKey.PATCH_END_OUT_OF_RANGE, oldStart + oldCount - 1, lines.size()));
        }

        List<String> newLines = new ArrayList<>(lines);
        String description;

        switch (operation) {
            case "A": // Append after line
                if (oldStartIdx >= lines.size()) {
                    // Append to end of file
                    newLines.addAll(content);
                } else {
                    newLines.addAll(oldStartIdx + 1, content);
                }
                description = I18n.tr(MessageKey.PATCH_APPEND_DESC, content.size(), oldStart);
                break;

            case "I": // Insert before line
                newLines.addAll(oldStartIdx, content);
                description = I18n.tr(MessageKey.PATCH_INSERT_DESC, content.size(), oldStart);
                break;

            case "R": // Replace range
                // Remove old lines
                for (int i = 0; i < oldCount; i++) {
                    newLines.remove(oldStartIdx);
                }
                // Insert new content at the same position
                newLines.addAll(oldStartIdx, content);
                description = I18n.tr(MessageKey.PATCH_REPLACE_DESC, oldCount, oldStart, content.size());
                break;

            case "D": // Delete range
                for (int i = 0; i < oldCount; i++) {
                    newLines.remove(oldStartIdx);
                }
                description = I18n.tr(MessageKey.PATCH_DELETE_DESC, oldCount, oldStart);
                break;

            default:
                throw new IllegalArgumentException(I18n.tr(MessageKey.PATCH_UNKNOWN_OP, operation));
        }

        return new PatchResult(newLines, description);
    }

    /**
     * Apply a patch in unified diff format (similar to standard diff/patch).
     * This is a simplified implementation that handles basic line-level changes.
     * <p>
     * Format:
     * <pre>
     * --- a/filepath
     * +++ b/filepath
     * @@ -start,count +start,count @@
     *  context line (unchanged, prefixed with space)
     * -removed line
     * +added line
     * </pre>
     *
     * @param filePath  path to the file to patch
     * @param unifiedDiff the unified diff content
     * @return a summary of changes
     * @throws IOException if the file cannot be read or written
     */
    public String applyUnifiedDiff(String filePath, String unifiedDiff) throws IOException {
        Path path = Paths.get(filePath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            return I18n.tr(MessageKey.FS_FILE_NOT_FOUND, path);
        }

        List<String> lines = Files.readAllLines(path);
        List<String> diffLines = List.of(unifiedDiff.split("\n", -1));

        // Parse the diff into hunks
        List<DiffHunk> hunks = parseUnifiedDiff(diffLines);
        if (hunks.isEmpty()) {
            return I18n.tr(MessageKey.PATCH_NO_HUNKS);
        }

        // Apply hunks in reverse order (bottom-up) to preserve line numbers
        // First, validate all hunks
        for (DiffHunk hunk : hunks) {
            int startIdx = hunk.oldStart - 1;
            int endIdx = startIdx + hunk.oldCount;
            if (startIdx < 0 || endIdx > lines.size()) {
                return I18n.tr(MessageKey.PATCH_HUNK_OUT_OF_RANGE, hunk.oldStart, lines.size());
            }
            // Verify context lines match
            List<String> fileSegment = lines.subList(startIdx, endIdx);
            String mismatch = hunk.verifyContext(fileSegment);
            if (mismatch != null) {
                return I18n.tr(MessageKey.PATCH_CONTEXT_MISMATCH, hunk.oldStart, mismatch);
            }
        }

        // Apply hunks in reverse order
        StringBuilder summary = new StringBuilder();
        for (int i = hunks.size() - 1; i >= 0; i--) {
            DiffHunk hunk = hunks.get(i);
            int startIdx = hunk.oldStart - 1;

            // Remove old lines
            for (int j = 0; j < hunk.oldCount; j++) {
                lines.remove(startIdx);
            }
            // Insert new lines
            lines.addAll(startIdx, hunk.newLines);
            summary.append(I18n.tr(MessageKey.PATCH_HUNK_APPLIED, hunk.oldStart, hunk.oldCount, hunk.newLines.size())).append("\n");
        }

        // Write back
        Files.write(path, lines);

        return I18n.tr(MessageKey.PATCH_SUCCESS_HUNKS, hunks.size(), filePath, summary.toString().trim());
    }

    /**
     * Parse a unified diff into hunks.
     */
    private List<DiffHunk> parseUnifiedDiff(List<String> diffLines) {
        List<DiffHunk> hunks = new ArrayList<>();
        DiffHunk currentHunk = null;

        Pattern hunkHeader = Pattern.compile("^@@\\s+-(\\d+)(?:,(\\d+))?\\s+\\+(\\d+)(?:,(\\d+))?\\s+@@");

        for (String line : diffLines) {
            Matcher matcher = hunkHeader.matcher(line);
            if (matcher.matches()) {
                if (currentHunk != null) {
                    hunks.add(currentHunk);
                }
                int oldStart = Integer.parseInt(matcher.group(1));
                int oldCount = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 1;
                int newStart = Integer.parseInt(matcher.group(3));
                int newCount = matcher.group(4) != null ? Integer.parseInt(matcher.group(4)) : 1;
                currentHunk = new DiffHunk(oldStart, oldCount, newStart, newCount);
            } else if (currentHunk != null) {
                if (line.startsWith("+")) {
                    currentHunk.addNewLine(line.substring(1));
                } else if (line.startsWith("-")) {
                    currentHunk.addRemoveLine(line.substring(1));
                } else if (line.startsWith(" ")) {
                    // Context line: exists in both old and new
                    currentHunk.addContextLine(line.substring(1));
                }
                // Skip lines that don't match expected format (e.g., ---/+++ headers)
            }
        }

        if (currentHunk != null) {
            hunks.add(currentHunk);
        }

        return hunks;
    }

    /**
     * Result of applying a single patch operation.
     */
    private static class PatchResult {
        final List<String> lines;
        final String description;

        PatchResult(List<String> lines, String description) {
            this.lines = lines;
            this.description = description;
        }
    }

    /**
     * Represents a single hunk in a unified diff.
     */
    private static class DiffHunk {
        final int oldStart;
        final int oldCount;
        @SuppressWarnings("unused")
        final int newStart;
        @SuppressWarnings("unused")
        final int newCount;
        final List<String> newLines = new ArrayList<>();
        private final List<String> removeLines = new ArrayList<>();
        private int contextBeforeCount = 0;
        private int contextAfterCount = 0;

        DiffHunk(int oldStart, int oldCount, int newStart, int newCount) {
            this.oldStart = oldStart;
            this.oldCount = oldCount;
            this.newStart = newStart;
            this.newCount = newCount;
        }

        void addContextLine(String line) {
            newLines.add(line);
            removeLines.add(line);
        }

        void addRemoveLine(String line) {
            removeLines.add(line);
        }

        void addNewLine(String line) {
            newLines.add(line);
        }

        /**
         * Verify that the context lines in this hunk match the given file segment.
         * Returns null if all context lines match, or an error message if not.
         */
        String verifyContext(List<String> fileSegment) {
            // We need to match the removal pattern (context + removed lines) against fileSegment
            int fileIdx = 0;
            for (String removeLine : removeLines) {
                if (fileIdx >= fileSegment.size()) {
                    return I18n.tr(MessageKey.PATCH_SEGMENT_TOO_SHORT, removeLine);
                }
                String fileLine = fileSegment.get(fileIdx);
                if (!fileLine.equals(removeLine)) {
                    return I18n.tr(MessageKey.PATCH_LINE_MISMATCH, removeLine, fileLine);
                }
                fileIdx++;
            }
            return null;
        }
    }
}
