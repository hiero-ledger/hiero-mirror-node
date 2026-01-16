-------------------
-- Fill missing entity created_timestamp and modified_timestamp
-------------------

-- update entity rows based on successful entity create/update/delete transaction types
with entity_timestamp as (
    select entity_id, min(consensus_ns) created_timestamp, max(consensus_ns) modified_timestamp
    from transaction
    where result = 22
      and entity_id is not null
      and type in (8, 9, 11, 12, 15, 17, 18, 19, 20, 21, 22, 24, 25, 26, 29, 35, 36, 42, 43)
    group by entity_id
)
update entity
set created_timestamp = coalesce(entity.created_timestamp, entity_timestamp.created_timestamp),
    modified_timestamp = entity_timestamp.modified_timestamp
from entity_timestamp
where entity.id = entity_timestamp.entity_id;

-- mark schedule entity as deleted and set modified timestamp to the consensus_ns of the schedule delete transaction
update entity
set deleted = true,
    modified_timestamp = consensus_ns
from transaction
where result = 22
  and transaction.type = 43
  and entity_id is not null
  and id = entity_id;
