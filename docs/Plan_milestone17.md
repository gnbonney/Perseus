## Plan: Milestone 17 — Real Arrays

Most of the codegen is already in place (`newarray double`, `daload`, `dastore`, lower-bound offset subtraction). Only three gaps need to be closed.

**Steps**

### Phase 1 — Fix real_array.alg
1. Wrap the file in `begin` … `end` — the grammar's `program` rule requires it (`program: BEGIN compoundStatement END`). The existing `outreal(q[-7])` calls without a channel are fine — the codegen already handles the optional channel via `args.size() > 1 ? args.get(0) : null`.

### Phase 2 — Extend Grammar (Algol.g4)
2. Add a `signedInt : '-'? unsignedInt ;` rule (reuses the existing `unsignedInt` rule).
3. Update the Algol.g4 to use `signedInt ':' signedInt` for bounds instead of `unsignedInt ':' unsignedInt`.
4. Run `gradle generateGrammarSource` to regenerate the ANTLR parser. *(depends on steps 2–3)*

### Phase 3 — Update SymbolTableBuilder (SymbolTableBuilder.java)
5. In `enterArrayDecl` ([around line 203](src/main/java/gnb/jalgol/compiler/SymbolTableBuilder.java#L203)), replace `ctx.unsignedInt(0)` / `ctx.unsignedInt(1)` with `ctx.signedInt(0)` / `ctx.signedInt(1)`. *(depends on step 4)*
6. Use `Integer.parseInt(ctx.signedInt(i).getText())` — Java's `parseInt` correctly handles `"-7"` and `"2"`. No special sign-stripping logic needed.

### Phase 4 — Verify Codegen (read-only, no changes expected)
7. Confirm CodeGenerator.java emits `newarray double` for `real[]` allocations.
8. Confirm `dastore` is emitted in `exitAssignment` for a `real[]` lvalue.
9. Confirm `daload` is emitted in `generateExpr` for `ArrayAccessExpr` of type `real[]`.
10. Confirm the lower-bound offset emits `ldc lower; isub` — `ldc -7` is valid Jasmin.

### Phase 5 — Add Test (AntlrAlgolListenerTest.java)
11. Add `real_array_test()` after `array_test()` (around AntlrAlgolListenerTest.java), following the same pattern:
    - Compile real_array.alg → class name `RealArrayTest`
    - Assert Jasmin source `contains("newarray double")` and `contains("daload")`
    - Assemble and run; assert output `contains("1.23")` and `contains("4.56")`

### Phase 6 — Update Docs
12. Mark M17 ✓ COMPLETE in Compiler-TODO.md, update test count.

---

**Relevant files**
- real_array.alg — add `begin`/`end`
- Algol.g4 — `signedInt` rule + `arrayDecl` bounds
- SymbolTableBuilder.java — `enterArrayDecl` context references
- CodeGenerator.java — read-only verify only
- AntlrAlgolListenerTest.java — new `real_array_test()`
- Compiler-TODO.md — status update

**Verification**
1. `gradle generateGrammarSource` — no ANTLR errors
2. `gradle build -x test` — compiles cleanly
3. `gradle test` — all prior tests pass + `real_array_test` green
4. Manual: `RealArrayTest` output is `1.234.56` (two `print(double)` calls, no newlines)

**Decisions**
- Keeping `[-7:2]` — negative lower bound exercises the non-zero offset path with a real-world edge case
- Test asserts both Jasmin source (`newarray double`, `daload`) and runtime output (`contains("1.23")`, `contains("4.56")`) for richer coverage
- `outreal` without channel already works — no change needed

**Out of scope**
- Multi-dimensional arrays; real arrays as procedure parameters; negative bounds for integer arrays