# New Feature Checklist

New tables or columns are routinely added to the mirror node to support changes in HAPI. Due to the microservice
nature of the mirror node there are a lot of components that need to be updated for any new feature. This checklist
aims to document the required changes associated with any new feature.

## Common

- [ ] Domain class
  - [ ] `@AllArgsConstructor(access = AccessLevel.PRIVATE)`, `@Builder(toBuilder = true)` for builder
  - [ ] `@IdClass` for composite keys
- [ ] `TransactionType`
- [ ] `DomainBuilder`

## GraphQL API

- [ ] Transaction type

## Importer

- [ ] V1 migration
- [ ] V2 migration
- [ ] Repository
  - [ ] `RetentionRepository` if insert-only
- [ ] `BlockItemTransformer`
- [ ] `EntityListener`
- [ ] `CompositeEntityListener`
- [ ] `SqlEntityListener`
  - [ ] Merge method added if not insert-only and all fields properly merged
- [ ] `TransactionHandler`
- [ ] Tests for each of the above
- [ ] `RecordItemBuilder`
- [ ] `EntityRecordItemListener*Test`

## Monitor

The Java monitor needs to be updated for any new or modified HAPI transactions that are user facing.

- [ ] `TransactionSupplier`
- [ ] `TransactionType`

## Rest API

- [ ] `openapi.yml`
  - [ ] Transaction type
  - [ ] Request/response
- [ ] Model
- [ ] View model
- [ ] Route
- [ ] Controller
- [ ] Service
- [ ] Tests for each of the above
- [ ] `integrationDomainOps`
- [ ] Spec tests
- [ ] Monitor API test

## Rosetta API

- [ ] Domain
- [ ] Repository
- [ ] Service
- [ ] `TransactionResults`
- [ ] `TransactionTypes`
- [ ] `rosetta-cli` validation configuration
- [ ] BDD Test

## Test

Acceptance and performance tests should be updated whenever there's an API change.

- [ ] Cucumber scenario
- [ ] Acceptance REST model
- [ ] Acceptance client
- [ ] Acceptance feature
- [ ] k6 performance test

## Documentation

- [ ] [Configuration Properties](/docs/configuration.md)
- [ ] [Database Indexes Table](/docs/database/README.md#indexes)
- [ ] [ERD SQL](/docs/database/erd.sql)
- [ ] [Regenerate](/docs/database/README.md#entity-relationship-diagram-erd) the [ERD diagram](/docs/database/erd.drawio)
- [ ] [HAPI Compatibility Matrix](/docs/importer/README.md#hapi-compatibility)
- [ ] [REST Database Table](/docs/rest/README.md#database)
