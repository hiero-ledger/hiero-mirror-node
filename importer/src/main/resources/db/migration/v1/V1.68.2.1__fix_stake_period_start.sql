-- fix stake_period_start after a reward payout
with rewarded as (
  select distinct account_id from staking_reward_transfer
), fix_entity_history as (
  update entity_history p
  set stake_period_start = extract(days from (timezone('utc', to_timestamp(consensus_timestamp / 1000000000)) - '1970-01-01')) - 1
  from (
    select account_id, timestamp_range, consensus_timestamp
    from rewarded r
    join entity_history e on e.id = r.account_id
    join lateral (
      select consensus_timestamp
      from staking_reward_transfer srt
      where srt.account_id = r.account_id and e.timestamp_range @> consensus_timestamp
        and lower(e.timestamp_range) < consensus_timestamp
      order by consensus_timestamp desc
      limit 1
    ) last_staking_reward on true
  ) incorrect
  where account_id = id and p.timestamp_range = incorrect.timestamp_range
)
update entity
set stake_period_start = extract(days from (timezone('utc', to_timestamp(consensus_timestamp / 1000000000)) - '1970-01-01')) - 1
from (
  select account_id, consensus_timestamp
  from rewarded r
  join entity e on e.id = r.account_id
  join lateral (
    select consensus_timestamp
    from staking_reward_transfer srt
    where srt.account_id = r.account_id and lower(e.timestamp_range) < consensus_timestamp
    order by consensus_timestamp desc
    limit 1
  ) last_staking_reward on true
) incorrect
where account_id = id;
