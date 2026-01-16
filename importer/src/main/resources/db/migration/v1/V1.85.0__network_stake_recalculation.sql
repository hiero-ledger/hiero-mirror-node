with updated_stake as (
    select consensus_timestamp, sum(stake_rewarded + stake_not_rewarded) as stake
    from node_stake group by consensus_timestamp
)
update network_stake ns
set stake_total=us.stake
    from updated_stake us
where ns.consensus_timestamp = us.consensus_timestamp;