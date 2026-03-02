# AI-Friendly Compiler Design for Algol-to-JVM

## Introduction

AI-friendly compiler design refers to engineering practices that make a compiler more accessible to automated tools, including AI-based assistants, static analyzers, and advanced IDE integrations. In practical terms, this means:

- Emitting structured, machine-readable diagnostics with stable error codes and explicit source locations.
- Ensuring deterministic, reproducible output for identical inputs.
- Exposing explicit, well-defined intermediate stages (AST, IR, JVM IR) for inspection and tooling.
- Providing fast feedback loops for rapid iteration, enabling both human and AI-assisted workflows.

This document outlines best practices for implementing an AI-friendly compiler for an Algol-like language targeting the JVM, with a backend that emits Jasmin assembler and produces .class files.

## Compiler Architecture Overview

A robust, AI-friendly compiler pipeline should be organized into clear, inspectable stages:

1. **Lexing**: Tokenizes Algol source code.
2. **Parsing**: Produces an Abstract Syntax Tree (AST).
3. **Name Resolution**: Resolves identifiers and scopes.
4. **Type Checking**: Annotates the AST with type information.
5. **Intermediate Representation (IR) Generation**: Lowers the typed AST to a JVM-oriented IR.
6. **JVM IR Optimization**: Performs JVM-specific transformations.
7. **Jasmin Generation**: Emits Jasmin assembler code.
8. **Class File Production**: Assembles Jasmin into JVM .class files.

**Key Definitions:**
- **AST (Abstract Syntax Tree):** Hierarchical representation of source code structure.
- **IR (Intermediate Representation):** Lower-level, language-agnostic representation suitable for analysis and transformation.
- **Typed IR:** IR annotated with type information, enabling type-driven optimizations and error checking.
- **JVM IR:** IR tailored for JVM bytecode semantics, bridging the gap between high-level constructs and Jasmin output.

**Benefits of Stage Separation:**
- Easier debugging and targeted inspection at each stage.
- Enables AI tools to analyze, transform, or suggest fixes at multiple abstraction levels.
- Facilitates deterministic output and reproducible builds.

## Diagnostic Design

Diagnostics are a primary interface between the compiler and both users and automated tools. Best practices include:

- **Stable Error Codes:** Assign unique, stable codes to each diagnostic for easy reference and automated handling.
- **Deterministic Ordering:** Emit diagnostics in a consistent order (e.g., by file, then line, then column).
- **Multiple Errors per Run:** Report as many errors as possible in a single pass, rather than aborting on the first error.
- **Structured JSON Output:** Emit diagnostics in a machine-readable JSON format, suitable for IDEs and AI tools.
- **Source Span Information:** Include file name, line, and column for both start and end of the error span.
- **Fix-It Suggestions:** Where possible, provide explicit text edits to resolve the issue.

**Example JSON Diagnostic:**

```json
{
  "code": "ALGOL1001",
  "message": "Undeclared variable 'x'",
  "severity": "error",
  "file": "example.algol",
  "range": {
    "start": { "line": 12, "column": 5 },
    "end": { "line": 12, "column": 6 }
  },
  "suggestions": [
    {
      "title": "Declare variable 'x'",
      "edit": {
        "insert": "integer x;\n",
        "position": { "line": 12, "column": 0 }
      }
    }
  ]
}
```

## Deterministic Code Generation

Deterministic output is essential for reproducible builds, stable testing, and effective AI-assisted workflows. Key practices:

- **Deterministic Label Naming:** Use predictable, collision-free label schemes (e.g., `L1`, `L2`, ... based on source order).
- **Stable Ordering:** Emit methods, fields, and class members in a canonical order (e.g., sorted by name or source position).
- **Canonical Formatting:** Format Jasmin output consistently (indentation, spacing, line breaks).
- **Reproducible Output:** Ensure identical inputs always produce byte-for-byte identical Jasmin and .class files.

**Example Jasmin Output:**

```jasmin
.class public Hello
.super java/lang/Object

.method public static main([Ljava/lang/String;)V
    .limit stack 2
    .limit locals 1
    getstatic java/lang/System/out Ljava/io/PrintStream;
    ldc "Hello, world!"
    invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V
    return
.end method
```

## Stack and JVM Verification Safety

JVM bytecode must satisfy strict verification rules. Compiler design should:

- **Track Stack Depth:** Maintain a stack depth counter during code generation.
- **Compute .limit Directives:** Automatically calculate `.limit stack` and `.limit locals` for each method.
- **Detect Stack Errors:** Report stack underflow/overflow at compile time, not just at runtime.
- **Source Mapping for Verifier Errors:** Map JVM verifier errors back to Algol source spans for actionable diagnostics.

## Debug Metadata and Source Mapping

To support effective debugging and runtime error analysis:

- **Emit Line Number Tables:** Generate Jasmin `.line` directives to map bytecode to Algol source lines.
- **Emit Local Variable Tables:** Include variable names and scopes for improved stack traces.
- **Instruction-to-Source Mapping:** Maintain mappings from JVM instructions back to Algol source lines for precise error reporting and debugging.

## Intermediate Representations

A well-designed, typed IR simplifies JVM code emission and enables advanced analysis. Benefits include:

- **Type-Driven Lowering:** Use type information to generate correct JVM instructions (e.g., `istore` vs. `astore`).
- **Simplified Code Generation:** Lower high-level constructs to JVM IR before emitting Jasmin.
- **Easier Analysis:** Enable static analysis and optimization at the IR level.

**Example: Lowering an Algol Assignment**

Algol:
```
x := 42;
```

Typed IR:
```
Assign(Var("x", type=integer), Const(42, type=integer))
```

JVM IR:
```
iconst_42
istore_x
```

Jasmin:
```jasmin
ldc 42
istore_1 ; assuming x is local 1
```

## CLI and Tooling Design

A modern compiler should provide a CLI with commands tailored for both human and automated workflows:

- **check**: Parse, resolve, and type-check source files; emit diagnostics only.
- **build**: Full compilation pipeline; produce .class files.
- **emit-jasmin**: Output Jasmin assembler for inspection or manual assembly.
- **assemble**: Assemble Jasmin files into .class files.
- **run**: Compile and execute a program in one step.
- **emit ast**: Output the parsed AST in a machine-readable format (e.g., JSON).
- **emit jvmir**: Output the JVM IR for inspection or tooling.

**Fast check mode** is critical for rapid iteration, enabling instant feedback for both developers and AI tools.

## Testing Strategy

Testing must ensure correctness, stability, and reproducibility:

- **Golden Tests for Diagnostics:** Compare actual diagnostic JSON output to expected results.
- **Snapshot Tests for Jasmin:** Compare emitted Jasmin files to stored snapshots.
- **End-to-End Runtime Tests:** Compile and run programs, checking output and exit codes.
- **Deterministic Output:** Ensure all tests rely on stable, reproducible outputs for reliable CI and AI-assisted debugging.

## Long-Term Considerations

- **Versioned Diagnostic Schemas:** Maintain backward-compatible, versioned JSON schemas for diagnostics.
- **Stable IR Formats:** Define and document IR formats for long-term stability and tool integration.
- **Language Server Protocol (LSP) Integration:** Design diagnostics and source mapping for future LSP support.
- **Minimal, Predictable Runtime Library:** Keep the runtime library small, stable, and well-documented to minimize surprises and ease AI-assisted reasoning.

---

By following these practices, the Algol-to-JVM compiler will be robust, maintainable, and highly accessible to both human engineers and AI-powered tools, enabling advanced workflows and rapid, reliable development.