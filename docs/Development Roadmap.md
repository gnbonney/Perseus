# Development Roadmap

This document tracks the active and longer-term roadmap for Perseus after the completed milestone history summarized in [Completed Milestones.md](Completed%20Milestones.md).

The near-term direction should reflect Perseus's center of gravity more clearly. That means prioritizing features that improve readable executable mathematics on the JVM, while still leaving room for the language's experimental edge. For the mathematical side of that vision, see [Mathematical Computing Design Spec.md](Mathematical%20Computing%20Design%20Spec.md).

## Active Milestones

The current intended order is:

1. Milestone 41: modern looping forms
2. Milestone 41.1: classic loop-surface refinement
3. Milestone 42: lambda notation
4. Milestone 43: collections, iterators, and Java-container interop
5. Milestone 44: complex numbers and mathematical types
6. Milestone 45: mathematical arrays and numerically oriented procedure features
7. Milestone 46: numeric precision and extended arithmetic
8. Milestone 47: numerical standard library foundations
9. Milestone 48: actors
10. Milestone 49: CLI polish and tooling

## Milestone 41 - Looping Extensions

**Goal:** Extend Perseus with more modern looping forms while preserving the original Algol `for` statement for compatibility (see [Looping and Collections Design Spec.md](Looping%20and%20Collections%20Design%20Spec.md)).

- [x] Add `for ... in ... do` iteration over existing array forms
- [x] Add a general `loop ... end` construct
- [x] Add `break` and `continue`
- [x] Define scope and evaluation rules for `for ... in ... do` iteration variables, including the rule that the variable must already be declared with a compatible type, that assignments inside the loop body do not alter the traversal sequence, and that the traversed array or collection expression is evaluated once at loop entry
- [x] Add focused sample programs and regression tests for the new loop forms

**Implementation notes:**
- The first Milestone 41 slice landed `loop begin ... end` with `break` and `continue`.
- A second Milestone 41 slice landed `for ... in ... do` over existing arrays using already-declared compatible variables.
- Historical `end loop` spellings continue to parse as `end` comments for compatibility, but plain `end` remains the structural closer.
- Numeric counting remains the job of the traditional Algol `for i := ... step ... until ... do` form rather than a new symbolic range syntax.
- `for ... in ... do` should continue the Algol tradition that loop variables are ordinary declared variables, not hidden declarations introduced by the loop syntax.
- As with the classic Algol `for`, the loop controls the traversal sequence even if user code assigns to the iteration variable inside the body.
- The array or collection being traversed should be evaluated once before iteration begins, not re-evaluated on each pass.

## Milestone 41.1 - Classic Loop-Surface Refinement

**Goal:** Replace the dedicated `loop ... end` surface with clearer standalone `while ... do` and `repeat ... until` forms while keeping `break` and `continue` across the structured loop family (see [Looping and Collections Design Spec.md](Looping%20and%20Collections%20Design%20Spec.md)).

- [ ] Add standalone `while ... do`
- [ ] Add standalone `repeat ... until`
- [ ] Keep `break` and `continue` working in both forms
- [ ] Decide whether `loop ... end` should be removed entirely or retained temporarily as a compatibility alias
- [ ] Update the language docs and loop examples to present `while` / `repeat` as the preferred structured loop surface
- [ ] Add focused sample programs and regression tests for the new forms

**Implementation notes:**
- The motivation here is readability and a cleaner fit with the broader Algol tradition, not JVM performance.
- `while true do ...` with `break` and `continue` should cover the ordinary open-ended repetition cases that previously motivated `loop ... end`.
- The original Algol `for` still remains the expressive heritage form, while `while ... do` and `repeat ... until` become the ordinary structured pre-test and post-test loops.

## Milestone 42 - Lambda Notation

**Goal:** Add anonymous procedure expressions as a higher-level extension on top of the procedure-value machinery (see [Perseus Language Design.md](Perseus%20Language%20Design.md)).

- [ ] Syntax and parsing for lambda-style procedure literals
- [ ] Lowering strategy onto existing procedure-reference infrastructure
- [ ] Tests for higher-order procedure use cases, including numerically oriented examples

## Milestone 43 - Collections, Iterators, and Java Container Interop

**Goal:** Add collection and iterator support in a way that helps both Perseus's own `for ... in ... do` direction and practical interop with Java-hosted iterable/container APIs (see [Looping and Collections Design Spec.md](Looping%20and%20Collections%20Design%20Spec.md)).

- [ ] Add collection types such as `vector`, `map`, and `set`
- [ ] Add collection literals and basic collection operations
- [ ] Extend `for ... in ... do` from arrays to collection and iterator-protocol-based iteration
- [ ] Define an iterator protocol that works with `for ... in ... do`
- [ ] Decide how Java `Iterable`-style interop should fit into the Perseus iterator model
- [ ] Decide how array-oriented iteration should relate to the same iterator protocol
- [ ] Decide how collection libraries fit into the standard environment / standard library story
- [ ] Add sample programs and regression tests for collection use cases
- [ ] Decide how iterator pipelines such as `map` and `filter` should depend on Milestone 42 lambda notation

**Implementation notes:**
- This milestone should not be treated as "collections for their own sake."
- It is also the bridge between Perseus's emerging modern loop forms and its JVM interop story for iterables and container-like APIs.
- If mathematical arrays later grow richer traversal or section semantics, that work should align with this iterator model rather than bypass it completely.

## Milestone 44 - Complex Numbers and Mathematical Types

**Goal:** Add the first explicitly mathematics-oriented type-system extensions that would make Perseus more natural for scientific and engineering work (see [Mathematical Computing Design Spec.md](Mathematical%20Computing%20Design%20Spec.md)).

- [ ] Add a source-level `complex` type
- [ ] Define complex literals or constructors in a form that fits Perseus cleanly
- [ ] Support complex arithmetic through the ordinary operator surface where appropriate
- [ ] Support arrays of complex values
- [ ] Define how complex values should interoperate with Java and the standard library
- [ ] Add focused sample programs and regression tests for scientific/numerical use cases

## Milestone 45 - Mathematical Arrays and Numerical Procedures

**Goal:** Make numerical code more expressive by improving array-oriented notation and by adding procedure features that fit mathematical programming well (see [Mathematical Computing Design Spec.md](Mathematical%20Computing%20Design%20Spec.md)).

- [ ] Add array sections or slices in a form consistent with Perseus's bounded-array model
- [ ] Add carefully chosen whole-array expressions where they clearly improve readability
- [ ] Decide whether vector/matrix-oriented operations belong partly in syntax or entirely in the standard library
- [ ] Add `pure` procedures or an equivalent purity marker for mathematically side-effect-free routines
- [ ] Add `elemental` procedures or an equivalent mechanism for array-lifted scalar procedures
- [ ] Consider lightweight tuple or multiple-return-value support for numerical procedures that naturally return values plus status or error information
- [ ] Add focused numerical sample programs and regression tests

## Milestone 46 - Numeric Precision and Extended Arithmetic

**Goal:** Add a small, explicit extended numeric model for mathematical and scientific work without turning Perseus into a large precision-taxonomy language (see [Mathematical Computing Design Spec.md](Mathematical%20Computing%20Design%20Spec.md)).

- [ ] Decide whether the first extension should be `bigreal`, `decimal`, `bigint`, or a narrower initial subset
- [ ] Define syntax, constructors, or literal rules for the added numeric types
- [ ] Define mixed-type arithmetic and coercion rules
- [ ] Define how the added numeric types interact with arrays and procedures
- [ ] Define the Java interop model for the new numeric types
- [ ] Add focused sample programs and regression tests for precision-sensitive mathematical cases

**Implementation notes:**
- Perseus currently keeps a deliberately simple numeric model, with `real` corresponding to JVM `double`.
- The main design challenge is not whether more precision can exist at all, but how to add it without making the language significantly less readable.
- A small explicit model is preferred over a large Fortran-style family of precision kinds unless real use cases later justify more complexity.

## Milestone 47 - Numerical Standard Library Foundations

**Goal:** Build out the first substantial mathematical standard-library layer beyond the minimal environmental procedures (see [Mathematical Computing Design Spec.md](Mathematical%20Computing%20Design%20Spec.md)).

- [ ] Add foundational linear algebra support
- [ ] Add interpolation and approximation routines
- [ ] Add quadrature and numerical integration support
- [ ] Add optimization and least-squares building blocks
- [ ] Add special-function support where it fits naturally
- [ ] Decide how much of this layer should be implemented in Perseus source versus direct Java-backed interop
- [ ] Add focused sample programs and regression tests based on real numerical-methods examples

**Implementation notes:**
- This milestone is about the standard library, not just the language core.
- Historical libraries such as NUMAL are relevant inspiration here, even if Perseus does not try to reproduce them wholesale.
- The strongest goal is to make Perseus a better language for expressing and packaging real numerical methods, not merely to accumulate standalone helper functions.

## Milestone 48 - Actors

**Goal:** Explore actors as a distinctive future direction for Perseus once the MVP and key follow-on milestones are in place (see [Actors Design Spec.md](Actors%20Design%20Spec.md)).

- [ ] Decide whether actors should become a primary language model, a peer to classes, or a higher-level library/runtime abstraction
- [ ] Define a small first slice (`actor`, references, creation, send, mailbox/handler basics)
- [ ] Decide how actors should relate to existing classes, exceptions, and external Java interop
- [ ] Add focused actor sample programs and a phased implementation plan

**Implementation notes:**
- The first parser surface would likely include `actor`, `on message`, `send ... the message`, `become`, and `ref(ActorName)`.
- Type checking would need to cover actor references, message tags, actor/class interaction, and Java interop boundaries.
- A plausible first lowering strategy is actors to a `Behavior` model plus virtual threads.
- The runtime could begin either as a small dedicated actor-support jar or as generated support code if avoiding runtime dependencies remains a priority.
- Later refinements could add `replyto` syntax and pattern-matching sugar once the base actor model is settled.

## Milestone 49 - CLI Polish and Tooling

**Goal:** Finish the lower-priority CLI and productization work that remains after the main CLI and library-workflow milestones.

- [ ] Define clearer output conventions for reusable separately compiled Perseus libraries
- [ ] Optional installer/distribution packaging beyond `installDist`
- [ ] Further exit-code and machine-facing CLI refinements
- [ ] Additional commands such as `check` or `emit-jasmin` if they remain desirable after the MVP

**Implementation notes:**
- The already-landed CLI slice supports multi-file compilation, `--package`, optional `--jar` packaging, and `-cp` / `--classpath` workflows for separately compiled Perseus and Java dependencies.
- The remaining work is mostly productization and tooling polish rather than core language enablement.

## Deferred / Specialized Directions

### External Call-by-Name ABI

**Goal:** Add a documented and stable Perseus-to-Perseus thunk ABI for the narrower case of separately compiled translations of historical Algol libraries.

- [ ] Define the external thunk ABI for call-by-name parameters across separately compiled Perseus boundaries
- [ ] Decide which generated support types must become stable shared runtime contracts
- [ ] Add focused external call-by-name regressions based on translated historical Algol library patterns
- [ ] Keep this as a specialized Perseus-to-Perseus compatibility feature rather than the default public JVM library surface

**Implementation notes:**
- This work is intentionally separated from the first external-procedure milestone because it depends on a stabilized thunk ABI.
- The strongest motivation is historical Algol library translation, not general JVM-facing library design.
- Public JVM-facing reuse should prefer value-oriented Perseus classes and methods where possible, while external call-by-name remains a narrower compatibility feature.

### Label and Switch Parameters / Designational Exits

**Priority:** Optional standards-completeness feature that may never be implemented if Perseus continues to prefer structured exceptions and other modern control-flow mechanisms.

**Goal:** Allow labels and switches to be passed as parameters and used for procedure-mediated exits, matching real Algol 60 designational-expression semantics more closely.

- [ ] Grammar: `label` and `switch` formal parameter specifiers
- [ ] Grammar: `Boolean procedure` declarations and `Boolean procedure` formal parameter specifiers
- [ ] Codegen/runtime: Boolean-valued procedure calls and Boolean procedure references/parameters
- [ ] Procedure calls: pass labels and switches as actual parameters
- [ ] Codegen: support designational exits through passed labels/switches where legal
- [ ] Enforce or document the goto-scope restrictions that still apply

Possible JVM strategy for passed labels: lower non-local label exits to tagged exceptions or an equivalent escape mechanism and catch them in the activation that owns the real target labels.

Possible JVM strategy for passed switches: lower a switch parameter to an indexed collection of label-exit descriptors or thunks that resolve to them, reusing the same non-local escape machinery as passed labels.

## Broader Product Direction

Beyond the numbered milestones above, a more polished general-purpose Perseus product would likely also need:

- Easier installation and distribution beyond `installDist`, especially for Windows and macOS
- A clearer standard-library story beyond the current environmental procedures and extensions
- More user-facing documentation such as tutorials, getting-started material, interop guides, and worked examples
- Release/versioning discipline, including compatibility expectations and clearer release notes
- Broader user-workflow testing, including multi-file builds, library use, packaging, and install-level smoke tests
- Better late-phase diagnostics coverage beyond the current parser/type-focused structured diagnostics
- Clearer source and ABI stability policies as the language continues to evolve
- Continued attention to performance and code-quality issues once the language surface stabilizes

## Infrastructure Backlog

- [ ] Replace deprecated `ANTLRInputStream` with `CharStreams.fromReader()`
- [ ] Write `.j` Jasmin files to a configurable output directory rather than a hardcoded location
- [ ] Decide on output directory structure for compiled classes

## Tool-Friendly Compiler Priorities

The following cross-cutting compiler/tooling improvements remain desirable as Perseus matures:

- Extend structured diagnostics beyond parse/type inference and collect multiple independent errors per run where practical
- Snapshot or golden tests for stable Jasmin output and diagnostics
- Full structured JSON diagnostics with deterministic ordering and fix-it support where appropriate
- Consistent debug metadata such as line number tables, local variable tables, and source-to-bytecode mapping
- Improved JVM verifier feedback that can be explained in terms of Perseus source and compiler phases
- Versioned diagnostic schemas, stable IR formats, and eventual LSP integration
