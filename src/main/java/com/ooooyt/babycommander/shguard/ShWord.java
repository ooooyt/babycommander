package com.ooooyt.babycommander.shguard;

import java.util.ArrayList;
import java.util.List;

/**
 * Model of a single logical shell word after dequoting.
 *
 * <p>A shell word may be composed of several raw fragments (e.g. {@code r"m"},
 * {@code a"b"c}, {@code *.java}) that the parser merges by source adjacency.
 * {@code ShWord} then splits the raw text into typed parts (literal, variable,
 * command substitution, arithmetic) so the semantic pass can:</p>
 * <ul>
 *   <li>dequote the word ({@code r"m"} → {@code rm}) to defeat quoted/escaped
 *       command-name obfuscation;</li>
 *   <li>detect expansion parts ({@code $VAR}, {@code $(...)}) that make a
 *       command name unresolvable at analysis time.</li>
 * </ul>
 */
public final class ShWord {

    /** Kind of a word part. */
    public enum PartKind {
        LITERAL, VARIABLE, COMMAND_SUBST, ARITHMETIC
    }

    /** A single typed part of the word. */
    public record Part(PartKind kind, String text, boolean wasQuoted, boolean wasEscaped) {
    }

    private final String raw;
    private final List<Part> parts;

    private ShWord(String raw, List<Part> parts) {
        this.raw = raw;
        this.parts = List.copyOf(parts);
    }

    public String raw() {
        return raw;
    }

    public List<Part> parts() {
        return parts;
    }

    /**
     * Parse a raw word fragment sequence into a {@code ShWord}.
     *
     * @param raw the concatenated raw text of adjacent fragments
     */
    public static ShWord parse(String raw) {
        return new ShWord(raw, splitParts(raw));
    }

    /**
     * Dequoted text: quotes and escapes removed, expansions retained as their
     * original {@code $...} spellings. Fully-static words dequote to their
     * literal value; words with expansions keep the expansion markers so the
     * caller can tell the name is not statically resolvable.
     */
    public String dequoted() {
        StringBuilder sb = new StringBuilder();
        for (Part p : parts) {
            if (p.kind() == PartKind.LITERAL) {
                sb.append(p.text());
            } else {
                sb.append(p.text());
            }
        }
        return sb.toString();
    }

    /**
     * {@code true} when the word contains no variable, command-substitution,
     * or arithmetic parts — i.e. its dequoted value is fully known statically.
     */
    public boolean isFullyStatic() {
        for (Part p : parts) {
            if (p.kind() != PartKind.LITERAL) {
                return false;
            }
        }
        return true;
    }

    /** {@code true} when any part of the word was quoted or escaped. */
    public boolean anyPartQuoted() {
        for (Part p : parts) {
            if (p.wasQuoted() || p.wasEscaped()) {
                return true;
            }
        }
        return false;
    }

    /** {@code true} when the word contains a command-substitution part. */
    public boolean hasCommandSubstitution() {
        for (Part p : parts) {
            if (p.kind() == PartKind.COMMAND_SUBST) {
                return true;
            }
        }
        return false;
    }

    /** {@code true} when the word contains a variable part. */
    public boolean hasVariable() {
        for (Part p : parts) {
            if (p.kind() == PartKind.VARIABLE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extract the inner command text of the first command-substitution part,
     * or {@code null} when the word has none.
     */
    public String commandSubstitutionBody() {
        for (Part p : parts) {
            if (p.kind() == PartKind.COMMAND_SUBST) {
                String t = p.text();
                if (t.startsWith("$(")) {
                    return t.substring(2, t.length() - 1);
                }
                if (t.startsWith("`")) {
                    return t.substring(1, t.length() - 1);
                }
            }
        }
        return null;
    }

    // ====================================================================
    // Raw-text splitting
    // ====================================================================

    private static List<Part> splitParts(String raw) {
        List<Part> out = new ArrayList<>();
        int i = 0;
        int n = raw.length();
        while (i < n) {
            char c = raw.charAt(i);

            // Single-quoted literal
            if (c == '\'') {
                int end = raw.indexOf('\'', i + 1);
                if (end < 0) {
                    out.add(new Part(PartKind.LITERAL, raw.substring(i), true, false));
                    break;
                }
                out.add(new Part(PartKind.LITERAL, raw.substring(i + 1, end), true, false));
                i = end + 1;
                continue;
            }

            // ANSI-C quoted literal $'...'
            if (c == '$' && i + 1 < n && raw.charAt(i + 1) == '\'') {
                int end = raw.indexOf('\'', i + 2);
                if (end < 0) {
                    out.add(new Part(PartKind.LITERAL, raw.substring(i), true, false));
                    break;
                }
                out.add(new Part(PartKind.LITERAL, decodeAnsiC(raw.substring(i + 2, end)), true, false));
                i = end + 1;
                continue;
            }

            // Double-quoted: literals + expansions
            if (c == '"') {
                int end = raw.indexOf('"', i + 1);
                if (end < 0) {
                    out.add(new Part(PartKind.LITERAL, raw.substring(i), true, false));
                    break;
                }
                splitDoubleQuoted(raw.substring(i + 1, end), out);
                i = end + 1;
                continue;
            }

            // Backslash escape
            if (c == '\\' && i + 1 < n) {
                out.add(new Part(PartKind.LITERAL, String.valueOf(raw.charAt(i + 1)), false, true));
                i += 2;
                continue;
            }
            if (c == '\\') {
                out.add(new Part(PartKind.LITERAL, "\\", false, true));
                i++;
                continue;
            }

            // Command substitution $(...)
            if (c == '$' && i + 1 < n && raw.charAt(i + 1) == '(') {
                int end = findMatchingParen(raw, i + 1);
                if (end >= 0) {
                    out.add(new Part(PartKind.COMMAND_SUBST, raw.substring(i, end + 1), false, false));
                    i = end + 1;
                    continue;
                }
            }

            // Arithmetic expansion $((...))
            if (c == '$' && i + 2 < n && raw.charAt(i + 1) == '(' && raw.charAt(i + 2) == '(') {
                int end = findMatchingParen(raw, i + 1);
                if (end >= 0) {
                    out.add(new Part(PartKind.ARITHMETIC, raw.substring(i, end + 1), false, false));
                    i = end + 1;
                    continue;
                }
            }

            // Backtick command substitution
            if (c == '`') {
                int end = raw.indexOf('`', i + 1);
                if (end >= 0) {
                    out.add(new Part(PartKind.COMMAND_SUBST, raw.substring(i, end + 1), false, false));
                    i = end + 1;
                    continue;
                }
            }

            // Variable expansion $NAME / ${NAME} / $?
            if (c == '$' && i + 1 < n) {
                char nxt = raw.charAt(i + 1);
                if (nxt == '{') {
                    int end = raw.indexOf('}', i + 2);
                    if (end >= 0) {
                        out.add(new Part(PartKind.VARIABLE, raw.substring(i, end + 1), false, false));
                        i = end + 1;
                        continue;
                    }
                } else if (Character.isLetter(nxt) || nxt == '_') {
                    int j = i + 1;
                    while (j < n && (Character.isLetterOrDigit(raw.charAt(j)) || raw.charAt(j) == '_')) {
                        j++;
                    }
                    out.add(new Part(PartKind.VARIABLE, raw.substring(i, j), false, false));
                    i = j;
                    continue;
                } else if ("?#@*$!".indexOf(nxt) >= 0 || Character.isDigit(nxt)) {
                    out.add(new Part(PartKind.VARIABLE, raw.substring(i, i + 2), false, false));
                    i += 2;
                    continue;
                }
            }

            // Plain literal character
            out.add(new Part(PartKind.LITERAL, String.valueOf(c), false, false));
            i++;
        }
        return out;
    }

    private static void splitDoubleQuoted(String inner, List<Part> out) {
        int i = 0;
        int n = inner.length();
        while (i < n) {
            char c = inner.charAt(i);
            if (c == '\\' && i + 1 < n) {
                char nxt = inner.charAt(i + 1);
                if ("\\\"$`".indexOf(nxt) >= 0) {
                    out.add(new Part(PartKind.LITERAL, String.valueOf(nxt), true, true));
                    i += 2;
                    continue;
                }
                out.add(new Part(PartKind.LITERAL, "\\", true, true));
                i++;
                continue;
            }
            if (c == '$' && i + 1 < n) {
                char nxt = inner.charAt(i + 1);
                if (nxt == '(') {
                    int end = findMatchingParen(inner, i + 1);
                    if (end >= 0) {
                        out.add(new Part(PartKind.COMMAND_SUBST, inner.substring(i, end + 1), true, false));
                        i = end + 1;
                        continue;
                    }
                } else if (nxt == '{') {
                    int end = inner.indexOf('}', i + 2);
                    if (end >= 0) {
                        out.add(new Part(PartKind.VARIABLE, inner.substring(i, end + 1), true, false));
                        i = end + 1;
                        continue;
                    }
                } else if (Character.isLetter(nxt) || nxt == '_') {
                    int j = i + 1;
                    while (j < n && (Character.isLetterOrDigit(inner.charAt(j)) || inner.charAt(j) == '_')) {
                        j++;
                    }
                    out.add(new Part(PartKind.VARIABLE, inner.substring(i, j), true, false));
                    i = j;
                    continue;
                } else if ("?#@*$!".indexOf(nxt) >= 0 || Character.isDigit(nxt)) {
                    out.add(new Part(PartKind.VARIABLE, inner.substring(i, i + 2), true, false));
                    i += 2;
                    continue;
                }
            }
            if (c == '`') {
                int end = inner.indexOf('`', i + 1);
                if (end >= 0) {
                    out.add(new Part(PartKind.COMMAND_SUBST, inner.substring(i, end + 1), true, false));
                    i = end + 1;
                    continue;
                }
            }
            out.add(new Part(PartKind.LITERAL, String.valueOf(c), true, false));
            i++;
        }
    }

    private static int findMatchingParen(String s, int openIdx) {
        int depth = 0;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String decodeAnsiC(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char nxt = s.charAt(i + 1);
                switch (nxt) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'a' -> sb.append('\007');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'v' -> sb.append('\013');
                    case '\\' -> sb.append('\\');
                    case '\'' -> sb.append('\'');
                    case '"' -> sb.append('"');
                    case 'x' -> {
                        if (i + 3 < s.length()) {
                            try {
                                sb.append((char) Integer.parseInt(s.substring(i + 2, i + 4), 16));
                                i += 3;
                                continue;
                            } catch (NumberFormatException ignored) {
                                sb.append('x');
                            }
                        } else {
                            sb.append('x');
                        }
                    }
                    default -> {
                        sb.append('\\').append(nxt);
                        i++;
                    }
                }
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "ShWord[" + raw + "]";
    }
}