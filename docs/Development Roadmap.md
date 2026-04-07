# Development Roadmap

This document tracks the active and longer-term roadmap for Perseus after the completed milestone history summarized in [Completed Milestones.md](Completed%20Milestones.md).

## Active Milestones

## Milestone 41 - Looping Extensions

**Goal:** Extend Perseus with more modern looping forms while preserving the original Algol `for` statement for compatibility (see [Looping and Collections Design Spec.md](Looping%20and%20Collections%20Design%20Spec.md)).

- [ ] Add `for ... in ... do` iteration over ranges and existing array forms
- [ ] Add a general `loop ... end` construct
- [ ] Add `break` and `continue`
- [ ] Define range syntax such as `..` and `..<`
- [ ] Define scope and evaluation rules for loop-local iteration variables
- [ ] Add focused sample programs and regression tests for the new loop forms

## Milestone 42 - Lambda Notation

**Goal:** Add anonymous procedure expressions as a higher-level extension on top of the procedure-value machinery (see [Perseus Language Design.md](Perseus%20Language%20Design.md)).

- [ ] Syntax and parsing for lambda-style procedure literals
- [ ] Lowering strategy onto existing procedure-reference infrastructure
- [ ] Tests for higher-order procedure use cases

## Milestone 43 - Collections and Iterators

**Goal:** Add higher-level collection types and iteration protocols suitable for more general-purpose programming (see [Looping and Collections Design Spec.md](Looping%20and%20Collections%20Design%20Spec.md)).

- [ ] Add collection types such as `vector`, `map`, and `set`
- [ ] Add collection literals and basic collection operations
- [ ] Extend `for ... in ... do` from ranges/arrays to collection and iterator-protocol-based iteration
- [ ] Define an iterator protocol that works with `for ... in ... do`
- [ ] Decide how collection libraries fit into the standard environment / standard library story
- [ ] Add sample programs and regression tests for collection use cases
- [ ] Decide how iterator pipelines such as `map` and `filter` should depend on Milestone 42 lambda notation

## Milestone 44 - Actors

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

## Milestone 45 - CLI Polish and Tooling

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

