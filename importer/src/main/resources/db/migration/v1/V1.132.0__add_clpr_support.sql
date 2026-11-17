-- HIP-1535 CLPR Mirror Node Support

create type clpr_pending_commitment_status as enum ('OPEN', 'COMPLETED', 'DELETED');
create type clpr_connector_status as enum ('PENDING', 'ACTIVE', 'DELETED');

create table if not exists clpr_channel_pending_commitment
(
    created_timestamp     bigint                           not null,
    timestamp_range       int8range                        not null,
    status                clpr_pending_commitment_status   not null default 'OPEN',
    ownership_commitment  bytea                            not null,

    primary key (ownership_commitment)
);

-- Partial index backing the Pending Channels API filter
create index if not exists clpr_channel_pending_commitment__created_timestamp
    on clpr_channel_pending_commitment (created_timestamp)
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
    timestamp_range                 int8range             not null,
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
    -- Raw com.hederahashgraph.api.proto.java.ClprChannelStatus wire number (0=PENDING, 1=ACTIVE, 2=PAUSED,
    -- 3=CLOSING, 4=DRAINED, 5=CLOSED, or an unrecognized number for a newer value) -- stored as-is rather than a
    -- native enum so an unrecognized value from a newer consensus node isn't lost. smallint and ordered last so
    -- this 2-byte column doesn't force alignment padding in front of the wider columns above.
    status                          smallint              not null default 1,
    -- Raw com.hederahashgraph.api.proto.java.ClprSignatureScheme wire number (0=ECDSA_SECP256K1, 1=ED25519), same
    -- reasoning as status above.
    signature_scheme                smallint,

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

create table if not exists clpr_connector
(
    connector_contract_id    bigint,
    created_timestamp        bigint                  not null,
    in_flight_message_count  bigint                  not null default 0,
    locked_stake             bigint                  not null default 0,
    slash_count              bigint                  not null default 0,
    stake_recipient_id       bigint,
    timestamp_range          int8range               not null,
    status                   clpr_connector_status   not null default 'PENDING',
    admin_key                bytea,
    channel_id               bytea                   not null,
    commitment               bytea                   not null,
    connector_id             bytea,
    public_key               bytea,
    salt                     bytea,
    signature                bytea,
    -- Raw com.hederahashgraph.api.proto.java.ClprSignatureScheme wire number, same reasoning as clpr_channel's
    -- signature_scheme column. smallint and ordered last, same reasoning as clpr_channel's status column.
    signature_scheme         smallint,

    primary key (channel_id, commitment)
);

create unique index if not exists clpr_connector__channel_id_connector_id
    on clpr_connector (channel_id, connector_id)
    where connector_id is not null;

create index if not exists clpr_connector__channel_id_created_timestamp
    on clpr_connector (channel_id, created_timestamp);

create index if not exists clpr_connector__channel_id_status
    on clpr_connector (channel_id, status, created_timestamp);

create table if not exists clpr_connector_history
(
    like clpr_connector including defaults
);

create index if not exists clpr_connector_history__timestamp_range
    on clpr_connector_history using gist (timestamp_range);

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

create table if not exists clpr_maintenance
(
    consensus_timestamp   bigint                      not null,
    -- Raw com.hedera.hapi.services.auxiliary.clpr.legacy.ClprMaintenanceReason wire number, same reasoning as
    -- clpr_channel's status column.
    reason                smallint                    not null,

    primary key (consensus_timestamp)
);

create table if not exists clpr_message
(
    consensus_timestamp    bigint                      not null,
    message_id             bigint                      not null,
    reply_to_message_id    bigint,
    channel_id             bytea                       not null,
    connector_id           bytea,
    control_message        jsonb,
    message_data           bytea,
    running_hash           bytea                       not null,
    sender                 bytea,
    target_application     bytea,
    -- Raw com.hederahashgraph.api.proto.java.ClprMessagePayload oneof field number (1=MESSAGE, 2=MESSAGE_REPLY,
    -- 3=CONTROL, or an unrecognized field number for a future payload variant), same reasoning as clpr_channel's
    -- status column.
    message_type           smallint                    not null,
    -- Raw com.hederahashgraph.api.proto.java.ClprMessageReplyStatus wire number, same reasoning as above.
    reply_status           smallint
) partition by range (consensus_timestamp);

create index if not exists clpr_message__channel_id_timestamp
    on clpr_message (channel_id, consensus_timestamp);

-- Partial index: only DATA messages have a non-null connector_id
create index if not exists clpr_message__connector_id
    on clpr_message (channel_id, connector_id, consensus_timestamp)
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
    partition_from := date_trunc('month', ${partitionStartDate}::timestamp);
    while current_timestamp + ${partitionTimeInterval}::interval >= partition_from loop
        partition_name := format('clpr_message_p%s', to_char(partition_from, 'YYYY_MM'));
        partition_from_ns := extract(epoch from partition_from)::bigint * one_sec_in_ns;
        partition_to := partition_from + ${partitionTimeInterval}::interval;
        partition_to_ns := extract(epoch from partition_to)::bigint * one_sec_in_ns;
        execute format('create table if not exists %I partition of clpr_message for values from (%L) to (%L)',
            partition_name, partition_from_ns, partition_to_ns);
        execute format('alter table %I set (autovacuum_vacuum_insert_scale_factor = 0,' ||
            ' autovacuum_vacuum_insert_threshold = 6000000, parallel_workers = 4)', partition_name);
        partition_from := partition_to;
    end loop;
end;
$$ language plpgsql;

call partition_clpr_message();

drop procedure if exists partition_clpr_message();
