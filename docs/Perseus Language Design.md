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
- Reference-typed arrays are supported with `ref(T) array a[1:n];` when a program needs arrays of object references.

That means a Perseus array declaration describes an abstract index space, while a Java array mainly describes storage with zero-based offsets. Perseus preserves the Algol view at the language level and hides the offset arithmetic from the programmer.

On the JVM, Perseus currently lowers declared arrays to ordinary JVM arrays and performs the bound normalization in generated code. Here "lowers" means "translates a Perseus source construct into its underlying JVM form." Non-zero lower bounds are handled by subtracting the declared lower bound, and multidimensional arrays are flattened in row-major order rather than emitted as Java arrays-of-arrays. This keeps the surface language close to Algol while still mapping cleanly onto JVM bytecode.

## Looping Extensions

Perseus keeps the original Algol `for ... step ... until ... do` statement, and it now also provides a more modern structured loop surface:

```algol
while cond do
begin
    ...
    if skiprest then continue;
    if done then break;
    ...
end;

repeat
begin
    ...
until cond
```

These forms fit naturally with the broader Algol tradition, read more directly than a dedicated infinite-loop keyword, and still allow `break` and `continue` for early exit.

Perseus also now supports an array-iteration form:

```algol
integer value;
for value in a do
    ...
```

The current `for ... in ... do` rules are:

- the iteration variable must already be declared with a compatible type
- the traversed array expression is evaluated once at loop entry
- assigning to the iteration variable inside the body does not change which element is visited next
- numeric counting remains with the traditional Algol `for ... step ... until ... do` form rather than being folded into `for ... in ... do`

The traditional Algol `for` form remains intentionally general. Its `for`-list elements may need to be re-evaluated as the loop proceeds, and the generated JVM code naturally reflects that with multiple branches and temporary locals for control state. That is the right tradeoff for historical correctness and translation of classic Algol code, but it also means this form should not be treated as the preferred choice for the hottest possible inner loops when a simpler loop shape would do.

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

## Formatted I/O

Many historic Algol compilers provided formatted I/O, but there was no single standard Algol 60 format language. Perseus therefore treats formatted I/O as an intentional extension with a small descriptor-based syntax that is Algol-friendly, strongly reminiscent of classic Fortran formatting, and practical on the JVM.

The current implementation renders output-side Perseus format descriptors through compiled standard-environment code in `perseus.io.TextOutput`. That keeps the user-facing model compact while still letting the implementation use ordinary JVM interop where it fits naturally.

## Rationale

- **Historic fit:** descriptor-based field formatting feels appropriate for an Algol-family language even though it is not defined by the Modified Report.
- **Practicality:** Perseus is likely to be used for teaching, reporting, finance-style output, and ordinary business programs where fixed-width numeric and text formatting matters.
- **Conservatism:** the format language should stay small and teachable rather than becoming a full clone of Fortran's larger edit-descriptor system.
- **JVM leverage:** the chosen descriptors should map cleanly onto Java formatting behavior.

## Procedures And Descriptor Direction

Perseus keeps these as the user-facing formatted-I/O procedures:

- `outformat(channel, format, arg1, arg2, ...)` — outputs formatted text to the given channel.
- `informat(channel, format, var1, var2, ...)` — reads formatted input from the given channel (future/optional).

**Current implemented state:**

- `outformat` currently supports:
  - `Iw[.m]`
  - `Fw.d`
  - `Ew.d`
  - `A` and `Aw`
  - `Lw`
  - `nX`
  - `/`
- `informat` currently keeps the narrower `I`, `F`, and `A` subset.
- Commas or spaces separate descriptors.

This asymmetry is intentional. Formatted output remains useful for reports, tables, finance-style output, and teaching examples, while formatted input is a less central modern need. Real input data is more often delimited or structured than fixed-width, and richer input parsing is usually better handled through ordinary string processing or library interop than through a large built-in descriptor language. Perseus therefore keeps `informat` as a small convenience unless stronger real-world use cases later justify expanding it.

The current `informat` implementation also lives in compiled standard-environment code (`perseus.io.TextInput`), even though its descriptor family remains intentionally smaller than `outformat`.

### Expanded conservative direction

The current `I`, `F`, and `A` subset is a good base, but Perseus should grow the format language in a deliberately small and practical way rather than trying to reproduce the full breadth of Fortran edit descriptors.

Perseus should keep the same descriptor-family style and expand it to:

- `Iw[.m]`
  - integer output with field width `w`
  - optional `.m` gives a minimum digit count with leading zero padding
- `Fw.d`
  - fixed-point real output with width `w` and `d` digits after the decimal point
- `Ew.d`
  - scientific notation with width `w` and `d` digits after the decimal point
- `A` or `Aw`
  - string output with no fixed width or with field width `w`
- `Lw`
  - logical / Boolean output with width `w`
- `nX`
  - output `n` spaces
- `/`
  - line break

This selection is intentionally conservative.

It is broad enough for:

- educational examples
- scientific notation when needed
- business and finance-style reports
- fixed-width tabular output

It also avoids drifting into descriptors that are more useful for systems programming or specialized report writers than for Perseus's main expected use cases.

For now Perseus does not need to prioritize:

- `B`, `O`, `Z` for binary, octal, and hexadecimal integer formatting
- `D`, because Perseus does not have a separate source-level double-precision type distinct from `real`
- `EX...` hexadecimal floating-point formats
- `Tc` cursor-position formatting
- quoted literal directives inside the format string

Ordinary `outstring` calls already handle literal text clearly, so the format language does not need to absorb that job.

This gives Perseus a formatting system that is:

- more capable than the current minimal slice
- still small enough to learn quickly
- and still clearly distinct from implementing every historical Fortran edit descriptor

Format strings should continue to follow these rules:

- descriptors are written in a single string such as `"I5, F8.2, A10"`
- commas or spaces separate descriptors
- each non-layout descriptor consumes one argument or one input variable
- `nX` and `/` affect layout only and do not consume an argument
- `A` without a width uses the full string without padding

## Example Usage

```algol
integer i; real x; string s;
i := 42; x := 3.14159; s := "Algol";
outformat(1, "I5, F8.2, A10", i, x, s);
% Output:   "   42    3.14      Algol"
```

An expanded example in the same style would be:

```algol
integer i;
real x;
Boolean ok;
string name;

i := 42;
x := 3141.59;
ok := true;
name := "Algol";

outformat(1, "A, I5.3, 2X, F8.2, /, L5, 1X, E10.2", name, i, x, ok, x);
```

## Translation Example

- Algol format: `"I5, F8.2, A10"`
- Java format:  `"%5d %8.2f %10s"`
- Emitted code: `System.out.print(String.format("%5d %8.2f %10s", i, x, s));`

## Extension and Compatibility

- The chosen next descriptor family is `I`, `F`, `E`, `A`, `L`, `X`, and `/`, with optional zero-padding on `I`.
- The current `I`, `F`, and `A` forms remain valid as the stable baseline.
- For backward compatibility, `outstring`, `outinteger`, etc., remain available for simple output.
- This approach allows easy integration with file I/O channels (e.g., `outformat(2, ...)` for file output).

## Future Directions

- `informat` may continue to lag behind `outformat` in how much of the descriptor family is implemented, but both procedures should use the same descriptor vocabulary in the language design.
- Advanced features (e.g., locale, grouping, error handling) can be layered on top as needed.

---

## String Channels and sprintf-style Output

Perseus supports associating a channel with a string variable, enabling output procedures to write directly to a string buffer. This provides the equivalent of `sprintf` in C or `StringWriter` in Java, and is a natural extension of the channel-based I/O model.

## Procedures

| Procedure | Syntax | Description |
|---|---|---|
| `openstring` | `openstring(channel, stringvar)` | **Extension.** Associates a channel with a string variable as a writable buffer. Output to this channel is appended to the string, enabling formatted string construction. |
| `closefile` | `closefile(channel)` | **Extension.** Closes the string buffer associated with the channel. |

### Procedure Signature Surface

Perseus now supports a wider procedure-signature surface than classic scalar-only Algol subsets.

- Ordinary procedure declarations may be untyped (`procedure p;`) or may declare returns of `integer`, `real`, `string`, `boolean`, or `ref(T)`.
- Ordinary formal parameters may be declared as scalar `integer`, `real`, `string`, `boolean`, or `ref(T)` values, as arrays, or as procedure parameters where that part of the language is already supported.
- `boolean` procedure results are represented with the same JVM integer-style truth convention Perseus already uses for boolean expressions and variables.
- `ref(T)` procedure results and parameters are intended for object references, Java interop, and standard-library code that needs to move object values through ordinary Perseus procedures.

Examples:

```algol
boolean procedure positive(x);
    value x;
    integer x;

ref(StringBuilder) procedure builder(text);
    value text;
    string text;
```

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
- In the current implementation, that string-channel behavior is owned by the compiled `perseus.io.Channels` standard-environment unit rather than by compiler-emitted special append code.
- Error handling for invalid channels, file not found, or permission errors should be integrated with the `fault` procedure.
- These extensions are not defined in the Algol 60 Modified Report, but are essential for modern usability and compatibility with historical Algol implementations.

For rationale and historical context, see Environmental-Block.md.

---
## External Libraries, Classes and Procedures

NU Algol had external procedures while Simula 67 had external classes and external procedures. They had to be declared in the program they were being used in, somewhat like an import statement in a Java class. In the Simula 67 standard, external classes and external procedures were considered "program modules".

For Perseus on the JVM, "external" now covers a broader family of interop cases:

1. Calling a Perseus procedure that was compiled from a different source file and therefore lives in a different generated JVM class.
2. Referring to a separately compiled Perseus class or library unit through explicit external linkage.
3. Calling a method defined in some other JVM language, primarily Java.
4. Referring to Java fields, constants, and object-valued bindings that are meant to be used directly in Perseus source.

These cases should not be treated as identical, because they have different semantic expectations. Cross-file Perseus linkage is mostly a separate-compilation problem, while Java interop is a foreign-interface problem. Java methods, Java fields, and imported Java object values also bring different resolution and member-access needs even within the Java interop side of the design.

Perseus treats external procedures as an incremental feature rather than one monolithic interoperability mechanism. The simplest and most robust foundation is separate compilation and explicit calls to static JVM entry points. Richer cases such as call-by-name across compilation units still come later, after the ABI is documented more formally.

This broader interop area covers:

- separately compiled Perseus libraries reached through `external(...)`
- imported JVM classes declared with `external java class ...`
- imported Java procedures, fields, and object values reached through `external java ...`

### External Procedure Syntax

Perseus makes the target model explicit:

```algol
external(Package.ClassName) real procedure f(real x);
external(Package.ClassName) boolean procedure ready(ref(Stream) s);
external java static(java.lang.Math) real procedure cos(real x);
external java static(java.util.Objects) boolean procedure isNull(ref(Object) candidate);
external java static(java.lang.System) ref(java.io.PrintStream) out as stdout;
```

The intent is:

- `external(...)`
  - Calls a procedure previously compiled from Perseus into another generated JVM class.
  - Uses Perseus's own notion of procedures, type coercions, and return conventions.
- `external java ...`
  - Calls a JVM member intended for Java-style interop.
  - Uses a stricter, Java-friendly subset of parameter passing.

This split keeps the common Perseus-to-Perseus case lightweight while still making Java interop explicit. It also leaves room for a later Simula-inspired class extension, where imported JVM types could be declared more naturally as `external java class ...` instead of being modeled only as procedure targets.

Perseus prioritizes `external(...)` and `external java static(...)`. The Java interop model now covers static methods, static fields, object-valued bindings, chaining, and stronger member resolution through the same explicit `external java` surface.

Perseus will also support an optional local alias for an external Java procedure declaration:

```algol
external java static(java.lang.Math)
    real procedure cos(real x) as math_cos;
```

This means:

- the external Java target method name is `cos`
- the local Perseus name in the current unit is `math_cos`

This is useful when a Perseus unit wants to define its own procedure with the standard name while implementing it in terms of an imported Java static. For example, a compiled standard-environment unit may define:

```algol
external java static(java.lang.Math)
    real procedure cos(real x) as math_cos;

real procedure cos(x);
    value x;
    real x;
begin
    cos := math_cos(x)
end;
```

That keeps `cos` as the exported Perseus procedure while making its Java-based implementation explicit.

### Resolution and Classpath

External procedure declarations should resolve against the ordinary JVM classpath rather than inventing a separate Perseus-specific search mechanism.

- Compiled external Perseus classes and Java classes should both be found on the classpath.
- The CLI supports explicit classpath options such as `--classpath` / `-cp`.
- Perseus prefers compile-time resolution and emits diagnostics when a referenced class or method cannot be found.
- The CLI supports user-facing package selection so separately compiled Perseus units can choose stable class names intentionally.

This gives users a predictable workflow:

1. Compile a Perseus library or obtain the target JVM classes.
2. Put the output directory or JAR on the classpath.
3. Compile the dependent Perseus program against that classpath.

That model is already familiar to JVM users and keeps external Perseus linkage and Java interop conceptually aligned.

### External Perseus

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
- No procedure-value parameters across `external(...)` boundaries.
- No call-by-name interop in the first version unless the callee signature can be described exactly in Perseus terms.

Procedure-valued external parameters are not planned. Perseus procedure references rely on generated interfaces, wrapper classes, and calling conventions that are reasonable for internal compilation but too implementation-specific to treat as a stable external ABI.

The call-by-name point matters because Perseus's current lowering depends on generated `Thunk` classes and environment-bridging conventions. It is possible to support cross-file Perseus call-by-name eventually, but only if the external declaration can fully describe the callee's thunk-based ABI. For an initial design, external Perseus procedures should therefore default to value-compatible signatures.

Arrays deserve to be called out separately. Historic Algol procedure libraries clearly used formal array parameters, and Perseus should support that style of separate compilation too. However, array parameters are still ABI-bearing rather than simple scalar value parameters, because the callee must agree with the caller about the JVM array representation, bounds metadata, and dimensionality. Perseus should therefore treat external Perseus arrays as a documented ABI case of their own rather than silently lumping them into "ordinary value passing".

### External Java

`external java` is for calling methods from Java or other JVM languages that expose ordinary JVM methods.

Examples:

```algol
external java static(java.lang.Math) real procedure cos(real x);
external java static(java.lang.Integer) integer procedure parseInt(string s);
external java static(java.util.Objects) boolean procedure isNull(ref(Object) candidate);
external java static(java.util.Objects)
    ref(Object) procedure requireNonNullElse(ref(Object) candidate; ref(Object) fallback);
external java static(java.lang.System) ref(java.io.PrintStream) out as stdout;
```

Here `static(...)` names the owning Java class. The same source form covers both imported static procedures and imported static fields, with the declaration kind determining whether Perseus is binding a callable member or an object-valued field.

Perseus does not use ordinary assignment from `real` to `integer` as a general language conversion. The normal source-level conversion remains `entier(x)`. To support implementations such as `MathEnv.entier`, the compiler may apply a narrow coercion rule when assigning to the implicit result variable of a typed procedure: if an `integer procedure` assigns a `real` expression to its result, the generated code converts that value to integer at the return-assignment point. This supports typed procedure results such as `entier` without turning ordinary variable assignment into a general implicit narrowing conversion.

The `Advanced Java Interop` subsection below defines the richer Java source model used by Perseus, including static fields, imported object values, chained instance calls, and stronger overload resolution.

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
| `boolean` | `boolean` |
| `string` | `java.lang.String` |
| `ref(T)` | matching reference type when `T` is an imported Java/Perseus class; otherwise object-compatible reference handling |
| `procedure` value | Not supported in first version, unless mapped explicitly to a JVM interface |
| `array` | Not supported in first version |
| `label` | Not supported |
| `switch` | Not supported |

Notes:

- `string` is already a natural interop case because Perseus's string design intentionally targets Java `String`.
- `integer -> real` widening may be allowed automatically where the target JVM signature expects `double`.
- `real -> integer` should **not** silently use Java's truncating cast if Perseus wants to preserve Algol-style rounding semantics. This boundary needs to be specified explicitly.
- Return values should follow the same mapping in reverse.

### Advanced Java Interop

`external java` extends beyond simple static methods so ordinary Java APIs can be used directly from Perseus source without extra wrapper classes whose only purpose is to stand between Perseus code and ordinary Java library members.

#### External Java Static Fields

Perseus will support importing Java static fields as named external bindings.

Example:

```algol
external java class java.io.PrintStream;

external java static(java.lang.System)
    ref(PrintStream) out as stdout;
external java static(java.lang.System)
    ref(PrintStream) err as stderr;
```

This means:

- the external Java member name is `out`
- the local Perseus name is `stdout`
- the declaration binds the value of the static field `java.lang.System.out`

The `as` form is required for static fields in the source design, because local aliases make imported Java API names more usable and avoid collisions with Perseus definitions.

#### Object-Valued External Bindings

Perseus will allow imported Java values to be named and reused in source, not only called immediately as procedures.

That includes:

- static object fields imported through `external java static(...)`
- object values stored in ordinary `ref(...)` variables
- later reuse of those bindings in subsequent member access or procedure calls

This gives Perseus a source-level model for Java objects as values, not just Java methods as call targets.

#### Null and Reference Comparisons

Perseus includes a source-level `null` literal for object references.

Examples:

```algol
ref(PrintStream) p;

p := null;
if p = null then ...
if p <> null then ...
```

The comparison rules are:

- `=` and `<>` are valid for object references
- ordered comparisons such as `<`, `<=`, `>`, and `>=` remain numeric comparisons and are not valid for object references
- `null` is intended for `ref(...)` values rather than as a general-purpose primitive value

This gives compiled stdlib code a direct way to test whether object-valued slots are initialized, open, or closed without relying on Java-side helper registries for that state check.

#### Chained Java Member Access

Once object-valued bindings exist, Perseus will allow chained access through those values.

Examples:

```algol
stdout.println("Hello");
stderr.println("Error");
```

At the source level, dotted access will therefore cover:

- external Java field selection
- method calls on the selected object
- repeated chaining where each step resolves to a valid Java member

This is the part of the interop design that removes the need for extra Java wrapper classes whose only role is to expose ordinary Java library members in a form Perseus can reach.

#### Java Overload Resolution

Java overload resolution in Perseus will use:

- member name
- number of arguments
- argument types
- ordinary widening conversions that Perseus already documents at the JVM boundary

Resolution by name and number of arguments alone is not sufficient once Java APIs are used directly from stdlib code and ordinary user programs.

The same rule applies to:

- overloaded methods
- overloaded constructors

Perseus does not need to reproduce every detail of Java source overload resolution, but it must choose the target method from the declared and inferred JVM-facing argument types rather than only from the procedure name and argument count.

#### External Java Instance Fields

Perseus will also support direct access to public Java instance fields where that is the natural interop surface.

Examples:

```algol
someObject.field
someObject.field := 42
```

This uses ordinary dotted member syntax. The language rule is that imported Java objects may expose both methods and public fields through that syntax, with resolution based on the imported JVM member metadata.

#### Java Constants and Enum-Like Static Members

Java constants and enum-like static members use the same source model as external Java static fields.

Examples:

```algol
external java static(java.lang.Double)
    real MAX_VALUE as max_double;
external java static(java.lang.Integer)
    integer MAX_VALUE as max_int;
```

This is the part of the interop design that removes the need for extra Java wrapper classes whose only role is to expose ordinary Java constants in a form Perseus can reach.

### Perseus to Perseus External Type Mapping

For `external(...)`, the mapping should follow Perseus's internal procedure ABI rather than Java source-language expectations.

For the first version, that likely means:

| Algol declaration type | Generated JVM form |
|---|---|
| `integer` parameter | `I` |
| `real` parameter | `D` |
| `boolean` parameter | `I` for Perseus external ABI, `Z` for `external java` |
| `string` parameter | `Ljava/lang/String;` |
| `ref(T)` parameter | object reference descriptor |
| procedure return `integer` | `I` |
| procedure return `real` | `D` |
| procedure return `boolean` | `I` for Perseus external ABI, `Z` for `external java` |
| procedure return `string` | `Ljava/lang/String;` |
| procedure return `ref(T)` | object reference descriptor |
| no declared return type | `V` |

This table now needs to be read in two layers:

- Perseus-to-Perseus external linkage keeps Perseus's own calling convention, including integer-backed booleans.
- `external java` lowers declared `boolean` signatures to JVM `Z` and declared `ref(T)` signatures to JVM reference descriptors.

A later external-Perseus ABI table should add:

- one-dimensional array parameters, including hidden bounds
- multidimensional arrays if and when formal multidimensional arrays are supported
- procedure references
- call-by-name parameters for translated historical Algol libraries, via a stable thunk ABI

Those cases should not be promised until the ABI is documented and tested.

### ABI Note

Here "ABI" means the binary calling convention used by separately compiled code, not just the source-level procedure declaration. For Perseus external procedures, the ABI includes things like:

- the JVM method name and descriptor
- whether a procedure is expected to be `static`
- how arrays are represented and how bounds are passed
- how call-by-name thunks are represented
- which helper interfaces or companion classes must be present

This matters because two separately compiled Perseus units may have source declarations that look compatible while still failing to link correctly if the underlying binary conventions are not defined and kept stable.

### External Scope

#### Initial External Scope

- `external(TargetClass)` for scalar/string procedures with exact signature matching
- `external java static(TargetClass)` with explicit Algol-to-Java type mapping
- compile-time classpath-based resolution and diagnostics
- no procedure values, no call-by-name, no labels, no switches
- a user-facing way to choose the generated JVM package/class path for separately compiled Perseus units

#### External Perseus Arrays

- document the array ABI explicitly
- support one-dimensional array parameters across compilation units
- verify bounds-passing conventions at compile time
- keep the initial scope to one-dimensional arrays whose representation already matches Perseus's current formal-array passing model
- treat an external Perseus array parameter as a JVM array reference plus hidden lower/upper bound integers, just as current internal procedure calls do
- require exact agreement on element type and array rank
- defer multidimensional formal arrays until the compiler supports a stable documented external multidimensional array ABI

Representative driver examples for this work come from historic Algol-style library procedures such as `INIVEC`, where a separately compiled procedure mutates a caller-supplied array through a formal array parameter.

#### Later External Work

- external Perseus call-by-name once the thunk ABI is frozen and documented

### Rationale

This split gives Perseus a cleaner long-term story:

- `external(...)` solves separate compilation and library reuse for Perseus code.
- `external java` solves JVM ecosystem interop.
- The compiler does not need to pretend that Java methods support Algol call-by-name, labels, or designational control flow.

It also fits the current architecture well. Perseus already generates JVM-static procedure entry points and already distinguishes between ordinary value passing and thunk-based call-by-name lowering. External linkage should build on those realities instead of hiding them.

## Exceptions

Perseus uses structured exception handling in a block-oriented form that fits Algol's `begin ... end` structure. This is especially important for `external java`, where Java methods may fail by throwing exceptions, but it is not limited to Java interop alone.

## Design Goals

- Keep the syntax Algol-like and block-oriented.
- Support both Perseus-signaled conditions and caught Java exceptions from `external java`.
- Make the common case readable without forcing Java class names everywhere.
- Preserve lexical scoping and block structure.
- Keep the model straightforward on the JVM through ordinary `try/catch`.

## Syntax

An exception part attaches to a block:

```algol
begin
    ...
exception
    when IOException do
        outstring(0, "I/O failure");
    when EOFException do
        done := true
end
```

Handlers are tried from top to bottom. The first matching `when` clause handles the exception. If no clause matches, the exception propagates outward to an enclosing exception block. If none exists, the program fails with the default runtime behavior.

## Bound Exception Variable

A handler may bind the caught exception to a variable:

```algol
begin
    ...
exception
    when java(java.io.IOException) as ex do
        outstring(0, ex.getMessage())
end
```

This is especially useful for external Java interop, where the caller may want the exception message or specific exception-class discrimination.

Bound exception variables keep the model modest:

- `when ... as ex do ...` binds a catch variable for the duration of the handler
- the bound value is treated as an exception reference, not as an arbitrary user-defined class instance
- ordinary Java exception methods such as `ex.getMessage()` are available on the bound exception object

This gives Perseus practical exception introspection without requiring a separate exception-object hierarchy.

## Exception Names

Perseus builds its exception syntax directly on Java exception classes rather than inventing a parallel set of renamed Perseus exceptions.

That means Perseus should support:

- users should not need `external java class ...` declarations just to catch familiar Java exceptions
- `java(...)` remains the fully explicit form
- common Java exception names may be recognized directly in exception patterns as built-in shorthand for the corresponding Java exception classes

For example, all of these should be valid:

- `when IOException do`
- `when NumberFormatException do`
- `when java(java.io.FileNotFoundException) do`

Perseus-specific duplicate names such as `IOError`, `BoundsError`, `ArithmeticError`, `EndOfFile`, and `FaultError` should not remain part of the long-term exception vocabulary. If Perseus libraries later define their own exception classes, those should be ordinary class/library exception types rather than renamed stand-ins for existing Java exceptions.

An initial practical set for direct exception-pattern names includes:

- `Exception`
- `RuntimeException`
- `IOException`
- `FileNotFoundException`
- `NumberFormatException`
- `IllegalArgumentException`
- `IllegalStateException`
- `ArrayIndexOutOfBoundsException`
- `IndexOutOfBoundsException`
- `ClassCastException`
- `ArithmeticException`
- `NullPointerException`

This is an initial practical set rather than a closed catalog. Additional common Java exception classes may be added if real Perseus programs need them.

## Semantics

- `begin ... exception ... end` establishes a protected block.
- Normal completion skips the exception part entirely.
- If an exception is raised inside the protected block, control transfers to the first matching `when` clause.
- The handler runs in the lexical scope of the block, so it can inspect and update surrounding locals.
- After the handler finishes, control continues after the whole block, not back inside the point where the exception occurred.
- A handler may later rethrow the exception if Perseus grows an explicit raising form.

This gives Perseus a model closer to structured exception handling than to resumable conditions.

## Raising Exceptions

Perseus uses a source-level `signal expr` statement as its primitive explicit exception-raising form.

Example:

```algol
begin
    ...
exception
    when IOException as ex do
        signal ex
end
```

This choice fits the existing exception vocabulary better than `throw`, because Perseus uses block-oriented `exception` / `when ... do ...` handling rather than Java-style `try` / `catch`.

The semantics are:

- `signal expr` is a statement, not an expression
- `expr` must evaluate to an exception object reference
- control does not continue after the `signal` statement
- matching and propagation use the existing `begin ... exception ... end` mechanism

This makes `signal` the primitive raising form, while higher-level procedures such as `fault(...)` are implemented in terms of constructing and signaling an ordinary Java-backed runtime exception.

## Interaction with Existing Procedures

- `fault(...)` remains the convenience environmental procedure for straightforward runtime failure.
- `fault(...)` is implemented by constructing a runtime exception object and signaling it with `signal expr`.
- Operations that currently go straight to `fault(...)` may instead raise catchable Java-backed exceptions that user code may handle where that gives a better exception model.
- This would let programs choose between:
  - fail-fast behavior
  - local recovery
  - translation of a low-level Java exception into a higher-level Algol condition

## Interaction with External Java

This is especially valuable for `external java`.

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

## Scope and Restrictions

To keep the design robust:

- handlers should attach only to `begin ... end` blocks, not individual statements
- matching should be by exception name/class only, not arbitrary Boolean guard expressions
- handlers should not resume execution at the throw site
- `signal expr` should remain the only general explicit raising statement rather than introducing a larger family of exception-control forms immediately
- the language may later add `finally`/cleanup syntax, but it is not part of the current model
- bound exception values currently expose practical Java-style method access rather than a separate Perseus-specific helper layer

If a cleanup feature is desired later, it could be added in an Algol-flavored way such as:

```algol
begin
    ...
exception
    when IOException do ...
finally
    closefile(ch)
end
```

but this should be a later layer, not part of the minimum design.

## Summary

- A block exception part (`begin ... exception ... end`) is the Perseus exception form.
- `when Name do ...` handles direct Java exception names from the built-in shorthand set.
- `when java(Fully.Qualified.Exception) as ex do ...` handles precise Java interop failures.
- `when ... as ex do ...` supports practical inspection through ordinary Java exception methods such as `ex.getMessage()`.
- `signal expr` is the explicit raising form for exception objects.
- A later convenience refinement may allow the common built-in Java exception names used in `when` patterns to also be available directly as constructor targets in expressions such as `signal new RuntimeException("boom")`, without requiring separate external declarations.
- This design makes external Java/class interop safer and more expressive without inventing a separate Perseus exception universe.

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

Perseus treats classes as a major Simula-inspired extension to an Algol-family language. The goal is not to reproduce every historical Simula surface form verbatim, but to preserve the important Algol-to-Simula lineage while fitting naturally on the JVM.

## Current Model

The current class model includes:

- class declarations
- instance fields
- instance procedures
- explicit object creation
- dotted member and procedure access
- Simula-style prefix inheritance
- dynamic dispatch for overridden procedures
- `external java class ...` declarations
- extension of concrete and abstract external Java classes
- implementation of one or more external Java interfaces with `implements`

This gives Perseus a real object model rather than a purely experimental class syntax.

## Core Direction

The class design should continue to follow these principles:

- Perseus should borrow the core object model from Simula more than from Java.
- Perseus does not need to copy every historical Simula surface detail exactly.
- Semantics matter more than surface imitation.
- JVM interop should be strong, but Perseus classes should not simply become Java classes written with Algol spelling.

So the design question is not how to copy Simula or Java exactly, but how to keep the Simula lineage while giving Perseus a coherent object model of its own.

## Class Identity and Naming

Traditional Perseus programs still require a chosen JVM entry-class name, because the JVM requires a class to hold the compiled program entry point. That is normal and not a design problem in itself.

Declared Perseus classes, however, use the class names written in the source. They are not anonymous helper artifacts.

The remaining design issue is narrower:

- how reusable Perseus classes should be packaged and named across separate compilation
- how multi-file library workflows should relate to those names

The current model is:

- a source file may define multiple Perseus classes
- each declared class should keep its own class identity
- reusable class identity is introduced in source with a `namespace` declaration

For example:

```algol
namespace mylib.geometry;

class Point;
class ColoredPoint;
```

This would give the reusable classes identities corresponding to:

- `mylib.geometry.Point`
- `mylib.geometry.ColoredPoint`

The `namespace` keyword is preferable here to terms such as `package`, `module`, or `library` because it describes naming and identity without implying a physical bundle or reusing a term that already has a different scope meaning elsewhere.

The CLI `--package` option remains useful for ordinary compiled programs, while reusable class identity in source is expressed with `namespace`.

That `namespace` model should also extend naturally to multi-file libraries: several source files may contribute to the same reusable library identity when they are compiled together and agree on the same `namespace`.

This matters for reusable libraries, separate compilation, inheritance across compilation units, and interop with other JVM languages.

## Call-by-Value Default for Class Procedures

Procedures declared inside classes should default to call-by-value.

That is the right design for Perseus because:

- it matches Simula's direction better than classic Algol call-by-name
- it fits naturally with JVM method-call expectations
- it makes object behavior easier to reason about in the presence of mutation and instance state
- it makes Perseus classes more usable from other JVM languages

This also preserves a useful semantic distinction:

- ordinary Algol-style standalone procedures may continue to follow classic Algol defaults
- class procedures follow Simula-style method defaults

## Prefix Inheritance

Perseus class inheritance should be understood as Simula-style single inheritance through prefixing.

The important design points are:

- inheritance is single, not multiple
- inherited fields and procedures become part of the prefixed class object
- object initialization follows the prefix chain
- dynamic dispatch belongs to the prefix model rather than being treated as an unrelated add-on

This gives Perseus a clearer identity than simply copying Java's `extends` model into Algol-family syntax.

## External Java Classes and Interfaces

External Java interop is a separate part of the class design from Perseus-to-Perseus prefix inheritance.

There are several distinct cases:

- Perseus classes inheriting from other Perseus classes
- Perseus code using external JVM classes as ordinary object types
- Perseus classes extending external Java classes
- Perseus classes implementing external Java interfaces

Those should not be conflated, even though they meet in the same runtime.

The current direction is:

- prefix inheritance for Perseus-defined classes
- explicit `external java class ...` declarations for imported JVM classes
- Java superclass and interface conformance checked during semantic validation before code generation
- Java/JVM verification left as a later safety net

This keeps the language design clear while still allowing meaningful JVM interoperability.

## Member Selection and Zero-Argument Procedures

Perseus currently allows dotted zero-argument procedure syntax such as `obj.name`.

A richer disambiguation rule may eventually be needed if real interop cases make it necessary. The likely rule would be:

- `obj.name` means field selection
- `obj.name()` means procedure or method call

At the moment this appears to be a possible future refinement rather than an active problem, since well-designed Java classes are unlikely to expose both a public field and a zero-argument method with the same name.

## Open Class Design Questions

Some class-design questions remain open:

- how constructor chaining and overriding should be expressed when Perseus classes extend external Java classes
- how separate-compilation and multi-file library conventions should build on the existing package-and-class naming model
- whether unusually demanding JVM framework interop would ever justify class-composition or inheritance mechanisms beyond the current single-prefix model
- whether exception values should later expose a richer object-style interface in Perseus source
- whether Perseus should later move further toward Simula process or coroutine concepts

## Summary

Perseus classes are Simula-inspired in semantics, value-oriented by default, and increasingly interoperable with ordinary JVM classes and interfaces. Prefix inheritance remains the native long-term inheritance model, while Java classes and interfaces are handled through explicit interop rather than by collapsing the Perseus model into Java's.
