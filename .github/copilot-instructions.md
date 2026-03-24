# Copilot Instructions for Perseus

## Project Overview
Perseus is a modular, multi-pass compiler that translates Algol 60 (and extensions) source code into Jasmin assembly for the JVM. It is designed for education, research, and language experimentation, supporting modern compiler architecture, deterministic output, and advanced Algol features (call-by-name, procedure variables, etc.).

**Audience:** Compiler developers, language researchers, and students interested in Algol, JVM bytecode, or compiler construction.

**Key Features:**
- Two-pass compilation: symbol table + codegen
- Modular codegen (delegation pattern)
- Jasmin output for JVM
- Full test suite with sample Algol programs
- Support for call-by-name, procedure variables, and environmental block

---

## Tech Stack
- **Java 21**: All core logic, using modern language features
- **ANTLR4**: Parser and lexer for Algol grammar
- **Jasmin 2.4**: JVM assembler (output target)
- **JUnit 5**: Testing framework
- **Gradle**: Build and dependency management
- **Soot**: (Optional) Bytecode analysis/disassembly

---

## Agent Tool Usage Rules
- **NEVER use shell text-editing tools** (`sed -i`, `awk`, `perl -i`, `cat > file`, etc.) to modify source files. These tools can corrupt files with invisible characters, wrong indentation, or incorrect whitespace that is difficult to debug.
- **ALWAYS use VS Code editor tools** (`replace_string_in_file`, `multi_replace_string_in_file`, `create_file`, `read_file`) for all source file edits.
- Shell tools (`grep`, `cat -n`, terminal output) are acceptable for **read-only inspection only**.
- If `replace_string_in_file` fails to match, re-read the target section with `read_file` to get the exact text, then retry. Do not fall back to `sed`.
- Never create temporary `.py`, `.txt`, or other scratch files to work around editing limitations. Fix the issue directly using the editor tools.

---

## Coding Guidelines
- Use Java 21 idioms and features where appropriate
- Prefer modular, delegation-based architecture (see docs/Architecture.md)
- Place codegen logic in the correct generator: ExpressionGenerator, StatementGenerator, ProcedureGenerator, or ContextManager
- Keep symbol table, type inference, and codegen logic separated
- Emit deterministic Jasmin output for reproducible builds/tests
- All new features/bugfixes must be covered by tests (see src/test/java/gnb/perseus/compiler/AntlrAlgolListenerTest.java)
- Document major architectural changes in docs/Architecture.md
- Use clear, maintainable code over brevity

---

## Project Structure
- `src/main/java/gnb/perseus/compiler/` : Main compiler logic
  - `antlr/` : ANTLR grammar and generated parser
  - `codegen/` : Modular codegen classes (ExpressionGenerator, StatementGenerator, ProcedureGenerator, ContextManager)
  - `SymbolTableBuilder.java` : Symbol table pass
  - `TypeInferencer.java` : Type inference pass
  - `CodeGenerator.java` : Facade listener, delegates to codegen modules
- `src/test/java/gnb/perseus/compiler/` : JUnit tests
- `test/algol/` : Sample Algol programs for milestone-driven testing
- `jasmin-2.4/` : Jasmin assembler
- `docs/` : Architecture, refactoring plan, and design docs
- `build.gradle` : Gradle build config

---

## Resources
- **Build/test:** `gradle build`, `gradle test`
- **Run sample:** See test/algol/ and docs/Compiler-TODO.md for milestone programs
- **Key docs:**
  - `README.md` : Quickstart and overview
  - `docs/Architecture.md` : Modular architecture and data flow
  - `docs/Refactoring-Plan.md` : Refactor phases and progress
  - `docs/Compiler-TODO.md` : Milestone-driven feature tracking
- **Scripts:**
  - `jasmin-2.4/build.sh` / `build.bat` : Jasmin assembler
- **Automation:**
  - Copilot Chat and agents should always check for modular code before suggesting monolithic logic
  - Update the todo list and commit after each major milestone
- **Other Gradle commands:**
  - `gradle clean` : Cleans the build directory, removing all generated files.
  - `gradle generateGrammarSource` : Regenerates the ANTLR parser and lexer from the grammar file (`Algol.g4`).
  - `gradle build -x test` : Compiles the source code and packages the project, skipping all unit tests.
  - `gradle compileAlgol -PinputFile=<file> -PoutputDir=<dir> -PclassName=<name>` : Compiles an Algol source file to Jasmin and assembles it to a JVM class file (see README.md for usage).

---

_Last updated: March 8, 2026_