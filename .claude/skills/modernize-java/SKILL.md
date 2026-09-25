---
name: modernize-java
description: Use when reviewing, refactoring, or writing Java code in hiero-mirror-node to apply Java idioms and project conventions. Prefers records over hand-written POJOs (but never converts Lombok @Value classes), text blocks, switch expressions, sealed types, JSpecify @NullMarked over Optional in code we own with non-null defaults where reasonable (primitives over boxed types when null isn't meaningful, `List.of()` / `Set.of()` / `Map.of()` over null collection returns, sensible default values for fields), immutability (final fields/parameters/variables and `final var` for non-primitive locals where the RHS type is clear), java.util.HexFormat and java.util.Base64 over hand-rolled or Apache Commons equivalents, and avoids streams in hot paths (importer per-record loops, web3 per-call, grpc per-message). Triggers on "modernize this", "convert to Java 25", "use newer Java features", or while reviewing recently-changed Java for upgrade opportunities.
---

# Java Modernization

## Overview

Apply Java idioms and hiero-mirror-node conventions when writing or refactoring Java in this repo. The Gradle build targets Java 25 and JSpecify + NullAway are wired into errorprone at error severity with `OnlyNullMarked=true` ([java-conventions.gradle.kts:62-68](buildSrc/src/main/kotlin/java-conventions.gradle.kts:62)) — so nullability mistakes inside `@NullMarked` scopes fail the build.

Modernize code that is already in motion (a file you're touching for another reason, a class under review). Don't go on a campaign to rewrite untouched files just to apply these rules.

## When NOT to apply

- **Lombok `@Value` classes — leave them alone.** They are project policy for immutable domain objects and are deliberately not records. Examples: [StreamType.Extension](common/src/main/java/org/hiero/mirror/common/domain/StreamType.java), [StreamFilename](importer/src/main/java/org/hiero/mirror/importer/domain/StreamFilename.java).
- **`Optional` returned by the JDK or third-party libraries** (Spring Data `JpaRepository.findById`, `Stream.findFirst`, `Optional.ofNullable`, etc.) — don't reshape signatures we don't own. The "no Optional" rule applies only to types we own.
- **Generated code** — OpenAPI, jOOQ, GraphQL, protobuf. Never edit; the build regenerates it.
- **Already-merged Flyway SQL migrations** — append-only.
- **`RestJavaRequest` DTOs bound by `@RequestParameter`** — the [RequestParameterArgumentResolver](rest-java/src/main/java/org/hiero/mirror/restjava/parameter/RequestParameterArgumentResolver.java) constructs DTOs via the no-arg constructor, so they must stay as Lombok POJOs (see [rest-api-conversion skill](.claude/skills/rest-api-conversion/SKILL.md)).

## Modernizations

### Records over hand-written POJOs

Use `record FooBar(...) {}` for plain immutable carriers we own — service layer response objects, internal DTOs, multi-table projections, multi-key cache keys, etc.

Examples in the repo:

- Nested key record: `PathKey` in [S3StreamFileProvider.java:183](importer/src/main/java/org/hiero/mirror/importer/downloader/provider/S3StreamFileProvider.java:183).
- API response shape with methods on the record: [PrometheusApiClient.java:55-73](monitor/src/main/java/org/hiero/mirror/monitor/health/PrometheusApiClient.java:55).
- Multi-table projection: [NetworkNodeDto.java](rest-java/src/main/java/org/hiero/mirror/restjava/dto/NetworkNodeDto.java).

Don't convert: Lombok `@Value` domain classes; request DTOs bound by `@RequestParameter` (see above).

### Text blocks for multi-line strings

Use `"""..."""` triple-quoted strings for any literal with embedded newlines — particularly SQL inside `@Query` / `@UpsertColumn` annotations and JSON fixtures in tests. Avoid `"foo\n" + "bar\n"` concatenation.

Examples: [EntityRepository.java:19-28](rest-java/src/main/java/org/hiero/mirror/restjava/repository/EntityRepository.java:19), [FileDataRepository.java:14-33](rest-java/src/main/java/org/hiero/mirror/restjava/repository/FileDataRepository.java:14), [AbstractTokenAccount.java:36-40](common/src/main/java/org/hiero/mirror/common/domain/token/AbstractTokenAccount.java:36).

### Switch expressions

Prefer arrow-form `switch (x) { case A -> ...; default -> ...; }` returning a value, over old `switch` statements with fall-through. Pairs naturally with sealed hierarchies for exhaustive pattern matching.

Examples: [RangeOperator.java:52-57](rest-java/src/main/java/org/hiero/mirror/restjava/common/RangeOperator.java:52), [GraphQlUtils.java:37-45](graphql/src/main/java/org/hiero/mirror/graphql/util/GraphQlUtils.java:37), [CommonMapper.java:97-110](graphql/src/main/java/org/hiero/mirror/graphql/mapper/CommonMapper.java:97).

### Sealed interfaces and classes

When a hierarchy is closed (a fixed list of subtypes), declare it `sealed ... permits ...`. The compiler then enforces exhaustive `switch` over it and rejects new subtypes added without updating the `permits` list.

Examples: [EntityIdParameter.java:7](rest-java/src/main/java/org/hiero/mirror/restjava/parameter/EntityIdParameter.java:7), [TransactionIdOrHashParameter.java:8](web3/src/main/java/org/hiero/mirror/web3/common/TransactionIdOrHashParameter.java:8).

### JSpecify nullability over `Optional` for code we own

We use JSpecify (`@NullMarked`, `@Nullable`) instead of `Optional` for return types, fields, and parameters in code we own. NullAway runs at error severity with `OnlyNullMarked=true`, so within a `@NullMarked` scope the compiler enforces nullness at every call site. Reasons:

- Avoids per-call `Optional` allocation overhead — significant in hot paths.
- Type system enforces nullness without a wrapper.
- Less ceremony at the call site (`if (x != null)` vs. `.orElseThrow()` / `.ifPresent(...)`).

**Where to put `@NullMarked` — apply it as broadly as possible, in this order of preference:**

1. **Package** (most preferred) — add `@NullMarked` to a `package-info.java`. Existing examples: [restjava/repository/package-info.java](rest-java/src/main/java/org/hiero/mirror/restjava/repository/package-info.java), [restjava/converter/package-info.java](rest-java/src/main/java/org/hiero/mirror/restjava/converter/package-info.java), [restjava/service/package-info.java](rest-java/src/main/java/org/hiero/mirror/restjava/service/package-info.java), [importer/reader/block/package-info.java](importer/src/main/java/org/hiero/mirror/importer/reader/block/package-info.java).
2. **Class** — only when a single class needs marking and converting the whole package would balloon the diff. Example: [S3StreamFileProvider.java:41](importer/src/main/java/org/hiero/mirror/importer/downloader/provider/S3StreamFileProvider.java:41).
3. **Method** — last resort, for a single odd-one-out signature.

Within a `@NullMarked` scope, use `@Nullable` on individual fields, parameters, and return types that may be null. Example: [ContractSlotId.java:26-32](common/src/main/java/org/hiero/mirror/common/domain/transaction/ContractSlotId.java:26).

A `package-info.java` for a new package looks like:

```java
// SPDX-License-Identifier: Apache-2.0

@NullMarked
package org.hiero.mirror.<module>.<sub>;

import org.jspecify.annotations.NullMarked;
```

**Conversion pattern:**

```java
// before — using Optional in code we own
public Optional<Foo> findOne(long id) { ... }
// caller:
foo.findOne(id).orElseThrow();
foo.findOne(id).ifPresent(this::handle);

// after — package or class is @NullMarked
public @Nullable Foo findOne(long id) { ... }
// caller:
final var result = foo.findOne(id);
if (result == null) {
    throw new NotFoundException();
}
if (result != null) {
    handle(result);
}
```

**Don't change** `Optional` returned by JDK or third-party libraries (`Stream.findFirst`, etc.) — leave call sites that already use `.orElse(...)` / `.map(...)` against those.

### Prefer non-null over `@Nullable` where reasonable

`@Nullable` is a tool, not a habit. When a field, return value, or parameter has a sensible non-null default, use it — callers don't have to null-check, and there's no boxing or wrapper allocation. Reach for `@Nullable` only when null actually carries meaning distinct from an empty / zero / false value.

**Fields — initialize to a sensible default rather than leaving them implicitly null.**

```java
// before
private String name;                  // implicitly null until set
private List<Foo> items;               // implicitly null
private Long count;                    // implicitly null; boxed Long

// after
private String name = "";
private List<Foo> items = List.of();
private long count = 0L;               // primitive, can't be null
```

For Lombok `@Builder`, mark the field with `@Builder.Default` so the default applies when the builder caller omits it.

**Collection-returning methods — return `List.of()` / `Set.of()` / `Map.of()` instead of `null`.**

Callers can then iterate, stream, or `isEmpty()`-check without a guard. Same applies to method parameters typed as collections — accept an empty collection rather than allowing null.

```java
// before
public @Nullable List<Foo> getFoos() {
    return result == null ? null : result;
}
// caller:
final var foos = getFoos();
if (foos != null) {
    for (var foo : foos) { ... }
}

// after
public List<Foo> getFoos() {
    return result == null ? List.of() : result;
}
// caller:
for (var foo : getFoos()) { ... }
```

**Primitives over boxed types when null isn't meaningful.** Each `Long` / `Integer` / `Boolean` is a heap allocation and a potential `NullPointerException` on unboxing — use `long` / `int` / `boolean` (or `double`, `short`, `byte`, `char`) when zero / false is a valid default and null doesn't add information.

```java
// before — Long forces every caller to null-check; null and 0L mean the same thing here
private Long timestamp;
private Boolean enabled;
private Integer retryCount;

// after
private long timestamp;        // 0L when unset is fine
private boolean enabled;       // false when unset is fine
private int retryCount;        // 0 when unset is fine
```

Keep boxed types when null carries meaning — for example a JPA / Spring Data column that is genuinely nullable in the database (a missing value is distinct from `0`), or a JSON field where absent and zero must be distinguished.

### Immutability by default

- **Fields:** `private final` whenever not reassigned post-construction.
- **Parameters:** `final` when not reassigned in the method body.
- **Locals:** `final` (or `final var`) when not reassigned.
- **Collections:** prefer `List.of(...)`, `Map.of(...)`, `Set.of(...)`, `List.copyOf(...)` over mutable builders when the value's lifetime is short and ownership is not handed off.

### `final var` for non-primitive locals

Use `final var x = expr;` for local variables when the RHS makes the type obvious — constructor calls, builders, well-named factory methods, fluent-call results. Prefer a written-out type for primitives (`final long timestamp = ...;`) so the reader sees the exact width.

Examples: [RangeOperator.java:42,61](rest-java/src/main/java/org/hiero/mirror/restjava/common/RangeOperator.java:42), [GenericControllerAdvice.java:89](rest-java/src/main/java/org/hiero/mirror/restjava/controller/GenericControllerAdvice.java:89).

### Streams — fine outside hot paths

Streams are idiomatic for one-shot collection transforms. **Avoid them in hot paths:**

- Importer per-record loops (each entry of a record-stream batch).
- `web3` per-call paths (every `eth_call` / EVM execution).
- `grpc` per-message paths (every streamed HCS message).

Lambda boxing, iterator allocation, and pipeline overhead show up under load there. A plain `for (X x : xs) { ... }` is the right choice.

When the result of a stream is a `Collection`, prefer `Stream.toList()` over `.collect(Collectors.toList())` — shorter, returns an unmodifiable list, no extra import.

### `java.util.HexFormat` for hex encoding

Use `HexFormat.of()` (and friends) for hex encoding/decoding. Replace hand-rolled `String.format("%02x", b)` loops, custom hex codecs, and Apache Commons `Hex.encodeHexString(...)`.

```java
HexFormat.of().formatHex(bytes);              // encode
HexFormat.of().parseHex(hexString);           // decode
HexFormat.of().withPrefix("0x").formatHex(b); // 0x-prefixed
HexFormat.of().withUpperCase().formatHex(b);  // uppercase
```

### `java.util.Base64` for Base64

Use `Base64.getEncoder()` / `Base64.getDecoder()` (or `getUrlEncoder` / `getMimeEncoder`) for Base64. Replace any third-party Base64 still in the codebase.

## Quick reference

| Old / verbose                                     | Java 25 idiom                                    |
| ------------------------------------------------- | ------------------------------------------------ |
| Hand-written immutable POJO we own                | `record`                                         |
| `"foo\n" + "bar\n"` SQL string                    | `"""..."""` text block                           |
| `switch`-statement returning a value              | `switch`-expression with `case ... ->`           |
| `Optional<Foo>` return in our code                | `@Nullable Foo` under `@NullMarked`              |
| `@Nullable Long count` (null means 0)             | `long count = 0L;`                               |
| `@Nullable Boolean enabled` (null means false)    | `boolean enabled = false;`                       |
| `return null;` from a collection-returning method | `return List.of();` (or `Set.of()` / `Map.of()`) |
| `private String name;` (implicitly null)          | `private String name = "";`                      |
| `private List<Foo> items;` (implicitly null)      | `private List<Foo> items = List.of();`           |
| Closed type hierarchy                             | `sealed ... permits ...`                         |
| `String.format("%02x", b)` loop                   | `HexFormat.of().formatHex(bytes)`                |
| Apache Commons `Hex.encodeHexString(b)`           | `HexFormat.of().formatHex(bytes)`                |
| Apache Commons `Base64`                           | `java.util.Base64`                               |
| `int x = ...; x = ...;` reassigned needlessly     | `final int x = ...;`                             |
| `var x = repo.findOne();`                         | `final var x = repo.findOne();`                  |
| `.collect(Collectors.toList())`                   | `.toList()`                                      |
| `stream().forEach(...)` in importer hot path      | `for (X x : xs) { ... }`                         |
| `@NullMarked` repeated on every class             | `@NullMarked` once in `package-info.java`        |

## Common mistakes

- **Converting a Lombok `@Value` class to a record** — project policy: don't.
- **Converting a `RestJavaRequest` DTO to a record** — `RequestParameterArgumentResolver` requires a no-arg constructor.
- **Reshaping a library `Optional` return to `@Nullable`** — only applies to types we own.
- **Marking `@NullMarked` on every method individually** when the whole package could be marked at once.
- **Returning `null` from a collection-returning method** — return `List.of()` / `Set.of()` / `Map.of()` so callers can iterate without a null guard.
- **Using `Long` / `Integer` / `Boolean` when null isn't meaningful** — use the primitive (`long` / `int` / `boolean`) with a default value. Keep the boxed type only when null and zero/false are semantically distinct (e.g. a nullable DB column).
- **Leaving a field implicitly null when a sensible default exists** — initialize `String` to `""`, collections to `List.of()` / `Set.of()` / `Map.of()`, numeric primitives to `0` / `0L` / `0.0`, booleans to `false`. For Lombok `@Builder`, pair the default with `@Builder.Default`.
- **Putting a stream in a per-record loop in the importer, per-call code in `web3`, or per-message code in `grpc`** — use a plain `for`.
- **`.collect(Collectors.toList())`** — use `.toList()`.
- **Forgetting `import org.jspecify.annotations.NullMarked;` / `Nullable;`** — they aren't auto-imported.
- **Using `final var` for primitives** — prefer the written-out type so the reader sees the width.
- **Editing generated code** (OpenAPI / jOOQ / GraphQL / protobuf) — never; the build regenerates it.

## Verification

1. `./gradlew :<module>:spotlessApply` — applies palantirJavaFormat / prettier.
2. `./gradlew :<module>:build` — passes errorprone + NullAway. NullAway runs at error severity, so any nullability mistake inside a `@NullMarked` scope fails the build.
3. `./gradlew :<module>:test` — module's unit and integration tests still pass.
