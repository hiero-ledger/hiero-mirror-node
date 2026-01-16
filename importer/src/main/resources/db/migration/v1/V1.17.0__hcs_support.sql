---
--- Add new topic_message table and indexes
--- Change existing tables to support the new entity types, response codes and transaction types
---

-- Add topic_message table
create table if not exists topic_message
(
    consensus_timestamp nanos_timestamp primary key not null,
    realm_num           entity_realm_num            not null,
    topic_num           entity_num                  not null,
    message             bytea                       not null,
    running_hash        bytea                       not null,
    sequence_number     bigint                      not null
);

create index if not exists topic_message__realm_num_timestamp
    on topic_message (realm_num, topic_num, consensus_timestamp);

-- Add topic to t_entity_types
insert into t_entity_types (id, name)
values (4, 'topic');

-- Add submit_key and topic_valid_start_time columns to t_entities
alter table t_entities
    add column if not exists submit_key bytea null;

alter table t_entities
    add column if not exists topic_valid_start_time nanos_timestamp null;

alter table t_entities
    add column if not exists memo text null;

-- Add new HCS related transaction types
insert into t_transaction_types (proto_id, name)
values (24, 'CONSENSUSCREATETOPIC');
insert into t_transaction_types (proto_id, name)
values (25, 'CONSENSUSUPDATETOPIC');
insert into t_transaction_types (proto_id, name)
values (26, 'CONSENSUSDELETETOPIC');
insert into t_transaction_types (proto_id, name)
values (27, 'CONSENSUSSUBMITMESSAGE');

-- Add new HCS related transaction results
insert into t_transaction_results (proto_id, result)
values (150, 'INVALID_TOPIC_ID');
insert into t_transaction_results (proto_id, result)
values (151, 'TOPIC_DELETED');
insert into t_transaction_results (proto_id, result)
values (152, 'MESSAGE_TOO_LONG');
insert into t_transaction_results (proto_id, result)
values (153, 'TOPIC_NOT_ENABLED');
insert into t_transaction_results (proto_id, result)
values (154, 'INVALID_TOPIC_VALID_START_TIME');
insert into t_transaction_results (proto_id, result)
values (155, 'INVALID_TOPIC_EXPIRATION_TIME');
insert into t_transaction_results (proto_id, result)
values (156, 'INVALID_TOPIC_ADMIN_KEY');
insert into t_transaction_results (proto_id, result)
values (157, 'INVALID_TOPIC_SUBMIT_KEY');
insert into t_transaction_results (proto_id, result)
values (158, 'UNAUTHORIZED');
insert into t_transaction_results (proto_id, result)
values (159, 'INVALID_TOPIC_MESSAGE');

-- Define trigger function. Base64 encoding is required since JSON doesn't support binary
create or replace function topic_message_notifier()
    returns trigger
    language plpgsql
as
$$
declare
    topicmsg text := TG_ARGV[0];
begin
    perform (
        with payload(consensus_timestamp, realm_num, topic_num, message, running_hash, sequence_number) as
                 (
                     select NEW.consensus_timestamp,
                            NEW.realm_num,
                            NEW.topic_num,
                            encode(NEW.message, 'base64'),
                            encode(NEW.running_hash, 'base64'),
                            NEW.sequence_number
                 )
        select pg_notify(topicmsg, row_to_json(payload)::text)
        from payload
    );
    return null;
end;
$$;

-- Setup trigger
create trigger topic_message_trigger
    after insert
    on topic_message
    for each row
execute procedure topic_message_notifier('topic_message');
commit;
