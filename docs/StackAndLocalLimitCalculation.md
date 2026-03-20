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

**Assembler Strategy**
- Jasmin will remain the primary assembler for JAlgol output.
- All stack and local limit calculation will be implemented in the JAlgol compiler, not delegated to Jasmin.
- If stack/local calculation proves intractable for advanced Algol features (deep call-by-name, thunks, etc.), migration to Krakatau or ASM (which can compute limits automatically) will be considered as Plan B.

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


## Phased Implementation Plan (Pragmatic Roadmap)

### Phase 1: Immediate — Hard-code Large Safe Limits
- Emit `.limit stack 64` and `.limit locals 64` (or higher) for all methods (main, procedures, thunks).
- This eliminates all `VerifyError` and unblocks language feature development.
- Document this as the default for all new codegen until further notice.

### Phase 2: Short-term — Locals Calculation
- Track local variable usage and slot allocation in the symbol table/local index map.
- At the end of each method, set `.limit locals` to the highest slot used plus one (accounting for double/long = 2 slots).
- Keep `.limit stack` hard-coded large for now.

### Phase 3: Medium-term — Stack Simulation
- Implement a simple stack-depth simulator in codegen (ExpressionGenerator, StatementGenerator, ProcedureGenerator).
- Traverse emitted instructions, simulating stack effect (push/pop) to find the maximum depth.
- Use this to set `.limit stack` more accurately.

### Phase 4: Long-term — ASM Post-processing (Optional, Production-Grade)
- After generating Jasmin and assembling to `.class`, run a post-pass using ASM:
  - Use `ClassWriter.COMPUTE_MAXS` to recompute stack and local limits automatically.
  - This guarantees perfect limits even for complex call-by-name/thunk code.
- Consider this for production builds or if stack simulation becomes too complex.

### Ongoing
- Add regression tests for edge cases (nested loops, recursion, call-by-name, procedure variables).
- Update `docs/Architecture.md` as the implementation evolves.

## Risks & Mitigations
- Risk: Stack simulation may miss rare JVM edge cases (e.g., try/catch, unusual control flow).
  - Mitigation: Use large hard-coded limits as a fallback. If stack simulation is insufficient, use ASM post-processing or consider migration to Krakatau. Add verbose debug output for Jasmin assembly failures.
- Risk: Refactoring may break existing codegen.
  - Mitigation: Use full test suite and milestone-driven regression checks.

## References
- JVM Spec: https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html#jvms-2.6.2
- Jasmin docs: https://jasmin.sourceforge.net/
- ASM: https://asm.ow2.io/ (Java bytecode library with automatic limit computation)
- Krakatau: https://github.com/Storyyeller/Krakatau (Plan B JVM assembler with automatic stack/local calculation)
- JAlgol `docs/Architecture.md` for modular codegen design

---
_Last updated: March 20, 2026_