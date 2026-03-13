## Plan: Fix ManBoy to Correct -67.0 Output

Use a scope-driven codegen fix, not a ManBoy-only exception. The primary issue to address is non-local variable semantics in nested procedure recursion, then verify dispatch/coercion paths and regressions.

**Steps**
1. Phase A - Baseline lock  
Capture current ManBoy failure contract from AntlrAlgolListenerTest.java and generated Jasmin behavior from manboy.alg.  
Outcome: pre-fix reference for value mismatch and call path shape.

2. Phase B - Non-local load fix (blocks later phases)  
In CodeGenerator.java, update generateLoadVar logic to distinguish:
- current-scope locals => local load opcodes
- outer-scope names => env/static-field access  
Outcome: nested procedure reads no longer fall through to wrong local slots.

3. Phase C - Non-local store fix (depends on Phase B)  
In CodeGenerator.java, update exitAssignment storage path so outer-scope targets use env/static-field writes instead of local store opcodes.  
Outcome: nested procedure writes (including k mutation in ManBoy recursion) affect the correct scope.

4. Phase D - Remove/generalize ManBoy-specific hack (depends on B/C)  
Per your decision, remove hardcoded ManBoy/A/k special-casing in CodeGenerator.java, replacing it with consistent scope semantics.  
Outcome: behavior is correctness-driven and reusable, not class-name-driven.

5. Phase E - Safety checks for related semantics (parallel after compile passes)  
Validate in CodeGenerator.java:
- procedure dispatch remains correct (declared procedures vs procedure values)
- arithmetic coercion remains correct for thunk-unboxed integer operands in real expressions  
Outcome: prevent regressions while fixing ManBoy.

6. Phase F - Regression validation (depends on B-E)  
Run targeted tests first, then full suite via AntlrAlgolListenerTest.java.  
Outcome: ManBoy green and no collateral breakage.

7. Phase G - Status sync (depends on F)  
If all tests pass, update milestone tracking in Compiler-TODO.md.  
Outcome: project docs reflect new ground truth.

**Relevant files**
- CodeGenerator.java - main fix surface
- AntlrAlgolListenerTest.java - failing contract and regression gates
- manboy.alg - semantic reference
- Compiler-TODO.md - milestone/status update

**Verification**
1. Run only ManBoy test and confirm exact expected value.
2. Run focused call-by-name and procedure-variable regressions.
3. Inspect generated ManBoy Jasmin for correct outer-scope access and invocation/coercion opcodes.
4. Run full test suite and require zero regressions.

**Decisions**
- Included: remove/generalize the ManBoy-specific k hack in the same pass.
- Included: keep fix scope in codegen semantics.
- Excluded: broad compiler architecture refactor unless new blocker appears.

If you approve this plan, I can hand off an implementation-ready checklist in exact execution order for the coding pass.