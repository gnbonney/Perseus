## Development Environment

This project is developed using VS Code with Gradle. The ANTLR grammar is auto-compiled by the Gradle ANTLR plugin whenever the grammar file changes (`gradle build` or `gradle generateGrammarSource`).

The project was originally developed under Eclipse, and some legacy Eclipse-related files may still be present in the repository.

## Methodology

At first I attempted to convert the entire Algol 60 BNF to an Antlr grammar, compile it, and run it against a small Algol program.  I had to make some changes to the grammar to get it to compile due to left recursion errors.  Even after reworking the grammar to fix those I ran the generated parser against a simple hello world program and got a "no viable alternative" error that I didn't know how to resolve.  

So, I started over with a more iterative test-driven approach where I started with a simple "hello world" input code, wrote the minimum Antlr grammar required to parse just that program, and the plan is to gradually add more to the grammar for increasingly complex test samples.  Hopefully by adding just a little at a time I can avoid the weird grammar errors or at least be able to isolate where they are coming from.

One thing I will have to be careful about in basing my grammar on code samples instead of the official bnf is that some language features described in the report may be not very obvious.  

## Choice of Parsers

There are many choices for parser generators.  However, I wanted one written in Java that generates Java code that has a permissive open source license and good documentation.  Other than Antlr, I also considered CUP.  Jasmin, the Java Assembler, uses CUP.  CUP is a LALR(1) parser generator whereas Antlr is LL(*). Other examples of LALR(1) parser generators are Yacc and Bison.  

LR parsers like CUP can handle left recursion, which LL parsers have more trouble with.  According to the Antlr documentation, however, "v4 accepts even left recursive grammars, except for indirectly left recursive grammars where x calls y which calls x".  

With LR parsers "...you have to deal with somewhat cryptic errors like shift-reduce and (the dreaded) reduce-reduce. These are errors that Bison catches when generating the parser, so it doesn't affect the end-user experience, but it can make the development process a bit more interesting. ANTLR is generally considered to be easier to use than YACC/Bison for precisely this reason."

## Bytecode Assembler

Jasmin is the defacto standard bytecode assembler.  There are others out there, but jasmin works well for this project.

This project uses **Jasmin 2.4**, included as `jasmin-2.4/jasmin.jar`. It is self-contained (bundles `jas/` and `java_cup/runtime/`) and is referenced directly as a local file dependency in `build.gradle`. Jasmin 3.x exists as a Maven artifact (`ca.mcgill.sable:jasmin:3.0.3`) maintained by the Soot project, but it is based on an older version (Jasmin 1.06) and is not used here.

There may be some advantage to using a tool like Soot for bytecode analysis or disassembly (see below), but it is not needed for compilation.

According to Rutishauser (p. 205), "The ALGOL report allows the body of a procedure to be written in a
language different from ALGOL, e.g. in internal machine code or in an
assembly language."  The algol report actually says, "It is understood that the procedure body may be expressed in non−ALGOL language. Since it is intended that the use of this feature should be entirely a question of implementation, no further rules concerning this code language can be given within the reference language."

The example given by Rutishauser looks something like this:

```
procedure precmp (x, y, z) res: (c, d) ;
integer x, y, z, c, d ;
<assembly code here>
```

The code procedure has no "begin" or "end".  It seems like there should be some kind of delimiters to show where the assembler begins and ends, but in his example there's not, so I'm not sure how the compiler understands where the inline assembly begins or ends.

Anyway, I think this code procedure concept would be handy for inserting bits of Jasmin assembler code for an Algol-to-JVM compiler.  For example, I could create many of the Algol standard functions as code procedures that call functions from java.lang.System and java.lang.Math instead of having them hard-coded into the compiler.  They could be in an algol.lang.Environment class.  Eventually, the math functions might be re-implemented in pure algol, but the i/o functions would probably always rely on the JRE.

## Disassembling JVM

In order to learn how to write the jasmin code it will be helpful to be able to disassemble compiled class files and study them.

Soot can disassemble bytecode for Java 7 and other JVM languages (https://github.com/Sable/soot/wiki/Disassembling-classfiles) to various intermediate formats, including jasmin (and is actually built on top of jasmin). Soot is **not currently a build dependency** of this project — it was removed because the Soot artifact pulls in Jasmin 3.x as a transitive dependency, which conflicted with our use of Jasmin 2.4. Soot can still be used as a standalone tool for analysis if needed, or added back as a `testImplementation`-only dependency in the future.

Example Soot invocation (standalone, not wired into the build):
```
java -cp soot.jar gnb.algol.programs.ManOrBoy -soot-class-path bin:rt.jar -f jasmin
```

## Symbol Tables

Most likely the compiler will be a multi-pass compiler.  The first pass will generate a symbol table for variables indicating their type and scope.  

I found a google groups discussion where somebody was asking about how to implement a symbol table for their compiler:  https://groups.google.com/forum/#!topic/antlr-discussion/uijq7JEBvRo

### Complications

* Scope rules are different from Algol to Java.  Algol has nested procedures, but Java does not.  Algol also has call-by-name, but Java does not.  Implementing these features on the JVM will be non-trivial.  
* Java doesn't have nested procedures.  The Free Pascal documentation describes how nested procedures are implemented in that compiler:  "When a routine is declared within the scope of a procedure or function, it is said to be nested. In this case, an additional invisible parameter is passed to the nested routine. This additional parameter is the frame pointer address of the parent routine. This permits the nested routine to access the local variables and parameters of the calling routine."
* Algol parameters are passed by value or by name.  Call-by-name was apparently usually implemented by something called a "thunk" (http://wiki.c2.com/?CallByName).  Java does not have call-by-name, so implementing it in jvm bytecode may be challenging.  Scala is a JVM language that does have pass by name (see https://dzone.com/articles/scala-call-me-my-name-please).

## JVM Implementation Strategy for Call-by-Name

The JVM actually makes this quite doable.

You can represent a name parameter as something like:

```java
interface Thunk<T> {
	T get();
	void set(T v);
}
```

The caller generates an implementation capturing the environment.

Example translation idea:

```
sum(i,1,10,A[i])
```

becomes something like:

```java
new Thunk<Integer>() {
	Integer get() { return A[i]; }
	void set(Integer v) { A[i] = v; }
}
```

The procedure then calls `get()` whenever the parameter is used, and `set()` to assign to it.

This is very close to how Scala implements call-by-name: it compiles parameters into zero-argument functions (lambdas or anonymous classes) that are invoked each time the parameter is referenced.

## JVM Implementation Strategy for Procedure References

Similar to call-by-name parameters, ALGOL 60 allows procedures to be treated as first-class values that can be stored in variables and passed as parameters. This requires representing procedure references as JVM objects.

### Procedure Reference Interface

We extend the thunk pattern to create procedure reference objects:

```java
interface Procedure {
    Object invoke(Object... args);
}
```

Type-specialized versions for different return types:
```java
interface VoidProcedure {
    void invoke(Object... args);
}
interface RealProcedure {
    double invoke(Object... args);
}
```

### Implementation Strategy

1. **Synthetic Class Generation**: For each procedure reference, generate a synthetic class that implements the appropriate Procedure interface
2. **Static Method Delegation**: The synthetic class delegates to the actual static method via `invokestatic`
3. **Variable Storage**: Procedure variables store instances of these synthetic classes
4. **Dynamic Invocation**: When calling through a procedure variable, invoke the interface method

### Example Generated Code

For `real procedure P; P := getPi; outreal(1, P);`:

```java
// Generated synthetic class
class Main$ProcRef0 implements RealProcedure {
    public double invoke(Object... args) {
        return Main.getPi(); // delegate to static method
    }
}

// In main method:
P = new Main$ProcRef0(); // store procedure reference
outreal(1, ((RealProcedure)P).invoke()); // call through reference
```

This approach maintains consistency with the existing thunk-based architecture while enabling ALGOL's procedure-as-values semantics.

### Complications
* Some variables or procedures may need to be renamed, because the list of reserved words differ between Algol and Java.  For example, "int" is used as a variable name in the Algol 60 modified report, but it is a reserved word in Java.
* Algol does not have exceptions, but the JVM, of course, could throw an exception.  The Algol 60 standard has a fault procedure, but it seems all it does it output an error message and then stop the program; it is like a throw with no catch.  Burroughs/Unisys Extended Algol had "fault declarations" and an ON statement for catching faults.  This seems to be the best example of how to do exceptions in the Algol flavor.

## Language IDE Support

There's a tool called EMFText (http://www.emftext.org/index.php/EMFText) in the Eclipse Marketplace that could potentially be used for creating an Eclipse plugin for with syntax highlighting and auto completion features.

## Resources/References

https://github.com/csaroff/MiniJava-Compiler - Compiler created as a college class project that implements a subset of Java using an Antlr parser and outputs to JVM bytecode

