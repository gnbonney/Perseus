# Project Architecture

This document describes the high-level architecture of the Algol-to-JVM compiler project.

## Overview

The project consists of several main components:
- **Frontend (Parser & Lexer):** Parses Algol source code using ANTLR grammar.
- **Semantic Analysis:** Builds symbol tables, checks types, and validates scope.
- **Code Generation:** Translates parsed Algol code into JVM bytecode using Jasmin.
- **Testing & Samples:** Includes sample Algol programs and JUnit tests for validation.
- **Supporting Tools:** Uses Soot for bytecode analysis/disassembly and Java CUP for alternative parsing.

## Component Diagram

```mermaid
flowchart TD
    A[Algol Source Code] --> B[ANTLR Lexer/Parser]
    B --> C[Parse Tree]
    C --> D[Semantic Analysis]
    D --> E[Code Generation]
    E --> F[Jasmin Assembler]
    F --> G[JVM Bytecode]
    G --> H[Run on JVM]
    F --> I[Soot Disassembly/Analysis]
```

## Data Flow

```mermaid
sequenceDiagram
    participant User
    participant Parser
    participant Analyzer
    participant Generator
    participant JVM
    User->>Parser: Provide Algol source
    Parser->>Analyzer: Parse to AST
    Analyzer->>Generator: Semantic checks
    Generator->>JVM: Emit JVM bytecode
    JVM-->>User: Execute program
```

## Directory Structure

- `src/main/java/` - Java source code
- `src/main/antlr/` - ANTLR grammar files
- `src/test/java/` - Unit tests
- `test/algol/` - Sample Algol programs used for testing
- `lib/` - Third-party libraries (Jasmin; ANTLR and Soot managed via Gradle)
- `docs/` - Documentation

## Future Extensions

- Support for more Algol features
- Improved error handling and diagnostics
- IDE integration (syntax highlighting, auto-completion)
- More advanced optimizations and analysis

---

_Last updated: February 28, 2026_
