create table if not exists staking_reward_transfer
(
    account_id          bigint not null,
    amount              bigint not null,
    consensus_timestamp bigint not null,
    payer_account_id    bigint not null,
    primary key (consensus_timestamp, account_id)
);
