# Perseus Language Design

This document collects design notes for language features that extend, reinterpret, or modernize Perseus's Algol heritage. Some of these features are inspired by historical Algol-family compilers; others are new directions intended to make Perseus more practical on the JVM.

## Standards Scope and Intentional Omissions

Perseus is rooted in the Algol 60 tradition, but it is not committed to implementing every standard feature if a different design direction makes better sense for the language on the JVM.

One important example is **label and switch parameters with designational exits**. These are part of the Algol 60 standard, but they are also one of the least natural features to carry forward into a modern JVM-oriented language. With structured exceptions now part of Perseus, the language has a clearer and more practical mechanism for many non-local control-flow situations that older Algol code handled through passed labels and designational exits.

For that reason, Perseus may intentionally leave this part of the standard unimplemented. That should be understood as a conscious scope decision, not an accidental gap. The Algol standards themselves leave room for different implementation levels and representations, and Perseus can remain an Algol-derived language without treating every standards-completeness milestone as equally important to its long-term identity.

## Arrays

Perseus arrays follow Algol-family conventions rather than Java's array syntax and semantics.

- A declaration gives explicit index bounds, not just a length.
- Lower bounds do not have to be zero; they may be one-based or even negative.
- Multidimensional arrays are written as a single array with multiple bound pairs, for example `integer array a[-1:0, 1:2];`.
- Access uses comma-separated subscripts such as `a[i, j]`, not chained Java-style indexing like `a[i][j]`.

That means a Perseus array declaration describes an abstract index space, while a Java array mainly describes storage with zero-based offsets. Perseus preserves the Algol view at the language level and hides the offset arithmetic from the programmer.

On the JVM, Perseus currently lowers declared arrays to ordinary JVM arrays and performs the bound normalization in generated code. Non-zero lower bounds are handled by subtracting the declared lower bound, and multidimensional arrays are flattened in row-major order rather than emitted as Java arrays-of-arrays. This keeps the surface language close to Algol while still mapping cleanly onto JVM bytecode.

## Strings

Many historic Algol compilers (e.g., NU Algol, Data General Extended Algol, Algol W, Simula) introduced a string type or class, often as a variable-length array of characters or a record with a length and character array. Simula's `Text` class and Algol W's `string` type are notable examples.

- **NU Algol:** Supported `STRING` and `STRING ARRAY` variables.
- **Algol W:** Had a `string` type and string operations.
- **Simula:** Used a `Text` class with similar semantics.
- **DEC Algol:** Included string handling extensions.

## Strings in Perseus

- **Type:** `string`
- **Semantics:** Strings are mutable, variable-length, and support slicing, concatenation, and assignment.
- **Example declaration:**  
	`string s;`
- **Example assignment:**  
	`s := "Hello, world!";`
- **String literals:** Double-quoted, as in `"example"`.
- **Operations:**  
	- `length(s)` returns the number of characters.
	- `s[i]` accesses the i-th character (1-based).
	- `concat(s1, s2)` returns a new string with the contents of `s1` followed by `s2`.
	- `substring(s, start, end)` returns a substring from `start` to `end` (inclusive).
	- `instring(channel, s)` reads a line or delimited string from the specified channel into `s`.
	- `outstring(channel, s)` writes the string to the specified channel.

## Implementation

For Perseus, we've decided the best approach is to generate code that uses Java String (or StringBuilder) for storage, but provide static utility methods (injected as needed) for Algol-like operations such as 1-based indexing, slicing, and concatenation. This approach:
- Keeps zero external dependencies (no runtime JAR required)
- Maximizes Java interoperability (native compatibility with Java APIs and external procedures)
- Maintains performance (leverages JVM-optimized String/StringBuilder)
- Preserves Algol semantics (via helper methods for 1-based indexing and mutability)
- Allows future migration to a runtime library if advanced features are needed

This balances historical fidelity, performance, Java integration, and ease of distribution.

## Example: Algol String Usage

```algol
string s;
s := "Hello, world!";
integer i;
i := length(s);           % i = 13
outstring(1, s[1]);       % outputs 'H' (first character)
outstring(1, substring(s, 8, 12));  % outputs 'world'
s[7] := 'W';              % changes 'w' to 'W', so s becomes "Hello, World!"
s := concat(s, "!!!");   % s is now "Hello, World!!!"
```

## File I/O

The original Algol 60 standard, and even the Modified Report, do not specify a standard mechanism for file input/output.  I/O in Algol 60 is limited to channels, which are left implementation-defined and typically mapped to standard input/output devices (e.g., teletype, punch cards, or console streams).  File I/O was handled in a variety of incompatible ways by different compilers, and the standard explicitly leaves the mapping of channels to devices or files outside its scope.

Perseus's current design (see Environmental-Block.md) maps channel numbers to Java streams (`System.out` for channel 1 and `System.err` for channel 0).  This is sufficient for standard output and error, but does not address file I/O.  Some recommendations for extending this:

* **File Open/Close Procedures:** Introduce procedures such as `openfile(channel, filename, mode)` and `closefile(channel)` to manage file streams.  These would register a mapping from a channel number to a Java `PrintStream` or `BufferedReader`, enabling subsequent I/O procedures to use the channel as a file handle.
* **Backward Compatibility:** Retain the current behavior for channels 0 and 1 (stderr/stdout), but allow higher channel numbers to be dynamically assigned to files.
* **Error Handling:** Define clear error semantics for invalid channel use, file not found, or permission errors, possibly by integrating with the `fault` procedure.

For example (proposed syntax):

```algol
openfile(2, "output.txt", "w");
outstring(2, "Hello, file!");
closefile(2);
```


This approach is consistent with the Modified Report's philosophy of implementation-defined channel-to-device mapping, while bringing Perseus closer to modern I/O expectations.  It mirrors patterns already seen in Simula 67 (file, infile, outfile classes), NU Algol (FORMAT and LIST declarations), and DEC Algol (comprehensive file I/O).

## Formatted I/O (Hybrid Design)

Many historic Algol compilers provided formatted I/O via FORMAT statements or format strings, but the syntax and semantics varied widely. Modern JVM languages use `String.format` or similar mechanisms. To balance authenticity and practicality, Perseus proposes a **hybrid formatted I/O system**:

* **User-facing syntax:** Algol-style format strings, inspired by historic FORMAT statements but simplified for ease of use.
* **Implementation:** Internally, these format strings are translated to Java `String.format` patterns, leveraging the JVM's formatting power while allowing Algol-like code.

## Rationale

- **Historic fidelity:** Algol users expect FORMAT-like capabilities, e.g., field width, decimal places, alignment.
- **Modern power:** Java's `String.format` is robust, internationalized, and well-tested.
- **Simplicity:** Translating a subset of Algol-style format strings to Java format patterns allows users to write familiar code, while the compiler handles the mapping.

## Proposed Syntax

Introduce new procedures:

- `outformat(channel, format, arg1, arg2, ...)` — outputs formatted text to the given channel.
- `informat(channel, format, var1, var2, ...)` — reads formatted input from the given channel (future/optional).

**Format string syntax:**

- Use a simplified Algol-inspired format string, e.g., `"I5, F8.2, A10"` (integer, float, alphanumeric fields).
- Each field specifier maps to a Java format code:
	- `I5` → `%5d` (integer, width 5)
	- `F8.2` → `%8.2f` (float, width 8, 2 decimals)
	- `A10` → `%10s` (string/alphanumeric, width 10)
- Commas or spaces separate fields.

The compiler parses the format string, translates it to a Java format string, and emits a call to `String.format`.

## Example Usage

```algol
integer i; real x; string s;
i := 42; x := 3.14159; s := "Algol";
outformat(1, "I5, F8.2, A10", i, x, s);
% Output:   "   42    3.14      Algol"
```

## Translation Example

- Algol format: `"I5, F8.2, A10"`
- Java format:  `"%5d %8.2f %10s"`
- Emitted code: `System.out.print(String.format("%5d %8.2f %10s", i, x, s));`

## Extension and Compatibility

- The format string parser can be extended to support more field types (e.g., scientific, hex, left/right alignment).
- For backward compatibility, `outstring`, `outinteger`, etc., remain available for simple output.
- This approach allows easy integration with file I/O channels (e.g., `outformat(2, ...)` for file output).

## Future Directions

- Input formatting (`informat`) can be added later, mapping format strings to Java `Scanner` or regex-based parsing.
- Advanced features (e.g., locale, grouping, error handling) can be layered on top as needed.

---

## String Channels and sprintf-style Output

Perseus supports associating a channel with a string variable, enabling output procedures to write directly to a string buffer. This provides the equivalent of `sprintf` in C or `StringWriter` in Java, and is a natural extension of the channel-based I/O model.

## Procedures

| Procedure | Syntax | Description |
|---|---|---|
| `openstring` | `openstring(channel, stringvar)` | **Extension.** Associates a channel with a string variable as a writable buffer. Output to this channel is appended to the string, enabling formatted string construction. |
| `closefile` | `closefile(channel)` | **Extension.** Closes the string buffer associated with the channel. |

## Example Usage

```algol
string buf;
openstring(5, buf);  % channel 5 writes to buf
outformat(5, "I4, F6.2", 42, 3.14);
closefile(5);
% buf now contains "  42  3.14"
```

## Rationale and Semantics

- Channels 0 and 1 retain their standard meaning (`System.err` and `System.out`).
- Higher channel numbers (e.g., 2+) can be dynamically assigned to files or other streams via `openfile`, or to string buffers via `openstring`.
- All I/O procedures accept a channel parameter, which determines the target stream, file, or string buffer.
- When a channel is mapped to a string variable, output procedures append to the string, enabling formatted string construction (like `sprintf` in C or `StringWriter` in Java).
- Error handling for invalid channels, file not found, or permission errors should be integrated with the `fault` procedure.
- These extensions are not defined in the Algol 60 Modified Report, but are essential for modern usability and compatibility with historical Algol implementations.

For rationale and historical context, see Environmental-Block.md.

---
## External Procedures

NU Algol had external procedures while Simula 67 had external classes and external procedures. They had to be declared in the program they were being used in, somewhat like an import statement in a Java class. In the Simula 67 standard, external classes and external procedures were considered "program modules".

For Perseus on the JVM, it helps to distinguish two different use cases that both look "external" from the source language:

1. Calling a Perseus procedure that was compiled from a different source file and therefore lives in a different generated JVM class.
2. Calling a method defined in some other JVM language, primarily Java.

Those two cases should not be treated as identical, because they have different semantic expectations. Cross-file Perseus linkage is mostly a separate-compilation problem, while Java interop is a foreign-interface problem.

Perseus should therefore treat external procedures as a phased feature rather than one monolithic interoperability milestone. The simplest and most robust first step is separate compilation and explicit calls to static JVM entry points. Richer cases such as instance-method interop, call-by-name across compilation units, and imported classes should come later, after the ABI is documented more formally.

## Proposed Syntax

Perseus should make the target model explicit:

```algol
external(Package.ClassName) real procedure f(real x);
external java static(java.lang.Math) real procedure cos(real x);
external java virtual(java.lang.System.out, java.io.PrintStream) procedure print(string s);
```

The intent is:

- `external(...)`
  - Calls a procedure previously compiled from Perseus into another generated JVM class.
  - Uses Perseus's own notion of procedures, type coercions, and return conventions.
- `external java ...`
  - Calls a JVM member intended for Java-style interop.
  - Uses a stricter, Java-friendly subset of parameter passing.

This split keeps the common Perseus-to-Perseus case lightweight while still making Java interop explicit. It also leaves room for a later Simula-inspired class extension, where imported JVM types could be declared more naturally as `external java class ...` instead of being modeled only as procedure targets.

For the first implementation, Perseus should prioritize `external(...)` and `external java static(...)`. The `virtual(...)` form is still a plausible direction, but it should be treated as a later phase rather than part of the minimum external-procedure milestone.

## Resolution and Classpath

External procedure declarations should resolve against the ordinary JVM classpath rather than inventing a separate Perseus-specific search mechanism.

- Compiled external Perseus classes and Java classes should both be found on the classpath.
- The CLI should therefore grow an explicit classpath option such as `--classpath` / `-cp`.
- For a first implementation, Perseus should prefer compile-time resolution and emit diagnostics when a referenced class or method cannot be found.
- The CLI should not assume one fixed JVM package forever. A user-facing package option is needed so separately compiled Perseus units can choose stable class names intentionally.

This gives users a predictable workflow:

1. Compile a Perseus library or obtain the target JVM classes.
2. Put the output directory or JAR on the classpath.
3. Compile the dependent Perseus program against that classpath.

That model is already familiar to JVM users and keeps external Perseus linkage and Java interop conceptually aligned.

## External Perseus

`external(TargetClass)` is intended for separate compilation. A program in one file should be able to call a Perseus procedure whose definition was compiled into another generated class.

Example:

```algol
external(mylib.Numeric) real procedure hypot(real a, b);
```

This should lower to a call to the generated static entry point in `mylib/Numeric`, using the same conventions Perseus already uses internally for ordinary procedure calls.

### Design Goals

- Allow one Perseus compilation unit to call procedures defined in another.
- Keep the mental model close to "this is still a Perseus procedure", not "this is Java FFI".
- Reuse Perseus's existing procedure machinery where possible.

### Resolution Model

For `external(TargetClass)`, the class name comes from `TargetClass` and the method name comes from the declared Perseus procedure identifier. In other words:

```algol
external(mylib.Numeric) real procedure hypot(real a, b);
```

means "look for a static JVM entry point named `hypot` in class `mylib.Numeric` whose descriptor matches the declared Perseus signature."

The first implementation should require an exact match. Separate compilation only works well if the external signature is checked explicitly instead of being left to fail later with a JVM linkage error.

### Restrictions

Even for external Perseus linkage, the first version should stay conservative:

- No label parameters.
- No switch parameters.
- No non-local `goto` across compilation-unit boundaries.
- No procedure-value parameters in the first version.
- No call-by-name interop in the first version unless the callee signature can be described exactly in Perseus terms.

That last point matters because Perseus's current call-by-name lowering depends on generated `Thunk` classes and environment-bridging conventions. It is possible to support cross-file Perseus call-by-name eventually, but only if the external declaration can fully describe the callee's thunk-based ABI. For an initial design, external Perseus procedures should therefore default to value-compatible signatures.

Arrays deserve to be called out separately. Historic Algol procedure libraries clearly used formal array parameters, and Perseus should support that style of separate compilation too. However, array parameters are still ABI-bearing rather than simple scalar value parameters, because the callee must agree with the caller about the JVM array representation, bounds metadata, and dimensionality. Perseus should therefore treat external Perseus arrays as a documented ABI case of their own rather than silently lumping them into "ordinary value passing".

## External Java

`external java` is for calling methods from Java or other JVM languages that expose ordinary JVM methods.

Examples:

```algol
external java static(java.lang.Math) real procedure cos(real x);
external java static(java.lang.Integer) integer procedure parseInt(string s);
external java virtual(java.lang.System.out, java.io.PrintStream) procedure print(string s);
```

Here `static(...)` names the owning class, while `virtual(targetExprType, ownerType)` is a sketch for calling an instance method through a receiver object. The exact surface syntax can still evolve, but the important point is that Java linkage should be explicit.

For the first implementation, only the `static(...)` form should be considered in scope. Instance-method linkage should wait until Perseus has either a more settled external-object story or class support of its own.

### Restrictions

External Java procedures should be deliberately narrower than ordinary Algol procedures:

- Only call-by-value-compatible parameters.
- No call-by-name parameters.
- No label parameters.
- No switch parameters.
- No `goto` semantics crossing the boundary.
- No dependence on caller environment or thunk refresh behavior.

In other words, `external java` should model a normal JVM method call, not attempt to export full Algol parameter-passing semantics onto Java.

## Parameter Passing Model

The design must take into account that Java passes arguments by value, while Algol has both call-by-value and call-by-name, and Simula adds call-by-reference.

For Perseus external procedures:

- `external java` should map only to call-by-value-compatible signatures.
- `external(...)` may eventually support richer conventions, but the first implementation should also be limited to signatures whose ABI is documented and stable.

That means the declaration itself should communicate that an external boundary is not the place to silently synthesize Jensen's Device semantics.

## Algol to Java Type Mapping

For `external java`, Perseus should define an explicit marshaling table instead of relying on ad hoc JVM coercions.

Recommended first-pass mapping:

| Algol type | Java/JVM view |
|---|---|
| `integer` | `int` |
| `real` | `double` |
| `Boolean` / boolean-valued expression | `boolean` |
| `string` | `java.lang.String` |
| `procedure` value | Not supported in first version, unless mapped explicitly to a JVM interface |
| `array` | Not supported in first version |
| `label` | Not supported |
| `switch` | Not supported |

Notes:

- `string` is already a natural interop case because Perseus's string design intentionally targets Java `String`.
- `integer -> real` widening may be allowed automatically where the target JVM signature expects `double`.
- `real -> integer` should **not** silently use Java's truncating cast if Perseus wants to preserve Algol-style rounding semantics. This boundary needs to be specified explicitly.
- Return values should follow the same mapping in reverse.

## Perseus to Perseus External Type Mapping

For `external(...)`, the mapping should follow Perseus's internal procedure ABI rather than Java source-language expectations.

For the first version, that likely means:

| Algol declaration type | Generated JVM form |
|---|---|
| `integer` parameter | `I` |
| `real` parameter | `D` |
| `string` parameter | `Ljava/lang/String;` |
| procedure return `integer` | `I` |
| procedure return `real` | `D` |
| procedure return `string` | `Ljava/lang/String;` |
| no declared return type | `V` |

This first table intentionally covers the scalar/string cases that already map cleanly onto JVM method descriptors. A later external-Perseus ABI table should add:

- one-dimensional array parameters, including hidden bounds
- multidimensional arrays if and when formal multidimensional arrays are supported
- procedure references
- call-by-name parameters via a stable thunk ABI

Those cases should not be promised until the ABI is documented and tested.

## ABI Note

Here "ABI" means the binary calling convention used by separately compiled code, not just the source-level procedure declaration. For Perseus external procedures, the ABI includes things like:

- the JVM method name and descriptor
- whether a procedure is expected to be `static`
- how arrays are represented and how bounds are passed
- how call-by-name thunks are represented
- which helper interfaces or companion classes must be present

This matters because two separately compiled Perseus units may have source declarations that look compatible while still failing to link correctly if the underlying binary conventions are not defined and kept stable.

## Lowering Strategy

### External Perseus

- Resolve the target generated class from the declaration.
- Emit a direct `invokestatic` to the generated procedure entry point.
- Apply the same Perseus-side coercions used for normal internal procedure calls.
- Require the external declaration to match the compiled Algol signature exactly.

### External Java

- Resolve the target class/member from the declaration.
- Type-check actual arguments against the external signature using the Algol-to-Java mapping table.
- Emit `invokestatic`, `invokevirtual`, or `invokeinterface` as appropriate.
- Apply only documented coercions at the boundary.

## External Procedure Scope

### Initial External Scope

- `external(TargetClass)` for scalar/string procedures with exact signature matching
- `external java static(TargetClass)` with explicit Algol-to-Java type mapping
- compile-time classpath-based resolution and diagnostics
- no procedure values, no call-by-name, no labels, no switches
- a user-facing way to choose the generated JVM package/class path for separately compiled Perseus units

### External Perseus Arrays

- document the array ABI explicitly
- support one-dimensional array parameters across compilation units
- verify bounds-passing conventions at compile time
- keep the initial scope to one-dimensional arrays whose representation already matches Perseus's current formal-array passing model
- treat an external Perseus array parameter as a JVM array reference plus hidden lower/upper bound integers, just as current internal procedure calls do
- require exact agreement on element type and array rank
- defer multidimensional formal arrays until the compiler supports a stable documented external multidimensional array ABI

Representative driver examples for this work come from historic Algol-style library procedures such as `INIVEC`, where a separately compiled procedure mutates a caller-supplied array through a formal array parameter.

### Later External Work

- external Perseus call-by-name once the thunk ABI is frozen and documented
- Java instance methods
- richer object/class interop, likely after Perseus class support

## Rationale

This split gives Perseus a cleaner long-term story:

- `external(...)` solves separate compilation and library reuse for Perseus code.
- `external java` solves JVM ecosystem interop.
- The compiler does not need to pretend that Java methods support Algol call-by-name, labels, or designational control flow.

It also fits the current architecture well. Perseus already generates JVM-static procedure entry points and already distinguishes between ordinary value passing and thunk-based call-by-name lowering. External linkage should build on those realities instead of hiding them.

## Exceptions

Access to external Java procedures and classes strongly suggests a need for structured exception handling. Java methods may fail by throwing exceptions, and Perseus should have a source-level way to respond to those failures without forcing everything through `fault(...)` or process termination. A block-oriented exception extension also fits Algol's existing `begin ... end` structure better than importing Java's `try/catch` syntax directly.

## Design Goals

- Keep the syntax Algol-like and block-oriented.
- Support both Perseus-signaled conditions and caught Java exceptions from `external java`.
- Make the common case readable without forcing Java class names everywhere.
- Preserve lexical scoping and block structure.
- Allow a simple first implementation on the JVM using ordinary `try/catch`.

## Proposed Syntax

The simplest form attaches an exception part to a block:

```algol
begin
    ...
exception
    when IOError do
        outstring(0, "I/O failure");
    when EndOfFile do
        done := true
end
```

Handlers are tried from top to bottom. The first matching `when` clause handles the exception. If no clause matches, the exception propagates outward to an enclosing exception block. If none exists, the program fails with the default runtime behavior.

## Optional Bound Exception Variable

For richer handling, a handler may bind the caught exception to a variable:

```algol
begin
    ...
exception
    when java(java.io.IOException) as ex do
        outstring(0, exceptionmessage(ex))
end
```

This is especially useful for external Java interop, where the caller may want the exception message or specific exception-class discrimination.

The first implementation of bound exception variables should keep the model modest:

- `when ... as ex do ...` binds a catch variable for the duration of the handler
- the bound value should be treated as an exception reference, not as an arbitrary user-defined class instance
- the first useful operations should be helper-style inspection facilities such as `exceptionmessage(ex)` and `printexception(ex)`

This gives Perseus practical exception introspection without requiring the full external-object/member-access story to be finished first.

## Proposed Exception Names

Perseus should distinguish between:

- **Language-level exception names**
  - `IOError`
  - `EndOfFile`
  - `ArithmeticError`
  - `BoundsError`
  - `FaultError`
- **Java exception patterns**
  - `java(java.io.IOException)`
  - `java(java.lang.IllegalArgumentException)`
  - and similar fully qualified Java exception classes

The language-level names give Perseus code a portable vocabulary. The `java(...)` form gives precise control when interoperating with external JVM code.

The initial language-level exception names should mean:

- `IOError`
  - Raised for file, channel, stream, or other I/O failures that are not better described as end-of-file.
- `EndOfFile`
  - Raised when an input operation reaches end-of-file in a context where the caller is expected to handle it explicitly.
- `ArithmeticError`
  - Raised for arithmetic failures such as division by zero or similar runtime numeric errors that Perseus chooses to expose as catchable conditions.
- `BoundsError`
  - Raised for array-subscript or similar bounds violations. On the JVM this should normally lower to a small Perseus runtime exception class rather than exposing raw JVM array exceptions directly.
- `FaultError`
  - Raised when Perseus code triggers `fault(...)` in a catchable context, allowing structured recovery instead of immediate process termination when exception handling is in effect.

These names should be treated as part of the source-language contract even if the runtime implementation uses small JVM exception classes underneath.

### Initial JVM Mapping

The first implementation should use small Perseus runtime exception classes where
that gives a cleaner and more stable language contract, while still translating
relevant Java exceptions when appropriate.

- `IOError`
  - Usually raised from translated Java I/O failures such as `java.io.IOException`
  - May also be represented by a dedicated Perseus runtime `IOErrorException`
- `EndOfFile`
  - Usually raised by Perseus input/runtime logic rather than by one universal Java exception type
  - Should map to a dedicated Perseus runtime `EndOfFileException`
- `ArithmeticError`
  - May be raised from translated `java.lang.ArithmeticException`
  - May also be raised directly by Perseus runtime checks
  - Should map to a dedicated Perseus runtime `ArithmeticErrorException`
- `BoundsError`
  - Should normally map to a dedicated Perseus runtime `BoundsErrorException`
  - May also be translated from low-level JVM failures such as `ArrayIndexOutOfBoundsException`
- `FaultError`
  - Should map to a dedicated Perseus runtime `FaultErrorException`
  - Raised when `fault(...)` is treated as a catchable condition instead of immediate termination

This split keeps the source language stable while still letting the compiler and
runtime build on ordinary JVM exception machinery.

## Semantics

- `begin ... exception ... end` establishes a protected block.
- Normal completion skips the exception part entirely.
- If an exception is raised inside the protected block, control transfers to the first matching `when` clause.
- The handler runs in the lexical scope of the block, so it can inspect and update surrounding locals.
- After the handler finishes, control continues after the whole block, not back inside the point where the exception occurred.
- A handler may rethrow the exception with a future `raise`/`signal` statement if desired.

This gives Perseus a model closer to structured exception handling than to resumable conditions.

## Raising Exceptions

For completeness, the extension should eventually include an explicit way to raise exceptions in Algol code:

```algol
signal IOError;
signal java(java.lang.IllegalStateException, "bad state");
```

The initial implementation does not need to expose every constructor form immediately. It is enough if Perseus can:

- translate internal runtime problems into language-level exception names, and
- catch Java exceptions thrown from `external java` calls

before growing a richer explicit `signal` syntax.

## Interaction with Existing Procedures

- `fault(...)` can remain a simple terminate-with-error procedure for compatibility.
- Over time, some operations that currently go straight to `fault(...)` could instead raise language-level exceptions that user code may catch.
- This would let programs choose between:
  - fail-fast behavior
  - local recovery
  - translation of a low-level Java exception into a higher-level Algol condition

## Interaction with External Java

This extension is especially valuable for `external java`.

Example:

```algol
begin
    x := parseInt(s)
exception
    when java(java.lang.NumberFormatException) do
        x := 0
end
```

Without exception handling, Java interop would either:

- force the compiler to terminate on every thrown exception, or
- silently flatten Java exceptions into vague runtime failures

Neither is a good long-term design.

## JVM Lowering Strategy

The obvious lowering strategy is:

- compile the protected part of the block to JVM `try`
- compile each `when` clause to a corresponding `catch`
- translate language-level exceptions to small Perseus runtime exception classes
- allow `java(...)` handlers to catch matching JVM exception types directly

For example:

- `when IOError do ...`
  - catches `gnb.perseus.runtime.IOErrorException`
- `when java(java.io.IOException) as ex do ...`
  - catches `java/io/IOException`

This is straightforward, maps well to the JVM, and avoids inventing a continuation model.

For bound exception variables, the first JVM-level implementation path should be:

- store the caught exception object in a temporary handler-local reference
- make helper operations such as `exceptionmessage(ex)` and `printexception(ex)` lower to ordinary JVM calls on that object
- delay richer member-style syntax such as `ex.message` until exception objects and external-object access are better integrated with the broader class model

## Scope and Restrictions

To keep the first version robust:

- handlers should attach only to `begin ... end` blocks, not individual statements
- matching should be by exception name/class only, not arbitrary Boolean guard expressions
- handlers should not resume execution at the throw site
- the first implementation may omit `finally`/cleanup syntax
- the first bound-variable implementation may expose exception inspection through helper procedures rather than full object-member syntax

If a cleanup feature is desired later, it could be added in an Algol-flavored way such as:

```algol
begin
    ...
exception
    when IOError do ...
finally
    closefile(ch)
end
```

but this should be a later layer, not part of the minimum design.

## Summary

- A block exception part (`begin ... exception ... end`) is the most Algol-like shape.
- `when Name do ...` should handle language-level exceptions.
- `when java(Fully.Qualified.Exception) as ex do ...` should handle precise Java interop failures.
- `when ... as ex do ...` should first support practical inspection helpers such as `exceptionmessage(ex)` and `printexception(ex)`.
- JVM lowering is natural through ordinary `try/catch`.
- This extension would make external Java/class interop much safer and more expressive.

## Lambda Notation

Rutishauser proposed, in the Handbook for Automatic Computation - Description of Algol, an extension that would replace Jensen's-Device-style call-by-name array expressions with Church's lambda notation for arrays. This approach is based on Alonzo Church's lambda calculus, which is the mathematical foundation for anonymous functions ("lambda functions") in modern programming languages.

## Background
- In lambda calculus, a lambda expression defines an anonymous function.
- In programming, lambda functions (or closures) are direct descendants of this idea.
- In the context of Algol, using lambda notation for arrays means treating an array as a function from indices to values, and passing/using them as such.
- This generalizes indexed array access: instead of relying on a call-by-name expression such as `A[i]`, you pass a function (lambda) that computes the value for each index.

## Relation to Jensen's Device
Jensen's device allows you to pass an expression (like A[i]) by name, so that it is re-evaluated for each value of i. Lambda notation formalizes this by passing a function of i.

## Example (Conceptual Algol-like Syntax)

Suppose you want to sum the elements of an array A from 1 to N:

```algol
sum := 0;
for i := 1 step 1 until N do
		sum := sum + A[i];
```

With lambda notation, you could write a procedure that takes a function (lambda) as a parameter:

```algol
procedure sum_by(f, lo, hi);
		value lo, hi;
		integer lo, hi;
		real procedure f;
begin
		sum := 0;
		for i := lo step 1 until hi do
				sum := sum + f(i);
end;

% Call with a lambda for A[i]:
sum_by(lambda i: A[i], 1, N);
```

Or, for a more modern pseudo-Algol:

```algol
sum_by((i) => A[i], 1, N);
```

This makes the parameter-passing mechanism explicit and generalizes it to any computable function of the index, not just array lookups.

## Summary
- Church's lambda notation for arrays is a formal, functional way to generalize array access and Jensen's-Device-style call-by-name expressions using anonymous functions.
- It is the conceptual ancestor of lambda functions and closures in modern languages.

## Simula-Style Classes

Perseus should treat classes as a major language extension inspired by Simula, but not as a requirement to reproduce every historical Simula surface form verbatim. The design goal is to preserve the important Algol-to-Simula lineage while still allowing Perseus to adopt a cleaner JVM-oriented presentation where that is helpful.

## General Direction

- Perseus should borrow the core object model from Simula more than from Java.
- Perseus does not need to copy every historical Simula syntax choice exactly.
- The first class milestone should focus on fields, instance procedures, object creation, and method calls.
- Inheritance, prefixing, and process/coroutine features should be deferred until the basic object model is stable.

This means Perseus classes should be understood as Simula-inspired program units in an Algol-family language, not as a second copy of Java classes disguised with Algol keywords.

## Call-by-Value Default for Class Procedures

One important design decision should be made explicitly: procedures declared inside classes should default to call-by-value.

That choice is attractive for several reasons:

- It matches Simula's own direction rather than fighting it.
- It fits naturally with JVM method-call expectations.
- It makes object behavior easier to reason about in the presence of mutation and instance state.
- It reduces friction if Perseus is later used to host or adapt Simula-style code.

This also gives Perseus a useful semantic distinction:

- ordinary Algol-style standalone procedures may continue to follow classic Algol parameter-passing defaults
- class procedures may follow Simula-style method defaults

That split is not a compromise born only of implementation convenience. It reflects the historical evolution from Algol toward Simula while also giving Perseus a cleaner and more practical object model.

## Surface Syntax vs Semantics

Perseus should separate semantic inheritance from surface imitation.

- Semantically, classes should be close to Simula's idea of objects with fields and procedures.
- Syntactically, Perseus may choose a simpler or more regular form if that makes the language easier to parse, teach, and interoperate with on the JVM.

So the design question for classes is not "how can Perseus copy Simula exactly?" but rather:

- which Simula semantics are essential,
- which historical surface details are worth preserving,
- and which parts should be expressed in a distinct Perseus style.

## Initial Scope

The first class milestone should aim for:

- class declarations
- instance fields
- instance procedures
- explicit object creation
- dotted member/procedure access

The following should be postponed until later design passes:

- inheritance or prefixing
- virtual override rules beyond the minimum needed for method dispatch
- imported external class syntax beyond the basic roadmap hooks already planned
- process or coroutine features
- any attempt to make class methods participate in full classic Algol call-by-name semantics by default

## Summary

For Perseus, the best path is to make classes Simula-inspired in semantics, Perseus-specific in final surface syntax, and explicitly call-by-value by default inside the class world. That keeps the language aligned with its Algol-family history while supporting the broader project goal of evolving beyond a strict Algol 60 compiler.
