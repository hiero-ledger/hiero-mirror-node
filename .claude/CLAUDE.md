# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

Java tooling requires **Java 25** (`sdk install java 25-tem`). The Gradle wrapper handles everything else.

```bash
./gradlew clean build              # Full build with tests
./gradlew clean build -x test      # Build, skip tests
./gradlew :importer:build          # Build a single module
./gradlew spotlessApply            # Apply code formatting (palantirJavaFormat, prettier, ktfmt)
./gradlew spotlessCheck            # Verify formatting
./gradlew test                     # Run all tests
./gradlew :common:test             # Tests for one module
./gradlew test --tests "*FooTest"            # Single test class
./gradlew test --tests "*FooTest.bar"        # Single test method
./gradlew :test:acceptance --info -Dcucumber.filter.tags=@acceptance   # E2E (Cucumber, hits a live network)
```

The REST module is Node.js, while Pinger and Rosetta are Go-based. While not native Java, most of the same Gradle
commands used for Java modules can be used with them:

```bash
./gradlew :pinger:clean :rest:clean :rest:monitoring:clean :rosetta:clean # Clean build artifacts
./gradlew :pinger:build :rest:build :rest:monitoring:build :rosetta:build # Full build with tests
./gradlew :pinger:test :rest:test :rest:monitoring:test :rosetta:test     # Run all tests
./gradlew :pinger:run :rest:run :rest:monitoring:run :rosetta:run         # Run the application locally
```

Run a JVM component locally via Spring Boot:

```bash
./gradlew :importer:run
./gradlew :importer:run --args='--spring.profiles.active=v2'   # Citus/sharded variant
```

Local stack (PostgreSQL plus all components):

```bash
docker compose up
```

Build/push images: `./gradlew dockerPush -PimagePlatform=linux/amd64 -PimageRegistry=... -PimageTag=...` (
see [docs/development.md](docs/development.md)).

## Architecture

A mirror node ingests record/block stream files produced by Hiero consensus nodes and serves them via several APIs. The
repo is one multi-module Gradle build (`settings.gradle.kts`). The Go and Node.js modules are integrated into the Gradle
build as well. Modules talk only via the database and Redis — no in-process cross-module calls.

### Data flow

```
Consensus nodes → S3/GCS (record + signature files) → importer → PostgreSQL → {rest, rest-java, web3, grpc, graphql, rosetta} → clients
                                                                     ↑
                                                                  monitor (synthetic load + verification)
```

The importer downloads signature files, verifies ≥ 1/3 node agreement on each record file's hash, downloads matching
record files, validates the hash chain, and ingests normalized rows.

### Modules

| Module      | Language          | Role                                                                                                                                        |
| ----------- | ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| `common`    | Java              | Shared domain entities, JPA repositories, configuration. Almost every JVM module depends on it.                                             |
| `graphql`   | Java/Spring       | GraphQL surface over the same data.                                                                                                         |
| `grpc`      | Java/Spring       | gRPC subscription endpoints (HCS topic streaming, etc.).                                                                                    |
| `importer`  | Java/Spring       | Stream-file downloader + parser + Flyway migrations. Owns the database schema (`importer/.../db/migration`).                                |
| `monitor`   | Java/Spring       | Generates synthetic transactions and validates round-trip ingestion across networks.                                                        |
| `pinger`    | Go                | Lightweight liveness/keepalive transaction submitter.                                                                                       |
| `protobuf`  | Java              | Generated protobuf bindings for the grpc module.                                                                                            |
| `rest-java` | Java/Spring       | Java replacement for newer REST endpoints (jOOQ-based queries).                                                                             |
| `rest`      | Node.js (Express) | Primary public REST API at `/api/v1/...`. Endpoint→table mapping is in [docs/rest/README.md](docs/rest/README.md).                          |
| `rosetta`   | Go                | Coinbase Rosetta API implementation.                                                                                                        |
| `test`      | Java/Cucumber     | E2E acceptance suite that drives a live network via the Hiero Java SDK. Disables the default `test` task — only the `acceptance` task runs. |
| `web3`      | Java/Spring       | EVM execution and `eth_call`-style endpoints; embeds Hyperledger Besu EVM and parts of consensus node.                                      |

`buildSrc/src/main/kotlin/*-conventions.gradle.kts` defines reusable Gradle plugins (`java-conventions`,
`spring-conventions`, `docker-conventions`, `openapi-conventions`, `jooq-conventions`, ...). When adding a new JVM
module, apply the relevant convention plugin instead of duplicating config.

### Database

Single PostgreSQL database, schema owned by Flyway migrations under
`importer/src/main/resources/db/migration/{v1,v2,common}`. The `v2` profile targets a Citus-sharded variant (
`./gradlew :importer:bootRun --args='--spring.profiles.active=v2'`); migrations live in separate folders. There also
exist Java migrations in `importer/src/main/java/org/hiero/mirror/importer/migration`. Most read-side
modules use Spring Data JPA repositories from `common`; `rest-java` additionally uses jOOQ.

### Configuration

All Spring components are configured under the `hiero.mirror.<module>.*` property prefix (e.g.
`hiero.mirror.importer.network`). Defaults live in each module's `application.yml` and also in Spring
`@ConfigurationProperies` marked Java classes. For Docker Compose runs, override via `configs.app-config.content` in
[docker-compose.yml](docker-compose.yml).

### Helm

Production deployment is via the Helm charts under [charts/](charts/) (one chart per component plus an umbrella
chart `hedera-mirror`). Chart values mirror the Spring property tree. The `deploy` branch contains the deployment
configuration used for GitOps-based deployment.

## Conventions

- **Formatting** is enforced via Spotless (`./gradlew spotlessApply`). Java uses palantirJavaFormat; Kotlin uses ktfmt;
  JS/JSON/MD/YAML use Prettier with the project's Node.js setup. Spotless is configured to ratchet against `origin/main`
  outside CI, so it only formats what you've changed.
- **License headers**: `// SPDX-License-Identifier: Apache-2.0` is required at the top of source files. Spotless adds it
  automatically.
- **Java style**: Lombok and MapStruct are used heavily across JVM modules. Errorprone and NullAway are wired into
  `java-conventions`.
- **Migrations are append-only**: never edit a Flyway SQL migration that has been merged. Add a new one.
- **Commit/PR style**: PR description = imperative bullets ("Add ...", "Fix ..."), copied to the squash-merge commit.
  Every PR needs a linked issue (`Fixes #1234`) and a milestone. See [docs/contributing.md](docs/contributing.md).
- **Fail fast**: Methods written in any language should prefer checking conditions early and failing fast with a return
  statement. Avoid if statements that cause logic to indent the remainder of the method.

## Required skills

- **[`modernize-java`](.claude/skills/modernize-java/SKILL.md) — required on every Java refactor or code review in this
  repo.** Invoke it (or apply its rules) before proposing any Java change. It codifies the project's Java idioms. Do
  not produce, suggest, or approve Java changes that violate the skill without explicit user override.

## Acceptance tests caveat

`./gradlew :test:acceptance` submits real transactions to a live Hiero network using the operator key in
`test/src/test/resources/application.yml`. Defaults target TESTNET. Don't run it pointed at MAINNET unless you mean to
spend hbar — set `hiero.mirror.test.acceptance.network` and `operatorKey` deliberately.
