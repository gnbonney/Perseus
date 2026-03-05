# Compiler Development TODO

This list follows an iterative, depth-first approach: get each sample program
fully compiling and running before expanding to the next. Each milestone produces
a real, executable class file — not just a parse tree or a Jasmin text skeleton.

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

**Pass 2 — Code generation:** Walk the parse tree again using the symbol table
built in Pass 1 to emit correct Jasmin instructions.

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
- [ ] Codegen: **Channel-aware stream selection for output procedures** — implement logic to select `System.out` or `System.err` based on channel parameter, per Environmental-Block.md (channel 0 → `System.err`, channel 1 → `System.out`, others → `System.out`). Warn and default to `System.out` if channel is not a compile-time constant.

**Implementation notes:**
- `outchar(channel, str, int)` uses `String.charAt(I)C` to extract the character, then prints it
- `outterminator(channel)` outputs a space character as a string separator
- Channel parameter currently accepted but ignored (always uses System.out); **implement channel-aware stream selection as described above**
- Both procedures work with existing outinteger, outreal, outstring infrastructure

## Milestone 11C — Input Procedures

**Goal:** Implement all standard input procedures.

- [ ] Codegen: `ininteger(channel, var)` → `Scanner.nextInt()` (reads from `System.in`)
- [ ] Codegen: `inreal(channel, var)` → `Scanner.nextDouble()` (reads from `System.in`; shared `Scanner` instance)
- [ ] Codegen: `inchar(channel, str, var)` — read one character; find its position in `str`

## Milestone 11D — Control and Error Procedures

**Goal:** Implement control and error procedures from the environmental block.

- [ ] Codegen: `stop` → `System.exit(0)`
- [ ] Codegen: `fault(str, r)` → print to `System.err` then `System.exit(1)`

## Milestone 11E — Environmental Constants

**Goal:** Implement all standard environmental constants.

- [ ] Codegen: `maxreal` → `ldc2_w Double.MAX_VALUE`
- [ ] Codegen: `minreal` → `ldc2_w Double.MIN_VALUE`
- [ ] Codegen: `maxint` → `ldc Integer.MAX_VALUE`
- [ ] Codegen: `epsilon` → `ldc2_w` machine epsilon (~2.220446049250313E-16)

## Milestone 11F — Integration and Tests

**Goal:** Integrate all environmental block features and validate with sample programs.

- [ ] Test: `pi.alg` and `pi2.alg` compile and print π to expected precision
- [ ] Test: `sqrt` of a negative number invokes `fault` (or returns NaN — document the choice)

---

## Milestone X — New Language Feature Samples

**Goal:** Each new feature sample in `test/algol` compiles and runs, demonstrating correct implementation of the corresponding language feature.

### recursion_euler.alg
- [ ] Grammar: procedure declarations with real/integer parameters and return types
- [ ] Grammar: nested blocks, for loops, and arithmetic expressions
- [ ] Grammar: procedure calls as expressions
- [ ] Codegen: recursive procedure calls
- [ ] Codegen: real arrays with nonzero lower bounds
- [ ] Codegen: correct handling of real/integer types in expressions and assignments
- [ ] Test: assert output matches expected result for a sample input

### boolean_operators.alg
- [ ] Grammar: boolean variable declarations and assignment
- [ ] Grammar: `and`, `or`, `not` operators (and synonyms)
- [ ] Grammar: if/then/else with boolean expressions
- [ ] Codegen: boolean logic (JVM int 0/1 or boolean)
- [ ] Test: assert output is correct for boolean logic

### real_array.alg
- [ ] Grammar: real array declarations with arbitrary bounds
- [ ] Grammar: array subscript assignment and access
- [ ] Codegen: real array allocation and access (with lower bound offset)
- [ ] Test: assert output matches expected real values

### string_output.alg
- [ ] Grammar: outstring with string and variable arguments
- [ ] Codegen: string concatenation or multi-argument output
- [ ] Test: assert output matches expected formatted string

### own_variables.alg
- [ ] Grammar: own variable and own array declarations
- [ ] Codegen: static/persistent local variables (retain value across block re-entry)
- [ ] Test: assert own variables retain values as specified

### switch_declaration.alg
- [ ] Grammar: switch declarations and designational expressions
- [ ] Codegen: multi-way goto using switch
- [ ] Test: assert correct label selection and control flow

---

These milestones should be addressed after the current core milestones, and may be split further as implementation progresses. Each feature should be covered by a dedicated test to ensure correct parsing, code generation, and runtime behavior.

---

## Future Milestones (not yet sequenced)

- `jen.alg` — call-by-name (Jensen's device); requires thunk implementation
- `pi.alg` / `pi2.alg` — `real` procedures; `sqrt` standard function
- `manboy.alg` — deep recursion + procedure references + call-by-name
- Standard math functions (`abs`, `sqrt`, `sin`, `cos`, `ln`, `exp`, etc.)
- Standard I/O (`ininteger`, `inreal`, `instring`)
- Error handling (`fault` procedure)

# The following features are now tracked as explicit milestones above and have been removed from this list:
# - Recursion (recursion_euler.alg)
# - Boolean operators (boolean_operators.alg)
# - Real arrays (real_array.alg)
# - String output (string_output.alg)
# - Own variables (own_variables.alg)
# - Switch declarations (switch_declaration.alg)

---

## Infrastructure TODOs (any milestone)

- [ ] Replace deprecated `ANTLRInputStream` with `CharStreams.fromReader()`
- [ ] Write `.j` Jasmin files to a configurable output directory (not hardcoded)
- [x] Add integration test helper to invoke Jasmin and run the resulting `.class`
- [ ] Decide on output directory structure for compiled classes

---

# AI-Friendly Compiler Design: Implementation Priorities

To ensure long-term maintainability and enable advanced tooling/AI workflows, the following improvements are prioritized for implementation. **High-priority items should be addressed before or alongside Milestone 2. Lower-priority items can be added incrementally with minimal rework.**

## High Priority (Before/With Milestone 2)
- Deterministic Jasmin output: canonical label naming, stable ordering of methods/fields, reproducible output for identical input.
- Modular two-pass architecture: clear separation of symbol table and codegen, enabling future IR/diagnostic hooks.
- Minimal structured diagnostics: error reporting with file, line, column, and stable error codes (as Java objects, even if not yet JSON). Allow multiple errors per run.
- Snapshot/golden tests: verify Jasmin output and diagnostics are stable and deterministic.

## Lower Priority (Can Be Added Any Time)
- Full structured JSON diagnostics: machine-readable output, fix-it suggestions, deterministic ordering.
- CLI options to emit AST, IR, or JVM IR for inspection/tooling.
- Compile-time stack analysis and mapping of JVM verifier errors to Algol source.
- Consistent debug metadata: line number tables, local variable tables, source-to-bytecode mapping.
- Modern CLI commands: `check`, `emit-jasmin`, `emit-ast`, `emit-jvmir`, etc.
- Versioned diagnostic schemas, stable IR formats, and LSP (Language Server Protocol) integration.

**Recommendation:**
Focus on the high-priority items as you implement Milestone 2. The rest can be layered on with minimal disruption once the core architecture is in place.
