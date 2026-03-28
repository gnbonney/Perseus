# Compiler Roadmap

This list follows an iterative, depth-first approach: get each sample program
fully compiling and running before expanding to the next. Each milestone produces
a real, executable class file — not just a parse tree or a Jasmin text skeleton.

 ---

## Current Status


**55/55 tests passing as of March 22, 2026.**

Recent milestone wins:

- `PerseusCompilerTest.manboy_test()` now passes. Perseus compiles and runs Knuth's Man-or-Boy test correctly and returns `-67.0`.
- `PerseusCompilerTest.primer2()` now uses an explicit expected-timeout path so its intentional infinite loop cannot be mistaken for a normal passing run.
- `FixLimits` now verifies generated class families (`Main.class` plus `Main$*.class`), not just the main class.
- The Jasmin pipeline is cleaner: `compileToFile()` writes the `.j` family, `assemble()` assembles that family, and tests can post-process the resulting class family in place.

**Near-term next steps:**
1. Keep the current core-language implementation stable while moving toward a JVM-practical MVP.
2. Prioritize interop-enabling extensions such as external procedures, classes, and exceptions.
3. Continue standard-compliance milestones incrementally, especially where classic Algol 60 examples expose concrete gaps.

### Resolved Issues

- Procedure-call boxing: procedure-reference invocation now boxes primitive arguments before storing them in `Object[]`.
- Man-or-Boy recursion semantics: recursive procedure-identifier thunks now refresh re-entrant state correctly, nested procedures save/clear/restore `__selfThunk_*` bridges per activation, and `manboy.alg` returns the correct `-67.0`.
- Generated class-family verification: `FixLimits.fixClassFamilyInPlace()` verifies the main generated class and all companion `$*.class` files.
- Assembly pipeline cleanup: `compileToFile()` only emits Jasmin files and `assemble()` is the single place that assembles the full family.

- ✅ **ParseTreeWalker / procedure inlining** — procedures are now correctly generated as separate static methods via the two-pass architecture.
- ✅ **CodeGenerator.java size limit** — refactored into modular delegation architecture; `CodeGenerator` delegates to `ExpressionGenerator`, `StatementGenerator`, and `ProcedureGenerator`.
- ✅ **Procedure variable support** — procedure variables, typed procedure references, and procedure parameters all working. `procBufferStack` supports nested procedure declarations.
- ✅ **For-list body capture** — for-list codegen uses `forBodyStack` (Deque) to capture the body once and inline it per element; eliminates label corruption in nested for-loops.
- ✅ **Real comparisons** — `RelExprContext` codegen now dispatches on operand types: `dcmpg + iflt/le/gt/ge` for real, `if_icmpxx` for integer.
- ✅ **String variable support** — grammar, `SymbolTableBuilder`, `TypeInferencer`, and `CodeGenerator` all handle `string` variables; `concat`, `length`, and `substring` built-ins implemented in `BuiltinFunctionGenerator`; `string_output.alg` passes.
- ✅ **`ProcedureGenerator` delegation wired** — `generateProcedureReference` and `generateProcedureVariableCall` now delegate to `ProcedureGenerator` via callback injection; `CodeGenUtils` consolidated with `scalarTypeToJvmDesc`, `getReturnInstruction`, and fixed `boolean[]`/default-return-type bugs.

---

## Roadmap Scope

This document is intended to track milestones, status, and implementation
priority. Architectural rationale such as the multi-pass compiler structure,
the role of `TypeInferencer`, and JVM-lowering design decisions now lives in
[Architecture.md](Architecture.md).

---

## Milestone 1 — Hello World (`hello.alg`)

**Goal:** `hello.alg` compiles to a `.class` file that runs and prints `Hello World`.

**Grammar already handles:** `begin`/`end`, `comment`, a single procedure call with
integer and string arguments.

- [x] Emit Jasmin for `outstring(channel, str)` — maps to `java/io/PrintStream.print(Ljava/lang/String;)V` via `System.out`
- [x] Wire Jasmin output to a file (currently returns a String; write `<ClassName>.j` to disk)
- [x] Invoke Jasmin assembler on the output file to produce a `.class` file
- [x] Run the `.class` file and capture stdout in the test
- [x] Update `PerseusCompilerTest.hello()` to assert the output is `"Hello World"`

---

## Milestone 2 — Variables and Assignment (`primer1.alg`)

**Goal:** `primer1.alg` compiles and runs, producing correct real-valued results.

**⚠ Requires the symbol-table pass described in [Architecture.md](Architecture.md).**

**New features needed:**
- [x] Grammar: `real` variable declarations (`real x, y, u;`)
- [x] **Design and implement symbol table** — first-pass visitor that collects variable names, types, and block scope
- [x] **Implement two-pass compile in `PerseusCompiler`** — split into separate `SymbolTableBuilder` and `CodeGenerator` listener classes
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

**⚠ Introduces mixed `integer`/`real` types — requires the `TypeInferencer` pass described in [Architecture.md](Architecture.md).**

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

**Note:** String variables and string I/O (e.g., `instring`, `outstring`) are extensions to Algol 60, not part of the original standard. Implementation of string input procedures depends on first supporting string variables (see Perseus Language Design.md).

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
- [x] **String variable support follows the design in [Perseus Language Design.md](Algol%20Extensions%20Design.md).**

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
- [x] `PerseusCompiler`: `assemble()` picks up all `ClassName$ThunkN.j` and `Thunk.j` companion files automatically
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
- [x] `PerseusCompiler`: pre-scan correctly detects procedure variables without over-adding

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


### 13E - Man or Boy (`manboy.alg`) DONE

**Goal:** Full Man or Boy test with all features integrated.

**Status: PASSING** (`manboy_test` green as of March 22, 2026).

**What was required to get it passing:**
- [x] Box procedure-call arguments before storing them in `Object[]` for procedure-reference invocation.
- [x] Clean up the Jasmin pipeline so `compileToFile()` writes the `.j` family and `assemble()` handles the family assembly step.
- [x] Run ASM recomputation and verification across the full generated class family, not just the main class.
- [x] Track nested procedures in `SymbolTableBuilder` so procedure entry/exit can save, clear, and restore nested `__selfThunk_*` bridges.
- [x] Add a `sync()` hook to generated thunks so recursive procedure-identifier actuals can refresh their captured bridged environment before re-entrant reuse.
- [x] Avoid the stale end-of-`get()` overwrite that had been resetting captured bridged parameters after recursive calls.
- [x] Keep timeout-based tests explicit so recursion failures fail loudly instead of looking like normal passes.

**Result:** `manboy.alg` now compiles, assembles, passes ASM verification, runs to completion, and prints the correct answer `-67.0`.

**Reference:** See `docs/ManBoy-Debugging.md` for the debugging history and the intermediate verifier/runtime failure analysis.

---

## Milestone 13.1 — Deferred-Typing (Optional Formal Types)

Algol allows formals without an explicit base type (deferred-typing). To handle this correctly and avoid brittle global defaults, implement call-site deferred-typing as a focused milestone between ManBoy and Milestone 14.

**Status: COMPLETE.**

- [x] Design call-site deferred-typing resolution: choose base type at each call site from (1) declared formal (if present), (2) inferred type of the actual, else (3) conservative fallback `integer`.
- [x] Implement initial thunk/descriptor generation using call-site-resolved/deferred base types (updated `CodeGenerator` and `SymbolTableBuilder` integration).
- [x] Fix runtime dispatch in deferred `Thunk.set/get` so integer vs real value conversions are handled without `ClassCastException`.
- [x] Confirm `deferred_typing_test` passes after implementation.
- [x] Add unit tests covering mixed-type name-parameters (integer ↔ real) and missing formal types (small focused sample and ManBoy reproduction cases).
- [x] Update documentation: explain deferred-typing behavior in `docs/Algol.md` and `docs/Compiler Roadmap.md`.

**Regression coverage now includes:**
- `deferred_typing_test`
- `deferred_name_params_mixed_type_test`
- `deferred_missing_formal_types_test`


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
- [x] Codegen: `instring` procedure — implemented in Milestone 11C.3 via `Scanner.nextLine()` for string variable input
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

## Milestone 19 — Own Variables (`own_variables.alg`) ✅

**Goal:** `own_variables.alg` compiles and demonstrates persistent local variable behavior across block re-entry.

**Status: PASSING** (`own_variables_test` green as of March 22, 2026).

**Features implemented:**
- [x] Grammar: `own` variable and `own` array declarations
- [x] SymbolTableBuilder: track procedure-local `own` variables separately from ordinary locals
- [x] Codegen: represent `own` procedure locals via persistent class static fields rather than per-activation JVM locals
- [x] Sample: `own_variables.alg` demonstrates persistent scalar and array state across repeated procedure calls
- [x] Test: `own_variables_test()` verifies the generated Jasmin includes persistent static fields and that repeated calls produce the expected `1 3 6`

---

## Milestone 20 — Switch Declarations (`switch_declaration.alg`) ✅

**Goal:** `switch_declaration.alg` compiles and demonstrates correct multi-way goto behavior.

**Status: PASSING** (`switch_declaration_test` green as of March 22, 2026).

**Features implemented:**
- [x] Grammar: switch declarations and designational expressions
- [x] Grammar: `goto` now accepts a full designational expression rather than only a bare label
- [x] SymbolTableBuilder: collect switch declarations for second-pass lowering
- [x] Codegen: lower switch designators into ordinary Jasmin branch chains that evaluate switch entries at jump time
- [x] Sample: `switch_declaration.alg` now demonstrates a small switch-driven score computation with a nested switch and an inline `if ... then ... else ...` designational expression
- [x] Test: `switch_declaration_test()` verifies compilation, assembly, ASM verification, and final output `25`

---

## Milestone 21 — Nested Procedures with Non-Local Variable Access

**Priority:** Highest remaining core-language milestone.

**Goal:** Make nested procedures reliably read and update enclosing-scope state across realistic activation patterns.

**Covered use cases:**

- [x] Nested procedure reads outer scalar variables (`pi2.alg`, Milestone 15)
- [x] Nested procedure reads an enclosing value parameter via environment bridging (`manboy.alg`)
- [x] Nested recursive procedure updates enclosing scalar locals and the outer procedure observes the updated values after re-entry (`nested_digits.alg`)
- [x] Nested procedures preserve re-entrant self-thunk state across recursive activations (`manboy.alg`)
- [x] Simple nested scope access regression remains covered (`nested_scope_access.alg`)

**Known limitations and possible future direction:** The current implementation uses the existing environment-bridge/static-field strategy and is intentionally being kept stable now that `manboy.alg` and `nested_digits.alg` both work. We have not yet added Milestone 21-specific regressions for nested procedures updating outer arrays or for deeper multi-level non-local mutation chains beyond the currently verified cases. If future classic Algol 60 examples expose gaps here, the next step should be to add targeted regression tests first and document the specific limitation before considering a broader refactor of the non-local access machinery.

## Milestone 22 — Algol 60 Formal Array Parameters

**Priority:** Final major classic Algol data-passing feature.

**Goal:** Support formal array parameters in procedures, including realistic classic Algol 60 numeric examples.

**Status: PASSING** (`array_param_test` and full suite green as of March 25, 2026).

- [x] Grammar and symbol-table support for formal array parameters
- [x] Codegen for array descriptor/bounds passing and indexed access inside callees
- [x] Tests with array-processing procedures drawn from classic Algol-style examples

**Implementation notes:**
- Formal parameters can now use typed or untyped `array` specs (`real array`, `integer array`, `string array`, `boolean array`, or plain `array`)
- Array arguments are passed as a JVM array reference plus hidden lower/upper bound integers so the callee preserves the caller's declared index range
- Array indexing inside procedures now uses those passed bounds instead of assuming compile-time static bounds
- Current coverage focuses on direct use of array formals inside the declaring procedure body; nested procedures capturing outer array formals should get their own regression before any broader refactor

## Milestone 23 — Multidimensional Arrays and Bound-Pair Syntax

**Priority:** Remaining core-language array-completeness milestone needed for classic Algol 60 numeric examples.

**Goal:** Support standard multidimensional array declarations and subscripts so classic matrix-style examples from the Modified Report can compile naturally.

**Status: PASSING** (`multidimensional_array_test` and full suite green as of March 25, 2026).

- [x] Grammar: multiple bound pairs in array declarations
- [x] Grammar: comma-separated subscript lists in array access and assignment
- [x] Codegen: lowering strategy for multidimensional arrays with non-zero lower bounds
- [x] Tests using classic Algol 60 matrix examples such as `Spur`, `Transpose`, or `Absmax`

**Implementation notes:**
- Multidimensional arrays are currently lowered to a single JVM array using row-major linearization
- Bound metadata is retained per dimension so subscripts with non-zero lower bounds are normalized before flattening
- The new `matrix_trace.alg` regression exercises `Spur`/`Transpose`, two-dimensional declaration, assignment, access, and lower-bound offset behavior
- Multidimensional formal array parameters are still not supported; current formal-array passing remains one-dimensional

## Milestone 24 — Hardware Representation Completion

**Priority:** Standard-syntax compliance milestone based on [Representation.md](Representation.md).

**Goal:** Bring the grammar and expression handling fully in line with Perseus's documented hardware representation before moving on to non-standard extensions.

**Status: PASSING** (Milestone 24 regressions and full suite green as of March 25, 2026).

- [x] Grammar / expression support: `div` integer division
- [x] Grammar / expression support: `**` and `^` exponentiation
- [x] Grammar / expression support: `imp` / `eqv` and synonyms `=>` / `==`
- [x] Grammar: allow `{` and `}` everywhere `begin` and `end` are accepted, not only at the top-level program rule
- [x] Tests covering the documented symbol mappings in [Representation.md](Representation.md)

**Implementation notes:**
- `div` now parses in the multiplicative-precedence tier and currently lowers to JVM integer division
- `**` and `^` now parse as exponentiation operators and lower through `java/lang/Math.pow`, with integer results converted back to `int` when both operands are integral
- `imp`, `eqv`, `=>`, and `==` now participate in Boolean expression parsing and code generation, with prefix `not` precedence tightened so mixed expressions behave as intended
- Brace-delimited blocks are now accepted anywhere a normal `begin ... end` block is accepted
- Milestone 24 coverage is anchored by `modified_division.alg`, `power_associativity.alg`, `boolean_imp_eqv.alg`, `representation_synonyms.alg`, and `brace_blocks.alg`

## Milestone 25 — Remaining Modified Report Surface Syntax

**Priority:** Final standard source-compatibility cleanup before extensions.

**Goal:** Close the remaining non-extension syntax gaps that affect real Modified Report examples, while still allowing Perseus's normalized grammar design.

**Status: PASSING** (Milestone 25 regressions and full suite green as of March 25, 2026).

- [x] Grammar: numeric labels
- [x] Grammar: dummy statements (empty statements used as label targets)
- [x] Grammar: parameter delimiters such as `Order:(n)` and `Result:(y)`
- [x] Document the compatibility policy from [Modified Report Compatibility.md](Modified%20Report%20Compatibility.md): accept numeric labels, dummy statements, and named parameter delimiters verbatim; keep simple parameter lists as the preferred style; continue normalizing report-only symbol/quoting variants rather than accepting them verbatim
- [x] Add focused parser/integration tests based on Modified Report examples for these forms

**Implementation notes:**
- Numeric labels are now accepted in declarations and designational `goto` targets, and are normalized internally to legal Jasmin labels during code generation
- Dummy statements are now accepted through the statement/compound-statement grammar without the ANTLR empty-match warning that appeared during the first draft of the rule
- Named parameter delimiters now parse in both procedure declarations and calls by treating `) Name:(` as a parameter delimiter alongside `,`
- Milestone 25 coverage is anchored by `numeric_labels_and_dummy_statements.alg` and `parameter_delimiters_absmax.alg`

## Milestone 26 — External Procedures

**Priority:** First JVM-practical interop milestone.

**Goal:** Introduce external procedures in phased form, starting with the simplest robust separate-compilation and JVM-static interop cases (see [Perseus Language Design.md](Perseus%20Language%20Design.md)).

**Status:** Phase 26A implemented and passing as of March 25, 2026.

- [x] Phase 26A: `external algol(TargetClass)` for exact-signature static procedure linkage across separately compiled Perseus units
- [x] Phase 26A: `external java static(TargetClass)` for explicit static JVM interop calls
- [x] Compile-time diagnostics for class-not-found, method-not-found, and signature-mismatch cases
- [x] Document and enforce the initial ABI boundary clearly: scalar/string support first; arrays, procedure values, labels, switches, and call-by-name handled only when their ABI is documented

**Implementation notes:**
- External procedure declarations now parse directly in the grammar and are tracked as ordinary declared procedures with external metadata
- `external algol(...)` and `external java static(...)` both lower to explicit `invokestatic` calls with declaration-driven JVM descriptors
- The current Phase 26A validation checks target class existence, static method existence, and declared signature compatibility at compile time
- Current supported external ABI surface is intentionally narrow: `integer`, `real`, `string`, `void`, and `boolean` for Java static interop
- Targeted regression coverage is provided by `external_algol_library.alg`, `external_algol_client.alg`, and `external_java_math.alg`

**TBD: Planned follow-on after Milestone 26:**
- Phase 26B: external Algol array parameters as a documented ABI case
  Keep the first array-interoperability slice to one-dimensional arrays using Perseus's current array-plus-bounds calling convention, with historic library-style regression cases such as `INIVEC`
- External Algol call-by-name only after the thunk ABI is frozen and documented
- Java instance-method interop after the object/class story is clearer

## Milestone 27 — Simula-Style Classes and External Classes

**Priority:** Core JVM-practical milestone after external procedures.

**Goal:** Add a class/object extension inspired by Simula 67, including a more natural model for external JVM classes (see [Perseus Language Design.md](Perseus%20Language%20Design.md)).

- [x] Design and implement a minimal class syntax compatible with Algol/Simula style
- [x] Support instance fields, procedures, and object creation semantics
- [x] Support `ref(ClassName)` declarations, `new ClassName(...)`, and dotted member calls
- [x] Add regression tests for simple object-oriented scenarios
- [ ] Add external java class ... style declarations for imported JVM classes
- [ ] Define how class-based interop relates to external procedures
- [ ] Add tests for class-based Java interop scenarios

**Implementation notes:**
- Basic class support now compiles class declarations into separate generated JVM classes that are assembled alongside the main program.
- The current slice covers constructor-style class parameters, instance fields, instance procedures, `ref(...)` references, `new`, and dotted method calls.
- Class procedures follow the Simula-style default of call-by-value.
- Current regression drivers: `class_counter.alg`, `class_point.alg`, and `class_two_counters.alg`.

**TBD: planned follow-on after Milestone 27**
- External JVM class declarations and object interop
- Inheritance/prefixing and virtual dispatch decisions
- Richer member syntax such as explicit field selection versus zero-argument procedure calls
- Exception-object/member access integration once Milestone 28 is in place

## Milestone 28 — Exceptions and Structured Recovery

**Priority:** Core JVM-practical recovery/interoperability milestone.

**Goal:** Add an Algol-flavored exception mechanism for structured recovery, especially around `external java` interop and modern I/O extensions (see [Perseus Language Design.md](Perseus%20Language%20Design.md)).

- [x] Grammar: `begin ... exception ... end` block form with `when ... do ...` clauses
- [x] Define initial language-level exception names such as `IOError`, `EndOfFile`, `ArithmeticError`, `BoundsError`, and `FaultError` in `Perseus Language Design.md`
- [x] Support catching Java exceptions by explicit class name in `when java(...) do ...` clauses
- [x] Lower exception blocks to JVM `try/catch`
- [ ] Decide which existing runtime failures should remain fail-fast and which should become catchable exceptions
- [ ] Give `when ... as ex do ...` real semantic/runtime support by binding a catch variable inside the handler
- [ ] Add initial exception-inspection helpers such as `exceptionmessage(ex)` and `printexception(ex)`
- [ ] Decide later whether richer exception member syntax should use helpers only or eventually support object-style access such as `ex.message`
- [x] Tests for `external java`, bounds handling, and `fault` recovery scenarios

**Current passing slice:** basic structured recovery is now working end to end. The compiler accepts `begin ... exception ... end`, lowers handlers to JVM `.catch` regions, and passes end-to-end tests for:

- Java exception recovery through `external java`
- `BoundsError`-style recovery for out-of-range array access
- `FaultError`-style recovery for `fault(...)` inside an exception block

**Current limitation:** this first slice still uses direct JVM exception mappings in code generation (`NumberFormatException`, `ArrayIndexOutOfBoundsException`, `RuntimeException`) rather than a full dedicated Perseus runtime exception hierarchy. Bound exception variables (`as ex`) are parsed but not yet given semantic/runtime support, and exception introspection helpers are not implemented yet.

## Milestone 29 — Dynamic Channels and File I/O

**Priority:** Next runtime/library milestone.

**Goal:** Move beyond channels 0/1 and support dynamic file and string channel mapping (see [Perseus Language Design.md](Perseus%20Language%20Design.md)).

**Status:** First slice passing.

- [x] Implement initial support for `openfile`, `openstring`, `closefile`, and channel-to-stream mapping for channels 2+
- [x] Extend key output/input procedures for the first dynamic-channel workflows: `outstring`, `outinteger`, `outreal`, `outterminator`, and `instring`
- [x] Cover an initial file/channel error path through structured exceptions (`IOError`)
- [ ] Generalize the runtime model beyond the current constant-channel / literal-path slice
- [ ] Extend more input/output procedures to use dynamic stream dispatch instead of only `System.out` / `System.err`
- [ ] Add explicit `EndOfFile` behavior and decide where `fault(...)` remains the compatibility fallback

The current passing slice is intentionally narrow: it supports the new regression samples for file output/readback, string-channel accumulation, and recoverable `IOError`, while leaving a fuller runtime channel abstraction for follow-on work.

## Milestone 30 — Formatted I/O

**Goal:** Implement `outformat` and `informat` with channel-backed formatting workflows (see [Perseus Language Design.md](Perseus%20Language%20Design.md)).

**Status:** Passing.

- [x] `outformat(channel, format, ...)`
- [x] `informat(channel, format, ...)`
- [x] String-channel formatting scenarios (`openstring`, `closefile`, sprintf-like usage)

The current passing slice supports the `I`, `F`, and `A` format families used by the new regression samples, including formatted stdout output, string-channel accumulation, file-channel formatted output with readback, and basic `informat` parsing.

## Milestone 31 — CLI and Compiler UX

**Goal:** Turn the existing CLI into a real compiler front end suitable for everyday use (see [CLI Design.md](CLI%20Design.md)).

- [x] `perseus` command mirroring `javac` for the current single-file `.alg` workflow
- [ ] Classpath options such as `-cp` / `--classpath` for external Algol and Java resolution
- [x] Optional `-d <outdir>` and sensible output layout
- [x] Optional `--jar <file>` packaging for runnable output
- [ ] Optional or default ASM post-processing/verification step after Jasmin assembly (for example via `FixLimits`) so CLI builds get the same class-family cleanup and verifier feedback used by the `manboy` tests
- [x] Clearer user-facing error output for diagnostics vs internal compiler failures

The current passing slice includes a real Gradle-distributed `perseus` launcher, javac-style `-d` handling, inferred class names from source files, and optional runnable JAR packaging. Remaining Milestone 31 work is mainly about classpath options, broader multi-file workflows, ASM post-processing, and any further refinement of exit-code policy.

## Milestone 32 — Lambda Notation

**Goal:** Add anonymous procedure expressions as a higher-level extension on top of the procedure-value machinery (see [Perseus Language Design.md](Perseus%20Language%20Design.md)).

- [ ] Syntax and parsing for lambda-style procedure literals
- [ ] Lowering strategy onto existing procedure-reference infrastructure
- [ ] Tests for higher-order procedure use cases

## Milestone 33 — Label and Switch Parameters / Designational Exits

**Priority:** Standards-completeness milestone that may be less important for a JVM-practical MVP than classes and exceptions.

**Goal:** Allow labels and switches to be passed as parameters and used for procedure-mediated exits, matching real Algol 60 designational-expression semantics more closely.

- [ ] Grammar: `label` and `switch` formal parameter specifiers
- [ ] Grammar: `Boolean procedure` declarations and `Boolean procedure` formal parameter specifiers
- [ ] Codegen/runtime: Boolean-valued procedure calls and Boolean procedure references/parameters
- [ ] Procedure calls: pass labels and switches as actual parameters
- [ ] Codegen: support designational exits through passed labels/switches where legal
- [ ] Enforce or document the goto-scope restrictions that still apply

Possible JVM strategy for passed labels: lower non-local label exits to tagged exceptions (or an equivalent non-local escape mechanism) and catch them in the block/procedure activation that owns the real target labels. This would avoid requiring impossible cross-method JVM jumps while still giving a plausible implementation path for Algol-style designational exits.

Possible JVM strategy for passed switches: lower a switch parameter to an indexed collection of label-exit descriptors (or thunks that resolve to them), reusing the same non-local escape machinery as passed labels when `goto sw[i]` selects a non-local target.

## Notes on Prioritization

The order above is intentional:
1. Keep the currently-working **core Algol 60** implementation stable while closing the most valuable remaining semantic gaps (`21`–`25`).
2. Then prioritize the most useful **JVM-practical interop features** (`26`–`28`) to reach a minimum viable product sooner.
3. After that, continue with **runtime/library work, tooling, and longer-horizon extensions** (`29`–`33`).

This keeps the roadmap aligned with the current goal of reaching a JVM-practical minimum viable product sooner, while still preserving the remaining Modified Report completeness work as explicit milestones rather than dropping it.
# Completed from previous Future Milestones:
# - Standard math functions (`abs`, `sqrt`, `sin`, `cos`, `ln`, `exp`, etc.) — ✅ Milestone 11A
# - `pi.alg` — `real` procedures; `sqrt` standard function — ✅ Milestone 11F
# - Standard I/O (`ininteger`, `inreal`, `inchar`) — ✅ Milestone 11C.1
# - String variables (M11C.2) — ✅ Milestone 18 (implemented; concat/length/substring built-ins done)
# - Standard I/O (`instring`) (M11C.3) — ✅ Milestone 18 (implemented; Scanner.nextLine())
# - Error handling (`fault` procedure) — ✅ Milestone 11D
# - `jen.alg` (call-by-name) — Milestone 12
# - `manboy.alg` (deep recursion + procedure refs) - completed in Milestone 13E
# - `recursion_euler.alg` (procedure parameters + real arrays) — Milestone 14
# - `pi2.alg` (non-local scalar access) — Milestone 15
# - `boolean_operators.alg` — Milestone 16
# - `real_array.alg` — Milestone 17
# - `string_output.alg` — ✅ Milestone 18
# - `own_variables.alg` — ✅ Milestone 19
# - `switch_declaration.alg` — ✅ Milestone 20

---

## Infrastructure TODOs (any milestone)

- [ ] Replace deprecated `ANTLRInputStream` with `CharStreams.fromReader()`
- [ ] Write `.j` Jasmin files to a configurable output directory (not hardcoded)
- [x] Add integration test helper to invoke Jasmin and run the resulting `.class`
- [ ] Decide on output directory structure for compiled classes

---

# Tool-Friendly Compiler Design: Implementation Priorities

To ensure long-term maintainability and enable advanced tooling workflows, the following improvements are tracked below. Items marked ✅ have been addressed; remaining items can be added incrementally.

## Completed
- ✅ Modular multi-pass architecture: `SymbolTableBuilder` → `TypeInferencer` → `CodeGenerator` — clear separation of concerns, implemented from Milestone 2 onward.
- ✅ Deterministic Jasmin output: canonical label naming, stable method/field ordering (sufficient for current milestones).

## Still Relevant (Can Be Added Any Time)
- Extend the current structured diagnostics work beyond parse/type inference: add more phases, collect multiple independent errors per run where practical, and keep stable file/line/column/code reporting.
- Snapshot/golden tests: verify Jasmin output and diagnostics are stable and deterministic across compiler changes.
- Full structured JSON diagnostics: machine-readable output, fix-it suggestions, deterministic ordering.
- CLI options to emit AST, IR, or JVM IR for inspection/tooling.
- Compile-time stack analysis and mapping of JVM verifier errors to Algol source.
- Consistent debug metadata: line number tables, local variable tables, source-to-bytecode mapping.
- Modern CLI commands: `check`, `emit-jasmin`, `emit-ast`, `emit-jvmir`, etc.
- Versioned diagnostic schemas, stable IR formats, and LSP (Language Server Protocol) integration.






