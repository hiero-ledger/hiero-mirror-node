ALTER TABLE t_entities ADD COLUMN exp_time_ns BIGINT;
ALTER TABLE t_account_balance_history ADD COLUMN snapshot_time_ns BIGINT;
ALTER TABLE t_transactions ADD COLUMN valid_start_ns BIGINT;
ALTER TABLE t_transactions ADD COLUMN consensus_ns BIGINT;

-- migrate existing data
UPDATE t_entities set exp_time_ns = exp_time_seconds * 1000000000 + exp_time_nanos;
UPDATE t_account_balance_history set snapshot_time_ns = seconds * 1000000000 + nanos;
UPDATE t_transactions set valid_start_ns = vs_seconds * 1000000000 + vs_nanos;
UPDATE t_transactions set consensus_ns = consensus_seconds * 1000000000 + consensus_nanos;

-- add not null constraints
ALTER TABLE t_account_balance_history ALTER COLUMN snapshot_time_ns SET NOT NULL;
ALTER TABLE t_transactions ALTER COLUMN valid_start_ns SET NOT NULL;
ALTER TABLE t_transactions ALTER COLUMN consensus_ns SET NOT NULL;
