drop function if exists get_transaction_info_by_hash(bytea);

create or replace function get_transaction_info_by_hash(transactionhash bytea)
returns table (
  consensus_timestamp bigint,
  hash                bytea,
  payer_account_id    bigint
)
language plpgsql
as $$
declare
    distributionid smallint;
    cutoff_ts_ns   bigint;
    recent_rows    bigint;
begin
distributionid := ('x' || encode(substring(transactionhash from 1 for 2), 'hex'))::bit(32)::int >> 16;
cutoff_ts_ns := (
extract(epoch from date_trunc('month', now() - interval ${transactionHashLookbackInterval})) * 1e9
)::bigint;

return query
select t.consensus_timestamp, t.hash, t.payer_account_id
from transaction_hash t
where t.distribution_id = distributionid
  and t.consensus_timestamp >= cutoff_ts_ns
  and substring(t.hash from 1 for 32) = substring(transactionhash from 1 for 32);

get diagnostics recent_rows = row_count;

if recent_rows = 0 then
    return query
    select t.consensus_timestamp, t.hash, t.payer_account_id
    from transaction_hash t
    where t.distribution_id = distributionid
      and t.consensus_timestamp < cutoff_ts_ns
      and substring(t.hash from 1 for 32) = substring(transactionhash from 1 for 32);
end if;
end
$$;

