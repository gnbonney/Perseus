## Plan: Milestone 15 — pi2.alg

**TL;DR**: The Compiler-TODO.md's listed new features for Milestone 15 are both already implemented. The **only real new feature** needed is non-local scalar variable access from procedures. The fix is promoting outer-scope scalar variables to static class fields — consistent with how arrays have worked since Milestone 10.

---

### Root Cause

`archprint` is a void, zero-parameter procedure that reads outer `a` and `b` directly (`outreal(1, (a / 2))`). Since procedures are lifted to separate JVM static methods, they cannot access `main()`'s local variable table. `generateExpr(VarExprContext)` in CodeGenerator.java does `currentLocalIndex.get(name)` → null → emits `; ERROR: undeclared variable`. There is **no fallback**.

`archimedes(a, b)` (CBN call) is completely fine — the existing box/thunk mechanism handles it.

---

### What's Already Working (nothing to do)
- `procedure archprint;` — grammar already has `('(' paramList? ')')?` (whole parens optional)
- `archprint;` — zero-arg call already supported
- `end archimedes` — named ends already treated as comments
- CBN real parameters (`real a, b;` with no `value`) — Milestone 12 thunks handle this
- for-loop, sqrt, outreal, outstring — all working

---

### Steps

**Phase 1 — Promote outer-scope scalars to static class fields** (all in CodeGenerator.java):

1. **`enterProgram` field declarations** — add `.field public static name D/I/Z/Ljava/lang/String;` for all scalars (non-array, non-procedure) in `currentSymbolTable`, alongside the existing array fields
2. **`enterProgram` scalar init** — replace `istore/dstore/astore slot` with `putstatic pkg/Cls/name type`; scalars removed from (or absent from) `currentLocalIndex`
3. **`generateExpr` VarExprContext** — when `currentLocalIndex.get(name) == null` but the var type is in `currentSymbolTable` or `mainSymbolTable`, emit `getstatic` instead of the ERROR comment ← *core fix for `archprint`*
4. **`exitAssignment` scalar store** — when no local slot, emit `putstatic` *(parallel with 3)*
5. **`generateLoadVar` helper** — emit `getstatic` for static-field scalars *(used by CBN box init in `generateUserProcedureInvocation`)*
6. **`generateUserProcedureInvocation` restore section** — post-CBN restore: emit `putstatic` when `varSlot == null`
7. **`ininteger`/`inreal`/`instring` handlers** — use `putstatic` for static-field vars
8. **`exitForStatement` loop variable** — add static-field check for init/increment (`putstatic`/`getstatic`)

Optional: in SymbolTableBuilder.java, don't allocate JVM local slots for outer-scope scalars, which makes the "is this a static field?" check trivial in CodeGenerator.

**Phase 2 — Test + docs:**

9. Add `pi2_test()` to AntlrAlgolListenerTest.java — compile, assemble, run; assert 10 output lines with `> pi >` separator
10. Update Compiler-TODO.md Milestone 15 section with correct description

---

### Verification
1. `gradle build -x test` — clean compile
2. `gradle test --tests "*pi2_test"` — passes, output converges toward π
3. `gradle test` — all 45 existing tests still pass (critical regression check for the static field change)
4. Inspect jen/cbn Jasmin output — box init and restore use `getstatic`/`putstatic` correctly

---

### Key Technical Notes
- **Backward compatible**: existing tests check output values, not Jasmin instructions — switching `dload 0` → `getstatic` doesn't change runtime behavior
- **Multiple assignment** `a := b := expr` with `dup2` + two `putstatic` is valid JVM
- **`main()`'s `.limit locals`** shrinks (no scalar slots), but thunk box slots at call sites still count — `ensureLocalLimit()` still works