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

INT_NUM : ('0'..'9')+;
    
STRING_LITERAL
  : UNTERMINATED_STRING_LITERAL '"'
  ;

UNTERMINATED_STRING_LITERAL
  : '"' (~["\\\r\n] | '\\' (. | EOF))*
  ;
  

// extension: optional BCPL style curly brackets
program: 'begin' compoundStatement 'end' | '{' compoundStatement '}';

// In Algol, semicolon is a statement separator, not a statement terminator, so no semicolon is required before the end.
compoundStatement: statement  | statement ';' compoundStatement;

statement
  : label? (procedureCall | varDecl | assignment | gotoStatement)
  ;

varDecl
  : 'real' varList
  ;

varList
  : identifier (',' identifier)*
  ;

assignment
  : identifier ':=' expr
  ;

gotoStatement
  : 'goto' identifier
  ;

label
  : identifier ':'
  ;

expr
  : expr op=('*'|'/') expr   # MulDivExpr
  | expr op=('+'|'-') expr   # AddSubExpr
  | realLiteral              # RealLiteralExpr
  | unsignedInt              # IntLiteralExpr
  | identifier               # VarExpr
  | '(' expr ')'             # ParenExpr
  ;

procedureCall: identifier '(' argList ')';

identifier: IDENT;

argList : arg (',' arg)*;

arg : unsignedInt | string;

unsignedInt : INT_NUM;

realLiteral : INT_NUM '.' INT_NUM;

string
  : STRING_LITERAL
  ;

/* IDENT should be defined after your other keywords */
IDENT : ('a'..'z' | 'A'..'Z')  ('a'..'z'|'A'..'Z' | '0'..'9'| '_')*;

/* Block comments starting with the word "comment" (or '!' from Simula) and ending with a semicolon. */
BLOCK_COMMENT
	: ('comment' | '!') ~[;]* ';' -> channel(HIDDEN)
	;
	
/* extension: TeX style single line comments (also used in Unisys Extended Algol) */
LINE_COMMENT : '%' ~[\r\n]* '\r'? '\n' -> skip ;

/* TBD: Algol allowed comments after any end and before a semicolon or another "end" or "else". */

/* ignore extra whitespace */
WS : [ \t\r\n\f]+ -> skip;

NL : '\r'? '\n' | '\r';

ANY : .+?;