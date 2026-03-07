# Algol to JVM Compiler

## Background

This project has the goal of creating an Algol 60 compiler (with some extensions) which will compile to JVM so that Algol programs can run wherever a Java runtime is installed.  

Algol is "...the common ancestor of C, Pascal, Algol-68, Modula, Ada, and most other conventional languages that aren't BASIC, FORTRAN, or COBOL."[http://www.catb.org/retro/] Edsger Dijkstra quoted C.A.R. Hoare as saying Algol 60 was, "a major improvement on most of its successors." [https://www.cs.utexas.edu/users/EWD/transcriptions/EWD12xx/EWD1284.html]

Algol 68 was not a new version of Algol 60 but "a completely new language".  Many people who already had a big investment in writing Algol compilers were very disappointed in the decision of the international committee to abandon previous work and go off in a new direction.  Dijkstra said of Algol 68, "The more I see of it, the more unhappy I become."  Niklaus Wirth, disillusioned with the committee process, created his own series of Algol-based languages, including Pascal.

Meanwhile, Algol 60 continued a life of its own.  In 1976 the Modified Report on the Algorithmic Language ALGOL 60 was published by IFIP Working Group 2.1.  The modified report gives some details on standard i/o procedures not addressed in previous versions of the report.  This became the basis for ISO1538 in 1984.  Several web sites say that the standard was later withdrawn, but when I checked the ISO web site it says, "This standard was last reviewed and confirmed in 2003. Therefore this version remains current."

## Motivation

Why create a new Algol compiler?

* **AI-Ready and Tool-Friendly:** JAlgol is designed for seamless integration with AI, language servers, and modern developer tools. Its explicit, structured design enables advanced code analysis, transformation, and verification by both humans and AI systems.
* **Education and Research:** ALGOL 60 is a foundational language for computer science, known for its clarity and structured programming. JAlgol makes it accessible on the JVM, providing a robust platform for teaching, research, and experimentation with classic and modern programming concepts.
* **Modern JVM Ecosystem:** There are many JVM compilers for other languages, but none for Algol or Simula. JAlgol fills this gap, enabling legacy and new Algol code to run anywhere Java is available.
* **Readable, Maintainable Code:** ALGOL 60’s low semantic density and mathematical clarity make it ideal for readable, maintainable code—benefiting education, research, and AI-driven development.
* **Open, Extensible Architecture:** JAlgol is built for extensibility, supporting future language features, research extensions, and integration with next-generation tooling.

## Building the Project

This project uses Gradle for build and dependency management.

```
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
- **`gradle generateGrammarSource`**: Regenerates the ANTLR parser and lexer from the grammar file (`Algol.g4`).
- **`gradle build -x test`**: Compiles the source code, generates the ANTLR parser and lexer, and packages the project, skipping all unit tests.

See [docs/Gradle-Build.md](docs/Gradle-Build.md) for more details.

## Using the `compileAlgol` Gradle Task

The `compileAlgol` Gradle task simplifies the process of compiling Algol source files into Jasmin assembly and then assembling the Jasmin files into JVM class files. This task performs the following steps:

1. **Compile Algol to Jasmin**:
   - The task uses the `AntlrAlgolListener` class to parse the Algol source file and generate a Jasmin `.j` file.

2. **Assemble Jasmin to Class Files**:
   - The task invokes the Jasmin assembler to convert the `.j` file into a `.class` file.

### Running the Task

To use the `compileAlgol` task, run the following command:

```bash
gradle compileAlgol
```

This will:
1. Compile the Algol source file located at `test/algol/hello.alg`.
2. Generate the Jasmin file (`Hello.j`) in the `build/test-algol` directory.
3. Assemble the Jasmin file into a JVM class file (`Hello.class`) in the same directory.

### Output
After running the task, you can find the following files in the `build/test-algol` directory:
- `Hello.j`: The generated Jasmin assembly file.
- `Hello.class`: The compiled JVM class file.

You can then run the compiled class file using the `java` command:

```bash
java -cp build/test-algol gnb.jalgol.programs.Hello
```

## Folder Structure of this Project

* `src/main/java/` - Java source code for the compiler
* `src/main/antlr/` - ANTLR grammar files
* `src/test/java/` - Unit tests
* `test/algol/` - Sample Algol programs used for testing
* `jasmin-2.4/` - Jasmin 2.4 assembler, bundled with the project
* `lib/` - Reserved for additional third-party libraries
* `docs/` - Documentation

## Sample Programs

The `test/algol/` directory contains sample Algol 60 programs used for testing the compiler. These programs were sourced from books and the internet to ensure they represent valid Algol 60. See [docs/Samples.md](docs/Samples.md) for sources and details.

## Other Algol 60 Compilers

MARST AND CIM

I found an article (https://www.linuxvoice.com/algol-the-language-of-academia/) that mentions a GNU compiler for Algol called MARST (https://www.gnu.org/software/marst/).  In addition, there's a GNU project for a Simula compiler called Cim.  Both translate code to C instead of compiling directly to assembler or machine code.  As GNU projects they have a restrictive open source license whereas I prefer more permissive licensing such as Eclipse, BSD, or MIT.  However, they may be useful for functional comparison.

## Dependencies

- **Jasmin 2.4**: Used for JVM bytecode generation. The self-contained jar is bundled at `jasmin-2.4/jasmin.jar`. For more information, see [Jasmin on SourceForge](https://jasmin.sourceforge.net/).
- **ANTLR 4**: Managed via Gradle.
- **JUnit 5**: Managed via Gradle for unit testing.

## AI-Friendly Compiler Design

JAlgol is designed from the ground up to be AI-friendly—possibly the first compiler intentionally architected for seamless AI and advanced tooling integration. This means:
- Structured, machine-readable diagnostics with stable error codes and source mapping
- Deterministic, reproducible output for identical inputs
- Explicit, inspectable intermediate representations (AST, IR, JVM IR)
- Fast feedback loops and modern CLI commands for rapid iteration
- Architecture that enables future integration with AI assistants, IDEs, and language servers

Our goal is to make JAlgol not only a robust Algol-to-JVM compiler, but also a model for next-generation, tool-friendly compiler engineering.

## Semantic Clarity for Human and AI Reasoning

ALGOL 60 was designed for mathematical clarity and structured programming, resulting in low semantic density—each statement expresses a single, clear idea. JAlgol preserves this property, making both the source language and the compiler pipeline highly explicit and easy to reason about. This benefits not only human readers but also AI tools and language models, which can more reliably analyze, transform, and verify code with minimal ambiguity. The result is a compiler and language ecosystem that is exceptionally well-suited for education, research, and advanced AI-driven tooling.