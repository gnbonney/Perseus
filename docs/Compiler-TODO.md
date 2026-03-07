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

**11C.2 — String Variable Support (prerequisite for string I/O):**
- [x] Grammar: string variable declarations and assignment
- [x] SymbolTableBuilder: track string variables and scope
- [x] TypeInferencer: handle string types and type rules
- [x] Codegen: emit JVM code for string operations (assignment, indexing, concatenation, slicing) using Java String/StringBuilder and static helpers

**11C.3 — String Input Procedures (requires 11C.2):**
- [x] Codegen: `instring(channel, var)` — read a string from the stream or file mapped to the channel (**extension; requires string variable support**)

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

**Goal:** Build up to `manboy.alg` through incremental steps, each with simpler test programs.

### 13A — Procedure Variables (`proc_var.alg`) ✅

**Goal:** Simple program that declares a procedure variable and assigns/calls it.

**Features implemented:**
- [x] Grammar: procedure variable declarations (`procedure P;`)
- [x] Grammar: procedure references as expressions (allow procedure names in assignments)
- [x] SymbolTableBuilder: track procedure variables with "procedure:void" type
- [x] TypeInferencer: handle procedure types in VarExpr
- [x] Codegen: procedure references (store method references as objects)
- [ ] Test: assign procedure to variable and call through variable

### 13B — Procedure Parameters (`proc_param.alg`) ✅

**Goal:** Simple program that passes a procedure as a parameter.

**Features implemented:**
- [x] Grammar: procedure parameters in paramSpec (`procedure P;`)
- [x] SymbolTableBuilder: track procedure parameters with "procedure:void" type
- [x] Codegen: procedure parameters (pass method references)
- [x] Codegen: calling procedure parameters
- [ ] Test: pass procedure as argument and call it

### 13C — Typed Procedure References (`proc_typed_simple.alg`)

**Goal:** Program with typed procedure variables/parameters (real/integer procedures).

**Features implemented:**
- [x] Grammar: typed procedure declarations (`real procedure P(...)`)
- [x] Grammar: typed procedure variables (`real procedure P;`)
- [x] SymbolTableBuilder: track typed procedures ("procedure:real", "procedure:integer")
- [x] TypeInferencer: handle typed procedure types
- [x] Codegen: procedure references as values (store method references as objects)
- [x] Codegen: calling procedure references that return values
- [x] Test: real procedure variables and parameters

**Implementation Summary:**
- Synthetic classes generated for procedure references implementing RealProcedure/IntegerProcedure interfaces
- Procedure variables stored as Object references in local slots
- Procedure calls on variables use interface invocation with proper casting
- Separate .j files generated for each procedure reference class
- Test validates assignment of procedures to variables and calling through variables

### 13D — Deep Recursion (`deep_recursion.alg`)

**Goal:** Simple deep recursion test (factorial or similar) to validate stack handling.

**New features needed:**
- [ ] Codegen: deep recursion stack handling
- [ ] Test: recursive calls with sufficient depth

### 13E — Man or Boy (`manboy.alg`)

**Goal:** Full Man or Boy test with all features integrated.

**New features needed:**
- [ ] Integration of all procedure reference features
- [ ] Test: assert correct "Man or Boy" result (-67)

---

## Milestone 14 — Procedure Parameters and Real Arrays (`recursion_euler.alg`)

**Goal:** `recursion_euler.alg` compiles and runs, demonstrating procedure parameters (call-by-name), real arrays with nonzero bounds, and complex expressions.

**New features needed:**
- [ ] Grammar: procedure declarations with real/integer parameters and return types
- [ ] Grammar: nested blocks, for loops, and arithmetic expressions
- [ ] Grammar: procedure calls as expressions
- [ ] Codegen: recursive procedure calls
- [ ] Codegen: real arrays with nonzero lower bounds
- [ ] Codegen: correct handling of real/integer types in expressions and assignments
- [ ] Test: assert output matches expected result for a sample input

---

## Milestone 15 — Call-by-Name Procedures (`pi2.alg`)

**Goal:** `pi2.alg` compiles and runs, demonstrating call-by-name parameters in a real-procedure context.

**New features needed:**
- [ ] Grammar: parameterless procedure declarations (zero-arg procedure syntax)
- [ ] Codegen: call-by-name for real variable parameters (builds on Milestone 12 thunk support)
- [ ] Test: assert correct π approximation output

---

## Milestone 16 — Boolean Operators (`boolean_operators.alg`)

**Goal:** `boolean_operators.alg` compiles and runs with correct `or` and `not` behavior.

**New features needed:**
- [ ] Grammar: `or` / `not` operators (and synonyms)
- [ ] Codegen: boolean `or` and `not` instructions
- [ ] Test: assert output is correct for boolean logic

---

## Milestone 17 — Real Arrays (`real_array.alg`)

**Goal:** `real_array.alg` compiles and prints correct real array values.

**New features needed:**
- [ ] Grammar: real array declarations with arbitrary bounds
- [ ] Grammar: array subscript assignment and access
- [ ] Codegen: real array allocation and access (`daload`/`dastore` with lower bound offset)
- [ ] Test: assert output matches expected real values

---

## Milestone 18 — String Output (`string_output.alg`)

**Goal:** `string_output.alg` compiles and prints correct formatted string output.

**Note:** String variable support (grammar, symbol table, type inference, codegen) completed in Milestone 11C.2. `instring` completed in Milestone 11C.3. Remaining work is validating `outstring` with string variable arguments against the sample.

- [x] Grammar: string variable declarations and assignment
- [x] Codegen: string operations (assignment, indexing, concatenation, slicing)
- [x] Codegen: `instring` procedure
- [ ] Test: assert output matches expected formatted string for `string_output.alg`

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
# - Standard I/O (`instring`), string variables — ✅ Milestones 11C.2, 11C.3
# - Error handling (`fault` procedure) — ✅ Milestone 11D
# - `jen.alg` (call-by-name) — Milestone 12
# - `manboy.alg` (deep recursion + procedure refs) — Milestone 13
# - `recursion_euler.alg` (procedure parameters + real arrays) — Milestone 14
# - `pi2.alg` (call-by-name procedures) — Milestone 15
# - `boolean_operators.alg` — Milestone 16
# - `real_array.alg` — Milestone 17
# - `string_output.alg` — Milestone 18
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
