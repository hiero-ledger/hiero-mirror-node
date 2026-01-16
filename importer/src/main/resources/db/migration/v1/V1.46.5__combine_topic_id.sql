-------------------
-- Replace the use of realm_num and topic_num with topic_id
-- When created the topic_message table used separate columns for realm and num. These should be reserved for entities tables
-------------------

-- create temporary function to encode integers
create function encodeEntityId(shard bigint, realm bigint, num bigint)
    returns entity_id as
$$
begin
    -- Encoding: 15 bits for shard (mask = 0x7fff = 32767), followed by 16 bits for realm (mask = 0xffff = 65535),
    -- followed by 32 bits for num (max = 0xffffffff = 4294967295)
    return (num & 4294967295) | ((realm & 65535) << 32) | ((shard & 32767) << 48);
end
$$ language plpgsql;

-- add new topic_id column
alter table if exists topic_message
    add column if not exists topic_id bigint null;

-- Compute encoded ids.
update topic_message tm
set topic_id
        = encodeEntityId
        (0, realm_num, topic_num);

-- drop indexes
drop index if exists topic_message__realm_num_timestamp;
drop index if exists topic_message__topic_num_realm_num_seqnum;

-- set new topic_id to not null and drop realm_num and topic_num
alter table if exists topic_message
    alter column topic_id set not null,
    drop column realm_num,
    drop column topic_num;

-- create updated indexes
create index if not exists topic_message__topic_id_timestamp
    on topic_message (topic_id, consensus_timestamp);

create unique index if not exists topic_message__topic_id_seqnum
    on topic_message (topic_id, sequence_number);

-- remove function
drop function if exists encodeEntityId(shard bigint, realm bigint, num bigint);
