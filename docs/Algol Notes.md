This document describes some features of the Algol language that may not be obvious to the beginner.

# Code Blocks vs Compound Statements

"A block differs from a compound statement in that it contains declarations."  ("Introduction to ALGOL 60 for those who have used other programming languages" by A. N. Habermann, 1971.)  This is a distinction not existing in C-like languages.

I think an Algol compiler could probably treat them as the same thing, which would maybe simplify the grammar for parsing.  

# Procedures

C-like languages (C++, C#, Java, Javascript) have functions but not procedures.  Algol-like languages (Pascal, PL/SQL, etc.) have both.  Functions in Algol normally return exactly one value, although there can be side-effects, whereas procedures can be written to return zero or more values up to some compiler-defined limit.  So, you could say that procedures are sort of like void functions in C.

# Parameter Passing

Java can only pass parameters by value, although in many cases the value being passed is actually a reference to an object so that it acts like pass-by-reference.  In contrast, Algol has pass-by-value and by-name while Simula extends Algol to add by-reference (and demotes by-name to be an optional mechanism instead of the default).  

Scala is one of the few newer programming languages that has call-by-name.  In reading several Scala programmers' discussions online, I get the impression there is no bias against call-by-name.  However, in discussions of Algol call-by-name is often maligned generally on the grounds that it can cause surprising side effects.  

### Call-by-value

The argument expression is evaluated **once** at the call site and the result is copied into the procedure's local parameter slot. Changes to the parameter inside the procedure do not affect the caller. Declared explicitly with `value`:

```algol
real procedure double(x);  value x;  real x;
begin double := x * 2 end;
```

### Call-by-name (the Algol default)

The argument expression is passed as a *thunk* — a callable object that re-evaluates the expression in the **caller's environment** every time the parameter is read or written inside the procedure. This is the default when `value` is not declared.

```algol
real procedure sumof(start, stop, index, expr);
value start, stop;           comment evaluated once;
integer start, stop, index;  comment index is call-by-name;
real expr;                   comment expr is call-by-name;
begin ...
```

Here, reading `index` inside the procedure re-evaluates whatever variable was passed by the caller, and writing `index := index + 1` updates the caller's variable directly. Reading `expr` re-evaluates the entire expression (e.g. `i*i`) in the caller's scope on each access, so its value changes as `index` changes. This is Jensen's Device.

Deferred typing: The Revised ALGOL 60 Report allowed type specifications for call-by-name parameters to be optional, which permits different call sites to pass values of different (but compatible) types to the same procedure. This is not the same as full dynamic typing: the language intent is compile-time flexibility where the compiler resolves the appropriate representation at each call site (for example, choosing whether to pass a primitive or a thunk that boxes values), rather than attaching runtime type tags to every value. In practical terms (and how Perseus approaches it), when a formal parameter has no explicit type the compiler should prefer the declared type if present, otherwise choose the actual argument's inferred type at the call site (from `exprTypes`), and only fall back to a conservative default (e.g., `integer`) when inference is not available. This preserves Algol's higher-order/call-by-name flexibility while keeping JVM method descriptors and local-slot usage predictable.

### Comparison with Java

Java has a single parameter passing mode often described as "pass-by-value, always":

| Java type | What is passed | Writes affect caller? |
|---|---|---|
| Primitive (`int`, `double`) | A copy of the value | No |
| Object reference | A copy of the *reference* | Mutating the object: yes; reassigning the variable: no |

Mapped to Algol terms:

| | Algol call-by-value | Algol call-by-name | Java |
|---|---|---|---|
| Argument evaluated | Once, at call | Every access, in caller scope | Once, at call |
| Writes affect caller | No | Yes | No (for primitives) |
| Lazy/re-evaluation | No | Yes | No |

**Algol call-by-value ≈ Java primitives** — effectively the same for `integer` and `real`.

**Algol call-by-name has no direct Java equivalent.** The closest analogies are `Supplier<T>` (read-only re-evaluation) combined with a mutable wrapper for writes, but neither gives you the transparent read+write syntax Algol provides. Perseus implements call-by-name using a `Thunk` interface with `get()` and `set()` methods, generating a synthetic `$ThunkN` class at each call site — the boilerplate that Algol requires the compiler to produce automatically.

## Nesting

Procedures can be declared within procedures.  This is different from C.  Java does allow nesting of classes (inner classes).

## Begin/end optional

Algol procedures can be declared without begin and end as long as the body of the procedure consists of only one basic statement instead of a block.  Rutishauser's book contains at least one example like this (on page 200).

```
procedure c1 (nr, k, p, n, a, r, label) ;
   value nr ;
   integer k, p, n, nr ; array a, r ; label label ;
      if nr = 1 then goto label
```

## Labels and Goto

The above example also demonstrates a weird feature where you can pass a label for a goto in the parameters.  It would appear this allows a procedure to exit and resume execution at an arbitrary place within the same code block where the procedure was declared.  

Algol does have the restriction that you cannot jump into the middle of a block from the outside, because then the variables declared at the top would be undefined and because a label is considered to be within the scope of the block where it is defined.

In "The Remaining Troublespots in Algol 60", Knuth suggested that early exit from a function with a goto (to a label outside the function) could cause side effects.  Knuth states that "Two rather convincing arguments can be put forward that this type of exit is not really allowed by Algol..."  However, paragraph 5.4.4 of the modified report addresses the issue and specifically allows for this behavior.

In "A commentary on the ALGOL 60 Revised Report" by R.M. De Morgan, I.D. Hill, B.A. Wichmann, the authors state:  "Numerical Labels add in no way to the power or usefulness of the Language although providing difficulties for the compiler-writer. They must now be regarded as obsolete in the full Language as well as in the subsets."  Unfortunately, this suggested change did not make it into the modified report. However, as Knuth states in his "Remaining Troublespots" article that most Algol compilers did not allow numeric labels.  As an example, NU ALGOL (Norwegian University Algol) did not allow numeric labels.

NU ALGOL placed the following additional limitations on GOTO:  
* "Only labels which are local or global may be used in a designational expression in a certain block. That is, GO TO statements may only lead to statements in the same block or in an enclosing block, never to statements in a non-nested block."
* "A GOTO leading to a label within the FOR statement is illegal. A label may, however, be used for a jump within the statement following DO."

One might question whether it is worthwhile to implement all the nuanced semantics of Algol goto and labels when apparently most old Algol compilers did not attempt to implement all those nuances and anyway these days goto is "considered harmful".  I feel that the compiler should impose whatever limitations are reasonable to prevent mis-use.

## Switch vs Case

In Algol, a switch is just an array of labels to be used in conjunction with the goto statement. Algol W replaced the switch with the case statement thereby reducing the need for GOTO and labels.

## Functions in Algol

You can also declare a procedure like a function.  In "Introduction to ALGOL 60 for those who have used other programming languages" by A. N. Habermann, 1971, the author calls these "type procedures".  

```
real procedure MAX(x,y); real x,y;
begin
if x > y then MAX:= x else MAX: = y
end;
```

The Algol report does allow procedures to return a value in both of these ways. 

## Named argument lists

In Algol a procedure can have multiple named parameter lists.  This example is from the official algol 60 report:

```
procedure Spur(a) Order:(n) Result:(s); value n;
array a; integer n; real s;
begin integer k;
   s := 0;
   for k := 1 step 1 until n do s := s + a[k, k]
end
```

The way you call Spur is as follows:

```
Spur(A) Order:(7) Result to:(V)
```

This is the only example from the Algol report of using the "to" keyword to return a value from a procedure.  It does appear (from other examples) that you can declare multiple result values in this way.  

Rutishauser suggests a convention for "structurized" procedure declarations:

> As an example, the formal parameter part in
> <pre>procedure mica (a, b, c) trans: (d, e) res: (I) exit: (g, h) ;
>    real c ; integer e ; array f; Boolean array d ;
>    label g ; switch h ; procedure b ; string a ; ...</pre>
> indicates that procedure mica has the following operands:
> 1) String a, procedure b, and the real variable c are arguments.
> 2) Boolean array d and the integer variable e are transients.
> 3) The real array f is result.
> 4) Label g and switch h are exits.
