create table if not exists transaction_hash
(
  consensus_timestamp bigint not null,
  hash                bytea  not null
);

create index if not exists transaction_hash__hash on transaction_hash using hash (hash);
