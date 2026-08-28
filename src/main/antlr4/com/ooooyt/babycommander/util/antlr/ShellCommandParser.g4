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
//
// Reserved words (if/then/fi/...) are lexed as dedicated tokens
// but are ONLY meaningful in command position (compound-command
// keywords). In argument position they are ordinary words, so the
// `word` rule accepts them. The command NAME itself must be a
// `command_word` (never a reserved word), which is what keeps
// compound commands (if/while/for/case) unambiguous: a command can
// never START with a reserved word, so the inner list of an
// if_clause stops at `then`, a while_clause stops at `do`, etc.
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
    : compound_command redirect_list?
    | simple_command
    | function_def
    ;

// Assignments and redirects may only appear before the command name
// (matching shell semantics); anything after is an argument word.
// The command name must be a non-reserved word (command_word); reserved
// words (if/then/fi/...) are only recognized in command position as
// compound-command keywords, never as a command name. They ARE allowed
// as arguments (e.g. `echo done`), which is why word includes them.
// The command part is optional so that pure assignment/redirect
// commands (e.g. "FOO=bar", "> file") still parse.
simple_command
    : prefix* ( command_word word* )? ( redirect )*
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
//
// `word` is any word-forming token, INCLUDING reserved words
// (if/then/.../esac): in argument position (redirect target,
// assignment value, echo argument, ...) they are ordinary words.
//
// `command_word` is a word that may START a simple command (i.e. be
// a command name). Reserved words are excluded so that compound-
// command keywords are only recognized in command position.
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
    | IF | THEN | ELIF | ELSE | FI | WHILE | UNTIL | DO | DONE | FOR | IN | CASE | ESAC
    ;

command_word
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