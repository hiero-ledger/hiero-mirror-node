-- transaction_ids (valid start timestamp + payer account id) are not unique; remove unique constraint.
-- the transaction_id is not actually part of any where clause in the rest-api, so remove all related index.
drop index if exists idx_t_transactions_transaction_id_unq;
drop index if exists idx_t_transactions_seconds;
drop index if exists idx_t_transactions_vs_ns;

alter table t_transactions drop column vs_seconds;
alter table t_transactions drop column vs_nanos;

-- The t_transactions.valid_start_ns appears in 1 query (transactions.js/getOneTransaction), so add index for that.
create index idx__t_transactions__transaction_id on t_transactions (valid_start_ns, fk_payer_acc_id);