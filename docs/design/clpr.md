# HIP-1535 CLPR Mirror Node Support - Design Document

## Overview

This design document outlines the implementation plan for supporting [HIP-1535](https://github.com/hiero-ledger/hiero-improvement-proposals/blob/main/HIP/hip-1535.md)
CLPR ("Clipper") in the Hiero mirror node. CLPR is a native Hiero service that lets a Hiero network exchange
ordered, reliable messages with peer ledgers (Hiero or non-Hiero) without a bridge or intermediary validator set.
The implementation will enable ingestion, storage, and querying of CLPR on-ledger state — Channels, Connectors,
the message queue, and ledger/endpoint configuration.

## Goals

- Ingest all new CLPR transaction types and persist their resulting state to the database (Channels, Connectors,
  ledger configuration, endpoint manifest, and the message queue).
- Model Channel and Connector state with full history, consistent with how other long-lived, mutable Hiero
  entities (tokens, topics, schedules) are tracked in this codebase.
- Model the CLPR message queue (Data/Response/Control payloads) as an append-only ledger suitable for audit and
  reconciliation.
- Expose CLPR state through the mirror node's read APIs so application developers, Connector Operators, and
  auditors can inspect Channel/Connector health and message history without running their own node.
- Capture the response-code and signal fields (`ClprChannelStatus`, `ClprMessageReplyStatus`, slash counts) needed
  to reconstruct the protocol's economic and lifecycle behavior.

## Non-Goals

- Verifying CLPR state proofs, bundle payloads, or trust anchor rotations — this is Hiero consensus-node and
  verifier-contract responsibility. The mirror node only records what the consensus node already decided.
- Acting as a CLPR endpoint, relaying bundles, or participating in the sync protocol.
- Indexing the Hiero-internal, non-cross-ledger mechanisms that never reach consensus state in a portable form:
  `ClprEndpointManifestConstruction` bookkeeping beyond the finalized manifest, and `ClprPeerEndpoints` (explicitly
  node-local, non-consensus state per its proto doc comment).
- Decoding or interpreting `bundle_payload`, verifier proof bytes, or application `message_data`/`message_reply_data`
  — these are opaque per HIP-1535 ("Applications MUST treat all cross-ledger payloads ... as untrusted input");
  the mirror node stores them as opaque bytes only.
- Rosetta, gRPC, GraphQL, or web3 support for CLPR — scoped to REST (`rest-java`) only for this iteration.
- Message redaction (`ClprRedactMessage`). HIP-1535 states this mechanism was removed from the specification under
  Rejected Ideas; this design follows the HIP text and does not persist or expose it, despite the reference
  implementation's proto still defining it (see [Consensus-Node Assumptions](#consensus-node-assumptions)).

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

## Architecture

CLPR transactions flow through the mirror node's existing, single transaction-processing pipeline — there is no
separate ingestion path for CLPR:

1. Every CLPR `TransactionBody` case (`clprRegisterChannel`, `clprCompleteChannel`, `clprCloseChannel`,
   `clprSubmitBundle`, `clprRegisterConnector`, `clprDeregisterConnector`, `clprCompleteConnector`,
   `clprUpdateLedgerConfiguration`) gets a dedicated `TransactionHandler`, the same as any other transaction type
   (see [Importer Module Changes](#importer-module-changes)). These eight cases aren't the only source of CLPR
   state changes, though — see point 5 below.
2. **Verifier-derived fields (e.g. `ClprCompleteChannel`'s `trust_anchor`/`channel_context`, `ClprSubmitBundle`'s
   dispatched messages) only exist as `state_changes.proto` `StateChange` entries** — not in
   `transaction_receipt.proto`, `transaction_record.proto`, the sidecar proto, or `RecordItem`
   (`common/src/main/java/org/hiero/mirror/common/domain/transaction/RecordItem.java`, which wraps those same
   classic protos), so a `TransactionHandler` can't read them off the transaction body or `TransactionRecord`. The
   fix: add new CLPR-specific fields directly to `RecordItem` (an importer-internal class, not a strict proto
   mirror — it already carries non-proto fields like `hookParent`), populated by a `ClprXxxTransformer` per CLPR
   transaction type that reads `StateChangeContext` at build time; the corresponding `TransactionHandler` then
   reads those fields directly. Since CLPR has no legacy record-stream carrier to begin with, this design assumes
   block-stream-only delivery and builds no separate record-stream path.
3. The importer persists to PostgreSQL following the existing current + history table pattern.
4. `rest-java` (jOOQ-based) exposes read endpoints over the persisted state.
5. **CLPR state changes are not confined to the eight `TransactionBody` cases above.** Three real exceptions exist
   in `hiero-consensus-node`, and they don't all need the same fix:

   - **`sendMessage`** is a system-contract call (`ClprSystemContract`, address `0x16e`/`0.0.366`) made inside a
     `ContractCall` or `EthereumTransaction`. `SendMessageCall.execute()` calls `ClprServiceApi.sendMessage(...)`
     directly, synchronously, inside that same transaction's handling — no child transaction is dispatched — so
     its resulting state changes (outbound Data message, Channel `next_message_id`/`sent_running_hash`, Connector
     `in_flight_message_count`) land in that same transaction's own block-stream `StateChanges` entry. This is a
     **mirror-node-only fix**: CLPR state changes carry their own dedicated, self-describing `StateIdentifier`s
     (`STATE_ID_CLPR_MESSAGE_QUEUE`/`STATE_ID_CLPR_CHANNELS`/`STATE_ID_CLPR_CONNECTORS`), not raw, anonymous
     contract storage — so no detection of which contract was called is needed. The fix is to add these three
     `StateIdentifier` cases to `StateChangeContext`'s constructor switch, with accessors for each, and have the
     existing `ContractCall`/`EthereumTransaction` handling also read them when present. No new tables — the
     writes land in `clpr_message`/`clpr_channel`/`clpr_connector`, which already exist; `sendMessage` is simply a
     second source feeding them, alongside `ClprSubmitBundle`.
   - **The ledger configuration and endpoint manifest singletons** can both be written with no transaction to
     anchor to at all — a consensus-node gap, not a mirror-node problem; see
     [Consensus-Node Assumptions](#consensus-node-assumptions).

```
Consensus node (CLPR Service)
  → block stream (TransactionResult/Output + StateChanges: clpr_channel_value, clpr_connector_value,
    clpr_message_value/_key, clpr_ledger_configuration_value)
  → importer block-to-record transformation (ClprXxxTransformer reads StateChangeContext, populates new
    CLPR-specific fields on RecordItem — there is no existing TransactionRecord/sidecar slot for this data)
  → importer TransactionHandlers (read the new RecordItem fields) + EntityListener
  → PostgreSQL (clpr_channel_pending_commitment/_history, clpr_channel/_history,
    clpr_connector_pending_commitment/_history, clpr_connector/_history, clpr_ledger_configuration,
    clpr_message)
  → rest-java (read APIs)
```

This diagram covers the eight transaction-anchored cases, plus `sendMessage` (point 5 above) once its
`StateChangeContext` extension lands. `clpr_endpoint_manifest_value` is deliberately absent: the per-round
reconciler that produces it has no transaction to anchor to, so none of this pipeline reaches it today (point 5).
`clpr_endpoint_manifest` stays in the schema design below regardless, since the finalized manifest still
needs to be queryable once ingestion is unblocked upstream.

`ClprEndpointPublicationTransactionBody` (field 91) and the `ClprEndpointManifestConstruction` singleton are a
separate, Hiero-internal concern (explicitly called out as "not part of the cross-ledger CLPR spec" in the proto
docs) — the mirror node only needs the _finalized_ `ClprEndpointManifest`, not the in-flight construction
bookkeeping (see [Non-Goals](#non-goals)).

## Transaction & Query Inventory

All CLPR `HederaFunctionality` values that are in scope for this design (verified against `basic_types.proto`,
`transaction.proto`, and `query.proto` in `hiero-consensus-node`). `ClprRedactMessage` (123) is deliberately
excluded — see [Non-Goals](#non-goals) and [Consensus-Node Assumptions](#consensus-node-assumptions) for
why it exists in the reference proto despite being a Rejected Idea in the HIP text. `ClprEndpointPublication` (127)
is also undocumented in the HIP text itself, but for a different reason (it's Hiero-internal) — see above.

| #   | `HederaFunctionality`           | `TransactionBody` / `Query` field    | What mirror node must persist                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| --- | ------------------------------- | ------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 117 | `ClprUpdateLedgerConfiguration` | `clprUpdateLedgerConfiguration` (tx) | Insert a new `clpr_ledger_configuration` row keyed by this transaction's own `consensus_timestamp` (throttles, protocol_version, chain_id, etc.).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| 118 | `ClprRegisterChannel`           | `clprRegisterChannel` (tx)           | Insert a row into `clpr_channel_pending_commitment` keyed by `ownership_commitment`; no `channel_id` is known yet.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| 119 | `ClprCompleteChannel`           | `clprCompleteChannel` (tx)           | Insert the new `clpr_channel` row in `ACTIVE` status from the `clpr_channel_value` state change (the complete `ClprChannel` object `ClprCompleteChannelHandler` builds — `chain_id`, `status`, `verifier_contract`, `verifier_fingerprint`, `trust_anchor`/`trust_anchor_id`, `channel_context`, `endpoint_manifest`/`endpoint_manifest_version`, `peer_throttles`, `peer_config_timestamp`, and fresh queue-state defaults), populated via the corresponding `ClprCompleteChannelTransformer` — plus `public_key`/`signature`/`signature_scheme`/`config_proof_bytes`/`endpoint_manifest_proof_bytes` directly off this transaction's body, since none of the five exist on real `ClprChannel` state (see [Database Schema Design](#database-schema-design)). Per `ClprCompleteChannelHandler`, the real consensus state leaves `clpr_channel_pending_commitment`'s entry untouched indefinitely (see row 120). The mirror node still recomputes `ownership_commitment` from this transaction's own fields (self-contained, not reliant on consensus state) to delete that row from `clpr_channel_pending_commitment` and archive it to `clpr_channel_pending_commitment_history`, so the Pending Channels API excludes it by simple row absence. |
| 120 | `ClprCloseChannel`              | `clprCloseChannel` (tx)              | If no Channel exists yet for the supplied `ownership_commitment` (still pending/abandoned), delete the `clpr_channel_pending_commitment` row and archive it to `clpr_channel_pending_commitment_history` with `deleted=true`. If a Channel exists, transition its status toward `CLOSING`/`DRAINED`/`CLOSED` — per `ClprCloseChannelHandler`, this path does **not** touch `clpr_channel_pending_commitment` in real consensus state; the mirror node already removed that row itself at completion time (see row 119), so there's nothing left to reconcile here.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| 122 | `ClprSubmitBundle`              | `clprSubmitBundle` (tx)              | Append dispatched `ClprMessage`/`ClprControlMessage` entries, and `ClprMessageReply` entries this ledger itself generated answering inbound Data, to `clpr_message` (populated via `ClprSubmitBundleTransformer` from `clpr_message_value`/`clpr_message_key` PUT changes, since payload contents come from the verifier, not the transaction body); `connector_id` is read directly for `ClprMessage` rows, left `null` for `ClprControlMessage` rows, and left `null` for generated `ClprMessageReply` rows too until a consensus-node change makes it available (see [Database Schema Design](#database-schema-design)). A reply confirming this ledger's own earlier outbound Data arrives as a `clpr_message_value` deletion of that Data entry, not a new row (see [Database Schema Design](#database-schema-design)); update `channel.next_message_id` / `received_message_id` / `acked_message_id` / running hashes / status from `clpr_channel_value`.                                                                                                                                                                                                                                                                                    |
| 124 | `ClprRegisterConnector`         | `clprRegisterConnector` (tx)         | Insert a row into `clpr_connector_pending_commitment` keyed by `commitment`; no `connector_id` is known yet.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| 125 | `ClprDeregisterConnector`       | `clprDeregisterConnector` (tx)       | Mark the `clpr_connector`/`clpr_connector_history` row `deleted=true` and record `stake_recipient_id` from this transaction's own body. Real consensus state removes the Connector from its map entirely (`ClprDeregisterConnectorHandler`'s `connectorStore.remove(key)`) — `stake_recipient` is never part of `ClprConnector` state, only a one-time transfer destination on the transaction body — so this is a mirror-node-only soft-delete, the same pattern as every other "closed" entity in this design.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| 126 | `ClprCompleteConnector`         | `clprCompleteConnector` (tx)         | Delete the matching `clpr_connector_pending_commitment` row and archive it to `clpr_connector_pending_commitment_history` (matching the real consensus-state removal in `ClprCompleteConnectorHandler`) and insert the `clpr_connector` row keyed by `(channel_id, connector_id)` with `connector_contract`, `admin_key`, `locked_stake`, plus `public_key`/`signature_scheme`/`signature`/`salt` directly off this transaction's body, since none of the four exist on real `ClprConnector` state (see [Database Schema Design](#database-schema-design)).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| 127 | `ClprEndpointPublication`       | `clpr_endpoint_publication` (tx)     | Hiero-internal, node-to-node protocol transaction — **out of scope** for the cross-ledger-facing tables; optionally worth ingesting only if the mirror node wants to expose per-node CLPR endpoint publication history as an operational/debugging aid.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |

## Database Schema Design

**Channel** and **Connector** get current + history table pairs (their state mutates repeatedly over their
lifetime and history has audit value per HIP-1535). **Ledger configuration** and the **endpoint manifest** are
each a single, append-only table keyed by `consensus_timestamp`: every update is a complete, self-sufficient
value, so the latest row is simply the one with the highest `consensus_timestamp`, with no separate history pair
needed. The **message queue** is likewise append-only (each `(channel_id, message_id)` is written once and never
mutated), so it too is modeled as a single table, not a current/history pair.

Column order within each table groups fixed 8-byte columns (`bigint`) first, then enums/booleans, then
variable-length columns (`bytea`/`varchar`/`int8range`) last, to avoid PostgreSQL padding waste from unfavorable
alignment (see https://www.enterprisedb.com/blog/rocks-and-sand).

Table and column definitions are identical between the `v1` and `v2` (Citus) migrations. For every table except
`clpr_channel_pending_commitment`/`clpr_connector_pending_commitment`, `clpr_ledger_configuration`, and
`clpr_endpoint_manifest` (which all stay plain, undistributed tables in both profiles — see the relevant subsections
below), the `v2` migration additionally runs the `create_distributed_table`
calls shown at the end of each SQL block; `v1` does not. `clpr_message` also differs in how its partitions get
created between profiles — see [Message Queue](#4-message-queue-append-only).

Unrevealed Channel commitments — the commit phase, before `ClprCompleteChannel` reveals `channel_id` — are
persisted in a separate table, `clpr_channel_pending_commitment`. `clpr_channel` is keyed by `channel_id`, but per
the commit-reveal scheme `ClprRegisterChannel` submits only a commitment hash — `channel_id` isn't revealed until
`ClprCompleteChannel` — so `clpr_channel` cannot hold a row for the commit-only phase. Per
`clpr_register_channel.proto`/`clpr_complete_channel.proto`, `ClprRegisterChannel` submits only
`ownership_commitment = keccak256(channel_id || public_key)` (computed off-chain, so `channel_id`/`public_key` stay
secret), and `ClprCompleteChannel` correlates back to it by recomputing that same hash from the _revealed_
`channel_id`/`public_key`. `clpr_channel` itself only ever holds post-reveal rows, so `channel_id` is always known
by the time a row is written there — `PENDING` is removed from `clpr_channel_status` accordingly.

`clpr_channel_pending_commitment` uses the generated `@Upsertable(history = true)` upsert only for registration.
Per [Transaction & Query Inventory](#transaction--query-inventory) row 119, the real consensus state never removes
a Channel's commitment from `STATE_ID_CLPR_PENDING_COMMITMENTS` — but once a commitment is completed or abandoned
it is no longer pending, so both `ClprCompleteChannelTransactionHandler` and `ClprCloseChannelTransactionHandler`
(for a genuinely-abandoned, never-completed commitment) delete the row directly and archive its final state —
`deleted = false` for a completion, `deleted = true` for an abandonment — into
`clpr_channel_pending_commitment_history` with a closed `timestamp_range`, bypassing the generated upsert. The
current table therefore only ever holds genuinely-pending rows: [Pending Channels](#3-pending-channels-api) needs
no filter, and re-registering an `ownership_commitment` is always a fresh insert.

`ClprRegisterConnector`/`ClprCompleteConnector` use the same commit-reveal _commit_ mechanism
(`commitment = keccak256(connectorId || pubKey)`), persisted the same way in `clpr_connector_pending_commitment`
(also `@Upsertable(history = true)` for registration, see below). At the real consensus-state level the cleanup
behavior is **not** symmetric with Channels: per `ClprCompleteConnectorHandler`, the Connector's commitment is
removed from `STATE_ID_CLPR_PENDING_CONNECTOR_COMMITMENTS` immediately at completion (`commitmentStore.remove(...)`,
step 9), unlike `ClprCompleteChannelHandler`, which leaves the Channel's commitment in place indefinitely. At the
mirror-node level the two end up symmetric anyway: `ClprCompleteConnectorTransactionHandler` uses the same
delete-and-archive path as the Channel side, moving the row to `clpr_connector_pending_commitment_history` rather
than upserting it. There's no separate abandonment path for Connectors today (no handler ever marks a Connector's
pending row `deleted=true`), so that column stays reserved for schema symmetry with Channel's table, not a
currently-reachable state.

### 1. Channel Tables

```sql
-- add_clpr_channel_support.sql
create type clpr_channel_status as enum ('ACTIVE', 'PAUSED', 'CLOSING', 'DRAINED', 'CLOSED');
create type clpr_signature_scheme as enum ('ECDSA_SECP256K1', 'ED25519');

create table if not exists clpr_channel_pending_commitment
(
    deleted                       boolean                not null default false,
    ownership_commitment          bytea                  not null,
    timestamp_range               int8range              not null,

    primary key (ownership_commitment)
);

create index if not exists clpr_channel_pending_commitment__timestamp
    on clpr_channel_pending_commitment (lower(timestamp_range));

create table if not exists clpr_channel_pending_commitment_history
(
    like clpr_channel_pending_commitment including defaults
);

create index if not exists clpr_channel_pending_commitment_history__timestamp_range
    on clpr_channel_pending_commitment_history using gist (timestamp_range);

create table if not exists clpr_channel
(
    acked_message_id                bigint                not null default 0,
    created_timestamp               bigint                not null,
    endpoint_manifest_version       bigint                not null default 0,
    last_config_timestamp           bigint,
    next_message_id                 bigint                not null default 1,
    peer_config_timestamp           bigint,
    peer_max_gas_per_message        bigint,
    peer_max_local_endpoints        bigint,
    peer_max_message_payload_bytes  bigint,
    peer_max_messages_per_bundle    bigint,
    peer_max_peer_endpoints         bigint,
    peer_max_queue_depth            bigint,
    peer_max_sync_bytes             bigint,
    received_message_id             bigint                not null default 0,
    verifier_contract_id            bigint,
    signature_scheme                clpr_signature_scheme,
    status                          clpr_channel_status   not null default 'ACTIVE',
    chain_id                        varchar               not null,
    channel_context                 bytea,
    channel_id                      bytea                 not null,
    config_proof_bytes              bytea,
    endpoint_manifest               jsonb,
    endpoint_manifest_proof_bytes   bytea,
    ownership_commitment            bytea                 not null,
    public_key                      bytea,
    received_running_hash           bytea,
    sent_running_hash               bytea,
    signature                       bytea,
    timestamp_range                 int8range             not null,
    trust_anchor                    bytea,
    trust_anchor_id                 bytea,
    verifier_fingerprint            bytea,

    primary key (channel_id)
);

create index if not exists clpr_channel__chain_id
    on clpr_channel (chain_id, created_timestamp);

create index if not exists clpr_channel__chain_id_status
    on clpr_channel (chain_id, status, created_timestamp);

create table if not exists clpr_channel_history
(
    like clpr_channel including defaults
);

create index if not exists clpr_channel_history__timestamp_range
    on clpr_channel_history using gist (timestamp_range);

select create_distributed_table('clpr_channel', 'channel_id', shard_count := ${shardCount});
select create_distributed_table('clpr_channel_history', 'channel_id', colocate_with => 'clpr_channel');
```

`clpr_channel_pending_commitment`/`_history` stay plain, undistributed tables in both profiles: nothing joins
against them from a distributed table, and their write volume — one row per Channel ever registered, written once
at registration and updated at most once or twice more — is low enough not to bottleneck a single coordinator-local
table, even though the row count isn't tiny.

`clpr_channel__chain_id` supports [Channels per Chain API](#1-channels-per-chain-api)'s unfiltered,
`created_timestamp`-ordered lookup; `clpr_channel__chain_id_status` supports the same lookup when `status` is
also filtered.

### 2. Connector Tables

```sql
-- add_clpr_connector_support.sql
create table if not exists clpr_connector_pending_commitment
(
    deleted             boolean                not null default false,
    commitment          bytea                  not null,
    timestamp_range     int8range              not null,

    primary key (commitment)
);

create index if not exists clpr_connector_pending_commitment__timestamp
    on clpr_connector_pending_commitment (lower(timestamp_range));

create table if not exists clpr_connector_pending_commitment_history
(
    like clpr_connector_pending_commitment including defaults
);

create index if not exists clpr_connector_pending_commitment_history__timestamp_range
    on clpr_connector_pending_commitment_history using gist (timestamp_range);

create table if not exists clpr_connector
(
    connector_contract_id    bigint      not null,
    created_timestamp        bigint      not null,
    in_flight_message_count  bigint      not null default 0,
    locked_stake             bigint      not null default 0,
    slash_count              bigint      not null default 0,
    stake_recipient_id       bigint,
    deleted                  boolean,
    signature_scheme         clpr_signature_scheme,
    admin_key                bytea,
    channel_id               bytea       not null,
    connector_id             bytea       not null,
    public_key               bytea,
    salt                     bytea,
    signature                bytea,
    timestamp_range          int8range   not null,

    primary key (channel_id, connector_id)
);

create index if not exists clpr_connector__channel_id_created_timestamp
    on clpr_connector (channel_id, created_timestamp);

create table if not exists clpr_connector_history
(
    like clpr_connector including defaults
);

create index if not exists clpr_connector_history__timestamp_range
    on clpr_connector_history using gist (timestamp_range);

select create_distributed_table('clpr_connector', 'channel_id', colocate_with => 'clpr_channel');
select create_distributed_table('clpr_connector_history', 'channel_id', colocate_with => 'clpr_connector');
```

### 3. Ledger Configuration and Endpoint Manifest

```sql
-- add_clpr_configuration_support.sql
create table if not exists clpr_ledger_configuration
(
    consensus_timestamp         bigint      not null,
    max_gas_per_message         bigint,
    max_local_endpoints         bigint,
    max_message_payload_bytes   bigint,
    max_messages_per_bundle     bigint,
    max_peer_endpoints          bigint,
    max_queue_depth             bigint,
    max_sync_bytes              bigint,
    protocol_version            bigint      not null,
    chain_id                    varchar     not null,
    initial_trust_anchor        bytea,
    initial_trust_anchor_id     bytea,
    service_address             bytea,

    primary key (consensus_timestamp)
);

create table if not exists clpr_endpoint_manifest
(
    consensus_timestamp   bigint      not null,
    version               bigint      not null,
    endpoints             jsonb,
    service_address       bytea,

    primary key (consensus_timestamp)
);
```

### 4. Message Queue (Append-Only)

`clpr_channel`, `clpr_connector`, and `clpr_message` all stay colocated by `channel_id`. This keeps `Messages per
Channel` and `Connectors per Channel` shard-local: Citus can route both straight to the one shard that `channel_id`
hashes to, without touching any other shard.

Colocation only solves _which shard_ a query hits, though — it says nothing about _which time partition within that
shard_. `clpr_message` is time-partitioned by `consensus_timestamp` (for the reasons in
[Performance Considerations](#2-performance-considerations)), and a query filtered only by `channel_id`/`message_id`
still can't be pruned to one partition without a timestamp bound. So `clpr_message` needs both: colocation (shard
pruning) _and_ a timestamp bound (partition pruning) — they're independent mechanisms solving two different
dimensions, not alternatives to each other. The REST layer supplies the latter by requiring `timestamp` alongside
any `message.id` filter (see [REST API Implementation](#rest-api-implementation)).

**`clpr_message` is time-partitioned by `consensus_timestamp` in both `v1` and `v2`.** Message volume grows with
total network lifetime, not with sharding, so a `v1` (non-Citus) deployment accumulates the same unbounded growth
as `v2` — leaving `v1` unpartitioned would just mean no partition pruning is possible there at all. The table and
index structure ends up identical between profiles; the only real differences are how partitions get created (`v2`
has a Citus helper; `v1` does not) and `v2`'s additional `create_distributed_table` calls.

Postgres requires a partitioned table's primary key (if any) to include the partition column. Rather than include
`consensus_timestamp` in `clpr_message`'s primary key, this design drops the primary key entirely in both profiles
— the replacement indexes that cover the dropped PK's lookup role are explained after the SQL.

#### `v1`

```sql
-- add_clpr_message_support.sql (v1)
create type clpr_message_type as enum ('DATA', 'RESPONSE', 'CONTROL');
create type clpr_message_reply_status as enum
    ('SUCCESS', 'APPLICATION_ERROR', 'CONNECTOR_NOT_FOUND', 'CONNECTOR_UNDERFUNDED');

create table if not exists clpr_message
(
    consensus_timestamp    bigint                      not null,
    message_id             bigint                      not null,
    reply_to_message_id    bigint,
    message_type           clpr_message_type           not null,
    reply_status           clpr_message_reply_status,
    channel_id             bytea                       not null,
    connector_id           bytea,
    control_configuration  jsonb,
    message_data           bytea,
    running_hash           bytea                       not null,
    sender                 bytea,
    target_application     bytea
) partition by range (consensus_timestamp);

create index if not exists clpr_message__channel_id_timestamp
    on clpr_message (channel_id, consensus_timestamp);

create index if not exists clpr_message__channel_id_message_id
    on clpr_message (channel_id, message_id);

-- Partial index: only DATA messages ever have a non-null connector_id, so Response and Control rows (the other
-- two thirds of the table) never enter this index at all.
create index if not exists clpr_message__connector_id
    on clpr_message (connector_id, message_id)
    where connector_id is not null;

create or replace procedure partition_clpr_message() as
$$
declare
    one_sec_in_ns constant bigint := 10^9;
    partition_from timestamp;
    partition_from_ns bigint;
    partition_name text;
    partition_to timestamp;
    partition_to_ns bigint;
begin
    partition_from := date_trunc('month', <clpr-enablement-date>::timestamp);
    while current_timestamp + ${partitionTimeInterval}::interval >= partition_from loop
        partition_name := format('clpr_message_p%s', to_char(partition_from, 'YYYY_MM'));
        partition_from_ns := extract(epoch from partition_from)::bigint * one_sec_in_ns;
        partition_to := partition_from + ${partitionTimeInterval}::interval;
        partition_to_ns := extract(epoch from partition_to)::bigint * one_sec_in_ns;
        execute format('create table if not exists %I partition of clpr_message for values from (%L) to (%L)',
            partition_name, partition_from_ns, partition_to_ns);
        partition_from := partition_to;
    end loop;
end;
$$ language plpgsql;

call partition_clpr_message();
```

`partition_clpr_message()` only creates the initial batch of partitions. For ongoing partitions, add
`call create_time_partition_for_table('clpr_message');` to `create_mirror_node_time_partitions()` in
`R__time_partition_maintenance.sql`.

#### `v2`

```sql
-- add_clpr_message_support.sql (v2)
create type clpr_message_type as enum ('DATA', 'RESPONSE', 'CONTROL');
create type clpr_message_reply_status as enum
    ('SUCCESS', 'APPLICATION_ERROR', 'CONNECTOR_NOT_FOUND', 'CONNECTOR_UNDERFUNDED');

create table if not exists clpr_message
(
    consensus_timestamp    bigint                      not null,
    message_id             bigint                      not null,
    reply_to_message_id    bigint,
    message_type           clpr_message_type           not null,
    reply_status           clpr_message_reply_status,
    channel_id             bytea                       not null,
    connector_id           bytea,
    control_configuration  jsonb,
    message_data           bytea,
    running_hash           bytea                       not null,
    sender                 bytea,
    target_application     bytea
) partition by range (consensus_timestamp);

create index if not exists clpr_message__channel_id_timestamp
    on clpr_message (channel_id, consensus_timestamp);

create index if not exists clpr_message__channel_id_message_id
    on clpr_message (channel_id, message_id);

-- Partial index: only DATA messages ever have a non-null connector_id, so Response and Control rows (the other
-- two thirds of the table) never enter this index at all.
create index if not exists clpr_message__connector_id
    on clpr_message (connector_id, message_id)
    where connector_id is not null;

select create_distributed_table('clpr_message', 'channel_id', colocate_with => 'clpr_channel');

-- start_from is CLPR's own enablement date, a new constant separate from the
-- existing partitionStartDate (network genesis) other tables use here.
select create_time_partitions(table_name := 'public.clpr_message',
                              partition_interval := ${partitionTimeInterval},
                              start_from := <clpr-enablement-date>::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
```

**`message_data` is shared between Data and Response rows.** `ClprMessage.message_data` (what the source
application sent) and `ClprMessageReply.message_reply_data` (what the destination application returned) are two
distinct wire fields serving the same role in opposite directions — never both populated for the same row, since a
row is exactly one of Data, Response, or Control. `message_data` holds whichever one applies, keyed off
`message_type`; it's `null` for Control rows, which carry no application payload. Control rows instead populate
`control_configuration` — the `ClprLedgerConfiguration` propagated via that row's `ClprConfigUpdate`, stored as
`jsonb` as with `clpr_channel.endpoint_manifest` — `null` for Data and Response rows.

**Every `clpr_message` Response row is one this ledger generated, answering inbound Data — never one confirming
this ledger's own outbound Data.** Per HIP-1535 §6.4, the sending ledger retains a sent Data message until a
matching reply arrives, then deletes it; that deletion (`clpr_message_value`'s `MapDeleteChange`) carries only the
key, never the reply's status or content, so there is nothing for the importer to insert — the importer's own
earlier Data row is left as-is (it is never deleted, like every other row in this design), and the reply becomes
visible only through `clpr_channel.acked_message_id`/`received_message_id` advancing and the
[Messages per Channel API](#2-messages-per-channel-api)'s computed `acknowledged` field. The reply's own status and
reply bytes are not recoverable from state changes for this direction and are not persisted. Unlike the consensus
node's own lookup (a direct keyed read against its in-memory queue state, with no partitions), the importer must
not try to recover anything for this case by looking up its own earlier Data row via `(channel_id, message_id)`
alone: `clpr_message` is time-partitioned by `consensus_timestamp` (see [Message Queue](#4-message-queue-append-only)),
and the reply carries no timestamp for the original Data message, so such a lookup would scan every partition.

**`connector_id` is not directly available for every message type.** Per `clpr_message.proto`: `ClprMessage`
(Data) carries `connector_id` directly. `ClprControlMessage` has no connector association at all — `connector_id`
is legitimately `null` for these rows. `ClprMessageReply` (Response) has **no `connector_id` field on the wire** —
only `message_id`, `status`, and `message_reply_data`. A generated Response reads its connector_id from the
inbound Data message it answers at dispatch time (`ClprSubmitBundleHandler`'s dispatch step reads that Data
message's own `connector_id` before enqueuing the Reply), but that inbound Data message is never itself persisted
to state, so there is no local row for the importer to copy `connector_id` from. This design assumes the
consensus-node team's confirmed plan to make this derivable from the initial Data message's own state — once that
lands, the importer reads `connector_id` from there the same way it already does for Data rows; until then,
`connector_id` stays `null` for Response rows, the same as Control rows.

The `connector_id` index supports [Messages per Channel](#2-messages-per-channel-api)'s `connector.id`-filtered,
`message.id`-ordered lookup — safe to paginate this way since a Connector is bound to exactly one Channel
(`connector_id` is derived as `keccak256(channelId || publicKey || salt)`), so its messages share that Channel's
monotonic `message_id` sequence. `Messages per Channel` and `Connectors per Channel` are both shard-local regardless
of which optional filters are supplied, since `channelId` is always the required path param. `Channels per Chain`
remains scatter-gather, since `chain_id`
isn't the distribution column and (unlike a Connector) a `chain_id` claim doesn't map to any single Channel. Being
shard-local doesn't make a query partition-local for free, though — colocation and time-partitioning are
independent dimensions (see above). Once the REST layer's required `timestamp` bound prunes to the right partition,
`clpr_message__channel_id_message_id` — the replacement for the primary key dropped above — finds the exact
row within it.

> **Distribution strategy.** `channel_id` and `connector_id` are protocol-level 32-byte values with no numeric
> entity id. Since `channel_id` is the root of the natural parent-child relationship (Connectors and Messages both
> belong to exactly one Channel), `clpr_channel`/`clpr_channel_history` are hash-distributed by `channel_id`, and
> `clpr_connector(_history)`/`clpr_message` are hash-distributed by `channel_id` too,
> `colocate_with => 'clpr_channel'`, so per-channel queries stay shard-local in `v2`. `clpr_message` is additionally
> time-partitioned by `consensus_timestamp` in both profiles (see above) — distribution (shard dimension, `v2`
> only) and partitioning (time dimension, both profiles) are orthogonal and both apply to it.
> `clpr_channel_pending_commitment`/`clpr_connector_pending_commitment`, `clpr_ledger_configuration`, and
> `clpr_endpoint_manifest` all
> stay plain, undistributed tables (see above): none is joined against from a distributed table, and each has a
> write pattern too low-volume to need sharding. Tables that aren't colocated with another table need an explicit
> `shard_count`, since otherwise they each start a new colocation group at Citus's configured default — `clpr_channel`
> is the only such table here, using `${shardCount}` since `channel_id` is an opaque, registrant-chosen 32-byte
> value.
>
> **Open risk, not yet resolved**: each Channel represents a whole cross-ledger relationship, so there will
> realistically be too few of them to spread evenly across 16 shards — a busy Channel's entire
> `clpr_connector`/`clpr_message` volume concentrates on whichever one shard its `channel_id` hashes to. This
> design accepts that concentration for now, rather than distributing by a uniform hash column with no colocation,
> which would eliminate the hotspot at the cost of making every query scatter-gather.

## Importer Module Changes

### 1. Domain Models

New domain classes under `common/src/main/java/org/hiero/mirror/common/domain/clpr/`, following the existing
`AbstractX` / `X` / `XHistory` pattern (`@Upsertable(history = true)` on the abstract base; see e.g.
`AbstractHook`/`Hook`/`HookHistory`) for the four history-tracked entities, and the plain-`Persistable` pattern
(see e.g. `NetworkStake`, `CryptoTransfer`) for the three that aren't. See the proto field mapping table at the
end of this section for how each class's fields relate to the real protos.

#### AbstractClprChannelPendingCommitment.java

```java
// common/src/main/java/org/hiero/mirror/common/domain/clpr/AbstractClprChannelPendingCommitment.java
@Upsertable(history = true)
public abstract class AbstractClprChannelPendingCommitment implements History {

    @Id
    private byte[] ownershipCommitment;

    // ...
}
```

#### ClprChannelPendingCommitment.java / ClprChannelPendingCommitmentHistory.java

```java
// common/src/main/java/org/hiero/mirror/common/domain/clpr/ClprChannelPendingCommitment.java
public class ClprChannelPendingCommitment extends AbstractClprChannelPendingCommitment {
    // Only the parent class should contain fields so that they're shared with both the history and non-history tables.
}
```

```java
// common/src/main/java/org/hiero/mirror/common/domain/clpr/ClprChannelPendingCommitmentHistory.java
public class ClprChannelPendingCommitmentHistory extends AbstractClprChannelPendingCommitment {
    // Only the parent class should contain fields so that they're shared with both the history and non-history tables.
}
```

#### AbstractClprChannel.java

```java
// common/src/main/java/org/hiero/mirror/common/domain/clpr/AbstractClprChannel.java
@Upsertable(history = true)
public abstract class AbstractClprChannel implements History {

    @Id
    private byte[] channelId;

    // ...
}
```

#### ClprChannel.java / ClprChannelHistory.java

```java
// common/src/main/java/org/hiero/mirror/common/domain/clpr/ClprChannel.java
public class ClprChannel extends AbstractClprChannel {
    // Only the parent class should contain fields so that they're shared with both the history and non-history tables.
}
```

```java
// common/src/main/java/org/hiero/mirror/common/domain/clpr/ClprChannelHistory.java
public class ClprChannelHistory extends AbstractClprChannel {
    // Only the parent class should contain fields so that they're shared with both the history and non-history tables.
}
```

#### AbstractClprConnectorPendingCommitment.java

```java
// common/src/main/java/org/hiero/mirror/common/domain/clpr/AbstractClprConnectorPendingCommitment.java
@Upsertable(history = true)
public abstract class AbstractClprConnectorPendingCommitment implements History {

    @Id
    private byte[] commitment;

    // ...
}
```

#### ClprConnectorPendingCommitment.java / ClprConnectorPendingCommitmentHistory.java

```java
// common/src/main/java/org/hiero/mirror/common/domain/clpr/ClprConnectorPendingCommitment.java
public class ClprConnectorPendingCommitment extends AbstractClprConnectorPendingCommitment {
    // Only the parent class should contain fields so that they're shared with both the history and non-history tables.
}
```

```java
// common/src/main/java/org/hiero/mirror/common/domain/clpr/ClprConnectorPendingCommitmentHistory.java
public class ClprConnectorPendingCommitmentHistory extends AbstractClprConnectorPendingCommitment {
    // Only the parent class should contain fields so that they're shared with both the history and non-history tables.
}
```

#### AbstractClprConnector.java

```java
// common/src/main/java/org/hiero/mirror/common/domain/clpr/AbstractClprConnector.java
@IdClass(AbstractClprConnector.Id.class)
@Upsertable(history = true)
public abstract class AbstractClprConnector implements History {

    private byte[] channelId;
    private byte[] connectorId;

    // ...

    public static class Id implements Serializable {
        private byte[] channelId;
        private byte[] connectorId;
    }
}
```

#### ClprConnector.java / ClprConnectorHistory.java

```java
// common/src/main/java/org/hiero/mirror/common/domain/clpr/ClprConnector.java
public class ClprConnector extends AbstractClprConnector {
    // Only the parent class should contain fields so that they're shared with both the history and non-history tables.
}
```

```java
// common/src/main/java/org/hiero/mirror/common/domain/clpr/ClprConnectorHistory.java
public class ClprConnectorHistory extends AbstractClprConnector {
    // Only the parent class should contain fields so that they're shared with both the history and non-history tables.
}
```

#### ClprLedgerConfiguration.java

```java
// common/src/main/java/org/hiero/mirror/common/domain/clpr/ClprLedgerConfiguration.java
public class ClprLedgerConfiguration implements Persistable<Long> {

    @jakarta.persistence.Id
    private long consensusTimestamp;

    // ...

    @Override
    public Long getId() {
        return consensusTimestamp;
    }

    @Override
    public boolean isNew() {
        return true; // every write is a fresh insert, never an update
    }
}
```

#### ClprEndpointManifest.java

```java
// common/src/main/java/org/hiero/mirror/common/domain/clpr/ClprEndpointManifest.java
public class ClprEndpointManifest implements Persistable<Long> {

    @jakarta.persistence.Id
    private long consensusTimestamp;

    // ...

    @Override
    public Long getId() {
        return consensusTimestamp;
    }

    @Override
    public boolean isNew() {
        return true; // every write is a fresh insert, never an update
    }
}
```

#### ClprMessage.java

```java
// common/src/main/java/org/hiero/mirror/common/domain/clpr/ClprMessage.java
@IdClass(ClprMessage.Id.class)
public class ClprMessage implements Persistable<ClprMessage.Id> {

    @jakarta.persistence.Id
    private byte[] channelId;

    @jakarta.persistence.Id
    private long messageId;

    // ...

    @Override
    public Id getId() {
        return new Id(channelId, messageId);
    }

    @Override
    public boolean isNew() {
        return true; // every write is a fresh insert, never an update
    }

    public static class Id implements Serializable {
        private byte[] channelId;
        private long messageId;
    }
}
```

#### Proto Field Mapping

Every field not listed below maps 1:1 by name to the same-named field on the indicated proto message.

| Domain class                     | Field(s)                                                                                  | Proto source                                                                        |
| -------------------------------- | ----------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| `ClprChannelPendingCommitment`   | `ownershipCommitment`                                                                     | recomputed: `keccak256(channel_id \|\| public_key)`, not read off the wire          |
| `ClprChannelPendingCommitment`   | `deleted`/`timestampRange`                                                                | mirror-node only                                                                    |
| `ClprChannel`                    | all fields except below                                                                   | `ClprChannel`                                                                       |
| `ClprChannel`                    | `endpointManifest`                                                                        | `ClprChannel.endpoint_manifest` — the peer's cached manifest, not this ledger's own |
| `ClprChannel`                    | `publicKey`/`signature`/`signatureScheme`/`configProofBytes`/`endpointManifestProofBytes` | `ClprCompleteChannelTransactionBody` (not state)                                    |
| `ClprChannel`                    | `createdTimestamp`/`timestampRange`                                                       | mirror-node only                                                                    |
| `ClprChannel`                    | _(not mapped)_                                                                            | `service_address` (field 3) — deprecated, superseded by `channel_context`           |
| `ClprConnectorPendingCommitment` | `commitment`                                                                              | recomputed: `keccak256(connectorId \|\| pubKey)`, not read off the wire             |
| `ClprConnectorPendingCommitment` | `deleted`/`timestampRange`                                                                | mirror-node only                                                                    |
| `ClprConnector`                  | all fields except below                                                                   | `ClprConnector`                                                                     |
| `ClprConnector`                  | `stakeRecipientId`                                                                        | `ClprDeregisterConnectorTransactionBody.stake_recipient` (not state)                |
| `ClprConnector`                  | `publicKey`/`signatureScheme`/`signature`/`salt`                                          | `ClprCompleteConnectorTransactionBody` (not state)                                  |
| `ClprConnector`                  | `deleted`/`createdTimestamp`/`timestampRange`                                             | mirror-node only                                                                    |
| `ClprLedgerConfiguration`        | all fields except below                                                                   | `ClprLedgerConfiguration` (`throttles` expanded into columns)                       |
| `ClprLedgerConfiguration`        | `consensusTimestamp`                                                                      | mirror-node only                                                                    |
| `ClprLedgerConfiguration`        | _(not mapped)_                                                                            | `endpoints` (field 6) — deprecated                                                  |
| `ClprEndpointManifest`           | all fields except below                                                                   | `ClprEndpointManifest`                                                              |
| `ClprEndpointManifest`           | `consensusTimestamp`                                                                      | mirror-node only                                                                    |
| `ClprMessage`                    | `channelId`/`messageId`                                                                   | `ClprMessageKey`                                                                    |
| `ClprMessage`                    | `connectorId`/`sender`/`targetApplication`/`messageData`                                  | `ClprMessage` (Data)                                                                |
| `ClprMessage`                    | `replyToMessageId`/`replyStatus`/`messageData`                                            | `ClprMessageReply.message_id`/`status`/`message_reply_data`                         |
| `ClprMessage`                    | `controlConfiguration`                                                                    | `ClprControlMessage.config_update.configuration`                                    |
| `ClprMessage`                    | `runningHash`                                                                             | `ClprMessageValue.running_hash_after_processing`                                    |
| `ClprMessage`                    | `messageType`                                                                             | which `ClprMessagePayload` oneof variant this row is; not itself a proto field      |

### 2. Transaction Handlers

One handler per new `TransactionType`, following the existing `@Named class extends AbstractTransactionHandler`
pattern:

- `ClprRegisterChannelTransactionHandler`
- `ClprCompleteChannelTransactionHandler`
- `ClprCloseChannelTransactionHandler`
- `ClprSubmitBundleTransactionHandler`
- `ClprRegisterConnectorTransactionHandler`
- `ClprDeregisterConnectorTransactionHandler`
- `ClprCompleteConnectorTransactionHandler`
- `ClprUpdateLedgerConfigurationTransactionHandler`

Each new `TransactionType` constant must be added to `common/src/main/java/org/hiero/mirror/common/domain/transaction/TransactionType.java`,
keyed by the verified `HederaFunctionality` numeric values (117–120, 122, 124–126). `ClprEndpointPublication` (127)
is Hiero-internal and may not need a mirror-node-facing `TransactionType` if it's not ingested; see the
[Transaction & Query Inventory](#transaction--query-inventory).

Most handlers read fields straight off their own same-named `TransactionBody`. `ClprCompleteChannelTransactionHandler`,
`ClprCloseChannelTransactionHandler` (terminal status transitions), and `ClprSubmitBundleTransactionHandler` also
need verifier-derived fields that exist only as state changes, never on the transaction — a `ClprXxxTransformer`
(see [below](#3-block-stream-transformers-and-recorditem-extensions)) copies those onto new CLPR-specific fields
on `RecordItem` for the handler to read instead.

See [Database Schema Design](#database-schema-design) for `ClprSubmitBundle`'s per-message dispatch and
`connector_id` handling.

### 3. Block Stream Transformers and `RecordItem` Extensions

Following the existing `importer/src/main/java/org/hiero/mirror/importer/downloader/block/transformer/` pattern
(`AbstractBlockTransactionTransformer`, one `@Named` subclass per `TransactionType` needing enrichment), these
transformers populate the new CLPR-specific `RecordItem` fields described in [Architecture](#architecture):

- `ClprCompleteChannelTransformer` — copies the complete `clpr_channel_value` state change (the full `ClprChannel`
  object `ClprCompleteChannelHandler` builds — `chain_id`, `status`, `verifier_contract`, `verifier_fingerprint`,
  `trust_anchor`/`trust_anchor_id`, `channel_context`, `endpoint_manifest`/`endpoint_manifest_version`,
  `peer_throttles`, `peer_config_timestamp`, and the fresh queue-state defaults) onto `RecordItem`.
- `ClprSubmitBundleTransformer` — populates the dispatched `clpr_message_value` entries for the handler to expand
  into `clpr_message` rows, plus the updated `clpr_channel_value` (queue positions, running hashes, status).
- `ClprCloseChannelTransformer` — populates the resulting channel status/queue state for the
  `CLOSING`/`DRAINED`/`CLOSED` transitions.

`ClprRegisterChannel`, `ClprRegisterConnector`, `ClprCompleteConnector`, `ClprDeregisterConnector`, and
`ClprUpdateLedgerConfiguration` need no dedicated transformer and fall back to `DefaultTransformer`.

### 4. Entity Listener Enhancement

Add to `importer/src/main/java/org/hiero/mirror/importer/parser/record/entity/EntityListener.java`, following the
existing `default void onX(X x) {}` pattern:

- `onClprChannel(ClprChannel)`
- `onClprChannelPendingCommitment(ClprChannelPendingCommitment)`
- `onClprChannelPendingCommitmentCompleted(ClprChannelPendingCommitment)`
- `onClprConnector(ClprConnector)`
- `onClprConnectorPendingCommitment(ClprConnectorPendingCommitment)`
- `onClprConnectorPendingCommitmentCompleted(ClprConnectorPendingCommitment)`
- `onClprEndpointManifest(ClprEndpointManifest)`
- `onClprLedgerConfiguration(ClprLedgerConfiguration)`
- `onClprMessage(ClprMessage)`

### 5. Repository Classes

New `JpaRepository`-based repositories:

- `ClprChannelPendingCommitmentRepository`
- `ClprChannelRepository`
- `ClprConnectorPendingCommitmentRepository`
- `ClprConnectorRepository`
- `ClprLedgerConfigurationRepository`
- `ClprEndpointManifestRepository`
- `ClprMessageRepository`

## REST API Implementation

Scoped to exactly five read APIs, following existing `rest-java` conventions (paginated, filterable):

1. Channels per chain
2. Messages per channel
3. Pending channels
4. Pending connectors
5. Connectors per channel

Ledger configuration and endpoint manifest read endpoints are explicitly **not** in scope for this iteration.

**Pagination note**: `channel_id` and `connector_id` are opaque, non-sequential 32-byte values chosen by the
registrant at commit time, with no meaningful ascending/descending order, so the Channels and Connectors list APIs
below paginate by `created_timestamp` (with the id as tiebreaker), not by the id itself. `message_id`, by contrast,
is a per-channel monotonically increasing, immutable sequence number (`next_message_id` in the schema), so the
Messages APIs paginate by `message.id`, requiring `timestamp` alongside it (see their Query Parameters below and
[Message Queue](#4-message-queue-append-only) for why).

### 1. Channels per Chain API

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
      "ownership_commitment": "0x7ab3f1...",
      "public_key": "0x04a1b2c3...",
      "signature": "0x9f8e7d6c...",
      "signature_scheme": "ED25519",
      "config_proof_bytes": "0x1122...",
      "endpoint_manifest_proof_bytes": "0x3344...",
      "verifier_contract_id": "0.0.456",
      "verifier_fingerprint": "0x55aabb...",
      "trust_anchor": "0x3344...",
      "trust_anchor_id": "0x9f8e...",
      "channel_context": "0x1a2b3c...0000016e",
      "next_message_id": 48,
      "acked_message_id": 47,
      "sent_running_hash": "0xaa11...",
      "received_message_id": 52,
      "received_running_hash": "0xbb22...",
      "last_config_timestamp": "1726874345.123456789",
      "peer_config_timestamp": "1726874300.000000000",
      "peer_throttles": {
        "max_messages_per_bundle": 100,
        "max_message_payload_bytes": 8192,
        "max_gas_per_message": 15000000,
        "max_queue_depth": 1000,
        "max_sync_bytes": 4194304,
        "max_local_endpoints": 39,
        "max_peer_endpoints": 39
      },
      "endpoint_manifest": {
        "version": 3,
        "endpoints": [
          {
            "service_endpoint": { "ip_address": "10.0.0.5", "port": 50211 },
            "account_id": "0.0.3"
          }
        ]
      },
      "endpoint_manifest_version": 3,
      "created_timestamp": "1726874200.000000000",
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

| Parameter    | Type    | Description                            | Default | Validation                                                                                                                                                                     |
| ------------ | ------- | -------------------------------------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `channel.id` | string  | Filter by `channel_id`                 | none    | Supports `eq:` only — `channel_id` is opaque with no meaningful order (see Pagination note above); `channel_id` is this table's primary key, so `eq:` is a direct lookup       |
| `status`     | string  | Filter by Channel status               | none    | One of `ACTIVE`, `PAUSED`, `CLOSING`, `DRAINED`, `CLOSED` (pending, unrevealed commitments aren't in `clpr_channel` — see the [Pending Channels API](#3-pending-channels-api)) |
| `timestamp`  | string  | Filter/paginate by `created_timestamp` | none    | Supports `eq:`, `gt:`, `gte:`, `lt:`, `lte:`; seconds.nanoseconds format                                                                                                       |
| `limit`      | integer | Maximum number of Channels to return   | `25`    | Must be between 1 and 100                                                                                                                                                      |
| `order`      | string  | Sort order for results                 | `desc`  | Must be either `asc` or `desc`                                                                                                                                                 |

**Examples:**

- `/api/v1/clpr/chains/eip155:1/channels` — Get all Channels claiming `chain_id=eip155:1`, newest first
- `/api/v1/clpr/chains/eip155:1/channels?status=eq:ACTIVE&limit=10` — Get first 10 active Channels on that chain
- `/api/v1/clpr/chains/eip155:1/channels?channel.id=eq:0x1a2b3c...` — Get a specific Channel by id (still requires
  its `chainId` in the path, since this isn't a dedicated single-Channel endpoint)

### 2. Messages per Channel API

```
GET /api/v1/clpr/channels/{channelId}/messages
```

`channelId` is required in the path, keeping the query shard-local via `channel_id` (`clpr_message` is hash-distributed
by that column in `v2`); `connector.id` is an optional filter on top of it. When `connector.id` is supplied, only
`DATA` messages can ever match, since Response and Control Messages both have `connector_id = null` (Response until
the consensus-node change described in [Database Schema Design](#database-schema-design) lands; Control permanently,
since it has no Connector association at all).

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
      "acknowledged": false,
      "timestamp": "1726874345.123456789"
    }
  ],
  "links": {
    "next": "/api/v1/clpr/channels/0x1a2b3c.../messages?message.id=lt:5&timestamp=lt:1726874345.123456789&limit=1"
  }
}
```

`connector_id` is `null` for Control Messages, which have no Connector association (see
[Database Schema Design](#database-schema-design)).

`acknowledged` is computed, not stored: `message_id <= clpr_channel.acked_message_id` for the owning Channel.

Payload bytes (`message_data`) are returned opaque/hex-encoded per the [Non-Goals](#non-goals) above.

#### Query Parameters

| Parameter      | Type    | Description                          | Default | Validation                                                                                                                                                                                                                                  |
| -------------- | ------- | ------------------------------------ | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `message.id`   | integer | Filter/paginate by `message_id`      | none    | Supports `eq:`, `gt:`, `gte:`, `lt:`, `lte:`; positive integer; **requires** `timestamp` to also be supplied, so the query always prunes directly to the right `clpr_message` partition (see [Message Queue](#4-message-queue-append-only)) |
| `connector.id` | string  | Filter by `connector_id`             | none    | Supports `eq:` only — `connector_id` is opaque with no meaningful order (see Pagination note above); only `DATA` messages can ever match, since Response and Control rows have `connector_id = null` (see above)                            |
| `timestamp`    | string  | Filter by `consensus_timestamp`      | none    | Supports `eq:`, `gt:`, `gte:`, `lt:`, `lte:`; combined range must not exceed the configured max window (following this codebase's `maxTransactionsTimestampRangeNs`-style convention, e.g. 30 days)                                         |
| `type`         | string  | Filter by message type               | none    | One of `DATA`, `RESPONSE`, `CONTROL`; **requires** `timestamp` to also be supplied (see below)                                                                                                                                              |
| `limit`        | integer | Maximum number of messages to return | `25`    | Must be between 1 and 100                                                                                                                                                                                                                   |
| `order`        | string  | Sort order for results               | `desc`  | Must be either `asc` or `desc`                                                                                                                                                                                                              |

With no filters, `channel_id` alone already lets Postgres take the latest `limit` rows for this Channel directly off
`clpr_message__channel_id_timestamp`, without touching partitions it doesn't need — the same `ORDER BY ... LIMIT`
behavior any indexed column gets. `connector.id` pins the query to `clpr_message__connector_id` instead — a direct,
selective point lookup with the same property, needing no `timestamp` bound of its own. `type` is different: it's
low-cardinality (3 values, no index of its own), so filtering by it without a bound could force scanning back
through many unrelated rows — and many partitions — to accumulate `limit` matches, especially for the rarer
`CONTROL` type. That's why `type` specifically requires `timestamp` alongside it (any `message.id` filter already
requires one too); a plain fetch, or a `connector.id`-filtered fetch, doesn't need either.

**Examples:**

- `/api/v1/clpr/channels/0x1a2b3c.../messages` — Get all messages on a Channel, newest first
- `/api/v1/clpr/channels/0x1a2b3c.../messages?type=eq:DATA&timestamp=lt:1726874345.000000000&limit=10` — Get first 10 Data messages before a given time
- `/api/v1/clpr/channels/0x1a2b3c.../messages?message.id=lt:5&timestamp=lt:1726874345.000000000&limit=5` — Get 5 messages with id less than 5
- `/api/v1/clpr/channels/0x1a2b3c.../messages?connector.id=eq:0x4d5e6f...` — Get all messages handled by a specific Connector, newest first
- `/api/v1/clpr/channels/0x1a2b3c.../messages?connector.id=eq:0x4d5e6f...&timestamp=gte:1726874345.000000000&limit=10` — Get 10 of a Connector's messages within a bounded time window

### 3. Pending Channels API

```
GET /api/v1/clpr/channels/pending
```

Lists `clpr_channel_pending_commitment` rows — every row in the table is genuinely still pending, since
completion and abandonment both delete the row and archive it to `clpr_channel_pending_commitment_history` (see
[Database Schema Design](#database-schema-design)), so no filter is needed. Served by
`clpr_channel_pending_commitment__timestamp`. `channel_id` is never known for these rows; they're keyed only by
`ownership_commitment`.

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

### 4. Pending Connectors API

```
GET /api/v1/clpr/connectors/pending
```

Same shape as [Pending Channels](#3-pending-channels-api), for the same reason: `ClprRegisterConnectorTransactionBody`
carries only `commitment` — neither `channel_id` nor `connector_id` is known at registration time, since
`connector_id = keccak256(channel_id || pub_key || salt)` is itself folded into the outer commitment hash. So this
is necessarily a standalone, channel-independent endpoint, not nested under a Channel path. Lists
`clpr_connector_pending_commitment` rows — every row is genuinely still pending, since completion deletes the row
and archives it to `clpr_connector_pending_commitment_history` (see [Database Schema Design](#database-schema-design)).
Served by `clpr_connector_pending_commitment__timestamp`.

Response format:

```json
{
  "pending_connectors": [
    {
      "commitment": "0x9c2e4a...",
      "created_timestamp": "1726874345.123456789"
    }
  ],
  "links": {
    "next": "/api/v1/clpr/connectors/pending?timestamp=lt:1726874345.123456789&limit=1"
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

- `/api/v1/clpr/connectors/pending` — Get all pending connector commitments, newest first
- `/api/v1/clpr/connectors/pending?limit=10` — Get first 10 pending commitments

### 5. Connectors per Channel API

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
      "stake_recipient_id": null,
      "deleted": false,
      "public_key": "0x04a1b2c3...",
      "signature_scheme": "ED25519",
      "signature": "0x9f8e7d6c...",
      "salt": "0x0000000000000000000000000000000000000000000000000000000000000001",
      "created_timestamp": "1726874345.123456789",
      "timestamp": {
        "from": "1726874345.123456789",
        "to": null
      }
    }
  ],
  "links": {
    "next": "/api/v1/clpr/channels/0x1a2b3c.../connectors?timestamp=lt:1726874345.123456789&limit=1"
  }
}
```

#### Query Parameters

| Parameter      | Type    | Description                            | Default | Validation                                                                                                                                                                                             |
| -------------- | ------- | -------------------------------------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `connector.id` | string  | Filter by `connector_id`               | none    | Supports `eq:` only — `connector_id` is opaque with no meaningful order (see Pagination note below); part of this table's primary key alongside `channel_id`, so `eq:` is a direct, shard-local lookup |
| `deleted`      | boolean | Filter by deregistration status        | none    | Supports `eq:true`/`eq:false`; omit to return both deregistered and still-registered Connectors                                                                                                        |
| `timestamp`    | string  | Filter/paginate by `created_timestamp` | none    | Supports `eq:`, `gt:`, `gte:`, `lt:`, `lte:`                                                                                                                                                           |
| `limit`        | integer | Maximum number of Connectors to return | `25`    | Must be between 1 and 100                                                                                                                                                                              |
| `order`        | string  | Sort order for results                 | `desc`  | Must be either `asc` or `desc`                                                                                                                                                                         |

**Examples:**

- `/api/v1/clpr/channels/0x1a2b3c.../connectors` — Get all Connectors on a Channel, newest first
- `/api/v1/clpr/channels/0x1a2b3c.../connectors?limit=10` — Get first 10 Connectors
- `/api/v1/clpr/channels/0x1a2b3c.../connectors?connector.id=eq:0x4d5e6f...` — Get a specific Connector by id
- `/api/v1/clpr/channels/0x1a2b3c.../connectors?deleted=eq:false` — Get only still-registered Connectors

## Consensus-Node Assumptions

Raised with and clarified by the consensus-node/HIP-1535 team, found while tracing CLPR state back to where it's
written and reconciling the HIP text against the actual protobuf definitions in `hiero-ledger/hiero-consensus-node`
(`hapi/hedera-protobuf-java-api/src/main/proto/`). None of these block this design; they're documented here as the
assumptions it's building on.

1. **Redaction proto cleanup.** We expect `ClprRedactMessage = 123`, `clpr_redact_message.proto`,
   `ClprRedactedMessage`, and `ClprMessageReplyStatus.REDACTED` to be removed from the proto entirely, confirmed by
   the consensus-node team since the mechanism isn't in use.
2. **Bundle-submission authority.** We expect no endpoint-authority attribution to be recordable for
   `ClprSubmitBundle` at all — submission is confirmed fully permissionless. This design persists only the generic
   `transaction.payer_account_id`, the same field every other transaction type already gets.
3. **Deprecated field cleanup.** We expect `ClprSubmitBundleTransactionBody.endpoint_node_id`/`.endpoint_signature`
   and `ClprLedgerConfiguration.endpoints` to be removed from the proto outright, per the consensus-node team's
   stated preference while the protocol isn't yet finalized.
4. **Formal block-stream artifacts for the endpoint-manifest reconciliation and upgrade-time ledger-configuration
   initialization.** We expect each to be emitted as its own synthetic system transaction.
5. **A documented maximum length for `chain_id`.** Neither `clpr_ledger_configuration.proto` nor
   `clpr_channel.proto` bounds `chain_id`'s length — it's a plain protobuf `string`, constrained only by convention
   to the CAIP-2 format (`namespace:reference`, well under 50 bytes for any conformant value). `clpr_channel.chain_id`
   is part of a Postgres btree index, which rejects any insert whose index entry exceeds ~2704 bytes (the standard
   8KB-page limit) — we'd like the consensus-node team to enforce an explicit maximum length at the protocol level,
   rather than relying solely on implementations emitting conformant values.

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
- Database migration tests for the new tables, including the `v2` Citus distribution calls and `clpr_message`'s
  time-partitioning (manual in `v1`, Citus-managed in `v2`; see [Database Schema Design](#database-schema-design)).

### 3. Acceptance Tests

Gated on CLPR actually being enabled and testable against a live/dev network with a deployed verifier (e.g. a
Hiero-to-Hiero test verifier):

```gherkin
Feature: CLPR Channel and Connector Lifecycle

  Scenario: Channel lifecycle through the mirror node
    Given I register a CLPR channel commitment
    And the mirror node processes the transaction
    Then I query mirror node REST API for the transaction and it reports the register transaction
    And I query mirror node REST API for pending channels and see the commitment listed
    When I complete the channel with a valid verifier and peer proof
    And the mirror node processes the transactions
    Then I query mirror node REST API for the channel and it reports status "ACTIVE"
    And I query mirror node REST API for pending channels and no longer see the commitment listed
    When I submit a bundle containing one data message
    And the mirror node processes the transactions
    Then I query mirror node REST API for the channel's messages and receive 2 entries (data + response)
    When I close the channel and it drains to CLOSED
    Then I query mirror node REST API for the channel and it reports status "CLOSED"
```

### 4. k6 Tests

Add all five REST read endpoints from [REST API Implementation](#rest-api-implementation) to the k6 test suite:
Channels per Chain, Messages per Channel, Pending Channels, Pending Connectors, and Connectors per Channel.

Staging environment needs, to exercise every endpoint variant:

- At least one `ACTIVE` Channel with Connectors and messages of all three types (Data, Response, Control), so
  Messages per Channel (plain, `type`-filtered, and `connector.id`-filtered) and Connectors per Channel have real
  rows to page through.
- At least one still-pending Channel commitment and one still-pending Connector commitment, to exercise the two
  Pending APIs.
- At least one Channel in a non-`ACTIVE` status, to exercise `status`-filtered queries on Channels per Chain.

This covers REST read-path load testing only; importer-side ingestion throughput is covered separately by
[Monitor](#monitor).

## Migration Strategy

Since CLPR introduces brand-new transaction types with no prior data and mirror-node support ships in lockstep with
the consensus-node release that enables CLPR, there is no phased rollout or backward-compatibility concern (no
feature flag, no mixed-version gating) — only the schema migrations needed to create the new tables.

### 1. Database Migrations

- New Flyway migrations under `importer/src/main/resources/db/migration/{v1,v2}` for
  `clpr_channel_pending_commitment(_history)`, `clpr_channel(_history)`, `clpr_connector_pending_commitment(_history)`,
  `clpr_connector(_history)`, `clpr_ledger_configuration`, `clpr_endpoint_manifest`,
  and `clpr_message`, following the append-only migration convention (never edit a merged migration).
- The `v2` (Citus) migrations include the `create_distributed_table` calls specified in
  [Database Schema Design](#database-schema-design) for every table except `clpr_channel_pending_commitment(_history)`,
  `clpr_connector_pending_commitment(_history)`, `clpr_ledger_configuration`,
  and `clpr_endpoint_manifest`, which all stay plain, undistributed
  tables in both profiles. `clpr_message` is `partition by range (consensus_timestamp)` and time-partitioned in
  both `v1` and
  `v2` — `v2` registers partitions via `create_time_partitions`; `v1` uses its own stored procedure, since no
  Citus helper is available there.
- Add `call create_time_partition_for_table('clpr_message');` to `create_mirror_node_time_partitions()` in
  `R__time_partition_maintenance.sql`.

### 2. Performance Considerations

- `ClprSubmitBundle` can enqueue up to `MaxMessagesPerBundle` messages per transaction — batch-insert
  `clpr_message` rows within a single `RecordItem`'s processing rather than issuing one insert per message.
- The message table has no natural TTL/archival policy defined by the protocol; large, long-lived Channels could
  accumulate very large `clpr_message` tables regardless of deployment profile, so `clpr_message` is
  time-partitioned by `consensus_timestamp` in both `v1` and `v2` — decided now, in the initial schema, rather than
  deferred (see [Message Queue](#4-message-queue-append-only)).

## Monitoring

### Logging

- Log CLPR transaction processing at the same level as other transaction types; log a `WARN` if a
  `BlockTransactionTransformer` finds no matching state change for a successful CLPR transaction that requires
  verifier-derived enrichment, since this would indicate either a mirror-node bug or an unexpected consensus-node
  behavior change.
- Log rejected/unrecognized CLPR `TransactionBody` cases (e.g. a future protocol version's new Control Message
  variant) rather than silently dropping them, mirroring the HIP's own "MUST reject rather than skip" philosophy
  for forward compatibility.

## Monitor

The `monitor` module generates synthetic transactions against a live network for load/throughput testing (e.g.
10k+ TPS scenarios) and verifies they round-trip through the mirror node. CLPR support here follows the existing
`TransactionSupplier` pattern (`monitor/src/main/java/org/hiero/mirror/monitor/publish/transaction/`) used for every
other transaction type — but with one real blocker worth flagging now rather than discovering later.

### Blocked on SDK support

CLPR is not supported in the Hiero Java SDK —
no typed transaction classes exist for it, which every existing `TransactionSupplier` relies on. This is a
dependency for whoever picks up the follow-up task, not something this design resolves on its own.

### Supplier design (once unblocked)

Each CLPR transaction type worth load-testing gets its own `TransactionSupplier` implementation under a new
`publish/transaction/clpr/` package — a `@Data` POJO with `jakarta.validation`-annotated config fields and a `get()`
method returning the built SDK transaction — plus a new entry in the `TransactionType` enum (e.g.
`CLPR_REGISTER_CHANNEL(ClprRegisterChannelTransactionSupplier::new)`), so it can be selected by name in a
`hiero.mirror.monitor.publish.scenarios.<name>` YAML entry (`type`, `tps`, `properties`) — the same configuration
shape every other scenario already uses.

One new requirement this introduces: the Channel/Connector commit-reveal lifecycle (register → complete) spans two
separate transactions correlated by a locally-computed commitment hash, so a realistic CLPR scenario needs
**stateful** suppliers that remember commitments between calls — e.g. a `ClprCompleteChannelTransactionSupplier`
that consumes not-yet-completed `(channel_id, public_key, commitment)` tuples produced by a paired
`ClprRegisterChannelTransactionSupplier`. Every existing `TransactionSupplier` is stateless and always has
something to return the moment it's called; none shares state with another supplier this way, so this is new
ground for the monitor module, not an existing pattern to copy.

The register scenario should run with `receiptPercent: 1.0`, so the paired complete-supplier only ever consumes
commitments it knows were actually accepted, rather than ones that may have failed.

This also raises a question the design doesn't resolve: what does `ClprCompleteChannelTransactionSupplier` return
when called before the register-supplier has produced any commitment yet, or faster than it's producing them?
Unlike every other supplier in this module, it can legitimately have nothing available to return. Whoever
implements this needs to decide the behavior — e.g. block until one is available, return a no-op/skip signal the
publish loop understands, or throw and let the scenario's own retry/backoff handle it.

### Round-trip verification needs no new code

`RestSubscriber` (`monitor/src/main/java/org/hiero/mirror/monitor/subscribe/rest/RestSubscriber.java`) already
verifies round-trip ingestion generically, by polling the mirror node's `GET /api/v1/transactions/{transactionId}`
for any transaction type until it appears. Since every CLPR transaction gets a standard `TransactionID` and is
persisted to the generic `transaction` table by the normal importer pipeline (see
[Transaction Handlers](#2-transaction-handlers)) regardless of its CLPR-specific tables, this existing,
transaction-type-agnostic mechanism works for CLPR with no monitor-side changes — only the supplier (publish) side
needs new code.

## Non-Functional Requirements

### 1. Performance Requirements

- CLPR transaction ingestion must not add disproportionate per-transaction overhead relative to other native
  services already processed at consensus-node peak TPS.
- Read APIs for Channel/Connector/message queries should target a p95 latency under 500ms, consistent with
  comparable existing REST endpoints.

### 2. Scalability Requirements

- `clpr_message` must support efficient pagination by `(channel_id, message_id)` for Channels that accumulate a
  large message history — via time-partitioning plus the REST layer's required `timestamp` bound, not an unbounded
  partition scan (see [Message Queue](#4-message-queue-append-only)).
- Per the Citus (`v2`) distribution strategy in [Database Schema Design](#database-schema-design), queries filtered
  by `channel_id` — `Connectors per Channel` and `Messages per Channel`, both requiring `channelId` in their path —
  are shard-local. `Channels per Chain` and the `Chain` API filter by `chain_id` instead, which isn't the
  distribution column, so those scatter-gather across shards regardless of colocation (to be addressed separately).

### 3. Reliability Requirements

- CLPR ingestion must maintain the same reliability guarantees (no dropped/duplicated records, no partial-commit
  states) as existing transaction and state-change processing.
- Because several fields are sourced from block-stream state changes rather than the transaction body, ingestion
  must fail loudly (not silently persist incomplete rows) if an expected state change is missing for a successful
  CLPR transaction.

## Proposed Follow-up Implementation Tasks

**These are proposals only — no GitHub issues have been created.** Scoping, sequencing, and milestone assignment
depend on the [Consensus-Node Assumptions](#consensus-node-assumptions) above.

1. **DB schema migration for CLPR tables.** Flyway migrations (`v1` and `v2`) for
   `clpr_channel_pending_commitment(_history)`, `clpr_channel(_history)`,
   `clpr_connector_pending_commitment(_history)`, `clpr_connector(_history)`, `clpr_ledger_configuration`,
   `clpr_endpoint_manifest`, and `clpr_message`, including the `v2` Citus
   `create_distributed_table` calls from [Database Schema Design](#database-schema-design) for
   `clpr_channel(_history)`, `clpr_connector(_history)`, and `clpr_message` — every other table in this list stays
   plain, undistributed in both profiles. `clpr_message`, `clpr_ledger_configuration`, and `clpr_endpoint_manifest`
   are all append-only, no-history-pair tables, unlike the rest; `clpr_message` additionally differs by being
   time-partitioned by `consensus_timestamp` in both profiles (see
   [Message Queue](#4-message-queue-append-only)) and is the highest-volume table; includes updating
   `R__time_partition_maintenance.sql` to cover it.
2. **Importer: Channel lifecycle transaction handlers and transformers.** `ClprRegisterChannel`,
   `ClprCompleteChannel`, `ClprCloseChannel` handlers plus the `BlockTransactionTransformer`s for verifier-derived
   Channel fields.
3. **Importer: Connector lifecycle transaction handlers.** `ClprRegisterConnector`, `ClprCompleteConnector`,
   `ClprDeregisterConnector` handlers.
4. **Importer: bundle/message ingestion.** `ClprSubmitBundleTransactionHandler` plus `ClprSubmitBundleTransformer`
   that expands a bundle's verifier-dispatched messages into `clpr_message` rows; depends on (1) and (2).
5. **Importer: ledger configuration and endpoint manifest ingestion.** `ClprUpdateLedgerConfigurationTransactionHandler`
   plus handling for the endpoint manifest singleton.
6. **REST API: the five read endpoints scoped in [REST API Implementation](#rest-api-implementation)** — Channels
   per chain, Messages per channel, Pending channels, Pending connectors, and Connectors per channel — including k6
   test coverage for each (see [k6 Tests](#4-k6-tests)).
7. **Acceptance tests for the CLPR Channel/Connector lifecycle**, gated on a working verifier and a
   CLPR-enabled test network being available to the acceptance suite.
8. **Monitor support** for synthetic CLPR transaction generation and 10k+ TPS load testing — see
   [Monitor](#monitor). Blocked on Hiero Java SDK CLPR support (or a raw-protobuf workaround) landing first; the
   stateful commit-reveal supplier design is new ground for the module, not a copy of an existing pattern.
