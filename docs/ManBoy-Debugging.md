# Man-or-Boy (manboy.alg) Debugging Notes

This document captures the current state of the `manboy_test` failure, the root cause analysis, and concrete next steps for getting the test passing.

The Man-or-Boy program is a classic call-by-name/closure semantics stress-test invented by Donald Knuth to distinguish correct implementations of name-parameter passing (Jensen's device) from incorrect ones. The goal of this note is to keep the diagnosis and progress focused on the core thunk/closure state issues that prevent the correct output (`-67.0`).


## Summary of Approaches Tried (2026)

- Refactored thunk/closure codegen to use per-call-site thunk classes and instance fields for captured variables.
- Attempted to fix stack discipline bugs in thunk/closure invocation (e.g., pop2 usage, dreturn mismatches).
- Validated nested-scope access and procedure-variable assignment (some tests now pass).
- Investigated and partially fixed proc-parameter call generation for void and string procedures.
- Explored and debugged static environment bridge fields (`__env_*`), but found they cause shared mutable state across activations.
- Confirmed that global static fields for captured variables break recursion and closure isolation.
- Used Grok and manual verifier analysis to trace operand stack mismatches and return value flow bugs.
- Repeatedly regenerated and inspected Jasmin output for ManBoy and related tests.

Despite these efforts, the core issue persisted: the code generator was still emitting invalid bytecode for certain closure/recursion patterns, and the JVM verifier continued to report operand stack or type errors.

---

## March 21, 2026: ASM Verifier Integration and New Findings

We have now integrated ASM's CheckClassAdapter into the test pipeline for `manboy_test`. This provides detailed bytecode verification and exposes concrete JVM type errors in the generated class files.

### Key ASM Verifier Output

The verifier reports:

   Error at instruction 28: Third argument: expected R, but found D B()D

This means the generated code is pushing a double (D) onto an Object[] array, but the JVM expects a reference (R). The problematic sequence is:

   ...
   27 INVOKEINTERFACE gnb/jalgol/compiler/RealProcedure.invoke ([Ljava/lang/Object;)D (itf)
   28 AASTORE
   ...

The code generator is emitting a primitive double where a boxed Double (reference) is required for storage in an Object array. This is a JVM type safety violation and will cause verification or runtime errors.

### Immediate Action Items

1. **Boxing required:** All primitive values (e.g., double) must be boxed (e.g., via `Double.valueOf`) before being stored in Object arrays for procedure argument passing.
2. **Locate codegen site:** Identify and update the codegen logic (likely in `ExpressionGenerator` or `StatementGenerator`) responsible for building argument arrays for procedure calls, ensuring correct boxing.
3. **Re-run ASM verification:** After fixing, regenerate Jasmin, reassemble, and re-run the ASM verifier to confirm the fix.

### Broader Implication


This finding confirms that the code generator must always box primitives when passing them as Object arguments to procedures. This is a general JVM requirement and applies to all call-by-name and procedure-variable invocations.

### Connection to Deferred-Typing (Milestone 13.1)

The ASM-reported boxing issue is a direct consequence of deferred-typing (see milestone 13.1 in Compiler-TODO.md). When the type of a procedure or call-by-name parameter is not known until the call site, the code generator must dynamically determine whether to box a primitive (int, double) or pass a reference. Failing to do so results in unboxed primitives being stored in Object[], causing JVM type errors. Correct boxing at call sites is essential for both JVM type safety and proper deferred-typing semantics.

---

