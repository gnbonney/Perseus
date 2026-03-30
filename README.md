# Perseus

## Background

Perseus began as an ALGOL 60 compiler targeting the JVM. It is now evolving into an Algol-derived language and compiler platform: one that preserves the best ideas of ALGOL's syntax, structure, and procedural semantics while leaving room for practical extensions and modern language features.

The long-term goal is not merely to reproduce every corner of historical ALGOL 60, but to build a language in the same family. That means Perseus can remain faithful to ALGOL where it is elegant and useful, while also growing in directions that make sense for modern software development, such as richer type systems, structured exceptions, object-oriented features, and deeper interoperability with the Java ecosystem.

Algol is "...the common ancestor of C, Pascal, Algol-68, Modula, Ada, and most other conventional languages that aren't BASIC, FORTRAN, or COBOL."[http://www.catb.org/retro/] Edsger Dijkstra quoted C.A.R. Hoare as saying Algol 60 was, "a major improvement on most of its successors." [https://www.cs.utexas.edu/users/EWD/transcriptions/EWD12xx/EWD1284.html]

## Motivation

Perseus exists to carry the Algol tradition into a practical JVM-based language: preserving block structure, procedural clarity, and call-by-name semantics where they remain valuable, while also supporting classes, exceptions, interoperation with Java, and experimentation with later Algol-family ideas in a compiler that can run in modern toolchains and deployment environments.

## Project Status

Perseus development follows a test-driven, iterative approach built around a broad set of sample programs and regression tests.

That set includes Donald Knuth's classic Man-or-Boy test. It stresses exactly the sort of features that made Algol implementations difficult: recursion, nested procedures, procedure parameters, non-local variable access, and call-by-name behavior. Producing the expected answer `-67.0` is a meaningful sign that Perseus handles a demanding part of the Algol tradition rather than only its surface syntax.

The implemented feature set remains closest to an ALGOL 60 compiler with extensions, while the broader direction is for Perseus to become its own Algol-family language rather than remain only a historical reconstruction.

## Language Features

Perseus supports a growing Algol-family feature set, including:

- classic Algol-style block structure, procedures, arrays, and call-by-name semantics
- procedure parameters and procedure variables
- structured exceptions
- object-oriented features through Simula-inspired classes with `ref(...)`, `new`, instance procedures, and prefix-style inheritance
- dynamic dispatch for overridden procedures
- external procedure linkage and external Java class interop
- file I/O and string-backed output buffers
- formatted I/O through `outformat` and `informat`

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

## Design Goals

Perseus aims to balance several goals:

* Preserve the clarity, structure, and procedural strengths of ALGOL-family languages
* Support historical ALGOL programs where practical
* Introduce modern features deliberately rather than as ad hoc additions
* Fit naturally into the JVM ecosystem and interoperate well with Java
* Remain a useful platform for education, experimentation, and language-design research
