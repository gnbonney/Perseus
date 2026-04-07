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
   - It would also overlap more with Milestone 39 CLI/product work and would likely require broader tooling decisions at the same time.

## Milestone 34 - Exception Follow-On

**Goal:** Finish the parts of the exception design that were deferred after the first working slice.

- [x] Give `when ... as ex do ...` real semantic/runtime support by binding a catch variable inside the handler
- [x] Bind `as ex` to the caught Java exception object in generated handler code
- [x] Allow exception-pattern identifiers to resolve against common Java exception classes without requiring `external java class` declarations
- [x] Add a built-in mapping for common Java exception classes used in Perseus exception patterns
- [x] Implement the initial built-in shorthand set for common Java exception pattern names
- [x] Distinguish exception-pattern type resolution from ordinary class/reference resolution where needed
- [x] Add regression tests for named Java exception patterns without prior external class declarations
- [x] Add regression tests for `when ... as ex do ...` handlers that inspect and use the bound exception value

**Implementation note:**
- Milestone 34 builds on ordinary Java exception objects rather than a separate Perseus exception universe.
- Java exception names should be the primary exception vocabulary in Perseus source where they already fit the JVM model naturally. Perseus-specific duplicate names such as `BoundsError`, `IOError`, and similar wrappers should be removed rather than carried forward as aliases.
- The current implementation supports explicit `java(...)` patterns, built-in shorthand for an initial practical set of common Java exception classes, and `when ... as ex do ...` handlers that bind and use the caught Java exception object.
- Regression coverage now includes named Java exception patterns, `as ex` message access, nested exception blocks, common Java exception shortcuts, and Java-based replacements for the older duplicate exception-name tests.

## Milestone 35 - Compiled Standard Environment

**Goal:** Move more of the environmental block out of compiler hardcoding and into a real always-available compiled standard environment.

- [x] Define the shape of the always-available standard environment / prelude
- [x] Create a dedicated stdlib source tree under `src/main/perseus/stdlib`
- [x] Move the Modified Report numeric procedures into compiled support such as `MathEnv`
- [x] Move the Modified Report numeric constants into compiled support such as `MathEnv`
- [x] Move string-oriented support such as `length`, `concat`, and `substring` into a compiled helper such as `Strings`
- [x] Move output-side environmental procedures into compiled support such as `TextOutput`
- [x] Move input-side environmental procedures into compiled support such as `TextInput`
- [x] Implement `fault` through a compiled/runtime helper that throws a runtime exception
- [x] Add a Gradle task that compiles the stdlib source tree with the Perseus compiler itself
- [x] Package the compiled standard environment as a separate jar such as `build/libs/perseus-stdlib.jar`
- [x] Ensure the standard environment is automatically available to all Perseus programs without explicit imports
- [x] Add tests showing that migrated environmental procedures work through the compiled standard environment rather than only through compiler-recognized names

**Modified Report inventory:**
- `perseus.lang.MathEnv`
  - `abs`, `iabs`, `sign`, `entier`, `sqrt`, `sin`, `cos`, `arctan`, `ln`, `exp`, `maxreal`, `minreal`, `maxint`, `epsilon`
- `perseus.text.Strings`
  - `length`
- `perseus.io.TextInput`
  - `inchar`, `ininteger`, `inreal`
- `perseus.io.TextOutput`
  - `outchar`, `outstring`, `outterminator`, `outinteger`, `outreal`
- `perseus.runtime.Faults`
  - `fault`

**Milestone 35 status:**
- `MathEnv` now covers `abs`, `iabs`, `sign`, `entier`, `sqrt`, `sin`, `cos`, `arctan`, `ln`, and `exp`.
- `MathEnv` now also covers the numeric constants `maxreal`, `minreal`, `maxint`, and `epsilon`.
- `Strings` now covers `length`, `concat`, and `substring`.
- `TextOutput` now covers `outchar`, `outstring`, `outterminator`, `outinteger`, and `outreal`.
- `TextInput` now covers `inchar`, `ininteger`, and `inreal`.
- `Faults` now covers `fault`.
- The typed procedure return-assignment coercion needed for integer-valued wrappers such as `entier` is implemented.
- The standard environment is now provisioned automatically for normal compilation, and the migrated math and string builtins route through `MathEnv` and `Strings` rather than directly to Java library calls.
- The migrated output procedures now route through `perseus.io.TextOutput`, with a small Java runtime helper handling the current JVM stream dispatch details behind that compiled stdlib unit.
- The migrated input procedures now route through `perseus.io.TextInput`, with a small Java runtime helper handling the current standard-input scanning details behind that compiled stdlib unit.
- The stdlib source tree, Gradle compile task, and stdlib jar packaging are in place.
- `stop` will remain a compiler/runtime intrinsic rather than being moved into the compiled standard environment.
- Milestone 35 is now complete at the current intended scope.

**Perseus string extensions:**
- `perseus.text.Strings` will also be the natural home for Perseus-specific string helpers such as `concat` and `substring`, even though they are not part of the Modified Report inventory.

**Suggested order:**
1. `src/main/perseus/stdlib`
2. `MathEnv`
3. `Strings`
4. `TextOutput`
5. `TextInput`
6. `Faults`
7. Gradle stdlib compile task
8. `perseus-stdlib.jar`

## Milestone 36 - Java API Interop Follow-On

**Goal:** Extend Perseus Java interop beyond static methods and first-slice object calls so ordinary Java APIs can be used more directly without helper bridge classes.

- [x] Add source-level support for external Java static fields such as `java.lang.System.out`
- [x] Add object-valued external bindings so imported Java values can be named and reused in Perseus source
- [x] Allow chaining instance calls through imported Java values and field lookups
- [x] Add source-level support for external Java instance fields where direct field access is the right interop surface
- [x] Improve overloaded-method resolution so Java calls are selected by argument types rather than only by name and number of arguments
- [x] Improve overloaded-constructor resolution for `new` calls against external Java classes
- [x] Implement Java constants and enum-like static members through the existing external Java static-field binding syntax
- [x] Improve diagnostics for ambiguous or unsupported Java overloads and member lookups
- [x] Add regression tests around `System.out` / `System.err`, `PrintStream`, overloaded methods, overloaded constructors, and chained Java member calls
- [x] Refactor the compiled standard library to use these richer Java interop features directly and remove bridge helpers where they are no longer needed

**Implementation notes:**
- This milestone grows out of concrete friction discovered while moving `TextOutput` and `MathEnv` into the compiled standard environment.
- The direct Java interop surface now covers aliased external Java static fields, imported object-valued bindings, chained instance calls through those bindings, direct reads of public Java instance fields, Java constants and enum-like members, and overload resolution by argument type for both Java methods and Java constructors.
- Diagnostics now distinguish ambiguous Java overloads from unsupported Java member calls instead of collapsing them into generic unknown-member errors.
- `MathEnv`, `TextOutput`, and `Faults` now use the richer interop directly, so the obsolete `MathConstantsSupport`, `TextOutputSupport`, and `FaultSupport` bridge helpers are gone.
- Milestone 36 is now complete at the current intended scope.

## Milestone 37 - Dynamic Channels and Formatted I/O Follow-On

**Goal:** Generalize the first working dynamic I/O slice into a broader runtime channel model.

**Acceptance criteria:**
- A program can open a file on channel `2+`, write through ordinary output procedures such as `outstring`, `outinteger`, and `outreal`, close the channel, and produce the expected file contents.
- A program can open a file on channel `2+`, read through ordinary input procedures such as `ininteger`, `inreal`, and `inchar`, and receive the expected values.
- A program can associate a string buffer with a channel and use ordinary output procedures against that channel to build the expected string result.
- Channels `0` and `1` continue to behave as standard error and standard output.
- Invalid or closed channels fail in a defined way rather than silently defaulting to the wrong target.
- End-of-file behavior is defined and covered by regression tests.
- Formatted and unformatted I/O both work against non-console channels, not only against `System.out` / `System.err`.

- [x] Generalize the runtime model beyond the current constant-channel / literal-path slice
- [x] Route `outstring`, `outinteger`, `outreal`, `outterminator`, and `instring` through dynamic file-channel dispatch
- [x] Extend the remaining ordinary input procedures to the same dynamic channel model (`ininteger`, `inreal`, and `inchar`)
- [x] Add more file/string-channel regression programs that combine unformatted and formatted I/O
- [x] Add explicit `EndOfFile` behavior and keep `fault(...)` only as the narrower compatibility fallback while ordinary I/O/channel failures use direct Java-backed exceptions
- [x] Extend output-side formatted I/O beyond the current `I`, `F`, and `A` subset
- [x] Move the existing `informat` `I`, `F`, and `A` functionality out of direct compiler hardcoding into shared runtime support
- [x] Consolidate the broader channel model into a shared runtime owner such as `Channels` where the heavier environmental features need it

**Notes on `fault(...)` as fallback:**
- Chosen direction: ordinary I/O/channel failures should use direct Java-backed exceptions such as `EOFException`, `IOException`, `IllegalStateException`, and `IllegalArgumentException`, while `fault(...)` remains only as the narrower compatibility fallback.
- Current formatting state: the current output-format rendering path is no longer parsed and rendered entirely inside the compiler. `outformat` now delegates rendering to a shared runtime helper before writing through the standard `TextOutput` channel path, and the output-side descriptor set now includes `Iw[.m]`, `Fw.d`, `Ew.d`, `A`/`Aw`, `Lw`, `nX`, and `/`. `informat` still supports the narrower `I`, `F`, and `A` subset, but its format parsing and value-reading path now also lives in shared runtime support, with the compiler only handling assignment into the destination variables.
- `informat` is intentionally staying simple for now. Fixed-width descriptor-driven input is much less central to modern programming than formatted output, and real-world input is more often delimited or structured (`CSV`, `JSON`, `XML`, whitespace-separated text, or library-driven parsing). Perseus should therefore treat `informat` as a small convenience rather than a major parsing framework unless real use cases later justify extending it. That design choice does not mean keeping the current compiler hardcoding indefinitely: the existing `I`, `F`, and `A` behavior should still move toward compiled stdlib/runtime support as the remaining helper-reduction work progresses.
- Current status: file-channel reads now raise `EOFException` on end of file, while invalid channel use and ordinary file/runtime failures continue to surface as direct Java-backed exceptions such as `IllegalStateException`, `IllegalArgumentException`, and `IOException`.
- Current channel model: dynamic file-channel state, file-channel reads/writes, standard-input-backed numeric/text reads, and current `informat` parsing now all share the same runtime owner in `gnb.perseus.runtime.Channels`, rather than being split between separate channel and text-input helpers.
- Milestone 37 is now complete at the current intended scope.
- Option 1: Keep `fault(...)` as the broad compatibility fallback for EOF, invalid channel use, unsupported modes, and other channel/runtime problems. This is the most conservative path, but it gives user code the least specific recovery information.
  Example:
  ```algol
  begin
      ininteger(2, x)
  exception
      when RuntimeException do
          done := true
  end
  ```
- Option 2: Use direct Java-backed exceptions for ordinary I/O conditions such as `EOFException`, `IOException`, `IllegalStateException`, and `IllegalArgumentException`, while keeping `fault(...)` only as a narrower fallback for cases that remain awkward or intentionally fail-fast. This currently looks like the best fit.
  Example:
  ```algol
  begin
      ininteger(2, x)
  exception
      when java(java.io.EOFException) do
          done := true
      when IOException do
          io_failed := true
      when IllegalStateException do
          bad_channel := true
      when RuntimeException do
          unexpected_fault := true
  end
  ```
- Option 3: Remove any internal `fault(...)` fallback from the I/O path entirely, so ordinary channel and file problems are always exposed as direct exceptions and `fault(...)` only happens when the source program calls it explicitly. This is the cleanest long-term model if Perseus continues to lean fully into Java-backed exceptions.
  Example:
  ```algol
  begin
      ininteger(2, x)
  exception
      when java(java.io.EOFException) do
          done := true
      when IOException do
          io_failed := true
      when IllegalStateException do
          bad_channel := true
  end
  ```

## Milestone 38 - Remaining Java Helper Reduction

**Goal:** Remove the remaining Java runtime bridge helpers by extending Perseus source/runtime support until the compiled standard environment can express these cases directly.

- [x] Identify the remaining Java runtime support classes still required by the compiled standard environment
- [x] Add a real source-level `signal expr` statement so `fault` can raise Java-backed exceptions without `FaultSupport`
- [x] Add reference-typed arrays so compiled stdlib units can keep channel, reader, writer, and scanner state in Perseus source instead of Java-side registries
- [x] Add a source-level `null` reference value and the needed reference comparisons so compiled stdlib code can test open/closed or initialized/uninitialized object slots directly
- [x] Add the boxing and `ref(Object)` array support needed for compiled stdlib code to build Java `Object[]` argument lists directly, removing the need for `TextFormatSupport`
- [x] Extend ordinary procedure declarations and parameter specs to support scalar `ref(...)` parameters plus `ref(...)` and `boolean` return types where the stdlib/runtime surface needs them
- [x] Extend external Java procedure declarations to support scalar `ref(...)` parameters plus `ref(...)` and `boolean` return types so compiled stdlib code can model Java I/O helpers directly without wrapper bridge classes
- [x] Extend formal procedure-parameter specs and generated procedure-reference support so higher-order `boolean` and `ref(...)` procedures work through ordinary procedure parameters and variables, not just direct calls
- [x] Support external Java `ref(...) array` parameters so compiled stdlib code can call ordinary JVM `Object[]`-style APIs directly
- [x] Move the dynamic-channel integer, real, and terminator output formatting path out of `Channels.java` and into compiled `TextOutput.alg` code using ordinary Java interop plus the remaining `outString(...)` primitive
- [x] Route `outformat` formatting through a compiled `TextOutput.alg` helper instead of emitting a direct compiler-side call to `TextFormatSupport`, even while the underlying formatter logic still lives there for now
- [x] Route `informat` value loading through a compiled `TextInput.alg` helper instead of emitting a direct compiler-side call to `Channels.informatValues(...)`, even while the underlying parsing logic still lives there for now
- [x] Move `informat` descriptor parsing and validation out of `Channels.java` and into compiled `TextInput.alg`, while keeping only the lower-level primitive reads at the runtime boundary
- [x] Move the simpler `outformat` descriptor handling cases such as `A`, `Aw`, `nX`, and `/` out of `TextFormatSupport.java` and into compiled `TextOutput.alg`
- [x] Migrate the remaining numeric/logical `outformat` rendering cases such as `I`, `F`, `E`, and `L` out of `TextFormatSupport.java` and into compiled `TextOutput.alg`
- [x] Delete the obsolete `TextFormatSupport.java` helper now that compiled stdlib formatting no longer depends on it
- [x] Trim `Channels.java` down to only the remaining low-level Java boundary for dynamic channel ownership plus raw token/line/write primitives
- [x] Resolve the final `Channels.java` boundary by migrating channel ownership/state out of `gnb.perseus.runtime.Channels` and into compiled `perseus.io.Channels` stdlib code
- [x] Remove compiler-side stdio/channel assumptions that currently live in `ChannelIOGenerator` by moving more behavior behind ordinary compiled stdlib code, including literal-only `informat` handling, compile-time `informat` spec parsing, and constant-channel / `openstring` bookkeeping
- [x] Add regression coverage showing the migrated stdlib paths still work after the helper deletion and compiler cleanup, including structural assertions that generated code no longer depends on the removed bridge surface

**Acceptance criteria:**
- The standard text I/O library can be expressed primarily as Perseus `.alg` units using ordinary compiled stdlib code plus direct external Java interop
- The compiled stdlib no longer depends on Java bridge classes in `gnb.perseus.runtime` for channel ownership, formatted output, or formatted input support
- The compiler no longer relies on hardcoded stdio-specific Jasmin emission paths to implement the migrated stdlib behavior
- `PerseusStdlibBuilder` no longer needs to copy `Channels` or `TextFormatSupport` into stdlib outputs
- A fresh clone of the repository can build the Java compiler and then compile the standard environment from source without requiring any prebuilt stdlib jar, checked-in generated classes, or previously installed Perseus distribution

**Implementation notes:**
- Perseus now has a source-level `null` literal for object references.
- Object-reference comparisons currently support `=` and `<>`, which lower to JVM reference equality/inequality.
- Ordered comparisons such as `<` and `>` remain numeric-only and produce a diagnostic if used with object references.
- Recent milestone 38 regression coverage now includes direct ordinary procedures, external Java procedures, and higher-order procedure-parameter cases for `boolean` and `ref(...)` signatures.
- External Java interop now also supports `ref(...) array` parameters, which helps unlock eventual migration of formatter logic away from `TextFormatSupport` and toward ordinary JVM `Object[]` APIs called from compiled stdlib code.
- `TextOutput.alg` now handles dynamic-channel integer, real, and terminator rendering itself via `java.lang.Integer.toString`, `java.lang.Double.toString`, and ordinary string output, so `Channels.java` no longer owns those wrapper methods.
- The compiler-side `outformat` path now routes formatted rendering through a helper in compiled `TextOutput.alg`, so the generator no longer emits a direct stdio-specific call to `TextFormatSupport`.
- The compiler-side `informat` path now routes parsed value loading through a helper in compiled `TextInput.alg`, so generated client code no longer emits a direct call to `Channels.informatValues(...)`.
- `TextInput.alg` now owns `informat` token parsing and validation itself, while `Channels.java` has been reduced to the lower-level numeric/token read primitives still needed at that boundary.
- `TextInput.alg` now also owns integer, real, and character parsing on top of the raw `Channels.inToken(...)` primitive, so `Channels.java` no longer carries separate numeric/character parsing helpers.
- `outformat` no longer requires a string literal at compile time; `ChannelIOGenerator` now passes arbitrary string expressions through to the compiled `TextOutput.alg` formatter helper, leaving `informat` as the remaining literal-only formatted-I/O path in the generator.
- `TextOutput.alg` now owns tokenization plus the simpler `outformat` descriptors `A`, `Aw`, `nX`, and `/`, while `TextFormatSupport.java` has been narrowed from whole-format rendering down to the remaining single-token numeric/logical cases.
- `TextOutput.alg` now also owns the remaining numeric/logical `outformat` descriptors `I`, `F`, `E`, and `L`, so compiled stdlib formatting no longer depends on `TextFormatSupport.java` at runtime and the stdlib builder no longer needs to copy that helper into stdlib outputs.
- The dead `TextFormatSupport.java` helper has now been deleted entirely, so `Channels.java` is the only remaining milestone 38 Java helper target.
- `ChannelIOGenerator` no longer keeps constant-channel bookkeeping for file or string output routing; `openstring` now lowers to the compiled `perseus.io.Channels` path and string-channel accumulation lives behind stdlib-owned channel state.
- Regression coverage now also checks that generated clients route `openstring` through `perseus.io.Channels` and that compiled `Channels.alg` owns thunk-backed string-channel mutation directly.
- `PerseusStdlibBuilder` now provisions the generic `Thunk` support class required by stdlib-owned string channels during from-source standard-environment builds.
- Milestone 38 now has a clearer staged helper-reduction path: first route compiler behavior through compiled stdlib helpers, then migrate parsing/formatting logic into `.alg`, and finally delete the obsolete Java bridge entry points.

## Milestone 39 - CLI Follow-On

**Goal:** Extend the working CLI into a broader tool suitable for multi-file and interop-heavy workflows.

- [x] Support compiling multiple Perseus source files in one CLI invocation
- [ ] Define clearer output conventions for reusable separately compiled Perseus libraries
- [x] Improve handling of external inputs from both directories and JAR files in library-oriented workflows
- [x] Decide whether the CLI should support more explicit library-oriented packaging or commands beyond raw class-file output
- [ ] Optional installer/distribution packaging beyond `installDist`
- [ ] Further exit-code and machine-facing CLI refinements
- [ ] Additional commands such as `check` or `emit-jasmin` if they remain desirable after the MVP

**Implementation notes:**
- The CLI already supports multi-file compilation, `--package`, optional `--jar` packaging, and `-cp` / `--classpath` workflows for separately compiled Perseus and Java dependencies.
- External resolution now accepts both directory and JAR classpath roots in library-oriented workflows, matching the intended JVM-style classpath model.
- The current CLI direction is now clear enough to treat ordinary class-file output as the default, with optional runnable JAR packaging, rather than requiring a heavier library-specific packaging mode before Milestone 39 can progress.

## Milestone 40 - Input Procedures Cleanup

**Goal:** Revisit the input-side environmental procedures that were marked complete during the MVP path and confirm that they really work end to end in realistic programs.

- [ ] Add a real regression around [`input_procedures.alg`](../test/algol/io/input_procedures.alg)
- [ ] Verify and, if necessary, fix ininteger(channel, var) runtime behavior
- [ ] Verify and, if necessary, fix inreal(channel, var) runtime behavior
- [ ] Verify and, if necessary, fix inchar(channel, str, var) runtime behavior
- [ ] Decide whether the current scanner/channel model for input procedures matches the intended environmental-block semantics closely enough
- [ ] Update the MVP and environmental-block documentation once the behavior is confirmed

## Future Direction Milestones

These milestones are not just deferred implementation cleanup. They represent larger possible directions for Perseus after the MVP and the most important follow-on work are in place.

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

## Out-of-Scope Features

These items remain possible later, but they are intentionally outside the active numbered roadmap.

### External Call-by-Name ABI

**Goal:** Add a documented and stable Perseus-to-Perseus thunk ABI for the narrower case of separately compiled translations of historical Algol libraries.

- [ ] Define the external thunk ABI for call-by-name parameters across separately compiled Perseus boundaries
- [ ] Decide which generated support types must become stable shared runtime contracts
- [ ] Add focused external call-by-name regressions based on translated historical Algol library patterns
- [ ] Keep this as a specialized Perseus-to-Perseus compatibility feature rather than the default public JVM library surface

**Implementation notes:**
- This work is intentionally separated from Milestone 32 because it depends on a stabilized thunk ABI rather than the first external procedure slice
- The strongest motivation is historical Algol library translation, not general JVM-facing library design
- Public JVM-facing reuse should prefer value-oriented Perseus classes and methods where possible, while external call-by-name remains a narrower compatibility feature

### Label and Switch Parameters / Designational Exits

**Priority:** Optional standards-completeness feature that may never be implemented if Perseus continues to prefer structured exceptions and other more modern control-flow mechanisms.

**Goal:** Allow labels and switches to be passed as parameters and used for procedure-mediated exits, matching real Algol 60 designational-expression semantics more closely.

- [ ] Grammar: `label` and `switch` formal parameter specifiers
- [ ] Grammar: `Boolean procedure` declarations and `Boolean procedure` formal parameter specifiers
- [ ] Codegen/runtime: Boolean-valued procedure calls and Boolean procedure references/parameters
- [ ] Procedure calls: pass labels and switches as actual parameters
- [ ] Codegen: support designational exits through passed labels/switches where legal
- [ ] Enforce or document the goto-scope restrictions that still apply

Possible JVM strategy for passed labels: lower non-local label exits to tagged exceptions (or an equivalent non-local escape mechanism) and catch them in the block/procedure activation that owns the real target labels. This would avoid requiring impossible cross-method JVM jumps while still giving a plausible implementation path for Algol-style designational exits.

Possible JVM strategy for passed switches: lower a switch parameter to an indexed collection of label-exit descriptors (or thunks that resolve to them), reusing the same non-local escape machinery as passed labels when `goto sw[i]` selects a non-local target.

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


