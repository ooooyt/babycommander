lexer grammar ShellCommandLexer;

// ============================================================
// Shell command lexer for the SH-GUARD safety analyzer.
//
// Tokenizes a POSIX-ish shell command line, preserving quoting,
// escapes, expansions, operators, and heredoc bodies so that the
// parser can build an AST that is robust against obfuscation
// (quoted/escaped command names, command substitution, variable
// indirection, etc.).
//
// Quoted strings are captured as COMPLETE single tokens (including
// their quote characters) so the parser can dequote them. This is
// essential for detecting obfuscated command names like r"m".
// ============================================================

// ------------------------------------------------------------------
// Whitespace & newlines
// ------------------------------------------------------------------
WS:             [ \t]+ -> channel(HIDDEN);
NEWLINE:        '\r'? '\n';

// ------------------------------------------------------------------
// Comments (only when '#' begins a word)
// ------------------------------------------------------------------
COMMENT:        '#' ~[\r\n]* -> channel(HIDDEN);

// ------------------------------------------------------------------
// Operators (longest match first)
// ------------------------------------------------------------------
OP_HEREDOC_STRIP:   '<<-';
OP_HERESTRING:      '<<<';
OP_HEREDOC:         '<<';
OP_APPEND:          '>>';
OP_CLOBBER:         '>|';
OP_DUP_OUT:         '>&';
OP_DUP_IN:          '<&';
OP_READWRITE:       '<>';
OP_AND_IF:          '&&';
OP_OR_IF:           '||';
OP_PIPE_ERR:        '|&';
OP_DSEMI:           ';;';
OP_SEMI:            ';';
OP_PIPE:            '|';
OP_AMP:             '&';
OP_LPAREN:          '(';
OP_RPAREN:          ')';
OP_LBRACE:          '{';
OP_RBRACE:          '}';
OP_LT:              '<';
OP_GT:              '>';
OP_ASSIGN:          '=';

// ------------------------------------------------------------------
// Reserved words (recognized only in command position)
// ------------------------------------------------------------------
IF:     'if';
THEN:   'then';
ELIF:   'elif';
ELSE:   'else';
FI:     'fi';
WHILE:  'while';
UNTIL:  'until';
DO:     'do';
DONE:   'done';
FOR:    'for';
IN:     'in';
CASE:   'case';
ESAC:   'esac';

// ------------------------------------------------------------------
// Quoted strings (captured whole, including quotes)
// ------------------------------------------------------------------
SQUOTE_STRING:  '\'' ( '\\' . | ~['\\] )* '\'';
DQUOTE_STRING:  '"'  ( '\\' . | ~["\\] )* '"';
ANSI_C_STRING:  '$' '\'' ( '\\' . | ~['\\] )* '\'';

// ------------------------------------------------------------------
// Expansions
// ------------------------------------------------------------------
ARITH_EXPAND:   '$' '(' '(' .*? ')' ')';
CMD_SUBST:      '$' '(' ( ~[()] | '(' ~[()]* ')' )* ')';
BACKTICK:       '`' ~[`]* '`';
VAR_BRACED:     '${' ~[}]* '}';
VAR_SIMPLE:     '$' [A-Za-z_][A-Za-z0-9_]*;
VAR_SPECIAL:    '$' [?#@*$!0-9];

// ------------------------------------------------------------------
// IO numbers (leading digits before a redirect operator)
// ------------------------------------------------------------------
IO_NUMBER:      [0-9]+;

// ------------------------------------------------------------------
// Unterminated quotes (parse errors -> conservative ASK_ONCE)
// ------------------------------------------------------------------
UNTERMINATED_SQUOTE:  '\'' ~['\r\n]*;
UNTERMINATED_DQUOTE:  '"'  ~["\r\n]*;

// ------------------------------------------------------------------
// Words
// ------------------------------------------------------------------
WORD:           [A-Za-z0-9_./:+~@%^,-]+;

// ------------------------------------------------------------------
// Escape sequences (backslash + any char)
// ------------------------------------------------------------------
ESCAPED:        '\\' .;

// Any other single character becomes part of a word.
OTHER:          .;