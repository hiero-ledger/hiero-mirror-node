-- HIP-331 Account NFTs REST API

-- add new index to support multiple query forms
create index if not exists nft__account_token_serialnumber on nft(account_id, token_id, serial_number);

-- drop old index
drop index if exists nft__account_token;
