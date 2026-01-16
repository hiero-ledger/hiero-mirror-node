drop index if exists nft_history__timestamp_range;
create index if not exists nft_history__account_timestamp_range
  on nft_history using gist (account_id, timestamp_range);
