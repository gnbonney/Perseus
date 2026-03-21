# Thunk/Closure Isolation Design (Call-by-Name + Procedure Variables)

## Goal
Make the compiler correctly implement *per-activation mutable captured state* for call-by-name parameters and procedure-valued variables, so that:

1. Each call-by-name argument creates a distinct thunk/closure whose captured variables are independent.
2. Nested procedures (and recursive calls) access the correct activation's captured state.
3. Procedure-valued variables can be assigned, stored, and invoked safely.

This is required to make `manboy.alg` produce the correct result (`-67.0`) and to support general Algol call-by-name semantics.

---

## Background / Current failure mode

### What already works
- The compiler can generate **thunk classes** (`ThunkN.j`) for call-by-name arguments.
- It can generate **nested-scope access** code using an "environment bridge" (static `__env_*` fields) that lets a nested procedure see an outer variable.

### What fails
- For call-by-name parameters and procedure variables, the compiler currently uses shared static `__env_*` fields (one per outer variable) to store mutable state.
- This makes different activations share the same storage, breaking per-activation isolation.
- A secondary failure is that procedure-valued variables are not always generated correctly (assignment/call paths sometimes treat them as scalars), producing:
  - `; ERROR: assigning integer to procedure variable ...`
  - `; unknown procedure: ...`
  - verifier errors like `Illegal local variable number`.

---

## Desired semantics (general)

1. **Call-by-name parameter => thunk object**
   - Each call produces a **new thunk object**.
   - That thunk object owns the captured variables (mutable boxes/fields).
   - `get()`/`set()` operate on those fields.

2. **Nested procedure sees the correct activation’s environment**
   - Nested procedures and thunks should not refer to a shared global store.
   - Instead, they should reference the *closure object* created for that activation.

3. **Procedure variable support**
   - A procedure variable is a first-class value (like a function pointer).
   - It must be stored and invoked via the correct procedure interface type (`VoidProcedure`, `RealProcedure`, etc.).

---

## Key components and where to change

### 1) `CodeGenerator` (main emitter)

**Why it’s involved:**
- Handles statement and assignment code emission.
- Determines how variables are stored/loaded.

**Relevant methods / sections:**
- `emitStore(...)` and the `generateStore*` logic (all assignments)
- `generateLoadVar(...)` in `ExpressionGenerator` (loads a variable)
- The code that generates `__env_*` static fields and reads/writes them.

**Change required:**
- When a variable is known to be `procedure:` (procedure-typed), *do not* treat it as scalar.
- Ensure procedure-typed locals/fields use the correct descriptor (`Lgnb/jalgol/compiler/VoidProcedure;` etc.) and are stored/loaded with `astore`/`aload`.
- Ensure calls through procedure variables use `invokeinterface` on the correct `*Procedure` interface.

### 2) `ProcedureGenerator` / `ExpressionGenerator`

**Why it’s involved:**
- Contains `generateProcedureReference(...)` and call generation logic.
- Generates thunk classes when passing call-by-name arguments.

**Relevant methods:**
- `generateProcedureReference(String procName, ProcInfo procInfo)`
- `generateProcedureCall(...)` (in `CodeGenerator` and/or `ProcedureGenerator`) - for actual call sites, including calls through variables.
- `createThunkClass(...)` (in `CodeGenerator`)

**Change required:**
- Ensure procedure-variable call sites (where the callee is a variable, not a named procedure) generate:
  1. `aload <proc-var>` (or static field)
  2. build arg array
  3. `invokeinterface <ProcedureInterface>/invoke([Ljava/lang/Object;)...`
- Make sure `createThunkClass(...)` **does not depend on global env fields** for its captured variables; it must store them in instance fields and use them in `get()`.

### 3) `ContextManager` / local-index tracking

**Why it’s involved:**
- It tracks local variable slots and ensures correct local indices.

**Relevant fields/methods:**
- `getLocalIndex()` / `setLocalIndex()`
- `setNextLocalIndex()` / `getNextLocalIndex()`

**Change required:**
- Ensure procedure-variable locals are assigned valid slot numbers and that `emitStore()` uses those slots correctly.
- Prevent negative/uninitialized slot usage (e.g., `istore -1`).

---

## Concrete design steps

### Step 1: Make procedure-variable assignment/call robust

1. **Identify where scalar vs procedure is branched**
   - In `CodeGenerator` `emitStore(...)` / `generateStoreVar` / `generateLoadVar`, locate the switch based on `type`.

### Step 4: Completed implementation notes (2026-03-20)

1. `CodeGenerator.generateExpr()` now handles `ProcCallExpr` where the call is through a procedure variable (`procedure:void` in thunk_closure_isolation).
2. For `procedure:void` expression calls, current procedure-variable binding is saved in a temp local, call is made, result binding is loaded, and old binding is restored.
3. `ProcedureGenerator.generateProcedureVariableCall()` now emits a void invocation without illegal `astore`/`pop`, and non-void paths are handled by caller contexts.
4. `StatementGenerator.exitProcedureCall()` no longer forcibly emits `astore 0` for procedure variables.

### Step 5: Validation results

- `thunk_closure_isolation_test` now passes: runtime output `1\n2`.
- Remaining known issues: `primer2` and `manboy_test` currently fail in full suite, unrelated to this specific thunk fix.

### Step 6: Path to ManBoy correctness

- The solution above addresses the key per-activation isolation and procedure-variable binding invariants required by ManBoy semantics.
- Next action for ManBoy: ensure call-by-name environment bridge and recursive procedure-variable self-thunk setup are also isolated (no shared static mutation between activations).

2. **Add a full `procedure:` branch** so that:
   - `procedure:...` variables use a procedure descriptor and are stored using `astore`/`putstatic`.
   - Calls through procedure variables use `invokeinterface` with the correct `*Procedure` interface.

3. **Fix local-index allocation for procedure variables**
   - Ensure `allocateNewLocal(...)` is used properly for procedure variables and that the slot map is updated.

### Step 2: Make thunk capture isolation work

1. **Stop writing captured vars to shared `__env_*` fields** when they are in a thunk.
2. Ensure the thunk constructor receives and stores **all needed captured values** in instance fields.
3. Ensure thunk `get()` and `set()` operate solely on those instance fields.

### Step 3: Add regression tests

- Keep `nested_scope_access.alg` for basic nested access.
- Add a new test that uses a procedure variable (or thunk) and verifies two distinct closure activations do not share state. (This is the one currently blocked by the procedure-variable generator bug.)

---

## Why this will unblock ManBoy
Once procedure variables are handled correctly and thunk capture is isolated, the Man‑Boy recursion will stop re‑using shared state and will obey the per‑activation mutable semantics, producing `-67.0`. The remaining work is to ensure the compiler reliably differentiates:

- scalar variables vs
- procedure-valued variables vs
- thunk-captured variables

and generates the correct loads/stores/invokes for each.
