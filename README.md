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

## Building the Project

This project uses Gradle for build and dependency management.

```bash
gradle build
gradle test
gradle clean
```

See [docs/Gradle-Build.md](docs/Gradle-Build.md) for more details.

## Gradle Targets

This project uses Gradle for build and dependency management. Below are the key Gradle targets available:

- **`gradle build`**: Compiles the source code, generates the ANTLR parser and lexer, and packages the project.
- **`gradle test`**: Runs all unit tests using JUnit 5.
- **`gradle clean`**: Cleans the build directory, removing all generated files.
- **`gradle generateGrammarSource`**: Regenerates the ANTLR parser and lexer from the grammar file (`Perseus.g4`).
- **`gradle build -x test`**: Compiles the source code, generates the ANTLR parser and lexer, and packages the project, skipping all unit tests.

See [docs/Gradle-Build.md](docs/Gradle-Build.md) for more details.

## Using the `compilePerseus` Gradle Task

The `compilePerseus` Gradle task simplifies the process of compiling Perseus source files into Jasmin assembly and then assembling the Jasmin files into JVM class files. This task performs the following steps:

1. **Compile source to Jasmin**:
   - The task uses the `PerseusCompiler` class to parse the source file and generate the main Jasmin `.j` file plus any needed companion `.j` files for thunks and procedure references.
2. **Assemble Jasmin to class files**:
   - The task invokes the Jasmin assembler to convert the main `.j` file, its generated companion `.j` files, and any emitted support interfaces into `.class` files.

### Running the Task

To use the `compilePerseus` task, you can specify the input file, output directory, and class name as parameters. For example:

```bash
gradle compilePerseus -PinputFile=test/algol/myfile.alg -PoutputDir=build/output -PclassName=MyClass
```

This will:

1. Compile the source file located at `test/algol/myfile.alg`.
2. Generate the main Jasmin file (`MyClass.j`) and any needed companion `.j` files in the `build/output` directory.
3. Assemble that Jasmin family into JVM class files in the same directory.

### Default Behavior

If no parameters are provided, the task defaults to:

- **Input File**: `test/algol/hello.alg`
- **Output Directory**: `build/test-algol`
- **Class Name**: `Hello`

### Output

After running the task, you can find the following files in the specified output directory:

- `MyClass.j`: The generated main Jasmin assembly file.
- `MyClass.class`: The compiled main JVM class file.
- `MyClass$ThunkN.j` / `MyClass$ThunkN.class`: Generated when call-by-name thunks are needed.
- `MyClass$ProcRefN.j` / `MyClass$ProcRefN.class`: Generated when procedure references are needed.
- `Thunk.j` / `Thunk.class` and `ProcedureInterfaces.j` / companion interface `.class` files when the program needs those runtime support interfaces.

You can then run the compiled class file using the `java` command:

```bash
java -cp build/output gnb.perseus.programs.MyClass
```

## Using the Perseus CLI

In addition to the Gradle task, you can use the `PerseusCLI` directly to compile source files. The CLI provides a simple interface for specifying the input file, output directory, and class name.

### Running the CLI

To use the CLI, run the following command:

```bash
java -cp build/classes/java/main gnb.perseus.cli.PerseusCLI <inputFile> <outputDir> <className>
```

For example:

```bash
java -cp build/classes/java/main gnb.perseus.cli.PerseusCLI test/algol/hello.alg build/output Hello
```

This will:

1. Compile the source file located at `test/algol/hello.alg`.
2. Generate the main Jasmin file (`Hello.j`) and any needed companion `.j` files in the `build/output` directory.
3. Assemble that Jasmin family into JVM class files in the same directory.

### Output

After running the CLI, you can find the following files in the specified output directory:

- `Hello.j`: The generated main Jasmin assembly file.
- `Hello.class`: The compiled main JVM class file.
- `Hello$ThunkN.*` and `Hello$ProcRefN.*` companions when the program uses call-by-name parameters or procedure references.

You can then run the compiled class file using the `java` command:

```bash
java -cp build/output gnb.perseus.programs.Hello
```

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


