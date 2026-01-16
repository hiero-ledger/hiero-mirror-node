--- drop the old index and create a new index on (payer_account_id, consensus_ns)
drop index if exists transaction__payer_account_id;
create index if not exists transaction__payer_account_id_consensus_ns on transaction (payer_account_id, consensus_ns);
