drop index if exists ethereum_transaction__hash;
create index if not exists ethereum_transaction__hash on ethereum_transaction using hash (hash);

alter table if exists event_file drop constraint if exists event_file__hash;
create index if not exists event_file__hash on event_file using hash (hash);
