# Strings

Many historic Algol compilers (e.g., NU Algol, Data General Extended Algol, Algol W, Simula) introduced a string type or class, often as a variable-length array of characters or a record with a length and character array. Simula's `Text` class and Algol W's `string` type are notable examples.

- **NU Algol:** Supported `STRING` and `STRING ARRAY` variables.
- **Algol W:** Had a `string` type and string operations.
- **Simula:** Used a `Text` class with similar semantics.
- **DEC Algol:** Included string handling extensions.

# Strings in JAlgol

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

For JAlgol, we've decided the best approach is to generate code that uses Java String (or StringBuilder) for storage, but provide static utility methods (injected as needed) for Algol-like operations such as 1-based indexing, slicing, and concatenation. This approach:
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

# File I/O

The original Algol 60 standard, and even the Modified Report, do not specify a standard mechanism for file input/output.  I/O in Algol 60 is limited to channels, which are left implementation-defined and typically mapped to standard input/output devices (e.g., teletype, punch cards, or console streams).  File I/O was handled in a variety of incompatible ways by different compilers, and the standard explicitly leaves the mapping of channels to devices or files outside its scope.

JAlgol's current design (see Environmental-Block.md) maps channel numbers to Java streams (`System.out` for channel 1 and `System.err` for channel 0).  This is sufficient for standard output and error, but does not address file I/O.  Some recommendations for extending this:

* **File Open/Close Procedures:** Introduce procedures such as `openfile(channel, filename, mode)` and `closefile(channel)` to manage file streams.  These would register a mapping from a channel number to a Java `PrintStream` or `BufferedReader`, enabling subsequent I/O procedures to use the channel as a file handle.
* **Backward Compatibility:** Retain the current behavior for channels 0 and 1 (stderr/stdout), but allow higher channel numbers to be dynamically assigned to files.
* **Error Handling:** Define clear error semantics for invalid channel use, file not found, or permission errors, possibly by integrating with the `fault` procedure.

For example (proposed syntax):

```algol
openfile(2, "output.txt", "w");
outstring(2, "Hello, file!");
closefile(2);
```


This approach is consistent with the Modified Report's philosophy of implementation-defined channel-to-device mapping, while bringing JAlgol closer to modern I/O expectations.  It mirrors patterns already seen in Simula 67 (file, infile, outfile classes), NU Algol (FORMAT and LIST declarations), and DEC Algol (comprehensive file I/O).

# Formatted I/O (Hybrid Design)

Many historic Algol compilers provided formatted I/O via FORMAT statements or format strings, but the syntax and semantics varied widely. Modern JVM languages use `String.format` or similar mechanisms. To balance authenticity and practicality, JAlgol proposes a **hybrid formatted I/O system**:

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

# String Channels and sprintf-style Output

JAlgol supports associating a channel with a string variable, enabling output procedures to write directly to a string buffer. This provides the equivalent of `sprintf` in C or `StringWriter` in Java, and is a natural extension of the channel-based I/O model.

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
# External Procedures

NU Algol had external procedures while Simula 67 had external classes and external procedures.  They had to be declared in the program they were being used in kind of like an import statement in a java class.  In the Simula 67 standard external classes and external procedures were considered "program modules".

In the context of a JVM compiler I think that external classes and procedures (functions) should be usable from other JVM languages.  With regard to external procedures written in Algol that would, I think, mean that such procedures should not have call-by-name parameters.  Also, I don't think you should be able to pass a label to an external procedure, and an external procedure shouldn't be able to have a GOTO leading to the outside of the procedure.

An external procedure declaration would look something like this:

external <kind> <type> procedure <identifier list> ;

<type> is real or integer
<kind> language or implementation-dependent

In NU Algol <kind> was ALGOL or another language such as FORTRAN, but the Simula 67 standard says <kind> is implementation-dependent.  I think for a JVM Algol or Simula compiler, <kind> would generally be a class name such as:

external static(java.lang.Math) real procedure cos(real a);

or

external virtual(java.lang.System.out, java.io.PrintStream) procedure print(string s);

This would provide an easy way to get at some of the most frequently used JRE functions rather than hard-coding inline Jasmine code.

# Lambda Notation

Rutishauser proposed, in the Handbook for Automatic Computation - Description of Algol, an extension to replace Jensen's device (call-by-name for array parameters) with Church's lambda notation for arrays. This approach is based on Alonzo Church's lambda calculus, which is the mathematical foundation for anonymous functions ("lambda functions") in modern programming languages.

## Background
- In lambda calculus, a lambda expression defines an anonymous function.
- In programming, lambda functions (or closures) are direct descendants of this idea.
- In the context of Algol, using lambda notation for arrays means treating an array as a function from indices to values, and passing/using them as such.
- This generalizes array parameters: instead of passing an array or a call-by-name parameter, you pass a function (lambda) that computes the value for each index.

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
- Church's lambda notation for arrays is a formal, functional way to generalize array and call-by-name parameters using anonymous functions.
- It is the conceptual ancestor of lambda functions and closures in modern languages.
