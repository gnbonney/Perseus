# Algol to JVM Compiler

## Background

This project has the goal of creating an Algol 60 compiler (with some extensions) which will compile to JVM so that Algol programs can run wherever a Java runtime is installed.  

Algol is "...the common ancestor of C, Pascal, Algol-68, Modula, Ada, and most other conventional languages that aren't BASIC, FORTRAN, or COBOL."[http://www.catb.org/retro/] Edsger Dijkstra quoted C.A.R. Hoare as saying Algol 60 was, "a major improvement on most of its successors." [https://www.cs.utexas.edu/users/EWD/transcriptions/EWD12xx/EWD1284.html]

Algol 68 was not a new version of Algol 60 but "a completely new language".  Many people who already had a big investment in writing Algol compilers were very disappointed in the decision of the international committee to abandon previous work and go off in a new direction.  Dijkstra said of Algol 68, "The more I see of it, the more unhappy I become."  Niklaus Wirth, disillusioned with the committee process, created his own series of Algol-based languages, including Pascal.

Meanwhile, Algol 60 continued a life of its own.  In 1976 the Modified Report on the Algorithmic Language ALGOL 60 was published by IFIP Working Group 2.1.  The modified report gives some details on standard i/o procedures not addressed in previous versions of the report.  This became the basis for ISO1538 in 1984.  Several web sites say that the standard was later withdrawn, but when I checked the ISO web site it says, "This standard was last reviewed and confirmed in 2003. Therefore this version remains current."

## Motivation

Why would I write my own compiler?  
* When asked by a reporter why anyone would want to climb a mountain, George Mallory famously replied, "Because it's there."  For a programmer writing a compiler is a significant challenge and that by itself is enough reason.  
* Years ago when I was in college I took a compilers class and was disappointed that we spent the entire semester on theory and never actually wrote a compiler.  After that the closest I ever came to writing something like a compiler was a program for converting between different graphics file formats used by a flight simulator company.  I used lex and yacc for that project, but I never wrote a complete compiler for a programming language.  In recent years the idea of writing my own compiler has become a kind of "bucket list" item.
* Recently in my jobs I have been doing more Javascript than Java, so this project is also a way to keep up my Java skills.

Why create a new Algol compiler?
* Algol is an elegant small programming language designed specifically for readability.
* I am interested in writing a compiler for Simula 67 which is basically a superset or extension of Algol.
* Algol and Simula are still discussed in many computer science classes, because they were so influential and because they have features that aren't commonly found in other languages.
* Currently, there are many JVM compilers for different languages, but not one for Algol or Simula.

## Building the Project

This project uses Gradle for build and dependency management.

```
gradle build
gradle test
gradle clean
```

See [docs/Gradle-Build.md](docs/Gradle-Build.md) for more details.

## Folder Structure of this Project

* `src/main/java/` - Java source code for the compiler
* `src/main/antlr/` - ANTLR grammar files
* `src/test/java/` - Unit tests
* `test/algol/` - Sample Algol programs used for testing
* `lib/` - Third-party libraries not available via Gradle (Jasmin)
* `docs/` - Documentation

## Sample Programs

The `test/algol/` directory contains sample Algol 60 programs used for testing the compiler. These programs were sourced from books and the internet to ensure they represent valid Algol 60. See [docs/Samples.md](docs/Samples.md) for sources and details.

## Other Algol 60 Compilers

MARST AND CIM

I found an article (https://www.linuxvoice.com/algol-the-language-of-academia/) that mentions a GNU compiler for Algol called MARST (https://www.gnu.org/software/marst/).  In addition, there's a GNU project for a Simula compiler called Cim.  Both translate code to C instead of compiling directly to assembler or machine code.  As GNU projects they have a restrictive open source license whereas I prefer more permissive licensing such as Eclipse, BSD, or MIT.  However, they may be useful for functional comparison.

## Dependencies

- **Jasmin 2.4**: Used for JVM bytecode generation. Jasmin is not available on Maven Central; the jasmin-2.4 jar is included in the `lib/` directory. For more information, see [Jasmin on SourceForge](https://jasmin.sourceforge.net/).
- **ANTLR**: Managed via Gradle.
- **Soot**: Managed via Gradle.
- **JUnit**: Managed via Gradle for unit testing.