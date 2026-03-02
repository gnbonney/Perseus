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
- [ ] Grammar: `real` variable declarations (`real x, y, u;`)
- [ ] **Design and implement symbol table** — first-pass visitor that collects variable names, types, and block scope
- [ ] **Implement two-pass compile in `AntlrAlgolListener`** or split into separate `SymbolTableBuilder` and `CodeGenerator` listener classes
- [ ] Grammar: assignment statement (`:=`)
- [ ] Grammar: arithmetic expressions (`*`, `-`, `+`, `/`)
- [ ] Grammar: real number literals
- [ ] Codegen: declare local variables in Jasmin (`.limit locals`)
- [ ] Codegen: load/store local variables (`dload`, `dstore` for `real` → JVM `double`)
- [ ] Codegen: arithmetic instructions (`dmul`, `dsub`, `dadd`, `ddiv`)
- [ ] Codegen: integer-to-real coercion (`5/13` in Algol is real division)
- [ ] Test: assert final values of `x` and `y` are correct

---

## Milestone 3 — `goto` and Labels (`primer2.alg`)

**Goal:** `primer2.alg` parses and compiles (infinite loop — run to confirm no crash on structure).

**New features needed:**
- [ ] Grammar: label declarations (`AA:`)
- [ ] Grammar: `goto` statement
- [ ] Codegen: emit Jasmin labels and `goto` instructions
- [ ] Test: assert successful compilation (execution not checked — infinite loop)

---

## Milestone 4 — `if`/`then` (`primer3.alg`)

**Goal:** `primer3.alg` compiles and terminates after 1000 iterations.

**New features needed:**
- [ ] Grammar: `integer` variable declarations
- [ ] Grammar: `if <expr> then <statement>` (no `else`)
- [ ] Grammar: comparison operators (`<`, `>`, `<=`, `>=`, `=`, `<>`)
- [ ] Codegen: integer variables (`iload`, `istore`)
- [ ] Codegen: conditional jump instructions (`if_icmplt` etc.)
- [ ] Test: assert program terminates and produces correct output

---

## Milestone 5 — `for` Loop (`primer4.alg`)

**Goal:** `primer4.alg` compiles and runs 1000 iterations correctly.

**New features needed:**
- [ ] Grammar: `for <var> := <expr> step <expr> until <expr> do <statement>`
- [ ] Codegen: `for` loop with step/until semantics per the Modified Report
  - Note: `step` and `until` expressions are re-evaluated each iteration
- [ ] Test: assert correct final values of `x` and `y`

---

## Milestone 6 — Multiple Statements and `outreal` (`primer5.alg`)

**Goal:** `primer5.alg` compiles and prints the correct approximation of *e*.

**New features needed:**
- [ ] Grammar: multiple statements separated by `;` (already partially handled — verify)
- [ ] Codegen: `outreal(channel, expr)` — maps to `System.out.print(double)`
- [ ] Test: assert output matches expected value of *e* (≈ 2.7182818...)

---

## Milestone 7 — `if`/`then`/`else` and Boolean (`boolean.alg`)

**Goal:** `boolean.alg` compiles and prints `true`.

**New features needed:**
- [ ] Grammar: `boolean` variable declarations
- [ ] Grammar: `if <expr> then <statement> else <statement>`
- [ ] Grammar: boolean literals (`true`, `false`)
- [ ] Codegen: boolean variables (JVM `int`, 0/1)
- [ ] Codegen: if/then/else branch instructions
- [ ] Test: assert output is `"true"`

---

## Milestone 8 — Integer Arrays (`array.alg`)

**Goal:** `array.alg` compiles and prints correct subscript values.

**New features needed:**
- [ ] Grammar: array declarations (`integer array nArr[1:5]`)
- [ ] Grammar: subscript expressions (`nArr[i]`)
- [ ] Grammar: array bounds (lower bound may be non-zero)
- [ ] Codegen: allocate JVM int array (adjusting for non-zero lower bound)
- [ ] Codegen: `iaload`, `iastore` with bound offset
- [ ] Test: assert `nArr[5]` prints `5`, `nArr[3]` prints `0` (uninitialized)

---

## Milestone 9 — Nested Blocks and `outinteger` (`oneton.alg`)

**Goal:** `oneton.alg` compiles, calls the `oneton` procedure, and prints 1 through 12.

**New features needed:**
- [ ] Grammar: nested `begin`/`end` blocks with their own declarations
- [ ] Grammar: `integer procedure` declarations with `value` parameters
- [ ] Grammar: procedure call as a statement (already partially handled) and as an expression (assignment from call)
- [ ] Grammar: `outinteger(channel, expr)`
- [ ] Codegen: procedure declarations as static methods
- [ ] Codegen: `outinteger` → `System.out.print(int)`
- [ ] Codegen: return value from integer procedure
- [ ] Test: assert output is `1\n2\n...\n12\n` and `M` equals 24

---

## Milestone 10 — Boolean Operators and Sieve (`primes.alg`)

**Goal:** `primes.alg` compiles and prints all primes below 1000.

**New features needed:**
- [ ] Grammar: `boolean array` declarations
- [ ] Grammar: `and` / `&` boolean operator
- [ ] Grammar: `for ... while <expr> do` (while clause in for list)
- [ ] Grammar: `stop` / procedure call as a single statement without args
- [ ] Codegen: boolean array (JVM `boolean[]` or `int[]`)
- [ ] Codegen: `and`/`or`/`not` boolean instructions
- [ ] Test: assert first few and last primes in output

---

## Future Milestones (not yet sequenced)

- `jen.alg` — call-by-name (Jensen's device); requires thunk implementation
- `pi.alg` / `pi2.alg` — `real` procedures; `sqrt` standard function
- `manboy.alg` — deep recursion + procedure references + call-by-name
- `boolean.alg` — `not` operator, `or` operator
- `own` variables — static locals
- Switch declarations — multi-way `goto`
- Standard math functions (`abs`, `sqrt`, `sin`, `cos`, `ln`, `exp`, etc.)
- Standard I/O (`ininteger`, `inreal`, `instring`)
- Error handling (`fault` procedure)

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
