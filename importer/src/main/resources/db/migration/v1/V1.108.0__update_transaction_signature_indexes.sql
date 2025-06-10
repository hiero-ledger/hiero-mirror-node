drop index if exists transaction_signature__timestamp_public_key_prefix;
drop index if exists transaction_signature__entity_id;

create index if not exists transaction_signature__entity_id
    on transaction_signature (entity_id desc, consensus_timestamp desc);