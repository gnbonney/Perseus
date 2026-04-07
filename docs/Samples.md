# Sample Programs

Perseus now has a broad set of sample Algol programs under [`test/algol`](../test/algol). They are organized by purpose rather than kept in one flat directory.

## Folder Layout

- [`test/algol/core`](../test/algol/core)
  Core language coverage: `hello`, the Dijkstra `primer` programs, arrays, boolean expressions, hardware-representation syntax, numeric labels, named parameter delimiters, `own`, and switch declarations.
- [`test/algol/procedures`](../test/algol/procedures)
  Procedure semantics: value parameters, call-by-name, Jensen's Device, deferred typing, nested procedures, procedure variables, procedure parameters, and thunk-isolation cases.
- [`test/algol/io`](../test/algol/io)
  Environmental procedures, strings, channels, file and string I/O, formatted I/O, constants, and input/output behavior.
- [`test/algol/external`](../test/algol/external)
  External Perseus and external Java interop samples.
- [`test/algol/namespaces`](../test/algol/namespaces)
  Source-level `namespace` and separately compiled multi-file library samples.
- [`test/algol/classes`](../test/algol/classes)
  Simula-style class and object samples.
- [`test/algol/exceptions`](../test/algol/exceptions)
  Structured exception-handling samples.
- [`test/algol/stdlib`](../test/algol/stdlib)
  Client programs that exercise the compiled standard environment directly.
- [`test/algol/historical`](../test/algol/historical)
  Larger historic and stress-oriented examples such as `manboy`, `primes`, and the `pi` programs.
- [`test/algol/misc`](../test/algol/misc)
  Samples that are not currently part of the active regression suite and need follow-up review.

## Source Background

The initial core of code samples were adapted from historically important Algol material, including:

- A. N. Habermann and other introductory Algol material
- E. W. Dijkstra, especially the `primer` programs
- Juliet Kemp's Algol examples
- Donald Knuth's Man-or-Boy test
- The Revised and Modified Reports on Algol 60
- Rutishauser's *Description of Algol*

Other samples are Perseus-specific regression drivers added to pin down implementation choices, extensions, and bug fixes.

## Coverage

The sample suite now exercises most of the implemented language surface, including:

- basic program structure and nested blocks
- arithmetic, assignment, and scalar variables
- `goto`, labels, numeric labels, and switch declarations
- `if`, `if ... else`, and `for`
- integer, real, boolean, string, and array features
- one-dimensional and multidimensional arrays
- procedures, procedure variables, and procedure parameters
- call-by-name, Jensen's Device, deferred typing, and nested procedure access
- `own` variables
- environmental output and much of the current input/output model
- string channels, file channels, and formatted I/O
- compiled standard-environment clients
- external Perseus linkage plus richer Java interop, including fields, overloads, references, and arrays
- `namespace` declarations and multi-file library compilation
- classes and objects
- structured exceptions
- historically difficult recursive and thunk-heavy programs such as `manboy`
