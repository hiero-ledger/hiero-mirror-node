create or replace view mirror_node_time_partitions as
select
  child.relname as name,
  parent.relname as parent,
  -- extract the from_timestamp and to_timestamp from the string "FOR VALUES FROM ('xxx') TO ('yyy')"
  substring(pg_get_expr(child.relpartbound, child.oid) from $$FROM \('(\d+)'\)$$)::bigint as from_timestamp,
  substring(pg_get_expr(child.relpartbound, child.oid) from $$TO \('(\d+)'\)$$)::bigint as to_timestamp
from pg_inherits
join pg_class as parent on pg_inherits.inhparent = parent.oid
join pg_class as child on pg_inherits.inhrelid = child.oid
where child.relkind = 'r' and pg_get_expr(child.relpartbound, child.oid) similar to $$FOR VALUES FROM \('[0-9]+'\) TO \('[0-9]+'\)$$
order by parent, from_timestamp;
