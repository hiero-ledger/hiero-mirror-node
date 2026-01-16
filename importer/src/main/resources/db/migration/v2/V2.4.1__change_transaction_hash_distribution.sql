drop index if exists transaction_hash__hash;

alter table if exists transaction_hash add column if not exists distribution_id smallint;
update transaction_hash
set distribution_id = ('x' || encode(substring(hash from 1 for 2), 'hex'))::bit(32)::int >> 16;

select alter_distributed_table('transaction_hash', distribution_column := 'distribution_id');
create index if not exists transaction_hash__hash on transaction_hash using hash (substring(hash from 1 for 32));
