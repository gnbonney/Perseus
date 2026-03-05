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

**Channel parameter for input procedures:**
- The channel argument is intended to select the input source, analogous to output procedures.
- In the current JVM implementation, only `System.in` is available for console input; there is no direct equivalent to `System.out`/`System.err` for input streams.
- For now, all input procedures ignore the channel parameter and read from `System.in`.
- If the channel argument is not a compile-time constant integer, codegen emits a warning comment and defaults to `System.in`.
- Future implementations may support additional input sources (e.g., files, sockets) mapped to channel values, but this is outside the scope of the current design.

**Design note for input:** A shared `Scanner` instance should be created once (as a static field on the generated class) rather than constructed per call. Channel selection is reserved for future extensibility.

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

---

## File I/O Extensions

> **Note:** The following procedures and syntax are **extensions** to the Algol 60 Modified Report. They are not part of the standard, but are necessary for practical, real-world compiler implementations. These extensions are inspired by historical Algol compilers and modern language design, and are discussed in detail in Algol Extensions.md.

To support file input/output and more meaningful channel usage, the following procedures are implemented by JAlgol:


| Procedure | Syntax | Description |
|---|---|---|
| `openfile` | `openfile(channel, filename, mode)` | **Extension.** Opens a file and associates it with a channel. `mode` is typically "r" (read), "w" (write), or "a" (append). |
| `closefile` | `closefile(channel)` | **Extension.** Closes the file associated with the channel. |
| `instring` | `instring(channel, var)` | **Extension.** Reads a string from the stream or file mapped to the channel. |

**Note:** String variables are an extended feature in JAlgol and many historic Algol compilers. The absence of a standard string type in Algol 60 is the reason why an `instring` procedure was not part of the original language specification. JAlgol's `instring` extension relies on the presence of string variables and associated operations. For rationale and historical context, see Algol Extensions.md.

**Extension semantics:**
- The standard I/O procedures (`outstring`, `outinteger`, `outreal`, `ininteger`, `inreal`, etc.) are extended to support file and stream channels opened with `openfile`/`closefile`.
- Channels 0 and 1 are reserved for `System.err` and `System.out` (stderr and stdout).
- Higher channel numbers (e.g., 2+) can be dynamically assigned to files or other streams via `openfile`.
- All I/O procedures accept a channel parameter, which determines the target stream or file.
- Error handling for invalid channels, file not found, or permission errors should be integrated with the `fault` procedure.
- These extensions are not defined in the Algol 60 Modified Report, but are essential for modern usability and compatibility with historical Algol implementations.

**Example usage:**
```algol
openfile(2, "output.txt", "w");
outstring(2, "Hello, file!");
closefile(2);
```

**Extension semantics:**
- Channels 0 and 1 retain their standard meaning (`System.err` and `System.out`).
- Higher channel numbers (e.g., 2+) can be dynamically assigned to files or other streams via `openfile`.
- All I/O procedures accept a channel parameter, which determines the target stream or file.
- Error handling for invalid channels, file not found, or permission errors should be integrated with the `fault` procedure.
- These extensions are not defined in the Algol 60 Modified Report, but are essential for modern usability and compatibility with historical Algol implementations.

For rationale and historical context, see Algol Extensions.md.
