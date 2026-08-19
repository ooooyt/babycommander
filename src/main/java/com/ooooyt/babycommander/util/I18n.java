package com.ooooyt.babycommander.util;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Lightweight i18n helper wrapping java.util.ResourceBundle + MessageFormat.
 * Thread-safe. Loaded once at startup.
 *
 * <p>Usage:
 * <pre>{@code
 *   I18n.tr(MessageKey.CHAT_GOODBYE);
 *   I18n.tr(MessageKey.CHAT_WORKFLOW_TASK, taskName);
 * }</pre>
 */
public final class I18n {

    private static final String BUNDLE_BASE = "i18n.messages";
    private static Locale locale = Locale.getDefault();
    private static ResourceBundle bundle;

    static {
        reload();
    }

    /** Reload the resource bundle for the current locale. */
    public static void reload() {
        bundle = ResourceBundle.getBundle(BUNDLE_BASE, locale);
    }

    /** Override the locale (call before any lookups). */
    public static void setLocale(Locale locale) {
        I18n.locale = locale != null ? locale : Locale.getDefault();
        reload();
    }

    /** Get the current locale. */
    public static Locale getLocale() {
        return locale;
    }

    /**
     * Look up a message by {@link MessageKey} and format with the given arguments.
     * Falls back to the key itself if the resource is missing.
     */
    public static String tr(MessageKey messageKey, Object... args) {
        return tr(messageKey.key(), args);
    }

    /**
     * Look up a message by raw key string and format with the given arguments.
     * Falls back to the key itself if the resource is missing.
     *
     * <p>Prefer {@link #tr(MessageKey, Object...)} for type-safe usage.
     */
    public static String tr(String key, Object... args) {
        try {
            String pattern = bundle.getString(key);
            if (args == null || args.length == 0) {
                return pattern;
            }
            return MessageFormat.format(pattern, args);
        } catch (MissingResourceException e) {
            // Fallback: return the key itself so developers see what's missing
            return "!" + key + "!";
        }
    }

    /** Package-private for testing: reset to default locale. */
    static void reset() {
        locale = Locale.getDefault();
        reload();
    }
}
