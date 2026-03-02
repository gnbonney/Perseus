# Project Architecture

This document describes the high-level architecture of the Algol-to-JVM compiler project.

## Overview

The project consists of several main components:
- **Frontend (Parser & Lexer):** Parses Algol source code using ANTLR grammar.
- **Pass 1 — Symbol Table Construction:** Walks the parse tree to collect variable names, types, and block scope nesting. Required before code generation because Jasmin needs `.limit locals N` declared before method body instructions, and forward `goto` labels must be known before jumps are emitted.
- **Pass 2 — Code Generation:** Walks the parse tree a second time, using the symbol table from Pass 1, to emit Jasmin assembly instructions.
- **Assembly:** Jasmin assembles the `.j` output into JVM `.class` files.
- **Testing & Samples:** Includes sample Algol programs and JUnit tests for validation.
- **Supporting Tools:** Soot can be used standalone for bytecode analysis/disassembly but is not a build dependency (see [Development.md](Development.md)).

## Component Diagram

```mermaid
flowchart TD
    A[Algol Source Code] --> B[ANTLR Lexer/Parser]
    B --> C[Parse Tree]
    C --> D[Pass 1: Symbol Table Builder]
    D --> E[Symbol Table]
    C --> F[Pass 2: Code Generator]
    E --> F
    F --> G[Jasmin Source]
    G --> H[Jasmin Assembler]
    H --> I[JVM Bytecode]
    I --> J[Run on JVM]
```

## Data Flow

```mermaid
sequenceDiagram
    participant User
    participant Parser
    participant SymbolTableBuilder
    participant CodeGenerator
    participant Jasmin
    participant JVM
    User->>Parser: Provide Algol source
    Parser->>SymbolTableBuilder: Parse tree (Pass 1)
    SymbolTableBuilder->>CodeGenerator: Symbol table
    Parser->>CodeGenerator: Parse tree (Pass 2)
    CodeGenerator->>Jasmin: Emit Jasmin source
    Jasmin->>JVM: Assemble to .class
    JVM-->>User: Execute program
```

## Directory Structure

- `src/main/java/` - Java source code
- `src/main/antlr/` - ANTLR grammar files
- `src/test/java/` - Unit tests
- `test/algol/` - Sample Algol programs used for testing
- `jasmin-2.4/` - Jasmin 2.4 assembler (jar bundled with project; ANTLR managed via Gradle)
- `lib/` - Reserved for additional third-party libraries
- `docs/` - Documentation

## Future Extensions

- Support for more Algol features
- Improved error handling and diagnostics
- IDE integration (syntax highlighting, auto-completion)
- More advanced optimizations and analysis

---

_Last updated: March 1, 2026_
