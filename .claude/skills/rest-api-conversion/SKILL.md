---
name: rest-api-conversion
description: Use when converting a JavaScript REST endpoint in rest/ to Java in rest-java/ for the hiero-mirror-node project. Covers Phase 1 of docs/checklist/rest-conversion.md — analyze the existing JS, implement controller / service / repository / request DTO / MapStruct mapper, write integration tests, wire JS specs, duplicate k6, update HAProxy / Helm / Cache-Control. Triggers on "convert /api/v1/<path> to Java" or "port the JS endpoint to rest-java".
---

# REST API Conversion (JavaScript → Java)

## Overview

Drives a Phase 1 conversion of one JavaScript REST endpoint in [rest/](rest/) to Java in [rest-java/](rest-java/), using
the patterns established by already-converted APIs. The canonical end-to-end example is the `/network/nodes` conversion
in [hiero-ledger/hiero-mirror-node#12889](https://github.com/hiero-ledger/hiero-mirror-node/pull/12889).

## When to use

- "Convert /api/v1/&lt;path&gt; to Java" / "port the JS endpoint to rest-java".
- Picking up an unimplemented endpoint from
  the [#1699 tracking table](https://github.com/hiero-ledger/hiero-mirror-node/issues/1699).

**Do not use for:**

- Phase 2 (enabling a route by default) or Phase 3 (deleting JS) — those steps are
  in [docs/checklist/rest-conversion.md](docs/checklist/rest-conversion.md).
- Modifying an already-converted endpoint (treat as a normal Java change).

## Prerequisites

1. Read [docs/checklist/rest-conversion.md](docs/checklist/rest-conversion.md) — this skill implements Phase 1; the
   checklist is the source of truth.
2. Confirm the target endpoint is in
   the [#1699 tracking table](https://github.com/hiero-ledger/hiero-mirror-node/issues/1699) and not already
   implemented.
3. Confirm the endpoint is defined in [rest/api/v1/openapi.yml](rest/api/v1/openapi.yml). The OpenAPI spec is the
   contract — the controller will return generated models from it. If the spec is missing or wrong, fix the spec first.
4. Apply the modernize-java skill for any output code.

## Layering rule (read before writing any code)

- **Controller** — HTTP only. Returns an OpenAPI-generated type from `org.hiero.mirror.rest.model.*`. Never calls a
  repository. Invokes service and mapper to convert from database models to OpenAPI generated response objects.
- **Service** — Business logic, parameter validation, and **the only layer that interacts with repositories**. A service
  may call multiple repositories when the JS query joined or aggregated data from multiple sources.
- **Repository** — Data access only.

## Phase 1 workflow

### 1. Analyze the existing JS implementation

Trace the request end-to-end through the JS before writing any Java. The JS may not be cleanly MVC-layered — that's
expected. Touch points:

- [rest/server.js](rest/server.js) → [rest/routes/](rest/routes/) → [rest/controllers/](rest/controllers/) → [rest/service/](rest/service/)
- Services should return [rest/model/](rest/model/)
- Controllers should return [rest/viewmodel/](rest/viewmodel/)
- Or root-level files when the endpoint isn't split into MVC layers,
  e.g. [rest/accounts.js](rest/accounts.js), [rest/transactions.js](rest/transactions.js), [rest/balances.js](rest/balances.js)

Capture:

- HTTP method + path; all query and path parameters with their validation rules
- Exact SQL queries (from [rest/sql/](rest/sql/) or inline strings)
- Examine example requests and responses from tests in [rest/**tests**/specs](rest/__tests__/specs/)
- Response shape — cross-check against [rest/api/v1/openapi.yml](rest/api/v1/openapi.yml)
- Edge cases: 404 vs empty list, special parameter combinations, ordering, pagination links

### 2. Request DTO

Lombok `@Value` POJO with field-level annotations
from [parameter/](rest-java/src/main/java/org/hiero/mirror/restjava/parameter/):

- [@RestJavaQueryParam](rest-java/src/main/java/org/hiero/mirror/restjava/parameter/RestJavaQueryParam.java) for query
  params
- [@RestJavaPathParam](rest-java/src/main/java/org/hiero/mirror/restjava/parameter/RestJavaPathParam.java) for path
  params
- `@Builder.Default` for defaults; `@Min`/`@Max`/`@Size` for validation
- For IDs that accept num / EVM address / alias, use the
  sealed [EntityIdParameter](rest-java/src/main/java/org/hiero/mirror/restjava/parameter/EntityIdParameter.java)
- Create a custom class with a static `valueOf` method similar to `EntityIdParameter` for other complex query parameter
  values.
- If the API only takes one query parameter, can skip the DTO and use `@RequestParam`.

Example: [NetworkNodeRequest.java](rest-java/src/main/java/org/hiero/mirror/restjava/dto/NetworkNodeRequest.java), [HookStorageRequest.java](rest-java/src/main/java/org/hiero/mirror/restjava/dto/HookStorageRequest.java).

> Records are not yet supported by
>
> the [RequestParameterArgumentResolver](rest-java/src/main/java/org/hiero/mirror/restjava/parameter/RequestParameterArgumentResolver.java) —
> it constructs the DTO via the no-arg constructor. Keep using POJOs.

### 3. Repository

Prefer **multiple pre-defined `@Query` methods**, one per parameter combination. Use native SQL in text blocks. The service
picks which method to call.

Example: [NetworkNodeRepository.java](rest-java/src/main/java/org/hiero/mirror/restjava/repository/NetworkNodeRepository.java).

If the query joins multiple tables and no domain entity fits the result, define a record projection
in [rest-java/.../dto/](rest-java/src/main/java/org/hiero/mirror/restjava/dto/) —
example: [NetworkNodeDto.java](rest-java/src/main/java/org/hiero/mirror/restjava/dto/NetworkNodeDto.java). For
single-table queries, use the existing domain entity; do not create an extra record.

**Use jOOQ only as a fallback** when static-query enumeration becomes unmaintainable.
Extend [JooqRepository.java](rest-java/src/main/java/org/hiero/mirror/restjava/repository/JooqRepository.java);
example: [TokenAirdropRepositoryCustom.java](rest-java/src/main/java/org/hiero/mirror/restjava/repository/TokenAirdropRepositoryCustom.java).

### 4. Service

Interface + Impl. Validate parameters, choose the right static repository call based on which optional parameters are
present, and compose results from multiple repositories when the JS did.
Example: [NetworkServiceImpl.java](rest-java/src/main/java/org/hiero/mirror/restjava/service/NetworkServiceImpl.java).

### 5. Controller

`@RestController` + `@RequestMapping(produces = APPLICATION_JSON)` + `@GetMapping`. Bind the request DTO with
`@RequestParameter`. The return type **must** be an OpenAPI-generated model from `org.hiero.mirror.rest.model.*` (e.g.,
`NetworkNodesResponse`, `BlocksResponse`).
Use [LinkFactory](rest-java/src/main/java/org/hiero/mirror/restjava/common/LinkFactoryImpl.java) when the response
includes pagination links.

Example: [NetworkController.java](rest-java/src/main/java/org/hiero/mirror/restjava/controller/NetworkController.java).

### 6. Mapper (MapStruct)

`@Mapper(config = MapperConfiguration.class)`. **MapStruct auto-maps any source → target field that shares the same name
and type** — only declare `@Mapping` when a field has a different name, a different type, or needs a transformation.

Always check [CommonMapper.java](rest-java/src/main/java/org/hiero/mirror/restjava/mapper/CommonMapper.java) before
writing a new conversion. Generic methods like `mapEntityId`, `mapKey`, `mapKeyList`, `mapTimestamp`, `mapRange`,
`mapByteArrayToHexString`, `mapTimestampRangeNullable`, `mapFraction` wire in automatically
via [MapperConfiguration.java](rest-java/src/main/java/org/hiero/mirror/restjava/mapper/MapperConfiguration.java) — do
not duplicate them.

Example: [NetworkNodeMapper.java](rest-java/src/main/java/org/hiero/mirror/restjava/mapper/NetworkNodeMapper.java), [TopicMapper.java](rest-java/src/main/java/org/hiero/mirror/restjava/mapper/TopicMapper.java).

### 7. Tests

Cover four layers — controller integration, repository, service, mapper. Tests must give **equivalent or better coverage
** than the JS spec tests for this endpoint.

- Prefer `@ParameterizedTest`, not `@TestFactory` / `Stream<DynamicTest>`.
  Examples: [HookStorageRepositoryTest.java](rest-java/src/test/java/org/hiero/mirror/restjava/repository/HookStorageRepositoryTest.java), [TokenAirdropRepositoryTest.java](rest-java/src/test/java/org/hiero/mirror/restjava/repository/TokenAirdropRepositoryTest.java), [TopicMapperTest.java](rest-java/src/test/java/org/hiero/mirror/restjava/mapper/TopicMapperTest.java).
- Controller test
  extends [ControllerTest.java](rest-java/src/test/java/org/hiero/mirror/restjava/controller/ControllerTest.java); use
  `@Nested` per endpoint shape, `RestClient`, `domainBuilder.<entity>().persist()` fixtures, AssertJ assertions.
  Structure
  example: [NetworkControllerTest.java](rest-java/src/test/java/org/hiero/mirror/restjava/controller/NetworkControllerTest.java).
- Ensure equivalent test coverage from applicable tests in [rest/**tests**/specs](rest/__tests__/specs/) in controller
  tests. Use dynamic input from DomainBuilder instead of static data in JS.
- Service test
  example: [NetworkServiceTest.java](rest-java/src/test/java/org/hiero/mirror/restjava/service/NetworkServiceTest.java).

### 8. Wire JS specs against the new endpoint

In [rest/build.gradle.kts](rest/build.gradle.kts) (lines 16–29), append the spec path regex to `specPaths` and the Jest
test file to `testFiles`. The runner [rest/**tests**/integration/template.js](rest/__tests__/integration/template.js)
reads `REST_JAVA_INCLUDE` and replays matching specs from [rest/**tests**/specs/](rest/__tests__/specs/) against the
rest-java container. All existing specs for the endpoint must pass unchanged.

### 9. Cache-Control

Find the path entry in [rest/config/application.yml](rest/config/application.yml) under
`hiero.mirror.rest.cache.response.headers.path` (e.g.,
`/api/v1/blocks/:hashOrNumber: { "cache-control": "public, max-age=600" }`). Copy that **exact value**
into [rest-java/src/main/resources/application.yml](rest-java/src/main/resources/application.yml) under
`response.headers.path` using the `[/api/v1/...]` keying syntax. Do not invent a value.

### 10. Routing — HAProxy + Helm chart

- [docker-compose.yml](docker-compose.yml): add an nginx `location` in `proxy-config` routing the path to
  `rest_java_host`.
- [charts/hedera-mirror-rest-java/values.yaml](charts/hedera-mirror-rest-java/values.yaml): add
  `routes.<apiName>: false`; add the ingress path with `condition: '{{ .Values.routes.<apiName> }}'`; add the gateway
  rule under `if .Values.routes.<apiName>` so it stays disabled by default until rolled out.

### 11. Acceptance test

Switch the relevant call
in [MirrorNodeClient.java](test/src/test/java/org/hiero/mirror/test/e2e/acceptance/client/MirrorNodeClient.java) from
`callRestEndpointNoRetry(...)` to `callConvertedRestEndpoint(...)`. Phase 1 exercises both clients to catch regressions;
Phase 3 will switch it to `callRestJavaEndpoint(...)`.

### 12. K6

Duplicate the matching test from [tools/k6/src/rest/test/](tools/k6/src/rest/test/)
into [tools/k6/src/rest-java/test/](tools/k6/src/rest-java/test/). Also add the copied tests to the rest-java index.js. Swap
`RestTestScenarioBuilder` → `RestJavaTestScenarioBuilder` and append it to [tools/k6/src/rest-java/test/index.js](tools/k6/src/rest-java/test/index.js).
Performance must be equal to or better than the JS endpoint.

## Quick reference

| Artifact                               | Canonical example                                                                                                                   |
| -------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| Controller (returns OpenAPI model)     | [NetworkController.java](rest-java/src/main/java/org/hiero/mirror/restjava/controller/NetworkController.java)                       |
| Request DTO (Lombok `@Value`)          | [NetworkNodeRequest.java](rest-java/src/main/java/org/hiero/mirror/restjava/dto/NetworkNodeRequest.java)                            |
| Service (only repo caller)             | [NetworkServiceImpl.java](rest-java/src/main/java/org/hiero/mirror/restjava/service/NetworkServiceImpl.java)                        |
| Repository (static `@Query`)           | [NetworkNodeRepository.java](rest-java/src/main/java/org/hiero/mirror/restjava/repository/NetworkNodeRepository.java)               |
| Repository (jOOQ fallback)             | [TokenAirdropRepositoryCustom.java](rest-java/src/main/java/org/hiero/mirror/restjava/repository/TokenAirdropRepositoryCustom.java) |
| Multi-table projection record          | [NetworkNodeDto.java](rest-java/src/main/java/org/hiero/mirror/restjava/dto/NetworkNodeDto.java)                                    |
| MapStruct mapper                       | [NetworkNodeMapper.java](rest-java/src/main/java/org/hiero/mirror/restjava/mapper/NetworkNodeMapper.java)                           |
| Generic conversions (reuse)            | [CommonMapper.java](rest-java/src/main/java/org/hiero/mirror/restjava/mapper/CommonMapper.java)                                     |
| Controller integration test            | [NetworkControllerTest.java](rest-java/src/test/java/org/hiero/mirror/restjava/controller/NetworkControllerTest.java)               |
| Repository test (`@ParameterizedTest`) | [HookStorageRepositoryTest.java](rest-java/src/test/java/org/hiero/mirror/restjava/repository/HookStorageRepositoryTest.java)       |
| Mapper test (`@ParameterizedTest`)     | [TopicMapperTest.java](rest-java/src/test/java/org/hiero/mirror/restjava/mapper/TopicMapperTest.java)                               |
| K6 (rest-java)                         | [tools/k6/src/rest-java/test/](tools/k6/src/rest-java/test/)                                                                        |
| HAProxy                                | [docker-compose.yml](docker-compose.yml)                                                                                            |
| Helm chart                             | [charts/hedera-mirror-rest-java/values.yaml](charts/hedera-mirror-rest-java/values.yaml)                                            |
| Cache-Control source                   | [rest/config/application.yml](rest/config/application.yml)                                                                          |
| Acceptance client                      | [MirrorNodeClient.java](test/src/test/java/org/hiero/mirror/test/e2e/acceptance/client/MirrorNodeClient.java)                       |

## Common mistakes

- Returning a custom DTO from the controller — must be an OpenAPI-generated type from `org.hiero.mirror.rest.model.*`.
- Calling a repository from the controller — go through the service.
- Putting business logic in the controller — belongs in the service.
- Using `@RequestParam` directly when the request has multiple optional parameters — use `@RequestParameter` with a
  `@Value` POJO.
- Building one big query with conditional `coalesce(...)` / `IS NULL OR ...` clauses — define multiple static `@Query`
  methods and let the service choose.
- Reaching for jOOQ first — try static `@Query` first; jOOQ only when enumeration becomes unmaintainable.
- Using `@TestFactory` / `Stream<DynamicTest>` — use `@ParameterizedTest` instead.
- Adding an `@Mapping` for a field that auto-maps (same name + same type) — let MapStruct do it.
- Writing a custom Jackson type-mapping in MapStruct without
  checking [CommonMapper.java](rest-java/src/main/java/org/hiero/mirror/restjava/mapper/CommonMapper.java) first.
- Inventing a Cache-Control value — copy the exact value
  from [rest/config/application.yml](rest/config/application.yml).
- Forgetting to wire the spec into [rest/build.gradle.kts](rest/build.gradle.kts) — JS specs never exercise the new
  endpoint without it.
- Skipping Step 1 (JS analysis) — leads to behavioral drift; the JS may not be MVC-layered, so trace the route
  end-to-end before writing Java.

## Verification

1. `./gradlew :rest-java:test` — Java unit + integration tests pass.
2. `./gradlew :rest:testRestJava` — JS spec tests replay against the new Java endpoint and pass.
3. K6 run locally (if available) — RPS at least matches the JS endpoint.
4. Walk the [Phase 1 checklist](docs/checklist/rest-conversion.md) section by section and confirm every box is
   checkable.
