# Compiler Development TODO

This list follows an iterative, depth-first approach: get each sample program
fully compiling and running before expanding to the next. Each milestone produces
a real, executable class file — not just a parse tree or a Jasmin text skeleton.

 ---

## Current Status


**51/53 tests passing as of March 21, 2026.** Current failing tests:

- `AntlrAlgolListenerTest.manboy_test()` (ASM verification fails due deferred/boxed primitives path)
- `AntlrAlgolListenerTest.primer2()` (existing goto/label handling regression)

`AntlrAlgolListenerTest.deferred_typing_test()` is now passing after fixing its thunk dispatch path.

The `thunk_closure_isolation_test` issue has been fixed with procedure variable call stack balance corrections and no more `VerifyError` for void procedure variable call paths.

**Empirical bytecode verification (ASM) is now integrated for `manboy_test`.**

**Root cause:** ASM CheckClassAdapter reports a JVM type error: primitive doubles are being stored directly in `Object[]` arrays for procedure argument passing, and call-by-name deferred typing path may currently emit unboxed `Integer`/`Double` mixing that triggers runtime `ClassCastException` in thunk wrappers.

**Next steps:**
1. Ensure codegen boxes all primitives before storing in `Object[]` for procedure calls (see `ExpressionGenerator`/`StatementGenerator`).
2. Fix deferred-typing thunk dispatch in `ProcedureGenerator`/`CodeGenerator` so `BaseType.REAL`/`INTEGER` handling is consistent from thunk creation to `eval()/set()` extraction.
3. Re-run ASM verification and confirm `manboy_test` and `deferred_typing_test` pass.
4. See `docs/ManBoy-Debugging.md` for full analysis and findings.

### Resolved Issues

- ✅ **ParseTreeWalker / procedure inlining** — procedures are now correctly generated as separate static methods via the two-pass architecture.
- ✅ **CodeGenerator.java size limit** — refactored into modular delegation architecture; `CodeGenerator` delegates to `ExpressionGenerator`, `StatementGenerator`, and `ProcedureGenerator`.
- ✅ **Procedure variable support** — procedure variables, typed procedure references, and procedure parameters all working. `procBufferStack` supports nested procedure declarations.
- ✅ **For-list body capture** — for-list codegen uses `forBodyStack` (Deque) to capture the body once and inline it per element; eliminates label corruption in nested for-loops.
- ✅ **Real comparisons** — `RelExprContext` codegen now dispatches on operand types: `dcmpg + iflt/le/gt/ge` for real, `if_icmpxx` for integer.
- ✅ **String variable support** — grammar, `SymbolTableBuilder`, `TypeInferencer`, and `CodeGenerator` all handle `string` variables; `concat`, `length`, and `substring` built-ins implemented in `BuiltinFunctionGenerator`; `string_output.alg` passes.
- ✅ **`ProcedureGenerator` delegation wired** — `generateProcedureReference` and `generateProcedureVariableCall` now delegate to `ProcedureGenerator` via callback injection; `CodeGenUtils` consolidated with `scalarTypeToJvmDesc`, `getReturnInstruction`, and fixed `boolean[]`/default-return-type bugs.

---

## Architecture: Two-Pass Compilation

The compiler will use (at minimum) two passes over the parse tree:

**Pass 1 — Symbol table construction:** Walk the parse tree and collect all
variable declarations, their types, and their scope (block nesting level).
This is required before code generation because:
- Jasmin requires `.limit locals N` *before* the method body — you must know
  the total number of locals before emitting any instructions.
- Forward `goto` references require knowing all labels in scope before emitting jumps.
- Nested procedure declarations need to be lifted to static methods, which
  requires knowing procedure signatures before their call sites are emitted.

**Pass 1.5 — Type inference:** Walk the parse tree between symbol table construction and code generation, annotating every expression node with its resolved type. Required because `CodeGenerator` must select different JVM instructions depending on expression type (e.g. `iadd` vs. `dadd`).

**Pass 2 — Code generation:** Walk the parse tree a third time using the symbol table from Pass 1 and the type annotations from Pass 1.5 to emit correct Jasmin instructions.

The symbol table pass is not needed for Milestone 1 (no variables, no labels)
but is required from **Milestone 2** onward. It should be designed and
implemented as part of Milestone 2 before any variable codegen is attempted.

See also: [Development.md](Development.md) (Symbol Tables section) and the
ANTLR discussion linked there.

---

### Design Decision: Separate Listener Classes for Two-Pass Compilation

For Milestone 2 and beyond, we will use **separate listener classes**:
- `SymbolTableBuilder`: First pass, walks the parse tree to build the symbol table (variables, types, scopes).
- `CodeGenerator`: Second pass, walks the parse tree again to emit Jasmin code using the symbol table.

**Rationale:**
- Clear separation of concerns (symbol analysis vs. code generation)
- Easier to test, debug, and extend each pass independently
- Facilitates future expansion (adding IR, diagnostics, or more passes)
- Symbol table logic can be reused for analysis, linting, or tooling
- Aligns with best practices for modular, AI-friendly compiler design

This approach may require more initial setup, but it will pay off as the compiler grows in complexity.

---

### Design Decision: TypeInferencer Pass (Pass 1.5)

Starting at **Milestone 4** (when `integer` variables are introduced alongside `real`), a third pass is required between `SymbolTableBuilder` and `CodeGenerator`:

- `TypeInferencer`: Walks the parse tree, annotates every expression node with its resolved type (`integer` or `real`), and enforces the type rules of the Algol 60 Modified Report.

**Why a separate pass?** The `CodeGenerator` must select different JVM instructions depending on expression type (e.g. `iadd` vs. `dadd`). Those types must be resolved before codegen begins, and the logic is complex enough to warrant its own class.

**Type rules from §3.3.4 of the Modified Report:**

| Expression | Operand types | Result type |
|---|---|---|
| `a + b`, `a - b`, `a × b` | both `integer` | `integer` |
| `a + b`, `a - b`, `a × b` | either is `real` | `real` |
| `a / b` | any combination | always `real` |
| `a ÷ b` | must both be `integer` (type error otherwise) | `integer` |
| `a ↑ b` | both `integer` | `integer` |
| `a ↑ b` | either is `real` | `real` |
| `if B then E1 else E2` | either branch is `real` | `real` |

**Assignment coercion rules from §4.2.4:**
- `integer` → `real`: silent widening
- `real` → `integer`: automatic transfer function ⌊E + 0.5⌋ (round-half-up, **not** Java's truncating `(int)` cast — requires `Math.floor(E + 0.5)`)
- `Boolean` ↔ arithmetic: **disallowed** (type error)
- All destinations in a multiple-assignment (`a := b := expr`) must share the same type

---

## Milestone 1 — Hello World (`hello.alg`)

**Goal:** `hello.alg` compiles to a `.class` file that runs and prints `Hello World`.

**Grammar already handles:** `begin`/`end`, `comment`, a single procedure call with
integer and string arguments.

- [x] Emit Jasmin for `outstring(channel, str)` — maps to `java/io/PrintStream.print(Ljava/lang/String;)V` via `System.out`
- [x] Wire Jasmin output to a file (currently returns a String; write `<ClassName>.j` to disk)
- [x] Invoke Jasmin assembler on the output file to produce a `.class` file
- [x] Run the `.class` file and capture stdout in the test
- [x] Update `AntlrAlgolListenerTest.hello()` to assert the output is `"Hello World"`

---

## Milestone 2 — Variables and Assignment (`primer1.alg`)

**Goal:** `primer1.alg` compiles and runs, producing correct real-valued results.

**⚠ Requires symbol table pass (see Architecture section above)**

**New features needed:**
- [x] Grammar: `real` variable declarations (`real x, y, u;`)
- [x] **Design and implement symbol table** — first-pass visitor that collects variable names, types, and block scope
- [x] **Implement two-pass compile in `AntlrAlgolListener`** — split into separate `SymbolTableBuilder` and `CodeGenerator` listener classes
- [x] Grammar: assignment statement (`:=`)
- [x] Grammar: arithmetic expressions (`*`, `-`, `+`, `/`)
- [x] Grammar: real number literals
- [x] Codegen: declare local variables in Jasmin (`.limit locals`)
- [x] Codegen: load/store local variables (`dload`, `dstore` for `real` → JVM `double`)
- [x] Codegen: arithmetic instructions (`dmul`, `dsub`, `dadd`, `ddiv`)
- [x] Codegen: integer-to-real coercion (`5/13` in Algol is real division)
- [x] Test: assert final values of `x` and `y` are correct

---

## Milestone 3 — `goto` and Labels (`primer2.alg`)

**Goal:** `primer2.alg` parses and compiles (infinite loop — run to confirm no crash on structure).

**New features needed:**
- [x] Grammar: label declarations (`AA:`)
- [x] Grammar: `goto` statement
- [x] Codegen: emit Jasmin labels and `goto` instructions
- [x] Test: assert successful compilation (execution not checked — infinite loop)

---

## Milestone 4 — `if`/`then` (`primer3.alg`)

**Goal:** `primer3.alg` compiles and terminates after 1000 iterations.

**⚠ Introduces mixed `integer`/`real` types — requires TypeInferencer pass (see Architecture section above)**

**New features needed:**
- [x] Grammar: `integer` variable declarations
- [x] Grammar: `if <expr> then <statement>` (no `else`)
- [x] Grammar: comparison operators (`<`, `>`, `<=`, `>=`, `=`, `<>`)
- [x] **Implement `TypeInferencer` pass** — annotates every `expr` node with its resolved type before codegen
- [x] TypeInferencer: enforce `+`/`-`/`×` → `integer` iff both operands `integer`, else `real`
- [x] TypeInferencer: enforce `/` → always `real`; `÷` → `integer` only if both operands `integer`
- [x] TypeInferencer: enforce assignment coercion (`real` → `integer` = ⌊E+0.5⌋, not truncation)
- [x] Codegen: select `iadd`/`dadd` etc. based on inferred type
- [x] Codegen: integer variables (`iload`, `istore`)
- [x] Codegen: conditional jump instructions (`if_icmplt` etc.)
- [x] Test: assert program terminates and produces correct output

---

## Milestone 5 — `for` Loop (`primer4.alg`)

**Goal:** `primer4.alg` compiles and runs 1000 iterations correctly.

**New features needed:**
- [x] Grammar: `for <var> := <expr> step <expr> until <expr> do <statement>`
- [x] Codegen: `for` loop with step/until semantics per the Modified Report
  - Note: `step` and `until` expressions are re-evaluated each iteration
- [x] Test: assert correct final values of `x` and `y`

---

## Milestone 6 — Multiple Statements and `outreal` (`primer5.alg`)

**Goal:** `primer5.alg` compiles and prints the correct approximation of *e*.

**New features needed:**
- [x] Grammar: multiple statements separated by `;` (already partially handled — verify)
- [x] Codegen: `outreal(channel, expr)` — maps to `System.out.print(double)`
- [x] Grammar: chained assignment (`a := b := expr`)
- [x] Test: assert output matches expected value of *e* (≈ 2.7182818...)

---

## Milestone 7 — `if`/`then`/`else` and Boolean (`boolean.alg`)

**Goal:** `boolean.alg` compiles and prints `true`.

**New features needed:**
- [x] Grammar: `boolean` variable declarations
- [x] Grammar: `if <expr> then <statement> else <statement>`
- [x] Grammar: boolean literals (`true`, `false`)
- [x] Codegen: boolean variables (JVM `int`, 0/1)
- [x] Codegen: if/then/else branch instructions
- [x] Test: assert output is `"true"`

---

## Milestone 8 — Integer Arrays (`array.alg`)

**Goal:** `array.alg` compiles and prints correct subscript values.

**New features needed:**
- [x] Grammar: array declarations (`integer array nArr[1:5]`)
- [x] Grammar: subscript expressions (`nArr[i]`)
- [x] Grammar: array bounds (lower bound may be non-zero)
- [x] Codegen: allocate JVM int array (adjusting for non-zero lower bound)
- [x] Codegen: `iaload`, `iastore` with bound offset
- [x] Codegen: `outinteger(channel, expr)` → `System.out.print(int)`
- [x] Test: assert `nArr[5]` prints `5`, `nArr[3]` prints `0` (uninitialized)

---

## Milestone 9 — Nested Blocks and `outinteger` (`oneton.alg`)

**Goal:** `oneton.alg` compiles, calls the `oneton` procedure, and prints 1 through 12.

**New features needed:**
- [x] Grammar: nested `begin`/`end` blocks with their own declarations
- [x] Grammar: `integer procedure` declarations with `value` parameters
- [x] Grammar: procedure call as a statement (already partially handled) and as an expression (assignment from call)
- [x] Grammar: `outinteger(channel, expr)`
- [x] Codegen: procedure declarations as static methods
- [x] Codegen: `outinteger` → `System.out.print(int)`
- [x] Codegen: return value from integer procedure
- [x] Test: assert output is `1\n2\n...\n12\n` and `M` equals 24

---

## Milestone 10 — Sieve of Eratosthenes (`primes.alg`) ✅

**Goal:** `primes.alg` compiles and prints all primes below 1000.

**Features implemented:**
- [x] Grammar: `boolean array` declarations
- [x] Grammar: `and` / `&` boolean operator
- [x] Grammar: `for ... while <expr> do` (while clause in for-list)
- [x] Grammar: void procedures (no return type prefix)
- [x] Codegen: boolean array as JVM `boolean[]` static field
- [x] Codegen: arrays as class-level static fields (enables cross-scope access from procedures)
- [x] Codegen: for-while loops re-assign loop variable before each condition check (Algol 60 semantics)
- [x] Codegen: user-defined void procedure calls via `invokestatic`
- [x] Codegen: `lookupVarType`/`lookupArrayBounds` helpers for outer-scope arrays inside procedures
- [x] Test: assert first 10 primes and last prime (997) in output

---


## Milestone 11A — Math Functions ✅

**Goal:** Implement all standard math functions required by the environmental block.

- [x] Codegen: `sqrt(E)` → `Math.sqrt(double)` (needed for `pi.alg`, `pi2.alg`)
- [x] Codegen: `abs(E)` → `Math.abs(double)`
- [x] Codegen: `iabs(E)` → `Math.abs(int)`
- [x] Codegen: `sign(E)` → inline `E > 0 ? 1 : E < 0 ? -1 : 0`
- [x] Codegen: `entier(E)` → `(int)Math.floor(double)` (true floor, not truncation)
- [x] Codegen: `sin(E)`, `cos(E)`, `arctan(E)` → `Math.sin/cos/atan(double)`
- [x] Codegen: `ln(E)` → `Math.log(double)`
- [x] Codegen: `exp(E)` → `Math.exp(double)`
- [x] Test: `math_functions_test()` validates all math functions
- [x] Test: `pi.alg` compiles and runs, computing π using `sqrt`

**Implementation notes:**
- All math functions handled in `CodeGenerator.generateBuiltinMathFunction()`
- Type inference for built-in functions added to `TypeInferencer.getBuiltinFunctionType()`
- Math functions map directly to `java.lang.Math` static methods via `invokestatic`
- Integer↔real coercion applied automatically to match Java method signatures

## Milestone 11B — Output Procedures ✅


**Goal:** Implement all standard output procedures with channel-aware stream selection.

- [x] Codegen: `outchar(channel, str, int)` — print the character at position `int` in string `str`
- [x] Codegen: `outterminator(channel)` — print a space separator (per Modified Report, used after `outinteger`/`outreal`)
- [x] Test: output_procedures_test validates outchar and outterminator generate correct Jasmin code
- [x] Codegen: **Channel-aware stream selection for output procedures** — implement logic to select `System.out` or `System.err` based on channel parameter, per Environmental-Block.md (channel 0 → `System.err`, channel 1 → `System.out`, others → `System.out`). Warn and default to `System.out` if channel is not a compile-time constant.

**Implementation notes:**
- `outchar(channel, str, int)` uses `String.charAt(I)C` to extract the character, then prints it
- `outterminator(channel)` outputs a space character as a string separator
- **Channel-aware stream selection implemented**: `getChannelStream()` helper method evaluates channel parameter at compile time and selects appropriate stream (channel 0 → `System.err`, channel 1/other → `System.out`). Non-constant channels emit warning and default to `System.out`.
- All five output procedures (outstring, outinteger, outreal, outchar, outterminator) use channel-aware stream selection
- Both procedures work with existing outinteger, outreal, outstring infrastructure

## Milestone 11C — Input Procedures

**Goal:** Implement all standard input procedures.

**Note:** String variables and string I/O (e.g., `instring`, `outstring`) are extensions to Algol 60, not part of the original standard. Implementation of string input procedures depends on first supporting string variables (see Algol Extensions.md).

**Subtasks:**

**11C.1 — Integer and Real Input Procedures (no string dependency):**
- [x] Codegen: `ininteger(channel, var)` → `Scanner.nextInt()` (reads from `System.in`)
- [x] Codegen: `inreal(channel, var)` → `Scanner.nextDouble()` (reads from `System.in`; shared `Scanner` instance)
- [x] Codegen: `inchar(channel, str, var)` — read one character; find its position in `str`

**11C.2 — String Variable Support (prerequisite for string I/O):** ✅
- [x] Grammar: string variable declarations and assignment
- [x] SymbolTableBuilder: track string variables and scope
- [x] TypeInferencer: handle string types and type rules
- [x] Codegen: string operations (assignment, concatenation via `concat`, slicing via `substring`, length via `length`)
- [x] **String variable support follows the design in [Algol Extensions Design.md](Algol%20Extensions%20Design.md).**

**11C.3 — String Input Procedures (requires 11C.2):** ✅
- [x] Codegen: `instring(channel, var)` — reads a line from `System.in` via `Scanner.nextLine()` and stores in a string variable; test `instring_test` asserts round-trip read/print of `"Test Input String"`

## Milestone 11D — Control and Error Procedures

**Goal:** Implement control and error procedures from the environmental block.

- [x] Codegen: `stop` → `System.exit(0)`
- [x] Codegen: `fault(str, r)` → print to `System.err` then `System.exit(1)`

## Milestone 11E — Environmental Constants

**Goal:** Implement all standard environmental constants.

- [x] Codegen: `maxreal` → `ldc2_w Double.MAX_VALUE`
- [x] Codegen: `minreal` → `ldc2_w Double.MIN_VALUE`
- [x] Codegen: `maxint` → `ldc Integer.MAX_VALUE`
- [x] Codegen: `epsilon` → `ldc2_w` machine epsilon (~2.220446049250313E-16)

## Milestone 11F — Integration and Tests ✅

**Goal:** Integrate all environmental block features and validate with sample programs.

- [x] Test: `pi_simple.alg` (Archimedes method, call-by-value procedures) compiles and prints π approximations
- [x] Test: `sqrt` of a negative number returns NaN (documented choice: Java `Math.sqrt` returns `NaN` for negative input)
- [x] Grammar: unary minus (`-expr`) added to the expression grammar and handled in code generation and type inference
- Note: `pi2.alg` (call-by-name procedures, zero-parameter procedure syntax) requires future milestones — call-by-name semantics and grammar support for parameterless procedure declarations

---

## Milestone 12 — Call-by-Name (Jensen's Device) (`jen.alg`) ✅

**Goal:** `jen.alg` compiles and runs, demonstrating correct call-by-name parameter passing.

**Features implemented:**
- [x] Grammar: call-by-name parameters (default for parameters without `value`)
- [x] Codegen: thunk implementation using `Thunk` interface emitted as Jasmin (self-contained; no compiler runtime dependency)
- [x] Codegen: generate synthetic `ClassName$ThunkN` classes for name parameters at each call site
- [x] Codegen: invoke `get()` and `set()` methods for parameter access/assignment
- [x] Codegen: `for` loop with thunk loop variable (set/get through thunk instead of direct istore/iload)
- [x] Codegen: `generateExpr` propagates `varToFieldIndex` recursively so thunk field access works inside complex expressions
- [x] Codegen: integer→double coercion when a real name-parameter thunk wraps an integer expression
- [x] SymbolTableBuilder: parameter passing modes already tracked via `valueParams`
- [x] TypeInferencer: strips `thunk:` prefix when resolving variable types
- [x] `Thunk` interface emitted as `Thunk.j` alongside compiled program; assembled into output dir so program is self-contained
- [x] `AntlrAlgolListener`: `assemble()` picks up all `ClassName$ThunkN.j` and `Thunk.j` companion files automatically
- [x] Test: `jen_test()` — `sumof(1,10,i,i)` = 55.0 and `sumof(-5,5,j,j*j)` = 110.0
- [x] Test: `callByNameUpdateTest()` — `inc(i)` updates caller's `i` from 5 to 6

**Implementation notes:**
- Each call-by-name argument creates one `Object[1]` box in the caller; multiple name args referencing the same variable share the same box (enabling Jensen's Device)
- Thunk classes are separate `.j` files; Jasmin cannot handle multiple `.class` directives in one file
- After a call with name parameters, the caller restores simple-variable name args from their boxes

---

## Milestone 13 — Procedure References and Parameters

---

**Goal:** Build up to `manboy.alg` through incremental steps, each with simpler test programs.

### 13A — Procedure Variables (`proc_var.alg`) ✅

**Goal:** Simple program that declares a procedure variable and assigns/calls it.

**Status: PASSING** (`proc_var_test` green as of March 9, 2026).

**Features implemented:**
- [x] Grammar: procedure variable declarations (`procedure P;`)
- [x] Grammar: procedure references as expressions (allow procedure names in assignments)
- [x] SymbolTableBuilder: track procedure variables with "procedure:void" type — uses `Deque<ProcInfo>` stack for nested procedure support
- [x] TypeInferencer: handle procedure types in VarExpr
- [x] Codegen: `ProcRef` synthetic class generation via `generateProcedureReference`
- [x] Codegen: `generateProcedureVariableCall` routes calls through the interface slot
- [x] Codegen: `procVarSlots` field stored and used in `CodeGenerator`
- [x] Codegen: self-reference slot allocated in `enterProcedureDecl` for procedure variables
- [x] Codegen: `exitAssignment` routes `P := hello` to `astore` slot (not retval store)
- [x] Codegen: `exitProcedureCall` routes `P;` through slot when inside a procedure body
- [x] Runtime: `VoidProcedure`/`RealProcedure`/`IntegerProcedure`/`StringProcedure` Java interfaces created
- [x] Build: Gradle `test.doFirst` copies interface `.class` files to `build/test-algol`
- [x] `ExpressionGenerator`: fallback to `procedures` map when symbol table type lookup returns null
- [x] `AntlrAlgolListener`: pre-scan correctly detects procedure variables without over-adding

### 13B — Procedure Parameters (`proc_param.alg`) ✅

**Goal:** Simple program that passes a procedure as a parameter.

**Status: PASSING** (`proc_param_test` green as of March 10, 2026).

**Root cause (fixed):** `proc_param.alg` was missing `(P)` in `callIt`'s formal parameter list, so `ctx.paramList()` returned null and `P` never entered `paramNames` or got a JVM slot. Adding `(P)` fixed the issue — slot assignment and `exitProcedureCall` routing through `currentSymbolTable` already worked correctly.

**Features implemented:**
- [x] Codegen: procedure params get correct JVM slots in their declaring procedure's frame
- [x] Codegen: `exitProcedureCall` resolves procedure params from `currentSymbolTable` inside the procedure body
- [x] Codegen: `generateUserProcedureInvocation` pushes a ProcRef for procedure-type value arguments
- [x] Test: `callIt(hello)` invokes `hello` correctly, printing `"Hello"`

### 13C — Typed Procedure References (`proc_typed_simple.alg`) ✅

**Goal:** Program with typed procedure variables/parameters (real/integer procedures).

**Status: PASSING** (`proc_typed_simple_test` green as of March 10, 2026).

**Root cause (fixed):** Jasmin 2.4's `ScannerUtils.convertNumber` narrowed `3.14159` to `float` if it fit in float range, then widened it back to `double` via `ldc2_w`, producing `3.1415901184082031`. The fix is to append a `d` suffix to all `ldc2_w` real literal emissions — `ScannerUtils` checks `!str.endsWith("d")` before applying float narrowing.

**Features implemented:**
- [x] SymbolTableBuilder: track typed procedures ("procedure:real", "procedure:integer")
- [x] TypeInferencer: handle typed procedure types
- [x] Codegen: `generateProcedureReference` generates `RealProcedure`/`IntegerProcedure` ProcRef classes
- [x] Codegen: `generateProcedureVariableCall` calls through the correct interface with correct return type
- [x] Codegen: `exitAssignment` for typed procedure vars routes to `astore` slot
- [x] `proc_typed_simple.alg`: explicit `; P` call added before outer `end`
- [x] Codegen: `ldc2_w` real literals emit with `d` suffix in both `CodeGenerator` and `ExpressionGenerator`

### 13D — Deep Recursion (`deep_recursion.alg`) — SKIPPED

**Rationale:** `manboy.alg` (13E) already exercises mutual recursion and deep recursive calls. No additional deep recursion milestone is needed.


### 13E — Man or Boy (`manboy.alg`) ⚠️ IN PROGRESS

**Goal:** Full Man or Boy test with all features integrated.

**Status: IN PROGRESS (JVM verifier failure remaining).**
- Fixes applied for procedure-variable call object argument boxing; the previous `TypeError` path `expected R but found D` is resolved.
- Current failure now is post-compilation ASM verification (`org.objectweb.asm.tree.analysis.AnalyzerException`) in the generated class mirror of `manboy_test`.
- Diagnostic improvements are in place in `AntlrAlgolListener.compileToFile()`: immediate exception chain and full stack from underlying compilation context are now propagated.
- Next concrete step: inspect and adjust the generated `aastore` call site for thunk-driven recursive procedure invocation in `CodeGenerator.generateProcedureVariableCallViaStaticField` to ensure all values are boxed and correct during nested recursive call-by-name procedure reference passing.

**Note:** `testProcedureVariableCallBug` regression is now passing after `proceduresSupplier` null-safe path patch in `ProcedureGenerator`.


**Root cause:** Codegen emits primitives directly into `Object[]` for procedure argument passing. All primitives must be boxed (e.g., `Double.valueOf`) before storing in `Object[]`.

**Immediate TODO:**
- [ ] Update codegen to box all primitives before storing in `Object[]` for procedure calls (see `ExpressionGenerator`/`StatementGenerator`).
- [ ] Re-run ASM verification and confirm `manboy_test` passes.

**Reference:** See `docs/ManBoy-Debugging.md` for ASM output and detailed analysis.

---

## Milestone 13.1 — Deferred-Typing (Optional Formal Types)

Algol allows formals without an explicit base type (deferred-typing). To handle this correctly and avoid brittle global defaults, implement call-site deferred-typing as a focused milestone between ManBoy and Milestone 14.

**Status: NEARLY COMPLETE.**

- [x] Design call-site deferred-typing resolution: choose base type at each call site from (1) declared formal (if present), (2) inferred type of the actual, else (3) conservative fallback `integer`.
- [x] Implement initial thunk/descriptor generation using call-site-resolved/deferred base types (updated `CodeGenerator` and `SymbolTableBuilder` integration).
- [x] Fix runtime dispatch in deferred `Thunk.set/get` so integer vs real value conversions are handled without `ClassCastException`.
- [x] Confirm `deferred_typing_test` passes after implementation.
- [ ] Add unit tests covering mixed-type name-parameters (integer ↔ real) and missing formal types (small focused sample and ManBoy reproduction cases).
- [ ] Update documentation: explain deferred-typing behavior in `docs/Algol.md` and `docs/Compiler-TODO.md`.


## Milestone 14 — Procedure Parameters and Real Arrays (`recursion_euler.alg`) ✅

**Goal:** `recursion_euler.alg` compiles and runs, demonstrating typed procedure parameters, real arrays, and complex expression forms.

**Status: PASSING** (`recursion_euler_test` green as of March 10, 2026).

**Already implemented (from prior milestones):**
- [x] Grammar: procedure declarations with real/integer parameters and return types
- [x] Grammar: nested blocks, for loops (step/until, while forms), and arithmetic expressions
- [x] Grammar: procedure calls as expressions (typed procedure return values)
- [x] Codegen: recursive procedure calls (`invokestatic` to same class)
- [x] Codegen: integer/real type handling in expressions and assignments
- [x] Codegen: real arrays with non-zero lower bound offset (`bounds[0]` subtracted on array access/store in CodeGenerator)

**New features implemented:**
- [x] Grammar: bare `array` declaration without type prefix (`array m[0:15]`; type keyword is now optional; defaults to `real`)
- [x] Grammar: comma-separated for-list (`forList : forElement (',' forElement)*`; each `forElement` labeled `# StepUntilElement`, `# WhileElement`, or `# SimpleElement`)
- [x] Grammar/Codegen: if-then-else as an **expression** (`# IfExpr` alternative in `expr` rule; `TypeInferencer.exitIfExpr` infers result type; `CodeGenerator.generateExpr` emits `ifeq/goto` branching)
- [x] Grammar: named `end` (`end euler`; already supported — Algol 60 identifiers after `end` are treated as label-comments by the grammar)
- [x] Codegen: typed procedure parameter called with arguments (`fct(0)`, `fct(i)`) — `generateProcedureVariableCall` boxes args into `Object[]` using `Integer.valueOf`/`Double.valueOf`; `generateProcedureReference` unboxes via `java.lang.Number.intValue()`/`doubleValue()`
- [x] Fix: `t` added to local declarations; `r` (unused) removed; `altsign` helper procedure added (replaces `square` — Euler acceleration requires an alternating convergent series; `square` was divergent and caused an infinite loop once `and` became a proper keyword in M16); `outreal` call added for observable output
- [x] Test: `recursion_euler_test` asserts non-empty output

**Implementation notes:**
- Body-capture pattern for for-lists: `enterForStatement` redirects `activeOutput` to a capture buffer pushed on `forBodyStack`; `exitForStatement` pops the buffer, restores `activeOutput`, and inlines the captured body code once per for-element
- Real comparison fix: `RelExprContext` codegen checks `exprTypes` for operand types and uses `dcmpg + iflt/le/gt/ge/eq/ne` for real operands; `if_icmpxx` for integer operands (applied in both `generateExpr` and `enterIfStatement`)
- `SymbolTableBuilder.enterParamSpec` updated to handle new `paramSpecType` labeled alternatives via instanceof dispatch
- `TypeInferencer`: added `exitFalseLiteralExpr` and `exitIfExpr`

---

## Milestone 15 — Non-Local Scalar Variable Access (`pi2.alg`)

**Goal:** `pi2.alg` compiles and runs, demonstrating procedures accessing outer-scope scalar variables.

**New features needed:**
- [x] Promote outer-scope scalars to static class fields (consistent with arrays)
- [x] Update all codegen sites to use `getstatic`/`putstatic` for static scalars
- [x] Test: assert correct π approximation output

---

## Milestone 16 — Boolean Operators (`boolean_operators.alg`) ✅

**Goal:** `boolean_operators.alg` compiles and runs with correct `or` and `not` behavior.

**Status: PASSING** (29/29 tests green as of March 10, 2026).

**Features implemented:**
- [x] Grammar: `or` / `not` operators (and synonyms `|`, `~`, `and`, `&`) — added `OR`, `NOT`, `AND_KW` lexer tokens; `OrExpr` and `NotExpr` grammar rules with correct precedence (MulDiv > AddSub > Rel > And > Or)
- [x] Codegen: boolean `or` (`ior`) and `not` (`iconst_1; ixor`) instructions
- [x] TypeInferencer: `exitOrExpr` and `exitNotExpr` — enforce boolean operands, annotate result as `boolean`
- [x] Test: `boolean_operators_test()` — asserts "or works", "not works", "Boolean logic test passed"
- [x] Fix: `recursion_euler.alg` replaced `square` (divergent series) with `altsign` (alternating convergent series) — required because `and` is now a proper keyword, making the loop-guard `abs(mn) < abs(m[n]) and n < 15` work correctly for the first time
- [x] Fix: removed `@Tag("slow")` workaround from `manboy_test` and `recursion_euler_test`; removed `slowTest` gradle task — both tests run in <2s under plain `gradle test`

---

## Milestone 17 — Real Arrays (`real_array.alg`) ✅

**Goal:** `real_array.alg` compiles and prints correct real array values.

**Status: PASSING** (`real_array_test` green as of March 11, 2026).

**New features implemented:**
- [x] Grammar: `signedInt` rule added (`'-'? unsignedInt`); `arrayDecl` bounds updated to use `signedInt` — supports negative lower bounds (e.g. `[-7:2]`)
- [x] SymbolTableBuilder: `enterArrayDecl` updated to call `ctx.signedInt(0/1)` instead of `ctx.unsignedInt(0/1)`; `Integer.parseInt` handles `"-7"` correctly
- [x] Codegen: real array allocation (`newarray double`), element store (`dastore`), element load (`daload`) with lower-bound offset — all pre-existing; no changes required
- [x] Test: `real_array_test()` — asserts Jasmin contains `newarray double` and `daload`; runtime output contains `1.23` and `4.56`

**Implementation notes:**
- Size computation `upper - lower + 1 = 2 - (-7) + 1 = 10` is correct for the full index range
- Lower-bound offset: `ldc lower; isub` (where lower = -7) correctly remaps subscript -7 → JVM index 0 and subscript 2 → JVM index 9
- Subscript `-7` in `q[-7]` is parsed as a unary minus expression and generates `ldc 7; ineg` on the JVM stack
- `outreal` with a channel argument is used (`outreal(1, q[-7])`) — already supported by codegen

---

## Milestone 18 — String Output (`string_output.alg`) ✅

**Goal:** `string_output.alg` compiles and prints correct formatted string output.

**Status: PASSING** (`string_output_test` green as of March 11, 2026).

- [x] Grammar: string variable declarations and assignment
- [x] SymbolTableBuilder: track string variables and scope
- [x] TypeInferencer: handle string types and type rules
- [x] Codegen: string operations (assignment, concatenation, character access)
- [x] Codegen: `concat(s1, s2)` built-in function — implemented in `BuiltinFunctionGenerator`
- [x] Codegen: `length(s)` and `substring(s, i, j)` built-in functions
- [ ] Codegen: `instring` procedure — not yet implemented (separate milestone, M11C.3)
- [x] Test: assert output matches expected formatted string for `string_output.alg`

**Implementation notes:**
- String variables map to JVM `java/lang/String`; assignments use `astore`/`aload`
- `concat`, `length`, `substring` handled in `BuiltinFunctionGenerator.generateStringBuiltin()`
- `TypeInferencer` infers `string` type for all string built-in calls and string literal expressions

### Algol 60 BNF vs. Code Examples (Procedure Call Parameters)

The Algol 60 Modified Report BNF does **not** allow empty parameter lists in procedure calls (e.g., `outstring()` or `foo()`). Every procedure call must supply the required number of arguments, as specified in the procedure's declaration. However, some code examples in the Report (e.g., `outstring(, str);`) show the channel parameter left empty, not omitted. This is a syntactic quirk, but the BNF does not formally allow empty parameters—every procedure call must supply all required arguments.

**Relevant BNF from the Modified Report:**
```
<procedure identifier> ::= <identifier>

<actual parameter> ::= <string> | <expression> |
  <array identifier> | <switch identifier> | <procedure identifier>

<parameter delimiter> ::= , | ) <letter string> : (

<actual parameter list> ::= <actual parameter> |
  <actual parameter list> <parameter delimiter> <actual parameter>

<actual parameter part> ::= <empty> | ( <actual parameter list> )

<function designator> ::= <procedure identifier> <actual parameter part>
```
These lines show that while an empty parameter part is allowed (for parameterless procedures), every procedure call with parameters must supply all required arguments. The BNF does not permit empty slots in the parameter list (e.g., `outstring(, str)` is not valid according to the BNF).

**Example from the Modified Report:**
```
outstring(, str);
```
Here, the channel parameter is left empty, but the argument list is still present. The BNF does not permit this form, and the compiler enforces the stricter rule: all required parameters (such as the channel in `outstring`) must be present and non-empty in every call.

**Design decision:** The compiler will require all parameters in procedure calls, matching the BNF, even if some examples leave them empty. This ensures deterministic parsing and code generation.

---

## Milestone 19 — Own Variables (`own_variables.alg`)

**Goal:** `own_variables.alg` compiles and demonstrates persistent local variable behavior across block re-entry.

**New features needed:**
- [ ] Grammar: `own` variable and `own` array declarations
- [ ] Codegen: static/persistent local variables (retain value across block re-entry)
- [ ] Test: assert own variables retain values as specified

---

## Milestone 20 — Switch Declarations (`switch_declaration.alg`)

**Goal:** `switch_declaration.alg` compiles and demonstrates correct multi-way goto behavior.

**New features needed:**
- [ ] Grammar: switch declarations and designational expressions
- [ ] Codegen: multi-way goto using switch
- [ ] Test: assert correct label selection and control flow

---

## Future Milestones (not yet sequenced)

- **`jalgol` CLI** — a command-line entry point mirroring `javac`: accepts one or more `.alg` source files, optional `-d <outdir>` flag, invokes the compiler pipeline and Jasmin assembler, exits non-zero on errors. Produces `Hello.class` and any `Hello$ThunkN.class` files in the output directory. No JAR packaging required — users run with `java -cp <outdir> Hello`.
- Nested procedures with non-local variable access (display/frame pointer)
- Algol 60 formal array parameters
- File I/O extensions (`openfile`, `closefile`, extended channel support) — channels 2+ mapped to files; all I/O procedures extended to use dynamic stream dispatch; error handling via `fault`
- External procedures — declare and call JVM static/virtual methods from Algol; syntax along the lines of `external static(java.lang.Math) real procedure cos(real a);`; restrictions: no call-by-name parameters, no label parameters, no external goto (see Algol Extensions.md)
- Lambda notation — anonymous procedure expressions (`λ(x) x × x`); syntactic sugar for inline procedure values; useful for higher-order procedures and call-by-name arguments (see Algol Extensions.md)
- Formatted I/O — `outformat(channel, format, ...)` and `informat(channel, format, ...)` with Algol-style format strings (e.g. `"I5, F8.2, A10"`); includes string channel support via `openstring`/`closefile` for sprintf-style output (see Algol Extensions.md and Environmental-Block.md)

# Completed from previous Future Milestones:
# - Standard math functions (`abs`, `sqrt`, `sin`, `cos`, `ln`, `exp`, etc.) — ✅ Milestone 11A
# - `pi.alg` — `real` procedures; `sqrt` standard function — ✅ Milestone 11F
# - Standard I/O (`ininteger`, `inreal`, `inchar`) — ✅ Milestone 11C.1
# - String variables (M11C.2) — ✅ Milestone 18 (implemented; concat/length/substring built-ins done)
# - Standard I/O (`instring`) (M11C.3) — ✅ Milestone 18 (implemented; Scanner.nextLine())
# - Error handling (`fault` procedure) — ✅ Milestone 11D
# - `jen.alg` (call-by-name) — Milestone 12
# - `manboy.alg` (deep recursion + procedure refs) — Milestone 13 (in progress; currently one failing test)
# - `recursion_euler.alg` (procedure parameters + real arrays) — Milestone 14
# - `pi2.alg` (non-local scalar access) — Milestone 15
# - `boolean_operators.alg` — Milestone 16
# - `real_array.alg` — Milestone 17
# - `string_output.alg` — ✅ Milestone 18
# - `own_variables.alg` — Milestone 19
# - `switch_declaration.alg` — Milestone 20

---

## Infrastructure TODOs (any milestone)

- [ ] Replace deprecated `ANTLRInputStream` with `CharStreams.fromReader()`
- [ ] Write `.j` Jasmin files to a configurable output directory (not hardcoded)
- [x] Add integration test helper to invoke Jasmin and run the resulting `.class`
- [ ] Decide on output directory structure for compiled classes

---

# AI-Friendly Compiler Design: Implementation Priorities

To ensure long-term maintainability and enable advanced tooling/AI workflows, the following improvements are tracked below. Items marked ✅ have been addressed; remaining items can be added incrementally.

## Completed
- ✅ Modular multi-pass architecture: `SymbolTableBuilder` → `TypeInferencer` → `CodeGenerator` — clear separation of concerns, implemented from Milestone 2 onward.
- ✅ Deterministic Jasmin output: canonical label naming, stable method/field ordering (sufficient for current milestones).

## Still Relevant (Can Be Added Any Time)
- Minimal structured diagnostics: error reporting with file, line, column, and stable error codes (as Java objects, even if not yet JSON). Allow multiple errors per run.
- Snapshot/golden tests: verify Jasmin output and diagnostics are stable and deterministic across compiler changes.
- Full structured JSON diagnostics: machine-readable output, fix-it suggestions, deterministic ordering.
- CLI options to emit AST, IR, or JVM IR for inspection/tooling.
- Compile-time stack analysis and mapping of JVM verifier errors to Algol source.
- Consistent debug metadata: line number tables, local variable tables, source-to-bytecode mapping.
- Modern CLI commands: `check`, `emit-jasmin`, `emit-ast`, `emit-jvmir`, etc.
- Versioned diagnostic schemas, stable IR formats, and LSP (Language Server Protocol) integration.
