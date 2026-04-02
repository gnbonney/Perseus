# Algol Extensions

Algol 60 left much to the imagination of compiler writers. Notably, it had no standard I/O procedures until the 1976 Modified Report. String support was also limited and inconsistent across the language's history: the Modified Report includes a `string` type in some examples, but only specifies a `length` function, not a broader library of helpers such as `substring` or `concat`. As a result, many Algol compilers introduced their own incompatible extensions, especially for I/O. Those systems were often developed before the Modified Report and were frequently tailored to older devices such as punch cards and tapes.

## Data General Extended Algol

Data General's Extended Algol 60 was designed for the Nova series minicomputers. It included a number of practical extensions to standard Algol 60 to support real-world programming and system integration:

* Extended I/O facilities, including file handling and device-specific operations
* String variables and string manipulation functions
* Additional arithmetic types and operations
* System calls and hooks for interacting with the Nova operating system
* Enhanced error handling and debugging features
* Support for larger identifier lengths and relaxed syntax rules
* Pragmas and compiler directives for optimization and system integration

These extensions made the language more suitable for business and scientific applications on Data General hardware, at the cost of deviating from strict Algol 60 compatibility.

## DEC Algol (decsystem10-20)

DEC Algol was implemented for the PDP-10 and PDP-20 mainframes. Its notable features and extensions included:

* Comprehensive I/O system, including file I/O and formatted input/output
* String handling and manipulation extensions
* Support for larger programs and data structures (arrays, records)
* Additional built-in functions and mathematical routines
* Extended error handling and diagnostic messages
* Integration with the TOPS-10/TOPS-20 operating systems (e.g., system calls, batch processing)
* Optional language features and switches for compatibility or performance

DEC Algol was widely used in academic and research settings, and its extensions reflected the needs of users on DEC mainframes, often prioritizing practicality and system integration over strict adherence to the original Algol 60 specification.

## MC-Translator

In his book, A Primer of Algol 60 Programming, Dijkstra describes this translator written at the Mathematical Centre, Amsterdam.  The translator had some minor limitations and differences to standard Algol, and had very basic i/o routines including read and print (for numbers only).

## NU Algol (Norwegian University Algol)

This was an Algol compiler for Univac 1100 series computers "... designed and implemented as a joint effort of the Norwegian Computing Center, Oslo, and the Computing Center at the Technical University of Norway, Trondheim." 

Note that Univac had at least three different Algol compilers, Algol, Extended Algol (not related to the Burroughs Extend Algol), and NU Algol.  I believe NU Algol was their most advanced Algol compiler.

Extensions to ALGOL 60:
* STRING and STRING ARRAY variables
* arithmetic types COMPLEX and REAL2 [double precision]
* XOR logical operator
* EXTERNAL PROCEDURE declarations for arranging code into libraries
* I/O routines along with FORMAT and LIST declarations
* A compact form for GO TO and FOR statements
* Variables are zeroed upon entry to a block so that initialization statements are not required.
* The controlled variable of a FOR statement has a defined value when the statement is terminated by exhaustion of the FOR list.
* Debugging statements and compiler directives

(NU Algol i/o procedures could take a list of "error labels" to catch exceptions, but implemented more like goto.)

Changes from ALGOL 60:
1. 12 characters limit to identifiers
1. Identifiers and numbers may not contain blanks
1. Certain ALGOL words may only be used in a specific context
1. No OWN variables 
1. No Numeric labels 
1. The comma is the only parameter delimiter allowed in a procedure call
1. The result of an integer raised to an integer power is always of type REAL
1. All the formal parameters of a procedure must be specified
1. In a Boolean expression, only those operands necessary for determining the result are evaluated

## Algol W

Algol W was a compiler developed along the lines of research by Niklaus Wirth before he abandoned Algol efforts for good and moved on to Pascal.

Features added:
* string, bitstring, complex number 
* reference to record datatypes 
* call-by-result passing of parameters
* while statement

Features changed or removed:
* replaced switch with the case statement

## Burroughs/Unisys Extended Algol

One of the most successful Algol compilers in terms of longevity and actual businesses use as opposed to university research.  It is used even today on mainframes manufactured by Unisys.

A couple of the many features added by Extended Algol:
* EVENT data type facilitates coordination between processes
* fault declarations and the ON statement for catching faults plus declaration of EXCEPTION procedures (I'm not real clear on why both were needed and how the two worked together)

### Exceptions

Seeman suggests, "A better approach is to use a sum type to indicate either success or failure in a composable form."  However, exceptions are a concern for any JVM language.  It's not really an option to just get rid of them.

Unisys Extended Algol had three ways to handle a fault, the first being the ON statement:

```
ON ZERODIVIDE OR INVALIDINDEX [FAULTARRAY:FAULTNO]:
   BEGIN
   REPLACE FAULTARRAY[8] BY FAULTNO FOR * DIGITS;
   WRITE(LINE, 22, FAULTARRAY);
   REPLACE FAULTARRAY BY " " FOR 22 WORDS;
   CASE FAULTNO OF
      BEGIN
      1: DIVISOR := 1;
      4: INDEX := 100;
      END;
   GO BACK;
   END
```

Extended Algol also had exception procedures:

```
PROCEDURE P1;
   BEGIN
   REAL A, B;
   FILE MYFILE (KIND=DISK);
   EXCEPTION PROCEDURE CLEANUP;
      BEGIN
      CLOSE (MYFILE, LOCK);
      END; %OF EXCEPTION PROCEDURE CLEANUP
   IF MYFILE.AVAILABLE THEN
      BEGIN
      OPEN (MYFILE);
      CLEANUP; % A DIRECT CALL TO THE EXCEPTION PROCEDURE
      END;
   A := 17* (B + 4);
   END; % OF PROCEDURE P1. THE PROCEDURE CLEANUP WILL BE
        % INVOKED AUTOMATICALLY IF WE EXIT P1 ABNORMALLY.
```

Notice you cannot see what error caused the exception procedure.  Extended Algol also had a TRY-ELSE statement which is kind of like a Java try-catch, but which doesn't gives you an opportunity to see what the error was.  So, in Extended Algol if you don't want the error to be "swallowed" you need to use the ON statement with the fault array.  

If the ON statement and the exception procedure were combined, you might have something like this:

```
begin
exception procedure cleanup(a); % based on Extended Algol exception procedure but with parameter
	string array a; % convert stacktrace to array of strings since Algol doesn't have classes
	begin
	outstring(stdout, "error ");
	fault(a[1], -1); % prints the first line of stack trace and exits the program with -1
	end;
% do something that might throw an exception
end;
```

Note that string arrays would be another extension to Algol. Also note that Simula already has an error() procedure which is equivalent to Algol's fault() procedure, both of which basically throw an exception that cannot be caught and exit the program.  The only difference is that error() doesn't take an exit code as one of its parameters.

## Simula67

The book Simula Begin says that Simula (later standardized by the Simula Standards Group, see http://simula67.at.ifi.uio.no/Standard-86/) was an extension of Algol 60.  It's usually treated as a separate language, but the list of differences from Algol 60 is actually shorter than the list for NU Algol.  By contrast, Algol 68 really was a new language although it misleadingly kept the Algol name.

Features added:
* external program modules
* classes, subclasses and virtual procedures
* new and ref keywords
* call by reference
* Text class
* file i/o classes (file, infile, outfile, etc.)
* coroutines
* Simset and Simulation classes

Features changed or removed:
* Simula allows call by name, but only if a parameter is explicitly declared with the "name" keyword.  For value-type variables the default is call by value, and for all other quantities it is call by reference.
* In Algol 60 the value of a variable is undefined until it has something assigned to it.  In Simula, all variables are initialized according to their type.  This includes the result variable in functions.  Simula does not require functions to assign to a result variable.
* Like the NU Algol compiler, Simula does not allow own variables.

The book Simula Begin lists the replacement of string type with the more powerful text class as a difference from Algol 60, but actually the string type was never part of the Algol 60 standard but an extension implemented by various compilers.
