# Development Roadmap

This document tracks the active and longer-term roadmap for Perseus after the completed milestone history summarized in [Completed Milestones.md](Completed%20Milestones.md).

The near-term direction should reflect Perseus's center of gravity more clearly. That means prioritizing features that improve readable executable mathematics on the JVM, while still leaving room for the language's experimental edge. For the mathematical side of that vision, see [Mathematical Computing Design Spec.md](Mathematical%20Computing%20Design%20Spec.md).

## Active Milestones

The current intended order is:

1. Milestone 41: modern looping forms
2. Milestone 42: anonymous procedures
3. Milestone 43: collections, iterators, and Java-container interop
4. Milestone 44: generics
5. Milestone 45: collection classes, iterators, and library ownership
6. Milestone 45.5: structured data and JSON
7. Milestone 46: complex numbers and mathematical types
8. Milestone 47: mathematical arrays and numerically oriented procedure features
9. Milestone 48: numeric precision and extended arithmetic
10. Milestone 49: numerical standard library foundations
11. Milestone 50: actors
12. Milestone 51: CLI polish and tooling

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

**Goal:** Convert the current `loop ... end` surface into clearer standalone `while ... do` and `repeat ... until` forms while keeping `break` and `continue` across the structured loop family (see [Looping and Collections Design Spec.md](Looping%20and%20Collections%20Design%20Spec.md)).

- [x] Convert `loop ... end` into standalone `while ... do`
- [x] Add standalone `repeat ... until`
- [x] Keep `break` and `continue` working in both forms
- [x] Remove `loop ... end`
- [x] Update the language docs and loop examples to present `while` / `repeat` as the preferred structured loop surface
- [x] Convert the current `loop` regressions to `while true do` regressions and add focused coverage for `repeat ... until`

**Implementation notes:**
- The motivation here is readability and a cleaner fit with the broader Algol tradition, not JVM performance.
- Perseus already has Algol's `for i := expr while cond do ...` form inside the classic `for` statement, but a direct `while ... do` loop is still worth adding because it is simpler to read, easier to teach, and easier for the compiler to lower into a tight structured loop shape.
- `while true do ...` with `break` and `continue` now covers the ordinary open-ended repetition cases that previously motivated `loop ... end`.
- The original Algol `for` still remains the expressive heritage form, while `while ... do` and `repeat ... until` are now the ordinary structured pre-test and post-test loops.
- `loop ... end` has been removed rather than kept as a parallel long-term loop form.

## Milestone 42 - Anonymous Procedures

**Goal:** Add anonymous procedures as first-class inline values using the `proc`-based design in [Anonymous Procedures Proposal.md](Anonymous%20Procedures%20Proposal.md).

- [x] Add `proc` as the anonymous-procedure introducer in expression position
- [x] Support the first-slice `proc (parameter-list) result-type : body` surface for expression-bodied anonymous procedures
- [x] Support expression-bodied anonymous procedures
- [x] Implement `void` anonymous procedures using explicit `proc (...) void : ...` syntax
- [x] Implement `begin ... end` multi-statement anonymous-procedure bodies
- [x] Support assignment of anonymous procedures to procedure-typed variables
- [x] Support passing anonymous procedures to procedure parameters
- [x] Support returning anonymous procedures from procedures where the surrounding procedure-value machinery already permits it
- [x] Implement closure capture for anonymous procedures so they can refer to enclosing locals, parameters, and procedure names using the capture model described in the proposal
- [x] Extend semantic analysis so anonymous procedures are checked against existing procedure-value and procedure-parameter types
- [x] Lower anonymous procedures onto the existing generated procedure-reference machinery rather than inventing a separate runtime model
- [x] Document the current first-slice restrictions around closure capture, procedure-variable rebinding, block bodies, and other procedure features that do not fit cleanly yet
- [x] Add focused regressions for:
- [x] simple expression-bodied anonymous procedures
- [x] assignment to procedure variables
- [x] passing anonymous procedures as arguments
- [x] returning anonymous procedures through procedure-valued results
- [x] captured outer-variable cases
- [x] higher-order numerical examples

**Implementation notes:**
- This milestone should follow the dedicated anonymous-procedures proposal rather than the older historical lambda discussion in the general language-design document.
- `proc` is currently the preferred spelling because it fits Perseus's Algol-descended style better than `lambda`, `fn`, or arrow syntax.
- The first slice now supports typed, expression-bodied `proc` forms that can be passed to existing procedure parameters without introducing a separate runtime model.
- Anonymous procedures can now also be assigned to existing bindable procedure names and then invoked through the current procedure-variable machinery.
- Anonymous procedures can now also be returned through procedure-valued procedure results and then passed onward through the existing higher-order procedure machinery.
- Expression-bodied anonymous procedures can now capture enclosing locals, parameters, and nested procedure names through the same general closure/environment model already used for nested procedures.
- Milestone 42 is now complete, including numerically meaningful higher-order examples for summation and array transformation.
- The anonymous-procedure surface remains fully explicit: `proc (parameter-list) result-type : body`, with no shorthand omission of parentheses or result type in the initial design.
- The first slice should continue to reuse the compiler's existing procedure-value and closure machinery as much as possible.
- Closure capture should stay aligned with existing nested-procedure and procedure-reference behavior rather than introducing a separate scope/runtime model for anonymous procedures.
- Public external ABIs should stay out of scope for the initial anonymous-procedure implementation.

## Milestone 43 - Collections, Iterators, and Java Container Interop

**Goal:** Add collection and iterator support in a way that helps both Perseus's own `for ... in ... do` direction and practical interop with Java-hosted iterable/container APIs (see [Looping and Collections Design Spec.md](Looping%20and%20Collections%20Design%20Spec.md)).

- [x] Introduce `vector` as the first Java-backed dynamic sequence collection type
- [x] Add `vector` construction, indexing, length/size access, and append-style growth operations on top of a Java runtime collection
- [x] Add straightforward conversion between Perseus `vector` values and external Java collection values at interop boundaries
- [x] Extend `for ... in ... do` from direct `vector` support to Java-backed collections and external Java `Iterable` values
- [x] Implement Java `Iterable`-style interop through the same broad `for ... in ... do` iteration model
- [x] Add `vector` literals
- [x] Add `map(...)` literals
- [x] Add `set` literals
- [x] Add basic collection operations for `vector`, `map`, and `set`
- [x] Add `map` as a later Java-backed collection slice
- [x] Add `set` as a later Java-backed collection slice
- [x] Add sample programs and regression tests for collection use cases

**Implementation notes:**
- This milestone should not be treated as "collections for their own sake."
- It is also the bridge between Perseus's emerging modern loop forms and its JVM interop story for iterables and container-like APIs.
- If mathematical arrays later grow richer traversal or section semantics, that work should align with this iterator model rather than bypass it completely.
- The current design direction already assumes one iterator model shared across arrays first, then collections, then Java-hosted iterable/container interop.
- Collection literals should use `{...}` for sets and `map(...)` for maps, rather than a more symbolic `#{...}` set form.
- A first implementation slice has already landed for Java-backed `vector` declarations with automatic empty construction, zero-based indexing, `append`, `length(...)`, `size`, `size()`, and direct `for ... in ... do` iteration.
- The current interop slice treats Perseus `vector` values as `java.util.List` at JVM procedure boundaries while still constructing `java.util.ArrayList` concretely for ordinary Perseus-side storage.
- `for ... in ... do` now also works over external Java objects that implement `java.lang.Iterable`, using the same already-declared loop-variable rules as arrays and vectors.
- Non-empty `vector` literals such as `[1, 2, 3]` now work as the first collection-literal slice, with homogeneous element typing and mixed `integer`/`real` inference to `vector real`.
- The basic Java-backed `vector` surface now also includes `insert`, `remove`, `contains`, and `clear` in addition to the original append/index/size operations.
- A first Java-backed `map` slice now also exists with declarations, keyed lookup and assignment, `contains`, `remove`, `clear`, and `size`/`length(...)`, backed concretely by `java.util.LinkedHashMap`.
- The current map slice also exposes `keys()` and `values()` views so `for ... in ... do` can traverse Java-backed map keys and values through the shared iterable lowering.
- A first Java-backed `set` slice now also exists with declarations, deduplicating `insert`, `contains`, `remove`, `clear`, `size`/`length(...)`, and direct `for ... in ... do` traversal, backed concretely by `java.util.LinkedHashSet`.
- Array, vector, set, Java `Iterable`, and map key/value iteration now all share the same broad `for ... in ... do` semantic model even though the full explicit iterator protocol remains later work.
- Collection implementations should be based on Java runtime collections rather than a separate Perseus-native storage/runtime hierarchy.
- Java interop should include an easy, explicit way to convert Perseus collections to and from Java collection values returned by external Java classes.
- The remaining class-based and standard-library-owned collection work has been moved into Milestones 44 and 45.

## Milestone 44 - Generics

**Goal:** Add a first generics system strong enough to support real reusable library abstractions, especially collection classes such as `Vector[T]`, `Map[K, V]`, and `Set[T]` (see [Generics Design Spec.md](Generics%20Design%20Spec.md)).

- [x] Introduce a structured internal `Type` model in the compiler rather than continuing to rely on ad hoc string tags
- [x] Move the compiler's main symbol-table and expression-type storage onto the structured `Type` model
- [x] Carry structured `Type` information into procedure, method, class, and external-value metadata
- [x] Thread structured `Type` information into the main code-generation context and descriptor helpers
- [ ] Migrate the remaining code-generation paths away from legacy string-tagged type checks and onto `Type`
- [x] Add focused regression coverage for the structured-type-model migration so existing typing behavior stays stable
- [ ] Add parser support for generic class declarations with bracketed type parameters such as `class Vector[T];`
- [ ] Add parser support for generic type uses such as `ref(Vector[integer])`
- [ ] Add symbol-table and semantic-analysis support for generic type parameters and generic class members
- [ ] Implement erased JVM lowering for generic classes and generic member signatures
- [ ] Add focused regression coverage for generic class declarations, instantiation, member use, and type-checking failures

**Implementation notes:**
- This milestone should follow [Generics Design Spec.md](Generics%20Design%20Spec.md).
- The first slice is generic classes first, not generic procedures everywhere.
- The preferred surface is bracketed type arguments such as `Vector[integer]` rather than a more symbolic or Java-specific notation.
- The recommended runtime strategy is erased generics on the JVM.
- The work should proceed in two stages within the milestone: first replace the current string-heavy type bookkeeping with a structured internal representation, then add the generic surface and lowering on top of that foundation.
- The remaining migration work is now concentrated mainly in the deeper code-generation paths, where many instruction-selection decisions still depend on legacy string tags.

## Milestone 45 - Collection Classes, Iterators, and Library Ownership

**Goal:** Move Perseus collections from compiler-special-cased surfaces toward real standard-library collection classes and iterator abstractions built on top of the new generics support.

- [ ] Define the first standard-library collection module layout for `Vector[T]`, `Map[K, V]`, and `Set[T]`
- [ ] Introduce Perseus-side standard-library collection classes for `Vector[T]`, `Map[K, V]`, and `Set[T]`
- [ ] Define and implement an explicit iterator protocol that works with `for ... in ... do`
- [ ] Move ordinary collection operations behind the standard-library collection surface rather than direct compiler-only method knowledge
- [ ] Add standard-library conversion helpers for collection interop with Java `List`, `Map`, and `Set`
- [ ] Define which collection syntax remains compiler-recognized sugar and which behavior is owned entirely by the library layer
- [ ] Add iterator-pipeline operations such as `map` and `filter` on top of anonymous procedures
- [ ] Add focused regressions and sample programs for the generic collection-class surface

**Implementation notes:**
- This milestone should treat the current `vector`, `map`, and `set` surface as the collection first slice, not as the permanent final architecture.
- The intended long-term direction is Perseus-side Simula-style collection classes backed internally by Java runtime collections.
- Existing collection declarations such as `vector integer nums;` may remain as source-level sugar if they can be defined cleanly in terms of the generic class-based collection model.

## Milestone 45.5 - Structured Data and JSON

**Goal:** Build a first structured-data library layer on top of Milestone 43 collections so Perseus can read and write practical JSON data without leaving the language.

- [ ] Add a standard-library JSON reader/parser
- [ ] Add JSON writing/encoding support
- [ ] Map JSON arrays to `vector`
- [ ] Map JSON objects to `map string ...`
- [ ] Define the initial JSON value model for mixed/dynamic content
- [ ] Add sample programs and regression tests for parsing and emitting JSON

**Implementation notes:**
- This milestone is intended as a practical follow-on to Milestone 43, not as a competing direction with the mathematical roadmap.
- JSON support should build directly on the Java-backed `vector` and `map` work that already exists.
- More ambitious object-to-JSON reflection can remain later work if needed; the first useful slice is structured data over vectors, maps, strings, numbers, booleans, and null.

## Milestone 46 - Complex Numbers and Mathematical Types

**Goal:** Add the first explicitly mathematics-oriented type-system extensions that would make Perseus more natural for scientific and engineering work (see [Mathematical Computing Design Spec.md](Mathematical%20Computing%20Design%20Spec.md)).

- [ ] Add a source-level `complex` type
- [ ] Define complex literals or constructors in a form that fits Perseus cleanly
- [ ] Support complex arithmetic through the ordinary operator surface where appropriate
- [ ] Support arrays of complex values
- [ ] Define how complex values should interoperate with Java and the standard library
- [ ] Add focused sample programs and regression tests for scientific/numerical use cases

## Milestone 47 - Mathematical Arrays and Numerical Procedures

**Goal:** Make numerical code more expressive by improving array-oriented notation and by adding procedure features that fit mathematical programming well (see [Mathematical Computing Design Spec.md](Mathematical%20Computing%20Design%20Spec.md)).

- [ ] Add array sections or slices in a form consistent with Perseus's bounded-array model
- [ ] Add carefully chosen whole-array expressions where they clearly improve readability
- [ ] Decide whether vector/matrix-oriented operations belong partly in syntax or entirely in the standard library
- [ ] Add `pure` procedures or an equivalent purity marker for mathematically side-effect-free routines
- [ ] Add `elemental` procedures or an equivalent mechanism for array-lifted scalar procedures
- [ ] Consider lightweight tuple or multiple-return-value support for numerical procedures that naturally return values plus status or error information
- [ ] Add focused numerical sample programs and regression tests

## Milestone 48 - Numeric Precision and Extended Arithmetic

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

## Milestone 49 - Numerical Standard Library Foundations

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

## Milestone 50 - Actors

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

## Milestone 51 - CLI Polish and Tooling

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
