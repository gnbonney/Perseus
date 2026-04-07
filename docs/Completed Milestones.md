# Completed Milestones

This document condenses the completed milestone history for Perseus through Milestone 40. It keeps the milestone numbering and the main outcome of each stage without preserving the full implementation checklist for every step.

Active and future work now lives in [Development Roadmap.md](Development%20Roadmap.md).

## MVP Milestones

**Milestone 1 — Hello World.** Established the basic end-to-end pipeline from Perseus source to Jasmin to runnable JVM class output, anchored by `hello.alg` and ordinary console output.

**Milestone 2 — Variables and Assignment.** Added real variables, assignment, arithmetic expressions, and the first real symbol-table-driven two-pass compilation flow needed for nontrivial programs.

**Milestone 3 — `goto` and Labels.** Introduced label declarations and direct `goto` lowering so control-flow-heavy Algol examples could compile structurally.

**Milestone 4 — `if`/`then`.** Added integer variables, relational expressions, and the `TypeInferencer` pass so mixed integer/real expressions and conditional jumps could be lowered correctly.

**Milestone 5 — `for` Loop.** Implemented Algol-style `for ... step ... until ... do` semantics, including re-evaluated bounds and step expressions.

**Milestone 6 — Multiple Statements and `outreal`.** Rounded out statement sequencing, `outreal`, and chained assignment so the early `primer` examples could execute cleanly.

**Milestone 7 — `if`/`then`/`else` and Boolean.** Added Boolean declarations, literals, and branch lowering for `if ... then ... else`.

**Milestone 8 — Integer Arrays.** Added one-dimensional integer arrays with lower-bound adjustment and ordinary indexed load/store behavior.

**Milestone 9 — Nested Blocks and `outinteger`.** Brought in nested blocks, integer procedures, and value-parameter calls, enabling classic procedure-driven examples such as `oneton.alg`.

**Milestone 10 — Sieve of Eratosthenes.** Completed the first substantial classic Algol regression slice with Boolean arrays, `for ... while`, void procedures, and reliable outer-scope array access.

**Milestone 11A — Math Functions.** Implemented the core environmental math procedures through direct JVM lowering to `java.lang.Math` and equivalent support code.

**Milestone 11B — Output Procedures.** Added `outchar`, `outterminator`, and the first channel-aware console output mapping for channels `0` and `1`.

**Milestone 11C — Input Procedures.** Added the first input procedures and later evolved them toward the compiled stdlib path that now owns `ininteger`, `inreal`, `inchar`, and `instring`.

**Milestone 11D — Control and Error Procedures.** Implemented the initial `stop` and `fault` behavior before later exception-oriented refinements.

**Milestone 11E — Environmental Constants.** Added the core predeclared numeric constants such as `maxreal`, `minreal`, `maxint`, and `epsilon`.

**Milestone 11F — Integration and Tests.** Consolidated the early environmental-block work with stronger sample-driven regression coverage.

**Milestone 12 — Call-by-Name.** Implemented thunk-based call-by-name semantics, including Jensen’s Device, caller-visible updates, and the supporting generated thunk classes.

**Milestone 13 — Procedure References and Parameters.** Added procedure variables, typed procedure references, and the first working higher-order procedure machinery.

**Milestone 13.1 — Deferred Typing.** Added the deferred formal-typing slice used by classic Algol procedure declarations with omitted type prefixes.

**Milestone 14 — Procedure Parameters and Real Arrays.** Extended higher-order procedure support and real-array handling enough to cover deeper recursive and numeric sample cases.

**Milestone 15 — Non-Local Scalar Variable Access.** Added the first reliable environment-bridge support for nested procedures reading and updating enclosing scalar state.

**Milestone 16 — Boolean Operators.** Completed the broader Boolean operator surface beyond simple literals and branches.

**Milestone 17 — Real Arrays.** Added real-valued array support parallel to the earlier integer-array machinery.

**Milestone 18 — String Output.** Established first-class string variables, string operations, and string-oriented environmental support such as `concat`, `length`, and `substring`.

**Milestone 19 — `own` Variables.** Implemented persistent `own` storage for locals and arrays via JVM static backing rather than per-activation locals.

**Milestone 20 — Switch Declarations.** Added switch declarations, designational expressions, and switch-driven `goto` lowering.

**Milestone 21 — Nested Procedures with Non-Local Access.** Stabilized nested-procedure access to enclosing variables across realistic recursive and re-entrant patterns, including Man-or-Boy-related cases.

**Milestone 22 — Formal Array Parameters.** Added Algol-style formal array parameters with explicit bounds passing so array procedures preserve caller index ranges.

**Milestone 23 — Multidimensional Arrays.** Added multidimensional declarations, comma-separated subscripts, and row-major lowering with retained bound metadata.

**Milestone 24 — Hardware Representation Completion.** Brought the parser and expression surface closer to the documented representation rules with `div`, exponentiation, implication/equivalence operators, and brace blocks.

**Milestone 25 — Remaining Modified Report Surface Syntax.** Added numeric labels, dummy statements, and named parameter delimiters to close important remaining source-compatibility gaps.

**Milestone 26 — External Procedures.** Introduced external Perseus and external Java static procedure linkage with compile-time validation for missing classes, missing members, and signature mismatches.

**Milestone 27 — Simula-Style Classes and External Classes.** Added the first class/object model, `ref(...)` class references, object creation, dotted member access, and the basis for later Java class interop.

**Milestone 28 — Exceptions and Structured Recovery.** Added `begin ... exception ... end`, `when ... do ...`, JVM `try/catch` lowering, and the first recoverable exception-oriented runtime model.

**Milestone 29 — Dynamic Channels and File I/O.** Moved beyond console-only I/O with dynamic file and string channels, recoverable file errors, and the first broader channel abstraction.

**Milestone 30 — Formatted I/O.** Added working `outformat` and `informat` support, including string-channel and file-channel formatted workflows.

**Milestone 31 — CLI and Compiler UX.** Established the real `perseus` launcher, `-d`, `--jar`, classpath support, ASM post-processing by default, and cleaner user-facing diagnostics.

## Follow-On Completed Milestones

**Milestone 32 — External Procedure Follow-On.** Expanded external procedure interop with better ABI documentation, external array parameters, `--package`, and stronger signature diagnostics.

**Milestone 33 — Class Model Follow-On.** Extended the class model with prefix inheritance, dynamic dispatch, external Java class interop, namespace-based identity, and multi-file library compilation under a shared `namespace`.

**Milestone 34 — Exception Follow-On.** Completed the `when ... as ex do ...` path, common Java exception shorthand, and named Java exception resolution without explicit class declarations.

**Milestone 35 — Compiled Standard Environment.** Moved most of the environmental block into real compiled stdlib units such as `MathEnv`, `Strings`, `TextInput`, `TextOutput`, and `Faults`, with automatic stdlib provisioning and separate stdlib jar packaging.

**Milestone 36 — Java API Interop Follow-On.** Broadened Java interop to include static fields, object-valued bindings, instance fields, overloaded methods and constructors, chained calls, and richer diagnostics.

**Milestone 37 — Dynamic Channels and Formatted I/O Follow-On.** Generalized the channel model across files and string channels, added explicit EOF behavior, and expanded formatted I/O while shifting more logic out of direct compiler hardcoding.

**Milestone 38 — Remaining Java Helper Reduction.** Removed the last major stdio/runtime bridge helpers by pushing channel ownership, formatted I/O logic, and input/output parsing into compiled Perseus stdlib code using ordinary Java interop.

**Milestone 39 — Initial CLI Follow-On.** Landed the core CLI follow-on slice with multi-file compilation, richer library-oriented packaging/classpath behavior, optional JAR output, and support for external inputs from both directories and JAR files. Remaining CLI polish has been deferred into the active development roadmap.

**Milestone 40 — Input Procedures Cleanup.** Closed out the remaining input-procedure follow-up by confirming the compiled stdlib path, tightening the console-input channel model, and aligning the current environmental-block documentation with the implemented behavior.

