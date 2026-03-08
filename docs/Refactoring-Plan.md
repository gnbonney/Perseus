# CodeGenerator Refactoring Plan

The goal of this refactor is to break down the monolithic `CodeGenerator.java` into smaller, specialized generator classes using a delegation pattern. This will improve maintainability and readability without breaking the existing Algol 60 compiler logic.

## Current Status (Pre-Refactor)
- **Commit**: `d77fb27a81acde612a8022faec144eccc33822c1`
- **Tests**: 24/28 passing.
- **Failures**: 
    - `proc_var_test`
    - `proc_param_test`
    - `proc_typed_simple_test`
    - `manboy_test`

## Refactoring Strategy

### 1. Delegation Pattern
Instead of moving all logic at once, `CodeGenerator` will remain the primary `AlgolBaseListener`. It will hold instances of specialized generators and delegate node-specific logic to them.

```java
public class CodeGenerator extends AlgolBaseListener {
    private ExpressionGenerator expressionGenerator;
    private StatementGenerator statementGenerator;
    private ProcedureGenerator procedureGenerator;
    
    // ...
    
    @Override
    public void exitExpression(AlgolParser.ExpressionContext ctx) {
        expressionGenerator.handle(ctx);
    }
}
```

### 2. Migration Phases

The following components from `CodeGenerator.java` will be moved to specialized classes.

#### Phase 1: Expression Logic (`ExpressionGenerator.java`)
**Target**: Logic for evaluating all types of Algol expressions.
- `generateExpr(ExprContext ctx)` - [Line 354](src/main/java/gnb/jalgol/compiler/CodeGenerator.java#L354)
- `generateExpr(ExprContext ctx, Map<String,Integer> varToFieldIndex)` - [Line 358](src/main/java/gnb/jalgol/compiler/CodeGenerator.java#L358)
- `generateBuiltinMathFunction(String funcName, ProcCallExprContext ctx)` - [Line 517](src/main/java/gnb/jalgol/compiler/CodeGenerator.java#L517)
- Utility: `lookupArrayBounds(String name)` - [Line 342](src/main/java/gnb/jalgol/compiler/CodeGenerator.java#L342)

#### Phase 2: Statement Logic (`StatementGenerator.java`)
**Target**: Control flow and assignment statement handling.
- `enterIfStatement(AlgolParser.IfStatementContext ctx)` - [Line 47](src/main/java/gnb/jalgol/compiler/CodeGenerator.java#L47)
- `exitIfStatement(AlgolParser.IfStatementContext ctx)` - [Line 85](src/main/java/gnb/jalgol/compiler/CodeGenerator.java#L85)
- `enterForStatement(AlgolParser.ForStatementContext ctx)` - [Line 96](src/main/java/gnb/jalgol/compiler/CodeGenerator.java#L96)
- `exitForStatement(AlgolParser.ForStatementContext ctx)` - [Line 161](src/main/java/gnb/jalgol/compiler/CodeGenerator.java#L161)
- *Note: `exitAssignmentStatement` (not currently in file) will be restored to this class.*

#### Phase 3: Procedure & Thunk Logic (`ProcedureGenerator.java`)
**Target**: Procedure declarations and complex call-by-name infrastructure.
- `createThunkClass(...)` - [Line 249](src/main/java/gnb/jalgol/compiler/CodeGenerator.java#L249)
- `generateProcedureReference(...)` - [Line 603](src/main/java/gnb/jalgol/compiler/CodeGenerator.java#L603)
- `generateProcedureVariableCall(...)` - [Line 707](src/main/java/gnb/jalgol/compiler/CodeGenerator.java#L707)
- `collectVarNames(ExprContext ctx)` - [Line 216](src/main/java/gnb/jalgol/compiler/CodeGenerator.java#L216)
- *Note: This class will work alongside the existing `ProcedureInvocationGenerator.java`.*

#### Phase 4: State & Infrastructure (`ContextManager.java` & Utilities)
**Target**: Local variable management and Jasmin limit tracking.
- `allocateNewLocal(String hint)` - [Line 207](src/main/java/gnb/jalgol/compiler/CodeGenerator.java#L207)
- `ensureLocalLimit(int required)` - [Line 223](src/main/java/gnb/jalgol/compiler/CodeGenerator.java#L223)

## Rules for Refactoring
1. **Incremental Commits**: Commit after every successful listener migration and test run.
2. **Backups**: Maintain a physical backup of `CodeGenerator.java` (e.g., `CodeGenerator.java.bak`) throughout the refactor process.
3. **State Management**: Identify whether state (like labels, scope, and method environments) should be passed by reference or if `CodeGenerator` should expose them via protected/package-private methods.
4. **No Logic Changes**: During the structural refactor, avoid fixing the failing tests. We need a stable baseline first.

## TODO List

### Infrastructure
- [ ] Create a physical backup of `CodeGenerator.java` as `CodeGenerator.java.bak`
- [ ] Define communication interface between `CodeGenerator` and delegates
- [ ] Create `ContextManager` to handle shared state (Symbol Tables, Loop Labels)

### Phase 1: Expression Logic
- [ ] Create `ExpressionGenerator.java`
- [ ] Migrate `generateExpr` method
- [ ] Migrate `generateBuiltinMathFunction` method
- [ ] Migrate `lookupArrayBounds` utility
- [ ] Update `CodeGenerator` to use `ExpressionGenerator`
- [ ] Verify 24/28 tests passing

### Phase 2: Statement Logic
- [ ] Create `StatementGenerator.java`
- [ ] Migrate `enterIfStatement` / `exitIfStatement`
- [ ] Migrate `enterForStatement` / `exitForStatement`
- [ ] Restore/Implement `exitAssignmentStatement`
- [ ] Verify 24/28 tests passing

### Phase 3: Procedure & Thunk Logic
- [ ] Create `ProcedureGenerator.java`
- [ ] Migrate `createThunkClass` and thunk state
- [ ] Migrate `generateProcedureReference`
- [ ] Migrate `generateProcedureVariableCall`
- [ ] Fix the 4 failing procedure tests (`proc_var`, `proc_param`, `proc_typed`, `manboy`)
- [ ] Verify 28/28 tests passing

### Phase 4: Cleanup
- [ ] Migrate `allocateNewLocal` and `ensureLocalLimit` to `ContextManager`
- [ ] Remove dead code from `CodeGenerator.java`
- [ ] Final full build and test run
