create or replace function get_transaction_info_by_hash(transactionHash bytea)
returns table (
  consensus_timestamp bigint,
  hash                bytea,
  payer_account_id    bigint
)
language plpgsql
as $$
declare
    shortHash    bytea;
    cutoffTsNs bigint;
    recent_rows  bigint;
begin
shortHash := substring(transactionHash from 1 for 32);
cutoffTsNs := (
extract(epoch from date_trunc('month', now() - interval ${transactionHashLookbackInterval})) * 1e9
)::bigint;

return query
select t.consensus_timestamp, (t.hash || coalesce(t.hash_suffix, ''::bytea)) as hash, t.payer_account_id
from transaction_hash t
where t.consensus_timestamp >= cutoffTsNs
  and t.hash = shortHash;

get diagnostics recent_rows = row_count;

if recent_rows = 0 then
    return query
    select t.consensus_timestamp, (t.hash || coalesce(t.hash_suffix, ''::bytea)) as hash, t.payer_account_id
    from transaction_hash t
    where t.consensus_timestamp < cutoffTsNs
      and t.hash = shortHash;
end if;
end
$$;

