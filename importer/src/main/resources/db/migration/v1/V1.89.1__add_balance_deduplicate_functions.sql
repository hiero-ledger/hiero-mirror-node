-- Add functions to deduplicate existing account and token balance snapshots. The async migration will drop the
-- functions when it's completed

create or replace function create_full_account_balance_snapshot(
  balance_timestamp bigint,
  prev_balance_timestamp bigint)
returns int as
$$
declare
  count int;
begin
  with previous as (
    select *
    from account_balance_old
    where consensus_timestamp = prev_balance_timestamp
  ), current as (
    select *
    from account_balance_old
    where consensus_timestamp = balance_timestamp
  )
  insert into account_balance (account_id, balance, consensus_timestamp)
  select coalesce(c.account_id, p.account_id), coalesce(c.balance, 0), balance_timestamp
  from current as c
  full outer join previous as p using (account_id);

  get diagnostics count = row_count;
  return count;
end;
$$ language plpgsql;

create or replace function create_deduped_account_balance_snapshot(
  balance_timestamp bigint,
  prev_balance_timestamp bigint)
returns int as
$$
declare
  count int;
begin
  with previous as (
    select *
    from account_balance_old
    where consensus_timestamp = prev_balance_timestamp
  ), current as (
    select *
    from account_balance_old
    where consensus_timestamp = balance_timestamp
  )
  insert into account_balance (account_id, balance, consensus_timestamp)
  select coalesce(c.account_id, p.account_id), coalesce(c.balance, 0), balance_timestamp
  from current as c
  full outer join previous as p using (account_id)
  -- add an account balance row if any of the following is true:
  --   - it's treasury account (id = 2)
  --   - its balance no longer exists in the current snapshot (i.e., the account is deleted between the two snapshots)
  --   - its balance has changed since last snapshot
  where coalesce(c.account_id, 0) = 2 or c.balance is null or (p.balance is null or c.balance <> p.balance);

  get diagnostics count = row_count;
  return count;
end;
$$ language plpgsql;

create or replace function create_full_token_balance_snapshot(
  balance_timestamp bigint,
  prev_balance_timestamp bigint)
returns int as
$$
declare
  count int;
begin
  with previous as (
    select *
    from token_balance_old
    where consensus_timestamp = prev_balance_timestamp
  ), current as (
    select *
    from token_balance_old
    where consensus_timestamp = balance_timestamp
  )
  insert into token_balance (account_id, balance, consensus_timestamp, token_id)
  select
    coalesce(c.account_id, p.account_id),
    coalesce(c.balance, 0),
    balance_timestamp,
    coalesce(c.token_id, p.token_id)
  from current as c
  full outer join previous as p using (account_id, token_id);

  get diagnostics count = row_count;
  return count;
end;
$$ language plpgsql;

create or replace function create_deduped_token_balance_snapshot(
  balance_timestamp bigint,
  prev_balance_timestamp bigint)
returns int as
$$
declare
  count int;
begin
  with previous as (
    select *
    from token_balance_old
    where consensus_timestamp = prev_balance_timestamp
  ), current as (
    select *
    from token_balance_old
    where consensus_timestamp = balance_timestamp
  )
  insert into token_balance (account_id, balance, consensus_timestamp, token_id)
  select
    coalesce(c.account_id, p.account_id),
    coalesce(c.balance, 0),
    balance_timestamp,
    coalesce(c.token_id, p.token_id)
  from current as c
  full outer join previous as p using (account_id, token_id)
  -- insert a row if its balance no longer exists (i.e., the token association no longer exists at balance_timestamp),
  -- or its balance has changed since last snapshot
  where c.balance is null or (p.balance is null or c.balance <> p.balance);

  get diagnostics count = row_count;
  return count;
end;
$$ language plpgsql;

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
