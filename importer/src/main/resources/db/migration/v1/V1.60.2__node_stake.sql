create table if not exists node_stake
(
  consensus_timestamp bigint not null,
  epoch_day           bigint not null,
  node_id             bigint not null,
  reward_rate         bigint not null,
  reward_sum          bigint not null,
  stake               bigint not null,
  stake_rewarded      bigint not null,
  stake_total         bigint not null,
  staking_period      bigint not null,
  primary key (epoch_day, node_id)
);
