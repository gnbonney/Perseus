## Plan: String Variables — M11C.2, M11C.3, M18

**TL;DR:** Much more string infrastructure is already in place than the TODO implied. Grammar, symbol table, assignment codegen, `outstring`, and `instring` are all **already working**. The actual gaps are only: `length()`, `substring()`, `concat()` built-in functions, `s[i]` character access (1-based, RHS), `s[i] := expr` character mutation (LHS), a rewritten string_output.alg, and one new test method.

---

**Steps**

### Phase 1 — Baseline
1. Run `gradle test` to confirm 30/30 (ensures a clean start).

### Phase 2 — TypeInferencer: string function types (*depends on nothing*)
2. In TypeInferencer.java `getBuiltinFunctionType()` (~line 182), add three cases to the switch:
   - `"length"` → `"integer"` 
   - `"substring"` → `"string"`
   - `"concat"` → `"string"`

### Phase 3 — CodeGenerator: built-in string functions (*parallel with step 2*)
3. In CodeGenerator.java `generateBuiltinMathFunction()` (~line 1805), add cases:
   - `length(s)`: generate `s` expr + `invokevirtual java/lang/String/length()I`
   - `substring(s, start, end)`: generate `s` + `(start-1)` + `end` + `invokevirtual java/lang/String/substring(II)Ljava/lang/String;` (Algol 1-based → Java 0-based start)
   - `concat(s1, s2)`: generate `s1` + `s2` + `invokevirtual java/lang/String/concat(Ljava/lang/String;)Ljava/lang/String;`

### Phase 4 — CodeGenerator: `s[i]` character access — RHS (*depends on 3*)
4. In `generateExpr()` `ArrayAccessExprContext` branch (~line 1743), add a **string-scalar guard** before the existing `getstatic`/`[a|i|d]load` logic: if `lookupVarType(arrName).equals("string")` (not ending in `[]`), emit via `generateLoadVar(arrName)` (handles both `aload` for locals and `getstatic` for statics) + index expr + `iconst_1, isub, dup, iconst_1, iadd` + `invokevirtual java/lang/String/substring(II)Ljava/lang/String;`. Existing array path only runs when type ends with `[]`.

### Phase 5 — CodeGenerator: `s[i] := expr` character mutation — LHS (*depends on 3*)
5. In the `exitAssignment()` subscript block (~line 436, `lvalues.size()==1 && lvalue.expr()!=null`), add a **string-scalar guard** before the `getstatic`/`aastore` logic: if `lookupVarType(arrName).equals("string")`, emit a StringBuilder reconstruction:
   - `new java/lang/StringBuilder` / `dup` / `invokespecial`
   - **Prefix**: load `s`, push `0` (iconst_0), load index, `iconst_1, isub` → `String.substring(II)` → `StringBuilder.append`
   - **Replacement**: generate RHS expr → `StringBuilder.append`
   - **Suffix**: load `s` again, load index → `String.substring(I)` → `StringBuilder.append`
   - `StringBuilder.toString()` → store back using `astore`/`putstatic` based on `currentLocalIndex` lookup.

### Phase 6 — Rewrite string_output.alg (*parallel with 2-5*)
6. Replace contents of string_output.alg with the design doc example adapted for double-quoted strings. Note: grammar only supports `"double-quoted"` strings, not `'single-char'`. Fix the design doc's indexing typo: use `s[8] := "W"` (not `s[7]`) to correctly change `'w'` → `'W'` in `"Hello, world!"` (1-based position 8 = `w`). Expected output: `Hello, world! 13 H world Hello, World! Hello, World!!!`

### Phase 7 — Add test method (*depends on 3–6*)
7. Add `@Test void stringOutputTest()` to AntlrAlgolListenerTest.java: compile → assemble → run `StringOutput`, assert output contains `"Hello, world!"`, `"13"`, `"H"`, `"world"`, `"Hello, World!"`, `"Hello, World!!!"`.

### Phase 8 — Update Compiler-TODO.md
8. Mark all M11C.2, M11C.3, and M18 checklist items as `[x]`; note that grammar was already complete and `instring` was already implemented.

---

**Relevant files**
- TypeInferencer.java — `getBuiltinFunctionType()` (~line 182)
- CodeGenerator.java — `generateBuiltinMathFunction()` (~line 1805), `generateExpr()` ArrayAccessExprContext (~line 1743), `exitAssignment()` subscript block (~line 436)
- string_output.alg — rewrite
- AntlrAlgolListenerTest.java — add `stringOutputTest()`
- Compiler-TODO.md — checkbox updates

**Verification**
1. `gradle test` → 31/31 pass
2. Inspect StringOutput.j for `String/length`, `String/substring(II)`, `String/concat`, `StringBuilder/toString`

**Decisions**
- **Grammar**: No changes — `STRING` is already fully integrated in all production rules
- **instring (M11C.3)**: Already implemented in CodeGenerator.java; only a TODO checkbox update needed
- **Character mutation `s[i] :=`**: Included in M18 (per your answer)
- **string_output.alg**: Rewritten using design doc example (per your answer)
- **`'W'` single-char literals**: Rendered as `"W"` in test program (grammar only supports double-quotes)
- **`s[i]` result type**: Returns 1-char string via `String.substring(i-1, i)` — compatible with `outstring` and string assignment