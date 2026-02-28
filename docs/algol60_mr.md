# Modified Report on the Algorithmic Language Algol 60

being the\
\
Revised Report on the Algorithmic Language ALGOL 60\
(dedicated to the memory of William Turanski)\
by J. W. Backus, F. L. Bauer, J. Green, C. Katz, J. McCarthy,\
P. Naur, A. J. Perlis, H. Rutishauser, K. Samelson, B. Vauquois,\
J. H. Wegstein, A. van Wijngaarden and M. Woodger\
\
as modified by\
\
R. M. De Morgan, I. D. Hill and B. A. Wichmann\
under the authority of IFIP Working Group 2.1.\
 

  --------------------------------------------------------
         If any man, who shall desire a more particular 
                                account of the several\ 
    Alterations . . . . shall take the pains to compare 
                                 the present Book with\ 
  the former; we doubt not but the reason of the change 
                                     may easily appear. 
  ----------------------------------------------------- --
                 Preface to Book of Common Prayer 1662. 

  --------------------------------------------------------

 

### Contents

- [Introduction](#Introduction)

- [Description of the reference
  language](#description-of-the-reference-language)

  - [1. Structure of the language](#1)

  - [2. Basic symbols, identifiers, numbers, and strings](#2)

  - [3. Expressions](#3)

  - [4. Statements](#4)

  - [5. Declarations](#5)

- [Apependix 1 - Subsets](#A1)

- [Apependix 2 - The environmental block](#A2)

- [References](#references)

- [Alphabetic index of definitions of concepts and syntactic
  units](#alphabetic-index-of-definitions-of-concepts-and-syntactic-units)

- [Note](#note.)

- [Note on the edition](#note-on-the-edition)

 []{#Introduction .anchor}

## Introduction

For the history of ALGOL 60 up to April 1962, the [Revised Report on
ALGOL 60](https://www.algol60.org/reports/algol60_rr.pdf)
([Naur, 1963](#Naur1963)) should be consulted.

   [\^ top](#modified-report-on-the-algorithmic-language-algol-60)\
 

## Description of the reference language

 []{#1 .anchor}

### 1. Structure of the language

The algorithmic language has two different kinds of representation --
reference and hardware -- and the development described in the sequel is
in terms of the reference representation. This means that all objects
defined within the language are represented by a given set of symbols --
and it is only in the choice of symbols that other representations may
differ. Structure and content must be the same for all representations.

 

##### Reference language

1.  It is the defining language.

2.  The characters are determined by ease of mutual understanding and
    not by any computer limitations, coder\'s notation, or pure
    mathematical notation.

3.  It is the basic reference and guide for compiler builders.

4.  It is the guide for all hardware representations.

 

##### Hardware representations

Each one of these:

1.  is a condensation of the reference language enforced by the limited
    number of characters on standard input equipment;

2.  uses the character set of a particular computer and is the language
    accepted by a translator for that computer;

3.  must be accompanied by a special set of rules for transliterating to
    or from reference language. It should be particularly noted that
    throughout the reference language underlining in typescript or
    manuscript, or boldface type in printed copy, is used to represent
    certain basic symbols (see Sections [2.2.2](#2_2_2) and
    [2.3](#delimiters)). These are understood to have no relation to the
    individual letters of which they are composed. In the reference
    language underlining or boldface is used for no other purpose.

The purpose of the algorithmic language is to describe computational
processes. The basic concept used for the description of calculating
rules is the well-known arithmetic expression containing as constituents
numbers, variables, and functions. From such expressions are compounded,
by applying rules of arithmetic composition, self-contained units of the
language -- explicit formulae -- called assignment statements.

To show the flow of computational processes, certain nonarithmetic
statements and statement clauses are added which may describe, e.g.
alternatives, or iterative repetitions of computing statements. Since it
is sometimes necessary for the function of the statements that one
statement refers to another, statements may be provided with labels. A
sequence of statements may be enclosed between the statement brackets
**[begin]{.underline}** and **[end]{.underline}** to form a compound
statement.

Statements are supported by declarations which are not themselves
computing instructions, but inform the translator of the existence and
certain properties of objects appearing in statements, such as the class
of numbers taken on as values by a variable, the dimension of an array
of numbers, or even the set of rules defining a function. A sequence of
declarations followed by a sequence of statements and enclosed between
**[begin]{.underline}** and **[end]{.underline}** constitutes a block.
Every declaration appears in a block in this way and is valid only for
that block.

A program is a block or a compound statement that is contained only
within a fictitious block (always assumed to be present and called the
environmental block), and that makes no use of statements or
declarations not contained within itself, except that it may use such
procedure identifiers and function designators as may be assumed to be
declared in the environmental block.

The environmental block contains procedure declarations for standard
functions, input and output operations, and possibly other operations to
be made available without declaration within the program. It also
contains the fictitious declaration, and initialisation, of
**[own]{.underline}** variables (see [Section 5](#5)).

In the sequel the syntax and semantics of the language will be given.

Whenever the precision of arithmetic is stated as being in general not
specified, or the outcome of a certain process is left undefined or said
to be undefined, this is to be interpreted in the sense that a program
only fully defines a computational process if the accompanying
information specifies the precision assumed, the kind of arithmetic
assumed, and the course of action to be taken in all such cases as may
occur during the execution of the computation.

 []{#1_1 .anchor}

#### 1.1 Formalism for syntactic description

The syntax will be described with the aid of metalinguistic formulae
([Backus, 1959](#Backus1959)). Their interpretation is best explained by
an example:

\<ab\> ::= ( \| \[ \| \<ab\> ( \| \<ab\> \<d\>

Sequences of characters enclosed in the bracket \<\> represent
metalinguistic variables whose values are sequences of symbols. The
marks ::= and \| (the latter with the meaning of \'or\') are
metalinguistic connectives. Any mark in a formula, which is not a
variable or a connective, denotes itself (or the class of marks which
are similar to it). Juxtaposition of marks and/or variables in a formula
signifies juxtaposition of the sequences denoted. Thus the formula above
gives a recursive rule for the formation of values of the variable
\<ab\>. It indicates that \<ab\> may have the value ( or \[ or that
given some legitimate value of \<ab\>, another may be formed by
following it with the character ( or by following it with some value of
the variable \<d\>. If the values of \<d\> are the decimal digits, some
values of \<ab\> are:

\[(((1(37(

(12345(

(((

\[86

In order to facilitate the study, the symbols used for distinguishing
the metalinguistic variables (i.e. the sequence of characters appearing
within the brackets \<\> as ab in the above example) have been chosen to
be words describing approximately the nature of the corresponding
variable. Where words which have appeared in this manner are used
elsewhere in the text they will refer to the corresponding syntactic
definition. In addition some formulae have been given in more than one
place.

Definition:

\<empty\> ::=

(i.e. the null string of symbols).

 []{#2 .anchor}

### 2. Basic symbols, identifiers, numbers, and strings

### Basic concepts

The reference language is built up from the following basic symbols:

\<basic symbol\> ::= \<letter\> \| \<digit\> \| \<logical value\> \|
\<delimiter\>

#### 2.1. Letters

\<letter\> ::= a \| b \| c \| d \| e \| f \| g \| h \| i \| j \| k \| l
\|

m \| n \| o \| p \| q \| r \| s \| t \| u \| v \| w \| x \| y \| z \| A
\|

B \| C \| D \| E \| F \| G \| H \| I \| J \| K \| L \| M \| N \| O \| P
\|

Q \| R \| S \| T \| U \| V \| W \| X \| Y \| Z

This alphabet may be arbitrarily restricted, or extended with any other
distinctive character (i.e. character not coinciding with any digit,
logical value or delimiter).

Letters do not have individual meaning. They are used for forming
identifiers and strings. They are used for forming identifiers and
strings (see Sections [2.4 Identifiers](#2_4), [2.6 Strings](#2_6)).
Within this report the letters (from an extended alphabet) , , and are
sometimes used and are understood as not being available to the
programmer. If an extended alphabet is in use, that does include any of
these letters, then their uses within this report must be systematically
changed to other letters that the extended alphabet does not include.

#### 2.2. Digits and logical values

[]{#2_2_1 .anchor}**2.2.1 Digits**

\<digit\> ::= 0 \| 1 \| 2 \| 3 \| 4 \| 5 \| 6 \| 7 \| 8 \| 9

Digits are used for forming numbers, identifiers, and strings.

[]{#2_2_2 .anchor}**2.2.2 Logical values**

\<logical value\> ::= **[true]{.underline}** \| **[false]{.underline}**

The logical values have a fixed obvious meaning.

#### 2.3. Delimiters

\<delimiter\> ::= \<operator\> \| \<separator\> \| \<bracket\> \|
\<declarator\> \| \<specificator\>

\<operator\> ::= \<arithmetic operator\> \| \<relational operator\> \|
\<logical operator\> \|

\<sequential operator\>

\<arithmetic operator\> ::= + \| - \| \| / \| ÷ \|

\<relational operator\> ::= \< \| \| = \| \| \> \|

\<logical operator\> ::= \| \| \| \| ¬

\<sequential operator\> ::= **[go to]{.underline}** \|
**[if]{.underline}** \| **[then]{.underline}** \| **[else]{.underline}**
\| **[for]{.underline}** \| **[do]{.underline}**

\<separator\> ::= , \| . \| ~10~ \| : \| ; \| := \| \|
**[step]{.underline}** \| **[until]{.underline}** \|
**[while]{.underline}** \| **[comment]{.underline}**

\<bracket\> ::= ( \| ) \| \[ \| \] \| \` \| \' \|
**[begin]{.underline}** \| **[end]{.underline}**

\<declarator\> ::= **[own]{.underline}** \| **[Boolean]{.underline}** \|
**[integer]{.underline}** \| **[real]{.underline}** \|
**[array]{.underline}** \| **[switch]{.underline}** \|
**[procedure]{.underline}**

\<specificator\> ::= **[string]{.underline}** \| **[label]{.underline}**
\| **[value]{.underline}**

Delimiters have a fixed meaning which for the most part is obvious or
else will be given at the appropriate place in the sequel.

Typographical features such as blank space or change to a new line have
no significance in the reference language. They, however, be used freely
for facilitating reading.

For the purpose of including text among the symbols of a program the
following \"comment\" conventions hold:

+-------------------------------------------+----+-------------------------+
| The sequence of basic symbols:            |    | is equivalent to        |
|                                           |    |                         |
| ; **[comment]{.underline}** \<any         |    | ;                       |
| sequence of zero or more                  |    |                         |
|                                           |    | **[begin]{.underline}** |
| characters not containing ;\>;            |    |                         |
|                                           |    | **[end]{.underline}**   |
| **[begin]{.underline}**                   |    |                         |
| **[comment]{.underline}** \<any sequence  |    |                         |
| of zero or more                           |    |                         |
|                                           |    |                         |
| characters not containing ;\>;            |    |                         |
|                                           |    |                         |
| **[end]{.underline}** \<any sequence of   |    |                         |
| zero or more                              |    |                         |
|                                           |    |                         |
| characters not containing                 |    |                         |
| **[end]{.underline}** or ; or             |    |                         |
| **[else]{.underline}**\>                  |    |                         |
+===========================================+====+=========================+

By equivalence is here meant that any of the three structures shown in
the left hand column may be replaced, in any occurrence outside of
strings, by the symbol shown on the same line in the right hand column
without any effect on the action of the program. It is further
understood that the comment structure encountered first in the text when
reading from left to right has precedence in being replaced over later
structures contained in the sequence.

 []{#2_4 .anchor}

#### 2.4. Identifiers

[]{#2_4_1 .anchor}**2.4.1. Syntax**

\<identifier\> ::= letter\> \| \<identifier\> \<letter\> \|
\<identifier\> \<digit\>

**2.4.2. Examples**

q

Soup

V17a

a34kTMNs

MARILYN

[]{#2_4_3 .anchor}**2.4.3. Semantics**\
Identifiers have no inherent meaning, but serve for the identification
of simple variables, arrays, labels, switches, and procedures. They may
be chosen freely. Identifiers also act as formal parameters of
procedures, in which capacity they may represent any of the above
entities, or a string.

The same identifier cannot be used to denote two different quantities
except when these quantities have disjoint scopes as defined by the
declarations of the program (see [Section 2.7 Quantities, kinds and
scopes](#2_7) and [Section 5 Declarations](#5)). This rule applies also
to the formal parameters of procedures, whether representing a quantity
or a string.

 

#### 2.5. Numbers

[]{#2_5_1 .anchor}**2.5.1 Syntax**

\<unsigned integer\> ::= \<digit\> \| \<unsigned integer\> \<digit\>

\<integer\> ::= \<unsigned integer\> \| + \<unsigned integer\> \| -
\<unsigned integer\>

\<decimal fraction\> ::= . \<unsigned integer\>

\<exponential part\> ::= ~10~ \<integer\>

\<decimal number\> ::= \<unsigned integer\> \| \<decimal fraction\> \|

\<unsigned integer\> \<decimal fraction\>

\<unsigned number\> ::= \<decimal number\> \| \<exponential part\> \|

\<decimal number\> \<exponential part\>

\<number\> ::= \<unsigned number\> \| + \<unsigned number\> \| -
\<unsigned number\>

**2.5.2. Examples**

0 -200.084 -.083~10~-02

177 + 07.43~10~8 -~10~7

.5384 9.34~10~+10 ~10~-4

+0.7300 2~10~-4 +~10~+5

[]{#2_5_3 .anchor}**2.5.3. Semantics**\
Decimal numbers have their conventional meaning. The exponent part is
scale factor expressed as an integral power of 10.

[]{#2_5_4 .anchor}**2.5.4. Types**\
Integers are of the type **[integer]{.underline}**. All other numbers
are of type **[real]{.underline}** (see [Section 5.1 Type
declarations](#5_1)).

 []{#2_6 .anchor}

#### 2.6. Strings

[]{#2_6_1 .anchor}**2.6.1. Syntax**

\<proper string\> ::= \<any sequence of characters not containing \` or
\' \> \| \<empty\>

\<open string\> ::= \<proper string\> \| \<proper string\> \<closed
string\> \<open string\>

\<closed string\> ::= \`\<open string\>\'

\<string\> ::= \<closed string\> \| \<closed string\> \<string\>

**2.6.2. Examples**

\`5k,,-\`\[\[\[\`=/:\'Tt\'\'

\`Thisisa\`string\'\'

\`Thisisall\'

\`onestring\'

[]{#2_6_3 .anchor}**2.6.3. Semantics**\
In order to enable the language to handle sequences of characters the
string quotes \` and \' are introduced.

The characters available within a string are a question of hardware
representation, and further rules are not given in the reference
language. However, it is recommended that visible characters, other than
and \", should represent themselves, while invisible characters other
than space should not occur within a string. To conform with Draft
ISO/TR 1672, a space may stand for itself, although in this document the
character is used to represent a space.

To allow invisible, or other exceptional characters to be used, they are
represented within either matching string quotes or a matched pair of
the \" symbol. The rules within such an inner string are unspecified, so
if such an escape mechanism is used a comment is necessary to explain
the meaning of the escape sequence.

A string of the form \<closed string\>\<string\> behaves as if it were
the string formed by deleting the closing string quote of the closed
string and the opening string quote of the following string (together
with any layout characters between them).

Strings are used as actual parameters of procedures (see Sections [3.2
Function designators](#3_2) and [4.7 Procedure statements](#4_7)).

 []{#2_7 .anchor}

#### 2.7. Quantities, kinds and scopes

The following kinds of quantities are distinguished: simple variables,
arrays, labels, switches, and procedures.

The scope of a quantity is the set of statements and expressions in
which the declaration of the identifier associated with that quantity is
valid. For labels see [Section 4.1.3](#4_1_3).

 []{#2_8 .anchor}

#### 2.8. Values and types

A value is an ordered set of numbers (special case: a single number), an
ordered set of logical values (special case: a single logical value), or
a label.

Certain of the syntactic units are said to possess values. These values
will in general change during the execution of the program. The values
of expressions and their constituents are defined in [Section 3](#3).
The value of an array identifier is the ordered set of values of the
corresponding array of subscripted variables (see [Section
3.1.4.1](#3_1_4_1)).

The various types (**[integer]{.underline}**, **[real]{.underline}**,
**[Boolean]{.underline}**) basically denote properties of values. The
types associated with syntactic units refer to the values of these
units.

 []{#3 .anchor}

### 3. Expressions

In the language the primary constituents of the programs describing
algorithmic processes are arithmetic, Boolean, and designational
expressions. Constituents of these expressions, except for certain
delimiters, are logical values, numbers, variables, function
designators, labels, switch designators, and elementary arithmetic,
relational, logical, and sequential operators. Since the syntactic
definition of both variables and function designators contains
expressions, the definition of expressions, and their constituents, is
necessarily recursive.

\<expression\> ::= \<arithmetic expression\> \|

\<Boolean expression\> \| \<designational expression\>

 

#### 3.1. Variables

[]{#3_1_1 .anchor}**3.1.1. Syntax**

\<variable identifier\> ::= \<identifier\>

\<simple variable\> ::= \<variable identifier\>

\<subscript expression\> ::= \<arithmetic expression\>

\<subscript list\> ::= \<subscript expression\> \|

\<subscript list\> , \<subscript expression\>

\<array identifier\> ::= \<identifier\>

\<subscripted value\> ::= \<array identifier\>\[\<subscripted list\>\]

\<variable\> ::= \<simple variable\> \| \<subscripted variable\>

**3.1.2. Examples**

epsilon

detA

a17

Q\[7, 2\]

x\[sin(n pi/2), Q\[3 ,n ,4\]\]

[]{#3_1_3 .anchor}**3.1.3. Semantics**\
A variable is a designation given to a single value. This value may be
used in expressions for forming other values and may be changed at will
by means of assignment statements (see [Section 4.2](#4_2)). The type of
the value of a particular variable is defined in the declaration for the
variable itself (see [Section 5.1 Type declarations](#5_1)) or for the
corresponding array identifier (see [Section 5.2 Array
declarations](#5_2)).

**3.1.4. Subscripts**\
[]{#3_1_4_1 .anchor}3.1.4.1. Subscripted variable designate values which
are components of multidimensional arrays (see [Section 5.2](#5_2) Array
declarations). Each arithmetic expression of the subscript list occupies
one subscript position of the subscripted variable and is called a
subscript. The complete list of subscripts is enclosed in the subscript
brackets \[ \]. The array component referred to by a subscripted
variable is specified by the actual numerical value of its subscripts
(see [Section 3.3 Arithmetic expressions](#3_3)).

[]{#3_1_4_2 .anchor}3.1.4.2. Each subscript position acts like a
variable of integer type and the evaluation of the subscript is
understood to be equivalent to an assignment to this fictitious variable
(see [Section 4.2.4](#4_2_4)). The value of the subscripted variable is
defined only if the value of the subscript expression is within the
subscript bounds of the array (see [Section 5.2 Array
declarations](#5_2)).

[]{#3_1_5 .anchor}**3.1.5. Initial values of variables**\
The value of a variable, not declared **[own]{.underline}**, is
undefined from entry into the block in which it is declared until an
assignment is made to it. The value of a variable declared
**[own]{.underline}** is zero (if arithmetic) or **[false]{.underline}**
(if Boolean) on first entry to the block in which it is declared and at
subsequent entries it has the same value as at the preceding exit from
the block.

 []{#3_2 .anchor}

#### 3.2. Function designators

[]{#3_2_1 .anchor}**3.2.1. Syntax**

\<procedure identifier\> ::= \<identifier\>

\<actual parameter\> ::= \<string\> \| \<expression\> \|

\<array identifier\> \| \<switch identifier\> \| \<procedure
identifier\>

\<letter string\> ::= \<letter\> \| \<letter string\> \<letter\>

\<parameter delimiter\> ::= , \| ) \<letter string\> : (

\<actual parameter list\> ::= \<actual parameter\> \|

\<actual parameter list\> \<parameter delimiter\> \<actual parameter\>

\<actual parameter part\> ::= \<empty\> \| ( \<actual parameter list\> )

\<function designator\> ::= \<procedure identifier\> \<actual parameter
part\>

**3.2.2. Examples**

sin(a - b)

J(v + s, n)

R

S(s - 5) Temperature:(T) Pressure:(P)

Compile(\`:=\') Stack:(Q)

[]{#3_2_3 .anchor}**3.2.3. Semantics**\
Function designators define single numerical or logical values which
result through the application of given sets of rules defined by a
procedure declaration (see [Section 5.4 Procedure declarations](#5_4))
to fixed sets of actual parameters. The rules governing specification of
actual parameters are given in Section 4.7 Procedure statements. Not
every procedure declaration defines rules for determining the value of a
function designator.

[]{#3_2_4 .anchor}**3.2.4. Standard functions**\
Certain standard functions and procedures are declared in the
environmental block with the following procedure identifiers:

abs, iabs, sign, entier, sqrt, sin, cos, arctan, ln, exp, inchar,
outchar, length, outstring, outterminator, stop, fault, ininteger,
outinteger, inreal, outreal, maxreal, minreal, maxint and epsilon.

For details of these functions and procedures, see the specification of
the environmental block given as [Appendix 2](#A2).

 []{#3_3 .anchor}

#### 3.3. Arithmetic expressions

[]{#3_3_1 .anchor}**3.3.1. Syntax**

\<adding operator\> ::= + \| -

\<multiplying operator\> ::= \| / \| ÷

\<primary\> ::= \<unsigned number\> \| \<variable\> \|

\<function designator\> \| ( \<arithmetic expression\> )

\<factor\> ::= \<primary\> \| \<factor\> \<primary\>

\<term\> ::= \<factor\> \| \<term\> \<multiplying operator\> \<factor\>

\<simple arithmetic expression\> ::= \<term\> \|

\<adding operator\> \<term\> \|

\<simple arithmetic expression\> \<adding operator\> \<term\>

\<if clause\> ::= **[if]{.underline}** \<Boolean expression\>
**[then]{.underline}**

\<arithmetic expression\> ::= \<simple arithmetic expression\> \|

\<if clause\> \<simple arithmetic expression\>

**[else]{.underline}** \<arithmetic expression\>

**3.3.2. Examples**

Primaries:

7.394~10~-8

sum

w\[i + 2, 8\]

cos(y + z 3)

(a - 3/y + vu8)

Factors:

omega

sum cos(y + z 3)

7.394~10~ - 8w\[i + 2, 8\](a - 3 /y + vu8)

Terms:

U

omega sumcos(y + z 3)/7.394~10~-8w\[i + 2 ,8\](a - 3/y + vu8)

Simple arithmetic expressions:

U - Yu + omega sum cos(y + z 3)/7.394~10~-8w\[i + 2 ,8\](a - 3/y + vu8)

Arithmetic expressions:

w u - Q(S + Cu)2

**[if]{.underline}** q \> 0 **[then]{.underline}** S + 3 Q/A
**[else]{.underline}** 2 S + 3 q

**[if]{.underline}** a \< 0 **[then]{.underline}** U + V
**[else]{.underline}** **[if]{.underline}** a b \> 17
**[then]{.underline}** U/V **[else]{.underline}** **[if]{.underline}** k
y **[then]{.underline}** V/U **[else]{.underline}** 0

a sin(omega t)

0.57~10~12 a\[N (N - 1)/2, 0\]

(A arctan(y) + Z)(7 + Q)

**[if]{.underline}** a \< 0 **[then]{.underline}** A/B
**[else]{.underline}** **[if]{.underline}** b = 0 **[then]{.underline}**
B/A **[else]{.underline}** z

 

[]{#3_3_3 .anchor}**3.3.3. Semantics**\
An arithmetic expression is a rule for computing a numerical value. In
the case of simple arithmetic expressions this value is obtained by
executing the indicated arithmetic operations on the actual numerical
values of the primaries of the expression, as explained in detail in
[Section 3.3.4](#3_3_4) below. The actual numerical value of a primary
is obvious in the case of numbers. For variables it is the current value
(assigned last in the dynamic sense), and for function designators it is
the value arising from the computing rules defining the procedure (see
[Section 5.4.4 Values of function designators](#5_4_4)) when applied to
the current values of the procedure parameters given in the expression.
Fi-nally, for arithmetic expression enclosed in parenthesis the value
must through a recursive analysis be expressed in terms of the values of
primaries of the other three kinds.

In the more general arithmetic expressions, which include if clauses,
one out of several simple arithmetic expressions is selected on the
basis of the actual values of the Boolean expressions (see [Section 3.4
Boolean expressions](#3_4)). This selection is made as follows: The
Boolean expressions of the if clauses are evaluated one by one in
sequence from left to right until one having the value
**[true]{.underline}** is found. The value of the arithmetic expression
is then the value if the first arithmetic expression following this
Boolean (the longest arithmetic expression found in this position is
understood). If none of the Boolean expressions has the value
**[true]{.underline}**, then the value of the arithmetic expression is
the value of expression following the final **[else]{.underline}**.

The order of evaluation of primaries within an expression is not
defined. If different orders of evaluation would produce different
results, due to the action of side effects of function designators, then
the program is undefined.

In evaluating an arithmetic expression, it is understood that all the
primaries within that expression are evaluated, except those within any
arithmetic expression that is governed by an if clause but not selected
by it. In the special case where an exit is made from a function
designator by means of a go to statement (see [Section 5.4.4](#5_4_4)),
the evaluation if the expression is abandoned, when the go to statement
is executed.

[]{#3_3_4 .anchor}**3.3.4. Operators and types**\
Apart from the Boolean expressions of clauses, the constituents of
simple arithmetic expressions must be of **[real]{.underline}** or
**[integer]{.underline}** types (see [Section 5.1 Type
declaration](#5_1)). The meaning of the basic operators and the types of
the expressions to which they lead are given by the following rules:

[]{#3_3_4_1 .anchor}3.3.4.1. The operators +, -, and have the
conventional meaning (addition, subtraction, and multiplication). The
type of the expression will by **[integer]{.underline}** if both of the
operands are of **[integer]{.underline}** type, otherwise
**[real]{.underline}**.

[]{#3_3_4_2 .anchor}3.3.4.2. The operations \<term\> / \<factor\> and
\<term\> ÷ \<factor\> both denote division. The operations are undefined
if the factor has the value zero, but are otherwise to be understood as
a multiplication of the term by the reciprocal of the factor with due
regard to the rules of precedence (see [Section 3.3.5](#3_3_5)). Thus
for example

a/b 7/(p - q) v/s

means

((((a (b^-1^)) 7) ((p - q)^-1^)) v) (s^-1^)

The operator / is defined for all four combinations of
**[real]{.underline}** and **[integer]{.underline}** types and will
yield results of **[real]{.underline}** type in any case. The operator ÷
is defined only for two operands both of **[integer]{.underline}** type
and will yield a result of **[integer]{.underline}** type. If a and b
are of **[integer]{.underline}** type, then the value of a ÷ b is given
by the function:

**[integer]{.underline}** **[procedure]{.underline}** div(a, b);
**[value]{.underline}** a, b;

**[integer]{.underline}** a, b;

**[if]{.underline}** b = 0 **[then]{.underline}**

fault( \`divbyzero\' , a)

**[else]{.underline}**

**[begin]{.underline}** **[integer]{.underline}** q, r;

q := 0; r := iabs(a);

**[for]{.underline}** r := r - iabs(b) **[while]{.underline}** r 0
**[do]{.underline}** q := q + 1;

div := **[if]{.underline}** a \< 0 b \> 0 **[then]{.underline}** -q
**[else]{.underline}** q

**[end]{.underline}** div;

[]{#3_3_4_3 .anchor}3.3.4.3. The operation \<factor\> \<factor\> denotes
exponentiation, where the factor is the base and the primary is the
exponent. Thus for example

2 n k means (2n)k

while

2 (n m) means 2(nm)

If r is of real type and x of either **[real]{.underline}** or
**[integer type]{.underline}**, then the value of xr is given by the
function:

**[real]{.underline}** **[procedure]{.underline}** expr(x, r);
**[value]{.underline}** x, r;

**[real]{.underline}** x, r;

**[if]{.underline}** x \> 0.0 **[then]{.underline}**

expr := exp(r ln(x))

**[else]{.underline}** **[if]{.underline}** x = 0.0 r \> 0.0
**[then]{.underline}**

expr := 0.0

**[else]{.underline}**

fault( \`exprundefined\' , x)

If i and j are both of **[integer]{.underline}** type, then the value of
ij is given by the function:

**[integer]{.underline}** **[procedure]{.underline}** expi(i, j);
**[value]{.underline}** i, j;

**[integer]{.underline}** i, j;

**[if]{.underline}** j \< 0 i = 0 j = 0 **[then]{.underline}**

fault( \`expiundefined\' , j)

**[else]{.underline}**

**[begin]{.underline}**

**[integer]{.underline}** k, result;

result := 1;

**[for]{.underline}** k := 1 **[step]{.underline}** 1
**[until]{.underline}** j **[do]{.underline}**

result := result i;

expi := result

**[end]{.underline}** expi

If n is of **[integer]{.underline}** type and x of
**[real]{.underline}** type, then the value of xn is given by the
function:

**[real]{.underline}** **[procedure]{.underline}** expn(x, n);
**[value]{.underline}** x, n;

**[real]{.underline}** x; **[integer]{.underline}** n;

**[if]{.underline}** n = 0 x = 0.0 **[then]{.underline}**

fault( \`expnundefined\' , x)

**[else]{.underline}**

**[begin]{.underline}**

**[real]{.underline}** result; **[integer]{.underline}** i;

result := 1.0;

**[for]{.underline}** i := iabs(n) **[step]{.underline}** -1
**[until]{.underline}** 1 **[do]{.underline}**

result := result x;

expn := **[if]{.underline}** n \< 0 then 1.0/result
**[else]{.underline}** result

**[end]{.underline}** expn

The call of the procedure fault denotes that the action of the program
is undefined. It is understood that the finite deviations (see [Section
3.3.6](#3_3_6)) of using the exponentiation operator may be different
from those of using the procedures expr and expn.

3.3.4.4. Type of a conditional expression\
The type of an arithmetic expression of the form

**[if]{.underline}** B **[then]{.underline}** SAE **[else]{.underline}**
AE

does not depend upon the value of B. The expression is of
**[real]{.underline}** type if either SAE or AE is
**[real]{.underline}** and is of **[integer]{.underline}** type
otherwise.

[]{#3_3_5 .anchor}**3.3.5. Precedence of operators**\
The sequence of operations within one expression is generally from left
to right, with the following additional rules:

3.3.5.1. According to the syntax given in [Section 3.3.1](#3_3_1) the
following rules of precedence hold:

first:

second: / ÷

third: + -

[]{#3_3_5_2 .anchor}3.3.5.2. The expression between a left parenthesis
and the matching right parenthesis is evaluated by itself and this value
is used in subsequent calculations. Consequently the desired order of
execution of operations within an expression can always be arranged by
appropriate positioning of parentheses.

[]{#3_3_6 .anchor}**3.3.6. Arithmetics of [real]{.underline}
quantities.**\
Numbers and variables of **[real]{.underline}** type must be interpreted
in the sense of numerical analysis, i.e. as entities defined inherently
with only a finite accuracy. Similarly, the possibility of the
occurrence of a finite deviation from the mathematically defined result
in any arithmetic expression is explicitly understood. No exact
arithmetic will be specified, however, and it is indeed understood that
different implementations may evaluate arithmetic expressions
differently. The control of the possible consequences of such
differences must be carried out by the methods of numerical analysis.
This control must be considered a part of the process to be described,
and will therefore be expressed in terms of the language itself.

 []{#3_4 .anchor}

#### 3.4. Boolean expressions

[]{#3_4_1 .anchor}**3.4.1. Syntax**

\<relational operator\> ::= \< \| \| = \| \| \> \|

\<relation\> ::= \<simple arithmetic expression\>

\<relational operator\> \<simple arithmetic expression\>

\<Boolean primary\> ::= \<logical value\> \| \<variable\> \|

\<function designator\> \| \<relation\> \| ( \<Boolean expression\> )

\<Boolean secondary\> ::= \<Boolean primary\> \|

¬ \<Boolean primary\>

\<Boolean factor\> ::= \<Boolean secondary\> \|

\<Boolean factor\> \<Boolean secondary\>

\<Boolean term\> ::= \<Boolean factor\> \|

\<Boolean term\> \<Boolean factor\>

\<implication\> ::= \<Boolean term\> \|

\<implication\> \<Boolean term\>

\<simple Boolean\> ::= \<implication\> \| \<simple Boolean\>

\<implication\>

\<Boolean expression\> ::= \<simple Boolean\> \| \<if clause\>

\<simple Boolean\> **[else]{.underline}** \<Boolean expression\>

**3.4.2. Examples**

x = -2

Y \> V z \< q

a + b \> -5 z - d \> q2

p q x y

g ¬a b ¬c d e ¬f

**[if]{.underline}** k \< 1 **[then]{.underline}** s \< w
**[else]{.underline}** h c

**[if]{.underline}** **[if]{.underline}** **[if]{.underline}** a
**[then]{.underline}** b **[else]{.underline}** c **[then]{.underline}**
d **[else]{.underline}** f **[then]{.underline}** g
**[else]{.underline}** h \< k

[]{#3_4_3 .anchor}**3.4.3. Semantics**\
A Boolean expression is a rule for computing a logical value. The
principles of evaluation are entirely analogous to those given for
arithmetic expressions in [Section 3.3.3](#3_3_3).

**3.4.4. Types**\
Variables and function designators entered as Boolean primaries must be
declared **[Boolean]{.underline}** (see [Section 5.1. Type
declarations](#5_1) and [Section 5.4.4. Value of function
designators](#5_4_4)).

[]{#3_4_5 .anchor}**3.4.5. The operators**\
The relational operators \<, , =, , \> and have their conventional
meaning (less than, less than or equal to, equal to, greater than or
equal to, greater than, not equal to). Relations take on the value
**[true]{.underline}** whenever the corresponding relation is satisfied
for the expressions involved, otherwise **[false]{.underline}**.

The meaning of the logical operators ¬ (not), (and), (or), (implies),
and (equivalent), is given by the following function table.

  ------------------------------------------------------------------------------------------------------------------
  b1\    **[false]{.underline}**\   **[false]{.underline}**\   **[true]{.underline}**\    **[true]{.underline}**\
  b2     **[false]{.underline}**    **[true]{.underline}**     **[false]{.underline}**    **[true]{.underline}**
  ------ -------------------------- -------------------------- -------------------------- --------------------------
  ¬b1\   **[true]{.underline}**\    **[true]{.underline}**\    **[false]{.underline}**\   **[false]{.underline}**\
  b1 b2\ **[false]{.underline}**\   **[false]{.underline}**\   **[false]{.underline}**\   **[true]{.underline}**\
  b1 b2\ **[false]{.underline}**\   **[true]{.underline}**\    **[true]{.underline}**\    **[true]{.underline}**\
  b1 b2\ **[true]{.underline}**\    **[true]{.underline}**\    **[false]{.underline}**\   **[true]{.underline}**\
  b1 b2  **[true]{.underline}**     **[false]{.underline}**    **[false]{.underline}**    **[true]{.underline}**

  ------------------------------------------------------------------------------------------------------------------

 

**3.4.6. Precedence of operators**\
The sequence of operations within one expression is generally from left
to right, with the following additional rules:

3.4.6.1. According to the syntax given in [Section 3.4.1](#3_4_1) the
following rules of precedence hold:

  ------------------------------------------------
  first:      arithmetic expressions according to
              [Section 3.3.5](#3_3_5).
  ----------- ------------------------------------
  second:     \< = \>

  third:      ¬

  fourth:     

  fifth:      

  sixth:      

  seventh:    
  ------------------------------------------------

3.4.6.2. The use of parentheses will be interpreted in the sense given
in [Section 3.3.5.2](#3_3_5_2).

 []{#3_5 .anchor}

#### 3.5. Designational expressions

[]{#3_5_1 .anchor}**3.5.1. Syntax**

\<label\> ::= \<identifier\> \| \<unsigned integer\>

\<switch identifier\> ::= \<identifier\>

\<switch designator\> ::= \<switch identifier\>\[\<subscript
expression\>\]

\<simple designational expression\> ::= \<label\> \|

\<switch designator\> \| (\<designational expression\>)

\<designational expression\> ::= \<simple designational expression\> \|

\<if clause\> \<simple designational expression\>

**[else]{.underline}** \<designational expression\>

**3.5.2. Examples**

17

p9

Choose\[n - 1\]

Town \[**[if]{.underline}** y \< 0 **[then]{.underline}** N
**[else]{.underline}** N + 1\]

**[if]{.underline}** Ab \< c **[then]{.underline}** 17
**[else]{.underline}** q\[**[if]{.underline}** w 0
**[then]{.underline}** 2 **[else]{.underline}** n\]

[]{#3_5_3 .anchor}**3.5.3. Semantics**\
A designational expression is a rule for obtaining a label of a
statement (see [Section 4 Statements](#4)). Again the principle of the
evaluation is entirely analogous to that of arithmetic expressions (see
[Section 3.3.3](#3_3_3)). In the general case the Boolean expressions of
the if clauses will select a simple designational expression. If this a
label the desired result is already found. A switch designator refers to
the corresponding switch declaration (see [Section 5.3 Switch
declarations](#5_3)) and by the actual numerical value of its subscript
expression selects one of the designational expressions listed in the
switch declaration by counting these from left to right. Since the
designational expression thus selected may again be a switch designator
this evaluation is obviously a recursive process.

**3.5.4. The subscript expression**\
The evaluation of the subscript expression is analogous to that of
subscripted variables (see [Section 3.1.4.2](#3_1_4_2)). The value of a
switch designator is defined only if the subscript expression assumes
one of the positive values 1, 2, 3, \..., n, where n is the number of
entries in the switch list.

 []{#4 .anchor}

### 4. Statements

The units of operation within the language are called statements. They
will normally be executed consecutively as written. However, this
sequence of operations may be broken by go to statements, which define
their successor explicitly, shortened by conditional statements, which
may cause certain statements to be skipped, and lengthened by for
statements which cause certain statements to be repeated.

In order to make it possible to define a specific dynamic succession,
statements may be provided with labels.

Since sequences of statements may be grouped together into compound
statements and blocks the definition of statement must necessarily be
recursive. Also since declarations, described in [Section 5](#5), enter
fundamentally into the syntactic structure, the syntactic definition of
statements must suppose declarations to be already defined.

 

#### 4.1. Compound statements and blocks

[]{#4_1_1 .anchor}**4.1.1 Syntax**

\<unlabelled basic statement\> ::= \<assignment statement\> \|

\<go to statement\> \| \<dummy statement\> \| \<procedure statement\>

\<basic statement\> ::= \<unlabelled basic statement\> \|

\<label\>: \<basic statement\>

\<unconditional statement\> ::= \<basic statement\> \|

\<compound statement\> \| \<block\>

\<statement\> ::= \<unconditional statement\> \|

\<conditional statement\> \| \<for statement\>

\<compound tail\> ::= \<statement\> **[end]{.underline}** \|

\<statement\> ; \<compound tail\>

\<block head\> ::= **[begin]{.underline}** \<declaration\> \|

\<block head\> ; \<declaration\>

\<unlabelled compound\> ::= **[begin]{.underline}** \<compound tail\>

\<unlabelled block\> ::= \<block head\> ; \<compound tail\>

\<compound statement\> ::= \<unlabelled compound\> \|

\<label\>: \<compound statement\>

\<block\> ::= \<unlabelled block\> \| \<label\>: \<block\>

\<program\> ::= \<block\> \| \<compound statement\>

This syntax may be illustrated as follows: Denoting arbitrary
statements, declarations, and labels, by the letters S, D, L,
respectively, the basic syntactic units take the forms:

Compound statement:

L:L: \... **[begin]{.underline}** S; S; \... S; S **[end]{.underline}**

Block:

L:L: \... **[begin]{.underline}** D; D; .. D; S; S; \... S; S
**[end]{.underline}**

It should be kept in mind that each of the statements S may again be a
complete compound statement or a block.

[]{#4_1_2 .anchor}**4.1.2. Examples**

Basic statements:

a := p + q

**[go]{.underline}** **[to]{.underline}** Naples

START:CONTINUE: W := 7.993

Compound statements:

**[begin]{.underline}** x := 0; **[for]{.underline}** y := 1
**[step]{.underline}** 1 **[until]{.underline}** n **[do]{.underline}**
x := x + A\[y\];

**[if]{.underline}** x \> q **[then]{.underline}** **[go]{.underline}**
**[to]{.underline}** STOP

**[else]{.underline}** **[if]{.underline}** x \> w-2
**[then]{.underline}** **[go]{.underline}** **[to]{.underline}** S;

Aw: St: W := x + bob

**[end]{.underline}**

Block:

Q: **[begin]{.underline}** **[integer]{.underline}** i, k;
**[real]{.underline}** w;

**[for]{.underline}** i:=1 **[step]{.underline}** 1
**[until]{.underline}** m **[do]{.underline}**

**[for]{.underline}** k := i + 1 **[step]{.underline}** 1
**[until]{.underline}** m **[do]{.underline}**

**[begin]{.underline}** w := A\[i, k\];

A\[i, k\] := A\[k, i\];

A\[k, i\] := w

**[end]{.underline}** for i and k

**[end]{.underline}** block Q

[]{#4_1_3 .anchor}**4.1.3. Semantics**\
Every block automatically introduces a new level of nomenclature. This
is realised as follows: Any identifier occurring within the block may
through a suitable declaration (see [Section 5 Declarations](#5)) be
specified to be local to the block in question. This means *(a)* that
the entity specified by this identifier inside the block has no
existence outside it and *(b)* that any entity represented by this
identifier outside the block is completely inaccessible inside the
block.

Identifiers (except those representing labels) occurring within a block
and not being declared to this block will be non-local to it, i.e. will
represent the same entity inside the block and in the level immediately
outside it. A label separated by a colon from a statement, i.e.
labelling that statement, behaves as though declared in the head if the
smallest embracing block, i.e. the smallest block whose brackets
**[begin]{.underline}** and **[end]{.underline}** enclose that
statement.

A label is said to be implicitly declared in this block head, as
distinct from the explicit declaration of all other local identifiers.
In this context a procedure body, or the statement following a for
clause, must be considered as if it were enclosed by begin and end and
treated as a block, this block being nested within the fictitious block
of [Section 4.7.3.1](#4_7_3_1), in the case of a procedure with
parameters by value. A label that is not within any block of the program
(nor with a procedure body, or the statement following a for clause) is
implicitly declared in the head of the environmental block.

Since a statement of a block may again itself be a block the concepts
local and non-local to a block must be understood recursively. Thus an
identifier which is non-local to a block A, may or may not be non-local
to the block B in which A is one statement.

 []{#4_2 .anchor}

#### 4.2. Assignment statements

[]{#4_2_1 .anchor}**4.2.1. Syntax**

\<destination\> ::= \<variable\> \| \<procedure identifier\>

\<left part\> ::= \<destination\> :=

\<left part list\> ::= \<left part\> \| \<left part list\> \<left part\>

\<assignment statement\> ::= \<left part list\> \<arithmetic
expression\> \|

\<left part list\> \<Boolean expression\>

**4.2.2. Examples**

s := p\[0\] := n := n + 1 + s

n := n + 1

A := B/C - v - q S

S\[v, k + 2\] := 3 - arctan(s zeta)

V := Q \> Y Z

[]{#4_2_3 .anchor}**4.2.3. Semantics**\
Assignment statements serve for assigning the value of an expression to
one or several destinations. Assignment to a procedure identifier may
only occur within the body of a procedure defining the value of the
function designator denoted by that identifier (see [Section
5.4.4](#5_4_4)). If assignment is made to a subscripted variable, the
values of all the subscripts must lie within the appropriate subscript
bounds. Otherwise the action of the program becomes undefined.\
The process will in the general case be understood to take place in
three steps as follows:

[]{#4_2_3_1 .anchor}4.2.3.1. Any subscript expressions occurring in the
destinations are evaluated in sequence from left to right.

4.2.3.2. The expression of the statement is evaluated.

4.2.3.3. The value of the expression is assigned to all the
destinations, with any subscript expres-sions having values as evaluated
in step [4.2.3.1](#4_2_3_1).

[]{#4_2_4 .anchor}**4.2.4. Types**\
The type associated with all destinations of a left part list must be
the same. If this type is Boolean, the expression must likewise be
Boolean. If the type is real or integer, the expression must be
arithmetic.\
If the type of the arithmetic expression differs from that associated
with the destinations, an appropriate transfer function is understood to
be automatically invoked. For transfer from real to integer type the
transfer function is understood to yield a result which is the largest
integral quantity not exceeding E + 0.5 in the mathematical sense (i.e.
without rounding error) where E is the value of the expression. It
should be noted that E, being of real type, is defined with only finite
accuracy (see [Section 3.3.6](#3_3_6)). The type associated with a
procedure identifier is given by the declarator which appears as the
first symbol of the corresponding procedure declaration (see [Section
5.4.4](#5_4_4)).

 

#### 4.3. Go to statements

[]{#4_3_1 .anchor}**4.3.1. Syntax**

\<go to statement\> ::= **[go]{.underline}** **[to]{.underline}**
\<designational expression\>

**4.3.2. Examples**

**[go]{.underline}** **[to]{.underline}** L8

**[go]{.underline}** **[to]{.underline}** exit\[n + 1\]

**[go]{.underline}** **[to]{.underline}** Town\[**[if]{.underline}** y
\< 0 **[then]{.underline}** N **[else]{.underline}** N + 1\]

**[go]{.underline}** **[to]{.underline}** **[if]{.underline}** Ab \< c
**[then]{.underline}** L17 **[else]{.underline}** q
\[**[if]{.underline}** w \< 0 **[then]{.underline}** 2
**[else]{.underline}** n\]

[]{#4_3_3 .anchor}**4.3.3. Semantics**\
A go to statement interrupts the normal sequence of operations, by
defining its successor explicitly by the value of a designational
expression. Thus the next statement to be executed will be the one
having this value as its label.

**4.3.4. Restriction**\
Since labels are inherently local, no go to statement can lead from
outside into a block. A go to statement may, however, lead from outside
into a compound statement.

**4.3.5. Go to an undefined switch designator**\
A go to statement is undefined if the designational expression is a
switch designator whose value is undefined.

 

#### 4.4. Dummy statements

[]{#4_4_1 .anchor}**4.4.1. Syntax**

\<dummy statement\> ::= \<empty\>

**4.4.2. Examples**

L:

**[begin]{.underline}** *statements*; John: **[end]{.underline}**

[]{#4_4_3 .anchor}**4.4.3. Semantics**\
A dummy statement executes no operation. It may serve to place a label.

 

#### 4.5. Conditional statements

[]{#4_5_1 .anchor}**4.5.1. Syntax**

\<if clause\> ::= **[if]{.underline}** \<Boolean expression\>
**[then]{.underline}**

\<unconditional statement\> ::= \<basic statement\> \|

\<compound statement\> \| \<block\>

\<if statement\> ::= \<if clause\> \<unconditional statement\>

\<conditional statement\> ::= \<if statement\> \|

\<if statement\> **[else]{.underline}** \<statement\> \|

\<if clause\> \<for statement\> \|

\<block\>: \<conditional statement\>

**4.5.2. Examples**

**[if]{.underline}** x \> 0 **[then]{.underline}** n := n + 1

**[if]{.underline}** v \> u **[then]{.underline}** V: q := n + m
**[else]{.underline}** **[go]{.underline}** **[to]{.underline}** R

**[if]{.underline}** s \< 0 PQ **[then]{.underline}**

AA: **[begin]{.underline}** **[if]{.underline}** q \< v
**[then]{.underline}** a := v/s

**[else]{.underline}** y := 2 a

**[end]{.underline}**

**[else]{.underline}** **[if]{.underline}** v \> s
**[then]{.underline}** a := v - q

**[else]{.underline}** **[if]{.underline}** v \> s - 1
**[then]{.underline}** **[go]{.underline}** **[to]{.underline}** S

[]{#4_5_3 .anchor}**4.5.3. Semantics**\
Conditional statements cause certain statements to be executed or
skipped depending on the running values of specified Boolean
expressions.

[]{#4_5_3_1 .anchor}**4.5.3.1. If statement**\
An if statement is of the form

**[if]{.underline}** B **[then]{.underline}** Su

where B is a Boolean expression and Su is an unconditional statement. In
execution, B is evaluated; if the result is **[true]{.underline}**, Su
is executed; if the result is false, Su is not executed.\
If Su contains a label, and a go to statement leads to the label, then B
is not evaluated, and the computation continues with execution of the
labelled statement.

[]{#4_5_3_2 .anchor}**4.5.3.2. Conditional statement**\
Three forms of unlabelled conditional statements exist, namely:

**[if]{.underline}** B **[then]{.underline}** Su

**[if]{.underline}** B **[then]{.underline}** Sfor

**[if]{.underline}** B **[then]{.underline}** Su else S

where Su is an unconditional statement, for is a for statement, and S is
a statement.\
The meaning of the first form is given in [Section 4.5.3.1](#4_5_3_1).
The second form is equivalent to

**[if]{.underline}** B **[then]{.underline}** **[begin]{.underline}**
Sfor **[end]{.underline}**

The third form is equivalent to

**[begin]{.underline}**

**[if]{.underline}** B **[then]{.underline}** **[begin]{.underline}**
Su; **[go]{.underline}** **[to]{.underline}** G **[end]{.underline}**;

: **[end]{.underline}**

(For the use of see [Section 2.1](#letters) Letters.) If S is
conditional and also of this form, a different label must be used
instead of following the same rule.

**4.5.4. Go to into a conditional statement**\
The effect of a **[go]{.underline}** **[to]{.underline}** statement
leading into a conditional statement follows directly from the above
explanation of the execution of conditional statement..

 []{#4_6 .anchor}

#### 4.6. For statements

[]{#4_6_1 .anchor}**4.6.1. Syntax**

\<for list element\> ::= \<arithmetic expression\> \|

\<arithmetic expression\> **[step]{.underline}** \<arithmetic
expression\>

**[until]{.underline}** \<arithmetic expression\> \|

\<arithmetic expression\> **[while]{.underline}** \<Boolean expression\>

\<for list\> ::= \<for list element\> \| \<for list\> ,

\<for list element\>

\<for clause\> ::= **[for]{.underline}** \<variable\> := \<for list\>
**[do]{.underline}**

\<for statement\> ::= \<for clause\> \<statement\> \|

\<label\>: \<for statement\>

**4.6.2. Examples**

**[for]{.underline}** q := 1 **[step]{.underline}** s
**[until]{.underline}** n **[do]{.underline}** A\[q\] := B\[q\]

**[for]{.underline}** k := 1, V1 2 **[while]{.underline}** V1 \< N
**[do]{.underline}**

**[for]{.underline}** j := I + G, L, 1 **[step]{.underline}** 1
**[until]{.underline}** N, C + D **[do]{.underline}**

A\[k, j\] := B\[k, j\]

[]{#4_6_3 .anchor}**4.6.3. Semantics**\
A for clause causes the statement S which it precedes to be repeatedly
executed zero or more times. In addition it performs a sequence of
assignments to its controlled variable (the variable after
**[for]{.underline}**). The controlled variable must be of real or
integer type.

[]{#4_6_4 .anchor}**4.6.4. The for list elements**\
If the for list contain more than one element then

**[for]{.underline}** V := X, Y **[do]{.underline}** S

where X is a for list element, and Y is a for list (which may consist of
one element or more), is equivalent to

**[begin]{.underline}**

**[procedure]{.underline}** S; S;

**[for]{.underline}** V := X **[do]{.underline}** ;

**[for]{.underline}** V := Y **[do]{.underline}**

**[end]{.underline}**

(For the use of see [Section 2.1 Letters](#letters).)

[]{#4_6_4_1 .anchor}**4.6.4.1. Arithmetic expression**\
If X is an arithmetic expression then

**[for]{.underline}** V := X **[do]{.underline}** S

is equivalent to

**[begin]{.underline}**

V := X; S

**[end]{.underline}**

where S is treated as if it were a block (see [Section 4.1.3](#4_1_3)).

[]{#4_6_4_2 .anchor}**4.6.4.2. Step-until element**\
If A, B, and C are arithmetic expressions then

**[for]{.underline}** V := A **[step]{.underline}** B
**[until]{.underline}** C **[do]{.underline}** S

is equivalent to

**[begin]{.underline}** \<type of B\> ;

V := A;

: := B;

**[if]{.underline}** (V - C) sign() 0 **[then]{.underline}**

**[begin]{.underline}**

S; V := V + ;

**[go]{.underline}** **[to]{.underline}**

**[end]{.underline}**

**[end]{.underline}**

where S is treated as if it were a block (see [Section 4.1.3](#4_1_3)).

In the above, \<type of B\> must be replaced by real or integer
according to the type of B. (For the use of and see [Section 2.1
Letters](#letters).)

[]{#4_6_4_3 .anchor}**4.6.4.3. While element**\
If E is an arithmetic expression and F is a Boolean expression then

**[for]{.underline}** V := E **[while]{.underline}** F
**[do]{.underline}** S

is equivalent to

**[begin]{.underline}**

: V := E;

**[if]{.underline}** F **[then]{.underline}**

**[begin]{.underline}**

S; **[go]{.underline}** **[to]{.underline}** G

**[end]{.underline}**

**[end]{.underline}**

where both S and the outermost compound statement of the above expansion
are treated as if they were blocks (see [Section 4.1.3](#4_1_3)). (For
the use of see [Section 2.1 Letters](#letters).)

**4.6.5. The value of the controlled variable upon exit**\
Upon exit from the for statement, either through a go to statement, or
by exhaustion of the for list, the controlled variable retains the last
value assigned to it.

**4.6.6. Go to leading into a for statement**\
The statement following a for clause always acts like a block, whether
it has the form of one or not.\
Consequently the scope of any label within this statement can never
extend beyond the statement.

 []{#4_7 .anchor}

#### 4.7. Procedure statements

[]{#4_7_1 .anchor}**4.7.1. Syntax**

\<actual parameter\> ::= \<string\> \| \<expression\> \|

\<array identifier\> \| \<switch identifier\> \| \<procedure
identifier\>

\<letter string\> ::= \<letter\> \| \<letter string\> \<letter\>

\<parameter delimiter\> ::= , \| ) \<letter string\> : (

\<actual parameter list\> ::= \<actual parameter\> \|

\<actual parameter list\> \<parameter delimiter\> \<actual parameter\>

\<actual parameter part\> ::= \<empty\> \| ( \<actual parameter list\> )

\<procedure statement\> ::= \<procedure identifier\> \<actual parameter
part\>

**4.7.2. Examples**

Spur(A) Order:(7) Result to:(V)

Transpose(W, v + 1)

Absmax(A, N, M, Yy, I, K)

Innerproduct(A\[t, P, u\], B\[P\], 10, P, Y)

These examples correspond to examples given in [Section 5.4.2](#5_4_2).

[]{#4_7_3 .anchor}**4.7.3. Semantics**\
A procedure statement serves to invoke (call for) the execution of a
procedure body (see [Section 5.4 Procedure declarations](#5_4)). Where
the procedure body is a statement written in ALGOL the effect of this
execution will be equivalent to the effect of performing the following
operations on the program at the time of execution of the procedure
statement:

[]{#4_7_3_1 .anchor}**4.7.3.1. Value assignment (call by value)**\
All formal parameters quoted in the value part of the procedure heading
(see [Section 5.4](#5_4)) are assigned the values (see [Section 2.8
Values and types](#2_8)) of the corresponding actual parameters, these
assignments being considered as being performed explicitly before
entering the procedure body. The effect is as though an additional block
embracing the procedure body were created in which these assignments
were made to variables local to this fictitious block with types as
given in the corresponding specifications (see [Section 5.4.5](#5_4_5)).
As a consequence, variables called by value are to be considered as
non-local to the body of the procedure, but local to the fictitious
block (see [Section 5.4.3](#5_4_3)).

[]{#4_7_3_2 .anchor}**4.7.3.2. Name replacement (call by name)**\
Any formal parameter not quoted in the value list is replaced,
throughout the procedure body, by the corresponding actual parameter,
after enclosing this latter in parentheses if it is an expression but
not a variable. Possible conflicts between identifiers inserted through
this process and other identifiers already present within the procedure
body will be avoided by suitable systematic changes of the formal or
local identifiers involved.

If the actual and formal parameters are of different arithmetic types,
then the appropriate type conversion must take place, irrespective of
the context of use of the parameter.

**4.7.3.3. Body replacement and execution**\
Finally the procedure body, modified as above, is inserted in place of
the procedure statement and executed. If the procedure is called from a
place outside the scope of any non-local quantity of the procedure body
the conflicts between identifiers inserted through this process of body
replacement and the identifiers whose declarations are valid at the
place of the procedure statement or function designator will be avoided
through suitable systematic changes of the latter identifiers.

**4.7.4. Actual-formal correspondence**\
The correspondence between the actual parameters of the procedure
statement and formal parameters of the procedure heading is established
as follows: The actual parameter list of the procedure statement must
have the same number of entries as the formal parameter list of the
procedure declaration heading. The correspondence is obtained by taking
the entries of these two lists in the same order.

**4.7.5. Restrictions**\
For a procedure statement to be defined it is evidently necessary that
the operations on the procedure body defined in Sections
[4.7.3.1](#4_7_3_1) and [4.7.3.2](#4_7_3_2) lead to a correct ALGOL
statement.

This imposes the restriction on any procedure statement that the kind
and type of each actual parameter be compatible with the kind and type
of the corresponding formal parameter. Some important particular cases
of this general rule, and some additional restrictions, are the
following:

4.7.5.1. If a string is supplied as an actual parameter in a procedure
statement or function designator, whose defining procedure body is an
Algol 60 statement (as opposed to non-Algol code, see [Section
4.7.8](#4_7_8)), then this string can only be used within the procedure
body as an actual parameter in further procedure calls. Ultimately it
can only be used by a procedure body expressed in non-Algol code.

[]{#4_7_5_2 .anchor}4.7.5.2. A formal parameter which occurs as a left
part variable in an assignment statement within the procedure body and
which is not called by value can only correspond to an actual parameter
which is a variable (special case of expression).

[]{#4_7_5_3 .anchor}4.7.5.3. A formal parameter which is used within the
procedure body as an array identifier can only correspond to an actual
parameter which is an array identifier of an array of the same
dimensions. In addition if the formal parameter is called by value the
local array created during the call will have the same subscript bounds
as the actual array.

Similarly the number, kind and type of any parameter of a formal
procedure parameter must be compatible with those of the actual
parameter.

[]{#4_7_5_4 .anchor}4.7.5.4. A formal parameter which is called by value
cannot in general correspond to a switch identifier or a procedure
identifier or a string, because latter do not posses values (the
exception is the procedure identifier of a procedure declaration which
has an empty formal parameter part (see [Section 5.4.1](#5_4_1)) and
which defines the value of a function designator (see [Section
5.4.4](#5_4_4)). This procedure identifier is in itself a complete
expression).

4.7.5.5. Restrictions imposed by specifications of formal parameters
must be observed. The correspondence between actual and formal
parameters should be in accordance with the following table.

  ---------------------------------------------------------------------------
  *Formal          *Mode*  *Actual parameter*
  parameter*               
  --------------- -------- --------------------------------------------------
  integer          value   arithmetic expression

                    name   arithmetic expression (see [4.7.5.2](#4_7_5_2))

  real             value   arithmetic expression

                    name   arithmetic expression (see [4.7.5.2](#4_7_5_2))

  Boolean          value   Boolean expression

                    name   Boolean expression (see [4.7.5.2](#4_7_5_2))

  label            value   designational expression

                    name   designational expression

  integer array    value   arithmetic array (see 4.7.5.3)

                    name   integer array (see [4.7.5.3](#4_7_5_3))

  real array       value   arithmetic array (see 4.7.5.3)

                    name   real array (see [4.7.5.3](#4_7_5_3))

  Boolean array    value   Boolean array (see [4.7.5.3](#4_7_5_3))

                    name   Boolean array (see [4.7.5.3](#4_7_5_3))

  typeless          name   arithmetic procedure, or typeless procedure, or
  procedure                Boolean procedure (see [4.7.5.3](#4_7_5_3))

  integer           name   arithmetic procedure (see [4.7.5.3](#4_7_5_3))
  procedure                

  real procedure    name   arithmetic procedure (see [4.7.5.3](#4_7_5_3))

  Boolean           name   Boolean procedure (see [4.7.5.3](#4_7_5_3))
  procedure                

  switch            name   switch

  string            name   actual string or string identifier
  ---------------------------------------------------------------------------

If the actual parameter is itself a formal parameter the correspondence
(as in the above table) must be with the specification of the immediate
actual parameter rather than with the declaration of the ultimate actual
parameter.

[]{#4_7_6 .anchor}**4.7.6. Label parameters**\
A label may be called by value, even though variables of type label do
not exist.

[]{#4_7_7 .anchor}**4.7.7. Parameter delimiters**\
All parameter delimiters are understood to be equivalent. No
correspondence between the parameter delimiters used in a procedure
statement and those used in the procedure heading is expected beyond
their number being the same. Thus the information conveyed by using the
elaborate ones is entirely optional.

[]{#4_7_8 .anchor}**4.7.8. Procedure body expressed in code**\
The restrictions imposed on a procedure statement calling a procedure
having its body expressed in non-ALGOL code evidently can only be
derived from the characteristics of the code used and the intent of the
user and thus fall outside the scope of the reference language.

[]{#4_7_9 .anchor}**4.7.9. Standard procedures**\
Ten standard procedures are defined, which are declared in the
environmental block in an identical manner to the standard functions.
These procedures are: inchar, outchar, outstring, ininteger, inreal,
outinteger, outreal, outterminator, fault and stop. The input/output
procedures identify physical devices or files by means of channel
numbers which appear as the first parameter. The method by which this
identification is achieved is outside the scope of this report. Each
channel is regarded as containing a sequence of characters; basic method
of accessing or assigning these characters being via the procedures
inchar and outchar.

The procedures inreal and outreal are converses of each other in the
sense that a channel containing characters from successive calls of
outreal can be re-input by the same number of calls of inreal, and some
accuracy may be lost. The procedures ininteger and outinteger are also a
pair, but no accuracy can be lost. The procedure outterminator is called
at the end of each of the procedure outreal and outinteger. Its action
is machine dependent but it must ensure separation between successive
output of numeric data.

Possible implementation of these additional procedures are given in
[Appendix 2](https://algol60.org/) as examples
to illustrate the environmental block.

 []{#5 .anchor}

### 5. Declarations

Declarations serve to define certain properties of the quantities used
in the program, and to associate them with identifiers. A declaration of
an identifier is valid for one block. Outside this block the particular
identifier may be used for other purposes (see [Section 4.1.3](#4_1_3)).

Dynamically this implies the following: at the time of an entry into a
block (through **[begin]{.underline}** since the labels inside are local
and therefore inaccessible from outside) all identifiers declared for
the block assume the significance implied by the nature of the
declarations given. If these identifiers had already been defined by
other declarations outside they are for the time being given a new
significance. Identifiers which are not declared for the block, on the
other hand, retain their old meaning.

At the the time of an exit from a block (through **[end]{.underline}**,
or by a go to statement) all identifiers which are declared for the
block lose their local significance.

A declaration may be marked with additional declarator
**[own]{.underline}**. This has the following effect: upon a reentry
into the block, the values of own quantities will be unchanged from
their values at the last exit, while the values of declared variables
which are not marked as **[own]{.underline}** are undefined.

Apart from labels, formal parameters of procedure declarations and
identifiers declared in the environmental block, each identifier
appearing in a program must be explicitly declared within a program.

No identifier may be declared either explicitly or implicitly (see
[Section 4.1.3](#4_1_3)) more than once in any one block head.

**Syntax**

\<declaration\> ::= \<type declaration\> \| \<array declaration\> \|

\<switch declaration\> \| \<procedure declaration\>

 []{#5_1 .anchor}

#### 5.1. Type declarations

[]{#5_1_1 .anchor}**5.1.1 Syntax**

\<type list\> ::= \<simple variable\> \| \<simple variable\> , \<type
list\>

\<type\> ::= **[real]{.underline}** \| **[integer]{.underline}** \|
**[Boolean]{.underline}**

\<local or own\> ::= \<empty\> \| **[own]{.underline}**

\<type declaration\> ::= \<local or own\> \<type\> \<type list\>

**5.1.2. Examples**

**[integer]{.underline}** p, q, s

**[own]{.underline}** **[Boolean]{.underline}** Acryl, n

[]{#5_1_3 .anchor}**5.1.3. Semantics**\
Type declarations serve to declare certain identifiers to represent
simple variables of a given type. Real declared variables may only
assume positive or negative values including zero. Integer declared
variables may only assume positive and negative integral values
including zero. Boolean declared variables may only assume the values
**[true]{.underline}** and **[false]{.underline}**.

A variable declared **[own]{.underline}** behaves as if it had been
declared (and initialised to zero or false, see [Section 3.1.5](#3_1_5))
in the environmental block, except that it is accessible only within its
own scope. Possible conflicts between identifiers, resulting from this
process, resolved by suitable systematic changes of the identifiers
involved.

 []{#5_2 .anchor}

#### 5.2. Array declarations

[]{#5_2_1 .anchor}**5.2.1 Syntax**

\<lower bound\> ::= \<arithmetic expression\>

\<upper bound\> ::= \<arithmetic expression\>

\<bound pair\> ::= \<lower bound\> : \<upper bound\>

\<bound pair list\> ::= \<bound pair\> \|

\<bound pair list\> , \<bound pair\>

\<array segment\> ::= \<array identifier\> \[ \<bound pair list\> \] \|

\<array identifier\> , \<array segment\>

\<array list\> ::= \<array segment\> \|

\<array list\> , \<array segment\>

\<array declarer\> ::= \<type\> **[array]{.underline}** \|
**[array]{.underline}**

\<array declaration\> ::= \<local or own\> \<array declarer\> \<array
list\>

**5.2.2. Examples**

**[array]{.underline}** a, b, c\[7:n, 2:m\], s\[-2:10\]

**[own]{.underline}** **[integer]{.underline}** **[array]{.underline}**
A\[2:20\]

**[real]{.underline}** **[array]{.underline}** q \[-7:
**[if]{.underline}** c \< 0 **[then]{.underline}** 2
**[else]{.underline}** 1\]

[]{#5_2_3 .anchor}**5.2.3. Semantics**\
An array declaration declares one or several identifiers to represent
multidimensional arrays of subscripted variables and gives the
dimensions of the arrays, the bounds of the subscripts, and the types of
the variables.

[]{#5_2_3_1 .anchor}**5.2.3.1. Subscript bounds**\
The subscript bounds for any array are given in the first subscript
brackets following the identifier of this array in the form of a bound
pair list. Each item of this list gives the lower and upper bounds of a
subscript in the form of two arithmetic expressions separated by the
delimiter :. The bound pair list gives the bounds of all subscripts
taken in order from left to right.

[]{#5_2_3_2 .anchor}**5.2.3.2. Dimensions**\
The dimensions are given as the number of entries in the bound pair
lists.

**5.2.3.3. Types**\
All arrays declared in one declaration are of the same quoted type. If
no type declarator is given the real type is understood.

**5.2.4. Lower upper bound expressions**

5.2.4.1. The expressions will be evaluated in the same way as subscript
expression (see [Section 3.1.4.2](#3_1_4_2)).

5.2.4.2. The expressions cannot include any identifier that is declared,
either explicitly or implicitly (see [Section 4.1.3](#4_1_3)), in the
same block head as the array in question. The bounds of an array
declared as own may only be of the syntactic form integer (see [Section
2.5.1](#2_5_1)).

5.2.4.3. An array is defined only when the values of all upper subscript
bounds are not smaller than those of the corresponding lower bounds. If
any lower subscript bound is greater that the corresponding upper bound,
the array has no component.

5.2.4.4. The expressions will be evaluated once at each entrance into
the block.

 []{#5_3 .anchor}

#### 5.3. Switch designators

[]{#5_3_1 .anchor}**5.3.1 Syntax**

\<switch list\> ::= \<designational expression\> \|

\<switch list\> , \<designational expression\>

\<switch declaration\> ::= **[switch]{.underline}** \<switch
identifier\> := \<switch list\>

**5.3.2. Examples**

**[switch]{.underline}** S := S1, S2, Q\[m\], **[if]{.underline}** v \>
-5 **[then]{.underline}** S3 **[else]{.underline}** S4

**[switch]{.underline}** Q := p1, w

[]{#5_3_3 .anchor}**5.3.3. Semantics**\
A switch declaration defines the set of values of the corresponding
switch designators. These values are given one by one as the values of
the designational expressions entered in the switch list. With each of
these designational expressions there is associated a positive integer,
1, 2, . . ., obtained by counting the items in the list from left to
right. The value of the switch designator corresponding to a given value
of the subscript expression (see [Section 3.5 Designational
expressions](#3_5)) is the value of the designational expression in the
switch list having this given value as its associated integer.

**5.3.4. Evaluation of expressions in the switch list**\
An expression in the switch list will be evaluated every time the item
of the list in which the expression occurs is referred to, using the
current values of all variables involved.

**5.3.5. Influence of scopes**\
If a switch designator occurs outside the scope of a quantity entering
into a designational expression in the switch list, and an evaluation of
this switch designator selects this designational expression, then the
conflicts between the identifiers for the quantities in this expression
and the identifiers whose declarations are valid at the place of the
switch designator will be avoided through suitable systematic changes of
the latter identifiers.

 []{#5_4 .anchor}

#### 5.4. Procedure declarations

[]{#5_4_1 .anchor}**5.4.1 Syntax**

\<formal parameter\> ::= \<identifier\>

\<formal parameter list\> ::= \<formal parameter\> \|

\<formal parameter list\> \<parameter delimiter\> \<formal parameter\>

\<formal parameter part\> ::= \<empty\> \| ( \<formal parameter list\> )

\<identifier list\> ::= \<identifier\> \|

\<identifier list\> , \<identifier\>

\<value part\> ::= **[value]{.underline}** \<identifier list\> ; \|
\<empty\>

\<specifier\> ::= **[string]{.underline}** \| \<type\> \| \<array
declarer\> \|

**[label]{.underline}** \| **[switch]{.underline}** \|
**[procedure]{.underline}** \| \<type\> **[procedure]{.underline}**

\<specification part\> ::= \<empty\> \| \<specifier\> \<identifier
list\> ; \|

\<specification part\> \<specifier\> \<identifier list\>

\<procedure heading\> ::= \<procedure identifier\> \<formal parameter
part\> ;

\<value part\> \<specification part\>

\<procedure body\> ::= \<statement\> \| \<code\>

\<procedure declaration\> ::= **[procedure]{.underline}** \<procedure
heading\> \<procedure body\> \|

\<type\> **[procedure]{.underline}** \<procedure heading\> \<procedure
body\>

[]{#5_4_2 .anchor}**5.4.2. Examples** (see also the examples in
[Appendix 2](#A2))

**[procedure]{.underline}** Spur(a) Order:(n) Result:(s);
**[value]{.underline}** n;

**[array]{.underline}** a; **[integer]{.underline}** n;
**[real]{.underline}** s;

**[begin]{.underline}** **[integer]{.underline}** k;

s := 0;

**[for]{.underline}** k := 1 **[step]{.underline}** 1
**[until]{.underline}** n **[do]{.underline}** s := s + a\[k, k\]

**[end]{.underline}**

**[procedure]{.underline}** Transpose(a) Order:(n);
**[value]{.underline}** n;

**[array]{.underline}** a; **[integer]{.underline}** n;

**[begin]{.underline}** **[real]{.underline}** w;
**[integer]{.underline}** i, k;

**[for]{.underline}** i := 1 **[step]{.underline}** 1
**[until]{.underline}** n **[do]{.underline}**

**[for]{.underline}** k := 1 + i **[step]{.underline}** 1
**[until]{.underline}** n **[do]{.underline}**

**[begin]{.underline}** w := a\[i, k\];

a\[i, k\] := a\[k, i\];

a\[k, i\] := w

**[end]{.underline}**

**[end]{.underline}** Transpose

**[integer]{.underline}** **[procedure]{.underline}** Step(u);
**[real]{.underline}** u;

Step := **[if]{.underline}** 0 u u 1 **[then]{.underline}** 1
**[else]{.underline}** 0

**[procedure]{.underline}** Absmax(a) Size:(n, m) Result:(y)
Subscripts:(i, k);

**[value]{.underline}** n, m; **[array]{.underline}** a;
**[integer]{.underline}** n, m, i, k; **[real]{.underline}** y;

**[comment]{.underline}** The absolute greatest element of the matrix a,
of size n by m

is transferred to y, and the subscripts of this element to i and k;

**[begin]{.underline}** **[integer]{.underline}** p, q;

y := 0; i := k := 1;

**[for]{.underline}** p:=1 **[step]{.underline}** 1
**[until]{.underline}** n **[do]{.underline}**

**[for]{.underline}** q:=1 **[step]{.underline}** 1
**[until]{.underline}** m **[do]{.underline}**

**[if]{.underline}** abs(a\[p, q\]\]) \> y **[then]{.underline}**

**[begin]{.underline}** y :=a bs(a\[p, q\]);

i := p; k := q

**[end]{.underline}**

**[end]{.underline}** Absmax

**[procedure]{.underline}** Innerproduct(a, b) Order:(k, p) Result:(y);
**[value]{.underline}** k;

**[integer]{.underline}** k, p; **[real]{.underline}** y, a, b;

**[begin]{.underline}** **[real]{.underline}** s;

s := 0;

**[for]{.underline}** p := 1 **[step]{.underline}** 1
**[until]{.underline}** k **[do]{.underline}** s := s + a b;

y := s

**[end]{.underline}** Innerproduct

**[real]{.underline}** **[procedure]{.underline}** euler(fct, eps, tim);

**[value]{.underline}** eps, tim;

**[real]{.underline}** **[procedure]{.underline}** fct; real eps;
**[integer]{.underline}** tim;

**[comment]{.underline}** euler computes the sum of fct(i) for i from
zero up to

infinity by means of a suitably refined euler transformation. The
summation

is stopped as soon as tim times in succession the absolute value of the

terms of the transformed series are found to be less than eps.

Hence one should provide a function fct with one integer argument, an
upper

bound eps, and an integer tim. euler is particularly efficient in the
case

of a slowly convergent or divergent alternating series;

**[begin]{.underline}**

**[integer]{.underline}** i, k, n, r;

**[array]{.underline}** m\[0:15\];

**[real]{.underline}** mn, mp, ds, sum;

n := t := 0;

m\[0\] := fct(0); sum := m\[0\]/2;

**[for]{.underline}** i := 1, i + 1 **[while]{.underline}** t \< tim
**[do]{.underline}**

**[begin]{.underline}**

mn := fct(i);

**[for]{.underline}** k := 0 **[step]{.underline}** 1
**[until]{.underline}** n **[do]{.underline}**

**[begin]{.underline}**

mp := (mn + m\[k\])/2;

m\[k\] := mn; mn := mp

**[end]{.underline}** means;

**[if]{.underline}** abs(mn) \< abs(m\[n\]) n \< 15
**[then]{.underline}**

**[begin]{.underline}**

ds := mn/2; n := n + 1;

m\[n\] := mn

**[end]{.underline}** accept

**[else]{.underline}**

ds := mn;

sum := sum + ds;

t := **[if]{.underline}** abs(ds) \< eps **[then]{.underline}** t + 1
**[else]{.underline}** 0

**[end;]{.underline}**

euler := sum

**[end]{.underline}** euler

[]{#5_4_3 .anchor}**5.4.3. Semantics**\
A procedure declaration serves to define the procedure associated with a
procedure identifier. The principal constituent of a procedure
declaration is a statement or a piece of code, the procedure body, which
through the use of procedure statements and/or function designators may
be activated from other parts of the block in the head of which the
procedure declaration appears. Associated with the body is a heading,
which specifies certain identifiers occurring within the body to
represent formal parameters. Formal parameters in the procedure body
will, whenever the procedure is activated (see Section 3.2 Function
designators and [Section 4.7 Procedure statements](#4_7)) be assigned
the values of or replaced by actual parameters. Identifiers in the
procedure body which are not formal will be wither local or non-local to
the body depending on whether they are declared within the body or not.
Those of them which are non-local to the body may well be local to the
block in the head of which the procedure declaration appears. The
procedure body always acts like a block, whether it has the form of one
or not. Consequently the scope of any label labelling a statement within
the body or the body itself can never extend beyond the procedure body.
In addition, if the identifier of a formal parameter is declared anew
within the procedure body (including the case of its use as a label as
in [Section 4.1.3](#4_1_3)), it is hereby given a local significance and
actual parameters which correspond to it are inaccessible throughout the
scope of this inner local quantity.

No identifier may appear more than once in any one formal parameter
list, nor may a formal parameter list contain the procedure identifier
of the same procedure heading.

[]{#5_4_4 .anchor}**5.4.4. Values of function designators**\
For a procedure declaration to define the value of a function designator
there must, within the procedure body, occur one or more use of the
procedure identifier as a destination; at least one of these must be
executed, and the type associated with the procedure identifier must be
declared through the appearance of a type declarator as the very first
symbol of the procedure declaration. The last value so assigned is used
to continue the evaluation of the expression in which the function
designator occurs. Any occurrence of the procedure identifier within the
body of the procedure other than as a destination in an assignment
statement denotes activation of the procedure.

If a go to statement within the procedure, or within any other procedure
activated by it, leads to an exit from the procedure, other than through
its **[end]{.underline}**, then the execution, of all statements that
has been started but not yet completed and which do not contain a label
to which the go to statements leads, is abandoned. The values of all
variables that still have significance remain as they were immediately
before execution of the go to statement.

If a function designator is used as a procedure statement, then the
resulting value is discarded, but such a statement may be used, if
desired, for the purpose of invoking side effects.

[]{#5_4_5 .anchor}**5.4.5. Specifications**\
The heading includes a specification part, giving information about the
kinds and types of the formal parameters. In this part no formal
parameter may occur more than once.

[]{#5_4_6 .anchor}**5.4.6. Code as procedure body.**\
It is understood that the procedure body may be expressed in non-ALGOL
language. Since it is intended that the use of this feature should be
entirely a question of implementation, no further rules concerning this
code language can be given within the reference language.

   [\^ top](#modified-report-on-the-algorithmic-language-algol-60)

 

 []{#A1 .anchor}

## Appendix 1   Subsets

 \
Two subsets of ALGOL 60 are defined, denoted as level 1 and level 2. The
full language is level 0.

The level 1 subset is defined as level 0 with the following additional
restrictions:

+-----+--------------------------------------------------------------------+
| 1\. | The **[own]{.underline}** declarator is not included.              |
+=====+====================================================================+
| 2\. | Additional restrictions are placed upon actual parameters as given |
|     | by the following replacement lines to the table in Section         |
|     | 4.7.5.5.                                                           |
+-----+--------------------------------------------------------------------+
|     |   -------------------------------------------------                |
|     |   *Formal          *Mode*   *Actual parameter*                     |
|     |   parameter*                                                       |
|     |   ---------------- -------- -----------------------                |
|     |   integer          name     integer expression (see                |
|     |                             [4.7.5.2](#4_7_5_2))                   |
|     |                                                                    |
|     |   real name        real     expression (see                        |
|     |                             [4.7.5.2](#4_7_5_2))                   |
|     |                                                                    |
|     |   integer array    value    integer array (see                     |
|     |                             [4.7.5.3](#4_7_5_3))                   |
|     |                                                                    |
|     |   real array       value    realarray (see                         |
|     |                             [4.7.5.3](#4_7_5_3))                   |
|     |                                                                    |
|     |   typeless         name     typeless procedure (see                |
|     |   procedure                 [4.7.5.3](#4_7_5_3))                   |
|     |                                                                    |
|     |   integer          name     integer procedure (see                 |
|     |   procedure                 [4.7.5.3](#4_7_5_3))                   |
|     |                                                                    |
|     |   real procedure   name     real procedure (see                    |
|     |                             [4.7.5.3](#4_7_5_3))                   |
|     |   -------------------------------------------------                |
+-----+--------------------------------------------------------------------+
| 3\. | Only one alphabet of 26 letters is provided, which is regarded of  |
|     | being the lower case alphabet of the reference language.           |
+-----+--------------------------------------------------------------------+
| 4\. | If deleting every symbol after the twelfth in every identifier     |
|     | would change the action of the program, then the program is        |
|     | undefined.                                                         |
+-----+--------------------------------------------------------------------+

The level 2 subset consists of 1-3 of level 1 and in addition:

  ---------------------------------------------------------------------------
  5\.    Procedures may not be called recursively, either directly or
         indirectly.
  ------ --------------------------------------------------------------------
  6\.    If a parameter is called by name, then the corresponding actual
         parameter may only be an identifier or a string.

  7\.    The designational expressions occurring in a switch list may only be
         lables.

  8\.    The specifiers **[switch]{.underline}**, **[procedure]{.underline}**
         and \<type\> **[procedure]{.underline}** are not included.

  9\.    A left part list may only be a left part.

  10\.   If deleting every symbol after the sixth in every identifier would
         change the action of the program, then the program is undefined.
  ---------------------------------------------------------------------------

   [\^ top](#modified-report-on-the-algorithmic-language-algol-60)

 

 []{#A2 .anchor}

## Appendix 2   The environmental block

As an example of the use of ALGOL 60, the structure of the environmental
block is given in detail.

**[begin]{.underline}**

**[comment]{.underline}** This description of the standard functions and
procedures

should be taken as definitive only so far as their effects are

concerned. An actual implementation should seek to produce these

effects in as efficient a manner as practicable. Furthermore, where

arithmetic of real quantities are concerned, even the effects must

be regarded as defined with only a finite accuracy (see [Section
3.3.6](#3_3_6)).

Thus, for example, there is no guarantee that the value of sqrt(x)

is exactly equal to the value x0.5, or that the effects of inreal

and outreal will exactly match those given here.

ALGOL coding has been replaced by a metalingustic variable (see

[Section 1.1](#1_1)) in places where the requirement is clear, and there
is

no simple way of specifying the operations needed in ALGOL;

**[comment]{.underline}** Simple functions;

**[real]{.underline}** **[procedure]{.underline}** abs(E);

**[value]{.underline}** E; **[real]{.underline}** E;

abs := **[if]{.underline}** E 0.0 **[then]{.underline}** E
**[else]{.underline}** -E;

**[integer]{.underline}** **[procedure]{.underline}** iabs(E);

**[value]{.underline}** E; **[integer]{.underline}** E;

iabs := **[if]{.underline}** E 0 **[then]{.underline}** E
**[else]{.underline}** -E;

**[integer]{.underline}** **[procedure]{.underline}** sign(E);

**[value]{.underline}** E; **[real]{.underline}** E;

sign := **[if]{.underline}** E \> 0.0 **[then]{.underline}** 1

**[else]{.underline}** **[if]{.underline}** E \< 0.0
**[then]{.underline}** -1 **[else]{.underline}** 0;

**[integer]{.underline}** **[procedure]{.underline}** entier(E);

**[value]{.underline}** E; **[real]{.underline}** E;

**[comment]{.underline}** entier := largest integer not greater than E,

i.e. E - 1 \< entier E;

**[begin]{.underline}**

**[integer]{.underline}** j;

j := E;

entier := **[if]{.underline}** j \> E **[then]{.underline}** j - 1
**[else]{.underline}** j

**[end]{.underline}** entier;

**[comment]{.underline}** Mathematical functions;

**[real]{.underline}** **[procedure]{.underline}** sqrt(E);

**[value]{.underline}** E; **[real]{.underline}** E;

**[if]{.underline}** E \< 0.0 **[then]{.underline}**

fault(\`negativesqrt\', E)

**[else]{.underline}**

sqrt := E0.5;

**[real]{.underline}** **[procedure]{.underline}** sin(E);

**[value]{.underline}** E; **[real]{.underline}** E;

**[comment]{.underline}** sin := sine of E radians;

\<body\>;

**[real]{.underline}** **[procedure]{.underline}** cos(E);

**[value]{.underline}** E; **[real]{.underline}** E;

**[comment]{.underline}** cos := cosine of E radians;

\<body\>;

**[real]{.underline}** **[procedure]{.underline}** arctan(E);

**[value]{.underline}** E; **[real]{.underline}** E;

**[comment]{.underline}** arctan := principal value, in radians, of
arctangent

of E, i.e. -π/2 arctan π/2;

\<body\>;

**[real]{.underline}** **[procedure]{.underline}** ln(E);

**[value]{.underline}** E; **[real]{.underline}** E;

**[comment]{.underline}** ln := natural logarithm of E;

**[if]{.underline}** E 0.0 **[then]{.underline}**

fault(\`lnnotpositive\', E)

**[else]{.underline}**

\<statement\>;

**[real]{.underline}** **[procedure]{.underline}** exp(E);

**[value]{.underline}** E; **[real]{.underline}** E;

**[comment]{.underline}** exp := exponential function of E;

**[if]{.underline}** E \> ln(maxreal) **[then]{.underline}**

fault(\`overflowonexp\', E)

**[else]{.underline}**

\<statement\>;

**[comment]{.underline}** Terminating procedures;

**[procedure]{.underline}** stop;

**[comment]{.underline}** for the use of , see [Section 2.1
Letters](#2);

go to ;

**[procedure]{.underline}** fault(str, r);

**[value]{.underline}** r; string str; **[real]{.underline}** r;

**[comment]{.underline}** is assumed to denote a standard output
channel.

For the use of see [Section 2.1 Letters](#2).

The following calls of fault appear:

integer divide by zero,

undefined operation in expr,

undefined operation in expn,

undefined operation in expi,

and in the environmental block:

sqrt of negative argument,

ln of negative or zero argument,

overflow on exp function,

invalid parameter for outchar,

invalid character in ininteger (twice),

invalid character in inreal (three times);

**[begin]{.underline}**

outstring(, \`fault\');

outstring(, str);

outstring(, \`\');

outreal(, r);

**[comment]{.underline}** additional diagnostics may be output here;

stop

**[end]{.underline}** fault;

**[comment]{.underline}** Input/output procedures;

**[procedure]{.underline}** inchar(channel, str, int);

**[value]{.underline}** channel;

**[integer]{.underline}** channel, int; string str;

**[comment]{.underline}** Set int to value corresponding to the first
position in

str of current character on channel. Set int to zero if character

not in str. Move channel pointer to next character;

\<body\>;

**[procedure]{.underline}** outchar(channel, str, int);

**[value]{.underline}** channel, int;

**[integer]{.underline}** channel, int; string str;

**[comment]{.underline}** Pass to channel the character in str,
corresponding to

the value of int;

**[if]{.underline}** int \< 1 int \> length(str) **[then]{.underline}**

fault(\`characternotinstring\', int)

**[else]{.underline}**

\<statement\>;

**[integer]{.underline}** **[procedure]{.underline}** length(str);

string str;

**[comment]{.underline}** length := number of characters in the open
string

enclosed by the outermost string quotes, after performing any

necessary concatenation as defined in [Section 2.6.3](#2_6_3).
Characters

forming an inner string (see [Section 2.6.3](#2_6_3)) are counted in an

implementation dependent manner;

\<body\>;

**[procedure]{.underline}** outstring(channel, str);

**[value]{.underline}** channel;

**[integer]{.underline}** channel; string str;

**[begin]{.underline}**

**[integer]{.underline}** m, n;

n := length(str);

**[for]{.underline}** m := 1 **[step]{.underline}** 1
**[until]{.underline}** n **[do]{.underline}**

outchar(channel, str, m)

**[end]{.underline}** outstring;

**[procedure]{.underline}** outterminator(channel);

**[value]{.underline}** channel; **[integer]{.underline}** channel;

**[comment]{.underline}** outputs a terminator for use after a number.
To be

converted into format control instructions in a machine

dependent fashion. The terminator should be a space, a newline

or a semicolon if ininteger and inreal are to be able to read

representations resulting from outinteger and outreal;

\<body\>;

**[procedure]{.underline}** ininteger(channel, int);

**[value]{.underline}** channel; **[integer]{.underline}** channel, int;

**[comment]{.underline}** int takes the value of an integer, as defined
in

[Section 2.5.1](#2_5_1), read from channel. The terminator of the

integer may be either a space, a newline or a semicolon

(if other terminators are to be allowed, they may be added

to the end of the string parameter of the call of inchar.

No other change is necessary). Any number of spaces or

newlines may precede the first character of the integer;

**[begin]{.underline}**

**[integer]{.underline}** k, m;

**[Boolean]{.underline}** b, d;

**[integer]{.underline}** **[procedure]{.underline}** ins;

**[begin]{.underline}**

**[integer]{.underline}** n;

**[comment]{.underline}** read one character, converting newlines to
spaces.

The inner string \`NL\' behaves as a single character repre-

senting newline;

inchar(channel, \`0123456789-+;\`NL\'\', n);

ins := **[if]{.underline}** n = 15 **[then]{.underline}** 13
**[else]{.underline}** n

**[end]{.underline}** ins;

**[comment]{.underline}** pass over initial spaces or newlines;

**[for]{.underline}** k := ins **[while]{.underline}** k = 13
**[do]{.underline}**

;

**[comment]{.underline}** fault anything except sign or digit;

**[if]{.underline}** k = 0 k \> 13 **[then]{.underline}**

fault(\`invalidcharacter\', k);

**[if]{.underline}** k \> 10 **[then]{.underline}**

**[begin]{.underline}**

**[comment]{.underline}** sign found, d indicates digit not found yet, b

indicates whether + or -, m is value so far;

d := b := true;

m := k - 1

**[end]{.underline}**;

**[for]{.underline}** k := ins **[while]{.underline}** k \> 0 k \< 11
**[do]{.underline}**

**[begin]{.underline}**

**[comment]{.underline}** deal with further digits;

m := 10 m + k - 1;

d := true

**[end]{.underline}** k loop;

**[comment]{.underline}** fault if no digit has been found, or the
terminator

was invalid;

**[if]{.underline}** d k \< 13 **[then]{.underline}**

fault(\`invalidcharacter\', k);

int := **[if]{.underline}** b **[then]{.underline}** m
**[else]{.underline}** -m

**[end]{.underline}** ininteger;

**[procedure]{.underline}** outinteger(channel, int);

**[value]{.underline}** channel, int;

**[integer]{.underline}** channel, int;

**[comment]{.underline}** Passes to channel the characters representing
the value

of int, followed by a terminator;

**[begin]{.underline}**

**[procedure]{.underline}** digits(int);

**[value]{.underline}** int; **[integer]{.underline}** int;

**[begin]{.underline}**

**[integer]{.underline}** j;

**[comment]{.underline}** use recursion to evaluate digits from right to
left,

but print them from left to right;

j := int ÷ 10;

int := int - 10 j;

**[if]{.underline}** j 0 **[then]{.underline}**

digits(j);

outchar(channel, \`0123456789\', int + 1)

**[end]{.underline}** digits;

**[if]{.underline}** int \< 0 **[then]{.underline}**

**[begin]{.underline}**

outchar(channel, \`-\', 1);

int := -int

**[end]{.underline}**;

digits(int); outterminator(channel)

**[end]{.underline}** outinteger;

**[procedure]{.underline}** inreal(channel, re);

**[value]{.underline}** channel;

**[integer]{.underline}** channel; **[real]{.underline}** re;

**[comment]{.underline}** re takes the value of a number, as defined in

[Section 2.5.1](#2_5_1), read from channel. Except for the different

definitions of a number and an integer the rules are exactly

as for ininteger. Spaces or newlines may follow the symbol ~10~;

**[begin]{.underline}**

**[integer]{.underline}** j, k, m;

**[real]{.underline}** r, s;

**[Boolean]{.underline}** b, d;

**[integer]{.underline}** **[procedure]{.underline}** ins;

**[begin]{.underline}**

**[integer]{.underline}** n;

**[comment]{.underline}** read one character, converting newlines to
spaces.

The inner string \`NL\' behaves as a single character repre-

senting newline;

inchar(channel, \`0123456789-+.~10~;\`NL\'\', n);

ins := **[if]{.underline}** n = 17 **[then]{.underline}** 15
**[else]{.underline}** n

**[end]{.underline}** ins;

**[comment]{.underline}** pass over initial spaces or newlines;

**[for]{.underline}** k := ins **[while]{.underline}** k = 15
**[do]{.underline}**

;

**[comment]{.underline}** fault anything except sign, digit, point or
ten;

**[if]{.underline}** k = 0 k \> 15 then

fault(\`invalidcharacter\', k);

**[comment]{.underline}** b indicates whether + or -, d indicates
whether further

digits can have any effect, m will count places after the point,

r is the value so far. j indicates whether last character read

was sign (j = 1), digit before point (j = 2), point (j = 3),

digit after point (j = 4), or ten (j = 5);

b := k 11;

d := true;

m := 1;

j := **[if]{.underline}** k \< 11 **[then]{.underline}** 2
**[else]{.underline}** iabs(k + k - 23);

r := **[if]{.underline}** k \< 11 **[then]{.underline}** k - 1
**[else]{.underline}** 0.0;

**[if]{.underline}** k 14 **[then]{.underline}**

**[begin]{.underline}**

**[comment]{.underline}** ten not found. Continue until ten or
terminator found;

**[for]{.underline}** k := ins **[while]{.underline}** k \< 14
**[do]{.underline}**

**[begin]{.underline}**

**[comment]{.underline}** fault for non-numerical character, sign or
second

point;

**[if]{.underline}** k = 0 k = 11 k = 12

k = 13 j \> 2 **[then]{.underline}**

fault(\`invalidcharacter\', k);

**[comment]{.underline}** deal with digit unless it cannot affect value;

**[if]{.underline}** d **[then]{.underline}**

**[begin]{.underline}**

**[if]{.underline}** k = 13 **[then]{.underline}**

**[begin]{.underline}**

**[comment]{.underline}** point found;

j := 3

**[end]{.underline}**

**[else]{.underline}**

**[begin]{.underline}**

**[if]{.underline}** j \< 3 **[then]{.underline}**

**[begin]{.underline}**

**[comment]{.underline}** deal with digit before point;

r := 10.0 r + k - 1

**[end]{.underline}**

**[else]{.underline}**

**[begin]{.underline}**

**[comment]{.underline}** deal with digit after point;

s := 10.0(-m);

m := m + 1;

r := r + s (k - 1);

**[comment]{.underline}** if r = r + s to machine accuracy,

further digits cannot affect value;

d := r r + s

**[end]{.underline}**;

**[if]{.underline}** j = 1 j = 3 **[then]{.underline}** j := j + 1

**[end]{.underline}**

**[end]{.underline}**

**[end]{.underline}** k loop;

**[comment]{.underline}** fault if no digit has been found;

**[if]{.underline}** j = 1 k 14 j = 3 **[then]{.underline}**

fault(\`invalidcharacter\', k);

**[end]{.underline}**;

**[if]{.underline}** k = 14 **[then]{.underline}**

**[begin]{.underline}**

**[comment]{.underline}** deal with exponent part;

ininteger(channel, m);

r := (**[if]{.underline}** j = 1 j = 5 **[then]{.underline}** 1.0
**[else]{.underline}** r) 10.0m

**[end]{.underline}**;

re := **[if]{.underline}** b **[then]{.underline}** r
**[else]{.underline}** - r

**[end]{.underline}** inreal;

**[procedure]{.underline}** outreal(channel, re);

**[value]{.underline}** channel, re;

**[integer]{.underline}** channel; **[real]{.underline}** re;

**[comment]{.underline}** Passes to channel the characters representing
the value of

re, following by a terminator;

**[begin]{.underline}**

**[integer]{.underline}** n;

**[comment]{.underline}** n gives number of digits to print. Could be
given as a

constant in any actual implementation;

n := entier(1.0 - ln(epsilon) / ln(10.0));

**[if]{.underline}** re \< 0.0 **[then]{.underline}**

**[begin]{.underline}**

outchar(channel, \`-\', 1);

re := - re

**[end]{.underline}**;

**[if]{.underline}** re \< minreal **[then]{.underline}**

outstring(channel, 0.0 )

**[else]{.underline}**

**[begin]{.underline}**

**[integer]{.underline}** j, k, m, p;

**[Boolean]{.underline}** float, nines;

**[comment]{.underline}** m will hold number of places point must be
moved to

standardise value of re to have one digit before point;

m := 0;

**[comment]{.underline}** standardise value of re;

**[for]{.underline}** m := m + 1 **[while]{.underline}** re 10.0
**[do]{.underline}**

re := re/10.0;

**[for]{.underline}** m := m - 1 **[while]{.underline}** re \< 1.0
**[do]{.underline}**

re := 10.0 re;

**[if]{.underline}** re 10.0 **[then]{.underline}**

**[begin]{.underline}**

**[comment]{.underline}** this can occur only by rounding error, but is
a

necessary safeguard;

re := 1.0;

m := m + 1

**[end]{.underline}**;

**[if]{.underline}** m n m \< -2 **[then]{.underline}**

**[begin]{.underline}**

**[comment]{.underline}** printing to be in exponent form;

float := true;

p := 1

**[end]{.underline}**

**[else]{.underline}**

**[begin]{.underline}**

**[comment]{.underline}** printing to be in non-exponent form;

float := false;

**[comment]{.underline}** if p = 0 point will not be printed. Otherwise

point will be after p digits;

p := **[if]{.underline}** m = n - 1 m \< 0 **[then]{.underline}** 0
**[else]{.underline}** m + 1;

**[if]{.underline}** m \< 0 **[then]{.underline}**

**[begin]{.underline}**

outstring(channel, \`0.\');

**[if]{.underline}** m = -2 **[then]{.underline}**

outchar(channel, \`0\', 1)

**[end]{.underline}**

**[end]{.underline}**;

nines := false;

**[for]{.underline}** j := 1 **[step]{.underline}** 1
**[until]{.underline}** n **[do]{.underline}**

**[begin]{.underline}**

**[comment]{.underline}** if nines is true, all remaining digits must

be 9. This can occur only by rounding error, but is

a necessary safeguard;

**[if]{.underline}** nines **[then]{.underline}**

k := 9

**[else]{.underline}**

**[begin]{.underline}**

**[comment]{.underline}** find digit to print;

k := entier(re);

**[if]{.underline}** k \> 9 **[then]{.underline}**

**[begin]{.underline}**

k := 9;

nines := true

**[end]{.underline}**

**[else]{.underline}**

**[begin]{.underline}**

**[comment]{.underline}** move next digit to before point;

re := 10.0 (re - k)

**[end]{.underline}**

**[end]{.underline}**;

outchar(channel, \`0123456789\', k + 1);

**[if]{.underline}** j = p **[then]{.underline}**

outchar(channel, \`.\', 1)

**[end]{.underline}** j loop;

**[if]{.underline}** float **[then]{.underline}**

**[begin]{.underline}**

**[comment]{.underline}** print exponent part. outinteger includes a
call

of outterminator;

outchar(channel, \`~10~ , 1);

outinteger(channel, m)

**[end]{.underline}**

**[else]{.underline}**

outterminator(channel)

**[end]{.underline}**

**[end]{.underline}** outreal;

**[comment]{.underline}** Environmental enquiries;

**[real]{.underline}** **[procedure]{.underline}** maxreal;

maxreal := \<number\>;

**[real]{.underline}** **[procedure]{.underline}** minreal;

minreal := \<number\>;

**[integer]{.underline}** **[procedure]{.underline}** maxint;

maxint := \<integer\>;

**[comment]{.underline}** maxreal, minreal, and maxint are,
respectively, the maximum

allowable positive real number, the minimum allowable positive real

number, and the maximum allowable positive integer, such that any

valid expression of the form

\<primary\> \<arithmetic operator\> \<primary\>

will be correctly evaluated, provided that each of the primaries

concerned, and the mathematically correct result lies within the

open interval (-maxreal, -minreal) or (minreal, maxreal) or is zero

if of real type, or within the open interval (-maxint, maxint) if

of integer type. If the result is of real type, the words

\`correctly evaluated\' must be understood in the sense of numerical

analysis (see [Section 3.3.6](#3_3_6));

**[real]{.underline}** **[procedure]{.underline}** epsilon;

**[comment]{.underline}** The smallest positive real number such that
the

computational result of 1.0 + epsilon is greater than 1.0 and

the computational result of 1.0 - epsilon is less than 1.0;

epsilon := \<number\>;

**[comment]{.underline}** In any particular implementation, further
standard functions

and procedures may be added here, but no additional ones may be

regarded as part of the reference language (in particular, a less

rudimentary input/output system is desirable);

\<fictitious declaration of **[own]{.underline}** variables\>;

\<initialisation of **[own]{.underline}** variables\>;

\<program\>;

:

**[end]{.underline}**

   [\^ top](#modified-report-on-the-algorithmic-language-algol-60)

 

 

## References

+-------------------------------------------------------------------------------------------------------+
|                                                                                                       |
+====================================+==================================================================+
| []{#Backus1959 .anchor}BACKUS, J. W. (1959)                                                           |
+------------------------------------+------------------------------------------------------------------+
|                                    | The syntax and Semantics of the Proposed International Algebraic |
|                                    | Language.\                                                       |
|                                    | Information Processing, Paris, UNESCO.                           |
+------------------------------------+------------------------------------------------------------------+
| ECMA Subset of ALGOL 60                                                                               |
+------------------------------------+------------------------------------------------------------------+
|                                    | CACM, Vol, 6 (1963), p. 595; European Computer Manufacturers     |
|                                    | Association (1965) ECMA Standard for a Subset of ALGOL 60.       |
+------------------------------------+------------------------------------------------------------------+
| IFIP (1964a)                                                                                          |
+------------------------------------+------------------------------------------------------------------+
|                                    | Report on Subset ALGOL 60, Num. Math., Vol. 6, p. 454; CACM,     |
|                                    | Vol. 7, p. 626.                                                  |
+------------------------------------+------------------------------------------------------------------+
| IFIP (1964b)                                                                                          |
+------------------------------------+------------------------------------------------------------------+
|                                    | Report on Input-Output Procedures for ALGOL 60, Num. Math., Vol. |
|                                    | 6, p. 459;\                                                      |
|                                    | CACM, Vol. 7, p. 628.                                            |
+------------------------------------+------------------------------------------------------------------+
| ISO (1972)                                                                                            |
+------------------------------------+------------------------------------------------------------------+
|                                    | ISO/R 1538, Programming Language ALGOL.                          |
+------------------------------------+------------------------------------------------------------------+
| KNUTH, D. E. et al. (1964)                                                                            |
+------------------------------------+------------------------------------------------------------------+
|                                    | A Proposal for Input-Output Conventions in ALGOL 60, CACM, Vol.  |
|                                    | 7, p. 273.                                                       |
+------------------------------------+------------------------------------------------------------------+
| KNUTH, D. E. (1967)                                                                                   |
+------------------------------------+------------------------------------------------------------------+
|                                    | The Remaining Trouble Spots in ALGOL 60, CACM, Vol. 10, p. 611.  |
+------------------------------------+------------------------------------------------------------------+
| []{#Naur1963 .anchor}NAUR, P. (Editor) (1963)                                                         |
+------------------------------------+------------------------------------------------------------------+
|                                    | [Revised Report on the Algorithmic Language ALGOL                |
|                                    | 60](https://www.algol60.org/reports/algol60_rr.pdf),     |
|                                    | CACM, Vol. 6, p. 1; The Computer Journal, Vol. 9, p. 349; Num.   |
|                                    | Math., Vol. 4, p. 420.                                           |
+------------------------------------+------------------------------------------------------------------+
| UTMAN, R. E. et al. (1963)                                                                            |
+------------------------------------+------------------------------------------------------------------+
|                                    | Suggestions on the ALGOL 60 (Rome) Issues, CACM, Vol. 6, p. 20.  |
+------------------------------------+------------------------------------------------------------------+

   [\^ top](#modified-report-on-the-algorithmic-language-algol-60)

 

 

## Alphabetic index of definitions of concepts and syntactic units

 \
All references are given through section numbers. The references are
given in three groups:

  ----------------------------------------------------------------------------
  *def*    Following the abbreviation \`def\', reference to the syntactic
           definition (if any) is given.
  -------- -------------------------------------------------------------------
  *synt*   Following the abbreviation \`synt\', references to the occurrences
           in metalinguistic formulae are given. References already quoted in
           the def-group are not repeated.

  *text*   Following the word \`text\', the references to definitions given in
           text are given.
  ----------------------------------------------------------------------------

 \
Index:   **[A](#a)   [B](#b)   [C](#c)   [D](#d)   [E](#e)   [F](#f)  
[G](#g)   [H](#i)   [I](#i)   [J](#l)   [K](#l)   [L](#l)   [M](#m)  
[N](#n)   [O](#o)   [P](#p)   [Q](#q)   [R](#r)   [S](#s)   [T](#t)  
[U](#u)   [V](#v)   [W](#w)   [X](#w)   [Y](#w)   [Z](#w)**\
 

##### Symbols

\+ see: plus

\- see: minus

see: multiply

/ ÷ see: divide

see: exponentiation

\< = \> see: \<relational operator\>

¬ see: \<logical operator\>

, see: comma

. see: decimal point

~10~ see: ten

: see: colon

; see: semicolon

:= see: colon equal

( ) see: parenthese

\[ \] see: subscript bracket

\` \' see: string quote

#### A

\<actual parameter\>, def [3.2.1](#3_2_1), [4.7.1](#4_7_1)\
\<actual parameter list\>, def [3.2.1](#3_2_1), [4.7.1](#4_7_1)\
\<actual parameter part\>, def [3.2.1](#3_2_1), [4.7.1](#4_7_1)\
\<adding operator\>, def [3.3.1](#3_3_1)\
alphabet, text [2.1](#letters)\
arithmetic, text [3.3.6](#3_3_6)\
\<arithmetic expression\>, def [3.3.1](#3_3_1) synt [3](#3),
[3.1.1](#3_1_1), [3.4.1](#3_4_1), [4.2.1](#4_2_1), [4.6.1](#4_6_1),
[5.2.1](#5_2_1) text [3.3.3](#3_3_3)\
\<arithmetic operator\>, def [2.3](#delimiters) text [3.3.4](#3_3_4)\
**[array]{.underline}**, synt [2.3](#delimiters), [5.2.1](#5_2_1)\
array, text [3.1.4.1](#3_1_4_1)\
\<array declaration\>, def [5.2.1](#5_2_1) synt [5](#5) text
[5.2.3](#5_2_3)\
\<array declarer\>, def [5.2.1](#5_2_1), synt [5.4.1](#5_4_1)\
\<array identifier\>, def [3.1.1](#3_1_1) synt [3.2.1](#3_2_1),
[4.7.1](#4_7_1), [5.2.1](#5_2_1) text [2.8](#2_8)\
\<array list\>, def [5.2.1](#5_2_1)\
\<array segment\>, def [5.2.1](#5_2_1)\
\<assignment statement\>, def [4.2.1](#4_2_1) synt [4.1.1](#4_1_1) text
[1](#1), [4.2.3](#4_2_3)

  [\^
index](#alphabetic-index-of-definitions-of-concepts-and-syntactic-units)

#### B

\<basic statement\>, def [4.1.1](#4_1_1) synt [4.5.1](#4_5_1)\
\<basic symbol\>, def [2](#2)\
**[begin]{.underline}**, synt [2.3](#delimiters), [4.1.1](#4_1_1)\
\<block\>, def [4.1.1](#4_1_1) synt [4.5.1](#4_5_1) text [1](#1),
[4.1.3](#4_1_3), [5](#5)\
\<block head\>, def [4.1.1](#4_1_1)\
**[Boolean]{.underline}**, synt [2.3](#delimiters), [5.1.1](#5_1_1) text
[5.1.3](#5_1_3)\
\<Boolean expression\>, def [3.4.1](#3_4_1) synt [3](#3),
[3.3.1](#3_3_1), [4.2.1](#4_2_1), [4.5.1](#4_5_1), [4.6.1](#4_6_1) text
[3.4.3](#3_4_3)\
\<Boolean factor\>, def [3.4.1](#3_4_1)\
\<Boolean primary\>, def [3.4.1](#3_4_1)\
\<Boolean secondary\>, def [3.4.1](#3_4_1)\
\<Boolean term\>, def [3.4.1](#3_4_1)\
\<bound pair\>, def [5.2.1](#5_2_1)\
\<bound pair list\>, def [5.2.1](#5_2_1)\
\<bracket\>, def [2.3](#delimiters)

  [\^
index](#alphabetic-index-of-definitions-of-concepts-and-syntactic-units)

#### C

\<closed string\>, def [2.6.1](#2_6_1)\
\<code\>, synt [5.4.1](#5_4_1) text [4.7.8](#4_7_8), [5.4.6](#5_4_6)\
colon :, synt [2.3](#delimiters), [3.2.1](#3_2_1), [4.1.1](#4_1_1),
[4.5.1](#4_5_1), [4.6.1](#4_6_1), [4.7.1](#4_7_1), [5.2.1](#5_2_1)\
colon equal :=, synt [2.3](#delimiters), [4.2.1](#4_2_1),
[4.6.1](#4_6_1), [5.3.1](#5_3_1)\
comma ,, synt [2.3](#delimiters), [3.1.1](#3_1_1), [3.2.1](#3_2_1),
[4.6.1](#4_6_1), [4.7.1](#4_7_1), [5.1.1](#5_1_1), [5.2.1](#5_2_1),
[5.3.1](#5_3_1), [5.4.1](#5_4_1)\
**[comment]{.underline}**, synt [2.3](#delimiters)\
comment convention, text [2.3](#delimiters)\
\<compound statement\>, def [4.1.1](#4_1_1) synt [4.5.1](#4_5_1) text
[1](#1)\
\<compound tail\>, def [4.1.1](#4_1_1)\
\<conditional statement\>, def [4.5.1](#4_5_1) synt [4.1.1](#4_1_1) text
[4.5.3](#4_5_3)

  [\^
index](#alphabetic-index-of-definitions-of-concepts-and-syntactic-units)

#### D

\<decimal fraction\>, def [2.5.1](#2_5_1)\
\<decimal number\>, def [2.5.1](#2_5_1) text [2.5.3](#2_5_3)\
decimal point ., synt [2.3](#delimiters), [2.5.1](#2_5_1)\
\<declaration\>, def [5](#5) synt [4.1.1](#4_1_1) text [1](#1), [5](#5)\
\<declarator\>, def [2.3](#delimiters)\
\<delimiter\>, def [2.3](#delimiters) synt [2](#2)\
\<designational expression\>, def [3.5.1](#3_5_1) synt [3](#3),
[4.3.1](#4_3_1), [5.3.1](#5_3_1) text [3.5.3](#3_5_3)\
\<destination\>, def [4.2.1](#4_2_1)\
\<digit\>, def [2.2.1](#2_2_1) synt [2](#2), [2.4.1](#2_4_1),
[2.5.1](#2_5_1)\
dimension, text [5.2.3.2](#5_2_3_2)\
divide / &devide;, synt [2.3](#delimiters), [3.3.1](#3_3_1) text
[3.3.4.2](#3_3_4_2)\
**[do]{.underline}**, synt [2.3](#delimiters), [4.6.1](#4_6_1)\
\<dummy statement\>, def [4.4.1](#4_4_1) synt [4.1.1](#4_1_1) text
[4.4.3](#4_4_3)

  [\^
index](#alphabetic-index-of-definitions-of-concepts-and-syntactic-units)

#### E

**[else]{.underline}**, synt [2.3](#delimiters), [3.3.1](#3_3_1),
[3.4.1](#3_4_1), [3.5.1](#3_5_1), [4.5.1](#4_5_1) text
[4.5.3.2](#4_5_3_2)\
\<empty\>, def [1.1](#1_1) synt [2.6.1](#2_6_1), [3.2.1](#3_2_1),
[4.4.1](#4_4_1), [4.7.1](#4_7_1), [5.1.1](#5_1_1), [5.4.1](#5_4_1)\
**[end]{.underline}**, synt [2.3](#delimiters), [4.1.1](#4_1_1)\
exponentiation , synt [2.3](#delimiters), [3.3.1](#3_3_1) text
[3.3.4.3](#3_3_4_3)\
\<exponent part\>, def [2.5.1](#2_5_1) text [2.5.3](#2_5_3)\
\<expression\>, def [3](#3) synt [3.2.1](#3_2_1), [4.7.1](#4_7_1) text
[3](#3)

  [\^
index](#alphabetic-index-of-definitions-of-concepts-and-syntactic-units)

#### F

\<factor\>, def [3.3.1](#3_3_1)\
**[false]{.underline}**, synt [2.2.2](#2_2_2)\
**[for]{.underline}**, synt [2.3](#delimiters), [4.6.1](#4_6_1)\
\<for clause\>, def [4.6.1](#4_6_1) text [4.6.3](#4_6_3)\
\<for list\>, def [4.6.1](#4_6_1) text [4.6.4](#4_6_4)\
\<for list element\>, def [4.6.1](#4_6_1) text [4.6.4.1](#4_6_4_1),
[4.6.4.2](#4_6_4_2), [4.6.4.3](#4_6_4_3)\
\<formal parameter\>, def [5.4.1](#5_4_1), text [5.4.3](#5_4_3)\
\<formal parameter list\>, def [5.4.1](#5_4_1)\
\<formal parameter part\>, def [5.4.1](#5_4_1)\
\<for statement\>, def [4.6.1](#4_6_1) synt [4.1.1](#4_1_1),
[4.5.1](#4_5_1) text [4.6](#4_6)\
\<function designator\>, def [3.2.1](#3_2_1) synt [3.3.1](#3_3_1),
[3.4.1](#3_4_1) text [3.2.3](#3_2_3), [5.4.4](#5_4_4)

  [\^
index](#alphabetic-index-of-definitions-of-concepts-and-syntactic-units)

#### G

**[go]{.underline}** **[to]{.underline}**, synt [2.3](#delimiters),
[4.3.1](#4_3_1)\
\<go to statement\>, def [4.3.1](#4_3_1) synt [4.1.1](#4_1_1) text
[4.3.3](#4_3_3)

  [\^
index](#alphabetic-index-of-definitions-of-concepts-and-syntactic-units)

#### I

\<identifier\>, def [2.4.1](#2_4_1) synt [3.1.1](#3_1_1),
[3.2.1](#3_2_1), [3.5.1](#3_5_1), [5.4.1](#5_4_1) text [2.4.3](#2_4_3)\
\<identifier list\>, def [5.4.1](#5_4_1)\
**[if]{.underline}**, synt [2.3](#delimiters), [3.3.1](#3_3_1),
[4.5.1](#4_5_1)\
\<if clause\>, def [3.3.1](#3_3_1), [4.5.1](#4_5_1) synt
[3.4.1](#3_4_1), [3.5.1](#3_5_1) text [3.3.3](#3_3_3),
[4.5.3.2](#4_5_3_2)\
\<if statement\>, def [4.5.1](#4_5_1) text [4.5.3.1](#4_5_3_1)\
\<implication\>, def [3.4.1](#3_4_1)\
**[integer]{.underline}**, synt [2.3](#delimiters), [5.1.1](#5_1_1) text
[5.1.3](#5_1_3)\
\<integer\>, def [2.5.1](#2_5_1) text [2.5.4](#2_5_4)

  [\^
index](#alphabetic-index-of-definitions-of-concepts-and-syntactic-units)

#### L

**[label]{.underline}**, synt [2.3](#delimiters), [5.4.1](#5_4_1)\
\<label\>, def [3.5.1](#3_5_1) synt [4.1.1](#4_1_1), [4.5.1](#4_5_1),
[4.6.1](#4_6_1) text [1](#1), [4.1.3](#4_1_3), [4.7.6](#4_7_6)\
\<left part\>, def [4.2.1](#4_2_1)\
\<left part list\>, def [4.2.1](#4_2_1)\
\<letter\>, def [2.1](#letters) synt [2](#2), [2.4.1](#2_4_1),
[3.2.1](#3_2_1), [4.7.1](#4_7_1)\
\<letter string\>, def [3.2.1](#3_2_1), [4.7.1](#4_7_1)\
local, text [4.1.2](#4_1_2)\
\<local or own\>, def [5.1.1](#5_1_1), synt [5.4.1](#5_4_1)\
\<logical operator\>, def [2.3](#delimiters) synt [3.4.1](#3_4_1) text
[3.4.5](#3_4_5)\
\<logical value\>, def [2.2.2](#2_2_2) synt [2](#2), [3.4.1](#3_4_1)\
\<lower bound\>, def [5.2.1](#5_2_1) text [5.2.4](#5_2_4)

  [\^
index](#alphabetic-index-of-definitions-of-concepts-and-syntactic-units)

#### M

minus -, synt [2.3](#delimiters), [2.5.1](#2_5_1), [3.3.1](#3_3_1) text
[3.3.4.1](#3_3_4_1)\
multiply , synt [2.3](#delimiters), [3.3.1](#3_3_1) text
[3.3.4.1](#3_3_4_1)\
\<multiplying operator\>, def [3.3.1](#3_3_1)

  [\^
index](#alphabetic-index-of-definitions-of-concepts-and-syntactic-units)

#### N

non-local, text [4.1.3](#4_1_3)\
\<number\>, def [2.5.1](#2_5_1) text [2.5.3](#2_5_3), [2.5.4](#2_5_4)

  [\^
index](#alphabetic-index-of-definitions-of-concepts-and-syntactic-units)

#### O

\<open string\>, def [2.6.1](#2_6_1)\
\<operator\>, def [2.3](#delimiters)\
**[own]{.underline}**, synt [2.3](#delimiters), [5.1.1](#5_1_1) text
[5](#5), [5.2.5](#5_2_5)

  [\^
index](#alphabetic-index-of-definitions-of-concepts-and-syntactic-units)

#### P

\<parameter delimiter\>, def [3.2.1](#3_2_1), [4.7.1](#4_7_1) synt
[5.4.1](#5_4_1) text [4.7.7](#4_7_7)\
parentheses ( ), synt [2.3](#delimiters), [3.2.1](#3_2_1),
[3.3.1](#3_3_1), [3.4.1](#3_4_1), [3.5.1](#3_5_1), [4.7.1](#4_7_1),
[5.4.1](#5_4_1), text [3.3.5.2](#3_3_5_2)\
plus +, synt [2.3](#delimiters), [2.5.1](#2_5_1), [3.3.1](#3_3_1) text
[3.3.4.1](#3_3_4_1)\
\<primary\>, def [3.3.1](#3_3_1)\
**[procedure]{.underline}**, synt [2.3](#delimiters), [5.4.1](#5_4_1)\
\<procedure body\>, def [5.4.1](#5_4_1)\
\<procedure declaration\>, def [5.4.1](#5_4_1) synt [5](#5) text
[5.4.3](#5_4_3)\
\<procedure heading\>, def [5.4.1](#5_4_1) text [5.4.3](#5_4_3)\
\<procedure identifier\>, def [3.2.1](#3_2_1) synt [3.2.1](#3_2_1),
[4.2.1](#4_2_1), [4.7.1](#4_7_1), [5.4.1](#5_4_1) text
[4.7.5.4](#4_7_5_4)\
\<procedure statement\>, def [4.7.1](#4_7_1) synt [4.1.1](#4_1_1) text
[4.7.3](#4_7_3)\
\<program\>, def [4.1.1](#4_1_1) text [1](#1)\
\<proper string\>, def [2.6.1](#2_6_1)

  [\^
index](#alphabetic-index-of-definitions-of-concepts-and-syntactic-units)

#### Q

quantity, text [2.7](#2_7)

  [\^
index](#alphabetic-index-of-definitions-of-concepts-and-syntactic-units)

#### R

**[real]{.underline}**, synt [2.3](#delimiters), [5.1.1](#5_1_1) text
[5.1.3](#5_1_3)\
\<relation\>, def [3.4.1](#3_4_1) text [3.4.5](#3_4_5)\
\<relational operator\>, def [2.3](#delimiters), [3.4.1](#3_4_1)

  [\^
index](#alphabetic-index-of-definitions-of-concepts-and-syntactic-units)

#### S

scope, text [2.7](#2_7)\
semicolon ;, synt [2.3](#delimiters), [4.1.1](#4_1_1), [5.4.1](#5_4_1)\
\<separator\>, def [2.3](#delimiters)\
\<sequential operator\>, def [2.3](#delimiters)\
\<simple arithmetic expression\>, def [3.3.1](#3_3_1) synt
[3.4.1](#3_4_1) text [3.3.3](#3_3_3)\
\<simple Boolean\>, def [3.4.1](#3_4_1)\
\<simple designational expression\>, def [3.5.1](#3_5_1)\
\<simple variable\>, def [3.1.1](#3_1_1) synt [5.1.1](#5_1_1) text
[2.4.3](#2_4_3)\
\<specification part\>, def [5.4.1](#5_4_1) text [5.4.5](#5_4_5)\
\<specificator\>, def [2.3](#delimiters)\
\<specifier\>, def [5.4.1](#5_4_1)\
standard functions and procedures, text [3.2.4](#3_2_4)\
standard procedures, text [4.7.9](#4_7_9)\
\<statement\>, def [4.1.1](#4_1_1) synt [4.5.1](#4_5_1),
[4.6.1](#4_6_1), [5.4.1](#5_4_1) text [4](#4)\
statement bracket see: **[begin]{.underline}** **[end]{.underline}**\
**[step]{.underline}**, synt [2.3](#delimiters), [4.6.1](#4_6_1) text
[4.6.4.2](#4_6_4_2)\
**[string]{.underline}**, synt [2.3](#delimiters), [5.4.1](#5_4_1)\
\<string\>, def [2.6.1](#2_6_1) synt [3.2.1](#3_2_1), [4.7.1](#4_7_1)
text [2.6.3](#2_6_3)\
string quotes \` \', synt [2.3](#delimiters), [2.6.1](#2_6_1) text
[2.6.3](#2_6_3)\
subscript, text [3.1.4.1](#3_1_4_1)\
subscript bound, text [5.2.3.1](#5_2_3_1)\
subscript brackets \[ \], synt [2.3](#delimiters), [3.1.1](#3_1_1),
[3.5.1](#3_5_1), [5.2.1](#5_2_1)\
\<subscripted variable\>, def [3.3.1](#3_3_1) text [3.1.4.1](#3_1_4_1)\
\<subscript expression\>, def [3.1.1](#3_1_1) synt [3.5.1](#3_5_1)\
\<subscript list\>, def [3.1.1](#3_1_1)\
successor, text [4](#4)\
**[switch]{.underline}**, synt [2.3](#delimiters), [5.3.1](#5_3_1),
[5.4.1](#5_4_1)\
\<switch declaration\>, def [5.3.1](#5_3_1) synt [5](#5) text
[5.3.3](#5_3_3)\
\<switch designator\>, def [3.5.1](#3_5_1) text [3.5.3](#3_5_3)\
\<switch identifier\>, def [5.3.1](#5_3_1)

  [\^
index](#alphabetic-index-of-definitions-of-concepts-and-syntactic-units)

#### T

\<term\>, def [3.3.1](#3_3_1)\
ten ~10~, synt [2.3](#delimiters), [2.5.1](#2_5_1)\
**[then]{.underline}**, synt [2.3](#delimiters), [3.3.1](#3_3_1),
[4.5.1](#4_5_1)\
**[true]{.underline}**, synt [2.2.2](#2_2_2)\
\<type\>, def [5.1.1](#5_1_1) synt [5.4.1](#5_4_1) text [2.8](#2_8)\
\<type declaration\>, def [5.1.1](#5_1_1) synt [5](#5) text
[5.1.3](#5_1_3)\
\<type list\>, def [5.1.1](#5_1_1)

  [\^
index](#alphabetic-index-of-definitions-of-concepts-and-syntactic-units)

#### U

\<unconditional statement\>, def [4.1.1](#4_1_1), [4.5.1](#4_5_1)\
\<unlabelled basic statement\>, def [4.1.1](#4_1_1)\
\<unlabelled block\>, def [4.1.1](#4_1_1)\
\<unlabelled compound\>, def [4.1.1](#4_1_1)\
\<unsigned integer\>, def [2.5.1](#2_5_1)\
\<unsigned number\>, def [2.5.1](#2_5_1) synt [3.3.1](#3_3_1)\
**[until]{.underline}**, synt [2.3](#delimiters), [4.6.1](#4_6_1) text
[4.6.4.2](#4_6_4_2)\
\<upper bound\>, def [5.2.1](#5_2_1) text [5.2.4](#5_2_4)

  [\^
index](#alphabetic-index-of-definitions-of-concepts-and-syntactic-units)

#### V

**[value]{.underline}**, synt [2.3](#delimiters), [5.4.1](#5_4_1)\
value, text [2.8](#2_8), [3.3.3](#3_3_3)\
\<value part\>, def [5.4.1](#5_4_1) text [4.7.3.1](#4_7_3_1)\
\<variable\>, def [3.1.1](#3_1_1) synt [3.3.1](#3_3_1), [3.4.1](#3_4_1),
[4.2.1](#4_2_1), text [3.1.3](#3_1_3)\
\<variable identifier\>, def [3.1.1](#3_1_1) synt [4.6.1](#4_6_1)

  [\^
index](#alphabetic-index-of-definitions-of-concepts-and-syntactic-units)

#### W

**[while]{.underline}**, synt [2.3](#delimiters), [4.6.1](#4_6_1) text
[4.6.4.3](#4_6_4_3)

  [\^
index](#alphabetic-index-of-definitions-of-concepts-and-syntactic-units)

 

### Note.

Insofar as this Modified Report is a correct application of the
Supplement to the Revised Report, reproduction for any purpose, but only
of the whole text, is explicitly permitted without formality (see *The
Computer Journal*, Vol. 19, p. 287, final paragraph).

   [\^ top](#modified-report-on-the-algorithmic-language-algol-60)

## Note on the edition

List of symbols and their representation:

  --------------------------------------------------------------------
  \[\]       A blank. Printed like a half box.
  ---------- ---------------------------------------------------------
  \[~10~\]   The ten for the exponent in a real-type number. Printed
             as a small lowered ten.

  \[\]       The power operator: an uparrow.

  \[\]       The times sign: a cross like an x.

  \[÷\]      The integer division operator: a - with a dot above and
             below.

  \[\<\]     Simple: less than.

  \[\]       Simple: less or equal.

  \[=\]      Simple: equal.

  \[\]       Simple: greater or equal.

  \[\>\]     Simple: greater than.

  \[\]       Simple: not equal.

  \[\]       Simple: logical equivalence.

  \[\]       Simple: logical implication.

  \[\]       Simple: logical or.

  \[\]       Simple: logical and.

  \[¬\]      Simple: logical not.

  \[\]       The Greek letter Gamma (upper case).

  \[\]       The Greek letter Theta (upper case).

  \[\]       The Greek letter Sigma (upper case).

  \[\]       The Greek letter Omega (upper case).
  --------------------------------------------------------------------

 

HTML-edition: N.Landsteiner (<n.landsteiner@masswerk.at>)  2001-2002

based on a PDF-document edited by Andrew Makhorin (mao@mai2.rcnet.ru),
Department for Applied Informatics, Moscow Aviation Institute, Moscow,
Russia.

Original publication:

Modified Report on the Algorithmic Language ALGOL 60.\
The Computer Journal, Vol. 19, No. 4, Nov. 1976, pp. 364---379.

 \
Other AGOL 60 related documents to be found on this site:

- [Algol 60
  References](https://algol60.org/)\
   

  - [Revised Report on the Algorithmic Language ALGOL
    60](https://www.algol60.org/reports/algol60_rr.pdf) (1963)\
     

  - [Algol 60 Versions of
    Syntax](https://algol60.org/4documentation.htm)
    (differences between the Revised Report & the Modified Report)\
     

  - [Algol 60 - Sample Implementation and
    Examples](https://algol60.org/5shortIntro.htm)\
     

  - ALGOL 60 syntax (EBNF) as compiled from the Revised Report: [Syntax
    of ALGOL
    60](https://algol60.org/).\
     

  - A step further - SIMULA 67: [Syntax of SIMULA
    67](https://algol60.org/).

   [\^ top](#modified-report-on-the-algorithmic-language-algol-60)

[**mass:werk - media environments**](http://www.masswerk.at/)

 
