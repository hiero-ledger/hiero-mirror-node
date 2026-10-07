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
2. Mirror-node-specific feedback on the HIP (see [Consensus-Node Assumptions](#consensus-node-assumptions)).
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
   (see [Importer Module Changes](#importer-module-changes)). These eight cases aren't the only source of CLPR
   state changes, though — see point 7 below.
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
   CLPR-specific field or oneof case — there is no sidecar case or `TransactionRecord` field to populate for this
   data at all. So the existing `BlockTransactionTransformer` pattern (see
   `importer/src/main/java/org/hiero/mirror/importer/downloader/block/transformer/`) can't be reused unmodified by
   just populating the synthetic `TransactionRecord`; it needs a companion change.
4. Since `RecordItem` is an importer-internal Java class, not a strict 1:1 mirror of the wire proto (it already
   carries non-proto fields like `hookParent`), the fix is to **add new CLPR-specific fields to `RecordItem`**
   (e.g. the parsed `clpr_channel_value`/`clpr_connector_value`/`clpr_message_value` state changes relevant to the
   transaction). A `ClprXxxTransformer` per CLPR transaction type that needs verifier-derived enrichment reads the
   relevant `StateChange`s from `StateChangeContext` and populates those new `RecordItem` fields at build time; the
   corresponding `TransactionHandler` then reads them directly, the same way it reads `transactionBody` or
   `transactionRecord` today. This is CLPR-specific handler awareness, not a fully source-agnostic mechanism.
5. The importer persists to PostgreSQL following the existing current + history table pattern.
6. `rest-java` (jOOQ-based) exposes read endpoints over the persisted state.
7. **CLPR state changes are not confined to the eight `TransactionBody` cases above.** Three real exceptions exist
   in `hiero-consensus-node` main, and they don't all need the same fix:

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
   - **The ledger configuration** singleton's first row is written either by `ClprServiceImpl#doGenesisSetup`
     (true genesis, block 0) or by `V0770ClprSchema.migrate()`'s non-genesis branch (CLPR added to an
     already-running network via upgrade) — depending on whether the network is brand new or being upgraded.
   - **The endpoint manifest** singleton (this ledger's own manifest of its own endpoints — not
     `ClprChannel.endpoint_manifest_version`, a different, per-channel cached copy of the _peer's_ manifest that
     `ClprSubmitBundle` does legitimately write) is kept current by a reconciler `HandleWorkflow` runs every
     round, independent of whether the round contained any transactions.

   The latter two share a root cause and **this is a consensus-node gap, not a mirror-node architecture
   problem**: both can arise with no transaction to anchor to at all, and `state_changes.proto`'s `StateChanges`
   message carries only a `consensus_timestamp` and a list of changes — nothing distinguishes
   "migration-triggered" from "per-round reconciler" state changes. `BlockStreamReaderImpl.shouldSkip()` already
   discards exactly this shape of block item outside genesis today, because there is no transaction for a
   `TransactionHandler` to attach to. (Genesis itself is already covered:
   `BlockStreamReaderImpl.readInitialState()`/`InitialStateReader`/`BlockFile.initialState` can be extended to
   also parse the CLPR singletons at block 0 — but that only covers a brand-new network starting with CLPR
   already enabled, not any network that reaches CLPR by upgrade, which is every existing Hiero network today.)
   Until this is resolved upstream, neither can be ingested outside of true genesis — see
   [Consensus-Node Assumptions](#consensus-node-assumptions).

```
Consensus node (CLPR Service)
  → block stream (TransactionResult/Output + StateChanges: clpr_channel_value, clpr_connector_value,
    clpr_message_value/_key, clpr_ledger_configuration_value)
  → importer block-to-record transformation (ClprXxxTransformer reads StateChangeContext, populates new
    CLPR-specific fields on RecordItem — there is no existing TransactionRecord/sidecar slot for this data)
  → importer TransactionHandlers (read the new RecordItem fields) + EntityListener
  → PostgreSQL (clpr_channel_pending_commitment/_history, clpr_channel/_history,
    clpr_connector_pending_commitment/_history, clpr_connector/_history, clpr_ledger_configuration/_history,
    clpr_message)
  → rest-java (read APIs)
```

This diagram covers the eight transaction-anchored cases, plus `sendMessage` (point 7 above) once its
`StateChangeContext` extension lands. `clpr_endpoint_manifest_value` is deliberately absent: the per-round
reconciler that produces it has no transaction to anchor to, so none of this pipeline reaches it today (point 7).
`clpr_endpoint_manifest`/`_history` stay in the schema design below regardless, since the finalized manifest still
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

| #   | `HederaFunctionality`           | `TransactionBody` / `Query` field    | What mirror node must persist                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| --- | ------------------------------- | ------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 117 | `ClprUpdateLedgerConfiguration` | `clprUpdateLedgerConfiguration` (tx) | Update the `clpr_ledger_configuration` singleton row (throttles, timestamp); close out prior history row.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| 118 | `ClprRegisterChannel`           | `clprRegisterChannel` (tx)           | Insert a row into `clpr_channel_pending_commitment` keyed by `ownership_commitment`; no `channel_id` is known yet.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| 119 | `ClprCompleteChannel`           | `clprCompleteChannel` (tx)           | Insert the new `clpr_channel` row in `ACTIVE` status, recording `channel_id`, `ownership_commitment` (carried forward, not cleared), `verifier_contract`, `verifier_fingerprint`, initial `trust_anchor`/`trust_anchor_id`, `channel_context`, and initial `endpoint_manifest_version` — populated via the corresponding `ClprCompleteChannelTransformer` from the `clpr_channel_value` state change, since these fields are verifier-derived. Per `ClprCompleteChannelHandler`, the real consensus state leaves `clpr_channel_pending_commitment`'s entry untouched indefinitely (see row 120). The mirror node still sets `completed_timestamp` on that row itself — a self-contained recomputation of `ownership_commitment` from this transaction's own fields, not reliant on consensus state — purely so the Pending Channels API can exclude it. |
| 120 | `ClprCloseChannel`              | `clprCloseChannel` (tx)              | If no Channel exists yet for the supplied `ownership_commitment` (still pending/abandoned), mark the `clpr_channel_pending_commitment` row `deleted=true`. If a Channel exists, transition its status toward `CLOSING`/`DRAINED`/`CLOSED` — per `ClprCloseChannelHandler`, this path does **not** touch `clpr_channel_pending_commitment` in real consensus state (see row 119); the mirror node already closed out that row itself at completion time via `completed_timestamp`, so there's nothing left to reconcile here.                                                                                                                                                                                                                                                                                                                            |
| 121 | `ClprGetLedgerConfiguration`    | `clprGetLedgerConfiguration` (query) | Read-only; no persistence — served from the current `clpr_ledger_configuration` row.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| 122 | `ClprSubmitBundle`              | `clprSubmitBundle` (tx)              | Append dispatched `ClprMessage`/`ClprMessageReply`/`ClprControlMessage` entries to `clpr_message` (populated via `ClprSubmitBundleTransformer` from `clpr_message_value`/`clpr_message_key`, since payload contents come from the verifier, not the transaction body); `connector_id` is read directly for `ClprMessage` rows, left `null` for `ClprControlMessage` rows, and left `null` for `ClprMessageReply` rows too until a consensus-node change makes it available (see [Database Schema Design](#database-schema-design)); update `channel.next_message_id` / `received_message_id` / running hashes / status from `clpr_channel_value`.                                                                                                                                                                                                       |
| 124 | `ClprRegisterConnector`         | `clprRegisterConnector` (tx)         | Insert a row into `clpr_connector_pending_commitment` keyed by `commitment`; no `connector_id` is known yet.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| 125 | `ClprDeregisterConnector`       | `clprDeregisterConnector` (tx)       | Close out the `clpr_connector`/`clpr_connector_history` row and record `stake_recipient`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| 126 | `ClprCompleteConnector`         | `clprCompleteConnector` (tx)         | Set `completed_timestamp` on the matching `clpr_connector_pending_commitment` row immediately (matching the real consensus-state removal in `ClprCompleteConnectorHandler`, unlike the Channel side) and insert the `clpr_connector` row keyed by `(channel_id, connector_id)` with `connector_contract`, `admin_key`, `locked_stake`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| 127 | `ClprEndpointPublication`       | `clpr_endpoint_publication` (tx)     | Hiero-internal, node-to-node protocol transaction — **out of scope** for the cross-ledger-facing tables; optionally worth ingesting only if the mirror node wants to expose per-node CLPR endpoint publication history as an operational/debugging aid.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| 128 | `ClprGetEndpointManifest`       | `clprGetEndpointManifest` (query)    | Read-only; no persistence — served from the current `clpr_endpoint_manifest` row.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |

Two additional CLPR state singletons are visible in the block stream but not driven by their own dedicated
transaction: `clpr_endpoint_manifest_construction_value` (Hiero-internal, see Non-Goals) and the
`STATE_ID_CLPR_PENDING_COMMITMENTS` / `STATE_ID_CLPR_PENDING_CONNECTOR_COMMITMENTS` maps (keyed by the generic
`proto_bytes_key`/`proto_bytes_value` case — there is no dedicated `MapChangeKey`/`MapChangeValue` type for these,
unlike Channels/Connectors/Messages).

## Database Schema Design

**Channel**, **Connector**, and **ledger configuration** get current + history table pairs
(their state mutates repeatedly over their lifetime and history has audit value per HIP-1535 §11). The
**endpoint manifest** is a network-wide singleton that also mutates over time, so it gets the same treatment. The
**message queue** is naturally append-only (each `(channel_id, message_id)` is written once and never mutated), so
it is modeled as a single table, not a current/history pair.

Column order within each table groups fixed 8-byte columns (`bigint`) first, then enums/booleans, then
variable-length columns (`bytea`/`varchar`/`int8range`) last, to avoid PostgreSQL padding waste from unfavorable
alignment (see https://www.enterprisedb.com/blog/rocks-and-sand).

Table and column definitions are identical between the `v1` and `v2` (Citus) migrations. For every table except
`clpr_channel_pending_commitment`/`clpr_connector_pending_commitment`, `clpr_ledger_configuration`,
`clpr_endpoint_manifest`, and `clpr_endpoint_manifest_endpoint` (which all stay plain, undistributed tables in both
profiles — see the relevant subsections below), the `v2` migration additionally runs the `create_distributed_table`
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

`clpr_channel_pending_commitment` is a proper `@Upsertable(history = true)` current + history pair, like
`ClprChannel`/`ClprConnector`, not a plain insert-only table — it has a real lifecycle (register → completed, or
register → abandoned) that legitimately needs updating after insert, the same reason Channel/Connector get history
tables. Per [Transaction & Query Inventory](#transaction--query-inventory) row 119, the real consensus state never
removes a Channel's commitment from `STATE_ID_CLPR_PENDING_COMMITMENTS` at completion — but the mirror node's own
`ClprCompleteChannelTransactionHandler` still recomputes the same `ownership_commitment` hash (pure function of
that transaction's own `channel_id`/`public_key` fields, no verifier/state-change data needed) purely to write
`completed_timestamp` back onto this row, by its own primary key — a direct point-update, not a cross-table join.
This lets [Pending Channels](#5-pending-channels-api) correctly exclude completed commitments without ever joining
across the two tables. `deleted` remains reserved for genuinely-abandoned (never-completed) commitments, set by
`ClprCloseChannel`.

`ClprRegisterConnector`/`ClprCompleteConnector` use the same commit-reveal _commit_ mechanism
(`commitment = keccak256(connectorId || pubKey)`), persisted the same way in `clpr_connector_pending_commitment`
(also `@Upsertable(history = true)`, see below). The _cleanup_ behavior is **not** symmetric with Channels, though:
per `ClprCompleteConnectorHandler`, the Connector's commitment is removed from
`STATE_ID_CLPR_PENDING_CONNECTOR_COMMITMENTS` immediately at completion (`commitmentStore.remove(...)`, step 9),
unlike `ClprCompleteChannelHandler`, which leaves the Channel's commitment in place indefinitely. Mirroring that
real removal, `ClprCompleteConnectorTransactionHandler` sets `completed_timestamp` immediately, in the same
transaction that completes the Connector — there's no separate abandonment path for Connectors today (no handler
ever marks a Connector's pending row `deleted=true`), so that column stays reserved for schema symmetry with
Channel's table, not a currently-reachable state.

### 1. Channel Tables

```sql
-- add_clpr_channel_support.sql
create type clpr_channel_status as enum ('ACTIVE', 'PAUSED', 'CLOSING', 'DRAINED', 'CLOSED');

create table if not exists clpr_channel_pending_commitment
(
    completed_timestamp  bigint,
    created_timestamp    bigint      not null,
    deleted              boolean     not null default false,
    ownership_commitment bytea       not null,
    timestamp_range      int8range   not null,

    primary key (ownership_commitment)
);

-- Partial index: only rows still genuinely pending are indexed, so its size tracks
-- that shrinking subset rather than the ever-growing total (nothing is ever
-- physically deleted — see Pending Channels API). Supports the API's
-- deleted=false AND completed_timestamp IS NULL filter directly.
create index if not exists clpr_channel_pending_commitment__created_timestamp
    on clpr_channel_pending_commitment (created_timestamp)
    where not deleted and completed_timestamp is null;

create table if not exists clpr_channel_pending_commitment_history
(
    like clpr_channel_pending_commitment including defaults
);

create index if not exists clpr_channel_pending_commitment_history__timestamp_range
    on clpr_channel_pending_commitment_history using gist (timestamp_range);

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

select create_distributed_table('clpr_channel', 'channel_id', shard_count := ${shardCount});
select create_distributed_table('clpr_channel_history', 'channel_id', colocate_with => 'clpr_channel');
```

`clpr_channel_pending_commitment`/`_history` stay plain, undistributed tables in both profiles: nothing joins
against them from a distributed table, and their write volume — one row per Channel ever registered, written once
at registration and updated at most once or twice more — is low enough not to bottleneck a single coordinator-local
table, even though the row count isn't tiny.

The `chain_id` index supports both the [Chain API](#1-chain-api)'s `chain_id`+count aggregation and the
[Channels per Chain API](#2-channels-per-chain-api)'s `chain_id`-filtered, `created_timestamp`-ordered lookup.

### 2. Connector Tables

```sql
-- add_clpr_connector_support.sql
create table if not exists clpr_connector_pending_commitment
(
    completed_timestamp bigint,
    created_timestamp   bigint      not null,
    deleted             boolean     not null default false,
    commitment          bytea       not null,
    timestamp_range     int8range   not null,

    primary key (commitment)
);

-- Partial index, same shape and rationale as clpr_channel_pending_commitment__created_timestamp: supports the
-- Pending Connectors API's completed_timestamp IS NULL filter directly, tracking the shrinking still-pending
-- subset rather than the ever-growing total. `deleted` is included only for schema symmetry with the Channel
-- side — no handler sets it for Connectors today (see Domain Models) — so this filters on completed_timestamp
-- alone in practice.
create index if not exists clpr_connector_pending_commitment__created_timestamp
    on clpr_connector_pending_commitment (created_timestamp)
    where not deleted and completed_timestamp is null;

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
    admin_key                bytea,
    channel_id               bytea       not null,
    connector_id             bytea       not null,
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

`clpr_connector_pending_commitment`/`_history` stay plain, undistributed tables in both profiles too, for the same
reason as the Channel side: no distributed table joins against them, and their write volume — one row per
Connector ever registered — is low enough not to bottleneck a single coordinator-local table.

`clpr_connector__channel_id_created_timestamp` backs the [Connectors per Channel API](#7-connectors-per-channel-api)'s
`timestamp`-ordered pagination — the primary key alone orders by `connector_id` within a `channel_id`, not by
`created_timestamp`.

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
    initial_trust_anchor        bytea,
    initial_trust_anchor_id     bytea,
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
    service_address   bytea,
    timestamp_range   int8range   not null,

    primary key (id)
);

-- One row per ClprEndpoint entry, normalized rather than a serialized blob column. Append-only: a new
-- manifest version's endpoints are inserted fresh, old versions' rows are never deleted, so history is implicit
-- in having one row set per version rather than needing a separate _history table.
create table if not exists clpr_endpoint_manifest_endpoint
(
    account_id       bigint,
    port             bigint      not null,
    version          bigint      not null,
    ip_address       varchar     not null,
    tls_certificate  bytea,

    primary key (version, ip_address, port)
);

create table if not exists clpr_endpoint_manifest_history
(
    like clpr_endpoint_manifest including defaults
);

create index if not exists clpr_endpoint_manifest_history__timestamp_range
    on clpr_endpoint_manifest_history using gist (timestamp_range);
```

These five stay as plain, ordinary Postgres tables in `v2` — no `create_distributed_table` or `create_reference_table`
call at all: nothing joins against them from a distributed table, and none is even exposed via REST in this
iteration (see [REST API Implementation](#rest-api-implementation)), so reference-table replication would add
write-side coordination overhead across every worker node for no actual benefit. This also means `v1` and `v2`'s
migrations for these tables are identical byte-for-byte, not just in table/column
definitions.

### 4. Message Queue (Append-Only)

`clpr_channel`, `clpr_connector`, and `clpr_message` all stay colocated by `channel_id`. This keeps `Messages per
Channel` and `Connectors per Channel` shard-local: Citus can route both straight to the one shard that `channel_id`
hashes to, without touching any other shard.

Colocation only solves _which shard_ a query hits, though — it says nothing about _which time partition within that
shard_. `clpr_message` is time-partitioned by `consensus_timestamp` (for the reasons in
[Performance Considerations](#2-performance-considerations)), and a query filtered only by `channel_id`/`message_id`
still can't be pruned to one partition without a timestamp bound. So `clpr_message` needs both: colocation (shard
pruning) _and_ a lookup table (partition pruning) — they're independent mechanisms solving two different
dimensions, not alternatives to each other.

**`clpr_message` is time-partitioned by `consensus_timestamp` in both `v1` and `v2`.** Message volume grows with
total network lifetime, not with sharding, so a `v1` (non-Citus) deployment accumulates the same unbounded growth
as `v2` — leaving `v1` unpartitioned would just mean no partition pruning is possible there at all. The table and
index structure ends up identical between profiles; the only real differences are how partitions get created (`v2`
has a Citus helper; `v1` does not) and `v2`'s additional `create_distributed_table` calls.

Postgres requires a partitioned table's primary key (if any) to include the partition column. Rather than include
`consensus_timestamp` in `clpr_message`'s primary key, this design drops the primary key entirely in both profiles
— the replacement indexes and the lookup table that replace the dropped PK's lookup role are explained after the
SQL.

#### `v1`

```sql
-- add_clpr_message_support.sql (v1)
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
    target_application   bytea
) partition by range (consensus_timestamp);

create index if not exists clpr_message__channel_id_timestamp
    on clpr_message (channel_id, consensus_timestamp);

create index if not exists clpr_message__channel_id_message_id
    on clpr_message (channel_id, message_id);

create index if not exists clpr_message__connector_id
    on clpr_message (connector_id, message_id);

create table if not exists clpr_message_lookup
(
    channel_id         bytea      not null,
    partition          text       not null,
    message_id_range   int8range  not null,
    timestamp_range    int8range  not null,

    primary key (channel_id, partition)
);

create index if not exists clpr_message_lookup__message_id_range
    on clpr_message_lookup using gist (channel_id, message_id_range);

-- v1 has no Citus create_time_partitions helper, so partitions are created manually.
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

#### `v2`

```sql
-- add_clpr_message_support.sql (v2)
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
    target_application   bytea
) partition by range (consensus_timestamp);

create index if not exists clpr_message__channel_id_timestamp
    on clpr_message (channel_id, consensus_timestamp);

create index if not exists clpr_message__channel_id_message_id
    on clpr_message (channel_id, message_id);

create index if not exists clpr_message__connector_id
    on clpr_message (connector_id, message_id);

select create_distributed_table('clpr_message', 'channel_id', colocate_with => 'clpr_channel');

-- start_from is CLPR's own enablement date, a new constant separate from the
-- existing partitionStartDate (network genesis) other tables use here.
select create_time_partitions(table_name := 'public.clpr_message',
                              partition_interval := ${partitionTimeInterval},
                              start_from := <clpr-enablement-date>::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});

create table if not exists clpr_message_lookup
(
    channel_id         bytea      not null,
    partition          text       not null,
    message_id_range   int8range  not null,
    timestamp_range    int8range  not null,

    primary key (channel_id, partition)
);

create index if not exists clpr_message_lookup__message_id_range
    on clpr_message_lookup using gist (channel_id, message_id_range);

select create_distributed_table('clpr_message_lookup', 'channel_id', colocate_with => 'clpr_channel');
```

`clpr_message_lookup` resolves `(channel_id, message_id)` to a timestamp range for partition pruning, in both
profiles. It's keyed by the stable `(channel_id, partition)` pair, not by `message_id_range` itself, so extending a
partition's range as more messages arrive is a cheap in-place `UPDATE` on a fixed key, not a delete+reinsert churn
every time the range grows. In `v2` it's additionally colocated with `clpr_channel` by `channel_id`, same as
`clpr_message` itself, so this resolution step is also shard-local, not just the final query.

**`connector_id` is not directly available for every message type.** Per `clpr_message.proto`: `ClprMessage`
(Data) carries `connector_id` directly. `ClprControlMessage` has no connector association at all — `connector_id`
is legitimately `null` for these rows. `ClprMessageReply` (Response) has **no `connector_id` field on the wire** —
only `message_id`, `status`, and `message_reply_data`. A Response is always generated while processing the inbound
Data message it answers (`ClprSubmitBundleHandler`'s dispatch step reads that Data message's own `connector_id`
before enqueuing the Reply), but that inbound Data message is never itself persisted to state, so there is no
local row for the importer to copy `connector_id` from. This design assumes the consensus-node team's confirmed
plan to make this derivable from the initial Data message's own state — once that lands, the importer reads
`connector_id` from there the same way it already does for Data rows; until then, `connector_id` stays `null` for
Response rows, the same as Control rows.

The `connector_id` index supports the [Messages per Connector API](#3-messages-per-connector-api)'s
`connector_id`-filtered, `message.id`-ordered lookup — safe to paginate this way since a Connector is bound to
exactly one Channel (`connector_id` is derived as `keccak256(channelId || publicKey || salt)`), so its messages
share that Channel's monotonic `message_id` sequence. `Messages per Connector` requires `channelId` in its path for
exactly this reason, so — like `Messages per Channel` and `Connectors per Channel` — it's shard-local (all three
filter by `channel_id` directly). `Channels per Chain` and the Chain API remain scatter-gather, since `chain_id`
isn't the distribution column and (unlike a Connector) a `chain_id` claim doesn't map to any single Channel. Being
shard-local doesn't make a query partition-local for free, though — colocation and time-partitioning are
independent — so within whichever shard a `message.id`-filtered query lands on, it still needs
`clpr_message_lookup` to resolve a `consensus_timestamp` bound before it can prune to the right time partition.
Once that bound is resolved, `clpr_message__channel_id_message_id` — the `v2` replacement for the primary key
dropped above — finds the exact row within that partition.

> **Distribution strategy.** `channel_id` and `connector_id` are protocol-level 32-byte values with no numeric
> entity id. Since `channel_id` is the root of the natural parent-child relationship (Connectors and Messages both
> belong to exactly one Channel), `clpr_channel`/`clpr_channel_history` are hash-distributed by `channel_id`, and
> `clpr_connector(_history)`/`clpr_message`/`clpr_message_lookup` are hash-distributed by `channel_id` too,
> `colocate_with => 'clpr_channel'`, so per-channel queries stay shard-local in `v2`. `clpr_message` is additionally
> time-partitioned by `consensus_timestamp` in both profiles (see above) — distribution (shard dimension, `v2`
> only) and partitioning (time dimension, both profiles) are orthogonal and both apply to it.
> `clpr_channel_pending_commitment`/`clpr_connector_pending_commitment` and
> `clpr_ledger_configuration(_history)`/`clpr_endpoint_manifest(_history)`/`clpr_endpoint_manifest_endpoint` all
> stay plain, undistributed tables (see above): none is joined against from a distributed table, and each has a
> write pattern too low-volume to need sharding. Tables that aren't colocated with another table need an explicit
> `shard_count`, since otherwise they each start a new colocation group at Citus's configured default — `clpr_channel`
> is the only such table here, using `${shardCount}` since `channel_id` is an opaque, registrant-chosen 32-byte
> value.
>
> **Open risk, not yet resolved**: colocating `clpr_connector`/`clpr_message` by `channel_id` means one
> disproportionately active Channel's entire volume still concentrates on whichever single shard that Channel's
> `channel_id` hashes to — this design accepts that risk for now, rather than distributing by a uniform hash column
> with no colocation, which would eliminate the hotspot risk at the cost of making every query scatter-gather.
> Revisit if real CLPR deployments show a small number of Channels dominating total volume.

## Importer Module Changes

### 1. Domain Models

New domain classes under `common/src/main/java/org/hiero/mirror/common/domain/clpr/` follow an `AbstractX` / `X` /
`XHistory` pattern: `@Upsertable(history = true)` on the abstract base, with current and history subclasses each
mapping to their own table:

- **`ClprChannelPendingCommitment`** (+ history): id `ownershipCommitment`, `createdTimestamp`,
  `completedTimestamp`, `deleted`, `timestampRange`. Inserted by `ClprRegisterChannelTransactionHandler`. Unlike
  real consensus state (where `ClprCompleteChannelHandler` never touches the pending-commitment map — see
  [Transaction & Query Inventory](#transaction--query-inventory) row 119), the mirror node's own
  `ClprCompleteChannelTransactionHandler` sets `completedTimestamp` on this row (a self-contained computation from
  that transaction's own `channel_id`/`public_key`, needing no verifier/state-change data), purely so
  [Pending Channels](#5-pending-channels-api) can exclude completed commitments without a cross-table join.
  `ClprCloseChannelTransactionHandler` sets `deleted=true` instead, for a genuinely-abandoned (never-completed)
  commitment. Uses `@Upsertable(history = true)` like `ClprChannel`/`ClprConnector`, since it has a real lifecycle
  worth tracking, not a plain insert-only entity. `deleted` is a nullable `Boolean`, not a primitive, because the
  generated upsert SQL uses `coalesce(incoming, existing, default)` per column — a handler that leaves a field
  unset preserves whatever the existing row already had, which is how `ClprCompleteChannelTransactionHandler` and
  `ClprCloseChannelTransactionHandler` can each touch only their own column. `ClprRegisterChannelHandler` writes
  its commitment unconditionally at the consensus level (`WritablePendingCommitmentStore.put`, no existence
  check), so the same `ownership_commitment` can be legitimately registered again after `ClprCloseChannel` swept
  it. `ClprRegisterChannelTransactionHandler` must therefore explicitly set `deleted = false` on every upsert
  (not leave it null) — otherwise `coalesce` preserves the prior sweep's `deleted = true`, and a
  swept-then-re-registered commitment stays permanently invisible to the Pending Channels API.
- **`ClprChannel`** (+ history): `channelId`, `chainId`, `status`, `ownershipCommitment`, `verifierContractId`,
  `verifierFingerprint`, `trustAnchor`, `trustAnchorId`, `channelContext`, `endpointManifest`,
  `endpointManifestVersion`, `nextMessageId`, `receivedMessageId`, `ackedMessageId`, `sentRunningHash`,
  `receivedRunningHash`, `lastConfigTimestamp`, `peerConfigTimestamp`, `peerThrottles` (expanded into
  `peerMaxGasPerMessage`/`peerMaxLocalEndpoints`/`peerMaxMessagePayloadBytes`/`peerMaxMessagesPerBundle`/
  `peerMaxPeerEndpoints`/`peerMaxQueueDepth`/`peerMaxSyncBytes`, matching how `ClprLedgerConfiguration`'s own
  `throttles` are already expanded into columns rather than stored as a serialized blob), `createdTimestamp`,
  `timestampRange`. Per `ClprCompleteChannelHandler` in `hiero-consensus-node`, `ownershipCommitment` is stored on
  the real `ClprChannel` state record itself (not just the pending-commitment table) — it persists there for the
  Channel's entire lifetime. `endpointManifest` is the cached _peer's_ manifest content — distinct from
  `endpointManifestVersion`, and distinct from this ledger's own `ClprEndpointManifest` singleton (see
  [Architecture](#architecture) point 7). `peerThrottles`/`peerConfigTimestamp`/`lastConfigTimestamp` are all
  nullable: each is absent until the first config exchange with the peer actually happens.
- **`ClprConnectorPendingCommitment`** (+ history): id `commitment`, `createdTimestamp`, `completedTimestamp`,
  `deleted`, `timestampRange` — the same shape as `ClprChannelPendingCommitment`. Inserted by
  `ClprRegisterConnectorTransactionHandler`. Per `ClprCompleteConnectorHandler`, the real commitment _is_ removed
  immediately at completion, so `ClprCompleteConnectorTransactionHandler` sets `completedTimestamp` in that same
  transaction. `deleted` stays reserved for schema symmetry — no handler ever sets it today, since
  `ClprDeregisterConnectorHandler` only ever operates on an already-completed Connector, so there's no
  never-completed/abandoned-commitment path for Connectors.
- **`ClprConnector`** (+ history): composite id `(channelId, connectorId)`, `connectorContractId`, `adminKey`,
  `lockedStake`, `inFlightMessageCount`, `slashCount`, `createdTimestamp`, `timestampRange`.
- **`ClprLedgerConfiguration`** (+ history) and **`ClprEndpointManifest`** (+ history): singleton rows, fields as
  in the [schema](#database-schema-design) above.
- **`ClprMessage`**: a plain, non-history `@Entity` with composite id `(channelId, messageId)`, since the schema
  is append-only.

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
keyed by the verified `HederaFunctionality` numeric values (117–120, 122, 124–126). `ClprRedactMessage` (123) is
intentionally excluded per [Non-Goals](#non-goals). `ClprEndpointPublication` (127) is Hiero-internal and may not
need a mirror-node-facing `TransactionType` if it's not ingested; see the
[Transaction & Query Inventory](#transaction--query-inventory).

For handlers whose fields come straight off the transaction body (e.g. `ClprRegisterChannel`'s
`ownership_commitment`, `ClprCompleteConnector`'s `connector_contract`/`admin_key`/`locked_stake`), processing is a
direct field mapping, same as any simple create-style transaction handler. `ClprRegisterChannelTransactionHandler`
additionally always sets `deleted = false` explicitly on its upsert, rather than leaving it unset — see
[Domain Models](#1-domain-models) for why that matters. For handlers whose resulting state is
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
`TransactionRecord` nor `TransactionSidecarRecord` has a CLPR-specific field or case, these transformers populate
**new CLPR-specific fields added to `RecordItem` itself** rather than the synthetic `TransactionRecord`:

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
- `onClprChannelPendingCommitment(ClprChannelPendingCommitment)` — called from three different handlers, all
  upserting by the `ownershipCommitment` primary key via the standard `@Upsertable(history = true)` mechanism (the
  same partial-update-with-history pattern `ClprChannel`'s own `status` transitions use, not a one-off upsert):
  insert at `ClprRegisterChannel` time; set `completedTimestamp` at `ClprCompleteChannel` time (a mirror-node-only
  enrichment — real consensus state never touches this row then, see row 119 of the inventory); set `deleted=true`
  when `ClprCloseChannel` abandons a still-pending commitment.
- `onClprConnector(ClprConnector)`
- `onClprConnectorPendingCommitment(ClprConnectorPendingCommitment)` — insert at `ClprRegisterConnector` time; set
  `completedTimestamp` at `ClprCompleteConnector` time (matching the real consensus-state removal there, see row
  126 of the inventory). No handler sets `deleted=true` today — see [Domain Models](#1-domain-models).
- `onClprEndpointManifest(ClprEndpointManifest)`
- `onClprLedgerConfiguration(ClprLedgerConfiguration)`
- `onClprMessage(ClprMessage)`

### 5. Repository Classes

New `JpaRepository`-based repositories for `ClprChannelPendingCommitment`, `ClprChannel`,
`ClprConnectorPendingCommitment`, `ClprConnector`, `ClprLedgerConfiguration`, `ClprEndpointManifest`, and
`ClprMessage`, keyed by their respective (possibly composite) ids. Given the seven REST APIs now scoped in
[REST API Implementation](#rest-api-implementation), `ClprChannelRepository` needs a `chain.id`-filtered lookup and
a distinct-`chain_id`-with-count aggregation for the Chain API (a `group by`, not a simple CRUD method);
`ClprMessageRepository` needs lookups by both `channel_id` and `connector_id`, plus the `v2` `clpr_message_lookup`
resolution step described in [Message Queue](#4-message-queue-append-only).

## REST API Implementation

Scoped to exactly seven read APIs, following existing `rest-java` conventions (paginated, filterable):

1. Chain
2. Channels per chain
3. Messages per connector
4. Messages per channel
5. Pending channels
6. Pending connectors
7. Connectors per channel

Ledger configuration and endpoint manifest read endpoints are explicitly **not** in scope for this iteration.

**Pagination note**: `channel_id` and `connector_id` are opaque, non-sequential 32-byte values chosen by the
registrant at commit time, with no meaningful ascending/descending order, so the Channels and Connectors list APIs
below paginate by `created_timestamp` (with the id as tiebreaker), not by the id itself. `message_id`, by contrast,
is a per-channel monotonically increasing, immutable sequence number (`next_message_id` in the schema), so the
Messages APIs safely paginate by `message.id` directly — though in `v2`, since `clpr_message` is time-partitioned
(see [Message Queue](#4-message-queue-append-only)), that requires first resolving `message.id` to a
`consensus_timestamp` bound via `clpr_message_lookup`, not a plain index lookup. `chain_id` is a free-form string
claim, so the Chain API paginates lexicographically by `chain.id` itself rather than by time.

### 1. Chain API

```
GET /api/v1/clpr/chains
```

There is no protocol-level chain registry (per HIP §4.2, _"A `ChainID` can be claimed by anyone"_), so this is a
mirror-node-side aggregation over `clpr_channel.chain_id`, not a queryable protocol resource. HIP-1535 never
defines a canonical way to enumerate all Channels for a given `chain_id` at the protocol level either — Channels
are permissionless and keyed only by an opaque 32-byte ID chosen by the registrant, so the mirror node can index
`chain_id` from a Channel's stored peer configuration once `ACTIVE`, but there is no protocol-level discovery
mechanism to cross-check completeness against. This is fine for indexing what actually happened on-ledger, but
means downstream consumers should not assume this endpoint can validate a Channel's `chain_id` claim.

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

| Parameter    | Type    | Description                            | Default | Validation                                                                                                                                                                     |
| ------------ | ------- | -------------------------------------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `channel.id` | string  | Filter by `channel_id`                 | none    | Supports `eq:` only — `channel_id` is opaque with no meaningful order (see Pagination note above); `channel_id` is this table's primary key, so `eq:` is a direct lookup       |
| `status`     | string  | Filter by Channel status               | none    | One of `ACTIVE`, `PAUSED`, `CLOSING`, `DRAINED`, `CLOSED` (pending, unrevealed commitments aren't in `clpr_channel` — see the [Pending Channels API](#5-pending-channels-api)) |
| `timestamp`  | string  | Filter/paginate by `created_timestamp` | none    | Supports `eq:`, `gt:`, `gte:`, `lt:`, `lte:`; seconds.nanoseconds format                                                                                                       |
| `limit`      | integer | Maximum number of Channels to return   | `25`    | Must be between 1 and 100                                                                                                                                                      |
| `order`      | string  | Sort order for results                 | `desc`  | Must be either `asc` or `desc`                                                                                                                                                 |

**Examples:**

- `/api/v1/clpr/chains/eip155:1/channels` — Get all Channels claiming `chain_id=eip155:1`, newest first
- `/api/v1/clpr/chains/eip155:1/channels?status=eq:ACTIVE&limit=10` — Get first 10 active Channels on that chain
- `/api/v1/clpr/chains/eip155:1/channels?channel.id=eq:0x1a2b3c...` — Get a specific Channel by id (still requires
  its `chainId` in the path, since this isn't a dedicated single-Channel endpoint)

### 3. Messages per Connector API

```
GET /api/v1/clpr/channels/{channelId}/connectors/{connectorId}/messages
```

`channelId` is required in the path, not just `connectorId` — this matches the protocol's own identity model, not
just a sharding optimization. Per `clpr_connector.proto`, `ClprConnectorKey`'s doc comment states it "uniquely
identifies a Connector **within a specific Channel**," and the authoritative on-ledger state key is always the
composite `(channel_id, connector_id)`, never `connector_id` alone — unlike `chain_id`, which is an unverifiable,
unscoped claim (see [Chain API](#1-chain-api)). Nesting under its Channel is therefore both spec-faithful and keeps this
query shard-local via `channel_id`, the same as [Messages per Channel](#4-messages-per-channel-api). A client with
a real `connectorId` already has its `channelId` in hand regardless, since that's how the Connector was discovered
in the first place (via [Connectors per Channel](#7-connectors-per-channel-api)). Returns Data Messages the
Connector authorized directly. Response Messages cannot be attributed to a Connector until the consensus-node
change described in [Database Schema Design](#database-schema-design) lands — until then, `connector_id` is `null`
on Response rows, so they're excluded from this endpoint's results; Control Messages never appear here either,
since they have no Connector association at all.

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
    "next": "/api/v1/clpr/channels/0x1a2b3c.../connectors/0x4d5e6f.../messages?message.id=lt:5&limit=1"
  }
}
```

Payload bytes (`message_data`) are returned opaque/hex-encoded per the [Non-Goals](#non-goals) above.

#### Query Parameters

| Parameter    | Type    | Description                          | Default | Validation                                                                                                                                                                                                             |
| ------------ | ------- | ------------------------------------ | ------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `message.id` | integer | Filter/paginate by `message_id`      | none    | Supports `eq:`, `gt:`, `gte:`, `lt:`, `lte:`; positive integer. In `v2`, resolved to a `consensus_timestamp` bound via `clpr_message_lookup` for partition pruning (see [Message Queue](#4-message-queue-append-only)) |
| `timestamp`  | string  | Filter by `consensus_timestamp`      | none    | Supports `eq:`, `gt:`, `gte:`, `lt:`, `lte:`; combined range must not exceed the configured max window (following this codebase's `maxTransactionsTimestampRangeNs`-style convention, e.g. 30 days)                    |
| `type`       | string  | Filter by message type               | none    | One of `DATA`, `RESPONSE`, `CONTROL`; **requires** `message.id` or `timestamp` to also be supplied (see below)                                                                                                         |
| `limit`      | integer | Maximum number of messages to return | `25`    | Must be between 1 and 100                                                                                                                                                                                              |
| `order`      | string  | Sort order for results               | `desc`  | Must be either `asc` or `desc`                                                                                                                                                                                         |

With no filters, `channelId` + `connectorId` already pin this query to `clpr_message__connector_id`, which — being
a direct, selective point lookup — lets Postgres take the latest `limit` rows for that Connector without touching
partitions it doesn't need, the same way `ORDER BY ... LIMIT` against any indexed column works. `type` breaks that:
it's low-cardinality (3 values, no index of its own), so filtering by it without a bound could force scanning back
through many unrelated rows — and many partitions — to accumulate `limit` matches, especially for the rarer
`CONTROL` type. That's why `type` specifically requires `message.id` or `timestamp` alongside it, while a plain
fetch or a `connectorId`-only fetch doesn't need either.

**Examples:**

- `/api/v1/clpr/channels/0x1a2b3c.../connectors/0x4d5e6f.../messages` — Get all messages handled by a Connector, newest first
- `/api/v1/clpr/channels/0x1a2b3c.../connectors/0x4d5e6f.../messages?type=eq:DATA&timestamp=gte:1726874345.000000000&limit=10` — Get first 10 Data messages within a bounded time window
- `/api/v1/clpr/channels/0x1a2b3c.../connectors/0x4d5e6f.../messages?timestamp=gte:1726874345.000000000&limit=10` — Get 10 messages within a bounded time window

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

| Parameter    | Type    | Description                          | Default | Validation                                                                                                                                                                                                             |
| ------------ | ------- | ------------------------------------ | ------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `message.id` | integer | Filter/paginate by `message_id`      | none    | Supports `eq:`, `gt:`, `gte:`, `lt:`, `lte:`; positive integer. In `v2`, resolved to a `consensus_timestamp` bound via `clpr_message_lookup` for partition pruning (see [Message Queue](#4-message-queue-append-only)) |
| `timestamp`  | string  | Filter by `consensus_timestamp`      | none    | Supports `eq:`, `gt:`, `gte:`, `lt:`, `lte:`; combined range must not exceed the configured max window (following this codebase's `maxTransactionsTimestampRangeNs`-style convention, e.g. 30 days)                    |
| `type`       | string  | Filter by message type               | none    | One of `DATA`, `RESPONSE`, `CONTROL`; **requires** `message.id` or `timestamp` to also be supplied (see below)                                                                                                         |
| `limit`      | integer | Maximum number of messages to return | `25`    | Must be between 1 and 100                                                                                                                                                                                              |
| `order`      | string  | Sort order for results               | `desc`  | Must be either `asc` or `desc`                                                                                                                                                                                         |

With no filters, `channel_id` alone already lets Postgres take the latest `limit` rows for this Channel directly off
`clpr_message__channel_id_timestamp`, without touching partitions it doesn't need — the same `ORDER BY ... LIMIT`
behavior any indexed column gets. `type` breaks that: it's low-cardinality (3 values, no index of its own), so
filtering by it without a bound could force scanning back through many unrelated rows — and many partitions — to
accumulate `limit` matches, especially for the rarer `CONTROL` type. That's why `type` specifically requires
`message.id` or `timestamp` alongside it, the same rule as [Messages per Connector](#3-messages-per-connector-api);
a plain fetch doesn't need either.

**Examples:**

- `/api/v1/clpr/channels/0x1a2b3c.../messages` — Get all messages on a Channel, newest first
- `/api/v1/clpr/channels/0x1a2b3c.../messages?type=eq:DATA&message.id=lt:50&limit=10` — Get first 10 Data messages before a given id
- `/api/v1/clpr/channels/0x1a2b3c.../messages?message.id=lt:5&limit=5` — Get 5 messages with id less than 5

### 5. Pending Channels API

```
GET /api/v1/clpr/channels/pending
```

Lists `clpr_channel_pending_commitment` rows that are genuinely still pending — i.e. `deleted = false and
completed_timestamp is null`. Both exclusions matter: `deleted=true` rows were abandoned via `ClprCloseChannel`, and
`completed_timestamp is not null` rows have already been promoted to a real `clpr_channel` (real consensus state
never removes their commitment entry — see [Transaction & Query Inventory](#transaction--query-inventory) row 119 —
so without this second filter, completed Channels would incorrectly still show up here indefinitely). This filter
is served by `clpr_channel_pending_commitment__created_timestamp`, a partial index covering only still-pending rows
(see [Database Schema Design](#database-schema-design)), so lookup cost tracks the shrinking "actually pending"
subset rather than the ever-growing total table size. `channel_id` is never known for these rows; they're keyed
only by `ownership_commitment`.

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

### 6. Pending Connectors API

```
GET /api/v1/clpr/connectors/pending
```

Same shape as [Pending Channels](#5-pending-channels-api), for the same reason: `ClprRegisterConnectorTransactionBody`
carries only `commitment` — neither `channel_id` nor `connector_id` is known at registration time, since
`connector_id = keccak256(channel_id || pub_key || salt)` is itself folded into the outer commitment hash. So this
is necessarily a standalone, channel-independent endpoint, not nested under a Channel path. Lists
`clpr_connector_pending_commitment` rows still genuinely pending — `completed_timestamp is null` (the `deleted`
exclusion is included only for schema symmetry with the Channel side; no handler sets `deleted` for Connectors
today — see [Domain Models](#1-domain-models)). Served by `clpr_connector_pending_commitment__created_timestamp`
(see [Database Schema Design](#database-schema-design)).

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

### 7. Connectors per Channel API

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

| Parameter      | Type    | Description                            | Default | Validation                                                                                                                                                                                             |
| -------------- | ------- | -------------------------------------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `connector.id` | string  | Filter by `connector_id`               | none    | Supports `eq:` only — `connector_id` is opaque with no meaningful order (see Pagination note below); part of this table's primary key alongside `channel_id`, so `eq:` is a direct, shard-local lookup |
| `timestamp`    | string  | Filter/paginate by `created_timestamp` | none    | Supports `eq:`, `gt:`, `gte:`, `lt:`, `lte:`                                                                                                                                                           |
| `limit`        | integer | Maximum number of Connectors to return | `25`    | Must be between 1 and 100                                                                                                                                                                              |
| `order`        | string  | Sort order for results                 | `desc`  | Must be either `asc` or `desc`                                                                                                                                                                         |

**Examples:**

- `/api/v1/clpr/channels/0x1a2b3c.../connectors` — Get all Connectors on a Channel, newest first
- `/api/v1/clpr/channels/0x1a2b3c.../connectors?limit=10` — Get first 10 Connectors
- `/api/v1/clpr/channels/0x1a2b3c.../connectors?connector.id=eq:0x4d5e6f...` — Get a specific Connector by id

## Consensus-Node Assumptions

Raised with and clarified by the consensus-node/HIP-1535 team, found while tracing CLPR state back to where it's
written and reconciling the HIP text against the actual protobuf definitions in `hiero-ledger/hiero-consensus-node`
(`hapi/hedera-protobuf-java-api/src/main/proto/`). None of these block this design; they're documented here as the
assumptions it's building on.

1. **Redaction proto cleanup.** `ClprRedactMessage = 123` is live in `basic_types.proto`, `transaction.proto` wires
   `clprRedactMessage` into `TransactionBody` field 87, and `clpr_redact_message.proto`/`ClprRedactedMessage`/
   `ClprMessageReplyStatus.REDACTED` are all fully specified in the state protos — despite HIP-1535 §10.6 listing
   redaction under **Rejected Ideas** (ADR `2026-08-01-remove-message-redaction.md`) and stating fields `123`/`127`
   were freed up after its removal. The consensus-node team has confirmed redaction should be removed from the
   proto, since it isn't in use. This design already follows the HIP text and excludes redaction entirely
   regardless (see [Non-Goals](#non-goals)). (`ClprEndpointPublication = 127` is unrelated to redaction despite
   sharing a footnote in the HIP — it's a legitimate, separate, Hiero-internal transaction; see
   [Architecture](#architecture).)
2. **Bundle-submission authority.** `ClprSubmitBundleTransactionBody.endpoint_node_id`/`.endpoint_signature` are
   `deprecated = true` with no equivalent note in the HIP's §10.3 text, which still describes `endpoint_node_id` as
   required, node-signed authority for bundle submission. The consensus-node team has confirmed submitting a
   bundle is fully permissionless — anyone can submit one. This design assumes there is no endpoint-authority
   attribution for the mirror node to record; at most, the submitting account is available the same way it already
   is for every transaction, via the generic `transaction.payer_account_id` — no CLPR-specific field needed.
3. **Deprecated field cleanup.** `ClprSubmitBundleTransactionBody`'s deprecated fields (above) and
   `ClprLedgerConfiguration.endpoints` (moved to the separate `ClprEndpointManifest`, described in HIP §4.3 but
   without its deprecation notice referenced from the HIP text) are expected to be removed outright from the proto
   rather than merely documented, per the consensus-node team's stated preference to clean these up while the
   protocol isn't yet finalized.
4. **Formal block-stream artifacts for the endpoint-manifest reconciliation and upgrade-time ledger-configuration
   initialization.** Both currently arise with no transaction to anchor to at all — the endpoint-manifest
   reconciler runs every round regardless of whether the round contains any transaction, and on a network that
   adds CLPR via upgrade (every existing Hiero network today), the ledger configuration's initial row is created
   the same way, at the upgrade's restart block.
   `BlockStreamReaderImpl.shouldSkip()` already discards exactly this shape of block item outside genesis today,
   because there is no transaction for a `TransactionHandler` to attach to. (Genesis itself is already covered:
   `BlockStreamReaderImpl.readInitialState()`/`InitialStateReader`/`BlockFile.initialState` can be extended to
   also parse the CLPR singletons at block 0 — but that only covers a brand-new network starting with CLPR
   already enabled, not any network that reaches CLPR by upgrade. See [Architecture](#architecture), point 7.) The
   consensus-node team agrees with the underlying principle — that mirror node needs a formal block-stream artifact
   for state it's expected to track — without yet committing to the specific mechanism. This design assumes that
   artifact will take the same shape `NODESTAKEUPDATE` already does for other periodic, non-user-triggered state:
   a synthetic system transaction, flowing through the ordinary per-transaction pipeline.

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
  time-partitioning (manual in `v1`, Citus-managed in `v2`) plus `clpr_message_lookup` in both profiles (see
  [Database Schema Design](#database-schema-design)).

### 3. Acceptance Tests

Gated on CLPR actually being enabled and testable against a live/dev network with a deployed verifier (e.g. a
Hiero-to-Hiero test verifier):

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

### 4. k6 Tests

Add all seven REST read endpoints from [REST API Implementation](#rest-api-implementation) to the k6 test suite:
Chain, Channels per Chain, Messages per Connector, Messages per Channel, Pending Channels, Pending Connectors, and
Connectors per Channel.

Staging environment needs, to exercise every endpoint variant:

- At least one `ACTIVE` Channel with Connectors and messages of all three types (Data, Response, Control), so the
  Messages/Connectors endpoints have real rows to page through.
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
  `clpr_connector(_history)`, `clpr_ledger_configuration(_history)`, `clpr_endpoint_manifest(_history)`,
  `clpr_endpoint_manifest_endpoint`, and `clpr_message`, following the append-only migration convention (never edit
  a merged migration).
- The `v2` (Citus) migrations include the `create_distributed_table` calls specified in
  [Database Schema Design](#database-schema-design) for every table except `clpr_channel_pending_commitment(_history)`,
  `clpr_connector_pending_commitment(_history)`, `clpr_ledger_configuration(_history)`,
  `clpr_endpoint_manifest(_history)`, and `clpr_endpoint_manifest_endpoint`, which all stay plain, undistributed
  tables in both profiles. `clpr_message` is `partition by range (consensus_timestamp)` and time-partitioned in
  both `v1` and
  `v2` — `v2` registers partitions via `create_time_partitions`; `v1` uses its own stored procedure, since no
  Citus helper is available there. `clpr_message_lookup`, which resolves partition pruning for it, is created in
  both profiles too, but is itself an ordinary, unpartitioned table.

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
  for forward compatibility (§6.1).

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
  large message history — in `v2`, via time-partitioning plus `clpr_message_lookup`, not an unbounded partition
  scan (see [Message Queue](#4-message-queue-append-only)).
- Per the Citus (`v2`) distribution strategy in [Database Schema Design](#database-schema-design), queries filtered
  by `channel_id` — `Connectors per Channel`, `Messages per Channel`, and `Messages per Connector` (which requires
  `channelId` in its path for this reason) — are shard-local. `Channels per Chain` and the `Chain` API filter by
  `chain_id` instead, which isn't the distribution column, so those scatter-gather across shards regardless of
  colocation (to be addressed separately).

### 3. Reliability Requirements

- CLPR ingestion must maintain the same reliability guarantees (no dropped/duplicated records, no partial-commit
  states) as existing transaction and state-change processing.
- Because several fields are sourced from block-stream state changes rather than the transaction body, ingestion
  must fail loudly (not silently persist incomplete rows) if an expected state change is missing for a successful
  CLPR transaction.

## Proposed Follow-up Implementation Tasks

**These are proposals only — no GitHub issues have been created.** Scoping, sequencing, and milestone assignment
depend on the [Consensus-Node Assumptions](#consensus-node-assumptions) above.

1. **DB schema migration for CLPR core tables.** Flyway migrations (`v1` and `v2`) for
   `clpr_channel_pending_commitment(_history)`, `clpr_channel(_history)`,
   `clpr_connector_pending_commitment(_history)`, `clpr_connector(_history)`, `clpr_ledger_configuration(_history)`,
   `clpr_endpoint_manifest(_history)`, and `clpr_endpoint_manifest_endpoint`, including the `v2` Citus
   `create_distributed_table` calls from [Database Schema Design](#database-schema-design) for `clpr_channel(_history)`
   and `clpr_connector(_history)` — every other table in this list stays plain, undistributed in both profiles.
2. **DB schema migration for `clpr_message` (+ `clpr_message_lookup`, both profiles).** Separate from (1) since
   it's a different table shape (append-only, no history pair, time-partitioned in both `v1` and `v2` — see
   [Message Queue](#4-message-queue-append-only)) and the highest-volume table.
3. **Importer: Channel lifecycle transaction handlers and transformers.** `ClprRegisterChannel`,
   `ClprCompleteChannel`, `ClprCloseChannel` handlers plus the `BlockTransactionTransformer`s for verifier-derived
   Channel fields.
4. **Importer: Connector lifecycle transaction handlers.** `ClprRegisterConnector`, `ClprCompleteConnector`,
   `ClprDeregisterConnector` handlers.
5. **Importer: bundle/message ingestion.** `ClprSubmitBundleTransactionHandler` plus `ClprSubmitBundleTransformer`
   that expands a bundle's verifier-dispatched messages into `clpr_message` rows, maintaining `clpr_message_lookup`
   ranges alongside them in `v2`; depends on (2) and (3).
6. **Importer: ledger configuration and endpoint manifest ingestion.** `ClprUpdateLedgerConfigurationTransactionHandler`
   plus handling for the endpoint manifest singleton.
7. **REST API: the seven read endpoints scoped in [REST API Implementation](#rest-api-implementation)** — Chain,
   Channels per chain, Messages per connector, Messages per channel, Pending channels, Pending connectors, and
   Connectors per channel — including k6 test coverage for each (see [k6 Tests](#4-k6-tests)).
8. **Acceptance tests for the CLPR Channel/Connector lifecycle**, gated on a working verifier and a
   CLPR-enabled test network being available to the acceptance suite.
9. **Monitor support** for synthetic CLPR transaction generation and 10k+ TPS load testing — see
   [Monitor](#monitor). Blocked on Hiero Java SDK CLPR support (or a raw-protobuf workaround) landing first; the
   stateful commit-reveal supplier design is new ground for the module, not a copy of an existing pattern.
