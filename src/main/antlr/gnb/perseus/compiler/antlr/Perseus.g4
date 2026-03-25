grammar Perseus;
// Copyright (c) 2017-2026 Greg Bonney

/* Grammar for the Perseus language.
 *
 * Perseus began as an ALGOL 60 compiler and this grammar still preserves much
 * of that heritage, but the project is no longer limited to a strict historical
 * reconstruction of ALGOL 60. The grammar is intended to support an
 * Algol-derived language on the JVM, including compatible ALGOL-style syntax
 * where practical and project-specific extensions where useful.
 *
 * Development has been iterative rather than a direct transcription of the full
 * ALGOL reports into ANTLR. The grammar has grown by implementing and testing
 * concrete language features against sample programs, which has been a more
 * practical way to evolve both correctness and compiler support.
 */

/* NOTE:  Lexer rules start with a capital letter; parser rules start with a lower case letter */
/* More on lexer rules at:  https://github.com/antlr/antlr4/blob/master/doc/lexer-rules.md */

/* Block comments starting with the word "comment" and ending with a semicolon. */
BLOCK_COMMENT
	: 'comment' ~[;]* ';' -> channel(HIDDEN)
	;

/* extension: TeX style single line comments (also used in Unisys Extended Algol) */
LINE_COMMENT : '%' ~[\r\n]* '\r'? '\n' -> skip ;

INT_NUM : ('0'..'9')+;
    
STRING_LITERAL
  : UNTERMINATED_STRING_LITERAL '"'
  ;

UNTERMINATED_STRING_LITERAL
  : '"' (~["\\\r\n] | '\\' (. | EOF))*
  ;
  

// extension: optional BCPL style curly brackets
program: BEGIN compoundStatement END | '{' compoundStatement '}';

// In Algol, semicolon is a statement separator, not a statement terminator.
// The optional trailing ';' before 'end' is allowed (common in Algol style).
compoundStatement: statement (';' statement)* ';'?;

statement
  : label? (procedureCall | procedureDecl | varDecl | arrayDecl | switchDecl | assignment | gotoStatement | ifStatement | forStatement | block)
  ;

block
  : BEGIN compoundStatement END endComment?
  | '{' compoundStatement '}'
  ;

endComment
  : IDENT
  ;

procedureDecl
  : (INTEGER | REAL | STRING)? PROCEDURE identifier ('(' paramList? ')')? ';'
    valueSpec?
    paramSpec*
    statement
  ;

valueSpec
  : VALUE paramList ';'
  ;

paramSpec
  : paramSpecType paramList ';'
  ;

paramSpecType
  : REAL PROCEDURE    # RealProcedureParamType
  | INTEGER PROCEDURE # IntegerProcedureParamType
  | STRING PROCEDURE  # StringProcedureParamType
  | REAL ARRAY        # RealArrayParamType
  | INTEGER ARRAY     # IntegerArrayParamType
  | STRING ARRAY      # StringArrayParamType
  | BOOLEAN ARRAY     # BooleanArrayParamType
  | ARRAY             # DefaultArrayParamType
  | REAL              # RealParamType
  | INTEGER           # IntegerParamType
  | STRING            # StringParamType
  | BOOLEAN           # BooleanParamType
  | PROCEDURE         # VoidProcedureParamType
  ;

paramList
  : identifier (',' identifier)*
  ;

varDecl
  : OWN? (REAL | INTEGER | BOOLEAN | STRING | PROCEDURE) varList
  ;

arrayDecl
  : OWN? (INTEGER | REAL | BOOLEAN | STRING | PROCEDURE)? ARRAY identifier '[' boundPair (',' boundPair)* ']'
  ;

boundPair
  : signedInt ':' signedInt
  ;

switchDecl
  : SWITCH identifier ':=' designationalExpr (',' designationalExpr)*
  ;

varList
  : identifier (',' identifier)*
  ;

assignment
  : (lvalue ':=')+ expr
  ;

lvalue
  : identifier ('[' expr (',' expr)* ']')?
  ;

gotoStatement
  : GOTO designationalExpr
  ;

ifStatement
  : IF expr THEN statement (ELSE statement)?
  ;

forStatement
  : FOR identifier (':=' | '=') forList DO statement
  ;

forList
  : forElement (',' forElement)*
  ;

forElement
  : expr STEP expr UNTIL expr   # StepUntilElement
  | expr WHILE expr             # WhileElement
  | expr                        # SimpleElement
  ;

label
  : identifier ':'
  ;

designationalExpr
  : IF expr THEN simpleDesignationalExpr ELSE designationalExpr   # IfDesignationalExpr
  | simpleDesignationalExpr                                       # DirectDesignationalExpr
  ;

simpleDesignationalExpr
  : identifier '[' expr ']'   # SwitchDesignatorExpr
  | identifier                # LabelDesignatorExpr
  | '(' designationalExpr ')' # ParenDesignatorExpr
  ;

expr
  : '-' expr                             # UnaryMinusExpr
  | ('~' | NOT) expr                     # NotExpr
  | expr op=('**'|'^') expr              # PowExpr
  | expr op=('*'|'/'|DIV_KW) expr        # MulDivExpr
  | expr op=('+'|'-') expr               # AddSubExpr
  | expr op=('<'|'<='|'>'|'>='|'='|'<>') expr   # RelExpr
  | expr ('&' | AND_KW) expr             # AndExpr
  | expr ('|' | OR) expr                 # OrExpr
  | expr (IMP | '=>') expr               # ImpExpr
  | expr (EQV | '==') expr               # EqvExpr
  | IF expr THEN expr ELSE expr          # IfExpr
  | identifier '[' expr (',' expr)* ']'  # ArrayAccessExpr
  | identifier '(' argList? ')'          # ProcCallExpr
  | realLiteral                          # RealLiteralExpr
  | unsignedInt                          # IntLiteralExpr
  | string                               # StringLiteralExpr
  | identifier                           # VarExpr
  | '(' expr ')'                         # ParenExpr
  | TRUE                                 # TrueLiteralExpr
  | FALSE                                # FalseLiteralExpr
  ;

procedureCall: identifier ('(' argList? ')')?;

identifier: IDENT;

argList : arg (',' arg)*;

arg : expr | string;

unsignedInt : INT_NUM;

signedInt : '-'? unsignedInt;

realLiteral : INT_NUM '.' INT_NUM;

string
  : STRING_LITERAL
  ;

/* Keywords */
BEGIN : 'begin';
END : 'end';
REAL : 'real';
INTEGER : 'integer';
BOOLEAN : 'boolean';
STRING : 'string';
ARRAY : 'array';
PROCEDURE : 'procedure';
VALUE : 'value';
IF : 'if';
THEN : 'then';
ELSE : 'else';
FOR : 'for';
STEP : 'step';
UNTIL : 'until';
WHILE : 'while';
DO : 'do';
GOTO : 'goto';
SWITCH : 'switch';
OWN : 'own';
TRUE : 'true';
FALSE : 'false';

NOT : 'not';
AND_KW : 'and';
OR : 'or';
IMP : 'imp';
EQV : 'eqv';
DIV_KW : 'div';
/* IDENT should be defined after your other keywords */
IDENT : ('a'..'z' | 'A'..'Z')  ('a'..'z'|'A'..'Z' | '0'..'9'| '_')*;

/* TBD: Algol allowed comments after any end and before a semicolon or another "end" or "else". */

/* ignore extra whitespace */
WS : [ \t\r\n\f]+ -> skip;

NL : '\r'? '\n' | '\r';

ANY : .+?;
