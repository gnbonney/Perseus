# Man or Boy VerifyError Analysis

This document captures the current state of debugging `manboy.alg` in JAlgol, based on the recent Grok debugging output and the implementation artifacts in `build/test-algol/ManBoy.j`.

## Symptom

- `VerifyError` when loading method `A`:
  - `VerifyError: ... method: A ... Expecting to find object/array on stack`
  - `expected: <0> but was: <1>`

This is a JVM operand stack type mismatch in the body of method `A`.

## Root cause (as identified by Grok)

### Bug in `else_2` branch of `A`

Generated Jasmin currently contains:

```jasmin
else_2:
  getstatic .../__proc_B Lgnb/jalgol/compiler/RealProcedure;
  checkcast gnb/jalgol/compiler/RealProcedure
  iconst_0
  anewarray java/lang/Object
  invokeinterface gnb/jalgol/compiler/RealProcedure/invoke([Ljava/lang/Object;)D 2
  pop2
endif_0:
```

- `B.invoke` returns a `double`.
- `pop2` removes the double result (incorrect for this language semantics).
- The method then falls through to common epilogue where a `dreturn` is expected. This leaves the stack empty (or mismatched), causing verifier failure.

### Semantics in `manboy.alg`

```
if k <= 0 then A := x4 + x5 else B
```

- else-case must return `B` result (as double) from `A`.
- current code discards the `B` result and returns stale `0.0` from local variable slot.

### Additional `then_1` branch issue

- computed `x4 + x5` is stored to `astore 8` (object) and assigned to `__proc_A` (procedure ref) incorrectly
- sum is never returned; result flow is incorrect.

### Deep architectural root cause

Current implementation uses global static environment fields:

- `__env_A_k`, `__env_A_x1`..`x5`, `__proc_A`, `__proc_B`

But Man-or-Boy requires per-activation environments for recursive calls:

- each call to `A` must preserve its own `k` and thunk bindings
- `B` is redefined per `A` activation and must capture the current `A` environment
- global statics are overwritten by nested recursion and clobber dynamic chain

Result: generated code is not valid for nested closures + recursion.

## Immediate fix (verification unblock)

1. remove `pop2` from `else_2` branch in method `A`
2. ensure the `double` from `B` remains on stack as return value
3. adjust control flow so both branches converge to common epilogue with return value available

## Medium fix (return value semantics)

- in `then_1`, compute real value `x4 + x5` and leave it on stack (instead of storing into `__proc_A`)
- convert deferred calls to `x4`, `x5` into double values and `dadd`
- make sure both branches return one double for `A`.

## Real fix (proper nested procedure closure model)

Options:

- implement `A` as a closure object with per-activation fields (`k`, `x1`..`x5`, `B` thunk)
- `B` as an inner closure object capturing enclosing `A` instance
- avoid globals for environment; use activation stack or heap frames

This is the critical fault line for the full `manboy_test` (current failing case).

## Current status

- `thunk_closure_isolation_test` now passes, after earlier procedure-variable fix.
- `manboy_test` still fails due to the above `A`/`B` closure flow issues.

## Next steps

- Apply the immediate syntax fix to `else_2` in generator path
- Add a regression test that asserts `manboy_alg` can compile and run to `-67.0`
- Refactor environment management to per-activation closures in `CodeGenerator`/`ProcedureGenerator`

---

### Notes

- The existing `ManBoy.j` has `; ERROR: undeclared variable x1` warnings inside generated `B()` path, confirming scope capture is not working.
- Validate closure semantics by comparing generated code to known implementations with per-activation state (e.g. academic Algol-manboy references).
