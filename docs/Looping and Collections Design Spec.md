### Perseus: Proposed Extensions to Algol 60  
**Draft Version 0.1**  
**Focus**: Looping Constructs and Collections  
**Author**: Greg Bonney
**Date**: March 2026  

#### 1. Motivation
Algol 60 provides a powerful but limited `for` statement (with `step … until` and `while` forms) and fixed-size `array` declarations. These work well for numerical computing but feel restrictive for general-purpose programming in 2026.

Perseus extends Algol 60 with:
- Safer, more expressive **container-oriented iteration** over arrays and later collections.
- Clearer structured **pre-test and post-test loops** alongside the classic Algol `for`.
- Dynamic, high-level **collections** (`vector`, `map`, `set`) with clean syntax, rich iterator support, and Java-backed runtime implementations.
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

This form is deliberately retained for Algol compatibility rather than as a new "fast path" loop construct. Because `for`-list elements may need to be re-evaluated as iteration proceeds, code generation naturally uses a more general control-flow shape with multiple conditional branches and temporary locals for loop state. That is acceptable and appropriate for heritage code, but it should not be advertised as the preferred form for the hottest inner loops when a simpler loop construct expresses the same intent.

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

This same loop surface should also cover ordinary external Java objects that implement `java.lang.Iterable`:

```algol
begin
    external java class java.util.ArrayList;
    external java class java.lang.Integer;
    external java static(java.lang.Integer)
        ref(Integer) procedure valueOf(integer boxedint);

    ref(ArrayList) values;
    integer item, sum;

    values := new ArrayList();
    values.add(valueOf(1));
    values.add(valueOf(2));
    values.add(valueOf(3));

    for item in values do
        sum := sum + item
end
```

Here the source still uses the ordinary Perseus `for ... in ... do` form. The Java object contributes the iteration behavior through `Iterable`, while the already-declared loop variable controls the expected element type in Perseus source.

##### 2.3 Structured Loops: `while ... do` and `repeat ... until`
```algol
while <boolean expression> do
begin
    ...
end

repeat
begin
    ...
end
until <boolean expression>
```

These forms give Perseus a cleaner and more familiar loop family in the broader Algol tradition:

- `while ... do` is the ordinary pre-test loop
- `repeat ... until` is the ordinary post-test loop
- `break` and `continue` remain available inside both forms

This direction is preferred over keeping a dedicated infinite-loop keyword. In practice, an open-ended loop can be written clearly as `while true do ...` with `break` and `continue`, while the language also gains the more readable standalone `while` and `repeat ... until` forms.

**Early exit**:
- `break` — exits the innermost loop.
- `continue` — skips to the next iteration (supported in all loop forms).

##### 2.5 Relation to Knuth and Dahl
Donald Knuth's discussion of structured looping in "Structured Programming with go to Statements" favors a small set of high-level loop forms that lower cleanly to simple control-flow graphs. In particular, he praises Ole-Johan Dahl's general loop style because it can express ordinary pre-test loops, post-test loops, and "test in the middle" loops without duplicating code.

Perseus's current direction is compatible with that advice even though the source syntax is simpler and more conventional:

- the original Algol `for` is retained as the expressive heritage form
- `while ... do` and `repeat ... until` provide the main structured loop forms, with `break` and `continue` for early exit
- `for ... in ... do` covers element-oriented traversal over arrays now and collections later

For JVM code generation, this is a sensible compromise. `while ... do`, `repeat ... until`, and `for ... in ... do` naturally lower to a clear loop header, a small number of conditional branches, and a single back-edge `goto`, which is exactly the kind of shape the JVM handles well. The original Algol `for` remains more general and therefore less ideal as a hot-loop form, but that is acceptable because its main job is semantic fidelity and compatibility with classic Algol code.

This means Perseus does not currently adopt Dahl's source syntax directly, but it does follow the same underlying principle: provide structured loop forms whose obvious lowering is already efficient, instead of depending on user-written `goto` for ordinary iteration.

#### 3. Collections

##### 3.1 Dynamic Vector (growable array)
Replaces most uses of fixed `array` when size is not known in advance.

The intended implementation direction is to back `vector` with an ordinary Java runtime collection rather than inventing a separate Perseus-native dynamic-array runtime. The same general principle should apply later to `map` and `set`.

That Java-backed direction should also make collection conversion easy at interop boundaries. Perseus code should have a straightforward way to wrap or convert Java collection values coming from external Java classes, and to hand Perseus collections back to Java code without awkward manual copying in user programs.

The intended core surface includes:

- `vector integer nums;` and the corresponding `real`, `boolean`, `string`, and `ref(T)` forms
- automatic empty construction backed concretely by `java.util.ArrayList`
- zero-based indexing with `nums[i]` and `nums[i] := value`
- append with `nums.append(value)`
- size access through both `length(nums)` and `nums.size` / `nums.size()`
- direct `for ... in ... do` traversal over vectors using the same loop-variable rules already established for arrays

At Java interop boundaries, the same `vector` values should flow through the more general `java.util.List` surface so ordinary Java `List`-based APIs can accept and return them without a separate custom collection ABI.

A Perseus program should still declare and use `vector`, while the external Java declaration naturally targets `List`-shaped APIs:

```algol
begin
    external java static(java.util.Collections)
        procedure reverse(vector integer values);
    external java static(java.util.Collections)
        vector integer procedure unmodifiableList(vector integer values);

    vector integer values, copy;
    integer item;

    values.append(1);
    values.append(2);
    values.append(3);

    reverse(values);
    copy := unmodifiableList(values);

    for item in copy do
        outinteger(1, item)
end
```

No explicit wrapper or conversion call should be needed for ordinary Java `List` interop. Perseus source stays in terms of `vector`, and the compiler maps that to the Java `List` surface at the boundary.

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
nums.insert(0, 41);
removed := nums.remove(1);
if nums.contains(42) then ...
nums.clear();
nums[0] := 99;                      // zero-based indexing (new default)
length := nums.size;                // or nums.length

for val in nums do ... 
```

##### 3.2 Map (associative array / dictionary)
```algol
map string real scores;

scores["Alice"] := 95.5;
scores["Bob"]   := 87.0;
if scores.contains("Alice") then
    print(scores["Alice"]);
removed := scores.remove("Bob");
scores.clear();

// Literal
scores := {"Alice": 95.5, "Bob": 87.0};
```

##### 3.3 Set
```algol
set integer uniques;

uniques.insert(1);
uniques.insert(2);
uniques.insert(3);
uniques.insert(3);   // automatically deduplicates
removed := uniques.remove(3);
uniques.insert(4);
if uniques.contains(2) then ...
uniques.clear();

for val in uniques do print(val);
```

**Literals summary**:
- Vector / array literal: `[1, 2, 3]`
- Map literal: `{"key": value, ...}`
- Set literal: `#{1, 2, 3}` or context-distinguished `{1, 2, 3}`

##### 3.4 Iterator Protocol and Pipelines
All collections support a standard **Iterator** interface. That protocol should bridge both Perseus collection syntax and Java-hosted iterable/container implementations rather than splitting them into separate models. This enables powerful, declarative pipelines:

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

##### 3.5 Java Collection Conversion
Because Perseus collections are intended to be backed by Java runtime collections, the interop story should include direct conversion helpers at the language or standard-library boundary.

Examples of the intended shape:

```algol
vector integer nums;
nums := vectorfromjava(javaNums);

external java static(java.util.List.copyOf) ref(Object) procedure copyof;
ref(Object) javaCopy;
javaCopy := vectortojava(nums);
```

The exact helper names and typing surface can evolve, but the design direction should be explicit:

- external Java methods that return collection values should be easy to bring into ordinary Perseus collection use
- Perseus collections should be easy to hand back to Java APIs
- `vector` should interoperate through `java.util.List` at external Java procedure boundaries
- common conversions should live in the standard library rather than requiring custom boilerplate in each user program

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
- **Readability**: Keywords like `while`, `repeat`, `until`, `in`, `break`, `filter`, and `map` are clear and Algol-like.
- **Safety**: `for ... in ... do` reuses an already-declared compatible variable, while the loop itself still controls traversal order and evaluates the traversed expression once at entry. Bounds checking stays on by default (with escape hatches).
- **Performance**: fixed `array` remains the direct bounded-array form, while dynamic collections should reuse efficient Java runtime collection implementations instead of introducing a second collection runtime.
- **Extensibility**: New collection types can implement the Iterator protocol.

This suggests a clear division of responsibility:
- classic Algol `for` remains the numeric counting form
- `for ... in ... do` is the element-iteration form
- `while ... do` is the pre-test form
- `repeat ... until` is the post-test form

#### 6. Optional Syntax Sugar
- Allow `{ … }` blocks as alternative to `begin … end` for teams coming from C-like languages.
- Make `do` optional in simple one-line loops (subject to further discussion).

#### 7. Next Steps
- Formal BNF grammar for the new constructs.
- Specification of the Iterator protocol (procedures or built-in interface).
- Standard library modules: `Collections`, `Iterators`, built on top of Java runtime collections.
- Discussion of generics for collections (`vector<T>`, `map<K,V>`).
- Error handling integration (e.g., `Result` type for safe access).

This draft keeps Perseus feeling like a natural evolution of Algol 60 — elegant, structured, and suitable for both teaching and serious programming — while incorporating the essential looping methods and collections needed for modern use.
