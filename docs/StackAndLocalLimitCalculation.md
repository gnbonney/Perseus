# Automatic Stack and Local Limit Calculation for JAlgol

## Motivation
- JVM requires accurate `.limit stack` and `.limit locals` directives for each method.
- Hard-coded values cause `VerifyError` and mask bugs in codegen.
- Algol programs with nested loops, procedure variables, and call-by-name require dynamic slot allocation and stack tracking.
- Deterministic, correct limits are essential for reproducible builds and robust testing.

## Goals
- Eliminate all hard-coded `.limit stack` and `.limit locals` values in codegen.
- Compute required limits for each method (main, procedures, thunks) based on:
  - Number of local variables (including temporaries, loop counters, procedure variables)
  - Maximum stack depth during execution (expression evaluation, procedure calls, array access, etc.)
- Integrate limit calculation into modular codegen (ExpressionGenerator, StatementGenerator, ProcedureGenerator).
- Emit correct Jasmin for all test cases, including edge cases (nested blocks, recursion, call-by-name).

## Approach
1. **Local Variable Limit Calculation**
   - Track all declared variables, parameters, and temporaries in symbol table and local index map.
   - For each method, count highest slot index used and add 1 (JVM convention).
   - Include procedure variables, loop counters, and any generated temporaries.
   - For thunks and procedure references, ensure all captured variables are included.

2. **Stack Limit Calculation**
   - Traverse codegen for each method, simulating stack effect of each instruction:
     - Push/pop for arithmetic, assignments, procedure calls, array access, etc.
     - Track maximum stack depth reached during codegen.
   - Use a stack simulation pass (pre-emit or post-emit) to determine required limit.
   - For modular codegen, allow each generator to report its max stack usage.

3. **Integration**
   - Refactor `CodeGenerator` and delegates to call limit calculation before emitting method header.
   - Replace `.limit stack N` and `.limit locals N` with computed values.
   - Add regression tests for programs that previously failed with `VerifyError`.

## Implementation Plan
- Phase 1: Add stack/local tracking to ExpressionGenerator and StatementGenerator.
- Phase 2: Refactor ProcedureGenerator and thunk codegen to use calculated limits.
- Phase 3: Integrate limit calculation into `CodeGenerator` main/procedure emit.
- Phase 4: Add tests for edge cases (nested loops, recursion, call-by-name, procedure variables).
- Phase 5: Remove all hard-coded limits and document new architecture in `docs/Architecture.md`.

## Risks & Mitigations
- Risk: Stack simulation may miss rare JVM edge cases (e.g., try/catch, unusual control flow).
  - Mitigation: Use Jasmin's own stack analysis as fallback, add verbose debug output.
- Risk: Refactoring may break existing codegen.
  - Mitigation: Use full test suite and milestone-driven regression checks.

## References
- JVM Spec: https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html#jvms-2.6.2
- Jasmin docs: https://jasmin.sourceforge.net/
- JAlgol `docs/Architecture.md` for modular codegen design

---
_Last updated: March 20, 2026_