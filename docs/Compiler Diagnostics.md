# Compiler Diagnostics

This document records the current compiler-diagnostics implementation in Perseus.

It complements:

- [Architecture.md](Architecture.md), which describes the current compiler structure and stage boundaries
- [Post-MVP Roadmap.md](Post-MVP%20Roadmap.md), which tracks unfinished diagnostics and tooling work

The focus here is practical: what diagnostics exist today, how they are represented, and what gaps remain.

## Current Model

Perseus now has a small structured diagnostics layer in the compiler front end.

- `CompilerDiagnostic` is the core record. It carries a stable code, severity, file, line, column, and message.
- `CompilationFailedException` wraps one or more diagnostics for user-facing compilation failures.
- `DiagnosticException` is a pass-internal exception used when a semantic phase wants to abort with a single structured diagnostic.

`CompilerDiagnostic.Severity` currently supports `ERROR` and `WARNING`, but the compiler currently emits errors only.

## Current Sources of Diagnostics

### Parse and Lexing Diagnostics

`DescriptiveErrorListener` collects lexer/parser errors and converts them into `CompilerDiagnostic` values.

Current behavior:

- Diagnostics are attached to the input file path used for compilation.
- Line and column are reported using ANTLR token coordinates.
- The compilation pipeline stops before symbol-table building if any parse diagnostics were collected.
- Multiple syntax errors can be collected during parsing, depending on ANTLR recovery.

### Type-Inference Diagnostics

`TypeInferencer` now throws `DiagnosticException` for a small set of semantic/type failures.

Current behavior:

- These diagnostics include a stable code and source location based on the offending parse-tree token.
- The pipeline currently stops on the first semantic diagnostic raised during type inference.
- This is structured and user-facing, but not yet full multi-error semantic reporting.

### CLI Reporting

`PerseusCLI` now distinguishes normal compilation failures from internal compiler failures.

Current behavior:

- `CompilationFailedException` diagnostics are printed cleanly to `stderr`, one per line.
- Internal exceptions still print a stack trace, which is useful during compiler development.

The current formatted output is:

```text
ERROR PERS1001 path/to/file.alg:1:9 mismatched input 'end' expecting ...
```

## Current Diagnostic Codes

The currently implemented codes are:

| Code | Phase | Meaning |
| --- | --- | --- |
| `PERS1001` | parse/lex | syntax error reported by ANTLR |
| `PERS2001` | type inference | undeclared variable |
| `PERS2002` | type inference | undeclared array |
| `PERS2003` | type inference | `&` used with non-boolean operands |
| `PERS2004` | type inference | `or` used with non-boolean operands |
| `PERS2005` | type inference | `not` used with a non-boolean operand |
| `PERS3001` | external linkage | external target class could not be found or inspected |
| `PERS3002` | external linkage | external method could not be matched to the declared procedure |
| `PERS3003` | external linkage | external declaration uses a signature outside the currently supported ABI |

The current numbering convention is intentionally simple:

- `PERS1xxx` for parse/front-end syntax diagnostics
- `PERS2xxx` for semantic/type diagnostics
- `PERS3xxx` for external-procedure linkage diagnostics

Additional ranges can be introduced later for code generation, assembly, verifier, or runtime-facing diagnostics.

## External Procedure Diagnostics

Perseus now emits a first structured diagnostics layer for `external(...)` and `external java ...` declarations.

Current behavior:

- External linkage is validated during compilation rather than left entirely to JVM runtime linkage errors.
- Missing target classes and unreadable target classes report `PERS3001`.
- Method-shape mismatches report `PERS3002`.
- Declarations outside the currently supported external ABI report `PERS3003`.

The current messages are still fairly compact. The next refinement should make them more specific about:

- expected versus actual signature shape
- static versus non-static mismatch
- scalar versus array-plus-bounds ABI mismatch
- the exact target class and method descriptor Perseus attempted to resolve

## Current Compiler Behavior

Today the front end behaves like this:

1. Parse the source and collect any syntax diagnostics.
2. If parsing produced diagnostics, stop and report them.
3. Build symbols and infer expression types.
4. If type inference raises a diagnostic, stop and report it.
5. Continue into code generation only when earlier stages succeeded.

This is already a meaningful improvement over raw stack traces or ad hoc `stderr` prints, but it is not yet a complete end-to-end diagnostics system.

## Tests

The current implementation is covered by focused regression tests in `PerseusCompilerTest`:

- `syntax_error_diagnostic_test()`
- `semantic_error_diagnostic_test()`

These tests assert that compilation failures produce structured diagnostics with the expected code and source-location information.

## Known Gaps

Important limitations remain:

- No end-span or range information yet; diagnostics currently report a single line/column position.
- No fix-it hints yet.
- No JSON or other machine-readable CLI output format yet.
- No warning diagnostics are currently emitted.
- Semantic diagnostics currently stop at the first error instead of collecting multiple independent issues.
- Code generation still contains several `; ERROR: ...` fallback comments instead of producing structured diagnostics.
- Jasmin assembly failures and JVM verifier failures are not yet mapped into stable Perseus diagnostic codes.
- The CLI currently distinguishes success vs failure, but it does not yet define a richer stable exit-code scheme.

## Relationship to Future Work

The current diagnostics implementation should be treated as the first structured layer, not the final design.

The next likely improvements are:

- broaden structured diagnostics beyond type inference
- replace code-generation error comments with real diagnostics
- add deterministic ordering and richer spans
- support multiple semantic diagnostics per run
- add a machine-readable diagnostics format

Those goals are still tracked in [Post-MVP Roadmap.md](Post-MVP%20Roadmap.md) and fit the broader compiler structure described in [Architecture.md](Architecture.md).
