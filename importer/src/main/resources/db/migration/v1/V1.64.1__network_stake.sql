create table if not exists network_stake
(
    consensus_timestamp              bigint not null,
    epoch_day                        bigint not null,
    max_staking_reward_rate_per_hbar bigint not null,
    node_reward_fee_denominator      bigint not null,
    node_reward_fee_numerator        bigint not null,
    stake_total                      bigint not null,
    staking_period                   bigint not null,
    staking_period_duration          bigint not null,
    staking_periods_stored           bigint not null,
    staking_reward_fee_denominator   bigint not null,
    staking_reward_fee_numerator     bigint not null,
    staking_reward_rate              bigint not null,
    staking_start_threshold          bigint not null,
    primary key (consensus_timestamp)
);

alter table if exists node_stake
    drop column if exists stake_total;
