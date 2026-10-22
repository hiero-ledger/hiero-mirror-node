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
   block-stream-only delivery and builds no separate record-stream path. The new fields:

   | Field                          | Type                                                                              | Populated by                                                                                   |
   | ------------------------------ | --------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
   | `clprChannelValue`             | `ClprChannel`                                                                     | `ClprCompleteChannelTransformer`, `ClprSubmitBundleTransformer`, `ClprCloseChannelTransformer` |
   | `clprConnectorValue`           | `ClprConnector`                                                                   | `ClprCompleteConnectorTransformer`                                                             |
   | `clprConnectorValues`          | `List<ClprConnector>` (sending + receiving)                                       | `ClprSubmitBundleTransformer`                                                                  |
   | `clprMessageValues`            | `List<Pair<ClprMessageKey, ClprMessageValue>>` (one bundle can dispatch multiple) | `ClprSubmitBundleTransformer`                                                                  |
   | `clprLedgerConfigurationValue` | `ClprLedgerConfiguration`                                                         | `ClprUpdateLedgerConfigurationTransformer`                                                     |

   **Rule: wherever a field exists on both a state change and the transaction body, this design reads it from the
   state change** — the transaction body is used only for fields genuinely absent from state.

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

| #   | `HederaFunctionality`           | `TransactionBody` / `Query` field    | What mirror node must persist                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| --- | ------------------------------- | ------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 117 | `ClprUpdateLedgerConfiguration` | `clprUpdateLedgerConfiguration` (tx) | Insert a new `clpr_ledger_configuration` row keyed by this transaction's own `consensus_timestamp`, populated from the `clpr_ledger_configuration_value` state change via `ClprUpdateLedgerConfigurationTransformer` — `protocol_version`/`chain_id` are immutable past genesis and always reflect existing state regardless of what's submitted, and `initial_trust_anchor`/`initial_trust_anchor_id` are never on the transaction body at all.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| 118 | `ClprRegisterChannel`           | `clprRegisterChannel` (tx)           | Insert a row into `clpr_channel_pending_commitment` keyed by `ownership_commitment`; no `channel_id` is known yet.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| 119 | `ClprCompleteChannel`           | `clprCompleteChannel` (tx)           | Insert the new `clpr_channel` row in `ACTIVE` status from the `clpr_channel_value` state change (the complete `ClprChannel` object `ClprCompleteChannelHandler` builds — `chain_id`, `status`, `verifier_contract`, `verifier_fingerprint`, `trust_anchor`/`trust_anchor_id`, `channel_context`, `endpoint_manifest`/`endpoint_manifest_version`, `peer_throttles`, `peer_config_timestamp`, and fresh queue-state defaults), populated via the corresponding `ClprCompleteChannelTransformer` — plus `public_key`/`signature`/`signature_scheme`/`config_proof_bytes`/`endpoint_manifest_proof_bytes` directly off this transaction's body, since none of the five exist on real `ClprChannel` state (see [Database Schema Design](#database-schema-design)). Per `ClprCompleteChannelHandler`, the real consensus state leaves `clpr_channel_pending_commitment`'s entry untouched indefinitely (see row 120). The mirror node still recomputes `ownership_commitment` from this transaction's own fields (self-contained, not reliant on consensus state) to upsert `status = 'COMPLETED'` on that row, so the Pending Channels API can exclude it.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| 120 | `ClprCloseChannel`              | `clprCloseChannel` (tx)              | If no Channel exists yet for the supplied `ownership_commitment` (still pending/abandoned), upsert `status = 'DELETED'` on the `clpr_channel_pending_commitment` row. If a Channel exists, transition its status toward `CLOSING`/`DRAINED`/`CLOSED` — per `ClprCloseChannelHandler`, this path does **not** touch `clpr_channel_pending_commitment` in real consensus state; the mirror node already marked that row `status = 'COMPLETED'` at completion time (see row 119), so there's nothing left to reconcile here.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| 122 | `ClprSubmitBundle`              | `clprSubmitBundle` (tx)              | Append dispatched `ClprMessage`/`ClprControlMessage` entries, and `ClprMessageReply` entries this ledger itself generated answering inbound Data, to `clpr_message` (populated via `ClprSubmitBundleTransformer` from `clpr_message_value`/`clpr_message_key` PUT changes, since payload contents come from the verifier, not the transaction body); `connector_id` is read directly for `ClprMessage` rows, left `null` for `ClprControlMessage` rows, and left `null` for generated `ClprMessageReply` rows too until a consensus-node change makes it available (see [Database Schema Design](#database-schema-design)). A reply confirming this ledger's own earlier outbound Data arrives as a `clpr_message_value` deletion of that Data entry, not a new row (see [Database Schema Design](#database-schema-design)); update `channel.next_message_id` / `received_message_id` / `acked_message_id` / running hashes / status from `clpr_channel_value`, and `connector.in_flight_message_count` / `slash_count` from `clpr_connector_value` for both the sending and receiving Connector. The `clpr_channel` update carries no `timestampRange`, so it never produces a `clpr_channel_history` row (see [Domain Models](#1-domain-models)). Per `ClprSlashingUtils.applySlash` (called from this same handler), a Connector whose `slash_count` crosses `clpr.slashBanThreshold` is banned — removed from state outright, the identical `connectorStore.remove(...)` operation `ClprDeregisterConnector` uses — so a `clpr_connector_value` deletion can arrive via `ClprSubmitBundle` too, not just via `ClprDeregisterConnector`; the mirror node must upsert `deleted = true` for that case the same way (see [Database Schema Design](#database-schema-design)). |
| 124 | `ClprRegisterConnector`         | `clprRegisterConnector` (tx)         | Insert a row into `clpr_connector_pending_commitment` keyed by `commitment`; no `connector_id` is known yet.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| 125 | `ClprDeregisterConnector`       | `clprDeregisterConnector` (tx)       | Mark the `clpr_connector`/`clpr_connector_history` row `deleted=true` and record `stake_recipient_id` from this transaction's own body. Real consensus state removes the Connector from its map entirely (`ClprDeregisterConnectorHandler`'s `connectorStore.remove(key)`) — `stake_recipient` is never part of `ClprConnector` state, only a one-time transfer destination on the transaction body — so this is a mirror-node-only soft-delete, the same pattern as every other "closed" entity in this design.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| 126 | `ClprCompleteConnector`         | `clprCompleteConnector` (tx)         | Upsert `status = 'COMPLETED'` on the matching `clpr_connector_pending_commitment` row (mirror-node-only — real consensus-state removal in `ClprCompleteConnectorHandler` isn't mirrored, see [Database Schema Design](#database-schema-design)) and insert the `clpr_connector` row keyed by `(channel_id, connector_id)`, populated from the `clpr_connector_value` state change via `ClprCompleteConnectorTransformer`, plus `public_key`/`signature_scheme`/`signature`/`salt` directly off this transaction's body, since none of the four exist on real `ClprConnector` state.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| 127 | `ClprEndpointPublication`       | `clpr_endpoint_publication` (tx)     | Hiero-internal, node-to-node protocol transaction — **out of scope** for the cross-ledger-facing tables; optionally worth ingesting only if the mirror node wants to expose per-node CLPR endpoint publication history as an operational/debugging aid.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |

## Database Schema Design

**Channel** and **Connector** get current + history table pairs (their state mutates repeatedly over their
lifetime and history has audit value per HIP-1535). **Ledger configuration** and the **endpoint manifest** are
each a single, append-only table keyed by `consensus_timestamp`: every update is a complete, self-sufficient
value, so the latest row is simply the one with the highest `consensus_timestamp`, with no separate history pair
needed. The **message queue** is likewise append-only (each `(channel_id, message_id)` is written once and never
mutated), so it too is modeled as a single table, not a current/history pair.

Table and column definitions are identical between the `v1` and `v2` (Citus) migrations. For every table except
`clpr_channel_pending_commitment`/`clpr_connector_pending_commitment`, `clpr_ledger_configuration`, and
`clpr_endpoint_manifest` (which all stay plain, undistributed tables in both profiles — see the relevant subsections
below), the `v2` migration additionally runs the `create_distributed_table`
calls shown at the end of each SQL block; `v1` does not. `clpr_message` also differs in how its partitions get
created between profiles — see [Message Queue](#4-message-queue-append-only).

`channel_id` isn't revealed until `ClprCompleteChannel`, so `clpr_channel` (keyed by `channel_id`) can't hold a row
for the commit-only phase — `clpr_channel_pending_commitment`, keyed by `ownership_commitment`, covers it instead.
It's a standard `@Upsertable(history = true)` pair, with a single `status` enum (`OPEN`/`COMPLETED`/`DELETED`)
instead of separate `completed`/`deleted` booleans, since the two outcomes are mutually exclusive.
`ClprRegisterChannelHandler` writes its commitment unconditionally (no existence check), so the same
`ownership_commitment` can legitimately be registered again later — `ClprRegisterChannelTransactionHandler` must
therefore explicitly reset `status = 'OPEN'` on every upsert, otherwise `coalesce` preserves the prior lifecycle's
value.

`clpr_connector_pending_commitment` works the same way, except real consensus state isn't symmetric:
`ClprCompleteConnectorHandler` removes the Connector's commitment from state immediately on completion, while
`ClprCompleteChannelHandler` leaves the Channel's commitment in place indefinitely. The mirror node doesn't mirror
that removal — both sides upsert `status` the same way. There's no abandonment path for Connectors today, so
`DELETED` stays reserved for schema symmetry with the Channel side.

### 1. Channel Tables

```sql
-- add_clpr_channel_support.sql
create type clpr_channel_status as enum ('ACTIVE', 'PAUSED', 'CLOSING', 'DRAINED', 'CLOSED');
create type clpr_signature_scheme as enum ('ECDSA_SECP256K1', 'ED25519');
create type clpr_pending_commitment_status as enum ('OPEN', 'COMPLETED', 'DELETED');

create table if not exists clpr_channel_pending_commitment
(
    status                 clpr_pending_commitment_status not null default 'OPEN',
    ownership_commitment   bytea                          not null,
    timestamp_range        int8range                      not null,

    primary key (ownership_commitment)
);

create index if not exists clpr_channel_pending_commitment__timestamp
    on clpr_channel_pending_commitment (lower(timestamp_range))
    where status = 'OPEN';

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

create index if not exists clpr_channel__created_timestamp
    on clpr_channel (created_timestamp);

create index if not exists clpr_channel__status
    on clpr_channel (status, created_timestamp);

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

### 2. Connector Tables

```sql
-- add_clpr_connector_support.sql
create table if not exists clpr_connector_pending_commitment
(
    status           clpr_pending_commitment_status not null default 'OPEN',
    commitment       bytea                          not null,
    timestamp_range  int8range                      not null,

    primary key (commitment)
);

create index if not exists clpr_connector_pending_commitment__timestamp
    on clpr_connector_pending_commitment (lower(timestamp_range))
    where status = 'OPEN';

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
    deleted                  boolean     not null default false,
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

create index if not exists clpr_connector__created_timestamp
    on clpr_connector (created_timestamp);

create table if not exists clpr_connector_history
(
    like clpr_connector including defaults
);

create index if not exists clpr_connector_history__timestamp_range
    on clpr_connector_history using gist (timestamp_range);

select create_distributed_table('clpr_connector', 'channel_id', colocate_with => 'clpr_channel');
select create_distributed_table('clpr_connector_history', 'channel_id', colocate_with => 'clpr_connector');
```

[Connectors](#5-connectors-api) requires `channel.id` whenever `connector.id`, `deleted`, or `timestamp` is also
supplied, so `clpr_connector` carries an index for each remaining case: `clpr_connector__channel_id_created_timestamp`
(`channel.id` supplied — shard-local in `v2`) and `clpr_connector__created_timestamp` (no filters at all —
scatter-gather). A `connector.id` lookup always pairs with `channel.id`, resolving via the `(channel_id,
connector_id)` primary key, so no separate index on `connector_id` is needed.

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

`clpr_channel`, `clpr_connector`, and `clpr_message` all stay colocated by `channel_id`. This keeps `Messages
per Channel` and `Connectors` (when filtered by `channel.id`) shard-local: Citus can route both straight to
the one shard that `channel_id` hashes to, without touching any other shard.

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
    control_message        jsonb,
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
    control_message        jsonb,
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
`control_message` — the complete `ClprControlMessage` (its `payload` oneof included, not just
`config_update.configuration`, so future oneof variants need no schema change), stored as `jsonb` as with
`clpr_channel.endpoint_manifest` — `null` for Data and Response rows.

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
monotonic `message_id` sequence. `Messages per Channel` is always shard-local, since `channelId` is a required path
param; `Connectors` and `Channels` are shard-local only when filtered by `channel.id`, scatter-gather otherwise (see
[Connectors API](#5-connectors-api)/[Channels API](#1-channels-api)) — `chain_id` isn't filterable at all for now,
so it never factors into this (see [Channels API](#1-channels-api) for why). Being
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
(see e.g. `NetworkStake`, `CryptoTransfer`) for the three that aren't. Each class below is followed by a field
sources table showing how its fields relate to the real protos.

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

**Field sources:**

| Field                  | Access path                                                                                                                                                                                                                                                                                                                                                                   | Set by                                                                                                                                                       |
| ---------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `ownership_commitment` | `TransactionBody` (`ClprRegisterChannel.ownership_commitment`); on completion, recomputed as `keccak256(channel_id, public_key)` from `ClprCompleteChannel`'s own fields to locate this row; on abandonment, read directly off `ClprCloseChannel.ownership_commitment` — the admin supplies it explicitly since `public_key` was never revealed for an abandoned registration | `ClprRegisterChannel`; `ClprCompleteChannel` (recomputed); `ClprCloseChannel` (read directly, only when no live Channel exists yet for the commitment)       |
| `status`               | Literal `OPEN`, explicitly reset on every register (not left unset); `COMPLETED` on completion; `DELETED` on abandonment                                                                                                                                                                                                                                                      | `ClprRegisterChannel` (`OPEN`); `ClprCompleteChannel` (`COMPLETED`); `ClprCloseChannel` (`DELETED`, only when no live Channel exists yet for the commitment) |
| `timestamp_range`      | Framework-managed (`@Upsertable`)                                                                                                                                                                                                                                                                                                                                             | —                                                                                                                                                            |

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

**Field sources:**

Fields sourced from the `clpr_channel_value` state change are read from the complete `ClprChannel` object, copied
via `ClprCompleteChannelTransformer`/`ClprSubmitBundleTransformer`/`ClprCloseChannelTransformer` (see
[Database Schema Design](#database-schema-design)).

`ClprSubmitBundleTransactionHandler` sends its `clpr_channel` update with `timestampRange` left unset — the same
mechanism `Entity.balance` uses to avoid a history row per transfer (`upsert_history.vm`'s "non-history rows"
branch treats a null `timestampRange` as a patch to the current row only; `mergeClprChannel` needs the matching
in-memory merge branch for same-block-file batches, mirroring `SqlEntityListener.mergeEntity`). Only
`ClprCompleteChannel` (creation) and `ClprCloseChannel` (status transitions) set `timestampRange` and produce
`clpr_channel_history` rows — `ClprSubmitBundle` never does, even when it changes `status`/`trust_anchor`/`trust_anchor_id`.

| Field                            | Access path                                                                                                  | Set by                                                        |
| -------------------------------- | ------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------- |
| `channel_id`                     | `clpr_channel_value` state change (`ClprChannel.channel_id`)                                                 | `ClprCompleteChannel`                                         |
| `created_timestamp`              | Derived (consensus timestamp); reset fresh on every completion, including reactivation of a `CLOSED` channel | `ClprCompleteChannel`                                         |
| `chain_id`                       | `clpr_channel_value` state change                                                                            | `ClprCompleteChannel`, `ClprSubmitBundle`, `ClprCloseChannel` |
| `status`                         | `clpr_channel_value` state change                                                                            | `ClprCompleteChannel`, `ClprSubmitBundle`, `ClprCloseChannel` |
| `verifier_contract_id`           | `clpr_channel_value` state change                                                                            | `ClprCompleteChannel`, `ClprSubmitBundle`, `ClprCloseChannel` |
| `verifier_fingerprint`           | `clpr_channel_value` state change                                                                            | `ClprCompleteChannel`, `ClprSubmitBundle`, `ClprCloseChannel` |
| `next_message_id`                | `clpr_channel_value` state change                                                                            | `ClprCompleteChannel`, `ClprSubmitBundle`, `ClprCloseChannel` |
| `acked_message_id`               | `clpr_channel_value` state change                                                                            | `ClprCompleteChannel`, `ClprSubmitBundle`, `ClprCloseChannel` |
| `sent_running_hash`              | `clpr_channel_value` state change                                                                            | `ClprCompleteChannel`, `ClprSubmitBundle`, `ClprCloseChannel` |
| `received_message_id`            | `clpr_channel_value` state change                                                                            | `ClprCompleteChannel`, `ClprSubmitBundle`, `ClprCloseChannel` |
| `received_running_hash`          | `clpr_channel_value` state change                                                                            | `ClprCompleteChannel`, `ClprSubmitBundle`, `ClprCloseChannel` |
| `ownership_commitment`           | `clpr_channel_value` state change                                                                            | `ClprCompleteChannel`, `ClprSubmitBundle`, `ClprCloseChannel` |
| `trust_anchor`                   | `clpr_channel_value` state change                                                                            | `ClprCompleteChannel`, `ClprSubmitBundle`, `ClprCloseChannel` |
| `trust_anchor_id`                | `clpr_channel_value` state change                                                                            | `ClprCompleteChannel`, `ClprSubmitBundle`, `ClprCloseChannel` |
| `channel_context`                | `clpr_channel_value` state change                                                                            | `ClprCompleteChannel`, `ClprSubmitBundle`, `ClprCloseChannel` |
| `endpoint_manifest`              | `clpr_channel_value` state change                                                                            | `ClprCompleteChannel`, `ClprSubmitBundle`, `ClprCloseChannel` |
| `endpoint_manifest_version`      | `clpr_channel_value` state change                                                                            | `ClprCompleteChannel`, `ClprSubmitBundle`, `ClprCloseChannel` |
| `last_config_timestamp`          | `clpr_channel_value` state change                                                                            | `ClprCompleteChannel`, `ClprSubmitBundle`, `ClprCloseChannel` |
| `peer_config_timestamp`          | `clpr_channel_value` state change                                                                            | `ClprCompleteChannel`, `ClprSubmitBundle`, `ClprCloseChannel` |
| `peer_max_gas_per_message`       | `clpr_channel_value` state change                                                                            | `ClprCompleteChannel`, `ClprSubmitBundle`, `ClprCloseChannel` |
| `peer_max_local_endpoints`       | `clpr_channel_value` state change                                                                            | `ClprCompleteChannel`, `ClprSubmitBundle`, `ClprCloseChannel` |
| `peer_max_message_payload_bytes` | `clpr_channel_value` state change                                                                            | `ClprCompleteChannel`, `ClprSubmitBundle`, `ClprCloseChannel` |
| `peer_max_messages_per_bundle`   | `clpr_channel_value` state change                                                                            | `ClprCompleteChannel`, `ClprSubmitBundle`, `ClprCloseChannel` |
| `peer_max_peer_endpoints`        | `clpr_channel_value` state change                                                                            | `ClprCompleteChannel`, `ClprSubmitBundle`, `ClprCloseChannel` |
| `peer_max_queue_depth`           | `clpr_channel_value` state change                                                                            | `ClprCompleteChannel`, `ClprSubmitBundle`, `ClprCloseChannel` |
| `peer_max_sync_bytes`            | `clpr_channel_value` state change                                                                            | `ClprCompleteChannel`, `ClprSubmitBundle`, `ClprCloseChannel` |
| `public_key`                     | `TransactionBody` (`ClprCompleteChannel.public_key`); not on real state                                      | `ClprCompleteChannel`                                         |
| `signature`                      | `TransactionBody` (`ClprCompleteChannel.signature`); not on real state                                       | `ClprCompleteChannel`                                         |
| `signature_scheme`               | `TransactionBody` (`ClprCompleteChannel.signature_scheme`); not on real state                                | `ClprCompleteChannel`                                         |
| `config_proof_bytes`             | `TransactionBody` (`ClprCompleteChannel.config_proof_bytes`); not on real state                              | `ClprCompleteChannel`                                         |
| `endpoint_manifest_proof_bytes`  | `TransactionBody` (`ClprCompleteChannel.endpoint_manifest_proof_bytes`); not on real state                   | `ClprCompleteChannel`                                         |
| `timestamp_range`                | Framework-managed (`@Upsertable`)                                                                            | —                                                             |

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

**Field sources:**

| Field             | Access path                                                                                                                                                        | Set by                                                                                       |
| ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------- |
| `commitment`      | `TransactionBody` (`ClprRegisterConnector.commitment`); recomputed as `keccak256(connector_id, public_key)` by the handler itself to locate this row on completion | `ClprRegisterConnector`; `ClprCompleteConnector` (recomputed to find the row by primary key) |
| `status`          | Literal `OPEN`, explicitly reset on every register (not left unset); `COMPLETED` on completion; `DELETED` is reserved for schema symmetry, no handler ever sets it | `ClprRegisterConnector` (`OPEN`); `ClprCompleteConnector` (`COMPLETED`)                      |
| `timestamp_range` | Framework-managed (`@Upsertable`)                                                                                                                                  | —                                                                                            |

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

**Field sources:**

| Field                     | Access path                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | Set by                                                                                               |
| ------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------- |
| `channel_id`              | `clpr_connector_value` state change (`ClprConnector.channel_id`), via `ClprCompleteConnectorTransformer`                                                                                                                                                                                                                                                                                                                                                                                                     | `ClprCompleteConnector`                                                                              |
| `connector_id`            | `clpr_connector_value` state change (`ClprConnector.connector_id`), via `ClprCompleteConnectorTransformer`                                                                                                                                                                                                                                                                                                                                                                                                   | `ClprCompleteConnector`                                                                              |
| `connector_contract_id`   | `clpr_connector_value` state change (`ClprConnector.connector_contract`), via `ClprCompleteConnectorTransformer`                                                                                                                                                                                                                                                                                                                                                                                             | `ClprCompleteConnector`                                                                              |
| `admin_key`               | `clpr_connector_value` state change (`ClprConnector.admin_key`), via `ClprCompleteConnectorTransformer`                                                                                                                                                                                                                                                                                                                                                                                                      | `ClprCompleteConnector`                                                                              |
| `locked_stake`            | `clpr_connector_value` state change (`ClprConnector.locked_stake`), via `ClprCompleteConnectorTransformer`                                                                                                                                                                                                                                                                                                                                                                                                   | `ClprCompleteConnector`                                                                              |
| `in_flight_message_count` | Literal `0` at completion; thereafter the `clpr_connector_value` state change via `ClprSubmitBundleTransformer`                                                                                                                                                                                                                                                                                                                                                                                              | `ClprCompleteConnector` (initial); `ClprSubmitBundle` (ongoing)                                      |
| `slash_count`             | Literal `0` at completion; thereafter the `clpr_connector_value` state change via `ClprSubmitBundleTransformer`                                                                                                                                                                                                                                                                                                                                                                                              | `ClprCompleteConnector` (initial); `ClprSubmitBundle` (ongoing)                                      |
| `created_timestamp`       | Derived (consensus timestamp); reset fresh on every completion, including reactivation after deregistration                                                                                                                                                                                                                                                                                                                                                                                                  | `ClprCompleteConnector`                                                                              |
| `deleted`                 | Literal `false` on completion; literal `true` on deregistration, and also on a ban (`ClprSlashingUtils.applySlash` removes a Connector from state via the identical `connectorStore.remove(...)` once `slash_count` crosses `clpr.slashBanThreshold` — same outcome, different trigger; see [Transaction & Query Inventory](#transaction--query-inventory) row 122). `stake_recipient_id IS NOT NULL` distinguishes a voluntary deregistration from a ban, since only `ClprDeregisterConnector` ever sets it | `ClprCompleteConnector` (false); `ClprDeregisterConnector` (true); `ClprSubmitBundle` (true, on ban) |
| `stake_recipient_id`      | `TransactionBody` (`ClprDeregisterConnector.stake_recipient`); no state equivalent                                                                                                                                                                                                                                                                                                                                                                                                                           | `ClprDeregisterConnector`                                                                            |
| `public_key`              | `TransactionBody` (`ClprCompleteConnector.public_key`); not on real state                                                                                                                                                                                                                                                                                                                                                                                                                                    | `ClprCompleteConnector`                                                                              |
| `signature_scheme`        | `TransactionBody` (`ClprCompleteConnector.signature_scheme`); not on real state                                                                                                                                                                                                                                                                                                                                                                                                                              | `ClprCompleteConnector`                                                                              |
| `signature`               | `TransactionBody` (`ClprCompleteConnector.signature`); not on real state                                                                                                                                                                                                                                                                                                                                                                                                                                     | `ClprCompleteConnector`                                                                              |
| `salt`                    | `TransactionBody` (`ClprCompleteConnector.salt`); not on real state                                                                                                                                                                                                                                                                                                                                                                                                                                          | `ClprCompleteConnector`                                                                              |
| `timestamp_range`         | Framework-managed (`@Upsertable`)                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | —                                                                                                    |

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

**Field sources:**

| Field                       | Access path                                                                                                                                                 | Set by                          |
| --------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------- |
| `consensus_timestamp`       | Derived (consensus timestamp)                                                                                                                               | `ClprUpdateLedgerConfiguration` |
| `protocol_version`          | `clpr_ledger_configuration_value` state change, via `ClprUpdateLedgerConfigurationTransformer` — always the existing/genesis value, never the submitted one | `ClprUpdateLedgerConfiguration` |
| `chain_id`                  | `clpr_ledger_configuration_value` state change, via `ClprUpdateLedgerConfigurationTransformer` — always the existing/genesis value, never the submitted one | `ClprUpdateLedgerConfiguration` |
| `service_address`           | `clpr_ledger_configuration_value` state change (handler-resolved: the submitted value, or the CLPR system contract address if omitted)                      | `ClprUpdateLedgerConfiguration` |
| `initial_trust_anchor`      | `clpr_ledger_configuration_value` state change; never on the transaction body at all                                                                        | `ClprUpdateLedgerConfiguration` |
| `initial_trust_anchor_id`   | `clpr_ledger_configuration_value` state change; never on the transaction body at all                                                                        | `ClprUpdateLedgerConfiguration` |
| `max_gas_per_message`       | `clpr_ledger_configuration_value` state change (`throttles.max_gas_per_message`)                                                                            | `ClprUpdateLedgerConfiguration` |
| `max_local_endpoints`       | `clpr_ledger_configuration_value` state change (`throttles.max_local_endpoints`)                                                                            | `ClprUpdateLedgerConfiguration` |
| `max_message_payload_bytes` | `clpr_ledger_configuration_value` state change (`throttles.max_message_payload_bytes`)                                                                      | `ClprUpdateLedgerConfiguration` |
| `max_messages_per_bundle`   | `clpr_ledger_configuration_value` state change (`throttles.max_messages_per_bundle`)                                                                        | `ClprUpdateLedgerConfiguration` |
| `max_peer_endpoints`        | `clpr_ledger_configuration_value` state change (`throttles.max_peer_endpoints`)                                                                             | `ClprUpdateLedgerConfiguration` |
| `max_queue_depth`           | `clpr_ledger_configuration_value` state change (`throttles.max_queue_depth`)                                                                                | `ClprUpdateLedgerConfiguration` |
| `max_sync_bytes`            | `clpr_ledger_configuration_value` state change (`throttles.max_sync_bytes`)                                                                                 | `ClprUpdateLedgerConfiguration` |

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

**Field sources:** not populated by this design, and not a gap — per [Architecture](#architecture) point 5 and
Consensus-Node Assumption #4, there is currently no transaction or state change for the mirror node to anchor
this to; the per-round reconciler that produces `clpr_endpoint_manifest_value` has no block-stream artifact
today. The schema, domain model, repository, and `EntityListener.onClprEndpointManifest(...)` are wired ahead of
time so ingestion can be added once the consensus node emits the expected synthetic system transaction.

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

**Field sources:** all sourced from the dispatched `clpr_message_key`/`clpr_message_value` state change, via
`ClprSubmitBundleTransformer`, for rows this ledger generates — never for a reply confirming this ledger's own
outbound Data, which produces no row at all (see [Database Schema Design](#database-schema-design)).

| Field                     | Access path                                                                                                                  | Notes                                                                                                           |
| ------------------------- | ---------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| `channel_id`              | `ClprMessageKey.channel_id`                                                                                                  |                                                                                                                 |
| `message_id`              | `ClprMessageKey.message_id`                                                                                                  |                                                                                                                 |
| `consensus_timestamp`     | Derived (consensus timestamp)                                                                                                |                                                                                                                 |
| `running_hash`            | `ClprMessageValue.running_hash_after_processing`                                                                             |                                                                                                                 |
| `message_type`            | `ClprMessageValue.payload` oneof case: `message`→DATA, `message_reply`→RESPONSE, `control`→CONTROL; not itself a proto field |                                                                                                                 |
| `connector_id`            | `ClprMessage.connector_id`                                                                                                   | DATA rows only (also pending on generated RESPONSE rows, see [Database Schema Design](#database-schema-design)) |
| `sender`                  | `ClprMessage.sender`                                                                                                         | DATA rows only                                                                                                  |
| `target_application`      | `ClprMessage.target_application`                                                                                             | DATA rows only                                                                                                  |
| `message_data`            | `ClprMessage.message_data` or `ClprMessageReply.message_reply_data`                                                          | DATA and generated-RESPONSE rows                                                                                |
| `reply_to_message_id`     | `ClprMessageReply.message_id`                                                                                                | generated-RESPONSE rows only                                                                                    |
| `reply_status`            | `ClprMessageReply.status`                                                                                                    | generated-RESPONSE rows only                                                                                    |
| `control_message` (jsonb) | `ClprControlMessage` (complete, including its `payload` oneof)                                                               | CONTROL rows only                                                                                               |

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
`ClprCompleteConnectorTransactionHandler`, `ClprCloseChannelTransactionHandler` (terminal status transitions),
`ClprSubmitBundleTransactionHandler`, and `ClprUpdateLedgerConfigurationTransactionHandler` also need fields sourced
from state changes instead (see [Architecture](#architecture) point 2) — a `ClprXxxTransformer`
(see [below](#3-block-stream-transformers-and-recorditem-extensions)) copies those onto new CLPR-specific fields
on `RecordItem` for the handler to read instead.

See [Database Schema Design](#database-schema-design) for `ClprSubmitBundle`'s per-message dispatch and
`connector_id` handling.

### 3. Block Stream Transformers and `RecordItem` Extensions

Following the existing `importer/src/main/java/org/hiero/mirror/importer/downloader/block/transformer/` pattern
(`AbstractBlockTransactionTransformer`, one `@Named` subclass per `TransactionType` needing enrichment), these
transformers populate the new CLPR-specific `RecordItem` fields listed in [Architecture](#architecture) point 2:

- `ClprCompleteChannelTransformer` — copies the complete `clpr_channel_value` state change (the full `ClprChannel`
  object `ClprCompleteChannelHandler` builds — `chain_id`, `status`, `verifier_contract`, `verifier_fingerprint`,
  `trust_anchor`/`trust_anchor_id`, `channel_context`, `endpoint_manifest`/`endpoint_manifest_version`,
  `peer_throttles`, `peer_config_timestamp`, and the fresh queue-state defaults) onto `RecordItem`.
- `ClprCompleteConnectorTransformer` — copies the complete `clpr_connector_value` state change (the full
  `ClprConnector` object `ClprCompleteConnectorHandler` builds — `connector_id`, `channel_id`,
  `connector_contract`, `admin_key`, `locked_stake`, and the fresh `slash_count`/`in_flight_message_count`
  defaults) onto `RecordItem`.
- `ClprSubmitBundleTransformer` — populates the dispatched `clpr_message_value` entries for the handler to expand
  into `clpr_message` rows, plus the updated `clpr_channel_value` (queue positions, running hashes, and any
  automatic `status` transition the bundle triggers — `ACTIVE`↔`PAUSED`, or `CLOSING`/`DRAINED`/`CLOSED` as the
  local Channel mirrors the peer's own advertised status) and `clpr_connector_value`
  (`in_flight_message_count`/`slash_count`, for both the sending and receiving Connector) — or, if slashing banned
  a Connector this bundle, its `clpr_connector_value` deletion instead (see
  [Transaction & Query Inventory](#transaction--query-inventory) row 122).
- `ClprCloseChannelTransformer` — populates the resulting channel status/queue state for the admin-initiated
  `CLOSING`/`DRAINED`/`CLOSED` push (`ClprSubmitBundle` can also drive these same transitions automatically, see
  above).
- `ClprUpdateLedgerConfigurationTransformer` — copies the complete `clpr_ledger_configuration_value` state change
  onto `RecordItem`, since `chain_id`/`protocol_version` always reflect existing state regardless of what's
  submitted (the handler ignores the transaction body for these — they're immutable past genesis) and
  `initial_trust_anchor`/`initial_trust_anchor_id` are never on the transaction body at all.

`ClprRegisterChannel`, `ClprRegisterConnector`, and `ClprDeregisterConnector` need no dedicated transformer and
fall back to `DefaultTransformer`.

### 4. Entity Listener Enhancement

Add to `importer/src/main/java/org/hiero/mirror/importer/parser/record/entity/EntityListener.java`, following the
existing `default void onX(X x) {}` pattern:

- `onClprChannel(ClprChannel)`
- `onClprChannelPendingCommitment(ClprChannelPendingCommitment)`
- `onClprConnector(ClprConnector)`
- `onClprConnectorPendingCommitment(ClprConnectorPendingCommitment)`
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

1. Channels
2. Messages per channel
3. Pending channels
4. Pending connectors
5. Connectors

Ledger configuration and endpoint manifest read endpoints are explicitly **not** in scope for this iteration.

### 1. Channels API

```
GET /api/v1/clpr/channels
```

`chain_id` is not filterable, for now: `chain_id` isn't the distribution column in `v2`, so a `chain.id` filter
would need to scatter-gather across every shard regardless of how it's exposed (see
[Scalability Requirements](#2-scalability-requirements)). Rather than ship that cost now, this design defers
`chain.id` filtering until there's a concrete need for it — `chain_id` is still returned on every Channel, just not
queryable yet.

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
    "next": "/api/v1/clpr/channels?timestamp=lt:1726874345.123456789&limit=1"
  }
}
```

#### Query Parameters

| Parameter    | Type    | Description                            | Default | Validation                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| ------------ | ------- | -------------------------------------- | ------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `channel.id` | string  | Filter by `channel_id`                 | none    | Supports `eq:` only — `channel_id` is opaque and non-sequential, so it isn't used for pagination; `channel_id` is this table's primary key, so `eq:` is a direct, shard-local lookup                                                                                                                                                                                                                                                                                                                        |
| `status`     | string  | Filter by Channel status               | none    | One of `ACTIVE`, `PAUSED`, `CLOSING`, `DRAINED`, `CLOSED` (pending, unrevealed commitments aren't in `clpr_channel` — see the [Pending Channels API](#3-pending-channels-api))                                                                                                                                                                                                                                                                                                                              |
| `timestamp`  | string  | Filter/paginate by `created_timestamp` | none    | Supports `eq:`, `gt:`, `gte:`, `lt:`, `lte:`; seconds.nanoseconds format; sole sort/pagination key, since `created_timestamp` is a `ClprCompleteChannel` consensus timestamp and is therefore unique per Channel; combined range must not exceed the configured max window (following this codebase's `maxTransactionsTimestampRangeNs`-style convention, e.g. 30 days) — this query scatter-gathers in `v2` whenever `channel.id` is omitted (see [Scalability Requirements](#2-scalability-requirements)) |
| `limit`      | integer | Maximum number of Channels to return   | `25`    | Must be between 1 and 100                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `order`      | string  | Sort order for results                 | `desc`  | Must be either `asc` or `desc`                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |

**Examples:**

- `/api/v1/clpr/channels` — Get all Channels across every chain, newest first
- `/api/v1/clpr/channels?status=eq:ACTIVE&limit=10` — Get first 10 active Channels across every chain
- `/api/v1/clpr/channels?channel.id=eq:0x1a2b3c...` — Get a specific Channel by id directly, shard-local

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

| Parameter      | Type    | Description                                                                                                                                                                                                           | Default | Validation                                                                                                                                                                                                                                  |
| -------------- | ------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `message.id`   | integer | Filter/paginate by `message_id` — secondary sort key, tiebreaking rows that share one `consensus_timestamp` (e.g. multiple messages from one bundle, see [Performance Considerations](#2-performance-considerations)) | none    | Supports `eq:`, `gt:`, `gte:`, `lt:`, `lte:`; positive integer; **requires** `timestamp` to also be supplied, so the query always prunes directly to the right `clpr_message` partition (see [Message Queue](#4-message-queue-append-only)) |
| `connector.id` | string  | Filter by `connector_id`                                                                                                                                                                                              | none    | Supports `eq:` only — `connector_id` is opaque and non-sequential, so it isn't used for pagination; only `DATA` messages can ever match, since Response and Control rows have `connector_id = null` (see above)                             |
| `timestamp`    | string  | Filter/paginate by `consensus_timestamp` — primary sort/pagination key, served directly by `clpr_message__channel_id_timestamp`                                                                                       | none    | Supports `eq:`, `gt:`, `gte:`, `lt:`, `lte:`; combined range must not exceed the configured max window (following this codebase's `maxTransactionsTimestampRangeNs`-style convention, e.g. 30 days)                                         |
| `type`         | string  | Filter by message type                                                                                                                                                                                                | none    | One of `DATA`, `RESPONSE`, `CONTROL`; **requires** `timestamp` to also be supplied (see below)                                                                                                                                              |
| `limit`        | integer | Maximum number of messages to return                                                                                                                                                                                  | `25`    | Must be between 1 and 100                                                                                                                                                                                                                   |
| `order`        | string  | Sort order for results                                                                                                                                                                                                | `desc`  | Must be either `asc` or `desc`                                                                                                                                                                                                              |

With no filters, `channel_id` alone already lets Postgres take the latest `limit` rows for this Channel directly off
`clpr_message__channel_id_timestamp`, without touching partitions it doesn't need — the same `ORDER BY ... LIMIT`
behavior any indexed column gets (`message_id` breaks ties — see its Query Parameters row above). `connector.id`
pins the query to `clpr_message__connector_id` instead — a direct,
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

Lists `clpr_channel_pending_commitment` rows with `status = 'OPEN'` — genuinely still pending, neither completed
nor abandoned. `COMPLETED` rows have already been promoted to a real `clpr_channel` (real consensus state never
removes their commitment entry — see [Transaction & Query Inventory](#transaction--query-inventory) row 119 — so
without this filter, completed Channels would incorrectly still show up here indefinitely); `DELETED` rows were
abandoned via `ClprCloseChannel`. This filter is served by `clpr_channel_pending_commitment__timestamp`, a partial
index covering only `OPEN` rows (see [Database Schema Design](#database-schema-design)). `channel_id` is never
known for these rows; they're keyed only by `ownership_commitment`. `created_timestamp` below is
`lower(timestamp_range)`.

Response format:

```json
{
  "pending_channels": [
    {
      "ownership_commitment": "0x7ab3f1...",
      "status": "OPEN",
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
`clpr_connector_pending_commitment` rows with `status = 'OPEN'` (`DELETED` is reserved for schema symmetry with the
Channel side; no handler sets it for Connectors today — see [Domain Models](#1-domain-models)). Served by
`clpr_connector_pending_commitment__timestamp` (see [Database Schema Design](#database-schema-design)).
`created_timestamp` below is `lower(timestamp_range)`.

Response format:

```json
{
  "pending_connectors": [
    {
      "commitment": "0x9c2e4a...",
      "status": "OPEN",
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

### 5. Connectors API

```
GET /api/v1/clpr/connectors
```

`channel.id` is optional only when no other filter is supplied, returning a scatter-gathered listing across every
Channel. If `connector.id`, `deleted`, or `timestamp` is also supplied, `channel.id` becomes mandatory, to keep the
query shard-local in `v2` (see [Database Schema Design](#database-schema-design)).

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
        "key": "302a300506032b6570032100e5b2…"
      },
      "locked_stake": "100000000",
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
    "next": "/api/v1/clpr/connectors?channel.id=eq:0x1a2b3c...&timestamp=lt:1726874345.123456789&limit=1"
  }
}
```

#### Query Parameters

| Parameter      | Type    | Description                                       | Default | Validation                                                                                                                                      |
| -------------- | ------- | ------------------------------------------------- | ------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| `channel.id`   | string  | Filter by `channel_id`                            | none    | Supports `eq:` only; omit to list Connectors across every Channel; required whenever `connector.id`, `deleted`, or `timestamp` is also supplied |
| `connector.id` | string  | Filter by `connector_id`                          | none    | Supports `eq:` only; requires `channel.id`                                                                                                      |
| `deleted`      | boolean | Filter by removal status (deregistered or banned) | none    | Supports `true`/`false`; omit to return both removed and still-registered Connectors; requires `channel.id`                                     |
| `timestamp`    | string  | Filter/paginate by `created_timestamp`            | none    | Supports `eq:`, `gt:`, `gte:`, `lt:`, `lte:`; combined range must not exceed the configured max window (e.g. 30 days); requires `channel.id`    |
| `limit`        | integer | Maximum number of Connectors to return            | `25`    | Must be between 1 and 100                                                                                                                       |
| `order`        | string  | Sort order for results                            | `desc`  | Must be either `asc` or `desc`                                                                                                                  |

**Examples:**

- `/api/v1/clpr/connectors?limit=10` — Get the 10 newest Connectors across every Channel (scatter-gathers in `v2`)
- `/api/v1/clpr/connectors?channel.id=eq:0x1a2b3c...` — Get all Connectors on a Channel, newest first
- `/api/v1/clpr/connectors?channel.id=eq:0x1a2b3c...&limit=10` — Get first 10 Connectors on a Channel
- `/api/v1/clpr/connectors?channel.id=eq:0x1a2b3c...&connector.id=eq:0x4d5e6f...` — Get a specific Connector by id
- `/api/v1/clpr/connectors?channel.id=eq:0x1a2b3c...&deleted=false` — Get only still-registered Connectors on a
  Channel

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
   to the CAIP-2 format (`namespace:reference`, well under 50 bytes for any conformant value) — we'd like the
   consensus-node team to enforce an explicit maximum length at the protocol level, rather than relying solely on
   implementations emitting conformant values.

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
- Connector ban path: repeated slashing until `slash_count` crosses `clpr.slashBanThreshold`, asserting
  `clpr_connector.deleted = true` with `stake_recipient_id IS NULL` (distinguishing it from a voluntary
  `deregisterConnector`, see [Domain Models](#1-domain-models)).
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
Channels, Messages per Channel, Pending Channels, Pending Connectors, and Connectors.

Staging environment needs, to exercise every endpoint variant:

- At least one `ACTIVE` Channel with Connectors and messages of all three types (Data, Response, Control), so
  Messages per Channel (plain, `type`-filtered, and `connector.id`-filtered) and Connectors have real
  rows to page through.
- At least one still-pending Channel commitment and one still-pending Connector commitment, to exercise the two
  Pending APIs.
- At least one Channel in a non-`ACTIVE` status, to exercise `status`-filtered queries on Channels.

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
- Per the Citus (`v2`) distribution strategy in [Database Schema Design](#database-schema-design), `Messages per
Channel` (`channelId` is a required path param) is always shard-local. `Connectors` and `Channels` are
  shard-local only when filtered by `channel.id` (see [Connectors API](#5-connectors-api)/
  [Channels API](#1-channels-api)); `chain.id` isn't queryable yet, so filtering `Channels` by peer chain remains
  out of scope until that's revisited (see [Channels API](#1-channels-api)).

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
   `ClprDeregisterConnector` handlers plus `ClprCompleteConnectorTransformer` for the Connector fields read from
   state.
4. **Importer: bundle/message ingestion.** `ClprSubmitBundleTransactionHandler` plus `ClprSubmitBundleTransformer`
   that expands a bundle's verifier-dispatched messages into `clpr_message` rows; depends on (1) and (2).
5. **Importer: ledger configuration and endpoint manifest ingestion.** `ClprUpdateLedgerConfigurationTransactionHandler`
   plus `ClprUpdateLedgerConfigurationTransformer` for the immutable `chain_id`/`protocol_version` fields, plus
   handling for the endpoint manifest singleton.
6. **REST API: the five read endpoints scoped in [REST API Implementation](#rest-api-implementation)** — Channels,
   Messages per channel, Pending channels, Pending connectors, and Connectors — including k6
   test coverage for each (see [k6 Tests](#4-k6-tests)).
7. **Acceptance tests for the CLPR Channel/Connector lifecycle**, gated on a working verifier and a
   CLPR-enabled test network being available to the acceptance suite.
8. **Monitor support** for synthetic CLPR transaction generation and 10k+ TPS load testing — see
   [Monitor](#monitor). Blocked on Hiero Java SDK CLPR support (or a raw-protobuf workaround) landing first; the
   stateful commit-reveal supplier design is new ground for the module, not a copy of an existing pattern.
