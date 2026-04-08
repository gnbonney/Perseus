### Perseus: Proposed Extensions to Algol 60  
**Draft Version 0.1**  
**Focus**: Looping Constructs and Collections  
**Author**: Greg Bonney
**Date**: March 2026  

#### 1. Motivation
Algol 60 provides a powerful but limited `for` statement (with `step … until` and `while` forms) and fixed-size `array` declarations. These work well for numerical computing but feel restrictive for general-purpose programming in 2026.

Perseus extends Algol 60 with:
- Safer, more expressive **container-oriented iteration** over arrays and later collections.
- A general-purpose **infinite loop** primitive.
- Dynamic, high-level **collections** (`vector`, `map`, `set`) with clean syntax and rich iterator support.
- Higher-order operations via **iterator pipelines** (composable `map`, `filter`, etc.).

These additions maintain Algol’s readability and block structure while reducing boilerplate and off-by-one errors.

#### 2. Looping Constructs

##### 2.1 Retained from Algol 60 (for compatibility)
The original `for` statement remains unchanged:

```algol
for i := 1 step 1 until 100 do
    sum := sum + i;

for i := 1, 3, 7, 20 while x > 0 do
    process(i);
```

##### 2.2 New Bounded Iteration: `for … in … do`
A clean, modern for-each style that works with arrays first and later collections.

**Syntax**:
```algol
for <variable> in <array or collection> do <statement>

for <pattern> in <collection> do <statement>   // future pattern matching
```

The iteration variable should be an already-declared ordinary Perseus variable with a type compatible with the element type being traversed. In that respect, `for ... in ... do` should remain closer to Algol's existing `for` statement than to languages that treat the loop header as an implicit declaration form.

**Examples**:
```algol
// Numeric counting remains with the traditional Algol for statement
for i := 1 step 1 until 100 do
    sum := sum + i;

// Over arrays and collections
vector integer data := [5, 12, 8, 3, 19];

for val in data do
    if val > 5 then sum := sum + val;

// With index (via standard library function)
for (idx, val) in enumerate(data) do
    print("Index ", idx, " = ", val);
```

**Semantics**:
- The control variable is an already-declared ordinary variable, not an implicit loop-local declaration.
- If an explicit outer declaration is desired for a reference element, ordinary declarations such as `ref(Point) p;` should appear before the loop.
- Assignment to the iteration variable inside the loop body does not change which element is visited next; the traversal order is controlled by the loop, as in the traditional Algol `for`.
- The array or collection expression is evaluated once at loop entry rather than being re-evaluated on each iteration.
- Works efficiently with fixed `array`, `vector`, `map`, `set`, and any type implementing the **Iterator** protocol.
- Numeric progression remains with the original Algol `for ... step ... until ... do` form rather than a separate symbolic range syntax.

##### 2.3 While Loop (clarified and retained)
```algol
while <boolean expression> do
begin
    ...
end
```

##### 2.4 General Loop Primitive
```algol
loop
begin
    ...
    if <condition> then break;
    ...
end
```

This is the most general form. It replaces many uses of `while true` and makes termination explicit.

**Early exit**:
- `break` — exits the innermost loop.
- `continue` — skips to the next iteration (supported in all loop forms).

#### 3. Collections

##### 3.1 Dynamic Vector (growable array)
Replaces most uses of fixed `array` when size is not known in advance.

**Declaration and literals**:
```algol
vector integer nums;                    // empty
vector real values := [1.0, 2.5, 3.14]; // literal

// Fixed-size Algol arrays remain for performance-critical code
array integer matrix[1:10, 1:10];
```

**Operations**:
```algol
nums.append(42);                    // or nums := nums + [42];
nums[0] := 99;                      // zero-based indexing (new default)
length := nums.size;                // or nums.length

for val in nums do ... 
```

##### 3.2 Map (associative array / dictionary)
```algol
map string real scores;

scores["Alice"] := 95.5;
scores["Bob"]   := 87.0;

if "Alice" in scores then
    print(scores["Alice"]);

// Literal
scores := {"Alice": 95.5, "Bob": 87.0};
```

##### 3.3 Set
```algol
set integer uniques := {1, 2, 3, 3};   // automatically deduplicates

uniques.insert(4);
if 2 in uniques then ...

for val in uniques do print(val);
```

**Literals summary**:
- Vector / array literal: `[1, 2, 3]`
- Map literal: `{"key": value, ...}`
- Set literal: `#{1, 2, 3}` or context-distinguished `{1, 2, 3}`

##### 3.4 Iterator Protocol and Pipelines
All collections support a standard **Iterator** interface. This enables powerful, declarative pipelines:

```algol
let result := data
    .filter(λ x: x mod 2 = 0)
    .map(λ x: x * x)
    .take(10)
    .sum();

vector integer evensSquared := data
    .filter(|x| x > 0)
    .map(|x| x * x)
    .collect();
```

Short lambda syntax: `λ x: …` or `|x| …` (both allowed).

#### 4. Full Example in Perseus
```algol
begin
    vector integer data := [5, 12, 8, 3, 19, 7];
    integer sum := 0;

    for val in data do
        if val > 5 then
            sum := sum + val;

    print("Sum of values > 5: ", sum);

    // Pipeline example
    let squares := data
        .filter(λ x: x mod 2 = 1)
        .map(λ x: x * x)
        .collect();

    for s in squares do print(s)
end
```

#### 5. Design Principles
- **Orthogonality**: Few concepts that compose well (`for … in` works with any iterable).
- **Backward compatibility**: Original Algol 60 `for`, `array`, and blocks continue to work.
- **Readability**: Keywords like `in`, `loop`, `break`, `filter`, `map` are clear and Algol-like.
- **Safety**: Control variables in `for … in` are scoped to the loop. Bounds checking on by default (with escape hatches).
- **Performance**: `vector` and fixed `array` compile to efficient contiguous memory; iterators allow zero-cost abstractions.
- **Extensibility**: New collection types can implement the Iterator protocol.

This suggests a clear division of responsibility:
- classic Algol `for` remains the numeric counting form
- `for ... in ... do` is the element-iteration form
- `loop` is the open-ended repetition form

#### 6. Optional Syntax Sugar
- Allow `{ … }` blocks as alternative to `begin … end` for teams coming from C-like languages.
- Make `do` optional in simple one-line loops (subject to further discussion).

#### 7. Next Steps
- Formal BNF grammar for the new constructs.
- Specification of the Iterator protocol (procedures or built-in interface).
- Standard library modules: `Collections`, `Iterators`.
- Discussion of generics for collections (`vector<T>`, `map<K,V>`).
- Error handling integration (e.g., `Result` type for safe access).

This draft keeps Perseus feeling like a natural evolution of Algol 60 — elegant, structured, and suitable for both teaching and serious programming — while incorporating the essential looping methods and collections needed for modern use.
