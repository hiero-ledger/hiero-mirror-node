create or replace function change_access_privilege(grant_or_revoke boolean) returns void as
$$
begin
  if current_user <> '${db-user}' then
    if grant_or_revoke then
      grant create on schema public to ${db-user};
      grant ${db-user} to current_user;
    else
      revoke ${db-user} from current_user;
      revoke create on schema public from ${db-user};
    end if;
  end if;
end
$$ language plpgsql;

select change_access_privilege(true);

drop materialized view if exists entity_state_start;

create materialized view entity_state_start as
with end_period as (
  select max(consensus_timestamp) as consensus_timestamp from node_stake
), balance_timestamp as (
  with adjusted_balance_file as (
    select
      consensus_timestamp,
      case when hapi_version_major > 0 or hapi_version_minor >= 27 then time_offset + 53
           else time_offset
      end as time_offset
    from account_balance_file,
         lateral (
           select hapi_version_major, hapi_version_minor
           from record_file
           where consensus_end >= consensus_timestamp
           order by consensus_end
           limit 1
         ) as hapi_version
    order by consensus_timestamp desc
  )
  select abf.consensus_timestamp, (abf.consensus_timestamp + abf.time_offset) adjusted_consensus_timestamp
  from adjusted_balance_file abf, end_period ep
  where abf.consensus_timestamp + abf.time_offset <= ep.consensus_timestamp
  order by abf.consensus_timestamp desc
  limit 1
), entity_state as (
  select
    decline_reward,
    id,
    staked_account_id,
    staked_node_id,
    stake_period_start
  from entity, end_period
  where deleted is not true and type in ('ACCOUNT', 'CONTRACT') and timestamp_range @> end_period.consensus_timestamp
  union all
  select *
  from (
        select
          distinct on (id)
          decline_reward,
          id,
          staked_account_id,
          staked_node_id,
          stake_period_start
        from entity_history, end_period
        where deleted is not true and type in ('ACCOUNT', 'CONTRACT') and timestamp_range @> end_period.consensus_timestamp
        order by id, timestamp_range desc
       ) as latest_history
), balance_snapshot as (
  select account_id, balance
  from account_balance ab
  join balance_timestamp bt on bt.consensus_timestamp = ab.consensus_timestamp
)
select
  coalesce(balance, 0) + coalesce(change, 0) as balance,
  decline_reward,
  id,
  coalesce(staked_account_id, 0)             as staked_account_id,
  coalesce(staked_node_id, -1)               as staked_node_id,
  coalesce(stake_period_start, -1)           as stake_period_start
from entity_state
  left join balance_snapshot on account_id = id
  left join (
    select entity_id, sum(amount) as change
    from crypto_transfer ct, balance_timestamp bt, end_period ep
    where ct.consensus_timestamp <= ep.consensus_timestamp
      and ct.consensus_timestamp > bt.adjusted_consensus_timestamp
    group by entity_id
    order by entity_id
  ) balance_change on entity_id = id,
  balance_timestamp bt
where bt.consensus_timestamp is not null;

create unique index if not exists entity_state_start__id on entity_state_start (id);
create index if not exists entity_state_start__staked_account_id
  on entity_state_start (staked_account_id) where staked_account_id <> 0;

alter materialized view entity_state_start owner to ${db-user};

select change_access_privilege(false);

drop function change_access_privilege(grant_or_revoke boolean);
