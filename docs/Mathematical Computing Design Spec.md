# Mathematical Computing Design Spec

This document collects design ideas specifically aimed at Perseus's mathematical, scientific, and engineering niche. It is intentionally separate from [Perseus Language Design.md](Perseus%20Language%20Design.md), which covers the broader language surface. The focus here is narrower: what features would make Perseus a strong language for readable executable mathematics on the JVM.

## Purpose

Perseus does not need to compete with Python or Java as a general-purpose language first. Its clearest center of gravity is:

- numerical methods
- scientific algorithms
- engineering-oriented computation
- translation and preservation of classic algorithmic literature
- JVM-hosted software where the shape of the algorithm matters

The goal of this design track is therefore not "add every feature people associate with technical computing." It is to identify the features that most improve mathematical expressiveness, numerical clarity, and scientific practicality while keeping Perseus readable and coherent.

## Design Principles

### 1. Readability over cleverness

Perseus should favor notation that helps a human read an algorithm, especially when comparing code to a paper, textbook, or historic library.

### 2. Mathematical structure over general-purpose fashion

A feature should be preferred when it helps express mathematical objects, transformations, or numerical procedures clearly, even if it is not currently fashionable in mainstream languages.

### 3. Strong JVM interop without Java-shaped syntax everywhere

Perseus should interoperate well with Java libraries, but its mathematical surface should not collapse into Java syntax or Java naming conventions.

### 4. Language support where notation matters, library support where it does not

Some capabilities belong in the language because they change how algorithms are written. Others are better handled in the standard library.

### 5. Deliberate growth

Perseus should avoid becoming a grab bag of unrelated technical features. Additions should reinforce the identity of the language as a vehicle for readable executable mathematics.

## Scope Split: Language vs Library

The following distinction should guide future work.

### Better candidates for language features

- features that change how formulas and algorithms are written
- features that make mathematical code substantially clearer
- features that affect typing, purity, array semantics, or numeric behavior

### Better candidates for library features

- large families of numerical routines
- specialized solvers and algorithms
- domain-specific scientific packages
- statistical or numerical utilities that do not need special syntax

## Candidate Feature Areas

## 1. Complex Numbers

This is one of the strongest candidates for a future mathematical extension.

### Why it matters

- Many scientific and engineering domains need complex arithmetic naturally.
- Numerical analysis, signal processing, controls, electromagnetics, and special functions all benefit from first-class complex support.
- Treating complex values as an ordinary library record or Java object is usually much less readable than a proper language-level type.

### Design direction

- Add a source-level `complex` type.
- Support complex literals in a form that does not fight existing Algol syntax.
- Support the ordinary arithmetic operators on complex values.
- Support arrays of complex values.
- Keep Java interop practical, likely through a standard runtime representation rather than ad hoc lowering.

### Questions

- Should Perseus expose one default complex type only, or later support multiple precisions?
- What literal syntax is cleanest without making the language visually noisy?

## 2. Array Sections and Whole-Array Expressions

This is probably the most important readability improvement after complex numbers.

### Why it matters

- Mathematical and numerical code often works on vectors, rows, columns, and submatrices.
- Scalar-only array access becomes verbose quickly.
- Languages such as Fortran, MATLAB, Julia, and APL-descended systems show how much clarity is gained when slices and whole-array operations are natural.

### Design direction

- Add array sections such as row, column, and range-based slices.
- Add whole-array expressions where the meaning is clear and unsurprising.
- Distinguish ordinary matrix operations from elementwise operations explicitly if needed.
- Keep the surface consistent with Perseus's existing bounded-array model rather than copying zero-based Java assumptions.

### Questions

- How much of this should be core language syntax versus standard-library procedures?
- Should broadcasting exist, or should Perseus prefer explicitness?

## 3. `pure` and `elemental` Procedures

These are especially attractive ideas to borrow from Fortran.

### Why they matter

- Scientific code benefits from distinguishing pure mathematical procedures from stateful or I/O-bound procedures.
- Elementwise array lifting is common in numerical programming.
- Purity annotations could later support optimization, parallel evaluation, and clearer diagnostics.

### Basic meaning

`pure` and `elemental` are both meant to make mathematical procedures more explicit.

A `pure` procedure is one that behaves like a mathematical computation rather than an action with side effects. In broad terms, a pure procedure should not:

- perform I/O
- mutate global state
- change unrelated variables outside its own local work

That makes it easier to read a procedure as "this computes a value from its inputs" rather than "this might also change the world."

An `elemental` procedure is a scalar procedure that the language is allowed to apply element-by-element to arrays. The procedure is still written as a scalar procedure, but it can be lifted naturally over array arguments.

### Example: `pure`

```algol
pure real procedure hypot2(x, y);
    value x, y;
    real x, y;
begin
    hypot2 := sqrt(x*x + y*y)
end;
```

The point of `pure` here is that `hypot2` reads like a mathematical definition. A reader should be able to assume that it only computes the result and does not also write to a file, mutate hidden state, or change unrelated variables.

### Example: `elemental`

```algol
elemental real procedure sqr(x);
    value x;
    real x;
begin
    sqr := x * x
end;
```

This would naturally support scalar use:

```algol
y := sqr(3.0);
```

and, if Perseus adopts array lifting in this style, also array-oriented use:

```algol
a := sqr(b);
```

where `b` is an array and `sqr` is applied to each element of `b`.

### Relationship between them

In practice, `elemental` procedures usually need to be `pure`, or at least close to that idea, because elementwise lifting becomes much harder to reason about if each application can also perform side effects.

So the short intuition is:

- `pure` means a procedure is mathematically well-behaved and side-effect-free
- `elemental` means a scalar mathematical procedure can also be applied element-by-element to arrays

### Design direction

- `pure` should mark procedures that have no observable side effects.
- `elemental` should mark scalar procedures that can be applied naturally over array arguments.
- These properties should be semantic, not just documentary.

### Questions

- How strict should purity checking be in the first implementation?
- Should class procedures and lambdas eventually support the same annotations?

## 4. Numeric Precision and Numeric Types

Perseus currently has a simpler numeric model than many scientific languages.

### Why it matters

- Scientific and engineering code often cares about precision explicitly.
- Some algorithms need higher precision than an ordinary JVM `double` provides.
- Some applications care more about stable semantics than about exposing many numeric kinds.

### Design direction

- Keep the initial model simple.
- Consider a future precision mechanism only if it genuinely improves mathematical work rather than just imitating Fortran's `kind` machinery.
- Prefer a clear, teachable model over an elaborate taxonomy of numeric kinds.

### Questions

- Is one `real` type plus library-level high-precision support enough for Perseus?
- Should any future extension focus on decimal arithmetic, arbitrary precision, or explicit binary precision tiers?

## 5. Tuples and Multiple Return Values

Numerical procedures often want to return more than one thing.

### Why they matter

- Many algorithms naturally produce a value plus status, error estimate, derivative, or iteration count.
- Forcing every such case into mutable out-parameters can obscure the shape of the algorithm.

### Design direction

- Add a lightweight tuple or multiple-return-value mechanism.
- Keep it readable and unsurprising.
- Make it work well with numerical and solver-oriented APIs.

### Questions

- Should Perseus prefer tuple values, destructuring assignment, or procedure-result records?
- How should this interact with external Java interop?

## 6. Numerical Standard Library Growth

Much of Perseus's mathematical niche will depend on libraries rather than syntax.

### Strong candidates

- linear algebra
- least squares and nonlinear fitting
- interpolation and approximation
- quadrature
- optimization
- differential equation solvers
- FFT and spectral tools
- special functions

### Design direction

- Keep the environmental block small.
- Build larger numerical capabilities as standard-library units rather than stuffing them into the language core.
- Treat historical libraries such as NUMAL as both inspiration and possible translation targets.

## 7. Probability and Statistics Support

Perseus should be careful here.

### Why it matters

- Scientists and engineers often need some probability and statistical support.
- This does not mean Perseus should try to become R.

### Strong candidates

- normal distribution helpers
- gamma/beta family support
- regression-oriented building blocks
- covariance and correlation helpers
- random-number generation once the broader numerical library story is stronger

### Design direction

- Prefer foundational mathematical and numerical support first.
- Add statistical features where they reinforce scientific computing rather than turning Perseus into a statistics-first environment.

## 8. Units of Measure

This is a highly appealing engineering-oriented extension.

### Why it matters

- Unit mistakes are real and costly.
- Engineers benefit from static help around dimensions and conversions.

### Design direction

- Treat this as a later but potentially distinctive feature.
- If adopted, it should integrate with numeric expressions naturally rather than feeling bolted on.

## 9. Interval Arithmetic

This is a more specialized but very mathematically aligned feature.

### Why it matters

- Interval methods are valuable for validated numerics and error-aware computation.
- This would fit Perseus's algorithmic and numerical identity better than many generic language features.

### Design direction

- Likely better as a library-first feature unless the language later adds support that clearly improves notation.

## 10. Automatic Differentiation

This could become very important if Perseus grows toward optimization and scientific modeling.

### Why it matters

- Optimization, fitting, and simulation increasingly benefit from AD.
- It fits scientific computing more directly than many fashionable language additions.

### Design direction

- Treat as a later design area.
- It may begin as a library/runtime technique before any syntax-level support is considered.

## 11. Array-Oriented Parallelism

Perseus may eventually want a mathematical notion of safe parallel evaluation.

### Why it matters

- Numerical workloads often contain data-parallel structure.
- A mathematical language benefits from parallel constructs that preserve clarity.

### Design direction

- Keep this separate from general actor-style concurrency.
- If added, prefer restrained and explicit forms inspired more by numerical languages than by task-system APIs.

## Features Borrowed from Other Traditions

Perseus should feel free to borrow good ideas where they fit its identity.

### Especially attractive borrowings

- Fortran: `pure`, `elemental`, array thinking, numerics-first design
- Julia: emphasis on readable mathematical code and rich numerical libraries
- MATLAB: matrix-oriented usability, where it improves clarity without copying the whole environment
- R: selective statistics support where it helps scientific users, but not R's whole language model

### Borrowings to avoid or limit

- Fortran-style implicit typing
- syntax that becomes cryptic or too symbolic
- feature accumulation without a clear mathematical payoff

## Potential Order of Exploration

If Perseus wants to grow its mathematical identity deliberately, the strongest order looks something like:

1. Complex numbers
2. Array sections and whole-array expressions
3. `pure` and `elemental` procedures
4. Numerical standard-library expansion
5. Tuples or multiple return values
6. Units of measure
7. Automatic differentiation
8. More advanced statistics support
9. Interval arithmetic
10. Array-oriented parallelism

This order favors features that most improve readability and mathematical expressiveness first.

## Non-Goals

At least for now, this design track should not aim to make Perseus:

- a clone of Python
- a replacement for R
- a full MATLAB-style interactive environment
- a kitchen-sink technical language with every possible numerical feature in the core language

The goal is narrower and stronger: make Perseus an unusually good language for readable executable mathematics on the JVM.

## Relationship to Experimental Features

This mathematical direction does not rule out more exploratory language work such as actors, lambdas, or new looping forms.

Instead, Perseus should try to hold both:

- a center of gravity in mathematical and scientific computing
- an experimental edge that explores carefully chosen modern ideas

The test for any new feature should be whether it strengthens the language's identity rather than diluting it.

## Summary

Perseus's mathematical niche will likely come less from expanding the tiny historical environmental block and more from a thoughtful mix of:

- first-class mathematical types
- clearer array and numerical semantics
- purity-oriented procedure design
- strong numerical libraries
- a small number of distinctive features that genuinely help scientific and engineering work

That is the direction most likely to make Perseus feel like a serious language for readable executable mathematics rather than only a historical Algol continuation.
