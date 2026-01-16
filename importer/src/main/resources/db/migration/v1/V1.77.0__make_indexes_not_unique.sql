-- replace the "unique" currently-existing indices with non-"unique" versions of the same index.

drop index if exists account_balance_file__name;
create index if not exists account_balance_file__name on account_balance_file(name);

drop index if exists nft_transfer__token_id_serial_num_timestamp;
create index if not exists nft_transfer__token_id_serial_num_timestamp
  on nft_transfer(token_id desc, serial_number desc, consensus_timestamp desc);

drop index if exists record_file__hash;
create index if not exists record_file__hash on record_file (hash collate "C");

drop index if exists transaction_signature__timestamp_public_key_prefix;
create index if not exists transaction_signature__timestamp_public_key_prefix
    on transaction_signature (consensus_timestamp desc, public_key_prefix);

-- one last unique index -- topic_message__topic_id_seqnum -- still remains, but it would be very expensive to rebuild
-- (with 4 billion rows as of March 1, 2023).
