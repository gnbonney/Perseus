## Plan: Fix ManBoy (M13E Remaining Tasks)

Three independent bugs prevent ManBoy from producing -67. Fix in order of dependency.

---

### Phase 1 — Fix procedure call dispatch

**Bug:** `generateExpr` ProcCallExprContext checks `varType.startsWith("procedure:")` first. Since all user procedures are also in the symbol table as `"procedure:real"`, they get routed through `generateProcedureVariableCall` → `invokeinterface` instead of `invokestatic`.

**Fix:** Move `procedures.containsKey(procName)` check BEFORE the varType check. Only dispatch to `generateProcedureVariableCall` if the name is NOT in the `procedures` map.

**File:** CodeGenerator.java — ProcCallExprContext branch in `generateExpr` (~line 1833–1844)

---

### Phase 2 — Fix i2d coercion for thunk-integer operands in real arithmetic

**Bug:** In procedure A, `x4 + x5` — both are `thunk:integer` so unboxing produces int. TypeInferencer marks the `+` as real (because the if-then-else branch B returns real), so `dadd` is generated with no `i2d` bridge → VerifyError.

**Fix:** After unboxing a thunk value, if the result arithmetic type is real but the unboxed type is int, emit `i2d`. Likely the existing guard in the AddExpr/SubExpr block checks `leftType`/`rightType` but isn't triggering — read the exact block and patch.

**File:** CodeGenerator.java — AddExpr/SubExpr codegen in `generateExpr` (~line 1600–1650)

---

### Phase 3 — Fix non-local variable access

**Bug:** `generateLoadVar` checks `currentLocalIndex` → falls back to static field. When nested procedure B accesses A's locals (k, x1–x5), those slots are in `mainLocalIndex` but `generateLoadVar` never checks it. Same gap in `exitAssignment` stores.

**Fix — loads:** After `currentLocalIndex.get(name)` misses, check `mainLocalIndex.get(name)` (using `mainSymbolTable` for type) before falling back to static.

**Fix — stores:** Same fallback order in `exitAssignment`: currentLocalIndex → mainLocalIndex → putstatic.

**Files:** CodeGenerator.java — `generateLoadVar` (~line 1221) and `exitAssignment` store section (~line 568–600)

---

### Phase 4 — Strengthen test assertion

Verify the exact output format of `outreal(-67.0)` then change `manboy_test` from `output.length() > 0` to `assertEquals("...", output.trim())`.

**File:** PerseusCompilerTest.java — `manboy_test`

---

### Steps (ordered)

1. Read + fix ProcCallExprContext dispatch (Phase 1) — independent
2. Read + trace + fix AddExpr i2d coercion (Phase 2) — independent of Phase 1
3. Read + fix `generateLoadVar` non-local loads + `exitAssignment` stores (Phase 3) — *depends on Phase 1+2 working at runtime*
4. Run `gradle test` — 49 tests pass, ManBoy outputs -67
5. Strengthen manboy_test assertion (Phase 4)
6. Commit + update Compiler-TODO.md

Phases 1 and 2 are parallelizable (static code edits); Phase 3 requires runtime execution to get past B().

---

**Verification**
1. Inspect generated `ManBoy.j`: Phase 1 → `invokestatic`; Phase 2 → `i2d` before `dadd`; Phase 3 → `iload`/`aload` for B's references to A's slots
2. `gradle test` — all tests pass with no regressions
3. ManBoy output strictly equals `-67.0` (or the exact `outreal` format)

**Scope:** Only CodeGenerator.java changes + one test assertion. No SymbolTableBuilder, TypeInferencer, or grammar changes needed.

