# Hardware Representation

## Background

The Algol 60 standard defines two levels of language representation:

- The **reference language** — the official mathematical notation, using symbols such as
  ≤, ≥, ≠, ∧, ∨, ¬, ÷, ↑, and bold or underlined keywords like **begin**, **end**, **if**.
- A **hardware representation** — an implementation-defined mapping of those symbols onto
  whatever character set is available on a given machine.

This distinction was necessary in the 1960s because no universal character encoding existed.
Different manufacturers used different symbol sets: CDC, IBM, Burroughs, ICL, and Honeywell
compilers all made different choices. Some used digraphs (`GEQ`, `LEQ`, `NEQ`); some used
punched-card-era glyphs; some quoted keywords (`'begin'`, `'end'`), a practice known as
*stropping*. The result was that Algol 60 source code was not portable between compilers even
though the language was defined by an international standard.

## Our Approach

This compiler targets developers in 2026. The hardware representation therefore follows
modern conventions familiar from C, Java, and most other contemporary languages. Stropping
(quoting keywords) is not used. Keywords are lowercase reserved words. Operators are ASCII
digraphs or reserved words where a single ASCII character is unavailable.

## Symbol Mapping

| Reference language | This compiler | Notes |
|---|---|---|
| **begin** | `begin` | lowercase reserved word |
| **end** | `end` | lowercase reserved word |
| **if** | `if` | lowercase reserved word |
| **then** | `then` | lowercase reserved word |
| **else** | `else` | lowercase reserved word |
| **for** | `for` | lowercase reserved word |
| **do** | `do` | lowercase reserved word |
| **while** | `while` | (used in `for` list element) |
| **step** | `step` | (used in `for` list element) |
| **until** | `until` | (used in `for` list element) |
| **goto** | `goto` | lowercase reserved word |
| **procedure** | `procedure` | lowercase reserved word |
| **value** | `value` | lowercase reserved word |
| **own** | `own` | lowercase reserved word |
| **switch** | `switch` | lowercase reserved word |
| **integer** | `integer` | lowercase reserved word |
| **real** | `real` | lowercase reserved word |
| **Boolean** | `boolean` | lowercase (unlike the report, which capitalises it) |
| **array** | `array` | lowercase reserved word |
| **string** | `string` | lowercase reserved word |
| **label** | `label` | lowercase reserved word |
| **comment** | `comment` | lowercase reserved word |
| **comment** | `%` | line comment / escape remark extension (to end-of-line; non-standard Algol 60, with Burroughs/Unisys precedent) |
| **begin** | `{` | BCPL/C-style block open (synonym for `begin`) |
| **end** | `}` | BCPL/C-style block close (synonym for `end`) |
| `:=` | `:=` | assignment (unchanged) |
| `=` | `=` | equality test (unchanged) |
| `<` | `<` | less than (unchanged) |
| `>` | `>` | greater than (unchanged) |
| ≤ | `<=` | less than or equal |
| ≥ | `>=` | greater than or equal |
| ≠ | `<>` | not equal |
| ∧ | `and` | logical and (`&` also accepted) |
| ∨ | `or` | logical or (`|` also accepted) |
| ¬ | `not` | logical not (`~` also accepted) |
| ⇒ | `imp` | logical implication (`=>` also accepted) |
| ≡ | `eqv` | logical equivalence (`==` also accepted) |
| ÷ | `div` | integer division |
| ↑ | `**` | exponentiation (`^` also accepted) |
| string quotes | "..." | double quotes for string literals |

## Rationale

- `<=`, `>=`, `<>` are the same as Pascal and SQL, and recognisable to any developer.
- `and`, `or`, `not` as reserved words follow Simula 67, Python, and SQL — they read as
  natural English and avoid the ambiguity of `&` vs `&&`.
- `&`, `|`, `~` are accepted as synonyms because they appear in some existing Algol 60
  reference material and in the sample programs inherited by this project (`primes.alg`).
- `%` line comments are a non-standard extension, but they have clear historical precedent in
  the Burroughs/Unisys Extended ALGOL family, where `%` introduced an "escape remark" causing
  the rest of the source record to be ignored. Perseus adopts the modern text-file analogue of
  that behavior by treating `%` as a comment introducer to end-of-line, which is convenient and
  avoids the trailing-semicolon requirement of `comment ... ;`.
- `imp` and `eqv` follow historical Algol-family hardware representations used by systems such
  as Burroughs and Univac-family compilers. They keep implication and equivalence in the same
  Boolean-operator family as `and`, `or`, and `not`, instead of overloading general equality syntax.
- `=>` and `==` are accepted as convenient alternate spellings, following later Algol-family
  practice such as Simula and some hosted Algol 60 implementations.
- `div` for integer division follows Pascal and Ada.
- `**` for exponentiation follows Fortran, Python, and many other languages.
- `boolean` is lowercased for consistency with all other keywords; the report's capitalisation
  reflects the name Boole, not any syntactic distinction.
- `{`/`}` as synonyms for `begin`/`end` follow BCPL and C notation; they are accepted by the
  grammar as a readability convenience for developers familiar with those languages.
- Double quotes ("...") are used for string literals, following Algol 68, C, Java, Python, and most modern languages. This choice improves familiarity and ease of typing for contemporary developers, and aligns with the conventions of most Algol descendants. The original Algol 60 report used backticks and single quotes due to hardware limitations, but double quotes are now the de facto standard for string literals.

## Relationship to the Modified Report

The Modified Report (IFIP Working Group 2.1, 1976; ISO 1538:1984) acknowledges hardware
representations in Appendix 1 (Subsets) but does not mandate a specific one, leaving the
choice to implementors. This document constitutes the hardware representation for Perseus.
