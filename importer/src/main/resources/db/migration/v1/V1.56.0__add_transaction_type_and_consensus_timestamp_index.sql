-- switch out the existing transaction type index with a multi-column index on transaction type and consensus timestamp to speed up type-filtered, timestamp-ordered queries on transactions

drop index if exists transaction_type;

create index if not exists transaction__type_consensus_timestamp on transaction (type, consensus_timestamp);