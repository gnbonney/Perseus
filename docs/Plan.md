## Plan: Fix 5 Failing Procedure Variable Tests

**Status as of March 9, 2026: 2 of 5 originally failing tests fixed. 41 passing, 3 failing.**

| Test | Was | Now | Milestone |
|------|-----|-----|-----------|
| `proc_var_test` | ❌ VerifyError | ✅ passes | 13A |
| `testGenerateProcedureReferenceAsValue` | ❌ no ProcRef | ✅ passes | 13A/unit |
| `proc_typed_simple_test` | ❌ VerifyError | ❌ wrong output | 13C |
| `proc_param_test` | ❌ empty output | ❌ empty output | 13B |
| `manboy_test` | ❌ Undeclared x1 | ❌ NPE in codegen | 13E |

---

## Completed Work

### Phase 1: SymbolTableBuilder.java ✅
- Replaced `currentProc` field with `Deque<ProcInfo> procStack` — fixes manboy's "Undeclared variable: x1" by correctly maintaining outer procedure context during nested procedure exits.
- Fixed `enterParamSpec`: default `"procedure:real"` → `"procedure:void"` for untyped procedure parameters.
- Fixed `enterParamSpec`: added procedure-type params to `valueParams` so they are passed by-value as ProcRef objects, not as call-by-name thunks.

### Phase 2: AntlrAlgolListener.java ✅
- Removed `enterProcedureCall` override from pre-scan walker (calling a procedure should not add it to `procedureVariables`).
- Removed "safety pass" loop that added all no-param procedures to `mainSymbolTable`.
- Rewrote `enterAssignment` in pre-scan walker to distinguish return-value assignments (typed `P := 3.14`) from procedure-variable assignments (`P := hello`), using the RHS expression type.

### Phase 3: CodeGenerator.java ✅
- Stored `procVarSlots` as a field (was silently discarded before).
- Removed `if (procName.equals("P"))` special-case hack in `enterProcedureDecl`/`exitProcedureDecl`.
- Added self-reference slot for procedure variables in `enterProcedureDecl`.
- Fixed local-var init loop to emit `aconst_null; astore` for `procedure:` types.
- Fixed `exitAssignment`: when `name.equals(currentProcName)` and the lvalue type is `procedure:`, falls through to the `astore`-slot path rather than the retval-store path.
- Fixed `generateExpr` VarExprContext: checks local slot before `procedures` map, so a self-reference slot load takes priority over generating a fresh ProcRef.
- Fixed `exitProcedureCall`: only routes through proc variable slot when inside a procedure body (not in main scope).
- Extended paramDesc builders in `enterProcedureDecl` and `generateUserProcedureInvocation` to map `procedure:*` types to their JVM interface descriptors.
- Fixed `generateProcedureVariableCall` stack order: `checkcast` before argument array push.
- Added null initialization for `procedure:` type slots in `enterProgram` (main method).

### Phase 4: proc_typed_simple.alg ✅
- Added `; P` before the outer `end` so the procedure P is actually called from main (previously relied on the removed P-hack).

### Phase 5: ExpressionGenerator.java ✅
- Added fallback to `context.getProcedures()` in `generateExpr` VarExprContext when symbol table lookup returns null, enabling the unit test to work with procedures registered only in the procedures map.

### New: Runtime procedure interfaces ✅
- Created `VoidProcedure`, `IntegerProcedure`, `RealProcedure`, `StringProcedure` Java interfaces in `gnb.jalgol.compiler`.
- Added `test.doFirst` in `build.gradle` to copy interface `.class` files to `build/test-algol` so assembled programs can load them at runtime.

---

## Remaining Failures

### Fix A: proc_param (13B) — procedure parameters not getting JVM slots

**Root cause:** `callIt` declares `procedure P;` as a formal parameter. `SymbolTableBuilder` correctly records P's type as `procedure:void` in `callIt`'s `ProcInfo.paramTypes`. However, `CodeGenerator.enterProcedureDecl` builds `callIt`'s JVM frame from `info.paramNames`, and when it encounters a `procedure:`-type param, it assigns a JVM slot for it correctly — BUT `generateUserProcedureInvocation` sees that `callIt` has a `procedure:void` value param and tries to push a ProcRef for `hello` before invoking `callIt`. Currently the code falls through to a thunk or unknown path instead of generating `new ProcRef0; dup; invokenonvirtual ProcRef0/<init>()V` for the procedure argument.

Generated code has `; unknown procedure: P` in `callIt()V`, meaning `P` is not found in `currentSymbolTable` during the `P;` call inside `callIt`'s body — its slot isn't visible there.

**Fix needed:** In `CodeGenerator.exitProcedureCall`, when inside a procedure, also check `currentLocalIndex` (not just via the `isInProc` guard) for procedure-typed params. The check already exists but `P`'s slot is not being populated. Investigation needed: confirm that `enterProcedureDecl` correctly adds `procedure:` params to `procLI` with a proper slot, and that `exitProcedureCall` routing uses that slot.

### Fix B: proc_typed_simple (13C) — `ldc2_w` float precision via Jasmin

**Root cause:** The test expects `3.141590.0` but gets `3.1415901184082030.0`. The value `3.14159011840820...` is exactly what `3.14159` looks like when parsed as a 32-bit `float` and then widened to `double` — a classic float/double precision mismatch. Jasmin 2.4 appears to parse `ldc2_w 3.14159` using `Float.parseFloat` rather than `Double.parseDouble`.

**Fix needed:** In `CodeGenerator.generateExpr` for `RealLiteralExprContext`, emit `ldc2_w` with a value that forces Jasmin to use double precision. Options:
1. Append `d` suffix: `ldc2_w 3.14159d` (if Jasmin 2.4 supports it)
2. Ensure the literal has enough trailing digits to exceed float precision (e.g. `3.141590000000000`)
3. Use `Double.toHexString` notation if Jasmin supports it

Needs a quick Jasmin format test to confirm which suffix works.

### Fix C: manboy (13E) — nested procedure overwrites outer `procBuffer`

**Root cause:** `NullPointerException: Cannot invoke StringBuilder.toString() because this.procBuffer is null` in `exitProcedureDecl`. Manboy has procedure B nested inside procedure A. When A's `enterProcedureDecl` fires, `procBuffer = new StringBuilder()`. When B's `enterProcedureDecl` fires inside A's body, it replaces `procBuffer` with a new `StringBuilder`, discarding A's accumulated content. When B exits, `procBuffer` (B's buffer) is saved and set to null. When A exits, `procBuffer` is null — crash.

**Fix needed:** Replace the single `procBuffer` field with a `Deque<StringBuilder>` stack in `CodeGenerator`, mirroring the `procStack` fix done in `SymbolTableBuilder`. On `enterProcedureDecl`, push a new buffer; on `exitProcedureDecl`, pop and save it. `activeOutput` should always point to the top of the stack (or `mainCode` when the stack is empty).

**Note:** Even after this fix, manboy's B procedure references A's parameters (k, x1-x5) as non-local variables — a scope/display problem beyond the current flat-JVM-static-method model. Full manboy execution (result = -67) is a Future Milestone item.

---

## Dependency Order

```
13A (proc_var) ✅
  └── 13B (proc_param) ← Fix A needed
  └── 13C (proc_typed_simple) ← Fix B needed
        └── 13E (manboy) ← Fix C needed + non-local access (future)
```

Fix A and Fix B are independent and can be done in parallel. Fix C blocks on A and B being green first.

---

**Decisions (unchanged)**
- The `name.equals("P")` hack is permanently removed.
- Procedure-type parameters are value params (ProcRef by-value), not call-by-name thunks.
- Pre-scan "safety pass" removed (was explicitly temporary).
- `proc_typed_simple.alg` requires an explicit outer `P;` call.