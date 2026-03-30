# Post-MVP Roadmap

This document covers the active roadmap after the largely implemented MVP path in [MVP Roadmap.md](MVP%20Roadmap.md). It tracks follow-on milestones and larger future directions for Perseus going forward.

---

## Follow-On Milestones After MVP

The milestones below collect follow-on work that was intentionally deferred while pushing toward the MVP path through Milestone 31. They are still important, but they are no longer mixed into the mainline milestones as scattered notes.

## Milestone 32 - External Procedure Follow-On

**Goal:** Extend the first working external-procedure slice into a richer and better documented interop model.

- [ ] Broader external-library and separate-compilation workflows beyond the initial CLI `-cp` / `--classpath` support
- [x] Replace the older `external algol(...)` wording in docs and remaining design notes with the default `external(...)` Perseus-to-Perseus model
- [x] Add a user-facing way to choose the generated JVM package/class path for separately compiled Perseus units instead of always assuming `gnb/perseus/programs`
- [x] External Perseus array parameters as a documented ABI case
- [x] Additional compile-time external signature validation and diagnostics
- [ ] Decide whether and when to support call-by-name across external Perseus boundaries for separately compiled translations of historical Algol libraries
- [x] Keep procedure-valued external parameters out of scope unless Perseus later adopts a stable external ABI for procedure references

**Implementation notes:**
- `external(...)` is now the default Perseus-to-Perseus syntax, while `external algol(...)` remains accepted as a compatibility spelling
- The CLI now supports `--package` so separately compiled Perseus units can choose stable JVM package/class names intentionally
- External one-dimensional array parameters now use the documented `array + lower + upper` calling convention in both direct compiler flow and CLI `-cp` workflows
- External compile-time diagnostics now distinguish missing classes, missing methods, return-type mismatches, parameter-signature mismatches, and array ABI mismatches
- Procedure-valued external parameters are intentionally out of scope rather than an open implementation gap
- External call-by-name remains a follow-on item because translated historical Algol libraries may need it, but it still depends on a documented stable thunk ABI

## Milestone 33 - Class Model Follow-On

**Goal:** Extend the basic Simula-inspired class model beyond the first working slice.

- [ ] Stable JVM naming and separate-compilation identity for reusable Perseus classes
- [x] Simula-style prefix inheritance and its initial construction rules
- [x] Dynamic dispatch for overridden procedures in the initial prefix model
- [x] External JVM class declarations and initial object interop
- [ ] Decide whether Perseus classes may extend external Java classes, or whether external Java classes remain reference/interoperability targets only
- [ ] Richer member syntax such as explicit field selection versus zero-argument procedure calls
- [ ] Exception-object/member access integration once richer exception binding exists
- [ ] Add tests for class-based Java interop scenarios

**Implementation notes:**
- The long-term inheritance direction is now prefix-style in the Simula tradition rather than Java-style subclass syntax
- External Java classes should be treated as a separate interop design problem, not as ordinary Perseus prefixes
- Prefix declarations now compile and run in the form `Base class Derived(...)`, with JVM subclass emission and inherited method availability
- Dynamic dispatch now works for overridden procedures when a derived object is held through a base-class `ref(...)`
- `external java class ...` now supports a first working slice of object creation and instance method calls for imported JVM classes
- The current generated JVM naming scheme is acceptable for the MVP class slice, but Milestone 33 should move toward stable class identities suitable for reusable libraries and separate compilation

## Milestone 34 - Exception Follow-On

**Goal:** Finish the parts of the exception design that were deferred after the first working slice.

- [ ] Decide which existing runtime failures should remain fail-fast and which should become catchable exceptions
- [ ] Give `when ... as ex do ...` real semantic/runtime support by binding a catch variable inside the handler
- [ ] Add initial exception-inspection helpers such as `exceptionmessage(ex)` and `printexception(ex)`
- [ ] Decide later whether richer exception member syntax should use helpers only or eventually support object-style access such as `ex.message`
- [ ] Decide how much of the current JVM exception mapping should be replaced with a dedicated Perseus runtime exception hierarchy

## Milestone 35 - Dynamic Channels and Formatted I/O Follow-On

**Goal:** Generalize the first working dynamic I/O slice into a broader runtime channel model.

- [ ] Generalize the runtime model beyond the current constant-channel / literal-path slice
- [ ] Extend more input/output procedures to use dynamic stream dispatch instead of only `System.out` / `System.err`
- [ ] Add explicit `EndOfFile` behavior and decide where `fault(...)` remains the compatibility fallback
- [ ] Extend formatted I/O beyond the current `I`, `F`, and `A` subset
- [ ] Add more file/string-channel regression programs that combine unformatted and formatted I/O

## Milestone 36 - CLI Follow-On

**Goal:** Extend the working CLI into a broader tool suitable for multi-file and interop-heavy workflows.

- [ ] Broader multi-file compilation workflows
- [ ] Optional installer/distribution packaging beyond `installDist`
- [ ] Further exit-code and machine-facing CLI refinements
- [ ] Additional commands such as `check` or `emit-jasmin` if they remain desirable after the MVP

## Milestone 37 - Label and Switch Parameters / Designational Exits

**Status:** Not currently planned.

**Priority:** Optional standards-completeness milestone that may never be implemented if Perseus continues to prefer structured exceptions and other more modern control-flow mechanisms.

**Goal:** Allow labels and switches to be passed as parameters and used for procedure-mediated exits, matching real Algol 60 designational-expression semantics more closely.

- [ ] Grammar: `label` and `switch` formal parameter specifiers
- [ ] Grammar: `Boolean procedure` declarations and `Boolean procedure` formal parameter specifiers
- [ ] Codegen/runtime: Boolean-valued procedure calls and Boolean procedure references/parameters
- [ ] Procedure calls: pass labels and switches as actual parameters
- [ ] Codegen: support designational exits through passed labels/switches where legal
- [ ] Enforce or document the goto-scope restrictions that still apply

Possible JVM strategy for passed labels: lower non-local label exits to tagged exceptions (or an equivalent non-local escape mechanism) and catch them in the block/procedure activation that owns the real target labels. This would avoid requiring impossible cross-method JVM jumps while still giving a plausible implementation path for Algol-style designational exits.

Possible JVM strategy for passed switches: lower a switch parameter to an indexed collection of label-exit descriptors (or thunks that resolve to them), reusing the same non-local escape machinery as passed labels when `goto sw[i]` selects a non-local target.

## Milestone 38 - Compiled Standard Environment

**Goal:** Move more of the environmental block out of compiler hardcoding and into a real always-available compiled standard environment.

- [ ] Define the shape of the always-available standard environment / prelude
- [ ] Decide which environmental identifiers remain true compiler intrinsics
- [ ] Move selected environmental procedures into compiled Perseus source where practical
- [ ] Use supporting Perseus classes where implementation-heavy features need them
- [ ] Ensure the standard environment is automatically available to all Perseus programs without explicit imports
- [ ] Add tests showing that migrated environmental procedures work through the compiled standard environment rather than only through compiler-recognized names

## Milestone 39 - Input Procedures Cleanup

**Goal:** Revisit the input-side environmental procedures that were marked complete during the MVP path and confirm that they really work end to end in realistic programs.

- [ ] Add a real regression around [`input_procedures.alg`](../test/algol/io/input_procedures.alg)
- [ ] Verify and, if necessary, fix ininteger(channel, var) runtime behavior
- [ ] Verify and, if necessary, fix inreal(channel, var) runtime behavior
- [ ] Verify and, if necessary, fix inchar(channel, str, var) runtime behavior
- [ ] Decide whether the current scanner/channel model for input procedures matches the intended environmental-block semantics closely enough
- [ ] Update the MVP and environmental-block documentation once the behavior is confirmed

## Milestone 40 - Recursive Thunk and Procedure-Parameter Cleanup

**Goal:** Revisit recursive call-by-name and passed-procedure edge cases that appear to go beyond the currently validated thunk machinery.

- [ ] Add a real regression around [`thunk_recursion.alg`](../test/algol/misc/thunk_recursion.alg)
- [ ] Decide whether `thunk_recursion.alg` represents intended Algol semantics exactly as written, and tighten the sample if needed without removing the recursive thunk edge case
- [ ] Fix the recursive thunk / passed-procedure lowering so the generated thunk getter and return shape are verifier-correct
- [ ] Verify that the fix does not regress existing call-by-name coverage such as `jen.alg`, `nested_digits.alg`, and `manboy.alg`
- [ ] Add focused non-regression tests for recursive procedure parameters and re-entrant thunk refresh behavior

## Future Direction Milestones

These milestones are not just deferred implementation cleanup. They represent larger possible directions for Perseus after the MVP and the most important follow-on work are in place.

## Milestone 41 - Lambda Notation

**Goal:** Add anonymous procedure expressions as a higher-level extension on top of the procedure-value machinery (see [Perseus Language Design.md](Perseus%20Language%20Design.md)).

- [ ] Syntax and parsing for lambda-style procedure literals
- [ ] Lowering strategy onto existing procedure-reference infrastructure
- [ ] Tests for higher-order procedure use cases

## Milestone 42 - Actors

**Goal:** Explore actors as a distinctive future direction for Perseus once the MVP and key follow-on milestones are in place (see [Actors Design Spec.md](Actors%20Design%20Spec.md)).

- [ ] Decide whether actors should become a primary language model, a peer to classes, or a higher-level library/runtime abstraction
- [ ] Define a small first slice (`actor`, references, creation, send, mailbox/handler basics)
- [ ] Decide how actors should relate to existing classes, exceptions, and external Java interop
- [ ] Add focused actor sample programs and a phased implementation plan

## Toward a General-Purpose Product

Beyond the follow-on milestones above, a more polished general-purpose Perseus product would likely also need:

- Easier installation and distribution beyond `installDist`, especially for Windows and macOS.
- A clearer standard-library story beyond the current environmental procedures and extensions.
- More user-facing documentation such as tutorials, getting-started material, interop guides, and worked examples.
- Release/versioning discipline, including compatibility expectations and clearer release notes.
- Broader user-workflow testing, including multi-file builds, library use, packaging, and install-level smoke tests.
- Better late-phase diagnostics coverage beyond the current parser/type-focused structured diagnostics.
- Clearer source and ABI stability policies as the language continues to evolve.
- Continued attention to performance and code-quality issues once the language surface stabilizes.

---

## Infrastructure TODOs (any milestone)

- [ ] Replace deprecated `ANTLRInputStream` with `CharStreams.fromReader()`
- [ ] Write `.j` Jasmin files to a configurable output directory (not hardcoded)
- [x] Add integration test helper to invoke Jasmin and run the resulting `.class`
- [ ] Decide on output directory structure for compiled classes

---

# Tool-Friendly Compiler Design: Implementation Priorities

To ensure long-term maintainability and enable advanced tooling workflows, the following improvements are tracked below. Items marked `[x]` have been addressed; remaining items can be added incrementally.

## Completed
- [x] Modular multi-pass architecture: `SymbolTableBuilder` -> `TypeInferencer` -> `CodeGenerator` -- clear separation of concerns, implemented from Milestone 2 onward.
- [x] Deterministic Jasmin output: canonical label naming, stable method/field ordering (sufficient for current milestones).

## Still Relevant (Can Be Added Any Time)
- Extend the current structured diagnostics work beyond parse/type inference: add more phases, collect multiple independent errors per run where practical, and keep stable file/line/column/code reporting.
- Snapshot/golden tests: verify Jasmin output and diagnostics are stable and deterministic across compiler changes.
- Full structured JSON diagnostics: machine-readable output, fix-it suggestions, deterministic ordering.
- Consistent debug metadata: line number tables, local variable tables, source-to-bytecode mapping.

## Longer-Term Tooling Ideas
- Improve JVM verifier feedback so bytecode verification failures can be explained more clearly in terms of Perseus source and compiler phases.
- Versioned diagnostic schemas, stable IR formats, and LSP (Language Server Protocol) integration.


