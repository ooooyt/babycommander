parser grammar ShellCommandParser;

options { tokenVocab=ShellCommandLexer; }

// ============================================================
// Shell command parser for the SH-GUARD safety analyzer.
//
// Builds a parse tree for a POSIX-ish shell command list. The
// tree is then walked by ShGuard to classify the command as
// DANGEROUS / ASK_ONCE / SAFE.
//
// The grammar is intentionally LENIENT: it accepts a wide range
// of real-world shell syntax (including malformed input) so the
// guard never crashes and always produces a usable AST. Parse
// errors are tolerated by the semantic pass (treated as ASK_ONCE).
//
// NOTE: 'word' matches a SINGLE word-forming token. Adjacent
// fragments that belong to one logical shell word (e.g. r"m",
// a"b"c, *.java) are merged by the semantic pass using source
// positions (no whitespace gap => same logical word).
// ============================================================

program
    : complete_command? EOF
    ;

complete_command
    : list separator_op?
    | list separator_op+
    ;

list
    : and_or ( separator_op and_or )*
    ;

separator_op
    : OP_SEMI
    | OP_AMP
    | NEWLINE
    | OP_DSEMI
    ;

and_or
    : pipeline ( ( OP_AND_IF | OP_OR_IF ) pipeline )*
    ;

pipeline
    : OP_PIPE? command ( ( OP_PIPE | OP_PIPE_ERR ) command )*
    ;

command
    : simple_command
    | compound_command redirect_list?
    | function_def
    ;

// Assignments and redirects may only appear before the command name
// (matching shell semantics); anything after is an argument word.
simple_command
    : prefix* word* ( redirect )*
    ;

prefix
    : assignment
    | redirect
    ;

compound_command
    : subshell
    | brace_group
    | if_clause
    | while_clause
    | until_clause
    | for_clause
    | case_clause
    ;

subshell
    : OP_LPAREN list OP_RPAREN
    ;

brace_group
    : OP_LBRACE list OP_RBRACE
    ;

if_clause
    : IF list THEN list ( ELIF list THEN list )* ( ELSE list )? FI
    ;

while_clause
    : WHILE list DO list DONE
    ;

until_clause
    : UNTIL list DO list DONE
    ;

for_clause
    : FOR word ( IN word* )? ( OP_SEMI | NEWLINE )? DO list DONE
    ;

case_clause
    : CASE word IN ( case_item )* ESAC
    ;

case_item
    : OP_LPAREN? word ( OP_PIPE word )* OP_RPAREN list OP_DSEMI
    ;

function_def
    : word OP_LPAREN OP_RPAREN ( compound_command | simple_command )
    ;

redirect_list
    : redirect+
    ;

redirect
    : ( IO_NUMBER )? redirect_op word
    | heredoc
    ;

redirect_op
    : OP_GT
    | OP_APPEND
    | OP_CLOBBER
    | OP_LT
    | OP_READWRITE
    | OP_DUP_IN
    | OP_DUP_OUT
    | OP_HEREDOC
    | OP_HERESTRING
    | OP_HEREDOC_STRIP
    ;

heredoc
    : OP_HEREDOC word
    | OP_HEREDOC_STRIP word
    ;

assignment
    : word OP_ASSIGN word
    ;

// ------------------------------------------------------------------
// Words: a single token that can form part of a command name or
// argument. Adjacent fragments are merged by the semantic pass.
// ------------------------------------------------------------------
word
    : WORD
    | IO_NUMBER
    | SQUOTE_STRING
    | DQUOTE_STRING
    | ANSI_C_STRING
    | ARITH_EXPAND
    | CMD_SUBST
    | BACKTICK
    | VAR_BRACED
    | VAR_SIMPLE
    | VAR_SPECIAL
    | ESCAPED
    | OTHER
    | OP_ASSIGN
    ;