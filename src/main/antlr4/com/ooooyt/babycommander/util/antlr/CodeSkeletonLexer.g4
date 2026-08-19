lexer grammar CodeSkeletonLexer;

// ============================================================
// Multi-language lexer for code skeleton extraction.
// Handles common constructs across C-family, Python, Ruby, Go,
// Rust, Swift, Kotlin, TypeScript, and many other languages.
// ============================================================

// -------------------------------------------
// Whitespace & Newlines
// -------------------------------------------
WS:                 [ \t]+ -> channel(HIDDEN);
NEWLINE:            '\r'? '\n' -> channel(HIDDEN);

// -------------------------------------------
// Comments
// -------------------------------------------
LINE_COMMENT:       '//' ~[\r\n]* -> channel(HIDDEN);
BLOCK_COMMENT:      '/*' .*? '*/' -> channel(HIDDEN);
HASH_COMMENT:       '#' ~[\r\n]* -> channel(HIDDEN);   // Python/Ruby/Shell/Perl

// Python docstrings
PYTHON_DOCSTRING:   '"""' .*? '"""' -> channel(HIDDEN);
RUBY_DOCSTRING:     '\'\'\'' .*? '\'\'\'' -> channel(HIDDEN);

// -------------------------------------------
// String literals
// -------------------------------------------
DOUBLE_STRING:      '"' ( '\\' . | ~["\\\r\n] )* '"';
SINGLE_STRING:      '\'' ( '\\' . | ~['\\\r\n] )* '\'';
BACKTICK_STRING:    '`' ( '\\' . | ~[`\\] )* '`';

// -------------------------------------------
// Braces, Parens, Brackets - THESE ARE THE IMPORTANT ONES
// -------------------------------------------
LBRACE:             '{';
RBRACE:             '}';
LPAREN:             '(';
RPAREN:             ')';
LBRACK:             '[';
RBRACK:             ']';

// -------------------------------------------
// Punctuation
// -------------------------------------------
SEMICOLON:          ';';
COLON:              ':';
DOT:                '.';
COMMA:              ',';
ARROW:              '->';
FAT_ARROW:          '=>';
ASSIGN:             '=';
AT:                 '@';

// -------------------------------------------
// Identifiers & Numbers
// -------------------------------------------
IDENTIFIER:         [a-zA-Z_$] [a-zA-Z0-9_$]*;
NUMBER:             [0-9]+ ('.' [0-9]+)? ([eE] [+-]? [0-9]+)?;

// -------------------------------------------
// Generic fallback
// -------------------------------------------
OTHER:              .;
