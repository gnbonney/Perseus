# Generics Design Spec

This document outlines a possible generics design for Perseus. The immediate motivation is collection classes such as `Vector[T]`, `Map[K, V]`, and `Set[T]`, but the same design should be general enough to support other reusable library abstractions later.

The design here is intentionally scoped to what fits Perseus as an Algol-descended JVM language. It is not trying to reproduce the full surface of Java, C#, or Scala generics.

## Motivation

Perseus has now reached the point where several useful library directions want parameterized types:

- collection classes
- iterator abstractions
- future JSON and structured-data support
- mathematical containers and helper abstractions

Without generics, these directions tend to collapse into one of two bad outcomes:

- a large family of duplicated specialized classes such as `IntVector`, `RealVector`, `StringVector`, and so on
- weak `ref(Object)`-style APIs that erase useful type information at the source-language level

Generics are therefore not just a convenience feature. They are likely the cleanest way to move collection support out of compiler special cases and into ordinary Perseus library design.

## Design Goals

- Support generic classes first.
- Keep the source syntax readable and consistent with Perseus's Algol-family tone.
- Prefer a simple erased-runtime model on the JVM.
- Avoid advanced features such as wildcards, bounds, and variance in the first slice.
- Make collection classes the main motivating use case, but do not hardwire the feature to collections alone.
- Replace the current string-based ad hoc type tags in the compiler with a structured internal type model.

## Proposed Surface Syntax

### Generic class declarations

The simplest proposed form is bracketed type parameters:

```algol
class Vector[T];
begin
    procedure append(T value);
    T procedure get(integer i);
    procedure set(integer i, T value);
    integer procedure size
end;
```

For multiple parameters:

```algol
class Map[K, V];
begin
    procedure put(K key, V value);
    V procedure get(K key);
    boolean procedure contains(K key)
end;
```

```algol
class Set[T];
begin
    procedure insert(T value);
    boolean procedure contains(T value);
    boolean procedure remove(T value)
end;
```

### Generic type use

The corresponding reference types would look like:

```algol
ref(Vector[integer]) nums;
ref(Map[string, real]) scores;
ref(Set[integer]) seen;
```

Instantiation would follow the same type-argument spelling:

```algol
nums := new Vector[integer];
scores := new Map[string, real];
seen := new Set[integer];
```

### Relation to existing collection syntax

If Perseus adopts generic collection classes, the current collection declaration syntax can be treated as source-level sugar rather than as a separate collection system:

```algol
vector integer nums;
map string real scores;
set integer seen;
```

could be defined as shorthand for something like:

```algol
ref(Vector[integer]) nums;
ref(Map[string, real]) scores;
ref(Set[integer]) seen;
```

with automatic empty construction preserved where desired.

That would allow Perseus to keep the existing readable collection surface while grounding it in real library classes.

## Why Brackets

Bracketed type arguments are the clearest fit for Perseus's current parser and surface language:

- they are concise
- they nest cleanly
- they are already familiar to modern programmers
- they avoid overloading existing class parameter parentheses

Alternatives such as `of` syntax are possible:

```algol
ref(Vector of integer) nums;
```

but are likely harder to parse and less clean for nested types.

Bracketed type arguments are therefore the best first design candidate.

## First-Slice Scope

The first generic slice should be deliberately small:

- generic classes
- generic class instantiation
- generic class reference types
- generic method/procedure signatures inside generic classes

The first slice should not initially include:

- generic procedures outside classes
- variance
- bounds or constraints
- wildcard syntax
- runtime reification of type arguments

This narrower slice is enough to support real collection classes and avoids taking on more type-system surface than is needed immediately.

## JVM Strategy

The recommended implementation strategy is erased generics.

That means:

- `Vector[integer]`, `Vector[real]`, and `Vector[ref(Point)]` all lower to the same runtime class
- runtime storage uses ordinary object references
- numeric values are boxed when stored in generic positions
- generated code inserts casts and unboxing as needed at use sites

This fits the JVM naturally and matches the way Perseus collections already behave internally today.

For example:

- `integer` elements would use boxed `Integer`
- `real` elements would use boxed `Double`
- `boolean` elements would use boxed `Boolean`
- reference-typed values remain ordinary object references

The first implementation should not attempt reified generics. That would add much more runtime complexity and is not necessary to make the feature useful.

## Implications for Collection Design

If generics are added, a more complete library-oriented collection design becomes possible.

For example:

```algol
class Vector[T];
begin
    integer procedure size;
    procedure clear;
    procedure append(T value);
    procedure insert(integer index, T value);
    T procedure remove(integer index);
    boolean procedure contains(T value);
    T procedure get(integer index);
    procedure set(integer index, T value);
    iterator(T) procedure iterator
end;
```

```algol
class Map[K, V];
begin
    integer procedure size;
    procedure clear;
    boolean procedure contains(K key);
    V procedure get(K key);
    procedure put(K key, V value);
    V procedure remove(K key);
    Vector[K] procedure keys;
    Vector[V] procedure values
end;
```

```algol
class Set[T];
begin
    integer procedure size;
    procedure clear;
    procedure insert(T value);
    boolean procedure contains(T value);
    boolean procedure remove(T value);
    iterator(T) procedure iterator
end;
```

This is much closer to the intended direction for Milestone 43 than the current compiler-special-cased collection surface.

## Compiler Impact

### Current limitation

Today, much of the compiler represents types as plain strings, for example:

- `integer`
- `real`
- `string`
- `boolean`
- `ref:Point`
- `vector:integer`
- `map:string=>real`
- `set:integer`
- `procedure:integer`

This works for the current feature set, but it does not scale well to generics.

A generic type system would quickly force more and more special parsing logic into these strings:

- `ref:Vector[integer]`
- `ref:Map[string, real]`
- `procedure:ref:Vector[integer]`
- nested structures such as `ref:Vector[ref:Point]`

That approach would become brittle and hard to maintain.

### Proposed replacement

The compiler should move toward a structured internal type model.

A simple first model could look conceptually like:

- `ScalarType("integer")`
- `ScalarType("real")`
- `ScalarType("string")`
- `ScalarType("boolean")`
- `RefType("Point")`
- `ArrayType(elementType, boundsInfo?)`
- `ProcedureType(returnType, parameterTypes?)`
- `GenericInstanceType("Vector", [ScalarType("integer")])`
- `GenericInstanceType("Map", [ScalarType("string"), ScalarType("real")])`
- `TypeParameterType("T")`

The exact Java class names are not important yet. The important change is structural:

- symbol tables should store `Type` objects rather than raw strings
- inferred expression types should also be `Type` objects
- compatibility logic should reason over type structure directly

### Where this affects the compiler

The main areas affected would be:

- grammar and parse-tree mapping for generic declarations and uses
- symbol table entries
- expression type inference
- assignment compatibility checks
- procedure and method signatures
- class metadata
- code generation descriptors
- boxing and unboxing in generic contexts

### Suggested migration path

The cleanest path is likely incremental:

1. Introduce a `Type` representation internally while preserving current user-visible behavior.
2. Migrate scalar, ref, array, procedure, and current collection types onto that representation.
3. Remove as much string-prefix type logic as possible.
4. Add generic class declarations and generic type applications.
5. Add erased code generation for generic classes.
6. Move collection design onto generic stdlib classes.

This would let the compiler evolve toward generics without trying to convert every subsystem in one step.

## Interaction with External Java Interop

For the first slice, Perseus generics should be largely a source-level feature with erased runtime behavior. That means Java interop can continue to work at the raw JVM type level:

- `Vector[integer]` still interoperates as an object reference
- Java collection-backed storage can still be used underneath
- static interop can continue matching erased JVM descriptors

The richer question of how to expose Java generic signatures back into Perseus source can remain later work.

## Interaction with Anonymous Procedures and Iterators

Generics would make later iterator and pipeline work much cleaner.

For example:

```algol
class Iterator[T];
begin
    boolean procedure hasNext;
    T procedure next
end;
```

and later:

```algol
class Vector[T];
begin
    Vector[T] procedure filter(proc (T x) boolean predicate);
    Vector[U] procedure map[U](proc (T x) U transform)
end;
```

The first slice does not need to include all of that. But generics would make those future directions much more coherent.

## Non-Goals for the First Slice

The first generics implementation should not try to solve everything.

It should not initially include:

- subtype variance
- bounded type parameters
- higher-kinded types
- specialization for primitive performance
- generic inference so aggressive that explicit type arguments become rare
- full Java-generic signature import/export

The goal is to make generic library design practical, not to turn Perseus into a research type system.

## Recommended Roadmap Interpretation

Generics now look like a plausible prerequisite for the long-term collection-class design envisioned under Milestone 43.

That does not necessarily mean every part of Milestone 43 must stop immediately, but it does suggest:

- the class-based collection design should probably wait for at least a first generics design decision
- further heavy investment in compiler-special-cased collection behavior should be approached cautiously

Generics may deserve either:

- a dedicated new milestone between collections and later math-oriented type work
- or a promoted role inside the remaining Milestone 43 architecture work

## Summary

Generics would let Perseus move from compiler-special-cased collections toward real library-owned collection classes.

The most promising design is:

- generic classes first
- bracketed type arguments such as `Vector[integer]`
- erased JVM implementation
- a structured internal compiler type model replacing the current string-heavy approach

That would be a substantial feature, but it would likely improve not only collections, but also the overall future of reusable libraries in Perseus.
