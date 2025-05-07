-- Cleans up bad data in testnet caused by consensus nodes failing to revert child records for unsuccessful parent transactions
with duplicate as (
    select c.id, c.created_timestamp
    from contract c, entity e
    where e.id = c.id
),
unreverted_child_contract as (
    select child.entity_id
    from duplicate dupe
    left join transaction child on child.consensus_timestamp = dupe.created_timestamp and child.entity_id = dupe.id
    left join transaction parent on child.parent_consensus_timestamp = parent.consensus_timestamp
    where child.result in (22, 104, 220) and parent.result not in (22, 104, 220)
),
deleted_contract as (
    delete from contract
    using unreverted_child_contract
    where id = entity_id
    returning id as deleted_id
)
delete from contract_history
using deleted_contract
where id = deleted_id;

-- Cleans up bad data in testnet caused by consensus nodes writing a "ERC-20/721 redirect" token id in the created_contract_ids
with token_contract as (
    select distinct unnest(created_contract_ids) as token_id
    from contract_result
    where array_length(created_contract_ids, 1) > 0
),
duplicate as (
    select id as dupe_id from entity, token_contract
    where id = token_id and type = 'TOKEN'
),
deleted_contract as (
    delete from contract
    using duplicate
    where id = dupe_id
    returning dupe_id
)
delete from contract_history
using deleted_contract
where id = dupe_id;
