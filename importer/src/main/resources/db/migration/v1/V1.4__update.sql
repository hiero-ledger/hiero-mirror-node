CREATE INDEX idx_t_entities_exp_t_ns ON t_entities (exp_time_ns);
CREATE INDEX idx_t_account_balance_hist_snap_ns_acc ON t_account_balance_history (snapshot_time_ns, fk_balance_id);
CREATE INDEX idx_t_account_balance_hist_snap_ns ON t_account_balance_history (snapshot_time_ns);
CREATE UNIQUE INDEX idx_t_transactions_vs_ns ON t_transactions (valid_start_ns);
CREATE UNIQUE INDEX idx_t_transactions_cs_ns ON t_transactions (consensus_ns);

ALTER TABLE t_entities DROP COLUMN balance;
