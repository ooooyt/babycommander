package com.ooooyt.babycommander.util;

import com.ooooyt.babycommander.util.antlr.CodeSkeletonLexer;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.antlr.v4.runtime.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Language-agnostic utility for extracting code skeletons and reading specific
 * functions/methods from source files using ANTLR 4 for lexical analysis.
 * <p>
 * Uses ANTLR 4's generated {@link CodeSkeletonLexer} (from
 * {@code CodeSkeletonLexer.g4}) to tokenize source files and identify
 * structural elements (classes, functions, methods) by analyzing token types
 * rather than raw regex. The ANTLR-generated lexer uses a DFA-based approach
 * that is highly optimized and much faster than hand-written character-by-character
 * lexers.
 * <p>
 * The ANTLR lexer correctly identifies which braces are inside string literals
 * or comments, allowing the skeleton extraction logic to accurately track
 * function body boundaries without being confused by braces in strings.
 * <p>
 * Supports three parsing strategies:
 * <ul>
 *   <li><b>Brace-based</b> — for C, C++, C#, Java, JavaScript, TypeScript, Go, Rust, Kotlin, Swift, etc.</li>
 *   <li><b>Indentation-based</b> — for Python, Ruby, YAML, etc.</li>
 *   <li><b>Line-based heuristic</b> — fallback for any language</li>
 * </ul>
 */
public class SkeletonExtractor {

    private static final Set<String> CONTROL_KEYWORDS = Set.of(
            "if", "for", "while", "switch", "catch", "else", "do", "try", "elif"
    );

    private static final Pattern LAST_WORD_BEFORE_PAREN = Pattern.compile(
            "(\\w+)\\s*\\(([^)]*)\\)"
    );

    private static final Pattern HAS_FUNCTION_BODY = Pattern.compile(
            ".*\\([^)]*\\)\\s*.*\\{"
    );

    private static final Pattern CONTROL_FLOW_PATTERN = Pattern.compile(
            "^\\s*(?:if|for|while|switch|catch|else\\s+if|do|try)\\s*\\(.*\\{"
    );

    private static final Pattern INDENT_FUNCTION_PATTERN = Pattern.compile(
            "^(\\s*)" +
            "(?:def|function|fun|fn|sub|async\\s+def|describe|it|test)\\s+" +
            "(?:self\\.)?" +
            "['\"]?(\\w[\\w?!]*)['\"]?" +
            "\\s*\\(?([^)]*)\\)?\\s*" +
            "(?:\\s*->\\s*[^:]+)?\\s*:" +
            "\\s*$"
    );

    private static final Pattern BRACE_TYPE_PATTERN = Pattern.compile(
            "^(\\s*)" +
            "(?:(?:public|private|protected|static|abstract|sealed|non-sealed|" +
            "open|internal|data|value|inline|final|export|declare|" +
            "pub|pub\\(crate\\)|pub\\(super\\)|pub\\(self\\)|" +
            "template\\s*<[^>]*>)\\s+)*" +
            "(class|interface|enum|struct|trait|record|object|module|" +
            "type|protocol|extension|implementation|component|controller|" +
            "service|repository|entity|bean|impl)\\s+" +
            "(\\w+)" +
            ".*\\{$"
    );

    private static final Pattern INDENT_TYPE_PATTERN = Pattern.compile(
            "^(\\s*)" +
            "(class|interface|enum|struct|trait|module|type)\\s+" +
            "(\\w+)" +
            ".*:$"
    );

    private static final Pattern RUBY_DEF_PATTERN = Pattern.compile(
            "^(\\s*)" +
            "(?:def)\\s+" +
            "(?:self\\.)?" +
            "(\\w[\\w?!]*)" +
            "(?:\\s*\\(([^)]*)\\))?"
    );

    private static final Pattern ANNOTATION_PATTERN = Pattern.compile("^\\s*@\\w+");
    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile("^\\s*#\\[.*\\]$");
    private static final Pattern DECORATOR_PATTERN = Pattern.compile("^\\s*@\\w+");

    private static final Pattern LINE_COMMENT = Pattern.compile("^\\s*(//|#|--|;|%|\\*|\\|>)");
    private static final Pattern BLOCK_COMMENT_START = Pattern.compile("^\\s*/\\*|^\\s*\"\"\"|^\\s*'''");
    private static final Pattern BLOCK_COMMENT_END = Pattern.compile("\\*/|\"\"\"|'''");

    private static final Pattern STATIC_INIT_PATTERN = Pattern.compile("^\\s*static\\s*\\{");

    // ========================================================================
    // Lombok support
    // ========================================================================

    private static final Set<String> LOMBOK_CLASS_ANNOTATIONS = Set.of(
            "Data", "Value", "Builder", "Getter", "Setter",
            "AllArgsConstructor", "NoArgsConstructor", "RequiredArgsConstructor",
            "ToString", "EqualsAndHashCode",
            "Slf4j", "Log", "Log4j", "Log4j2", "CommonsLog", "Flogger", "JBossLog"
    );

    private static final Pattern FIELD_DECL_PATTERN = Pattern.compile(
            "^\\s*" +
            "(?:(?:public|private|protected|static|final|transient|volatile|strictfp)\\s+)*" +
            "([\\w<>,?\\[\\]\\.\\s]+)" +
            "\\s+" +
            "(\\w+)" +
            "\\s*(?:=|;|//|$)"
    );

    private static final Pattern ANNOTATION_NAME_PATTERN = Pattern.compile("@(?:\\w+\\.)*(\\w+)");

    private record FieldInfo(String name, String type) {}

    @Getter
    public static class SkeletonResult {
        private final String skeleton;
        private final String language;
        private final List<FunctionInfo> functions;
        private final List<TypeInfo> types;

        public SkeletonResult(String skeleton, String language,
                              List<FunctionInfo> functions, List<TypeInfo> types) {
            this.skeleton = skeleton;
            this.language = language;
            this.functions = Collections.unmodifiableList(functions);
            this.types = Collections.unmodifiableList(types);
        }

        public String getFunctionSummary() {
            if (functions.isEmpty()) {
                return I18n.tr(MessageKey.SKELETON_NO_FUNCTIONS);
            }
            StringBuilder sb = new StringBuilder();
            sb.append(I18n.tr(MessageKey.SKELETON_FUNCTIONS_FOUND, functions.size())).append("\n");
            for (FunctionInfo fi : functions) {
                sb.append("  - ").append(fi.name);
                if (fi.paramCount >= 0) {
                    sb.append("(").append(fi.paramCount).append(" params)");
                }
                sb.append(" at line ").append(fi.lineNumber);
                if (fi.endLineNumber > fi.lineNumber) {
                    sb.append("-").append(fi.endLineNumber);
                }
                sb.append("\n");
            }
            return sb.toString();
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PACKAGE)
    public static class FunctionInfo {
        final String name;
        final int lineNumber;
        final int endLineNumber;
        final int paramCount;
        final String signature;
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PACKAGE)
    public static class TypeInfo {
        final String name;
        final String kind;
        final int lineNumber;
    }

    public static String detectLanguage(String filePath) {
        String name = Paths.get(filePath).getFileName().toString().toLowerCase();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return "unknown";
        String ext = name.substring(dot);
        return switch (ext) {
            case ".java" -> "Java";
            case ".kt", ".kts" -> "Kotlin";
            case ".scala", ".sc" -> "Scala";
            case ".groovy", ".gvy", ".gy", ".gsh" -> "Groovy";
            case ".js", ".mjs", ".cjs" -> "JavaScript";
            case ".ts", ".tsx" -> "TypeScript";
            case ".jsx" -> "JSX";
            case ".py", ".pyw" -> "Python";
            case ".rb", ".rbw" -> "Ruby";
            case ".c", ".h" -> "C";
            case ".cpp", ".cc", ".cxx", ".hpp", ".hh", ".hxx" -> "C++";
            case ".cs" -> "C#";
            case ".go" -> "Go";
            case ".rs" -> "Rust";
            case ".swift" -> "Swift";
            case ".php" -> "PHP";
            case ".pl", ".pm" -> "Perl";
            case ".lua" -> "Lua";
            case ".r", ".rdata" -> "R";
            case ".m" -> "Objective-C";
            case ".mm" -> "Objective-C++";
            case ".dart" -> "Dart";
            case ".ex", ".exs" -> "Elixir";
            case ".clj", ".cljs", ".cljc" -> "Clojure";
            case ".hs", ".lhs" -> "Haskell";
            case ".erl", ".hrl" -> "Erlang";
            case ".sql" -> "SQL";
            case ".sh", ".bash", ".zsh" -> "Shell";
            case ".yaml", ".yml" -> "YAML";
            case ".json" -> "JSON";
            case ".xml", ".xsd", ".xslt" -> "XML";
            case ".md", ".markdown" -> "Markdown";
            case ".html", ".htm" -> "HTML";
            case ".css", ".scss", ".less" -> "CSS";
            case ".vue" -> "Vue";
            case ".svelte" -> "Svelte";
            case ".tf" -> "Terraform";
            case ".proto" -> "Protobuf";
            case ".gradle", ".gradle.kts" -> "Gradle";
            default -> "unknown";
        };
    }

    private static boolean isBraceLanguage(String language) {
        return switch (language) {
            case "Java", "Kotlin", "Scala", "Groovy", "JavaScript", "TypeScript",
                 "JSX", "C", "C++", "C#", "Go", "Rust", "Swift", "PHP",
                 "Perl", "Dart", "Objective-C", "Objective-C++",
                 "Terraform", "Protobuf", "Gradle" -> true;
            default -> false;
        };
    }

    private static boolean isIndentLanguage(String language) {
        return switch (language) {
            case "Python", "Ruby", "Elixir", "Haskell", "YAML" -> true;
            default -> false;
        };
    }

    // ========================================================================
    // ANTLR-based Token Analysis using generated lexer
    // ========================================================================

    /**
     * Analyze source text using ANTLR 4's generated {@link CodeSkeletonLexer}
     * to identify structural elements. The ANTLR-generated lexer uses a DFA
     * (Deterministic Finite Automaton) for highly efficient tokenization.
     * <p>
     * This method correctly handles:
     * <ul>
     *   <li>String literals (double, single, and backtick) with escape sequences</li>
     *   <li>Block comments and line comments</li>
     *   <li>Brace matching that ignores braces inside strings/comments</li>
     * </ul>
     *
     * @param text the full source text
     * @return set of (line, charPosition) pairs for real braces
     */
    private static Set<Long> findRealBracePositions(String text) {
        try {
            Set<Long> realBracePositions = new HashSet<>();
            CharStream input = CharStreams.fromString(text);
            CodeSkeletonLexer lexer = new CodeSkeletonLexer(input);

            for (Token token : lexer.getAllTokens()) {
                int type = token.getType();
                if (type == CodeSkeletonLexer.LBRACE || type == CodeSkeletonLexer.RBRACE) {
                    long key = ((long) token.getLine() << 32) | token.getCharPositionInLine();
                    realBracePositions.add(key);
                }
            }
            return realBracePositions;
        } catch (Exception e) {
            // ANTLR lexer failed — fall back to empty set, causing body extraction
            // to treat all braces as non-structural (produces full file content,
            // which is better than crashing)
            return new HashSet<>();
        }
    }

    public SkeletonResult extractSkeleton(String filePath) throws IOException {
        Path path = Paths.get(filePath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new IOException(I18n.tr(MessageKey.SKELETON_FILE_NOT_FOUND, filePath));
        }
        String language = detectLanguage(filePath);
        List<String> lines = Files.readAllLines(path);

        if (isBraceLanguage(language)) {
            return extractBraceSkeleton(lines, language);
        } else if (isIndentLanguage(language)) {
            return extractIndentSkeleton(lines, language);
        } else {
            return extractHeuristicSkeleton(lines, language);
        }
    }

    /**
     * Extract skeleton from a brace-delimited language using ANTLR-generated
     * lexer for robust brace/string/comment tracking.
     */
    private SkeletonResult extractBraceSkeleton(List<String> lines, String language) {
        List<String> skeletonLines = new ArrayList<>();
        List<FunctionInfo> functions = new ArrayList<>();
        List<TypeInfo> types = new ArrayList<>();

        // Use ANTLR-generated lexer to find real brace positions
        String fullText = String.join("\n", lines);
        Set<Long> realBracePositions = findRealBracePositions(fullText);

        boolean inBlockComment = false;
        boolean inFunctionBody = false;
        boolean inStaticInit = false;
        int functionBraceDepth = 0;
        int staticInitBraceDepth = 0;

        // Lombok tracking state
        Set<String> lombokClassAnnotations = new LinkedHashSet<>();
        List<FieldInfo> currentFields = new ArrayList<>();
        String currentTypeName = null;
        boolean inTypeBody = false;
        int typeBraceDepth = 0;
        String typeIndent = "";
        List<String> recentAnnotations = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            if (inBlockComment) {
                if (BLOCK_COMMENT_END.matcher(line).find()) {
                    inBlockComment = false;
                }
                skeletonLines.add(line);
                continue;
            }
            if (BLOCK_COMMENT_START.matcher(line).find() && !line.contains("*/")) {
                inBlockComment = true;
                skeletonLines.add(line);
                continue;
            }

            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                skeletonLines.add(line);
                continue;
            }

            if (ANNOTATION_PATTERN.matcher(line).find() ||
                    ATTRIBUTE_PATTERN.matcher(line).find() ||
                    DECORATOR_PATTERN.matcher(line).find()) {
                skeletonLines.add(line);
                if (ANNOTATION_PATTERN.matcher(line).find()) {
                    recentAnnotations.add(trimmed);
                }
                continue;
            }

            if (STATIC_INIT_PATTERN.matcher(line).find()) {
                inStaticInit = true;
                staticInitBraceDepth = countRealBraces(line, realBracePositions, i + 1, '{')
                        - countRealBraces(line, realBracePositions, i + 1, '}');
                skeletonLines.add(line);
                if (staticInitBraceDepth <= 0) {
                    inStaticInit = false;
                }
                continue;
            }
            if (inStaticInit) {
                int openBraces = countRealBraces(line, realBracePositions, i + 1, '{');
                int closeBraces = countRealBraces(line, realBracePositions, i + 1, '}');
                staticInitBraceDepth += openBraces - closeBraces;
                if (staticInitBraceDepth <= 0) {
                    inStaticInit = false;
                    skeletonLines.add(line);
                }
                continue;
            }

            if (inFunctionBody) {
                int openBraces = countRealBraces(line, realBracePositions, i + 1, '{');
                int closeBraces = countRealBraces(line, realBracePositions, i + 1, '}');
                functionBraceDepth += openBraces - closeBraces;
                if (functionBraceDepth <= 0) {
                    inFunctionBody = false;
                    if (!functions.isEmpty()) {
                        FunctionInfo last = functions.get(functions.size() - 1);
                        functions.set(functions.size() - 1,
                                new FunctionInfo(last.name, last.lineNumber, i + 1,
                                        last.paramCount, last.signature));
                    }
                    skeletonLines.add(line);
                }
                continue;
            }

            Matcher typeMatcher = BRACE_TYPE_PATTERN.matcher(line);
            if (typeMatcher.matches()) {
                // Check recent annotations for Lombok class annotations
                for (String ann : recentAnnotations) {
                    String simpleName = extractAnnotationSimpleName(ann);
                    if (simpleName != null && LOMBOK_CLASS_ANNOTATIONS.contains(simpleName)) {
                        lombokClassAnnotations.add(simpleName);
                    }
                }
                recentAnnotations.clear();

                String kind = typeMatcher.group(2);
                String name = typeMatcher.group(3);
                types.add(new TypeInfo(name, kind, i + 1));
                skeletonLines.add(line);

                // Track type body brace depth
                int openBraces = countRealBraces(line, realBracePositions, i + 1, '{');
                int closeBraces = countRealBraces(line, realBracePositions, i + 1, '}');
                typeBraceDepth += openBraces - closeBraces;
                if (!inTypeBody) {
                    inTypeBody = true;
                    currentTypeName = name;
                    typeIndent = extractIndent(line);
                    currentFields.clear();
                }
                continue;
            }

            if (isBraceFunctionLine(line)) {
                recentAnnotations.clear();
                String name = extractFunctionName(line);
                String params = extractParams(line);
                int paramCount = countParams(params);

                int startLine = i + 1;
                functions.add(new FunctionInfo(name, startLine, startLine,
                        paramCount, line.trim()));

                inFunctionBody = true;
                functionBraceDepth = 0;
                int openBraces = countRealBraces(line, realBracePositions, i + 1, '{');
                int closeBraces = countRealBraces(line, realBracePositions, i + 1, '}');
                functionBraceDepth = openBraces - closeBraces;

                String sigLine = line;
                if (sigLine.contains("{")) {
                    int braceIdx = sigLine.indexOf('{');
                    sigLine = sigLine.substring(0, braceIdx).trim() + " {";
                    String indent = extractIndent(line);
                    sigLine = indent + sigLine.trim();
                }
                skeletonLines.add(sigLine);

                if (functionBraceDepth <= 0) {
                    inFunctionBody = false;
                    if (!functions.isEmpty()) {
                        FunctionInfo last = functions.get(functions.size() - 1);
                        functions.set(functions.size() - 1,
                                new FunctionInfo(last.name, last.lineNumber, i + 1,
                                        last.paramCount, last.signature));
                    }
                }
                continue;
            }

            // Track type body brace depth and detect fields for Lombok
            if (inTypeBody && !inFunctionBody && !inStaticInit) {
                int openBraces = countRealBraces(line, realBracePositions, i + 1, '{');
                int closeBraces = countRealBraces(line, realBracePositions, i + 1, '}');
                typeBraceDepth += openBraces - closeBraces;

                if (!lombokClassAnnotations.isEmpty()) {
                    Matcher fieldMatcher = FIELD_DECL_PATTERN.matcher(line);
                    if (fieldMatcher.matches()) {
                        currentFields.add(new FieldInfo(fieldMatcher.group(2), fieldMatcher.group(1).trim()));
                    }
                }

                if (typeBraceDepth <= 0) {
                    addLombokSyntheticMethods(skeletonLines, currentFields, currentTypeName,
                                               lombokClassAnnotations, typeIndent + "    ");
                    inTypeBody = false;
                    currentFields.clear();
                    lombokClassAnnotations.clear();
                    currentTypeName = null;
                }
            }

            // Clear recent annotations for non-annotation lines
            if (!trimmed.isEmpty()) {
                recentAnnotations.clear();
            }

            skeletonLines.add(line);
        }

        // Handle unclosed type at end of file
        if (inTypeBody && !lombokClassAnnotations.isEmpty()) {
            addLombokSyntheticMethods(skeletonLines, currentFields, currentTypeName,
                                       lombokClassAnnotations, typeIndent + "    ");
        }

        String skeleton = String.join("\n", skeletonLines);
        return new SkeletonResult(skeleton, language, functions, types);
    }

    /**
     * Count the number of real braces of a given type on a line, using
     * ANTLR-verified positions to exclude braces inside string literals
     * and comments.
     */
    private static int countRealBraces(String line, Set<Long> realBracePositions,
                                        int lineNumber, char braceType) {
        int count = 0;
        for (int j = 0; j < line.length(); j++) {
            if (line.charAt(j) == braceType) {
                long key = ((long) lineNumber << 32) | j;
                if (realBracePositions.contains(key)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean isBraceFunctionLine(String line) {
        String trimmed = line.trim();

        if (!HAS_FUNCTION_BODY.matcher(trimmed).matches()) {
            return false;
        }

        if (CONTROL_FLOW_PATTERN.matcher(trimmed).matches()) {
            return false;
        }

        String name = extractFunctionName(trimmed);
        if (name == null) {
            return false;
        }

        return !CONTROL_KEYWORDS.contains(name);
    }

    private static String extractFunctionName(String line) {
        Matcher m = LAST_WORD_BEFORE_PAREN.matcher(line);
        String lastName = null;
        while (m.find()) {
            lastName = m.group(1);
        }
        return lastName;
    }

    private static String extractParams(String line) {
        Matcher m = LAST_WORD_BEFORE_PAREN.matcher(line);
        String lastParams = null;
        while (m.find()) {
            lastParams = m.group(2);
        }
        return lastParams != null ? lastParams : "";
    }

    private static String extractIndent(String line) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ' ' || c == '\t') {
                sb.append(c);
            } else {
                break;
            }
        }
        return sb.toString();
    }

    private SkeletonResult extractIndentSkeleton(List<String> lines, String language) {
        List<String> skeletonLines = new ArrayList<>();
        List<FunctionInfo> functions = new ArrayList<>();
        List<TypeInfo> types = new ArrayList<>();

        boolean inBlockComment = false;
        boolean inFunctionBody = false;
        int functionBodyIndent = -1;
        boolean isRuby = "Ruby".equals(language);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            if (inBlockComment) {
                if (BLOCK_COMMENT_END.matcher(line).find()) {
                    inBlockComment = false;
                }
                skeletonLines.add(line);
                continue;
            }
            if (line.contains("\"\"\"") || line.contains("'''")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("\"\"\"") || trimmed.startsWith("'''")) {
                    if (!trimmed.endsWith("\"\"\"") && !trimmed.endsWith("'''")) {
                        inBlockComment = true;
                    }
                }
                skeletonLines.add(line);
                continue;
            }

            if (line.trim().isEmpty()) {
                skeletonLines.add(line);
                continue;
            }

            if (inFunctionBody) {
                int indent = countIndent(line);
                boolean isEndKeyword = isRuby && line.trim().equals("end") && indent == functionBodyIndent;

                if ((indent <= functionBodyIndent && !line.trim().isEmpty()) || isEndKeyword) {
                    inFunctionBody = false;
                    if (!functions.isEmpty()) {
                        FunctionInfo last = functions.get(functions.size() - 1);
                        functions.set(functions.size() - 1,
                                new FunctionInfo(last.name, last.lineNumber, i + 1,
                                        last.paramCount, last.signature));
                    }
                    if (isEndKeyword) {
                        skeletonLines.add(line);
                    }
                } else {
                    continue;
                }
            }

            if (DECORATOR_PATTERN.matcher(line).find()) {
                skeletonLines.add(line);
                continue;
            }

            Matcher typeMatcher = INDENT_TYPE_PATTERN.matcher(line);
            if (typeMatcher.matches()) {
                String kind = typeMatcher.group(2);
                String name = typeMatcher.group(3);
                types.add(new TypeInfo(name, kind, i + 1));
                skeletonLines.add(line);
                continue;
            }

            Matcher funcMatcher = INDENT_FUNCTION_PATTERN.matcher(line);
            boolean matched = funcMatcher.matches();
            String funcName = null;
            String funcParams = "";

            if (!matched && isRuby) {
                Matcher rubyMatcher = RUBY_DEF_PATTERN.matcher(line);
                if (rubyMatcher.matches()) {
                    matched = true;
                    funcName = rubyMatcher.group(2);
                    funcParams = rubyMatcher.group(3) != null ? rubyMatcher.group(3) : "";
                }
            } else if (matched) {
                funcName = funcMatcher.group(2);
                funcParams = funcMatcher.group(3) != null ? funcMatcher.group(3) : "";
            }

            if (matched && funcName != null) {
                int paramCount = countParams(funcParams);
                int startLine = i + 1;
                functions.add(new FunctionInfo(funcName, startLine, startLine,
                        paramCount, line.trim()));
                inFunctionBody = true;
                functionBodyIndent = countIndent(line);
                skeletonLines.add(line);
                continue;
            }

            skeletonLines.add(line);
        }

        if (inFunctionBody && !functions.isEmpty()) {
            FunctionInfo last = functions.get(functions.size() - 1);
            functions.set(functions.size() - 1,
                    new FunctionInfo(last.name, last.lineNumber, lines.size(),
                            last.paramCount, last.signature));
        }

        String skeleton = String.join("\n", skeletonLines);
        return new SkeletonResult(skeleton, language, functions, types);
    }

    private SkeletonResult extractHeuristicSkeleton(List<String> lines, String language) {
        List<String> skeletonLines = new ArrayList<>();
        List<FunctionInfo> functions = new ArrayList<>();
        List<TypeInfo> types = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                skeletonLines.add(line);
                continue;
            }

            if (LINE_COMMENT.matcher(line).find() || ANNOTATION_PATTERN.matcher(line).find()) {
                skeletonLines.add(line);
                continue;
            }

            if (trimmed.matches(".*\\b(class|interface|enum|struct|function|def|fun|fn|" +
                    "var|val|let|const|import|package|namespace|module|" +
                    "public|private|protected)\\b.*") &&
                    !trimmed.matches(".*\\b(return|if|for|while|switch|case|try|catch|finally)\\b.*")) {
                skeletonLines.add(line);
                continue;
            }

            if (trimmed.matches("^[\\w<>\\[\\],\\s]+\\s+\\w+\\s*[=;].*") &&
                    !trimmed.contains("(") && !trimmed.contains(")")) {
                skeletonLines.add(line);
                continue;
            }

            if (trimmed.matches("[{}()\\[\\]]+\\s*")) {
                skeletonLines.add(line);
                continue;
            }
        }

        String skeleton = String.join("\n", skeletonLines);
        return new SkeletonResult(skeleton, language, functions, types);
    }

    public String readFunction(String filePath, String functionName) throws IOException {
        return readFunction(filePath, functionName, -1);
    }

    public String readFunction(String filePath, String functionName, int paramCount) throws IOException {
        Path path = Paths.get(filePath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            return "Error: File not found: " + filePath;
        }
        String language = detectLanguage(filePath);
        List<String> lines = Files.readAllLines(path);

        if (isBraceLanguage(language)) {
            return readBraceFunction(lines, functionName, paramCount);
        } else if (isIndentLanguage(language)) {
            return readIndentFunction(lines, functionName, paramCount);
        } else {
            return readHeuristicFunction(lines, functionName, paramCount);
        }
    }

    private String readBraceFunction(List<String> lines, String functionName, int paramCount) {
        String fullText = String.join("\n", lines);
        Set<Long> realBracePositions = findRealBracePositions(fullText);

        boolean inBlockComment = false;
        StringBuilder annotations = new StringBuilder();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            if (inBlockComment) {
                if (BLOCK_COMMENT_END.matcher(line).find()) {
                    inBlockComment = false;
                }
                annotations.append(line).append("\n");
                continue;
            }
            if (BLOCK_COMMENT_START.matcher(line).find() && !line.contains("*/")) {
                inBlockComment = true;
                annotations.append(line).append("\n");
                continue;
            }

            if (ANNOTATION_PATTERN.matcher(line).find() ||
                    ATTRIBUTE_PATTERN.matcher(line).find() ||
                    DECORATOR_PATTERN.matcher(line).find()) {
                annotations.append(line).append("\n");
                continue;
            }

            if (isBraceFunctionLine(line)) {
                String name = extractFunctionName(line);
                String params = extractParams(line);
                int foundParamCount = countParams(params);

                boolean nameMatches = name != null && name.equals(functionName);
                boolean paramMatches = paramCount < 0 || foundParamCount == paramCount;

                if (nameMatches && paramMatches) {
                    StringBuilder result = new StringBuilder();
                    if (!annotations.isEmpty()) {
                        result.append(annotations);
                    }

                    int braceDepth = 0;
                    boolean started = false;

                    for (int j = i; j < lines.size(); j++) {
                        String l = lines.get(j);
                        result.append(l).append("\n");
                        if (!started) {
                            braceDepth += countRealBraces(l, realBracePositions, j + 1, '{')
                                    - countRealBraces(l, realBracePositions, j + 1, '}');
                            started = true;
                        } else {
                            braceDepth += countRealBraces(l, realBracePositions, j + 1, '{')
                                    - countRealBraces(l, realBracePositions, j + 1, '}');
                        }
                        if (braceDepth <= 0 && started) {
                            break;
                        }
                    }
                    return result.toString().trim();
                }
                annotations = new StringBuilder();
            } else if (!line.trim().isEmpty()) {
                if (!LINE_COMMENT.matcher(line).find()) {
                    annotations = new StringBuilder();
                }
            }
        }

        String paramMsg = paramCount >= 0 ? " with " + paramCount + " parameter(s)" : "";
        return "Function '" + functionName + "'" + paramMsg + " not found in file";
    }

    private String readIndentFunction(List<String> lines, String functionName, int paramCount) {
        boolean inBlockComment = false;
        StringBuilder annotations = new StringBuilder();
        boolean isRuby = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            if (inBlockComment) {
                if (BLOCK_COMMENT_END.matcher(line).find()) {
                    inBlockComment = false;
                }
                annotations.append(line).append("\n");
                continue;
            }
            if (line.contains("\"\"\"") || line.contains("'''")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("\"\"\"") || trimmed.startsWith("'''")) {
                    if (!trimmed.endsWith("\"\"\"") && !trimmed.endsWith("'''")) {
                        inBlockComment = true;
                    }
                }
                annotations.append(line).append("\n");
                continue;
            }

            if (DECORATOR_PATTERN.matcher(line).find()) {
                annotations.append(line).append("\n");
                continue;
            }

            Matcher funcMatcher = INDENT_FUNCTION_PATTERN.matcher(line);
            boolean matched = funcMatcher.matches();
            String funcName = null;
            String funcParams = "";

            if (!matched) {
                Matcher rubyMatcher = RUBY_DEF_PATTERN.matcher(line);
                if (rubyMatcher.matches()) {
                    matched = true;
                    isRuby = true;
                    funcName = rubyMatcher.group(2);
                    funcParams = rubyMatcher.group(3) != null ? rubyMatcher.group(3) : "";
                }
            } else {
                funcName = funcMatcher.group(2);
                funcParams = funcMatcher.group(3) != null ? funcMatcher.group(3) : "";
            }

            if (matched && funcName != null) {
                int foundParamCount = countParams(funcParams);
                boolean nameMatches = funcName.equals(functionName);
                boolean paramMatches = paramCount < 0 || foundParamCount == paramCount;

                if (nameMatches && paramMatches) {
                    StringBuilder result = new StringBuilder();
                    if (!annotations.isEmpty()) {
                        result.append(annotations);
                    }
                    int funcIndent = countIndent(line);
                    result.append(line).append("\n");

                    for (int j = i + 1; j < lines.size(); j++) {
                        String l = lines.get(j);
                        if (l.trim().isEmpty()) {
                            result.append(l).append("\n");
                            continue;
                        }
                        int indent = countIndent(l);
                        if (isRuby && l.trim().equals("end") && indent == funcIndent) {
                            result.append(l).append("\n");
                            break;
                        }
                        if (indent <= funcIndent) {
                            break;
                        }
                        result.append(l).append("\n");
                    }
                    return result.toString().trim();
                }
                annotations = new StringBuilder();
            } else if (!line.trim().isEmpty()) {
                if (!LINE_COMMENT.matcher(line).find()) {
                    annotations = new StringBuilder();
                }
            }
        }

        String paramMsg = paramCount >= 0 ? " with " + paramCount + " parameter(s)" : "";
        return "Function '" + functionName + "'" + paramMsg + " not found in file";
    }

    private String readHeuristicFunction(List<String> lines, String functionName, int paramCount) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();

            if (trimmed.matches(".*\\b(function|def|fun|fn|sub|macro)\\s+" +
                    Pattern.quote(functionName) + "\\b.*") ||
                    trimmed.matches(".*\\b" + Pattern.quote(functionName) +
                            "\\s*[=:].*\\b(function|fn|lambda|->|=>)\\b.*")) {

                StringBuilder result = new StringBuilder();
                result.append(line).append("\n");
                int baseIndent = countIndent(line);

                for (int j = i + 1; j < lines.size(); j++) {
                    String l = lines.get(j);
                    if (l.trim().isEmpty()) {
                        result.append(l).append("\n");
                        continue;
                    }
                    int indent = countIndent(l);
                    if (indent <= baseIndent && !l.trim().matches("[{}].*")) {
                        break;
                    }
                    result.append(l).append("\n");
                }
                return result.toString().trim();
            }
        }
        return "Function '" + functionName + "' not found in file";
    }

    private static int countParams(String params) {
        if (params == null || params.trim().isEmpty()) {
            return 0;
        }
        int depth = 0;
        int count = 1;
        for (int i = 0; i < params.length(); i++) {
            char c = params.charAt(i);
            if (c == '<' || c == '(' || c == '[') depth++;
            else if (c == '>' || c == ')' || c == ']') depth--;
            else if (c == ',' && depth == 0) count++;
        }
        return count;
    }

    private static int countChar(String s, char c) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) count++;
        }
        return count;
    }

    private static int countIndent(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ' ') count++;
            else if (c == '\t') count += 4;
            else break;
        }
        return count;
    }

    // ========================================================================
    // Lombok helper methods
    // ========================================================================

    private static String extractAnnotationSimpleName(String annotationLine) {
        Matcher m = ANNOTATION_NAME_PATTERN.matcher(annotationLine.trim());
        return m.find() ? m.group(1) : null;
    }

    private static String getGetterName(String fieldName, String fieldType) {
        boolean isBoolean = "boolean".equals(fieldType) || "Boolean".equals(fieldType);
        String prefix = isBoolean ? "is" : "get";
        return prefix + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    }

    private static String getSetterName(String fieldName) {
        return "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    }

    private static void addLombokSyntheticMethods(List<String> skeletonLines,
                                                    List<FieldInfo> fields,
                                                    String typeName,
                                                    Set<String> annotations,
                                                    String indent) {
        if (annotations.isEmpty()) return;

        boolean hasData = annotations.contains("Data");
        boolean hasValue = annotations.contains("Value");
        boolean hasGetter = hasData || hasValue || annotations.contains("Getter");
        boolean hasSetter = hasData || (!hasValue && annotations.contains("Setter"));
        boolean hasBuilder = annotations.contains("Builder");
        boolean hasNoArgsConstructor = annotations.contains("NoArgsConstructor");
        boolean hasAllArgsConstructor = hasValue || annotations.contains("AllArgsConstructor");
        boolean hasRequiredArgsConstructor = hasData || annotations.contains("RequiredArgsConstructor");
        boolean hasToString = hasData || hasValue || annotations.contains("ToString");
        boolean hasEqualsAndHashCode = hasData || hasValue || annotations.contains("EqualsAndHashCode");
        boolean hasSlf4j = annotations.stream().anyMatch(a ->
                Set.of("Slf4j", "Log", "Log4j", "Log4j2", "CommonsLog", "Flogger", "JBossLog").contains(a));

        List<String> lines = new ArrayList<>();
        lines.add(indent + "// --- Lombok-generated methods ---");

        if (hasGetter) {
            StringBuilder sb = new StringBuilder(indent + "// @Getter:");
            for (FieldInfo f : fields) {
                sb.append(" ").append(getGetterName(f.name, f.type)).append("(),");
            }
            if (sb.charAt(sb.length() - 1) == ',') {
                sb.setLength(sb.length() - 1);
            }
            lines.add(sb.toString());
        }

        if (hasSetter) {
            StringBuilder sb = new StringBuilder(indent + "// @Setter:");
            for (FieldInfo f : fields) {
                sb.append(" set").append(Character.toUpperCase(f.name.charAt(0)))
                  .append(f.name.substring(1)).append("(").append(f.type).append("),");
            }
            if (sb.charAt(sb.length() - 1) == ',') {
                sb.setLength(sb.length() - 1);
            }
            lines.add(sb.toString());
        }

        if (hasBuilder) {
            lines.add(indent + "// @Builder: static " + typeName + "Builder builder()");
        }

        if (hasNoArgsConstructor) {
            lines.add(indent + "// @NoArgsConstructor: " + typeName + "()");
        }

        if (hasAllArgsConstructor && !fields.isEmpty()) {
            StringBuilder sb = new StringBuilder(indent + "// @AllArgsConstructor: " + typeName + "(");
            for (FieldInfo f : fields) {
                sb.append(f.type).append(" ").append(f.name).append(", ");
            }
            sb.setLength(sb.length() - 2);
            sb.append(")");
            lines.add(sb.toString());
        }

        if (hasRequiredArgsConstructor && !fields.isEmpty()) {
            StringBuilder sb = new StringBuilder(indent + "// @RequiredArgsConstructor: " + typeName + "(");
            for (FieldInfo f : fields) {
                sb.append(f.type).append(" ").append(f.name).append(", ");
            }
            sb.setLength(sb.length() - 2);
            sb.append(")");
            lines.add(sb.toString());
        }

        if (hasToString) {
            lines.add(indent + "// @ToString: toString()");
        }

        if (hasEqualsAndHashCode) {
            lines.add(indent + "// @EqualsAndHashCode: equals(), hashCode()");
        }

        if (hasSlf4j) {
            lines.add(indent + "// @Slf4j: log field");
        }

        // Only add if we generated something beyond the header line
        if (lines.size() > 1) {
            skeletonLines.addAll(lines);
        }
    }
}
