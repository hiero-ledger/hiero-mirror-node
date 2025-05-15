drop index if exists transaction_hash__hash;
create index transaction_hash__hash on transaction_hash using hash (substring(hash from 1 for 32));
