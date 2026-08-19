package com.ooooyt.babycommander.intent;

import com.ooooyt.babycommander.util.I18n;

/**
 * Lightweight intent detector: mode is determined from user input via keyword matching.
 * Uses i18n resource bundles for multi-language support.
 * Used as a fallback when no explicit --mode flag is provided.
 */
public class IntentDetector {

    public enum Mode {
        BUGFIX,
        REFACTOR,
        EXTENSION,
        CREATE,
        DOCUMENT
    }

    private static final String KEY_BUGFIX = "intent.keywords.bugfix";
    private static final String KEY_REFACTOR = "intent.keywords.refactor";
    private static final String KEY_EXTENSION = "intent.keywords.extension";

    private IntentDetector() {}

    /**
     * Detect the execution mode from user input.
     * Checks keywords in priority order: BUGFIX > REFACTOR > EXTENSION > CREATE
     * Uses i18n resource bundles for the current locale.
     * Returns CREATE for null, empty, or unrecognized input.
     */
    public static Mode detect(String input) {
        if (input == null || input.isBlank()) {
            return Mode.CREATE;
        }

        String lower = input.toLowerCase();

        if (matchesAny(lower, getKeywords(KEY_BUGFIX))) {
            return Mode.BUGFIX;
        }

        if (matchesAny(lower, getKeywords(KEY_REFACTOR))) {
            return Mode.REFACTOR;
        }

        if (matchesAny(lower, getKeywords(KEY_EXTENSION))) {
            return Mode.EXTENSION;
        }

        return Mode.CREATE;
    }

    /**
     * Parse --mode flag value into a Mode enum.
     */
    public static Mode fromFlag(String flag) {
        if (flag == null || flag.isBlank()) {
            return null;
        }
        return switch (flag.toLowerCase().trim()) {
            case "bugfix", "bug-fix", "fix" -> Mode.BUGFIX;
            case "refactor", "refactoring" -> Mode.REFACTOR;
            case "extension", "extend", "ext" -> Mode.EXTENSION;
            case "create", "new" -> Mode.CREATE;
            case "document", "doc" -> Mode.DOCUMENT;
            default -> null;
        };
    }

    /**
     * Load keywords from i18n resource bundle for the current locale.
     * Falls back to English defaults if the key is missing.
     */
    private static String[] getKeywords(String key) {
        String value = I18n.tr(key);
        // If the key wasn't found, I18n returns "!key!" format
        if (value == null || value.startsWith("!")) {
            return getFallbackKeywords(key);
        }
        return value.split(",");
    }

    /**
     * Fallback English keywords used when resource bundle lookup fails.
     */
    private static String[] getFallbackKeywords(String key) {
        return switch (key) {
            case KEY_BUGFIX -> new String[]{"fix", "bug", "broken", "debug", "crash", "error in", "fail", "failing"};
            case KEY_REFACTOR -> new String[]{"refactor", "extract", "rename", "clean up", "restructure", "improve code"};
            case KEY_EXTENSION -> new String[]{"add", "extend", "new feature", "implement a new", "support for", "enhance"};
            default -> new String[0];
        };
    }

    /**
     * Check if the input contains any of the given keywords (case-insensitive).
     */
    private static boolean matchesAny(String input, String[] keywords) {
        for (String keyword : keywords) {
            String trimmed = keyword.trim();
            if (!trimmed.isEmpty() && input.contains(trimmed)) {
                return true;
            }
        }
        return false;
    }
}
