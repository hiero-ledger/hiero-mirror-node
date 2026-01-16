-------------------
-- Add index to speed up nft transfer query for the combination of token id, serial number, and optional timestamp
-------------------

-- drop the big index and replace it with the index on consensus_timestamp
drop index if exists nft_transfer__timestamp_token_id_serial_num;
create index if not exists nft_transfer__timestamp on nft_transfer(consensus_timestamp desc);

-- add the index to speed up nft transfer query
create unique index if not exists nft_transfer__token_id_serial_num_timestamp
  on nft_transfer(token_id desc, serial_number desc, consensus_timestamp desc);
