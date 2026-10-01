# HIP-1535 CLPR Mirror Node Support - Design Document

## Overview

This design document outlines the implementation plan for supporting [HIP-1535](https://github.com/hiero-ledger/hiero-improvement-proposals/blob/main/HIP/hip-1535.md)
CLPR ("Clipper") in the Hiero mirror node. CLPR is a native Hiero service that lets a Hiero network exchange
ordered, reliable messages with peer ledgers (Hiero or non-Hiero) without a bridge or intermediary validator set.
The implementation will enable ingestion, storage, and querying of CLPR on-ledger state — Channels, Connectors,
the message queue, and ledger/endpoint configuration.

This is tracked by [issue #14356](https://github.com/hiero-ledger/hiero-mirror-node/issues/14356), targeted for
milestone `0.165.0`. The issue asks for three things, each addressed by a section below:

1. A design document (this document).
2. Mirror-node-specific feedback on the HIP (see [HIP-1535 Feedback / Required Clarifications](#hip-1535-feedback--required-clarifications)).
3. A breakdown of follow-up implementation tasks (see [Proposed Follow-up Implementation Tasks](#proposed-follow-up-implementation-tasks)).

## Goals

- Ingest all new CLPR transaction types and persist their resulting state to the database (Channels, Connectors,
  ledger configuration, endpoint manifest, and the message queue).
- Model Channel and Connector state with full history, consistent with how other long-lived, mutable Hiero
  entities (tokens, topics, schedules) are tracked in this codebase.
- Model the CLPR message queue (Data/Response/Control payloads) as an append-only ledger suitable for audit and
  reconciliation, per HIP-1535 §11 ("Impact on Mirror Node").
- Expose CLPR state through the mirror node's read APIs so application developers, Connector Operators, and
  auditors can inspect Channel/Connector health and message history without running their own node.
- Capture the response-code and signal fields (`ClprChannelStatus`, `ClprMessageReplyStatus`, slash counts) needed
  to reconstruct the protocol's economic and lifecycle behavior described in HIP-1535 §6-§9.

## Non-Goals

- Verifying CLPR state proofs, bundle payloads, or trust anchor rotations — this is Hiero consensus-node and
  verifier-contract responsibility. The mirror node only records what the consensus node already decided.
- Acting as a CLPR endpoint, relaying bundles, or participating in the sync protocol described in HIP-1535 §6.2.
- Indexing the Hiero-internal, non-cross-ledger mechanisms that never reach consensus state in a portable form:
  `ClprEndpointManifestConstruction` bookkeeping beyond the finalized manifest, and `ClprPeerEndpoints` (explicitly
  node-local, non-consensus state per its proto doc comment).
- Decoding or interpreting `bundle_payload`, verifier proof bytes, or application `message_data`/`message_reply_data`
  — these are opaque per HIP-1535 §5 and §7.5 ("Applications MUST treat all cross-ledger payloads ... as untrusted
  input"); the mirror node stores them as opaque bytes only.
- Rosetta, gRPC, GraphQL, or web3 support for CLPR — scoped to REST (`rest-java`) only for this iteration.
- Message redaction (`ClprRedactMessage`). HIP-1535 §10.6 states this mechanism was removed from the specification
  under Rejected Ideas; this design follows the HIP text and does not persist or expose it, despite the reference
  implementation's proto still defining it (see [HIP-1535 Feedback](#hip-1535-feedback--required-clarifications)).

## Background

CLPR (Cross Ledger Protocol) lets a Hiero network exchange arbitrary payloads with a peer ledger, with every claim
about the peer's state authenticated by a pluggable **verifier contract** rather than a bridge validator set. Key
concepts from the HIP:

- **Channel** — a permissionless, 32-byte-ID entity representing a communication path to one peer CLPR Service
  instance, bound at creation to one immutable verifier contract. A Channel moves through a six-state lifecycle:
  `PENDING → ACTIVE ⇄ PAUSED → CLOSING → DRAINED → CLOSED`. Creation uses a two-phase commit-reveal scheme
  (`registerChannel` then `completeChannel`) to prevent cross-chain front-running of a chosen Channel ID.
- **Connector** — an economic actor, also registered via commit-reveal, that authorizes messages on one ledger and
  pays for their execution on the other via a locked, slashable stake plus a funded balance.
- **Message / Bundle** — messages (Data, Response, or Control) travel in an ordered, per-Channel queue sharing one
  running-hash chain, and are relayed between ledgers in **bundles** submitted as `ClprSubmitBundle` transactions.
  Every Data Message produces exactly one Response Message.
- **Endpoint / Endpoint Manifest** — on Hiero, every consensus node is automatically an endpoint; the manifest is
  derived from the roster via an internal, per-node self-publication transaction (`ClprEndpointPublication`) and a
  manifest-construction process, and is exposed as a queryable singleton.
- **Ledger Configuration** — a singleton per CLPR Service holding `ChainID`, protocol version, and throttle limits,
  admin-updatable via `ClprUpdateLedgerConfiguration`.

Per HIP-1535 §11 ("Impact on Mirror Node"): _"Mirror nodes MUST index CLPR Service state changes (Channel
creation/state transitions, Connector registration/balance/slashing events, and enqueued/dispatched messages) to
support application-level auditing and reconciliation, the same way they index other native Hiero services today.
No new consensus-level query type is required; CLPR state is ordinary Merkle state."_ This is the mandate this
design implements.

## Architecture

CLPR transactions flow through the mirror node's existing, single transaction-processing pipeline — there is no
separate ingestion path for CLPR:

1. Every CLPR `TransactionBody` case (`clprRegisterChannel`, `clprCompleteChannel`, `clprCloseChannel`,
   `clprSubmitBundle`, `clprRegisterConnector`, `clprDeregisterConnector`, `clprCompleteConnector`,
   `clprUpdateLedgerConfiguration`) gets a dedicated `TransactionHandler`, the same as any other transaction type
   (see [Importer Module Changes](#importer-module-changes)).
2. **CLPR's verifier-derived fields are only ever available via consensus state, not via any record-stream
   carrier — so this design assumes CLPR transactions are only ever delivered via block stream.** Checked against
   the reference implementation: none of `transaction_receipt.proto`, `transaction_record.proto`, or the sidecar
   proto define any CLPR-specific field. Values such as `ClprCompleteChannel`'s verifier-assigned
   `trust_anchor`/`channel_context`, or `ClprSubmitBundle`'s dispatched message contents, exist only as
   `state_changes.proto` `StateChange` entries (`clpr_channel_value`, `clpr_connector_value`,
   `clpr_message_value`/`clpr_message_key`, `clpr_ledger_configuration_value`, `clpr_endpoint_manifest_value`) —
   the block-stream representation of consensus state. This is a real implementation constraint: a
   `TransactionHandler` cannot read these fields off the transaction body or a classic `TransactionRecord`. We
   handle it with new `RecordItem` fields and dedicated `BlockTransactionTransformer` subclasses (see points 3-4
   below). Since there is no legacy record-stream carrier for CLPR to begin with, this design builds no
   CLPR-specific handling for a classic (non-block-stream-derived) record stream — a dedicated record-stream code
   path would be dead code; if a future network somehow needs CLPR over a pure record stream, that would require
   its own follow-up design once a concrete carrier for the verifier-derived fields is defined.
3. **`RecordItem` — the object every `TransactionHandler` actually consumes — has no slot for this data either.**
   `RecordItem` wraps a classic `TransactionRecord` plus a list of classic `TransactionSidecarRecord`s (see
   `common/src/main/java/org/hiero/mirror/common/domain/transaction/RecordItem.java`), and neither proto has any
   CLPR-specific field or oneof case. This is different from e.g. `ContractStateChange`, which has a real sidecar
   case to populate, or `TokenAirdrop`, which has a real `TransactionRecord.newPendingAirdrops` field — CLPR has
   neither. So the existing `BlockTransactionTransformer` pattern (see
   `importer/src/main/java/org/hiero/mirror/importer/downloader/block/transformer/`, e.g. `TokenAirdropTransformer`)
   can't be reused unmodified by just populating the synthetic `TransactionRecord`; it needs a companion change.
4. Since `RecordItem` is an importer-internal Java class, not a strict 1:1 mirror of the wire proto (it already
   carries non-proto fields like `hookParent`), the fix is to **add new CLPR-specific fields to `RecordItem`**
   (e.g. the parsed `clpr_channel_value`/`clpr_connector_value`/`clpr_message_value` state changes relevant to the
   transaction). A `ClprXxxTransformer` per CLPR transaction type that needs verifier-derived enrichment reads the
   relevant `StateChange`s from `StateChangeContext` and populates those new `RecordItem` fields at build time; the
   corresponding `TransactionHandler` then reads them directly, the same way it reads `transactionBody` or
   `transactionRecord` today. This is CLPR-specific handler awareness, not a fully source-agnostic mechanism.
5. The importer persists to PostgreSQL following the existing current + history table pattern.
6. `rest-java` (jOOQ-based) exposes read endpoints over the persisted state.

```
Consensus node (CLPR Service)
  → block stream (TransactionResult/Output + StateChanges: clpr_channel_value, clpr_connector_value,
    clpr_message_value/_key, clpr_ledger_configuration_value, clpr_endpoint_manifest_value)
  → importer block-to-record transformation (ClprXxxTransformer reads StateChangeContext, populates new
    CLPR-specific fields on RecordItem — there is no existing TransactionRecord/sidecar slot for this data)
  → importer TransactionHandlers (read the new RecordItem fields) + EntityListener
  → PostgreSQL (clpr_channel_pending_commitment, clpr_channel/_history, clpr_connector_pending_commitment,
    clpr_connector/_history, clpr_ledger_configuration/_history, clpr_endpoint_manifest/_history, clpr_message)
  → rest-java (read APIs)
```

`ClprEndpointPublicationTransactionBody` (field 91) and the `ClprEndpointManifestConstruction` singleton are
Hiero-internal (explicitly called out as "not part of the cross-ledger CLPR spec" in the proto docs) — the mirror
node only needs the _finalized_ `ClprEndpointManifest`, not the in-flight construction bookkeeping (see
[Non-Goals](#non-goals)).

## Transaction & Query Inventory

All CLPR `HederaFunctionality` values that are in scope for this design (verified against `basic_types.proto`,
`transaction.proto`, and `query.proto` in `hiero-consensus-node`). `ClprRedactMessage` (123) is deliberately
excluded — see [Non-Goals](#non-goals) and the [Feedback](#hip-1535-feedback--required-clarifications) section for
why it exists in the reference proto despite being a Rejected Idea in the HIP text. `ClprEndpointPublication` (127)
is also undocumented in the HIP text itself, but for a different reason (it's Hiero-internal) — see above.

| #   | `HederaFunctionality`           | `TransactionBody` / `Query` field    | What mirror node must persist                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| --- | ------------------------------- | ------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 117 | `ClprUpdateLedgerConfiguration` | `clprUpdateLedgerConfiguration` (tx) | Update the `clpr_ledger_configuration` singleton row (throttles, timestamp); close out prior history row.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| 118 | `ClprRegisterChannel`           | `clprRegisterChannel` (tx)           | Insert a row into `clpr_channel_pending_commitment` keyed by `ownership_commitment`; no `channel_id` is known yet.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| 119 | `ClprCompleteChannel`           | `clprCompleteChannel` (tx)           | Insert the new `clpr_channel` row in `ACTIVE` status, recording `channel_id`, `ownership_commitment` (carried forward, not cleared), `verifier_contract`, `verifier_fingerprint`, initial `trust_anchor`/`trust_anchor_id`, `channel_context`, and initial `endpoint_manifest_version` — populated via the corresponding `ClprCompleteChannelTransformer` from the `clpr_channel_value` state change, since these fields are verifier-derived. Per `ClprCompleteChannelHandler`, the `clpr_channel_pending_commitment` row is **not** deleted at this point — it remains in consensus state for the life of the Channel (see row 120 and Feedback). |
| 120 | `ClprCloseChannel`              | `clprCloseChannel` (tx)              | If no Channel exists yet for the supplied `ownership_commitment` (still pending/abandoned), mark the `clpr_channel_pending_commitment` row `deleted=true`. If a Channel exists, transition its status toward `CLOSING`/`DRAINED`/`CLOSED` — per `ClprCloseChannelHandler`, this path does **not** touch `clpr_channel_pending_commitment`, so a completed Channel's pending row is never cleaned up even once `CLOSED` (see Feedback).                                                                                                                                                                                                              |
| 121 | `ClprGetLedgerConfiguration`    | `clprGetLedgerConfiguration` (query) | Read-only; no persistence — served from the current `clpr_ledger_configuration` row.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| 122 | `ClprSubmitBundle`              | `clprSubmitBundle` (tx)              | Append dispatched `ClprMessage`/`ClprMessageReply`/`ClprControlMessage` entries to `clpr_message` (populated via `ClprSubmitBundleTransformer` from `clpr_message_value`/`clpr_message_key`, since payload contents come from the verifier, not the transaction body); `connector_id` is read directly for `ClprMessage` rows, left `null` for `ClprControlMessage` rows, and derived for `ClprMessageReply` rows by looking up the Data Message it replies to (see [Database Schema Design](#database-schema-design)); update `channel.next_message_id` / `received_message_id` / running hashes / status from `clpr_channel_value`.               |
| 124 | `ClprRegisterConnector`         | `clprRegisterConnector` (tx)         | Insert a row into `clpr_connector_pending_commitment` keyed by `commitment`; no `connector_id` is known yet.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| 125 | `ClprDeregisterConnector`       | `clprDeregisterConnector` (tx)       | Close out the `clpr_connector`/`clpr_connector_history` row and record `stake_recipient`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| 126 | `ClprCompleteConnector`         | `clprCompleteConnector` (tx)         | Mark the matching `clpr_connector_pending_commitment` row `deleted=true` immediately (per `ClprCompleteConnectorHandler`, unlike the Channel side) and insert the `clpr_connector` row keyed by `(channel_id, connector_id)` with `connector_contract`, `admin_key`, `locked_stake`.                                                                                                                                                                                                                                                                                                                                                                |
| 127 | `ClprEndpointPublication`       | `clpr_endpoint_publication` (tx)     | Hiero-internal, node-to-node protocol transaction — **out of scope** for the cross-ledger-facing tables; optionally worth ingesting only if the mirror node wants to expose per-node CLPR endpoint publication history as an operational/debugging aid.                                                                                                                                                                                                                                                                                                                                                                                             |
| 128 | `ClprGetEndpointManifest`       | `clprGetEndpointManifest` (query)    | Read-only; no persistence — served from the current `clpr_endpoint_manifest` row.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |

Two additional CLPR state singletons are visible in the block stream but not driven by their own dedicated
transaction: `clpr_endpoint_manifest_construction_value` (Hiero-internal, see Non-Goals) and the
`STATE_ID_CLPR_PENDING_COMMITMENTS` / `STATE_ID_CLPR_PENDING_CONNECTOR_COMMITMENTS` maps (keyed by the generic
`proto_bytes_key`/`proto_bytes_value` case — there is no dedicated `MapChangeKey`/`MapChangeValue` type for these,
unlike Channels/Connectors/Messages; see Feedback).

## Database Schema Design

Following the current + history pattern used for other long-lived, mutable Hiero entities (tokens, topics):
**Channel**, **Connector**, and **ledger configuration** get current + history table pairs
(their state mutates repeatedly over their lifetime and history has audit value per HIP-1535 §11). The
**endpoint manifest** is a network-wide singleton that also mutates over time, so it gets the same treatment. The
**message queue** is naturally append-only (each `(channel_id, message_id)` is written once and never mutated), so
it is modeled as a single table, not a current/history pair.

Column order within each table groups fixed 8-byte columns (`bigint`) first, then enums/booleans, then
variable-length columns (`bytea`/`varchar`/`int8range`) last, to avoid PostgreSQL padding waste from unfavorable
alignment (see https://www.enterprisedb.com/blog/rocks-and-sand).

Table and column definitions are identical between the `v1` and `v2` (Citus) migrations. The only difference is
that the `v2` migration additionally runs the `create_distributed_table`/`create_reference_table` calls shown at
the end of each SQL block below; `v1` does not.

Unrevealed Channel commitments — the commit phase, before `ClprCompleteChannel` reveals `channel_id` — are
persisted in a separate table, `clpr_channel_pending_commitment`. `clpr_channel` is keyed by `channel_id`, but per
the commit-reveal scheme `ClprRegisterChannel` submits only a
commitment hash — `channel_id` isn't revealed until `ClprCompleteChannel` — so `clpr_channel` cannot hold a row for
the commit-only phase. A minimal table keyed by the commitment hash is sufficient: per `clpr_register_channel.proto`/
`clpr_complete_channel.proto`, `ClprRegisterChannel` submits only `ownership_commitment = keccak256(channel_id ||
public_key)` (computed off-chain, so `channel_id`/`public_key` stay secret), and `ClprCompleteChannel` correlates
back to it by recomputing that same hash from the _revealed_ `channel_id`/`public_key` and doing a direct key
lookup in `STATE_ID_CLPR_PENDING_COMMITMENTS` — a plain set-membership check, with nothing else tying the two
transactions together. That means the map's value needs no richer structure than a bare marker, so
`clpr_channel_pending_commitment` only needs the commitment hash itself plus `created_timestamp` (see below).
`clpr_channel` itself only ever holds post-reveal rows, so `channel_id` is always known by the time a row is
written there — `PENDING` is removed from `clpr_channel_status` accordingly.

`ClprRegisterConnector`/`ClprCompleteConnector` use the same commit-reveal _commit_ mechanism
(`commitment = keccak256(connectorId || pubKey)`), persisted the same way in `clpr_connector_pending_commitment`
(see below). The _cleanup_ behavior is **not** symmetric with Channels, though: per `ClprCompleteConnectorHandler`,
the Connector's commitment is removed from `STATE_ID_CLPR_PENDING_CONNECTOR_COMMITMENTS` immediately at completion
(`commitmentStore.remove(...)`, step 9), unlike `ClprCompleteChannelHandler`, which leaves the Channel's commitment
in place indefinitely. So a Connector's pending row is marked `deleted=true` at `ClprCompleteConnector` time, while
a Channel's is not touched until (and unless) it's abandoned via `ClprCloseChannel`.

### 1. Channel Tables

```sql
-- add_clpr_channel_support.sql
create type clpr_channel_status as enum ('ACTIVE', 'PAUSED', 'CLOSING', 'DRAINED', 'CLOSED');

create table if not exists clpr_channel_pending_commitment
(
    created_timestamp    bigint   not null,
    deleted              boolean  not null default false,
    ownership_commitment bytea    not null,

    primary key (ownership_commitment)
);

create table if not exists clpr_channel
(
    acked_message_id           bigint                not null default 0,
    created_timestamp          bigint                not null,
    endpoint_manifest_version  bigint                not null default 0,
    next_message_id            bigint                not null default 1,
    received_message_id        bigint                not null default 0,
    verifier_contract_id       bigint,
    status                     clpr_channel_status   not null default 'ACTIVE',
    channel_context            bytea,
    channel_id                 bytea                 not null,
    chain_id                   varchar               not null,
    ownership_commitment       bytea                 not null,
    received_running_hash      bytea,
    sent_running_hash          bytea,
    timestamp_range            int8range             not null,
    trust_anchor               bytea,
    trust_anchor_id            bytea,
    verifier_fingerprint       bytea,

    primary key (channel_id)
);

create index if not exists clpr_channel__chain_id
    on clpr_channel (chain_id, created_timestamp);

create table if not exists clpr_channel_history
(
    like clpr_channel including defaults
);

create index if not exists clpr_channel_history__timestamp_range
    on clpr_channel_history using gist (timestamp_range);

select create_distributed_table('clpr_channel_pending_commitment', 'ownership_commitment');
select create_distributed_table('clpr_channel', 'channel_id');
select create_distributed_table('clpr_channel_history', 'channel_id');
```

The `chain_id` index supports both the [Chain API](#1-chain-api)'s `chain_id`+count aggregation and the
[Channels per Chain API](#2-channels-per-chain-api)'s `chain_id`-filtered, `created_timestamp`-ordered lookup.

### 2. Connector Tables

```sql
-- add_clpr_connector_support.sql
create table if not exists clpr_connector_pending_commitment
(
    created_timestamp  bigint   not null,
    deleted            boolean  not null default false,
    commitment         bytea    not null,

    primary key (commitment)
);

create table if not exists clpr_connector
(
    connector_contract_id    bigint      not null,
    created_timestamp        bigint      not null,
    in_flight_message_count  bigint      not null default 0,
    locked_stake             bigint      not null default 0,
    slash_count              bigint      not null default 0,
    admin_key                bytea,
    channel_id               bytea       not null,
    connector_id             bytea       not null,
    timestamp_range          int8range   not null,

    primary key (channel_id, connector_id)
);

create table if not exists clpr_connector_history
(
    like clpr_connector including defaults
);

create index if not exists clpr_connector_history__timestamp_range
    on clpr_connector_history using gist (timestamp_range);

select create_distributed_table('clpr_connector_pending_commitment', 'commitment');
select create_distributed_table('clpr_connector', 'channel_id', colocate_with => 'clpr_channel');
select create_distributed_table('clpr_connector_history', 'channel_id', colocate_with => 'clpr_channel');
```

### 3. Ledger Configuration and Endpoint Manifest (Singletons)

```sql
-- add_clpr_configuration_support.sql
create table if not exists clpr_ledger_configuration
(
    id                          bigint      not null default 1,  -- singleton row id
    max_gas_per_message         bigint,
    max_local_endpoints         bigint,
    max_message_payload_bytes   bigint,
    max_messages_per_bundle     bigint,
    max_peer_endpoints          bigint,
    max_queue_depth             bigint,
    max_sync_bytes              bigint,
    protocol_version            bigint      not null,
    chain_id                    varchar     not null,
    service_address             bytea,
    timestamp_range             int8range   not null,

    primary key (id)
);

create table if not exists clpr_ledger_configuration_history
(
    like clpr_ledger_configuration including defaults
);

create index if not exists clpr_ledger_configuration_history__timestamp_range
    on clpr_ledger_configuration_history using gist (timestamp_range);

create table if not exists clpr_endpoint_manifest
(
    id                bigint      not null default 1, -- singleton row id
    version           bigint      not null,
    endpoints         bytea,      -- serialized repeated ClprEndpoint, or normalized into a child table if queried directly
    service_address   bytea,
    timestamp_range   int8range   not null,

    primary key (id)
);

create table if not exists clpr_endpoint_manifest_history
(
    like clpr_endpoint_manifest including defaults
);

create index if not exists clpr_endpoint_manifest_history__timestamp_range
    on clpr_endpoint_manifest_history using gist (timestamp_range);

select create_reference_table('clpr_ledger_configuration');
select create_reference_table('clpr_ledger_configuration_history');
select create_reference_table('clpr_endpoint_manifest');
select create_reference_table('clpr_endpoint_manifest_history');
```

Both are network-wide singletons (one row each), so they use `create_reference_table` (replicated to every node for
fast local reads) rather than `create_distributed_table` — hash-distributing a single row would just pin it to one
shard anyway, with none of a reference table's replication benefit.

### 4. Message Queue (Append-Only)

```sql
-- add_clpr_message_support.sql
create type clpr_message_type as enum ('DATA', 'RESPONSE', 'CONTROL');
create type clpr_message_reply_status as enum
    ('SUCCESS', 'APPLICATION_ERROR', 'CONNECTOR_NOT_FOUND', 'CONNECTOR_UNDERFUNDED');

create table if not exists clpr_message
(
    consensus_timestamp  bigint                      not null,
    message_id           bigint                      not null,
    reply_to_message_id  bigint,
    message_type         clpr_message_type           not null,
    reply_status         clpr_message_reply_status,
    channel_id           bytea                       not null,
    connector_id         bytea,
    message_data         bytea,
    running_hash         bytea                       not null,
    sender               bytea,
    target_application   bytea,

    primary key (channel_id, message_id)
);

create index if not exists clpr_message__connector_id
    on clpr_message (connector_id, message_id);

select create_distributed_table('clpr_message', 'channel_id', colocate_with => 'clpr_channel');
```

**`connector_id` is not directly available for every message type.** Per `clpr_message.proto`: `ClprMessage`
(Data) carries `connector_id` directly. `ClprControlMessage` has no connector association at all — `connector_id`
is legitimately `null` for these rows. `ClprMessageReply` (Response) has **no `connector_id` field on the wire** —
only `message_id` (the Data Message it responds to), `status`, and `message_reply_data`. So when inserting a
Response row, the importer derives `connector_id` by looking up the Data Message it replies to (same `channel_id`,
`message_id = reply_to_message_id`) and copying its `connector_id` — that Data row is guaranteed to already exist,
since every Response is generated for a Data Message that was enqueued earlier in the same channel.

The `connector_id` index supports the [Messages per Connector API](#3-messages-per-connector-api)'s
`connector_id`-filtered, `message.id`-ordered lookup — safe to paginate this way since a Connector is bound to
exactly one Channel (`connector_id` is derived as `keccak256(channelId || publicKey || salt)`), so its messages
share that Channel's monotonic `message_id` sequence. No index on `consensus_timestamp` is needed since none of the
six scoped REST APIs filter or paginate by it. Distributing by `channel_id` makes `Messages per Channel` and
`Connectors per Channel` shard-local (both filter by `channel_id` directly). `Messages per Connector` (filters by
`connector_id`) and `Channels per Chain`/the Chain API (filter by `chain_id`) are not the distribution column, so
those scatter-gather across all shards regardless of colocation.

> **Distribution strategy.** CLPR tables have no natural Hiero `EntityId` to colocate against — `channel_id` and
> `connector_id` are protocol-level 32-byte values, not Hiero entity numbers. Since `channel_id` is the root of the
> natural parent-child relationship
> (Connectors and Messages both belong to exactly one Channel), `clpr_channel`/`clpr_channel_history` are
> hash-distributed by `channel_id`, and `clpr_connector(_history)`/`clpr_message` are hash-distributed by
> `channel_id` too, `colocate_with => 'clpr_channel'`, so per-channel queries stay shard-local.
> `clpr_channel_pending_commitment` and `clpr_connector_pending_commitment` have no `channel_id` at all, so each is
> distributed by its own key (`ownership_commitment`/`commitment`). `clpr_ledger_configuration(_history)`/
> `clpr_endpoint_manifest(_history)` are network-wide singletons, so they're `create_reference_table`s instead (see
> above).

## Importer Module Changes

### 1. Domain Models

New domain classes under `common/src/main/java/org/hiero/mirror/common/domain/clpr/` follow the same
`AbstractX` / `X` / `XHistory` pattern (`@Upsertable(history = true)` on the abstract base, current and history
subclasses each map to their own table) used by `AbstractToken`/`Token`/`TokenHistory`:

- **`ClprChannelPendingCommitment`**: a plain, non-history `@Entity` with id `ownershipCommitment`,
  `createdTimestamp`, and `deleted`. Inserted by `ClprRegisterChannelTransactionHandler`. Per
  `ClprCloseChannelHandler` in `hiero-consensus-node`, only `ClprCloseChannelTransactionHandler` ever marks a row
  `deleted=true`, and only for a still-pending (never-completed) commitment — `ClprCompleteChannelTransactionHandler`
  does **not** touch this table at all (see [Transaction & Query Inventory](#transaction--query-inventory) row 119
  and [Feedback](#hip-1535-feedback--required-clarifications)). Never has a history variant since there's no value
  in tracking intermediate states of a commit-only row.
- **`ClprChannel`** (+ history): `channelId`, `chainId`, `status`, `ownershipCommitment`, `verifierContractId`,
  `verifierFingerprint`, `trustAnchor`, `trustAnchorId`, `channelContext`, `endpointManifestVersion`,
  `nextMessageId`, `receivedMessageId`, `ackedMessageId`, `sentRunningHash`, `receivedRunningHash`,
  `createdTimestamp`, `timestampRange`. Per `ClprCompleteChannelHandler` in `hiero-consensus-node`,
  `ownershipCommitment` is stored on the real `ClprChannel` state record itself (not just the pending-commitment
  table) — it persists there for the Channel's entire lifetime.
- **`ClprConnectorPendingCommitment`**: a plain, non-history `@Entity` with id `commitment`, `createdTimestamp`, and
  `deleted`. Inserted by `ClprRegisterConnectorTransactionHandler`. Per `ClprCompleteConnectorHandler`, marked
  `deleted=true` immediately by `ClprCompleteConnectorTransactionHandler` — unlike
  `ClprChannelPendingCommitment`, there is no never-completed/abandoned-commitment cleanup path, since
  `ClprDeregisterConnectorHandler` only ever operates on an already-completed Connector.
- **`ClprConnector`** (+ history): composite id `(channelId, connectorId)`, `connectorContractId`, `adminKey`,
  `lockedStake`, `inFlightMessageCount`, `slashCount`, `createdTimestamp`, `timestampRange`.
- **`ClprLedgerConfiguration`** (+ history) and **`ClprEndpointManifest`** (+ history): singleton rows, fields as
  in the [schema](#database-schema-design) above.
- **`ClprMessage`**: a plain, non-history `@Entity` with composite id `(channelId, messageId)`, since the schema
  is append-only.

### 2. Transaction Handlers

One handler per new `TransactionType`, following the existing `@Named class extends AbstractTransactionHandler`
pattern (e.g. `TokenAirdropTransactionHandler`):

- `ClprRegisterChannelTransactionHandler`
- `ClprCompleteChannelTransactionHandler`
- `ClprCloseChannelTransactionHandler`
- `ClprSubmitBundleTransactionHandler`
- `ClprRegisterConnectorTransactionHandler`
- `ClprDeregisterConnectorTransactionHandler`
- `ClprCompleteConnectorTransactionHandler`
- `ClprUpdateLedgerConfigurationTransactionHandler`

Each new `TransactionType` constant must be added to `common/src/main/java/org/hiero/mirror/common/domain/transaction/TransactionType.java`,
keyed by the verified `HederaFunctionality` numeric values (117–120, 122, 124–126). `ClprRedactMessage` (123) is
intentionally excluded per [Non-Goals](#non-goals). `ClprEndpointPublication` (127) is Hiero-internal and may not
need a mirror-node-facing `TransactionType` if it's not ingested; see the
[Transaction & Query Inventory](#transaction--query-inventory).

For handlers whose fields come straight off the transaction body (e.g. `ClprRegisterChannel`'s
`ownership_commitment`, `ClprCompleteConnector`'s `connector_contract`/`admin_key`/`locked_stake`), processing is a
direct field mapping, same as any simple create-style transaction handler. For handlers whose resulting state is
verifier-derived (`ClprCompleteChannel`, `ClprSubmitBundle`, `ClprCloseChannel`'s terminal transitions), the handler
reads the new CLPR-specific fields the transformer added to `RecordItem` (see below) — not the transaction body,
and not the classic `TransactionRecord`, since neither carries this data. `ClprSubmitBundle` can dispatch multiple
messages in one transaction, so its handler calls the entity listener once per dispatched message. For a
`ClprMessageReply` entry, the handler looks up the Data Message's `connector_id` (same channel,
`message_id = reply.message_id`, already persisted earlier in the same or a prior bundle) before building the
`ClprMessage` domain object, since the reply payload itself carries no `connector_id` (see
[Database Schema Design](#database-schema-design)).

### 3. Block Stream Transformers and `RecordItem` Extensions

Following the existing `importer/src/main/java/org/hiero/mirror/importer/downloader/block/transformer/` pattern
(`AbstractBlockTransactionTransformer`, one `@Named` subclass per `TransactionType` that needs enrichment beyond
what the base class copies automatically), but with one difference from the existing transformers: since neither
`TransactionRecord` nor `TransactionSidecarRecord` has a CLPR-specific field or case (unlike e.g. `ContractStateChange`
or `TokenAirdrop`'s `newPendingAirdrops`), these transformers populate **new CLPR-specific fields added to
`RecordItem` itself** rather than the synthetic `TransactionRecord`:

- `ClprCompleteChannelTransformer` — reads the channel's `StateChangeContext` entry and populates `RecordItem`'s new
  CLPR channel fields with the verifier-assigned `trust_anchor`, `channel_context`, and `endpoint_manifest_version`.
- `ClprSubmitBundleTransformer` — reads the dispatched `clpr_message_value` entries from `StateChangeContext` and
  populates `RecordItem`'s new CLPR message field for the handler to expand into `clpr_message` rows; also reads the
  updated `clpr_channel_value` (queue positions, running hashes, status).
- `ClprCloseChannelTransformer` — reads the resulting channel status/queue state for the `CLOSING`/`DRAINED`/`CLOSED`
  transitions.

Transaction types with no verifier-derived enrichment (e.g. `ClprRegisterChannel`, `ClprRegisterConnector`,
`ClprCompleteConnector`, `ClprDeregisterConnector`, `ClprUpdateLedgerConfiguration`) need no dedicated transformer
and fall back to `DefaultTransformer`, the same as most existing transaction types.

### 4. Entity Listener Enhancement

Add to `importer/src/main/java/org/hiero/mirror/importer/parser/record/entity/EntityListener.java`, following the
existing `default void onX(X x) {}` pattern:

- `onClprChannel(ClprChannel)`
- `onClprChannelPendingCommitment(ClprChannelPendingCommitment)` — used both to insert the row at
  `ClprRegisterChannel` time and, via the same upsert-by-`ownershipCommitment` mechanism, to mark it `deleted=true`
  when `ClprCloseChannel` abandons a still-pending commitment — consistent with this codebase's `deleted` boolean
  convention, rather than a physical row delete, since `EntityListener` has no delete-oriented methods anywhere.
  Not called from `ClprCompleteChannel` (see row 119 of the inventory).
- `onClprConnector(ClprConnector)`
- `onClprConnectorPendingCommitment(ClprConnectorPendingCommitment)` — used both to insert the row at
  `ClprRegisterConnector` time and, via the same upsert-by-`commitment` mechanism, to mark it `deleted=true`
  immediately at `ClprCompleteConnector` time (see row 126 of the inventory).
- `onClprEndpointManifest(ClprEndpointManifest)`
- `onClprLedgerConfiguration(ClprLedgerConfiguration)`
- `onClprMessage(ClprMessage)`

### 5. Repository Classes

New `JpaRepository`-based repositories for `ClprChannelPendingCommitment`, `ClprChannel`,
`ClprConnectorPendingCommitment`, `ClprConnector`, `ClprLedgerConfiguration`, `ClprEndpointManifest`, and
`ClprMessage`, keyed by their respective (possibly composite) ids. Given the six REST APIs now scoped in
[REST API Implementation](#rest-api-implementation), `ClprChannelRepository` needs a `chain.id`-filtered lookup and
a distinct-`chain_id`-with-count aggregation for the Chain API (a `group by`, not a simple CRUD method);
`ClprMessageRepository` needs lookups by both `channel_id` and `connector_id`.

## REST API Implementation

Scoped to exactly six read APIs, following existing `rest-java` conventions (paginated, filterable):

1. Chain
2. Channels per chain
3. Messages per connector
4. Messages per channel
5. Pending channels
6. Connectors per channel

Ledger configuration and endpoint manifest read endpoints are explicitly **not** in scope for this iteration.

**Pagination note**: `channel_id` and `connector_id` are opaque, non-sequential 32-byte values chosen by the
registrant at commit time, with no meaningful ascending/descending order, so the Channels and Connectors list APIs
below paginate by `created_timestamp` (with the id as tiebreaker), not by the id itself. `message_id`, by contrast,
is a per-channel monotonically increasing, immutable sequence number (`next_message_id` in the schema), so the
Messages APIs safely paginate by `message.id` directly. `chain_id` is a free-form string claim (see Feedback item
4), so the Chain API paginates lexicographically by `chain.id` itself rather than by time.

### 1. Chain API

```
GET /api/v1/clpr/chains
```

There is no protocol-level chain registry (per HIP §4.2, _"A `ChainID` can be claimed by anyone"_ — see Feedback
item 4), so this is a mirror-node-side aggregation over `clpr_channel.chain_id`, not a queryable protocol resource.

Response format:

```json
{
  "chains": [
    {
      "chain_id": "eip155:1",
      "channel_count": 3
    }
  ],
  "links": {
    "next": "/api/v1/clpr/chains?chain.id=gt:eip155:1&limit=1"
  }
}
```

#### Query Parameters

| Parameter  | Type    | Description                        | Default | Validation                                                                     |
| ---------- | ------- | ---------------------------------- | ------- | ------------------------------------------------------------------------------ |
| `chain.id` | string  | Filter/paginate by `chain_id`      | none    | Supports `eq:`, `gt:`, `gte:`, `lt:`, `lte:` (lexicographic string comparison) |
| `limit`    | integer | Maximum number of chains to return | `25`    | Must be between 1 and 100                                                      |
| `order`    | string  | Sort order for results             | `asc`   | Must be either `asc` or `desc`                                                 |

**Examples:**

- `/api/v1/clpr/chains` — Get all known chains, alphabetically
- `/api/v1/clpr/chains?chain.id=gt:eip155:1&limit=10` — Get 10 chains after a given `chain_id`

### 2. Channels per Chain API

```
GET /api/v1/clpr/chains/{chainId}/channels
```

Response format:

```json
{
  "channels": [
    {
      "channel_id": "0x1a2b3c...",
      "chain_id": "eip155:1",
      "status": "ACTIVE",
      "verifier_contract_id": "0.0.456",
      "trust_anchor_id": "0x9f8e...",
      "created_timestamp": "1726874345.123456789",
      "timestamp": {
        "from": "1726874345.123456789",
        "to": null
      }
    }
  ],
  "links": {
    "next": "/api/v1/clpr/chains/eip155:1/channels?timestamp=lt:1726874345.123456789&limit=1"
  }
}
```

#### Query Parameters

| Parameter   | Type    | Description                            | Default | Validation                                                                                                                                                                     |
| ----------- | ------- | -------------------------------------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `status`    | string  | Filter by Channel status               | none    | One of `ACTIVE`, `PAUSED`, `CLOSING`, `DRAINED`, `CLOSED` (pending, unrevealed commitments aren't in `clpr_channel` — see the [Pending Channels API](#5-pending-channels-api)) |
| `timestamp` | string  | Filter/paginate by `created_timestamp` | none    | Supports `eq:`, `gt:`, `gte:`, `lt:`, `lte:`; seconds.nanoseconds format                                                                                                       |
| `limit`     | integer | Maximum number of Channels to return   | `25`    | Must be between 1 and 100                                                                                                                                                      |
| `order`     | string  | Sort order for results                 | `desc`  | Must be either `asc` or `desc`                                                                                                                                                 |

**Examples:**

- `/api/v1/clpr/chains/eip155:1/channels` — Get all Channels claiming `chain_id=eip155:1`, newest first
- `/api/v1/clpr/chains/eip155:1/channels?status=eq:ACTIVE&limit=10` — Get first 10 active Channels on that chain

### 3. Messages per Connector API

```
GET /api/v1/clpr/connectors/{connectorId}/messages
```

`connector_id` is derived as `keccak256(channelId || publicKey || salt)`, so it's effectively unique on its own
without needing `channelId` in the path. Returns Data Messages the Connector authorized directly and the Response
Messages addressed to it (both have a `connector_id` on `clpr_message` — see
[Database Schema Design](#database-schema-design)); Control Messages never appear here, since they have no
Connector association.

Response format:

```json
{
  "messages": [
    {
      "message_id": 5,
      "channel_id": "0x1a2b3c...",
      "connector_id": "0x4d5e6f...",
      "type": "DATA",
      "sender": "0x4d5e6f...",
      "target_application": "0x7788...",
      "message_data": "0xdeadbeef",
      "reply_status": null,
      "reply_to_message_id": null,
      "running_hash": "0xaa11...",
      "timestamp": "1726874345.123456789"
    }
  ],
  "links": {
    "next": "/api/v1/clpr/connectors/0x4d5e6f.../messages?message.id=lt:5&limit=1"
  }
}
```

Payload bytes (`message_data`) are returned opaque/hex-encoded per the [Non-Goals](#non-goals) above.

#### Query Parameters

| Parameter    | Type    | Description                          | Default | Validation                                                     |
| ------------ | ------- | ------------------------------------ | ------- | -------------------------------------------------------------- |
| `message.id` | integer | Filter/paginate by `message_id`      | none    | Supports `eq:`, `gt:`, `gte:`, `lt:`, `lte:`; positive integer |
| `type`       | string  | Filter by message type               | none    | One of `DATA`, `RESPONSE`, `CONTROL`                           |
| `limit`      | integer | Maximum number of messages to return | `25`    | Must be between 1 and 100                                      |
| `order`      | string  | Sort order for results               | `desc`  | Must be either `asc` or `desc`                                 |

**Examples:**

- `/api/v1/clpr/connectors/0x4d5e6f.../messages` — Get all messages handled by a Connector, newest first
- `/api/v1/clpr/connectors/0x4d5e6f.../messages?type=eq:DATA&limit=10` — Get first 10 Data messages

### 4. Messages per Channel API

```
GET /api/v1/clpr/channels/{channelId}/messages
```

Response format:

```json
{
  "messages": [
    {
      "message_id": 5,
      "connector_id": "0x4d5e6f...",
      "type": "DATA",
      "sender": "0x4d5e6f...",
      "target_application": "0x7788...",
      "message_data": "0xdeadbeef",
      "reply_status": null,
      "reply_to_message_id": null,
      "running_hash": "0xaa11...",
      "timestamp": "1726874345.123456789"
    }
  ],
  "links": {
    "next": "/api/v1/clpr/channels/0x1a2b3c.../messages?message.id=lt:5&limit=1"
  }
}
```

`connector_id` is `null` for Control Messages, which have no Connector association (see
[Database Schema Design](#database-schema-design)).

Payload bytes (`message_data`) are returned opaque/hex-encoded per the [Non-Goals](#non-goals) above.

#### Query Parameters

| Parameter    | Type    | Description                          | Default | Validation                                                     |
| ------------ | ------- | ------------------------------------ | ------- | -------------------------------------------------------------- |
| `message.id` | integer | Filter/paginate by `message_id`      | none    | Supports `eq:`, `gt:`, `gte:`, `lt:`, `lte:`; positive integer |
| `type`       | string  | Filter by message type               | none    | One of `DATA`, `RESPONSE`, `CONTROL`                           |
| `limit`      | integer | Maximum number of messages to return | `25`    | Must be between 1 and 100                                      |
| `order`      | string  | Sort order for results               | `desc`  | Must be either `asc` or `desc`                                 |

**Examples:**

- `/api/v1/clpr/channels/0x1a2b3c.../messages` — Get all messages on a Channel, newest first
- `/api/v1/clpr/channels/0x1a2b3c.../messages?type=eq:DATA&limit=10` — Get first 10 Data messages
- `/api/v1/clpr/channels/0x1a2b3c.../messages?message.id=lt:5&limit=5` — Get 5 messages with id less than 5

### 5. Pending Channels API

```
GET /api/v1/clpr/channels/pending
```

Lists `clpr_channel_pending_commitment` rows — Channels registered (commit phase) but not yet completed (reveal
phase). Per [Database Schema Design](#database-schema-design), `channel_id` is never known for these
rows; they're keyed only by `ownership_commitment`. `deleted=true` rows (abandoned via `ClprCloseChannel`) are
excluded by default, consistent with this codebase's `deleted` boolean convention.

Response format:

```json
{
  "pending_channels": [
    {
      "ownership_commitment": "0x7ab3f1...",
      "created_timestamp": "1726874345.123456789"
    }
  ],
  "links": {
    "next": "/api/v1/clpr/channels/pending?timestamp=lt:1726874345.123456789&limit=1"
  }
}
```

#### Query Parameters

| Parameter   | Type    | Description                            | Default | Validation                                   |
| ----------- | ------- | -------------------------------------- | ------- | -------------------------------------------- |
| `timestamp` | string  | Filter/paginate by `created_timestamp` | none    | Supports `eq:`, `gt:`, `gte:`, `lt:`, `lte:` |
| `limit`     | integer | Maximum number of rows to return       | `25`    | Must be between 1 and 100                    |
| `order`     | string  | Sort order for results                 | `desc`  | Must be either `asc` or `desc`               |

**Examples:**

- `/api/v1/clpr/channels/pending` — Get all pending channel commitments, newest first
- `/api/v1/clpr/channels/pending?limit=10` — Get first 10 pending commitments

### 6. Connectors per Channel API

```
GET /api/v1/clpr/channels/{channelId}/connectors
```

Response format:

```json
{
  "connectors": [
    {
      "connector_id": "0x4d5e6f...",
      "channel_id": "0x1a2b3c...",
      "connector_contract_id": "0.0.789",
      "admin_key": {
        "_type": "ED25519",
        "key": "0x302a300506032b6570032100e5b2…"
      },
      "locked_stake": 100000000,
      "in_flight_message_count": 2,
      "slash_count": 0,
      "created_timestamp": "1726874345.123456789"
    }
  ],
  "links": {
    "next": "/api/v1/clpr/channels/0x1a2b3c.../connectors?timestamp=lt:1726874345.123456789&limit=1"
  }
}
```

#### Query Parameters

| Parameter   | Type    | Description                            | Default | Validation                                   |
| ----------- | ------- | -------------------------------------- | ------- | -------------------------------------------- |
| `timestamp` | string  | Filter/paginate by `created_timestamp` | none    | Supports `eq:`, `gt:`, `gte:`, `lt:`, `lte:` |
| `limit`     | integer | Maximum number of Connectors to return | `25`    | Must be between 1 and 100                    |
| `order`     | string  | Sort order for results                 | `desc`  | Must be either `asc` or `desc`               |

**Examples:**

- `/api/v1/clpr/channels/0x1a2b3c.../connectors` — Get all Connectors on a Channel, newest first
- `/api/v1/clpr/channels/0x1a2b3c.../connectors?limit=10` — Get first 10 Connectors

## HIP-1535 Feedback / Required Clarifications

These are concrete gaps found by cross-referencing the HIP-1535 text against the actual protobuf definitions
currently in `hiero-ledger/hiero-consensus-node` (`hapi/hedera-protobuf-java-api/src/main/proto/`), not general
process feedback:

1. **The HIP's own text is stale regarding message redaction, and the proto should be reconciled with it.**
   HIP-1535 §10.6 states _"Values `123` and `127` are reserved and intentionally not assigned here; an earlier
   internal message-redaction mechanism occupied them and has since been removed from the specification"_, and
   lists message redaction under **Rejected Ideas** with a pointer to ADR
   `2026-08-01-remove-message-redaction.md`. However, the actual `basic_types.proto` in the reference
   implementation still assigns `ClprRedactMessage = 123` as a live `HederaFunctionality` value, `transaction.proto`
   wires `clprRedactMessage` into `TransactionBody` field 87, and `clpr_redact_message.proto`,
   `ClprRedactedMessage`, and `ClprMessageReplyStatus.REDACTED` all exist and are fully specified in the state
   protos. **This design follows the HIP text** and excludes redaction entirely (see
   [Non-Goals](#non-goals)) — but the proto/HIP mismatch should still be raised with the HIP authors and
   `hiero-consensus-node` maintainers so the dead proto surface can either be removed or the HIP updated to
   reflect that it shipped after all. `ClprEndpointPublication = 127` is unrelated to redaction despite sharing a
   footnote in the HIP — it is a legitimate, separate, Hiero-internal transaction (see
   [Architecture](#architecture)).
2. **`ClprSubmitBundleTransactionBody.endpoint_node_id` and `.endpoint_signature` are marked `deprecated = true`**
   in the proto with no equivalent note in the HIP's §10.3 spec text. The HIP still describes `endpoint_node_id`
   as required, node-signed authority for bundle submission. Please clarify the current authority model for who
   may submit a bundle now that these fields are deprecated — this affects whether the mirror node should still
   record a submitting-endpoint attribution on `clpr_message`/`clpr_channel` rows.
3. **`ClprLedgerConfiguration.endpoints` is `deprecated = true`** (moved to the separate `ClprEndpointManifest`),
   which HIP §4.3 does describe, but the deprecation annotation and its issue-tracker pointer ("see issue: remove
   ConfigUpdate endpoint propagation + PeerEndpointRosterEntry") aren't referenced from the HIP text itself. Not
   blocking, but worth linking so implementers don't accidentally read/write the deprecated field.
4. **No explicit mirror-node-facing identifier scheme is specified for querying by chain/channel.** HIP-1535 never
   defines a canonical way to enumerate all Channels for a given `chain_id` at the protocol level (Channels are
   permissionless and keyed only by an opaque 32-byte ID chosen by the registrant) — the mirror node can index
   `chain_id` from the Channel's stored peer configuration once `ACTIVE`, but there is no protocol-level channel
   _discovery_ mechanism to cross-check completeness against (e.g. "how many Channels currently claim
   `chain_id = eip155:1`"). This is fine for indexing what actually happened on-ledger, but should be called out
   so downstream consumers don't assume the mirror node can validate a Channel's `chain_id` claim (per HIP §4.2:
   _"A `ChainID` can be claimed by anyone"_).

## Testing Strategy

### 1. Unit Tests

- `TransactionHandler` tests per CLPR transaction type, covering success and failure record items.
- Domain object round-trip tests (`ClprChannel`, `ClprConnector`, `ClprMessage` builders/history transitions).
- `BlockTransactionTransformer` tests verifying verifier-derived fields (`trust_anchor`, `channel_context`,
  dispatched message contents) are picked up correctly from `StateChangeContext` rather than guessed from the
  transaction body.

### 2. Integration Tests

- Full Channel lifecycle: `registerChannel` → `completeChannel` → `submitBundle` (multiple) → `closeChannel` →
  drain to `CLOSED`, asserting `clpr_channel`/`clpr_channel_history` rows at each transition.
- Full Connector lifecycle: `registerConnector` → `completeConnector` → message dispatch affecting
  `in_flight_message_count`/`slash_count` → `deregisterConnector`.
- Database migration tests for the new tables, including the `v2` Citus distribution/reference-table calls (see
  [Database Schema Design](#database-schema-design)).

### 3. Acceptance Tests

Gated on CLPR actually being enabled and testable against a live/dev network with a deployed verifier (e.g. a
Hiero-to-Hiero test verifier, referenced in the `clpr-hiero` fork's `feat: Endpoint manifest Hiero to Hiero tests`
work):

```gherkin
Feature: CLPR Channel and Connector Lifecycle

  Scenario: Channel lifecycle through the mirror node
    Given I register a CLPR channel commitment
    And I complete the channel with a valid verifier and peer proof
    And the mirror node processes the transactions
    Then I query mirror node REST API for the channel and it reports status "ACTIVE"
    When I submit a bundle containing one data message
    And the mirror node processes the transactions
    Then I query mirror node REST API for the channel's messages and receive 2 entries (data + response)
    When I close the channel and it drains to CLOSED
    Then I query mirror node REST API for the channel and it reports status "CLOSED"
```

## Migration Strategy

Since CLPR introduces brand-new transaction types with no prior data and mirror-node support ships in lockstep with
the consensus-node release that enables CLPR, there is no phased rollout or backward-compatibility concern (no
feature flag, no mixed-version gating) — only the schema migrations needed to create the new tables.

### 1. Database Migrations

- New Flyway migrations under `importer/src/main/resources/db/migration/{v1,v2}` for
  `clpr_channel_pending_commitment`, `clpr_channel(_history)`, `clpr_connector_pending_commitment`,
  `clpr_connector(_history)`, `clpr_ledger_configuration(_history)`, `clpr_endpoint_manifest(_history)`, and
  `clpr_message`, following the
  append-only migration convention (never edit a merged migration).
- The `v2` (Citus) migrations include the `create_distributed_table`/`create_reference_table` calls specified in
  [Database Schema Design](#database-schema-design).

### 2. Performance Considerations

- `ClprSubmitBundle` can enqueue up to `MaxMessagesPerBundle` messages per transaction — batch-insert
  `clpr_message` rows within a single `RecordItem`'s processing rather than issuing one insert per message.
- The message table has no natural TTL/archival policy defined by the protocol; large, long-lived Channels could
  accumulate very large `clpr_message` tables. Partitioning by `channel_id` or `consensus_timestamp` should be
  evaluated once real message volumes from a production CLPR deployment are known.

## Monitoring

### Logging

- Log CLPR transaction processing at the same level as other transaction types; log a `WARN` if a
  `BlockTransactionTransformer` finds no matching state change for a successful CLPR transaction that requires
  verifier-derived enrichment, since this would indicate either a mirror-node bug or an unexpected consensus-node
  behavior change.
- Log rejected/unrecognized CLPR `TransactionBody` cases (e.g. a future protocol version's new Control Message
  variant) rather than silently dropping them, mirroring the HIP's own "MUST reject rather than skip" philosophy
  for forward compatibility (§6.1).

## Non-Functional Requirements

### 1. Performance Requirements

- CLPR transaction ingestion must not add disproportionate per-transaction overhead relative to other native
  services already processed at consensus-node peak TPS.
- Read APIs for Channel/Connector/message queries should target a p95 latency under 500ms, consistent with
  comparable existing REST endpoints.

### 2. Scalability Requirements

- `clpr_message` must support efficient pagination by `(channel_id, message_id)` for Channels that accumulate a
  large message history.
- Per the Citus (`v2`) distribution strategy in [Database Schema Design](#database-schema-design), only queries
  filtered by `channel_id` (Connectors per Channel, Messages per Channel) are shard-local; Messages per Connector
  and Channels per Chain/the Chain API filter by `connector_id`/`chain_id` instead, so those scatter-gather across
  shards regardless of colocation.

### 3. Reliability Requirements

- CLPR ingestion must maintain the same reliability guarantees (no dropped/duplicated records, no partial-commit
  states) as existing transaction and state-change processing.
- Because several fields are sourced from block-stream state changes rather than the transaction body, ingestion
  must fail loudly (not silently persist incomplete rows) if an expected state change is missing for a successful
  CLPR transaction.

## Proposed Follow-up Implementation Tasks

**These are proposals only — no GitHub issues have been created.** Scoping, sequencing, and milestone assignment
depend on the [HIP-1535 clarifications](#hip-1535-feedback--required-clarifications) above.

1. **DB schema migration for CLPR core tables.** Flyway migrations (`v1` and `v2`) for
   `clpr_channel_pending_commitment`, `clpr_channel(_history)`, `clpr_connector_pending_commitment`,
   `clpr_connector(_history)`, `clpr_ledger_configuration(_history)`, and `clpr_endpoint_manifest(_history)`,
   including the `v2` Citus `create_distributed_table`/`create_reference_table` calls from
   [Database Schema Design](#database-schema-design).
2. **DB schema migration for `clpr_message`.** Separate from (1) since it's a different table shape
   (append-only, no history pair) and highest-volume table; include indexing/partitioning strategy.
3. **Importer: Channel lifecycle transaction handlers and transformers.** `ClprRegisterChannel`,
   `ClprCompleteChannel`, `ClprCloseChannel` handlers plus the `BlockTransactionTransformer`s for verifier-derived
   Channel fields.
4. **Importer: Connector lifecycle transaction handlers.** `ClprRegisterConnector`, `ClprCompleteConnector`,
   `ClprDeregisterConnector` handlers.
5. **Importer: bundle/message ingestion.** `ClprSubmitBundleTransactionHandler` plus `ClprSubmitBundleTransformer`
   that expands a bundle's verifier-dispatched messages into `clpr_message` rows; depends on (2) and (3).
6. **Importer: ledger configuration and endpoint manifest ingestion.** `ClprUpdateLedgerConfigurationTransactionHandler`
   plus handling for the endpoint manifest singleton.
7. **REST API: the six read endpoints scoped in [REST API Implementation](#rest-api-implementation)** — Chain,
   Channels per chain, Messages per connector, Messages per channel, Pending channels, and Connectors per channel.
8. **Acceptance tests for the CLPR Channel/Connector lifecycle**, gated on a working verifier and a
   CLPR-enabled test network being available to the acceptance suite.
9. **Monitor support**, if warranted, for synthetic CLPR transaction generation/validation, following the
   pattern of the existing `monitor` module for other transaction types.
