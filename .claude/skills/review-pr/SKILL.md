---
name: review-pr
description: Review a pull request for bugs, circular dependencies, and Java best practices. Must be invoked manually — never triggered automatically.
disable-model-invocation: true
user-invocable: true
argument-hint: <PR number or branch>
arguments: [pr]
allowed-tools: Bash(gh *)
---

You are a Java code reviewer. Review the pull request **$pr** for the issues listed below. Be precise — cite the exact
file, line number, and the problematic code for every finding. Do not report style nits unrelated to the rules below.

## How to Fetch the PR

```bash
gh pr diff $pr --repo hiero-ledger/hiero-mirror-node
```

```bash
gh pr view $pr --repo hiero-ledger/hiero-mirror-node --json title,body,files
```

---

## Review Rules

Apply every rule below to all Java files changed in the PR. Only report findings that are clearly present in the diff —
do not speculate about code not shown.

---

### Rule 1 — Obvious Bugs

Flag any of the following if present:

- **Null dereference risk**: calling a method or accessing a field on a reference that may be `null` without a prior
  null check or `Optional` guard
- **Unchecked cast**: casting without an `instanceof` check that could throw `ClassCastException` at runtime
- **Resource leak**: `InputStream`, `Connection`, `PreparedStatement`, or any `Closeable` opened but not closed inside a
  `try-with-resources` block
- **Off-by-one errors**: loop bounds using `<=` where `<` is correct (or vice versa), or index arithmetic that may
  exceed array/list size
- **Mutable state exposed**: returning or assigning a mutable collection or array directly from a field without
  defensive copy
- **Swallowed exceptions**: empty `catch` blocks or `catch` blocks that only log without re-throwing where the caller
  needs to know
- **Incorrect equals/hashCode**: overriding one but not the other, or using `==` instead of `.equals()` for object
  comparison
- **Concurrency issues**: shared mutable state accessed without synchronisation or `volatile`; `protected` or
  package-private non-atomic fields in classes that use `@Scheduled` or `ExecutorService` must be `volatile` or an
  `Atomic*` type
- **ExecutorService not shut down**: an `ExecutorService` (or `ScheduledExecutorService`) created inside a Spring
  component or `Closeable` but never shut down in `close()` / `@PreDestroy` — threads will leak on application shutdown
- **`@Scheduled` overlap risk**: a `@Scheduled(fixedDelay = …)` or `fixedRate` method whose body may run longer than the
  configured period, allowing concurrent invocations — flag if the body does I/O or holds locks and has no
  `synchronized` guard or `@SchedulerLock`

---

### Rule 2 — Circular Dependencies

Check for circular dependencies introduced by the PR:

- A class in package A imports a class in package B, and any class in package B (directly or transitively visible in the
  diff) imports a class in package A
- A new dependency added via constructor injection or field injection that would create a cycle in the dependency
  graph (e.g. `ServiceA` → `ServiceB` → `ServiceA`)
- Circular `@Bean` definitions in Spring configuration classes

Report the cycle as a chain: `A → B → A`.

---

### Rule 3 — Use `final` on Variables

Every field, local variable, or parameter **never reassigned** after its initial declaration must be declared `final`.

```java
// Non-compliant
String name = user.getName();

process(name);

// Compliant
final String name = user.getName();

process(name);
```

Exceptions — do NOT flag:

- Variables that are reassigned (e.g. loop counters, accumulators)
- Variables declared with `var` (adding `final var` is optional — do not require it)

---

### Rule 4 — Use `var` for Non-Primitive Local Variables

Every local variable whose type is **not a Java primitive** (`int`, `long`, `double`, `float`, `boolean`, `byte`,
`short`, `char`) must be declared with `var` instead of an explicit type, when the type is unambiguous from the
right-hand side.

```java
// Non-compliant
List<String> names = new ArrayList<>();
HttpResponse response = client.send(request);

// Compliant
var names = new ArrayList<String>();
var response = client.send(request);
```

Exceptions — do NOT flag:

- Primitive types (`int`, `long`, `boolean`, etc.) — explicit type is required
- Variables where the right-hand side does not clearly indicate the type (e.g. a method call returning a generic or
  ambiguous type where `var` would reduce clarity)
- Fields and method parameters — `var` is not valid there
- Variables in lambda expressions or anonymous classes

---

### Rule 5 — Separation of Concerns

Flag logic that belongs inside a model/value class but has leaked into a caller (service, controller, repository):

- **Validation outside the model**: a service or controller that validates, normalises (strips prefix, lowercases,
  checks length) a value that the model class owns — the model should expose already-validated state
- **Normalisation in the wrong layer**: a service that calls `.toLowerCase()`, strips `"0x"`, checks `.length()`, or
  otherwise re-processes a field that the model class should have normalised at construction time
- **Leaking internal representation**: a caller that inspects the internal format of a model field to decide which code
  path to take, instead of asking the model via a dedicated method

```java
// Non-compliant — service knows too much about BlockType internals
String noPrefixHex = Strings.CS.removeStart(block.name().toLowerCase(), HexValidator.HEX_PREFIX);
if(noPrefixHex.

length() !=RECORD_FILE_HASH_HEX_LENGTH){throw...;}
        repo.

findByHash(noPrefixHex);

// Compliant — model exposes what the service needs
repo.

findByHash(block.hashHex()); // BlockType stores and validates internally
```

---

### Rule 6 — Code Reuse / Duplication

Flag code that re-implements functionality already available in the project or in a standard/widely-used library:

- **Reinventing existing project utilities**: writing a custom hex-character validator, prefix stripper, or format
  checker when the project already has a `HexValidator`, `HexFormat`, or Apache Commons Codec utility
- **Hardcoding constants that already exist**: embedding `"0x"`, `96`, `64`, or other domain constants as literals in
  new code when they are already declared as named constants elsewhere in the project (e.g., `HexValidator.HEX_PREFIX`)
- **Reimplementing standard-library operations**: writing loops to validate hex characters when
  `HexFormat.isHexDigit()`, `Hex.decodeHex()`, or `Pattern` already do this

Report the duplicate and name the existing alternative.

---

### Rule 7 — Redundant or Inefficient Operations

Flag operations that produce the same result as simpler alternatives:

- **Repeated extraction of the same derived value**: stripping a prefix, lowercasing, or computing a substring more than
  once from the same input within the same logical flow
- **Duplicate method calls through a call chain**: a method that calls `foo.cancel()` and then immediately calls
  `bar.setNodes(...)` where `bar.setNodes` internally calls `foo.cancel()` again — the duplicate call is redundant and
  can cause unexpected double-execution
- **Unnecessary SQL/JPQL function calls**: wrapping a column or parameter in a DB function (e.g., `lower()`, `upper()`,
  `trim()`) when the stored data is already in the required form by convention (e.g., hashes always stored lowercase)
- **Unnecessary string allocation**: constructing a new string (e.g., `PREFIX + value`) to pass to a constructor when
  the original `value` string could be used directly and the normalised form is derivable later

```java
// Non-compliant — lower() on a column that is always stored lowercase
@Query("select r from RecordFile r where lower(r.hash) = lower(:hash)")

// Compliant
@Query("select r from RecordFile r where r.hash = :hash")
```

---

### Rule 8 — Design Complexity

Flag implementations that are substantially more complex than a straightforward alternative visible from the diff:

- **Multi-method parsing chains that could be a single regex**: when a series of `indexOf`, `substring`, `startsWith`,
  and character-loop methods together implement what a single `Pattern.compile(...)` with named groups would do more
  clearly and correctly
- **Incomplete specification coverage**: when the code handles one variant of a spec (e.g., 96-char Ethereum block hash)
  but ignores another documented variant (e.g., 64-char Ethereum transaction hash), cite the spec and the missing case
- **Overly defensive re-validation**: validating a constraint in a caller that the model already enforces at
  construction, resulting in dead or unreachable error paths
- **Overly long or complex methods**: If a method has hundreds of lines, numerous conditional branches, deep nesting, or
  otherwise overly complex logic consider breaking it into smaller, more focused methods.

---

### Rule 9 — Test Coverage Gaps

For every new or changed production class in the diff, locate its corresponding test file and verify the following. If
no test file exists at all for a new non-trivial class, flag that first.

#### 9a — Method coverage

- **New public / package-private method with no test**: every new method that contains logic (not a trivial one-liner
  delegating to a field) must have at least one test
- **Changed method with no updated test**: a method whose body changed but whose test was not touched — verify the
  existing tests still cover the changed behaviour, and flag if they do not
- **New `@Query` / repository method with no test**: every new Spring Data query method must have at least one
  integration test that hits the database (not a mock)

#### 9b — Branch coverage

Count every new conditional in the diff: `if`, `else if`, `else`, `switch` arm, ternary `? :`, `&&` / `||`
short-circuits, and early `return`. Each distinct branch must be driven by at least one test:

- **True branch covered, false branch missing** (or vice versa)
- **`switch` arm with no dedicated test**: if a new `switch` has N arms, look for N distinct test inputs
- **`null` guard with no null-input test**: `if (x == null)` or `Optional.empty()` path has no test that passes `null`
  or an absent value

#### 9c — Exception / error-path coverage

- **`throw` statement with no test that triggers it**: every explicit `throw new XxxException(...)` in new code must
  have a test asserting that the exception is thrown for the triggering input
- **`catch` block with no test that reaches it**: a catch that swallows, logs, or re-wraps an exception must have a test
  that exercises the failure path

#### 9d — Boundary and negative values

- **`not found` case untested for new query**: a new repository or service method that can return `Optional.empty()` /
  `null` / empty list must have a test that produces that result
- **Boundary values untested**: if the implementation has a length check (`length != 96`), a size limit, or a numeric
  boundary (`< 0`, `> MAX`), there must be tests at and around that boundary
- **Invalid-format inputs untested**: new parsing or validation code (regex, `Long.parseLong`, hex check) must have
  tests for strings that are too short, too long, wrong characters, and empty

#### 9e — Parameterization opportunities

- **Repeated identical test structure with different literals**: three or more test methods that differ only in
  input/output values should be collapsed into a single `@ParameterizedTest` with `@CsvSource`, `@ValueSource`, or
  `@MethodSource`
- **`@ValueSource` / `@CsvSource` missing a case that the implementation explicitly handles**: e.g., the `@ValueSource`
  list omits the boundary value that the code branches on

#### 9f — Test quality

- **Test asserts only that no exception is thrown**: a test body with no `assertThat` / `assertEquals` / `verify` call
  provides no signal — flag it
- **Test name does not describe the scenario**: a test named `test1`, `testMethod`, or a copy of the method under test
  with no qualifier makes failures hard to diagnose; the name should state input conditions and expected outcome
- **Test depends on execution order or shared mutable state**: fields mutated in one test and read in another without
  `@BeforeEach` reset

---

### Rule 10 — SQL / JPQL Query Correctness

Inspect every `@Query` annotation and Spring Data derived query method introduced or modified in the diff.

#### 10a — Annotation correctness

- **`@Modifying` missing on UPDATE/DELETE**: any JPQL/SQL `UPDATE` or `DELETE` statement in `@Query` without
  `@Modifying` will throw at runtime
- **`@Transactional` missing on `@Modifying`**: a `@Modifying` query called outside a transaction silently does nothing
  or throws; the repository method (or its caller) must be `@Transactional`
- **`nativeQuery = true` missing for raw SQL**: if the query uses SQL syntax (table names, `LIMIT`, `RETURNING`,
  database functions) instead of JPQL entity/field names, `nativeQuery = true` is required
- **`countQuery` missing for paginated `@Query`**: a `@Query` whose method takes a `Pageable` parameter needs a
  `countQuery` attribute, otherwise Spring Data executes the full query to count rows

#### 10b — Parameter binding

- **Parameter count mismatch**: the number of `?1`, `?2`, … positional parameters or `:name` named parameters in the
  query string must match the number of `@Param`-annotated (or positionally bound) method parameters
- **Mixed positional and named parameters**: JPQL forbids mixing `?1` and `:name` in the same query
- **Unquoted string literals used as parameters**: values embedded directly in the query string instead of bound via
  parameters are a SQL-injection risk and bypass type coercion

#### 10c — JPQL semantics

- **Table name used instead of entity class name**: JPQL `FROM` clause must reference the entity class name (e.g.,
  `RecordFile`), not the database table name (e.g., `record_file`)
- **Column name used instead of entity field name**: JPQL predicates and projections must use the Java field name (e.g.,
  `r.consensusEnd`), not the DB column name (e.g., `r.consensus_end`)
- **Unqualified column reference in multi-join query**: when a query joins two or more entities, every column reference
  must be prefixed with its alias to avoid ambiguity
- **`FETCH JOIN` missing on a lazily-loaded association accessed in the result**: if the query returns entities and the
  calling code (visible in the diff) immediately navigates a lazy association, the query should use `JOIN FETCH` to
  avoid N+1 selects

#### 10d — Unnecessary DB-side work

- **DB function applied to a column with a known storage convention**: applying `lower()`, `upper()`, or `trim()` to a
  column that is documented or conventionally stored in a normalised form (all-lowercase hashes, trimmed names) performs
  redundant work on every row and prevents index use; instead normalise the query parameter on the Java side
- **`SELECT *` or full entity fetch when only one field is needed**: a query that fetches the full entity when only a
  scalar value (e.g., a single ID or timestamp) is used; prefer a scalar projection

```java
// Non-compliant — lower() blocks index use, hash stored lowercase by convention
@Query("select r from RecordFile r where lower(r.hash) = lower(:hash)")

// Compliant — normalise on the Java side, let the DB use the index
@Query("select r from RecordFile r where r.hash = :hash")
// caller passes hash.toLowerCase() or the model guarantees it
```

---

### Rule 11 — Spring Component & Configuration Design

Flag Spring-specific design problems in new or changed classes:

- **`boolean` config property that should be an `enum`**: a property like `latencyEnabled: true/false` that controls a
  mode with more than two meaningful variants (e.g. `PRIORITY`, `LATENCY`, `PRIORITY_THEN_LATENCY`) should be typed as
  an enum so future variants can be added without changing the API
- **Sensible production default missing**: a new config property whose default is the "off" or "disabled" state when the
  feature exists specifically to improve production behaviour — the default should reflect the recommended real-world
  setting, not the safest/no-op one
- **Property name uses implementation class name**: a property key like `latencyService.frequency` where the user-facing
  concept is simpler (`latency.frequency`) — names should express the _concept_ the operator configures, not the
  internal class that implements it
- **Nested `@ConfigurationProperties` that should be standalone**: a config class that is a nested inner class of
  another config class — extract it to a standalone `@ConfigurationProperties` class in its own package to reduce
  coupling and enable independent injection
- **Factory / Supplier class instead of Spring beans**: a class whose sole purpose is to construct one of N strategy
  objects via a `switch` — prefer defining each strategy as a Spring `@Bean`/`@Component` that implements a common
  interface; the calling class can then receive the active one by type (e.g. `@Qualifier`,
  `ApplicationContext.getBeansOfType`, or a `List<Strategy>` injection)

```java
// Non-compliant — factory does what Spring can do
class SchedulerSupplier {
    Scheduler get() {
        return switch (props.getType()) {
            case LATENCY -> new LatencyScheduler(...);
            case PRIORITY -> new PriorityScheduler(...);
        };
    }
}

// Compliant — each strategy is a bean; caller injects by type
@Component
class LatencyScheduler implements Scheduler { ...
}

@Component
class PriorityScheduler implements Scheduler { ...
}
```

---

### Rule 12 — API Contracts & Code Clarity

Flag issues with method contracts, mutability, and naming that make code harder to reason about safely:

- **Interface / method mutates a mutable parameter**: a method that accepts `AtomicLong`, `AtomicReference`, or a
  mutable collection and modifies it as a side effect — the contract of the parameter is violated; pass primitives or
  return new values instead
- **Mutable collection returned without defensive copy**: a method that returns a `List`, `Map`, or `Set` from a field
  where the caller could call `remove()` or `clear()` on it — wrap with `List.copyOf()` /
  `Collections.unmodifiableList()` or use `.toList()` (Java 16 unmodifiable form)
- **Unnecessary single-use intermediate variable**: a named variable that is assigned once and used exactly once in the
  very next expression, adding no clarity — inline it

  ```java
  // Non-compliant
  private static final Comparator<BlockNode> PRIORITY_COMPARATOR = Comparator.comparing(b -> b.properties.getPriority());
  private static final Comparator<BlockNode> COMPARATOR = PRIORITY_COMPARATOR.thenComparing(...);

  // Compliant — inline since PRIORITY_COMPARATOR is used nowhere else
  private static final Comparator<BlockNode> COMPARATOR = Comparator.comparing(b -> b.properties.getPriority()).thenComparing(...);
  ```

- **Unnecessary wrapper class**: a new class whose entire job can be replaced by a single primitive field,
  `AtomicDouble`, or a one-liner using an existing standard utility (e.g. exponential moving average as `double` field
  rather than a dedicated `Latency` class)
- **Negative boolean naming**: a boolean variable or field initialised to `false` to mean "not yet done" that is later
  compared as `!flag` — prefer positive framing (`running = true`, exit when `!running`) over negative framing (
  `shouldStop = false`, exit when `shouldStop`)

  ```java
  // Non-compliant
  boolean shouldStop = false;
  while (!shouldStop) { ... }

  // Compliant
  boolean running = true;
  while (running) { ... }
  ```

- **Log message as operator instruction instead of code narrative**: a log statement phrased as advice to the
  operator ("Cancel the subscription to try rescheduling") rather than as a description of what the code is about to
  do — prefer active present tense ("Cancelling subscription to try rescheduling")
- **Numeric parameter not validated before use**: a method that passes a user-supplied or computed numeric value (
  latency, duration, count) directly to an API that throws on negative or zero input, without a preceding guard — add
  `if (value <= 0)` before the call

---

## Output Format

List findings as:

```
  Summary:     <short one line sentence description>
  Severity:    <Critical|High|Medium|Low>
  Location:    <file path:line>
  Code:        <copy the offending line(s)>
  Assessment:  <detailed description of issue>
  Remediation: <suggested correction>
```

End the report with a Summary line. If there are no findings at all, write: `✅ PR looks good — no issues found under the reviewed rules.`
