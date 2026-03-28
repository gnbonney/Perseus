# Modified Report Compatibility

This note records which Modified Report notations Perseus should:

- accept verbatim,
- normalize into a preferred Perseus form,
- or decline to support directly.

The goal is not to maximize parser nostalgia. The goal is to preserve the most valuable standard Algol source forms while keeping Perseus readable, teachable, and maintainable as an Algol-derived language rather than a museum reconstruction.

## Decision Table

| Notation or feature | Example | Current / proposed Perseus handling | Category | Rationale |
|---|---|---|---|---|
| Numeric labels | `10 x := x + 1` | Accept verbatim | Accept verbatim | Common in historical Algol code and useful for source compatibility, especially around `goto` examples. |
| Dummy statements | `L:` followed by nothing, or empty statement between separators | Accept verbatim | Accept verbatim | Needed so labeled empty targets and some report-style control-flow examples compile naturally. |
| Label and switch parameters / designational exits | `procedure p(label L); goto L` or passed `switch` targets | Not currently planned | Do not support | Even though this is part of standard Algol, Perseus now treats structured exceptions and more modern control-flow mechanisms as the preferred direction. Ordinary labels remain supported, but passed labels/switches and their non-local exit semantics are intentionally outside the current language scope. |
| Named parameter delimiters | `procedure Spur (a) Order:(n) Result:(s);` | Accept verbatim, but normalize internally to an ordinary parameter list | Accept verbatim | Historically important, appears in well-known examples, and is best understood as user-defined parameter delimiters rather than a different kind of procedure declaration. |
| Simple parameter lists | `procedure f(x, y); value x; integer x, y;` | Preferred spelling in docs and most tests | Normalize/prefer | Clearer for modern readers and already matches most of the current compiler structure. |
| Lowercase reserved words | `begin`, `end`, `if`, `then` | Preferred and accepted | Normalize/prefer | Already the project convention; avoids stropping and typography issues. |
| Alternative hardware-representation operators documented in `Representation.md` | `div`, `**`, `^`, `imp`, `eqv`, `=>`, `==` | Accept as documented | Accept verbatim | These are now part of Perseus's declared hardware representation and no longer count as rare legacy syntax. |
| `{` and `}` as block delimiters | `{ ... }` | Accept as Perseus extension | Accept verbatim | Not Modified Report syntax, but explicitly part of Perseus's chosen representation. |
| `%` line comments | `% note` | Accept as Perseus extension | Accept verbatim | Useful and historically defensible as a later Algol-family extension, even though not standard Algol 60 report syntax. |
| Standard Algol block comments | `comment text;` | Accept verbatim | Accept verbatim | Standard, readable, and already part of the grammar. |
| Stropping / quoted keywords | `'begin'`, `'if'` | Do not support | Do not support | Adds historical complexity without helping modern readability or the current project direction. |
| Reference-language mathematical symbols | `≤`, `≥`, `≠`, `¬`, `∨`, `∧`, `÷`, `↑` | Do not accept directly in source; document their hardware representation instead | Normalize only | The language should document these forms, but source compatibility should go through `Representation.md` rather than Unicode-heavy verbatim parsing. |
| Original Algol 60 string quoting conventions | backtick / single-quote combinations | Do not support directly; use double quotes | Normalize only | Double quotes are already the Perseus convention and are much more practical on modern systems. Embedded escaped quotes inside a double-quoted string remain part of the supported Perseus form. |
| Rare compiler-specific hardware spellings not documented by Perseus | vendor-specific digraphs and lexical quirks | Do not support unless a concrete compatibility need appears | Do not support | Avoids an ever-growing list of one-off spellings from unrelated Algol implementations. |

## Working Policy

Until a stronger reason appears, Perseus should follow these rules:

1. Accept verbatim forms when they are both historically important and still readable enough to justify permanent parser support.
2. Prefer normalized Perseus spellings in project docs, examples, and new tests even when a rarer historical spelling is accepted.
3. Decline syntax that mainly recreates hardware, typography, or vendor quirks without improving source compatibility for examples we actually care about.
4. Distinguish between ordinary historical surface syntax that Perseus accepts and deeper standard semantics that Perseus may intentionally leave out when they do not fit the language's current direction.
