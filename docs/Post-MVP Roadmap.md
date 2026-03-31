# Post-MVP Roadmap

This document covers the active roadmap after the largely implemented MVP path in [MVP Roadmap.md](MVP%20Roadmap.md). It tracks follow-on milestones and larger future directions for Perseus going forward.

---

## Follow-On Milestones After MVP

The milestones below collect follow-on work that was intentionally deferred while pushing toward the MVP path through Milestone 31. They are still important, but they are no longer mixed into the mainline milestones as scattered notes.

## Milestone 32 - External Procedure Follow-On

**Goal:** Extend the first working external-procedure slice into a richer and better documented interop model.

- [x] Replace the older `external algol(...)` wording in docs and remaining design notes with the default `external(...)` Perseus-to-Perseus model
- [x] Add a user-facing way to choose the generated JVM package/class path for separately compiled Perseus units instead of always assuming `gnb/perseus/programs`
- [x] External Perseus array parameters as a documented ABI case
- [x] Additional compile-time external signature validation and diagnostics
- [x] Keep procedure-valued external parameters out of scope unless Perseus later adopts a stable external ABI for procedure references

**Implementation notes:**
- `external(...)` is now the default Perseus-to-Perseus syntax, while `external algol(...)` remains accepted as a compatibility spelling
- The CLI now supports `--package` so separately compiled Perseus units can choose stable JVM package/class names intentionally
- External one-dimensional array parameters now use the documented `array + lower + upper` calling convention in both direct compiler flow and CLI `-cp` workflows
- External compile-time diagnostics now distinguish missing classes, missing methods, return-type mismatches, parameter-signature mismatches, and array ABI mismatches
- Procedure-valued external parameters are intentionally out of scope rather than an open implementation gap

## Milestone 33 - Class Model Follow-On

**Goal:** Extend the basic Simula-inspired class model beyond the first working slice.

- [x] Simula-style prefix inheritance and its initial construction rules
- [x] Dynamic dispatch for overridden procedures in the initial prefix model
- [x] External JVM class declarations and initial object interop
- [x] Allow Perseus classes to extend external Java classes where meaningful JVM interop requires it
- [x] Support abstract Java superclasses and Java interfaces in the class interop model
- [x] Add initial semantic validation for Java superclass/interface conformance before code generation
- [x] Add tests for Java subclassing, abstract-class, interface, and override scenarios
- [x] Decide that Perseus should use a source-level `namespace` declaration for reusable class identity
- [x] Add grammar support for `namespace ...;` source headers
- [x] Define how `namespace` interacts with the current CLI `--package` option
- [x] Update code generation so reusable class identity is derived from `namespace + class name`
- [x] Add tests for separately compiled classes using `namespace`
- [x] Decide that multi-file library workflows should allow multiple Perseus source files in one compiler invocation, with shared `namespace` agreement
- [x] Add CLI support for compiling multiple Perseus source files in one invocation
- [x] Define and enforce the rule that source files compiled together for one library must agree on `namespace`
- [x] Add regression tests for multi-file namespaced libraries compiled in one compiler run

**Implementation notes:**
- The long-term inheritance direction is now prefix-style in the Simula tradition rather than Java-style subclass syntax
- External Java classes should be treated as a separate interop design problem, not as ordinary Perseus prefixes
- Prefix declarations now compile and run in the form `Base class Derived(...)`, with JVM subclass emission and inherited method availability
- Dynamic dispatch now works for overridden procedures when a derived object is held through a base-class `ref(...)`
- `external java class ...` now supports a first working slice of object creation and instance method calls for imported JVM classes
- Perseus classes can now extend concrete and abstract external Java classes, and can implement one or more Java interfaces with `implements`
- Current regression coverage now includes concrete Java superclass extension, abstract Java superclass conformance, single-interface conformance, and multi-interface conformance
- The current semantic-validation slice now covers the cases exercised by those class and interface regressions before JVM/ASM verification
- External Java subclassing is now the intended direction for meaningful Java interop, because many Java APIs accept or return specific framework base classes rather than generic objects
- Source-level `namespace` declarations are now implemented, and declared Perseus classes are emitted under the source namespace while ordinary wrapper/main program classes still use the compile target identity
- Current regression coverage now also includes namespace parsing, multiple classes in one namespace, and a namespaced separate-compilation library/client path
- The CLI can now compile multiple source files in one invocation, provided they agree on the same `namespace`
- Current regression coverage now also includes successful multi-file namespaced compilation and rejection of mismatched namespaces
- Conformance to extended Java classes and implemented Java interfaces should be checked during semantic validation before code generation, with JVM/ASM verification left as a later safety net
- Reusable class identity now has a chosen language-level direction: a source `namespace` declaration rather than relying only on CLI package configuration
- Milestone 33 is now complete at the current intended scope

**Decision:**
- Perseus will use the keyword `namespace` for a source-level naming declaration for reusable classes and libraries.
- This is preferred over `package`, `module`, or `library` because it describes naming and identity without implying a physical bundle or reusing a term that already has a different established meaning in the language.

**Decision:**
- Perseus will support multi-file library compilation by allowing multiple source files in one compiler invocation, provided they agree on the same `namespace`.
- This keeps the feature at the compiler level, closer to `javac`, rather than turning the Perseus CLI into a fuller build tool.

**Former options considered:**

1. Keep the current single-file compilation model and treat `namespace` only as a naming declaration.
   - This is the smallest implementation step.
   - Separate compilation still works, but multi-file libraries remain a user-managed workflow built out of repeated CLI invocations and classpaths.

2. Allow multiple Perseus source files in one CLI invocation, requiring them to agree on the `namespace`.
   - This would make it easier to compile one logical library from several source files without adding a heavier project model.
   - It preserves the current lightweight CLI feel while giving `namespace` a more practical library workflow.

3. Add a fuller library-oriented build mode around `namespace`, with explicit conventions for multi-file library roots, outputs, and possibly packaging.
   - This is the strongest long-term workflow.
   - It would also overlap more with Milestone 36 CLI/product work and would likely require broader tooling decisions at the same time.

## Milestone 34 - Exception Follow-On

**Goal:** Finish the parts of the exception design that were deferred after the first working slice.

- [ ] Decide which existing runtime failures should remain fail-fast and which should become catchable exceptions
- [ ] Give `when ... as ex do ...` real semantic/runtime support by binding a catch variable inside the handler
- [ ] Decide the initial source-level exception interface in Perseus
- [ ] Implement that initial exception interface through helpers, object-style access, or both
- [ ] Decide how much of the current JVM exception mapping should remain visible behind that interface versus being replaced with a dedicated Perseus runtime exception hierarchy

**Recommendation on fail-fast versus catchable failures:**
- Programmer-facing runtime conditions should become catchable exceptions where Perseus code can reasonably recover or report them.
- Compiler bugs, verifier failures, impossible internal states, and other broken-invariant conditions should remain fail-fast.
- In practice, this means ordinary runtime faults such as file and input problems are better candidates for catchable exceptions, while internal compiler/runtime corruption is not.

**Options for caught exception values in Perseus source:**
- Option 1: Keep exceptions mostly opaque in source and continue to prefer helper procedures such as `exceptionmessage(ex)` and `printexception(ex)`.
  Analysis: This is the most conservative option and fits the current exception design well. It keeps the language simple and avoids committing to an exception object model too early, but it is less elegant and less object-oriented.
- Option 2: Introduce a small standard object-style exception interface in source, with selected members such as `ex.message` and perhaps `ex.cause`.
  Analysis: This would give Perseus a cleaner and more modern source-level exception model without requiring a fully open-ended object system for exceptions. It is a good compromise if exception handling becomes more central to everyday Perseus code.
- Option 3: Treat exceptions as ordinary class-style objects and allow a broader member model, potentially including richer JVM exception details.
  Analysis: This is the most expressive option, but it also risks overcommitting the language to the current JVM exception model and making the source language more complex than necessary.

**Likely best direction:**
- Option 2 is probably the best eventual direction if Perseus decides to expose exception values more richly in source.
- Option 1 remains the best short-term position until exception binding and inspection needs become more central.

**Consequences of that decision:**
- If Perseus stays with Option 1, the next work is helper procedures such as `exceptionmessage(ex)` and `printexception(ex)`.
- If Perseus adopts Option 2, the next work is to define a small object-style surface such as `ex.message` and decide whether helpers remain as convenience wrappers.
- Option 3 would also require a broader decision about how much of the JVM exception model Perseus wants to expose directly.

**Options for the underlying exception mapping model:**
- Option 1: Keep a thin mapping to the current JVM exception model.
  Analysis: This is the simplest option. It preserves debugging fidelity and minimizes new runtime design work, but it leaves Perseus more dependent on JVM exception concepts and names.
- Option 2: Use a hybrid model with a small Perseus exception hierarchy for common language/runtime cases while still allowing recognizable JVM exceptions underneath.
  Analysis: This gives Perseus a clearer language identity without requiring every exception to be normalized immediately. It is a good compromise between language clarity and implementation practicality.
- Option 3: Replace most of the current JVM exception exposure with a dedicated Perseus runtime exception hierarchy.
  Analysis: This is the cleanest language-level story, but it is also the most ambitious option and risks hiding useful JVM-level detail too early.

**Likely best direction:**
- Option 2 is probably the best direction.
- It gives Perseus its own exception vocabulary where that matters most, while still preserving the practical value of the underlying JVM exception information.

## Milestone 35 - Dynamic Channels and Formatted I/O Follow-On

**Goal:** Generalize the first working dynamic I/O slice into a broader runtime channel model.

- [ ] Generalize the runtime model beyond the current constant-channel / literal-path slice
- [ ] Extend more input/output procedures to use dynamic stream dispatch instead of only `System.out` / `System.err`
- [ ] Add explicit `EndOfFile` behavior and decide where `fault(...)` remains the compatibility fallback
- [ ] Extend formatted I/O beyond the current `I`, `F`, and `A` subset
- [ ] Add more file/string-channel regression programs that combine unformatted and formatted I/O

## Milestone 36 - CLI Follow-On

**Goal:** Extend the working CLI into a broader tool suitable for multi-file and interop-heavy workflows.

- [ ] Support compiling multiple Perseus source files in one CLI invocation
- [ ] Define clearer output conventions for reusable separately compiled Perseus libraries
- [ ] Improve handling of external inputs from both directories and JAR files in library-oriented workflows
- [ ] Decide whether the CLI should support more explicit library-oriented packaging or commands beyond raw class-file output
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

## Milestone 43 - Looping Extensions

**Goal:** Extend Perseus with more modern looping forms while preserving the original Algol `for` statement for compatibility (see [Looping and Collections Design Spec.md](Looping%20and%20Collections%20Design%20Spec.md)).

- [ ] Add `for ... in ... do` iteration over ranges and existing array forms
- [ ] Add a general `loop ... end` construct
- [ ] Add `break` and `continue`
- [ ] Define range syntax such as `..` and `..<`
- [ ] Define scope and evaluation rules for loop-local iteration variables
- [ ] Add focused sample programs and regression tests for the new loop forms

## Milestone 44 - Collections and Iterators

**Goal:** Add higher-level collection types and iteration protocols suitable for more general-purpose programming (see [Looping and Collections Design Spec.md](Looping%20and%20Collections%20Design%20Spec.md)).

- [ ] Add collection types such as `vector`, `map`, and `set`
- [ ] Add collection literals and basic collection operations
- [ ] Extend `for ... in ... do` from ranges/arrays to collection and iterator-protocol-based iteration
- [ ] Define an iterator protocol that works with `for ... in ... do`
- [ ] Decide how collection libraries fit into the standard environment / standard library story
- [ ] Add sample programs and regression tests for collection use cases
- [ ] Decide how iterator pipelines such as `map` and `filter` should depend on Milestone 41 lambda notation

## Milestone 45 - External Call-by-Name ABI

**Goal:** Add a documented and stable Perseus-to-Perseus thunk ABI for the narrower case of separately compiled translations of historical Algol libraries.

- [ ] Define the external thunk ABI for call-by-name parameters across separately compiled Perseus boundaries
- [ ] Decide which generated support types must become stable shared runtime contracts
- [ ] Add focused external call-by-name regressions based on translated historical Algol library patterns
- [ ] Keep this as a specialized Perseus-to-Perseus compatibility feature rather than the default public JVM library surface

**Implementation notes:**
- This work is intentionally separated from Milestone 32 because it depends on a stabilized thunk ABI rather than the first external procedure slice
- The strongest motivation is historical Algol library translation, not general JVM-facing library design
- Public JVM-facing reuse should prefer value-oriented Perseus classes and methods where possible, while external call-by-name remains a narrower compatibility feature

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
- Richer member syntax may still need a later refinement such as `obj.name` for field selection and `obj.name()` for explicit calls if real interop cases make the ambiguity matter, but this looks unlikely for well-designed Java classes and is not an active Milestone 33 blocker.

## Longer-Term Tooling Ideas
- Improve JVM verifier feedback so bytecode verification failures can be explained more clearly in terms of Perseus source and compiler phases.
- Versioned diagnostic schemas, stable IR formats, and LSP (Language Server Protocol) integration.


