# Debugging Strategy: Procedure Variable Test Failures

This document outlines the strategy for resolving the current failures in the procedure variable support (Milestone 13A), specifically targeting the `VerifyError` and `ProcRef` instantiation issues.

## 1. Problem Definition: Observed Failures

The compiler currently fails to correctly handle procedure variables as demonstrated by two distinct error states in the test suite:

### A. JVM Verification Error (`ProcVar` Runtime)
**Observed Error:**
`Caused by: java.lang.VerifyError: (class: gnb/perseus/programs/ProcVar, method: main signature: ([Ljava/lang/String;)V) Illegal local variable number`

**Impact:** The compiled Algol program cannot be loaded by the JVM.
**Potential Indicators:** This typically occurs when a local variable instruction (load/store) uses an invalid index, such as `-1` or an index exceeding the `.limit locals` directive.

### B. ExpressionGeneratorTest: ProcRef Instantiation Failure
**Observed Error:**
`org.opentest4j.AssertionFailedError: Should instantiate a ProcRef class for the procedure ==> expected: <true> but was: <false>`
at `gnb.perseus.compiler.codegen.ExpressionGeneratorTest.testGenerateProcedureReferenceAsValue(ExpressionGeneratorTest.java:95)`

**Significance:** This is a crucial unit test failure. It indicates that the `ExpressionGenerator` is not triggering the creation of the synthetic `ProcRef` class when it encounters a procedure name used as a value (an R-value). If this low-level component doesn't generate the object placeholder, the higher-level assignment logic will have no valid object to store, contributing to the corrupted bytecode and `VerifyError`.

## 2. Research & Discovery: Instrumental Logging Strategy

To uncover the root causes without making premature assumptions about `istore -1`, we will use systematic logging across the compilation pipeline.

### Step 1: Trace Identifier Resolution
We need to know if the compiler recognizes procedure variables during the second pass.
- **Log Point:** Inside `StatementGenerator.generateAssignment` and `ExpressionGenerator.generateExpr`.
- **Query:** For each identifier, log the name, the retrieved type from the symbol table, and the retrieved index from the local mapping.

### Step 2: Monitor Scope Transitions
The transition between the main block and procedure declarations may be causing state loss.
- **Log Point:** `ContextManager.saveMainContext()` and `restoreMainContext()`.
- **Query:** Log the size and contents of `localIndex` before and after these calls to see if slot mappings are being dropped or overwritten.

### Step 3: Audit ProcRef Generation
Determine why `ProcRef` classes are missing in the unit test context.
- **Log Point:** `ProcedureGenerator.generateProcedureReference`.
- **Query:** Log when this method is entered, for which procedure, and whether it successfully calls `context.addProcRefClass`.

## 3. Targeted Debugging Commands

Use these commands to extract the actual state of the generated artifacts:

```bash
# 1. Inspect the generated Jasmin for invalid indices or incorrect store instructions
gradle compilePerseus -PinputFile=test/algol/proc_var.alg -PoutputDir=build/test-algol -PclassName=ProcVar
cat build/test-algol/ProcVar.j

# 2. Check if the ProcRef companion classes are actually being written to the output directory
ls build/test-algol/ProcVar$ProcRef*.j

# 3. Run the compiler with stacktrace to see if internal exceptions are being swallowed
gradle compilePerseus -PinputFile=test/algol/proc_var.alg -PoutputDir=build/test-algol -PclassName=ProcVar --stacktrace
```

## 4. Expected Outcome
The goal of this debugging strategy is to confirm:
1. Exactly which instruction and variable name causes the `VerifyError`.
2. Whether the `localIndex` contains valid mappings for `hello`, `goodbye`, and `P` during the assignment phase.
3. Why `ExpressionGeneratorTest` expects a `ProcRef` that the compiler decides not to create.
