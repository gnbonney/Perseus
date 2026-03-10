## Plan: Milestone 14 — recursion_euler.alg compile + run

**TL;DR**: Six features are genuinely new. Named `end` is already free. Work proceeds in 3 sequential phases, with most steps within each phase parallelizable.

---

### Phase 1: Grammar (Algol.g4) — all 4 steps independent

1. **Optional array type prefix** — change `(INTEGER | REAL | BOOLEAN | STRING | PROCEDURE) ARRAY` to `(INTEGER | REAL | BOOLEAN | STRING | PROCEDURE)? ARRAY`. Bare `array` defaults to `real`.

2. **Typed procedure paramSpec** — `real procedure fct;` currently fails to parse because `paramSpec` only accepts a single type keyword. Add a `paramSpecType` subrule that also handles `REAL PROCEDURE`, `INTEGER PROCEDURE`, `STRING PROCEDURE` as combined two-token types.

3. **For-list with multiple elements** — refactor `forStatement` to use `forList : forElement (',' forElement)*` with labeled alternatives `# StepUntilElement`, `# WhileElement`, `# SimpleElement`. Current grammar only supports one for-element.

4. **If-as-expression** — add `| IF expr THEN expr ELSE expr # IfExpr` to the `expr` rule (ELSE is mandatory in expression form).

After Phase 1: `gradle generateGrammarSource`.

---

### Phase 2: SymbolTableBuilder + TypeInferencer — steps 5–7 independent

5. **`enterArrayDecl`** in SymbolTableBuilder.java — if type token is null, register as `real[]`.

6. **`enterParamSpec`** in SymbolTableBuilder.java — replace `ctx.getStart().getText()` with the new `paramSpecType` context; map `REAL PROCEDURE` → `procedure:real`, etc.; mark procedure-type params as `valueParams`.

7. **TypeInferencer** — handle `IfExprContext`, result type = type of the `THEN` branch.

---

### Phase 3: Codegen — steps 8–11 independent; step 12 depends on 11

8. **`enterArrayDecl`** in CodeGenerator.java — handle null type → `real[]`.

9. **For-list codegen** in StatementGenerator.java — replace the enter/exit split with a single-pass `generateForStatement()` helper. **Inline body duplication per element**: `SimpleElement` assigns and runs the body once then falls through; `WhileElement` generates a loop with assign/test/body/goto; `StepUntilElement` reuses the existing step/until pattern.

10. **`IfExpr` codegen** in ExpressionGenerator.java — add `IfExprContext` case: emit `<cond>; ifeq else_L; <then_expr>; goto end_L; else_L: <else_expr>; end_L:`. Use `exprTypes` to choose int vs double.

11. **Argument boxing** in ProcedureGenerator.java `generateProcedureVariableCall` — replace the hardcoded `iconst_0 / anewarray` with a loop over `args`: push array, box each arg (`Integer.valueOf(I)` or `Double.valueOf(D)`), aastore, then invokeinterface.

12. **ProcRef arg unboxing** in ProcedureGenerator.java `generateProcedureReference` — when the wrapped procedure has params, emit unboxing code in the `invoke()` body (`aaload → checkcast Integer/Double → intValue()/doubleValue()`). Update `.limit locals` and the `invokestatic` descriptor. *Depends on step 11.*

---

### Phase 4: Test file + test

13. **Fix + expand recursion_euler.alg** — add `t` to `integer i, k, n, r, t;`; wrap in a `begin...end` main block with a simple `real procedure square(x); real x; begin square := x * x end;` and a call to `euler(square, 0.001, 5)` with `outreal` output.

14. **Add `recursion_euler_test`** to AntlrAlgolListenerTest.java asserting compile + correct output.

---

### Verification
1. `gradle generateGrammarSource` — no ANTLR errors
2. `gradle build -x test` — no Java compile errors
3. `gradle test` — 44 existing tests stay green, new test passes
4. Named `end euler` already handled by the `endComment` grammar rule — no work needed there

---

**Decisions**
- Named `end` is already done — the `endComment : IDENT` catch-all rule handles it
- For-list codegen uses **inline body duplication** per element (simple, avoids shared-label dispatch complexity)
- ProcRef arguments use Java boxing/unboxing via `Integer.valueOf`/`Double.valueOf`

**Biggest risk** — Step 12 (ProcRef with args) is the highest-complexity unit. If it proves unexpectedly difficult, M14 can be split: first milestone gets everything compiling (no-crash test), second adds runtime assertion.