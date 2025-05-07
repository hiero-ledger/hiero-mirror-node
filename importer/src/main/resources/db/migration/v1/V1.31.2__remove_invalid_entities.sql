-------------------
-- Fix entity type mismatches
-------------------

--- Delete entities above the max entity id of successful transactions stored
delete from t_entities where id > (select max(entity_id) from transaction where entity_id is not null and result = 22);

--- Update transaction_types table with the entity type the transaction type acts on
--- Ensures this migration can utilize t_transaction_types mappings with new entity types that may be added
alter table t_transaction_types
    add column if not exists entity_type integer null;

-- crypto accounts
update t_transaction_types set entity_type = 1 WHERE proto_id = 10;
update t_transaction_types set entity_type = 1 WHERE proto_id = 11;
update t_transaction_types set entity_type = 1 WHERE proto_id = 12;
update t_transaction_types set entity_type = 1 WHERE proto_id = 13;
update t_transaction_types set entity_type = 1 WHERE proto_id = 14;
update t_transaction_types set entity_type = 1 WHERE proto_id = 15;
update t_transaction_types set entity_type = 1 WHERE proto_id = 31;
update t_transaction_types set entity_type = 1 WHERE proto_id = 32;
update t_transaction_types set entity_type = 1 WHERE proto_id = 33;
update t_transaction_types set entity_type = 1 WHERE proto_id = 34;
update t_transaction_types set entity_type = 1 WHERE proto_id = 40;
update t_transaction_types set entity_type = 1 WHERE proto_id = 41;

-- contracts
update t_transaction_types set entity_type = 2 WHERE proto_id = 7;
update t_transaction_types set entity_type = 2 WHERE proto_id = 8;
update t_transaction_types set entity_type = 2 WHERE proto_id = 9;
update t_transaction_types set entity_type = 2 WHERE proto_id = 22;

-- files
update t_transaction_types set entity_type = 3 WHERE proto_id = 16;
update t_transaction_types set entity_type = 3 WHERE proto_id = 17;
update t_transaction_types set entity_type = 3 WHERE proto_id = 18;
update t_transaction_types set entity_type = 3 WHERE proto_id = 19;

-- topics
update t_transaction_types set entity_type = 4 WHERE proto_id = 24;
update t_transaction_types set entity_type = 4 WHERE proto_id = 25;
update t_transaction_types set entity_type = 4 WHERE proto_id = 26;
update t_transaction_types set entity_type = 4 WHERE proto_id = 27;

-- tokens
update t_transaction_types set entity_type = 5 WHERE proto_id = 29;
update t_transaction_types set entity_type = 5 WHERE proto_id = 35;
update t_transaction_types set entity_type = 5 WHERE proto_id = 36;
update t_transaction_types set entity_type = 5 WHERE proto_id = 37;
update t_transaction_types set entity_type = 5 WHERE proto_id = 38;
update t_transaction_types set entity_type = 5 WHERE proto_id = 39;

--- Fix entity type mismatches
-- Retrieve a distinct set of entityId and expected entityType results based on successful and entity modifying transactions
-- Find and update entities where fk_entity_type_id is not equal to the expected entity_type in t_transaction_types
with entity_id_type_map as (
    select distinct t.entity_id, tt.entity_type
    from transaction t
    join t_transaction_types tt on t.type = tt.proto_id
    where t.result = 22 and t.entity_id is not null and t.type not in (-1, 20, 21, 23, 28)
)
update t_entities
set fk_entity_type_id = entity_id_type_map.entity_type
from entity_id_type_map
where id = entity_id_type_map.entity_id and fk_entity_type_id <> entity_id_type_map.entity_type;
