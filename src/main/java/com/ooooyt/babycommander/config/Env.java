package com.ooooyt.babycommander.config;

import io.quarkus.logging.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Typed environment-variable resolution with {@code .env} file support.
 *
 * <p>Lookup precedence (low → high): YAML default → {@code ~/.babycommander/.env}
 * → process environment → system property. An empty string is treated as
 * "unset" so it falls back to the next source / default (bash
 * {@code ${VAR:-default}} semantics).</p>
 *
 * <p>Secrets are loaded from {@code ~/.babycommander/.env} only — never from a
 * project-local {@code .env} (to avoid leaking keys into a git repository).
 * The file must not be group/world readable (0600); a startup warning is
 * emitted otherwise.</p>
 */
public final class Env {

    private static final Map<String, String> DOT_ENV = new HashMap<>();
    private static final Set<String> WARNED_ALIASES = new HashSet<>();
    private static volatile boolean dotEnvLoaded = false;

    private Env() {}

    /**
     * Clears cached .env state and deprecation warnings. Test-only hook.
     */
    public static synchronized void resetForTests() {
        DOT_ENV.clear();
        WARNED_ALIASES.clear();
        dotEnvLoaded = false;
    }

    // ------------------------------------------------------------------
    // .env file support
    // ------------------------------------------------------------------

    /**
     * Loads {@code ~/.babycommander/.env} (if present) into the lookup map.
     * Called once at config load time. Checks file permissions (POSIX only)
     * and warns if the file is group/world readable.
     */
    public static synchronized void loadDotEnv() {
        if (dotEnvLoaded) {
            return;
        }
        dotEnvLoaded = true;
        Path home = Path.of(System.getProperty("user.home", "."));
        Path envFile = home.resolve(".babycommander").resolve(".env");
        if (!Files.exists(envFile)) {
            return;
        }
        checkPermissions(envFile);
        try {
            int count = 0;
            for (String rawLine : Files.readAllLines(envFile)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    Log.warnf("Ignoring malformed line in %s: %s", envFile, rawLine);
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                value = stripQuotes(value);
                if (key.isEmpty()) {
                    continue;
                }
                DOT_ENV.put(key, value);
                count++;
            }
            Log.infof("Loaded %d variable(s) from %s", count, envFile);
        } catch (IOException e) {
            Log.warnf("Failed to read %s: %s", envFile, e.getMessage());
        }
    }

    private static void checkPermissions(Path envFile) {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(envFile);
            if (perms.contains(PosixFilePermission.GROUP_READ)
                    || perms.contains(PosixFilePermission.GROUP_WRITE)
                    || perms.contains(PosixFilePermission.OTHERS_READ)
                    || perms.contains(PosixFilePermission.OTHERS_WRITE)) {
                Log.warnf("%s is too open (permissions %s). Run: chmod 600 %s",
                        envFile, perms, envFile);
            }
        } catch (UnsupportedOperationException | IOException e) {
            // Non-POSIX filesystem or unreadable — skip the permission check.
        }
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    /**
     * Emits a startup warning if a {@code .env} file exists inside the current
     * working directory (a common git-leak vector). Secrets belong in
     * {@code ~/.babycommander/.env}.
     */
    public static void warnIfDotEnvInProject() {
        Path env = Path.of(System.getProperty("user.dir", ".")).resolve(".env");
        if (Files.exists(env)) {
            Log.warnf("Found %s in the project directory. For safety, move secrets to "
                    + "~/.babycommander/.env and never commit .env to git.", env);
        }
    }

    // ------------------------------------------------------------------
    // Lookup
    // ------------------------------------------------------------------

    /**
     * Resolves a variable: system property → process env → {@code .env} → null.
     * Empty values are treated as unset (return {@code null}).
     */
    public static String get(String key) {
        String sysProp = System.getProperty(key);
        if (sysProp != null && !sysProp.isEmpty()) {
            return sysProp;
        }
        String env = System.getenv(key);
        if (env != null && !env.isEmpty()) {
            return env;
        }
        String dot = DOT_ENV.get(key);
        if (dot != null && !dot.isEmpty()) {
            return dot;
        }
        return null;
    }

    /** Resolves a variable, falling back to {@code defaultValue} when unset/empty. */
    public static String getOr(String key, String defaultValue) {
        String v = get(key);
        return v != null ? v : defaultValue;
    }

    /**
     * Resolves a variable with deprecated aliases. The primary key wins; if it
     * is unset, each alias is tried in order (first hit wins and emits a
     * one-time deprecation warning). Falls back to {@code defaultValue}.
     */
    public static String getWithAliases(String primary, String[] aliases, String defaultValue) {
        String v = get(primary);
        if (v != null) {
            return v;
        }
        if (aliases != null) {
            for (String alias : aliases) {
                v = get(alias);
                if (v != null) {
                    warnDeprecated(alias, primary);
                    return v;
                }
            }
        }
        return defaultValue;
    }

    /** Typed int resolution with empty→default semantics. */
    public static int getInt(String key, int defaultValue) {
        String v = get(key);
        if (v == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            Log.warnf("Invalid integer for %s: '%s' (using default %d)", key, v, defaultValue);
            return defaultValue;
        }
    }

    /** Typed int resolution with deprecated aliases. */
    public static int getIntWithAliases(String primary, String[] aliases, int defaultValue) {
        String v = get(primary);
        if (v != null) {
            return parseInt(primary, v, defaultValue);
        }
        if (aliases != null) {
            for (String alias : aliases) {
                v = get(alias);
                if (v != null) {
                    warnDeprecated(alias, primary);
                    return parseInt(alias, v, defaultValue);
                }
            }
        }
        return defaultValue;
    }

    private static int parseInt(String key, String v, int defaultValue) {
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            Log.warnf("Invalid integer for %s: '%s' (using default %d)", key, v, defaultValue);
            return defaultValue;
        }
    }

    /** Typed double resolution with empty→default semantics. */
    public static double getDouble(String key, double defaultValue) {
        String v = get(key);
        if (v == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            Log.warnf("Invalid number for %s: '%s' (using default %s)", key, v, defaultValue);
            return defaultValue;
        }
    }

    /** Typed double resolution with deprecated aliases. */
    public static double getDoubleWithAliases(String primary, String[] aliases, double defaultValue) {
        String v = get(primary);
        if (v != null) {
            return parseDouble(primary, v, defaultValue);
        }
        if (aliases != null) {
            for (String alias : aliases) {
                v = get(alias);
                if (v != null) {
                    warnDeprecated(alias, primary);
                    return parseDouble(alias, v, defaultValue);
                }
            }
        }
        return defaultValue;
    }

    private static double parseDouble(String key, String v, double defaultValue) {
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            Log.warnf("Invalid number for %s: '%s' (using default %s)", key, v, defaultValue);
            return defaultValue;
        }
    }

    /** Typed boolean resolution; accepts true/false/1/0/yes/no (case-insensitive). */
    public static boolean getBoolean(String key, boolean defaultValue) {
        String v = get(key);
        if (v == null) {
            return defaultValue;
        }
        return parseBoolean(key, v, defaultValue);
    }

    /** Typed boolean resolution with deprecated aliases. */
    public static boolean getBooleanWithAliases(String primary, String[] aliases, boolean defaultValue) {
        String v = get(primary);
        if (v != null) {
            return parseBoolean(primary, v, defaultValue);
        }
        if (aliases != null) {
            for (String alias : aliases) {
                v = get(alias);
                if (v != null) {
                    warnDeprecated(alias, primary);
                    return parseBoolean(alias, v, defaultValue);
                }
            }
        }
        return defaultValue;
    }

    private static boolean parseBoolean(String key, String v, boolean defaultValue) {
        switch (v.trim().toLowerCase()) {
            case "true": case "1": case "yes": case "on":
                return true;
            case "false": case "0": case "no": case "off":
                return false;
            default:
                Log.warnf("Invalid boolean for %s: '%s' (using default %s)", key, v, defaultValue);
                return defaultValue;
        }
    }

    // ------------------------------------------------------------------
    // Diagnostics
    // ------------------------------------------------------------------

    /** Redacts a secret for logging: keeps the first 4 chars, then {@code ***}. */
    public static String redact(String value) {
        if (value == null || value.isEmpty()) {
            return "(unset)";
        }
        if (value.length() <= 4) {
            return "***";
        }
        return value.substring(0, 4) + "***";
    }

    private static void warnDeprecated(String alias, String primary) {
        if (WARNED_ALIASES.add(alias)) {
            Log.warnf("Environment variable '%s' is deprecated; use '%s' instead.", alias, primary);
        }
    }
}