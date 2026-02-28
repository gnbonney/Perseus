# Algol Extensions

Algol 60 left much to the imagination of compiler-writers.  Notably, it lacked any standard i/o procedures until the 1976 Modified Report.  It also lacked string variables.  Below is a list of Algol compilers with some of the extensions they implemented.  All of them have their own incompatible i/o procedures developed ahead of the modified standard generally written very specifically for old i/o devices such as punch cards and tapes.

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

# Desirable Language Features 

## Strings

A common theme with the various Algol compilers is the implementation of a string type.  Simula, as noted above, had a Text class.

Java Strings are notoriously slow.  In an article on "How to get C like performance in Java" (http://vanillajava.blogspot.com/2011/05/how-to-get-c-like-performance-in-java.html) one of the suggestions is "...use your own Text type which wraps a byte[], or get you text data from ByteBuffer, CharBuffer or use Unsafe."  (See https://github.com/nitsanw/psy-lob-saw/blob/master/src/util/UnsafeString.java and http://mishadoff.com/blog/java-magic-part-4-sun-dot-misc-dot-unsafe/)  

I noticed with a quick google search that Hadoop has its own Text class (https://hadoop.apache.org/docs/r2.6.0/api/org/apache/hadoop/io/Text.html).  This stores data in UTF8 encoding.  Unlike a Java String, it is not immutable.

This might be useful for implementation of a Simula Text class or we might just use StringBuffer.

The Eclipse git project has a RawText with a string saved as byte[] with no assumption of encoding (http://download.eclipse.org/jgit/docs/jgit-2.0.0.201206130900-r/apidocs/org/eclipse/jgit/diff/RawText.html).  This class is intended to represent a Unix formatted text file, not a general string.

## Lambda Notation

Rutishauser proposed in Handbook for Automatic Computation - Description of Algol an extension of Algol to replace the Jensens Device (pass-by-name) with Church's lambda notation for arrays (similar to anonymous functions or closures in other languages, see https://en.wikipedia.org/wiki/Anonymous_function).  

# Undesirable Language Features 

Programmer and author Mark Seeman suggests, "Many languages have redundant features; progress in language design includes removing those features." (http://blog.ploeh.dk/2015/04/13/less-is-more-language-features/)  He lists several features that he thinks languages could do with out:

* GOTO
* Exceptions
* Pointers
* Lots of specialized number types
* Null pointers
* Mutation
* Reference equality
* Inheritance
* Interfaces
* Reflection
* Cyclic dependencies

I don't agree with all of these, but I do agree with the general "less is more" kind of philosophy.

## Goto

Obviously, some modern languages have eliminated the goto.  To have a standards-compliant Algol 60 compiler, however, goto is a requirement.  Java doesn't have goto, but the JVM actually does, so it is possible to support this much-maligned feature with an Algol-to-JVM compiler.  

## Exceptions

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

## External Procedures

NU Algol had external procedures while Simula 67 had external classes and external procedures.  They had to be declared in the program they were being used in kind of like an import statement in a java class.  In the Simula 67 standard external classes and external procedures were considered "program modules".

In the context of a JVM compiler I think that external classes and procedures (functions) should be usable from other JVM languages.  With regard to external procedures written in Algol that would, I think, mean that such procedures should not have call-by-name parameters.  Also, I don't think you should be able to pass a label to an external procedure, and an external procedure shouldn't be able to have a GOTO leading to the outside of the procedure.

An external procedure declaration would look something like this:

external <kind> <type> procedure <identifier list> ;

<type> is real or integer
<kind> language or implementation-dependent

In NU Algol <kind> was ALGOL or another language such as FORTRAN, but the Simula 67 standard says <kind> is implementation-dependent.  I think for a JVM Algol or Simula compiler, <kind> would generally be a class name such as:

external static(java.lang.Math) real procedure cos(real a);

or

external virtual(java.lang.System.out, java.io.PrintStream) procedure print(string s);

This would provide an easy way to get at some of the most frequently used JRE functions rather than hard-coding inline Jasmine code.

## Header files

This also brings up the question of whether it would be helpful to have header files and an include statement like in C.  Then you could have a file with a bunch of external procedure declarations that could be included in a a main file.  The question is, how much must a JVM language adhere to design decisions of the creator of Java.  Java was based on C and C++, of course, but eliminated some features for simplicity, but other features were added over time making Java at least as complicated as its predecessors.

