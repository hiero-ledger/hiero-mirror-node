---
name: modernize-java
description: Use when writing, refactoring, or reviewing Java in hiero-mirror-node to apply Java 25 idioms and project conventions (records, text blocks, switch expressions, sealed types, JSpecify nullability over Optional, non-null defaults, immutability, HexFormat/Base64, no streams in hot paths). Triggers on "modernize this", "convert to Java 25", "use newer Java features", or reviewing recently-changed Java.
---

# Java Modernization

**Load once per session.** If this skill's content is already in the conversation, do not invoke it again — just apply
the rules below.

## Overview

Apply these idioms when writing or refactoring Java in this repo. The build targets Java 25, and JSpecify + NullAway run
in errorprone at error severity with `OnlyNullMarked=true` ([java-conventions.gradle.kts:62-68](buildSrc/src/main/kotlin/java-conventions.gradle.kts:62)),
so nullability mistakes inside `@NullMarked` scopes fail the build.

Modernize code already in motion (a file you're touching, a class under review). Don't rewrite untouched files just to
apply these rules.

## When NOT to apply

- **Lombok `@Value` classes** — project policy for immutable domain objects; deliberately not records. Examples: [StreamType.Extension](common/src/main/java/org/hiero/mirror/common/domain/StreamType.java), [StreamFilename](importer/src/main/java/org/hiero/mirror/importer/domain/StreamFilename.java).
- **`RestJavaRequest` DTOs bound by `@RequestParameter`** — [RequestParameterArgumentResolver](rest-java/src/main/java/org/hiero/mirror/restjava/parameter/RequestParameterArgumentResolver.java) uses the no-arg constructor, so they stay Lombok POJOs (see [rest-api-conversion skill](.claude/skills/rest-api-conversion/SKILL.md)).
- **`Optional` returned by the JDK or third-party libraries** (`findById`, `Stream.findFirst`, ...) — don't reshape signatures we don't own. The "no Optional" rule applies only to types we own.
- **Generated code** (OpenAPI, jOOQ, GraphQL, protobuf) — never edit; the build regenerates it.
- **Merged Flyway SQL migrations** — append-only.

## Modernizations

### Records

Use `record FooBar(...) {}` for plain immutable carriers we own: service response objects, internal DTOs, multi-table
projections, multi-key cache keys. Exceptions are listed above.

Examples: `PathKey` in [S3StreamFileProvider.java:183](importer/src/main/java/org/hiero/mirror/importer/downloader/provider/S3StreamFileProvider.java:183), [PrometheusApiClient.java:55-73](monitor/src/main/java/org/hiero/mirror/monitor/health/PrometheusApiClient.java:55), [NetworkNodeDto.java](rest-java/src/main/java/org/hiero/mirror/restjava/dto/NetworkNodeDto.java).

### Text blocks

Use `"""..."""` for any literal with embedded newlines — SQL in `@Query` / `@UpsertColumn`, JSON test fixtures. No
`"foo\n" + "bar\n"` concatenation.

Examples: [EntityRepository.java:19-28](rest-java/src/main/java/org/hiero/mirror/restjava/repository/EntityRepository.java:19), [AbstractTokenAccount.java:36-40](common/src/main/java/org/hiero/mirror/common/domain/token/AbstractTokenAccount.java:36).

### Switch expressions

Prefer arrow-form `switch` returning a value over statements with fall-through. Pairs with sealed hierarchies for
exhaustiveness.

Examples: [RangeOperator.java:52-57](rest-java/src/main/java/org/hiero/mirror/restjava/common/RangeOperator.java:52), [CommonMapper.java:97-110](graphql/src/main/java/org/hiero/mirror/graphql/mapper/CommonMapper.java:97).

### Sealed types

Declare closed hierarchies `sealed ... permits ...` so the compiler enforces exhaustive `switch` and rejects unlisted
subtypes.

Examples: [EntityIdParameter.java:7](rest-java/src/main/java/org/hiero/mirror/restjava/parameter/EntityIdParameter.java:7), [TransactionIdOrHashParameter.java:8](web3/src/main/java/org/hiero/mirror/web3/common/TransactionIdOrHashParameter.java:8).

### JSpecify nullability over `Optional`

In code we own, use `@Nullable` instead of `Optional` for returns, fields, and parameters. It avoids per-call
allocation in hot paths, the type system enforces it, and call sites are simpler.

**Where to put `@NullMarked`** — as broadly as possible:

1. **Package** (preferred): `package-info.java`. Examples: [restjava/repository](rest-java/src/main/java/org/hiero/mirror/restjava/repository/package-info.java), [importer/reader/block](importer/src/main/java/org/hiero/mirror/importer/reader/block/package-info.java).
2. **Class**: when converting the whole package would balloon the diff. Example: [S3StreamFileProvider.java:41](importer/src/main/java/org/hiero/mirror/importer/downloader/provider/S3StreamFileProvider.java:41).
3. **Method**: last resort, for one odd-one-out signature. Never mark every method individually.

Within a `@NullMarked` scope, put `@Nullable` on the individual fields, parameters, and returns that may be null.
Example: [ContractSlotId.java:26-32](common/src/main/java/org/hiero/mirror/common/domain/transaction/ContractSlotId.java:26).
`org.jspecify.annotations.NullMarked` / `Nullable` are not auto-imported.

```java
// SPDX-License-Identifier: Apache-2.0

@NullMarked
package org.hiero.mirror.<module>.<sub>;

import org.jspecify.annotations.NullMarked;
```

```java
// before: foo.findOne(id).orElseThrow();
// after:
public @Nullable Foo findOne(long id) { ... }

final var result = foo.findOne(id);
if (result == null) {
    throw new NotFoundException();
}
```

### Prefer non-null over `@Nullable`

Use `@Nullable` only when null means something distinct from empty / zero / false.

- **Fields:** initialize to a sensible default instead of leaving them implicitly null: `String name = ""`,
  `List<Foo> items = List.of()`. With Lombok `@Builder`, add `@Builder.Default`.
- **Collection returns and parameters:** return `List.of()` / `Set.of()` / `Map.of()` instead of `null`, so callers
  iterate without a guard.
- **Primitives over boxed types:** each `Long` / `Integer` / `Boolean` is a heap allocation and a potential
  `NullPointerException` on unboxing. Use `long` / `int` / `boolean` (or `double`, `short`, `byte`, `char`) when 0 / false
  is a valid default and null adds no information. Keep the boxed type only when null is meaningful, e.g. a genuinely
  nullable DB column or a JSON field where absent differs from zero.

### Immutability

- **Fields:** `private final` unless reassigned after construction.
- **Parameters and locals:** `final` when not reassigned. For non-primitive locals use `final var x = expr;` when the
  RHS makes the type obvious (constructors, builders, factories, fluent calls). For primitives write the type out
  (`final long timestamp = ...;`) so the width is visible — never `final var` for primitives.
- **Collections:** prefer `List.of(...)`, `Map.of(...)`, `Set.of(...)`, `List.copyOf(...)` over mutable builders when
  the lifetime is short and ownership isn't handed off.

Examples: [RangeOperator.java:42,61](rest-java/src/main/java/org/hiero/mirror/restjava/common/RangeOperator.java:42), [GenericControllerAdvice.java:89](rest-java/src/main/java/org/hiero/mirror/restjava/controller/GenericControllerAdvice.java:89).

### Streams

Fine for one-shot collection transforms. **Avoid in hot paths:**

- Importer per-record loops (each entry of a record-stream batch).
- `web3` per-call paths (every `eth_call` / EVM execution).
- `grpc` per-message paths (every streamed HCS message).

Lambda boxing, iterator allocation, and pipeline overhead show up under load there. Use a plain `for`.

Prefer `Stream.toList()` over `.collect(Collectors.toList())` — shorter, returns an unmodifiable list, no extra import.

### `HexFormat` and `Base64`

Use `java.util.HexFormat` and `java.util.Base64` instead of hand-rolled loops, `String.format("%02x", b)`, Apache
Commons `Hex` / `Base64`, or other third-party equivalents.

```java
HexFormat.of().formatHex(bytes);              // encode
HexFormat.of().parseHex(hexString);           // decode
HexFormat.of().withPrefix("0x").formatHex(b); // 0x-prefixed
Base64.getEncoder() / getDecoder() / getUrlEncoder() / getMimeEncoder()
```

## Verification

1. `./gradlew :<module>:build` — passes errorprone + NullAway.
2. `./gradlew :<module>:test` — module tests still pass.

Formatting is applied by the git `pre-commit` hook; don't run Spotless.
