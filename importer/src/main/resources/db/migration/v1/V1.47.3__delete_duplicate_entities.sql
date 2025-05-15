-- Remove duplicate entities from entity and contract tables.

delete
from entity
where lower(timestamp_range) <= 0
  and id in (select id from contract);
