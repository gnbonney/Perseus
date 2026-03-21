# Man-or-Boy (manboy.alg) Debugging Notes

This document captures the current state of the `manboy_test` failure, the root cause analysis, and concrete next steps for getting the test passing.

The Man-or-Boy program is a classic call-by-name/closure semantics stress-test invented by Donald Knuth to distinguish correct implementations of name-parameter passing (Jensen's device) from incorrect ones. The goal of this note is to keep the diagnosis and progress focused on the core thunk/closure state issues that prevent the correct output (`-67.0`).

## Current status (as of March 15, 2026)

- **Thunk isolation test:** failing (`thunk_closure_isolation_test`): compilation produces a `VerifyError: Unable to pop operand off an empty stack` in `p1`, indicating a stack-discipline bug in thunk/closure invocation.
- **Nested-scope access test:** passing (`nested_scope_access_test` now assembles and executes successfully; the previous Jasmin syntax problem with `outer`/procedure field naming is fixed).
- **proc_typed_simple_test:** passing (procedure-variable assignment handling now correctly stores `ProcRef` objects rather than trying to store them in a double slot).
- **proc_param_test:** passing (`proc_param_test` now produces `Hello` as expected; proc-parameter call generation is now correct for void procedures).
- **primer5:** failing (output isn’t close to `e ≈ 2.718…`, suggesting arithmetic/loop codegen or numeric stability issues).
- **ManBoy test:** failing (`manboy_test`).

## Recent Grok confirmation (March 20, 2026)

A detailed verifier analysis reconfirms the existing root cause with additional specifics:

- `A(...)` method in `ManBoy.j` has an incorrect `else_2` branch that calls `B` and then executes `pop2`, dropping the returned `double` result.
- The method then proceeds to a common epilogue that expects a `double` for `dreturn`, so this path is an operand stack mismatch that manifests as `VerifyError`.
- `then_1` branch also has incorrect value flow: `x4 + x5` is computed and held in `astore 8`, but not returned; the branch stores to a procedure field (`__proc_A`) rather than making the result the method return value.
- The underlying semantic issue is unchanged: per-activation environment is not implemented; static `__env_*` fields are shared across recursive calls.

This matches previous notes but adds a concrete path to unblock verification (remove `pop2`, keep `B` result) and explicit `A`/`B` return-flow correctness.
- **ManBoy failure mode:** code generation emits invalid Jasmin with `; ERROR: undeclared variable x1..x4` in the argument list for the call to `A` from `B`. The generated output does not assemble/verify.
- **Verifier state:** ManBoy still fails with a `VerifyError` (expecting object/array on stack for the call to `A`), which is now the primary observable failure.
- **Behavior observed:** The generated class includes `__env_A_x1..x4` fields (the env bridge exists), but the code that builds the argument array for the proc-to-proc call fails to resolve those captured variables into loads.

## Root cause (confirmed via Java reference model)

The compiler is treating a captured outer variable as a shared global static field rather than as a per-activation mutable cell.

In correct call-by-name / nested-procedure semantics, each activation that captures an outer variable must get its own *mutable box* for that variable (a per-instance field inside the closure/thunk object). When the runtime evaluates a call-by-name parameter or enters a nested procedure, it should access the *box belonging to that activation*, not a shared global.

In correct behavior:
- Each activation of the outer procedure creates a new closure object containing its own copy of the captured variables.
- Nested procedures (and thunks) access the captured variables through that closure object.
- Recursive re-entry therefore operates on an independent set of captured boxes (not on the same shared global state).

In the current codegen:
- The captured variable is stored in a shared static `__env_*` field and updated via `getstatic/putstatic`.
- All activations end up sharing the same mutable state, so recursion and nested calls interfere with each other.
- The result is incorrect numeric output (and, in earlier versions, verifier failures) even though the generated code appears to create distinct thunk objects.

## Fix required (general, not ManBoy-specific)

The compiler already generates a **per-call-site thunk class** (e.g., `ManBoy$Thunk0`) whose constructor takes the closed-over variables as parameters and stores them in **instance fields**. That part is correct: each thunk object is supposed to have its own mutable state (one-element boxes) so re-entrant calls see their own counter.

### Why this still fails

Despite the thunk mechanics, nested procedures and non-local access are currently implemented via an “environment bridge” that emits **static `__env_*` fields** (one per outer parameter/return) and uses `getstatic/putstatic` to access them. That means the value for `k` is stored in a shared global field rather than per-thunk, so **all thunk instances end up sharing the same mutable state**.

The correct approach is to ensure that call-by-name parameters:
- are represented by **distinct thunk objects** per call site,
- capture their own variable boxes as **instance fields**, and
- never read/write the shared `__env_*` fields when accessing those captured variables.

That means the compiler must implement **correct interaction between thunks, recursion, and mutation**: each recursive re-entry should see the thunk’s own mutable state, not a shared global state.

When a procedure identifier is passed by name as an argument, the generated thunk class must store each mutable outer variable it closes over as an **instance field**, not read/write from a shared static env field.

The thunk's `get()` method must operate exclusively on those instance fields, using `this` as the re-entry identity for recursive self-passing.

## Recent build observation

Running `manboy_test` now produces generated Jasmin that contains `; ERROR: undeclared variable x1..x4` in the argument list for the call to `A` from `B`. The compiler emits the expected `__env_A_x1..x4` fields, but fails to generate the correct loads to populate the argument array, leaving unresolved placeholders in the output.

The relevant generated artifacts are:

- `build/test-algol/ManBoy.j` (main class and proc invocation)
- `build/test-algol/ManBoy$Thunk0.j` (thunk class)

## Next debugging steps

1. **Fix proc_param_test** (use `test/algol/proc_param.alg`)
   - This failure is currently the easiest to address: the compiler emits output that is empty instead of `Hello`, indicating proc-parameter codegen for string procedures is broken.
   - Fixing this will validate the proc-param argument-passing path without needing full thunk/closure recursion.

0. **Fix section: statement vs expression proc call pop behavior**
   - `pop2` is only correct for statement calls that discard a `double` result, not for expression calls whose result is needed by surrounding code.
   - In JAlgol, `ProcedureGenerator.generateProcedureCall(..., isStatement)` and `generateUserProcedureInvocation(..., isStatement)` must pass `isStatement=false` for expression contexts (e.g., `A := ... else B`) and `true` for statement contexts (`B` as standalone stmt).
   - This ensures `A` else branch keeps `B`'s real return value on stack for the `dreturn` epilogue, matching the `ManBoy-VerifyError-Analysis` immediate fix.

2. **Validate thunk isolation** (use `test/algol/thunk_isolation.alg`)
   - Confirm whether two call-by-name args referencing the same variable end up sharing state.
   - If this fails, the issue is in thunk construction / box sharing.

3. **Validate nested-scope access** (use `test/algol/nested_scope_access.alg`)
   - Confirm whether nested procedure access works at all (i.e., a nested proc can read/write an outer variable).
   - If this fails, focus on making nested‑scope access reliable before tackling thunk isolation.

4. **Validate per-activation thunk isolation (still pending)**
   - The intended test would create two distinct thunks that escape their creation scopes and should each carry independent captured state.
   - **Current blocker:** the compiler emits invalid bytecode when generating procedure-variable returns/assignments (invalid local slot indices, `istore -1`, and `VerifyError`). This must be fixed before the isolation test can be written and relied on.

5. **Target full ManBoy** (use `test/algol/manboy.alg`)
   - Once the above tests pass and thunk capture isolation is confirmed, re-run ManBoy and verify it produces `-67.0`.

### Implementation focus areas
- Trace the code emission path that produced the faulty `B()` sequence (likely in `ProcedureGenerator.generateProcedureCall` / `createThunkClass` or caller-side restore logic).
- Fix the inconsistent instruction descriptors and stack handling (ensure ctor descriptors, `getfield`/`putfield` types, and boxing/unboxing sequences match the emitted types).
- Ensure the compiler no longer uses shared static `__env_*` fields for call-by-name captures; instead, thunk instance fields must carry all mutable closure state.

## Historical note: why this wasn't caught earlier

`manboy_test` and `recursion_euler_test` appeared to pass in earlier milestones but were actually broken — both relied on `redirectErrorStream(true)` capturing exception/error messages as non-empty output, satisfying a weak `output.length() > 0` assertion.

Later milestones (see the Milestone 15/16 entries in `docs/Compiler-TODO.md`) exposed the real failures:

1. `and` became a proper keyword, making `recursion_euler.alg`'s `abs(mn) < abs(m[n]) and n < 15` guard work correctly — which revealed that `square(x) = x*x` is a divergent series incompatible with Euler acceleration.
2. The ManBoy `VerifyError` was pre-existing.

`recursion_euler_test` is now genuinely fixed; ManBoy remains under active repair.
