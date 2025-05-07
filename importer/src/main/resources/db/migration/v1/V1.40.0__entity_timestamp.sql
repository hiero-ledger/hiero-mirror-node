-------------------
-- Fill missing entity created_timestamp and modified_timestamp
-------------------

-- set created and modified timestamp based on entity creating or modifying transaction types
with entity__timestamp_map as (
  select entity_id, min(consensus_ns) as created_timestamp, max(consensus_ns) as modified_timestamp
  from transaction
  where result = 22
    and entity_id is not null
    and consensus_ns > coalesce((
        select modified_timestamp
        from entity
        where modified_timestamp is not null
        order by modified_timestamp desc
        limit 1
      ), 0)
  group by entity_id
  order by entity_id
)
update entity
set created_timestamp  = coalesce(entity.created_timestamp, entity__timestamp_map.created_timestamp),
    modified_timestamp = entity__timestamp_map.modified_timestamp
from entity__timestamp_map
where id = entity__timestamp_map.entity_id;
