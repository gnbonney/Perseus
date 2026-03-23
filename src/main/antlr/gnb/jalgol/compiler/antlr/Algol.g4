grammar Algol;
// Copyright (c) 2017-2026 Greg Bonney

/* This is an Algol 60 grammar, not Algol 68.
 * 
 * Initially I tried to convert the entire Algol 60 BNF to Antlr format, but I ran into numerous 
 * left recursion and other errors.  So, instead I am using small code samples from various sources 
 * and slowly building up a grammar that can correctly parse the code.
 * 
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
  : OWN? (INTEGER | REAL | BOOLEAN | STRING | PROCEDURE)? ARRAY identifier '[' signedInt ':' signedInt ']'
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
  : identifier ('[' expr ']')?
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
  : expr op=('*'|'/') expr               # MulDivExpr
  | expr op=('+'|'-') expr               # AddSubExpr
  | expr op=('<'|'<='|'>'|'>='|'='|'<>') expr   # RelExpr
  | expr ('&' | AND_KW) expr             # AndExpr
  | expr ('|' | OR) expr                 # OrExpr
  | '-' expr                             # UnaryMinusExpr
  | ('~' | NOT) expr                     # NotExpr
  | IF expr THEN expr ELSE expr          # IfExpr
  | identifier '[' expr ']'              # ArrayAccessExpr
  | identifier '(' argList ')'           # ProcCallExpr
  | realLiteral                          # RealLiteralExpr
  | unsignedInt                          # IntLiteralExpr
  | string                               # StringLiteralExpr
  | identifier                           # VarExpr
  | '(' expr ')'                         # ParenExpr
  | TRUE                                 # TrueLiteralExpr
  | FALSE                                # FalseLiteralExpr
  ;

procedureCall: identifier ('(' argList ')')?;

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
/* IDENT should be defined after your other keywords */
IDENT : ('a'..'z' | 'A'..'Z')  ('a'..'z'|'A'..'Z' | '0'..'9'| '_')*;

/* TBD: Algol allowed comments after any end and before a semicolon or another "end" or "else". */

/* ignore extra whitespace */
WS : [ \t\r\n\f]+ -> skip;

NL : '\r'? '\n' | '\r';

ANY : .+?;
