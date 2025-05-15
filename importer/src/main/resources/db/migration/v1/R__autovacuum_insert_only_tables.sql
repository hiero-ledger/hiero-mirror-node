-------------------
-- autovacuum insert-only tables more frequently to ensure most pages are visible for index-only scans
-------------------

-- reset table storage parameters set prior to postgresql 13
alter table if exists crypto_transfer reset (
    autovacuum_freeze_max_age,
    autovacuum_freeze_table_age,
    autovacuum_freeze_min_age,
    log_autovacuum_min_duration
    );

alter table if exists token_transfer reset (
    autovacuum_freeze_max_age,
    autovacuum_freeze_table_age,
    autovacuum_freeze_min_age,
    log_autovacuum_min_duration
    );

alter table if exists transaction reset (
    autovacuum_freeze_max_age,
    autovacuum_freeze_table_age,
    autovacuum_freeze_min_age,
    log_autovacuum_min_duration
    );

-- auto vacuum daily with 15-minute interval account balance files
alter table if exists account_balance_file set (
  autovacuum_vacuum_insert_scale_factor = 0,
  autovacuum_vacuum_insert_threshold = 96
  );

-- based on 12 contract actions per smart contract transaction and max 300 TPS
alter table if exists contract_action set (
  autovacuum_vacuum_insert_scale_factor = 0,
  autovacuum_vacuum_insert_threshold = 2160000
  );

-- base on average of 3 contract logs per smart contract transaction and max 300 TPS
alter table if exists contract_log set (
  autovacuum_vacuum_insert_scale_factor = 0,
  autovacuum_vacuum_insert_threshold = 540000
  );

-- max 300 smart contract transactions per second
alter table if exists contract_result set (
  autovacuum_vacuum_insert_scale_factor = 0,
  autovacuum_vacuum_insert_threshold = 180000
  );

-- based on average of 10 state changes per smart contract transaction and max 300 TPS
alter table if exists contract_state_change set (
  autovacuum_vacuum_insert_scale_factor = 0,
  autovacuum_vacuum_insert_threshold = 1800000
  );

-- autovacuum every 10 minutes at 10K TPS with average of 4 crypto transfers per transaction
alter table if exists crypto_transfer set (
  autovacuum_vacuum_insert_scale_factor = 0,
  autovacuum_vacuum_insert_threshold = 24000000
  );

-- adjust when ethereum transaction TPS becomes higher
alter table if exists ethereum_transaction set (autovacuum_vacuum_insert_scale_factor = 0.1);

-- autovacuum every 60 minutes with 2s interval record files
alter table if exists record_file set (
  autovacuum_vacuum_insert_scale_factor = 0,
  autovacuum_vacuum_insert_threshold = 1800
  );

-- autovacuum every 60 minutes with 2s interval record files. Note normally there is just one sidecar record
-- per each record file
alter table if exists sidecar_file set (
  autovacuum_vacuum_insert_scale_factor = 0,
  autovacuum_vacuum_insert_threshold = 1800
  );

-- threshold set based on recent stats so the table has about 2 to 3 automatic vacuums per day, adjust if there's more
-- daily staking reward claims
alter table if exists staking_reward_transfer set (
  autovacuum_vacuum_insert_scale_factor = 0,
  autovacuum_vacuum_insert_threshold = 4000
  );

-- autovacuum every 10 minutes at 10K TPS crypto transfer transactions with two token transfer rows per transaction
alter table if exists token_transfer set (
  autovacuum_vacuum_insert_scale_factor = 0,
  autovacuum_vacuum_insert_threshold = 12000000
  );

-- autovacuum every 10 minutes at 10K TPS consensus submit message transactions
alter table if exists topic_message set (
  autovacuum_vacuum_insert_scale_factor = 0,
  autovacuum_vacuum_insert_threshold = 6000000
  );

-- autovacuum every 10 minutes at 10K TPS
alter table if exists transaction set (
  autovacuum_vacuum_insert_scale_factor = 0,
  autovacuum_vacuum_insert_threshold = 6000000
  );
