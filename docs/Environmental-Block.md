# JAlgol Environmental Block Design

Every ALGOL 60 program executes inside a fictitious outermost block called the **environmental block**
(Modified Report §1, Appendix 2). It pre-declares all standard functions and procedures so that
programs can call them without declaring them. JAlgol implements this block not as generated ALGOL
code at runtime, but as **special-cased bytecode emission** in `CodeGenerator`, mapping each
standard identifier directly to the appropriate JRE call or JVM instruction sequence.

---

## Implementation Strategy

JAlgol recognises environmental-block identifiers at **code-generation time**, inside
`exitProcedureCall` (statement form) and `generateExpr` (expression/function-designator form).
No ALGOL source declarations are needed. No extra class file is produced.

The channel parameter (first argument of all I/O procedures) is a **compile-time constant integer**
that selects the target stream. JAlgol resolves it at code-generation time using the following
mapping:

| Channel value | Java stream | Use |
|---|---|---|
| `0` | `System.err` | Standard error |
| `1` | `System.out` | Standard output (conventional ALGOL default) |
| Any other value | `System.out` | Treated as standard output |

The channel argument must be a literal integer (or a constant expression reducible at compile time).
If it is a variable or non-constant expression, codegen emits a warning comment and defaults to
`System.out`. This restriction exists because Jasmin `getstatic` targets are determined at compile
time; dynamic stream dispatch would require a helper method.

**Rationale:** The Modified Report says the method of channel-to-device mapping is outside the
scope of the language. Using `System.err` for channel 0 maps naturally to Unix convention (fd 2 =
stderr) and lets ALGOL programs write diagnostics and error output cleanly separable from normal
output — for example:

```algol
outstring(0, "error: value out of range");   comment goes to stderr;
outinteger(1, result);                        comment goes to stdout;
```

---

## I/O Procedures

### Output

| ALGOL identifier | Signature (Algol 60) | JRE mapping | Status |
|---|---|---|---|
| `outstring(channel, str)` | `procedure outstring(channel, str)` | `channel(0→err, 1→out).print(String)` | ✅ implemented (channel ignored — defaults to out; channel-aware: future) |
| `outinteger(channel, int)` | `procedure outinteger(channel, int)` | `channel(0→err, 1→out).print(int)` | ✅ implemented (same caveat) |
| `outreal(channel, re)` | `procedure outreal(channel, re)` | `channel(0→err, 1→out).print(double)` | ✅ implemented (same caveat) |
| `outchar(channel, str, int)` | `procedure outchar(channel, str, int)` | `channel(0→err, 1→out).print(char)` — extract char at position `int` from `str` | ☐ future |
| `outterminator(channel)` | `procedure outterminator(channel)` | `channel(0→err, 1→out).print(' ')` (space) | ☐ future |

**Notes:**
- Per Appendix 2, `outstring` is defined in terms of `outchar`. JAlgol short-circuits this to a
  direct `PrintStream.print(String)` call, which is semantically equivalent for well-formed strings.
- Per Appendix 2, `outinteger` calls `outterminator` after printing the number. JAlgol currently
  omits the terminator call; this is acceptable because the ALGOL spec says the terminator is only
  required to separate successive numbers read back with `ininteger`. If `outterminator` is added
  it can emit `System.out.print(' ')`.
- Per Appendix 2, `outreal` similarly calls `outterminator`. Same note applies.

### Input

| ALGOL identifier | Signature (Algol 60) | JRE mapping | Status |
|---|---|---|---|
| `ininteger(channel, int)` | `procedure ininteger(channel, int)` | `new java.util.Scanner(System.in).nextInt()` | ☐ future |
| `inreal(channel, re)` | `procedure inreal(channel, re)` | `new java.util.Scanner(System.in).nextDouble()` | ☐ future |
| `inchar(channel, str, int)` | `procedure inchar(channel, str, int)` | scan one char; find its position in `str` | ☐ future |

**Design note for input:** Input channels are not yet mapped (no `System.in` equivalent for
channel selection). For now all input reads from `System.in`. A shared `Scanner` instance should
be created once (as a static field on the generated class) rather than constructed per call.

---

## Math Functions

All math functions are **expression-position** calls (function designators) returning a value.
They are handled in `generateExpr` when the expr is a `ProcCallExpr` whose name matches a
standard identifier.

| ALGOL identifier | Type | JRE mapping | Notes |
|---|---|---|---|
| `sqrt(E)` | `real procedure sqrt(E)` | `Math.sqrt(double)` → `invokestatic java/lang/Math/sqrt(D)D` | Appendix 2 calls `fault` if E < 0; we can `invokestatic` Math.sqrt (returns NaN for negative, which JVM handles gracefully) or add runtime check |
| `abs(E)` | `real procedure abs(E)` | `Math.abs(double)` → `invokestatic java/lang/Math/abs(D)D` | |
| `iabs(E)` | `integer procedure iabs(E)` | `Math.abs(int)` → `invokestatic java/lang/Math/abs(I)I` | |
| `sign(E)` | `integer procedure sign(E)` | inline: `E > 0 ? 1 : E < 0 ? -1 : 0` | or `(int)Math.signum(double)` |
| `entier(E)` | `integer procedure entier(E)` | `(int)Math.floor(double)` | Appendix 2: largest integer not greater than E (true floor, not truncation) |
| `sin(E)` | `real procedure sin(E)` | `Math.sin(double)` → `invokestatic java/lang/Math/sin(D)D` | E in radians |
| `cos(E)` | `real procedure cos(E)` | `Math.cos(double)` → `invokestatic java/lang/Math/cos(D)D` | E in radians |
| `arctan(E)` | `real procedure arctan(E)` | `Math.atan(double)` → `invokestatic java/lang/Math/atan(D)D` | returns −π/2 to π/2 |
| `ln(E)` | `real procedure ln(E)` | `Math.log(double)` → `invokestatic java/lang/Math/log(D)D` | Appendix 2 calls `fault` if E ≤ 0 |
| `exp(E)` | `real procedure exp(E)` | `Math.exp(double)` → `invokestatic java/lang/Math/exp(D)D` | |
| `length(str)` | `integer procedure length(str)` | `String.length()` → `invokevirtual java/lang/String/length()I` | for use inside `outstring` etc. |

**Required for:** `pi.alg` (`sqrt`), `pi2.alg` (`sqrt`), `recursion_euler.alg` (potentially `sqrt`).

---

## Constants

These are declared as read-only values in the environmental block. JAlgol will treat them as
special `VarExpr` names and inline their values at code-generation time.

| ALGOL identifier | Type | Value / JRE source |
|---|---|---|
| `maxreal` | `real` | `Double.MAX_VALUE` → `ldc2_w 1.7976931348623157E308` |
| `minreal` | `real` | `Double.MIN_VALUE` → `ldc2_w 5.0E-324` (smallest positive double) |
| `maxint` | `integer` | `Integer.MAX_VALUE` → `ldc 2147483647` |
| `epsilon` | `real` | `Math.ulp(1.0)` / `Double.MIN_NORMAL` → machine epsilon ≈ `2.220446049250313E-16` |

---

## Control / Error Procedures

| ALGOL identifier | Signature | Behaviour | JRE mapping |
|---|---|---|---|
| `stop` | `procedure stop` | Terminate execution immediately | `invokestatic java/lang/System/exit(I)V` with arg 0 |
| `fault(str, r)` | `procedure fault(str, r)` | Print error message and stop | `System.err.print(str + " " + r)` + `System.exit(1)` — `fault` always writes to `System.err` regardless of channel |

**Notes:**
- Appendix 2 implements `stop` as a `goto` to a label outside the program; on the JVM this
  maps cleanly to `System.exit(0)`.
- `fault` is called internally by `sqrt` (negative arg) and `ln` (non-positive arg). JAlgol can
  either suppress these checks (rely on JVM NaN/Infinity semantics) or emit inline guard code.

---

## Codegen Approach

Environmental identifiers are recognised **by name** in `CodeGenerator` — they are not entered
in `SymbolTableBuilder`'s symbol table (to avoid polluting user-visible scope or consuming JVM
local-variable slots). Recognition happens in two places:

1. **`exitProcedureCall`** — for void-returning procedures used as statements:
   `outstring`, `outinteger`, `outreal`, `outchar`, `outterminator`, `stop`, `fault`

2. **`generateExpr` → `ProcCallExprContext`** — for value-returning function designators:
   `sqrt`, `abs`, `iabs`, `sign`, `entier`, `sin`, `cos`, `arctan`, `ln`, `exp`, `length`,
   `ininteger`, `inreal`

3. **`generateExpr` → `VarExprContext`** — for constants (no argument list):
   `maxreal`, `minreal`, `maxint`, `epsilon`

This keeps the implementation entirely within `CodeGenerator` and requires no grammar changes.

---

## Implementation Priority

| Priority | Identifiers | Needed for |
|---|---|---|
| **Now** | `sqrt` | `pi.alg`, `pi2.alg` (Milestone X) |
| **Soon** | `abs`, `iabs`, `entier`, `sign` | `primes.alg`, general use |
| **Soon** | `sin`, `cos`, `arctan`, `ln`, `exp` | `primer4.alg` final values, future science samples |
| **Later** | `maxreal`, `minreal`, `maxint`, `epsilon` | Numerical guard code |
| **Later** | `stop`, `fault` | Error handling |
| **Later** | `ininteger`, `inreal`, `inchar`, `outchar`, `outterminator` | Interactive programs |
