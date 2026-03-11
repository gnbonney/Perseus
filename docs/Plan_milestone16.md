## Plan: Milestone 16 — Boolean OR/NOT Operators (boolean_operators.alg)

The goal is simple: boolean_operators.alg compiles and runs with correct `or` and `not` behavior. Three things are missing — grammar tokens, type inference, and codegen.

**Steps**

### Phase 1: Fix sample + grammar

1. **Fix boolean_operators.alg**: add `begin`/`end` wrapper and change `outstring("...")` → `outstring(1, "...")`

2. **Add keyword lexer tokens in Algol.g4**: add `NOT : 'not';`, `AND_KW : 'and';`, `OR : 'or';` placed *before* `IDENT` so they don't tokenize as identifiers

3. **Update `expr` rule in Algol.g4** — reorder for correct Algol 60 boolean precedence (first alternative = lowest in ANTLR4):
   - `expr OR expr  # OrExpr` → first (lowest)
   - `expr ('&' | AND_KW) expr  # AndExpr` → second (currently `&` is wrongly above arithmetic — this corrects it)
   - Existing RelExpr, MulDivExpr, AddSubExpr stay in their relative order
   - `NOT expr  # NotExpr` added as unary prefix alongside `UnaryMinusExpr`

4. Run `gradle generateGrammarSource` to regenerate ANTLR classes

### Phase 2: Type inference + codegen (*parallel steps*)

5. **TypeInferencer.java**: add `exitOrExpr` (→ `"boolean"`) and `exitNotExpr` (→ `"boolean"`), mirroring existing `exitAndExpr`

6. **CodeGenerator.java — `generateExpr()`**: two new cases:
   - `OrExprContext` → `generateExpr(left) + generateExpr(right) + "ior\n"`
   - `NotExprContext` → `generateExpr(e.expr()) + "iconst_1\nixor\n"`

7. **CodeGenerator.java — `collectVarNames()`**: add `OrExprContext` and `NotExprContext` cases following the `AndExprContext` pattern *(parallel with step 5)*

### Phase 3: Test

8. **AntlrAlgolListenerTest.java**: add `boolean_operators_test()` — compile, assemble, run, assert output is `"Boolean logic test passed"`

**Relevant files**
- boolean_operators.alg — fix sample (step 1)
- Algol.g4 — grammar (steps 2–3)
- TypeInferencer.java — step 5
- CodeGenerator.java — steps 6–7
- AntlrAlgolListenerTest.java — step 8

**Verification**
1. `gradle generateGrammarSource` succeeds
2. `gradle build -x test` succeeds
3. `gradle test --tests "*boolean_operators*"` passes
4. `gradle test` — all 46 tests pass (no regressions)

**Decisions**
- Fix the sample file, not grammar, for the missing `begin`/`end` and one-arg `outstring`
- `and` and `&` are synonyms — grammar updated to support both
- Precedence corrected: current `&` is wrongly above arithmetic; correct order (low→high): `or` → `and` → relational → `+`/`-` → `*`/`/` → `not`/unary-minus. Safe to change since no existing test mixes `&` with arithmetic
- `not` uses `iconst_1; ixor` (XOR with 1 flips boolean bit) — minimal instruction sequence

---

## Issues found

### 1. The plan was incomplete — three synonyms are missing

The Representation.md specifies:

| Operator | Primary | Synonyms |
|---|---|---|
| logical and | `and` | `&` |
| logical or | `or` | `\|` |
| logical not | `not` | `~` |

The original plan only added `or`/`not` keywords and the `and` synonym for `&`. It missed `|` as synonym for `or` and `~` as synonym for `not`. These are straightforward grammar additions — `'|'` and `'~'` as character literals in the `expr` rule, no new lexer tokens needed since they're single characters.

### 2. The current boolean_operators.alg has problems

Three issues:
1. **No `begin`/`end`** — it's a bare compound statement, which the grammar doesn't support
2. **`outstring` missing channel argument** — should be `outstring(1, ...)`
3. **Relies on zero-initialization of undefined variables** — All 7 booleans are never assigned. Algol 60 says reading an uninitialized non-`own` variable is *undefined behavior* (see §3.1.5 of the Modified Report). Our JVM static fields happen to default to 0/false, so the expression evaluates to `true` because `not c`, `not f` etc. flip to true — but this is fragile and semantically wrong as a test.

### 3. boolean_operators.alg origin

I don't know where this sample came from. The Modified Report (attached) doesn't contain sample programs for boolean operators in the portions provided — it focuses on formal syntax definitions. **A better replacement** that explicitly initializes variables and verifiably exercises `and`, `or`, `not`:

```algol
begin
    boolean p, q, r;
    p := true;
    q := false;
    r := true;
    if p or q then outstring(1, "or works\n");
    if not q then outstring(1, "not works\n");
    if p and not q and r then outstring(1, "Boolean logic test passed\n")
end
```

This tests all three operators with known values and the final line only prints if all three work correctly.

### 4. No grammar conflicts for `|` and `~`

Neither `|` nor `~` appears anywhere in the current grammar, so adding them as inline character literals (`'|'`, `'~'`) in the `expr` rule is clean and safe in ANTLR4.

---

## Updated plan

The only real change to the plan is adding the two missing synonyms and replacing the sample file. Updated steps 1 and 3:

- **Step 1**: Rewrite boolean_operators.alg with proper `begin`/`end`, explicit initialization, and channel arg on `outstring`
- **Step 3**: Grammar `expr` rule additions:
  - `expr ('|' | OR) expr  # OrExpr`
  - `expr ('&' | AND_KW) expr  # AndExpr`
  - `('~' | NOT) expr  # NotExpr`

Everything else in the plan stands. Should I update the saved plan and start implementation?