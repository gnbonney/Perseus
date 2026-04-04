# Perseus Language Design Specification: Actors in a Class-Capable Perseus

Version 1.4

- Draft date: March 26, 2026
- Author: Greg Bonney
- Target: Perseus compiler (Algol 60 base + JVM backend via Project Loom virtual threads)

### 1. Motivation and Design Goals
Perseus is an Algol-60 descendant targeting the modern JVM. This document explores **actors** as a possible future primary mechanism for pure, message-based object-oriented programming in the spirit of Alan Kay. It assumes Perseus already has its class model for JVM interoperability, libraries, and conventional object-oriented structure.

This design is being kept intentionally separate from the active language roadmap. Not because the actor idea is bad, but because it is substantial enough to deserve a deliberate entry instead of getting bolted on impulsively.

**Core Philosophy**
- **Actors** would be the recommended, distinctive feature for new concurrent Perseus code: pure messaging, encapsulated state-process, extreme late binding, and true concurrency with no shared mutable state.
- **Classes** remain the familiar bridge for Java interop: synchronous calls, fields, and direct mapping to JVM classes/objects are assumed to come from the main Perseus class model rather than this proposal.
- The two models should coexist peacefully. Actors remain isolated; classes can be used for data structures, utility objects, or when calling Java libraries. An actor may hold references to class instances (and vice versa), but actors never share mutable state with each other or with classes in a way that breaks isolation.

This hybrid approach could keep Perseus distinctive (actor-centric concurrency) while remaining practical for real-world JVM development.

**Key Guarantees (Actors)**
- Asynchronous send (fire-and-forget).
- No shared memory between actors.
- Each actor processes exactly one message at a time.
- Dynamic behavior change via `become`.
- Full JVM concurrency without user-level locks.

**Classes** are assumed to continue following the more traditional Perseus model already being developed elsewhere in the roadmap.

### 2. Syntax

#### Actors
Actors are declared like Algol blocks but with the `actor` keyword. Message handling uses `on message` clauses inside the actor body.
**Comments use `%` (as per Perseus convention).**

```algol
actor Name;
begin
  % Local state (private to this actor)
  integer count;
  real threshold;

  % Message handlers (runtime dispatch)
  on message 'put' with value do
    if count < threshold then count := count + 1;

  on message 'get' do
    if count > 0 then count := count - 1;

  % Optional: dynamic behavior change
  on message 'upgrade' do
    become UpgradedVersion;

  % Optional: initialization (runs once on creation)
  count := 0;
  threshold := 100.0;
end;
```

**Reference Type** (for actor addresses):
```algol
ref(ActorName) myActor;   % analogous to Simula ref, but holds an ActorRef
```

**Sending Messages** (asynchronous):
```algol
send myActor the message 'put' with 42;
send myActor the message 'get';                  % no payload
send myActor the message 'status' replyto replyActor;  % optional reply pattern
```

**Spawning an Actor**:
```algol
ref(Buffer) b;
b :- new Buffer;   % creates and starts the actor on a virtual thread
```

**Become** (changes behavior for future messages):
```algol
become NewBehavior;   % NewBehavior must be another actor declaration or a runtime behavior reference
```

**Optional: Pattern Matching on Messages** (syntactic sugar, compiler expands to if/else):
```algol
on message 'transfer' with amount to dest do
  ...
```

#### Classes
Classes are shown here only as context for how actors might coexist with the existing Perseus class model and Java interop story.

```algol
class Point;
begin
  real x, y;

  procedure distance(other); ref(Point) other;
  begin
    distance := sqrt((x - other.x)**2 + (y - other.y)**2);
  end;

  % Constructor-like initialization
  x := 0.0; y := 0.0;
end;

class ColoredPoint;
begin
  ref(Point) prefix;   % or use inheritance syntax if we add "prefix" or "extends"
  string color;

  % Methods can call Java library methods directly
  procedure draw;
    JavaGraphics.drawPoint(x, y, color);
end;
```

**Basic Inheritance** (single, to keep it simple and Algol-friendly):
```algol
class Vehicle;
begin
  procedure move; ...
end;

Vehicle class Car;   % prefix-style for familiarity with Simula users
begin
  procedure move; ... % can override
end;
```

**Using Classes from Actors** (and vice versa):
```algol
actor Renderer;
begin
  ref(Point) p;

  on message 'render' do
    p :- new Point;
    p.distance(someOtherPoint);   % synchronous call on class instance
end;
```

**Interop with Java**:
- Perseus classes compile directly to JVM classes (with public methods/fields as needed).
- You can declare `ref(JavaClassName)` or import Java types.
- Call Java static methods, instantiate Java objects, and pass Perseus class instances to Java libraries seamlessly.

### 3. Semantics (Key Distinctions)

**Actors**
- Pure message passing (asynchronous).
- Private state, serialized processing, `become` for dynamic behavior.
- No inheritance between actors.
- Ideal for concurrent, distributed, or reactive logic.

**Classes**
- Synchronous method calls (like Java/C++).
- Support for fields, procedures/methods, and limited inheritance.
- Shared mutable state is allowed *within* the class model (but discouraged when mixing with actors).
- Direct JVM mapping for easy interop (e.g., implementing `java.lang.Runnable`, extending `javax.swing.JPanel`, using collections, etc.).

**Mixing the Two**
- An actor can create and use class instances (e.g., for data holders or calling Java APIs).
- A class can hold `ref(ActorName)` and send messages to actors.
- **Rule**: Never pass mutable class instances between actors in a way that allows direct shared mutation. Use immutable data or message payloads for communication.
- The compiler can provide warnings or optional strict mode to prevent accidental sharing.

**Runtime**
- **Actors**: Lightweight virtual threads + mailbox + `Behavior` interface.
- **Classes**: Standard JVM class generation (no virtual threads needed unless explicitly requested).
- Both benefit from the JVM ecosystem. Actors use `Executors.newVirtualThreadPerTaskExecutor()` (or configurable supervisor).

### 4. Recommendations for Perseus Users
- Use **actors** for concurrency, services, stateful components, simulations, or any system needing isolation and scalability.
- Use **classes** for:
  - Data structures and utilities.
  - Wrapping or extending Java libraries.
  - Performance-critical synchronous code.
  - GUI or framework integration.
- Hybrid pattern: Actor systems orchestrate high-level behavior; classes handle fine-grained data and library calls.

### 5. Examples

**Classic Buffer (Producer-Consumer)**:
```algol
actor Buffer;
begin
  integer capacity, count;

  on message 'put' with value do
    if count < capacity then count := count + 1;

  on message 'get' do
    if count > 0 then count := count - 1;

  capacity := 10; count := 0;
end;

ref(Buffer) b :- new Buffer;
send b the message 'put' with 5;
```

**Ping-Pong Actors** (showing replies and become):
```algol
actor Pinger;
begin
  on message 'ping' replyto replyer do
    send replyer the message 'pong';
    become Ponger;   % switch role
end;

% Similar for Ponger...
```

**Hybrid Example (Actor using Class + Java interop)**:
```algol
class Point;
begin
  real x, y;
  procedure setX(newX); real newX; x := newX;
end;

actor GraphicsManager;
begin
  ref(Point) currentPoint;

  on message 'move' with dx do
  begin
    currentPoint :- new Point;
    currentPoint.setX(dx);
    JavaGraphics.render(currentPoint);   % direct Java library call
  end;
end;
```

### 6. Error Handling and Guarantees
- Unhandled messages are silently dropped (standard actor model).
- Exceptions inside a handler cause the actor to die and optionally notify a supervisor.
- Compiler static checks (optional): warn on sending unknown message tags to a `ref` type.
- No shared mutable state across actors (enforced by language rules).

### 7. Future Extensions (Out of Scope for v1.0)
- Distributed actors (networked mailboxes).
- Typed message protocols (interfaces for message tags).
- Built-in supervision hierarchies (Erlang-style).
- Hot code swapping via `become` with live updates.

This specification provides a foundation for adding actors to a Perseus that already has a class/interoperability story on the main roadmap. It aims to balance the purity of Alan Kay-style OOP (via actors) with practical JVM interop (via the existing class model), while staying faithful to Algol 60's block-structured roots.
