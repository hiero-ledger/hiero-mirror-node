-- HIP-1535 CLPR Mirror Node Support
-- Columns are ordered by decreasing alignment to avoid padding waste: bigint/int8range (8-byte), then
-- enum/bytea/varchar/jsonb (4-byte), then boolean last (1-byte).

create type clpr_channel_status as enum ('ACTIVE', 'PAUSED', 'CLOSING', 'DRAINED', 'CLOSED');
create type clpr_signature_scheme as enum ('ECDSA_SECP256K1', 'ED25519');
create type clpr_message_type as enum ('DATA', 'RESPONSE', 'CONTROL');
create type clpr_message_reply_status as enum
    ('SUCCESS', 'APPLICATION_ERROR', 'CONNECTOR_NOT_FOUND', 'CONNECTOR_UNDERFUNDED');

-- Channel tables

create table if not exists clpr_channel_pending_commitment
(
    completed_timestamp   bigint,
    created_timestamp     bigint     not null,
    timestamp_range       int8range  not null,
    ownership_commitment  bytea      not null,
    deleted               boolean    not null default false,

    primary key (ownership_commitment)
);

-- Partial index backing the Pending Channels API filter
create index if not exists clpr_channel_pending_commitment__created_timestamp
    on clpr_channel_pending_commitment (created_timestamp)
    where deleted is false and completed_timestamp is null;

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
    timestamp_range                 int8range             not null,
    status                          clpr_channel_status   not null default 'ACTIVE',
    signature_scheme                clpr_signature_scheme,
    channel_context                 bytea,
    channel_id                      bytea                 not null,
    chain_id                        varchar               not null,
    config_proof_bytes              bytea,
    endpoint_manifest               jsonb,
    endpoint_manifest_proof_bytes   bytea,
    ownership_commitment            bytea                 not null,
    public_key                      bytea,
    received_running_hash           bytea,
    sent_running_hash               bytea,
    signature                       bytea,
    trust_anchor                    bytea,
    trust_anchor_id                 bytea,
    verifier_fingerprint            bytea,

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

-- Connector tables

create table if not exists clpr_connector_pending_commitment
(
    completed_timestamp  bigint,
    created_timestamp    bigint     not null,
    timestamp_range      int8range  not null,
    commitment           bytea      not null,
    deleted              boolean    not null default false,

    primary key (commitment)
);

-- Partial index backing the Pending Connectors API filter
create index if not exists clpr_connector_pending_commitment__created_timestamp
    on clpr_connector_pending_commitment (created_timestamp)
    where deleted is false and completed_timestamp is null;

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
    timestamp_range          int8range   not null,
    signature_scheme         clpr_signature_scheme,
    admin_key                bytea,
    channel_id               bytea       not null,
    connector_id             bytea       not null,
    public_key               bytea,
    salt                     bytea,
    signature                bytea,
    deleted                  boolean,

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

-- Ledger configuration and endpoint manifest singletons: each update is complete and self-sufficient (latest row =
-- highest consensus_timestamp), so no history pair is needed.

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

-- Message queue (append-only). Primary key omitted: a partitioned table's primary key must include the partition
-- column, and consensus_timestamp isn't part of this table's natural identity; clpr_message__channel_id_message_id
-- below covers that lookup instead.

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

-- Partial index: only DATA messages have a non-null connector_id
create index if not exists clpr_message__connector_id
    on clpr_message (connector_id, message_id)
    where connector_id is not null;

-- v1 has no Citus create_time_partitions helper, so partitions are created manually, matching the
-- partition_balance_table() pattern used for account_balance/token_balance (V1.89.2).
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
    partition_from := date_trunc('month', ${clprEnablementDate}::timestamp);
    while current_timestamp + ${partitionTimeInterval}::interval >= partition_from loop
        partition_name := format('clpr_message_p%s', to_char(partition_from, 'YYYY_MM'));
        partition_from_ns := extract(epoch from partition_from)::bigint * one_sec_in_ns;
        partition_to := partition_from + ${partitionTimeInterval}::interval;
        partition_to_ns := extract(epoch from partition_to)::bigint * one_sec_in_ns;
        execute format('create table if not exists %I partition of clpr_message for values from (%L) to (%L)',
            partition_name, partition_from_ns, partition_to_ns);
        execute format('alter table %I set (autovacuum_vacuum_insert_scale_factor = 0,' ||
            ' autovacuum_vacuum_insert_threshold = 10000, parallel_workers = 4)', partition_name);
        partition_from := partition_to;
    end loop;
end;
$$ language plpgsql;

call partition_clpr_message();

drop procedure if exists partition_clpr_message();
