# Perseus

## Background

Perseus began as an ALGOL 60 compiler targeting the JVM. It is now evolving into an Algol-derived language and compiler platform: one that preserves the best ideas of ALGOL's syntax, structure, and procedural semantics while leaving room for practical extensions and modern language features.

The long-term goal is not merely to reproduce every corner of historical ALGOL 60, but to build a language in the same family. That means Perseus can remain faithful to ALGOL where it is elegant and useful, while also growing in directions that make sense for modern software development, such as richer type systems, structured exceptions, object-oriented features, and deeper interoperability with the Java ecosystem.

Algol is "...the common ancestor of C, Pascal, Algol-68, Modula, Ada, and most other conventional languages that aren't BASIC, FORTRAN, or COBOL."[http://www.catb.org/retro/] Edsger Dijkstra quoted C.A.R. Hoare as saying Algol 60 was, "a major improvement on most of its successors." [https://www.cs.utexas.edu/users/EWD/transcriptions/EWD12xx/EWD1284.html]

Algol 68 was not a new version of Algol 60 but "a completely new language". Many people who already had a big investment in writing Algol compilers were very disappointed in the decision of the international committee to abandon previous work and go off in a new direction. Dijkstra said of Algol 68, "The more I see of it, the more unhappy I become." Niklaus Wirth, disillusioned with the committee process, created his own series of Algol-based languages, including Pascal.

Meanwhile, Algol 60 continued a life of its own. In 1976 the Modified Report on the Algorithmic Language ALGOL 60 was published by IFIP Working Group 2.1. The modified report gives some details on standard I/O procedures not addressed in previous versions of the report. This became the basis for ISO1538 in 1984. Several web sites say that the standard was later withdrawn, but when I checked the ISO web site it says, "This standard was last reviewed and confirmed in 2003. Therefore this version remains current."

## Motivation

Why create a new Algol-based language for the JVM?

* **A Strong Historical Foundation:** ALGOL 60 remains one of the most influential languages ever designed. Its block structure, procedural abstraction, and emphasis on clarity still make it a valuable starting point for language design.
* **A Bridge Between Classic and Modern Ideas:** Perseus provides a path from classic ALGOL-style programming to modern features such as classes, exceptions, richer libraries, and improved tooling without abandoning the language family entirely.
* **Modern JVM Ecosystem:** Targeting the JVM means Perseus programs can run anywhere Java runs and can benefit from mature tooling, packaging, debugging, profiling, and deployment options.
* **Access to the Java Ecosystem:** A language on the JVM can eventually interoperate with Java libraries and frameworks, making it more practical than a purely historical implementation.
* **A Platform for Experimentation:** Perseus is not limited to strict language preservation. It is also a place to explore thoughtful extensions in the spirit of Algol, much as Simula and Pascal grew from the same lineage.
* **Readable, Structured Code:** ALGOL's block-oriented design still encourages code that is explicit, disciplined, and approachable for teaching, experimentation, and long-term maintenance.

## Project Status

Perseus development follows a test-driven, iterative approach built around a variety of sample programs, including Donald Knuth's classic Man-or-Boy test.

The Man-or-Boy test is notable because it stresses exactly the sort of features that made Algol implementations difficult: recursion, nested procedures, procedure parameters, non-local variable access, and call-by-name behavior. Many older Algol compilers either did not implement those features fully or handled them incorrectly, so passing `manboy.alg` and producing the expected answer `-67.0` is a meaningful sign that Perseus is handling a demanding part of the Algol tradition rather than just its surface syntax.

Today, the implemented feature set is still closest to an ALGOL 60 compiler with extensions. The broader direction, however, is for Perseus to become its own Algol-family language rather than remain only a historical reconstruction.

## Installing and Running Perseus

### Build the Launcher Distribution

From the project root:

```bash
gradle installDist
```

On Windows this produces:

```text
build/install/perseus/bin/perseus.bat
```

On macOS and Linux this produces:

```text
build/install/perseus/bin/perseus
```

### Compile a Program

Example:

```bash
perseus test/algol/core/hello.alg -d build/output
```

This compiles and assembles the program into JVM class files under the requested output directory.

### Override the Main Class Name

Example:

```bash
perseus test/algol/core/hello.alg -d build/output --class-name HelloDemo
```

This is useful when you want the generated main program class to use a specific name instead of the default name inferred from the source filename.

### Package a Runnable JAR

Example:

```bash
perseus test/algol/core/hello.alg -d build/output --jar build/output/hello.jar
```

This compiles the program, assembles the class family, and packages a runnable JAR.

### Choose a JVM Package

Example:

```bash
perseus test/algol/core/hello.alg -d build/output --package mylib.demo --class-name HelloDemo
```

This is useful when you want stable package and class names for separate compilation, library-style workflows, or external procedure linkage.

### Compile Against External Libraries

Example:

```bash
perseus test/algol/external/external_algol_client.alg -d build/output -cp build/libs
```

This lets the compiler resolve separately compiled Perseus libraries and external Java classes on the ordinary JVM classpath.

### Current CLI Options

- `-d <outdir>` selects the output directory for generated artifacts
- `--jar <file>` creates a runnable JAR after compilation
- `--class-name <name>` overrides the inferred main class name when needed
- `--package <name>` chooses the generated JVM package name
- `-cp <path>` / `--classpath <path>` adds directories or JARs for external resolution

## Developer Workflow

This project uses Gradle for build and dependency management.

Common commands:

```bash
gradle build
gradle test
gradle clean
gradle generateGrammarSource
```

See [docs/Gradle-Build.md](docs/Gradle-Build.md) for more details.

### The `compilePerseus` Gradle Task

The `compilePerseus` task remains useful for compiler development and debugging. It compiles a source file to Jasmin and then assembles the generated `.j` family into `.class` files.

Example:

```bash
gradle compilePerseus -PinputFile=test/algol/myfile.alg -PoutputDir=build/output -PclassName=MyClass
```

This is primarily a developer convenience task. End users should generally prefer the `perseus` launcher workflow above.

### Typical Development Outputs

Depending on the program, compilation may produce:

- `MyClass.j` and `MyClass.class`
- `MyClass$ThunkN.*` for call-by-name support
- `MyClass$ProcRefN.*` for procedure references
- `Thunk.*` and `ProcedureInterfaces.*` support artifacts when needed

For VS Code users, the [Algol 60 syntax highlighter](https://marketplace.visualstudio.com/items?itemName=TrisTOON.language-algol60) by TrisTOON is a useful extension for editing `.alg` files.

## Folder Structure of this Project

* `src/main/java/` - Java source code for the compiler
* `src/main/antlr/` - ANTLR grammar files
* `src/test/java/` - Unit tests
* `test/algol/` - Sample source programs used for testing
* `jasmin-2.4/` - Jasmin 2.4 assembler, bundled with the project
* `lib/` - Reserved for additional third-party libraries
* `docs/` - Documentation

## Sample Programs

The `test/algol/` directory contains sample Algol-family programs used for testing the compiler. Many were sourced from classic ALGOL 60 examples, books, and reference material so the project can be checked against historically meaningful programs while the language evolves. See [docs/Samples.md](docs/Samples.md) for sources and details.

## Dependencies

- **Jasmin 2.4**: Used for JVM bytecode generation. The self-contained jar is bundled at `jasmin-2.4/jasmin.jar`. For more information, see [Jasmin on SourceForge](https://jasmin.sourceforge.net/).
- **ANTLR 4**: Managed via Gradle.
- **JUnit 5**: Managed via Gradle for unit testing.

## Why an Algol-Superset on the JVM?

An Algol-superset language on the JVM offers a combination that is hard to find elsewhere:

* **Classic language design with modern runtime support:** Perseus can preserve ALGOL's strengths while relying on the JVM for portability, garbage collection, runtime services, and a mature execution environment.
* **Room for pragmatic extensions:** Features such as classes, exceptions, modules, or improved standard libraries can be added without needing to abandon the language's Algol roots.
* **A path to real-world usability:** JVM deployment makes it easier to package, test, run, and eventually integrate Perseus code into existing systems.
* **A useful research and education platform:** Perseus can serve both as an implementation of important historical ideas and as a sandbox for exploring how those ideas evolve in a modern setting.

The inspiration here is less "strictly preserve every historical detail" and more "carry the best ideas of the Algol tradition forward into a language that can still grow."

## Design Goals

Perseus aims to balance several goals:

* Preserve the clarity, structure, and procedural strengths of ALGOL-family languages
* Support historical ALGOL programs where practical
* Introduce modern features deliberately rather than as ad hoc additions
* Fit naturally into the JVM ecosystem and eventually interoperate well with Java
* Remain a useful platform for education, experimentation, and language-design research


