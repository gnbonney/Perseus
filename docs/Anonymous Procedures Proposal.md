### Anonymous Procedures in Perseus
**Design Specification**
**Author:** Greg Bonney
**Date:** April 8, 2026
**Version:** Draft 1.0

#### 1. Motivation
Perseus adds support for **anonymous procedures** (also called lambda expressions, closures, or function literals in other languages). These are first-class values that can be:
- Defined inline without a name.
- Assigned to variables.
- Passed as arguments to other procedures.
- Returned from procedures.
- Used for higher-order programming (map/filter-style operations, callbacks, custom comparators, etc.).

This feature brings modern expressiveness to Perseus while staying true to its ALGOL roots: strong static typing, block structure, and efficiency for numerical/scientific computing. Closures capture enclosing variables (by reference by default for performance).

#### 2. Syntax
Anonymous procedures are introduced with the keyword **`proc`**.

**General form:**
```algol
proc (parameter-list) result-type : body
```

- `proc` - starts the anonymous procedure.
- `(parameter-list)` - typed parameters, e.g., `(real x, integer n)`. Parentheses are required for zero or multiple parameters; optional for a single parameter in some shorthand cases.
- `result-type` - the return type (e.g., `real`, `bool`, `void` for no result). Can often be inferred in simple cases.
- `:` - separates the header from the body (ALGOL-like and easy to read).
- `body` - either a single expression or a compound statement (`begin ... end`).

**Shorthand variations** (for convenience):
- Single parameter without parentheses: `proc real x real : x * x`
- No parameters: `proc void : print("hello")`
- Void result (procedure, not function): `proc (integer i) : print(i)`

**Assignment example:**
```algol
real procedure (real) square := proc (real x) real : x * x;
```

#### 3. Semantics
- The type of an anonymous procedure is a **procedure mode**, e.g., `proc(real) real` (a procedure taking a real and returning a real).
- **Lexical scoping / closures**: Variables from the surrounding scope are captured. By default, capture is by reference (efficient for numerical code); an optional `value` keyword can force value capture if needed in future extensions.
- Full static type checking - consistent with Perseus's ALGOL-style strong typing.
- Efficient compilation: trivial anonymous procedures can be inlined; closures that capture variables are implemented with minimal overhead.
- Can be used anywhere a named procedure designator or formal parameter of procedure mode is expected.

#### 4. Code Samples

**Example 1: Simple squaring function**
```algol
begin
  real procedure (real) square := proc (real x) real : x * x;
  print(square(5.0))  // 25.0
end
```

**Example 2: Applying a transformation to an array (higher-order)**
```algol
procedure apply(real array A[1:n]; proc (real) real f);
  for i := 1 step 1 until n do
    A[i] := f(A[i]);

real array data[1:10];
... initialize data ...

apply(data, proc (real x) real : x * 2.0 + 1.0);
```

**Example 3: Closure capturing an outer variable**
```algol
real threshold := 10.0;

real procedure (real) above_threshold := proc (real x) real :
  if x > threshold then x else 0.0;

print(above_threshold(15.0));  // 15.0
threshold := 20.0;
print(above_threshold(15.0));  // 0.0  (reflects change via reference capture)
```

**Example 4: Custom comparator for sorting**
```algol
procedure sort_real(real array A[1:n]; proc (real, real) bool compare);
  ... bubble sort or quicksort using compare ...

sort_real(data, proc (real a, real b) bool : abs(a) < abs(b));
```

**Example 5: Multi-statement body**
```algol
integer procedure (integer) factorial := proc (integer n) integer :
  begin
    integer result := 1;
    for i := 1 step 1 until n do
      result := result * i;
    result
  end;
```

#### 5. Alternative Syntax Options
If `proc ... :` doesn't feel right, here are other easy-to-type alternatives:
- **Keyword `lambda`** (most common in modern languages like Python):
  `lambda (real x) real : x * x`
- **Arrow syntax** (popular in JavaScript, C#, Java, Julia):
  `(real x) => x * x` or `real x => x * x` (with type inference)
- **Short `fn`** (used in Rust, Swift-inspired):
  `fn (real x) real : x * x`

I recommend starting with **`proc`** because it aligns best with Perseus being an ALGOL descendant and avoids any conflict if you later add other keywords.

#### 6. Implementation Notes
- The parser treats `proc` as a new reserved word in expression context.
- Type checker supports procedure modes for variables, parameters, and results.
- Optimizer can inline short anonymous procedures.
- Backward compatibility: All existing named `procedure` declarations remain unchanged.
