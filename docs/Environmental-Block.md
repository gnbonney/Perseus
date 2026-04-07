# Perseus Environmental Block Design

Every ALGOL 60 program executes inside a fictitious outermost block called the **environmental block**
(Modified Report §1, Appendix 2). It pre-declares all standard functions and procedures so that
programs can call them without declaring them. No explicit declarations are needed in the program source.

---

## Channel Mapping

The channel parameter (first argument of all I/O procedures) selects the target stream:

| Channel value | Target | Use |
|---|---|---|
| `0` | Standard error | Diagnostics and error output |
| `1` | Standard output | Normal program output (conventional default) |
| `2`+ | File or string buffer | **Extension.** Opened via `openfile` or `openstring` |

The channel argument may be a literal, variable, or other integer expression. Dynamic file-channel dispatch is now supported for channel expressions that evaluate to `2+`.

The Algol 60 Modified Report says the method of channel-to-device mapping is outside the scope of the language. Channel 0 maps naturally to standard error, letting programs write diagnostics cleanly separable from normal output:

```algol
outstring(0, "error: value out of range");   comment goes to standard error;
outinteger(1, result);                        comment goes to standard output;
```

---

## I/O Procedures

### Output

| ALGOL identifier | Signature (Algol 60) | Description |
|---|---|---|
| `outstring(channel, str)` | `procedure outstring(channel, str)` | Writes `str` to the given channel |
| `outinteger(channel, int)` | `procedure outinteger(channel, int)` | Writes `int` to the given channel |
| `outreal(channel, re)` | `procedure outreal(channel, re)` | Writes `re` to the given channel |
| `outchar(channel, str, int)` | `procedure outchar(channel, str, int)` | Writes the character at position `int` in `str` to the given channel |
| `outterminator(channel)` | `procedure outterminator(channel)` | Writes a field terminator (space) to the given channel |
| `outformat(channel, format, ...)` | `procedure outformat(channel, format, ...)` | **Extension.** Formatted output to the given channel (see Format String Examples below) |

**Notes (Algol 60 Modified Report, Appendix 2):**
- `outstring` is defined in terms of `outchar`. Perseus short-circuits this to a direct string output, which is semantically equivalent for well-formed strings.
- `outinteger` and `outreal` are specified to call `outterminator` after printing. Perseus currently omits this; the terminator is only required to separate successive numbers read back with `ininteger`.

### Input

| ALGOL identifier | Signature (Algol 60) | Description |
|---|---|---|
| `ininteger(channel, int)` | `procedure ininteger(channel, int)` | Reads an integer from the given channel into `int` |
| `inreal(channel, re)` | `procedure inreal(channel, re)` | Reads a real from the given channel into `re` |
| `inchar(channel, str, int)` | `procedure inchar(channel, str, int)` | Reads one character from the channel; sets `int` to its position in `str` |
| `informat(channel, format, ...)` | `procedure informat(channel, format, ...)` | **Extension.** Formatted input from the given channel (see Format String Examples below) |

Input procedures now read from standard input on the ordinary console path and from file channels opened with `openfile(channel, filename, "r")` for channels `2+`. File-channel reads now raise `EOFException` on end of file. String-channel input remains later work.

---

## Format String Examples

> **Extension.** `outformat` and `informat` are Perseus extensions, not part of the Algol 60 Modified Report. See Perseus Language Design.md for rationale and historical context.

The `outformat` and `informat` procedures accept a format string that specifies the width, type, and precision of each output field. Multiple fields are separated by commas or spaces. The current implemented output-side subset is `Iw[.m]`, `Fw.d`, `Ew.d`, `A`/`Aw`, `Lw`, `nX`, and `/`. `informat` still uses the narrower `I`, `F`, and `A` subset for now.

| Format specifier | Meaning | Example call | Example output |
|---|---|---|---|
| `I5` | Integer, width 5 | `outformat(1, "I5", 42)` | `   42` |
| `I5.3` | Integer, width 5, at least 3 digits with leading zeros | `outformat(1, "I5.3", 7)` | `  007` |
| `F8.2` | Real, width 8, 2 decimal places | `outformat(1, "F8.2", 3.14159)` | `    3.14` |
| `E10.2` | Real in scientific notation | `outformat(1, "E10.2", 1234.0)` | `  1.23e+03` |
| `A` | String with no fixed width | `outformat(1, "A", "Algol")` | `Algol` |
| `A10` | String, width 10 | `outformat(1, "A10", "Algol")` | `     Algol` |
| `L5` | Boolean/logical, width 5 | `outformat(1, "L5", true)` | ` true` |
| `2X` | Output 2 spaces | `outformat(1, "2X")` | `  ` |
| `/` | Line break | `outformat(1, "/")` | newline |
| `I4, F6.2` | Integer width 4, then real width 6 | `outformat(1, "I4, F6.2", 42, 3.14)` | `  42  3.14` |
| `A5, I3, F7.3` | String, integer, real | `outformat(1, "A5, I3, F7.3", "Test", 7, 2.718)` | ` Test  7  2.718` |

---

## Math Functions

All math functions are expression-position calls (function designators) that return a value.

| ALGOL identifier | Signature | Notes |
|---|---|---|
| `sqrt(E)` | `real procedure sqrt(E)` | The Modified Report (Appendix 2) specifies `fault` if E < 0 |
| `abs(E)` | `real procedure abs(E)` | |
| `iabs(E)` | `integer procedure iabs(E)` | |
| `sign(E)` | `integer procedure sign(E)` | Returns 1, 0, or −1 |
| `entier(E)` | `integer procedure entier(E)` | Largest integer not greater than E (true floor, not truncation) |
| `sin(E)` | `real procedure sin(E)` | E in radians |
| `cos(E)` | `real procedure cos(E)` | E in radians |
| `arctan(E)` | `real procedure arctan(E)` | Returns −π/2 to π/2 |
| `ln(E)` | `real procedure ln(E)` | The Modified Report (Appendix 2) specifies `fault` if E ≤ 0 |
| `exp(E)` | `real procedure exp(E)` | |
| `length(str)` | `integer procedure length(str)` | From the Modified Report string support. Returns the number of characters in `str` |
| `concat(a, b)` | `string procedure concat(a, b)` | **Extension.** Concatenates two strings |
| `substring(str, i, j)` | `string procedure substring(str, i, j)` | **Extension.** Returns a substring using Perseus string indexing rules |

---

## Constants

These read-only values are pre-declared in the environmental block and may be used in any expression.

| ALGOL identifier | Type | Value |
|---|---|---|
| `maxreal` | `real` | Largest representable real value |
| `minreal` | `real` | Smallest positive representable real value |
| `maxint` | `integer` | Largest representable integer value |
| `epsilon` | `real` | Machine epsilon: smallest real ε such that 1 + ε ≠ 1 |

---

## Control / Error Procedures

| ALGOL identifier | Signature | Behaviour |
|---|---|---|
| `stop` | `procedure stop` | Terminates execution immediately |
| `fault(str, r)` | `procedure fault(str, r)` | Raises a runtime exception carrying the fault message |

**Notes (Algol 60 Modified Report, Appendix 2):**
- `stop` is defined as a `goto` to a label outside the program.
- In the current JVM implementation, `fault` raises a runtime exception. Uncaught faults therefore terminate execution naturally, while exception handlers may recover if they choose to catch the resulting runtime exception.

---

## File I/O Extensions

> **Note:** The following procedures and syntax are **extensions** to the Algol 60 Modified Report. They are not part of the standard, but are necessary for practical, real-world compiler implementations. These extensions are inspired by historical Algol compilers and modern language design, and are discussed in detail in Perseus Language Design.md.

To support file input/output and more meaningful channel usage, the following procedures are implemented by Perseus:


| Procedure | Syntax | Description |
|---|---|---|
| `openfile` | `openfile(channel, filename, mode)` | **Extension.** Opens a file and associates it with a channel. `mode` is typically "r" (read), "w" (write), or "a" (append). |
| `closefile` | `closefile(channel)` | **Extension.** Closes the file or string buffer associated with the channel. |
| `instring` | `instring(channel, var)` | **Extension.** Reads a string from the stream or file mapped to the channel. |

**Note:** String variables are an extended feature in Perseus and many historic Algol compilers. The absence of a standard string type in Algol 60 is the reason why an `instring` procedure was not part of the original language specification. Perseus's `instring` extension relies on the presence of string variables and associated operations. For rationale and historical context, see Perseus Language Design.md.



**Extension semantics:**
- All standard I/O procedures work with file channels opened via `openfile`/`closefile`.
- Channels 0 and 1 are reserved for standard error and standard output.
- Channel numbers 2 and above can be assigned to files or string buffers.
- Invalid channel use, file not found, permission errors, and end-of-file conditions are exposed through direct Java-backed exceptions such as `IllegalStateException`, `IllegalArgumentException`, `IOException`, and `EOFException`.
- These extensions are not defined in the Algol 60 Modified Report, but are consistent with historical Algol compiler practice.

For string channel support (sprintf-style output), see the next section.


**Example usage:**
```algol
openfile(2, "output.txt", "w");
outstring(2, "Hello, file!");
closefile(2);
```

---

## String Channels and sprintf-style Output

Perseus supports associating a channel with a string variable, enabling output procedures to write directly to a string buffer. This provides the equivalent of `sprintf` in C or `StringWriter` in Java.

| Procedure | Syntax | Description |
|---|---|---|
| `openstring` | `openstring(channel, stringvar)` | **Extension.** Associates a channel with a string variable as a writable buffer. Output to this channel is appended to the string, enabling formatted string construction. |
| `closefile` | `closefile(channel)` | **Extension.** Closes the string buffer associated with the channel (also used for files). |

**Example usage:**
```algol
string buf;
openstring(5, buf);  % channel 5 writes to buf
outformat(5, "I4, F6.2", 42, 3.14);
closefile(5);
% buf now contains "  42  3.14"
```

**Extension semantics:**
- Channels 0 and 1 retain their standard meaning (standard error and standard output).
- Channel numbers 2 and above can be assigned to string buffers via `openstring` or to files via `openfile`.
- All I/O procedures work with string channels in the same way as file and console channels.
- These extensions are not defined in the Algol 60 Modified Report, but are consistent with historical Algol compiler practice.

For rationale and historical context, see Perseus Language Design.md.

---

## Compiled Standard Environment

Perseus now provides most of the environmental block through a real
always-available compiled standard environment rather than through compiler
name-recognition alone.

This standard environment is not one giant source file. It is split into focused support classes:

- `perseus.lang.MathEnv`
- `perseus.text.Strings`
- `perseus.io.TextInput`
- `perseus.io.TextOutput`
- `perseus.runtime.Faults`

This makes the environmental block:

- more testable as ordinary Perseus code,
- less dependent on hardcoded name recognition in the compiler,
- easier to document as a real library interface,
- and more consistent with Perseus's support for external procedures, classes, `namespace`, and standard classpath-based linkage.

At the current stage:

- `MathEnv`, `Strings`, `TextOutput`, `TextInput`, and `Faults` are already on
  the compiled standard-environment path.
- `stop` intentionally remains intrinsic.
- Dynamic channel state is now owned by the compiled `perseus.io.Channels` unit rather than a shared Java-side runtime helper.

Some JVM-facing details still use narrow Java helper classes where Perseus
source does not yet express the needed mechanism directly. That is now the
exception rather than the main model.

