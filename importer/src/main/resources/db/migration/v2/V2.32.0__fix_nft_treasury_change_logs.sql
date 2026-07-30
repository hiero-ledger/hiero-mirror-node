-- Remove synthetic ERC-721 Transfer logs created for NFT treasury changes.
-- Consensus records these as a single wildcard NFT transfer with serial number -1
-- (topic3 = 0xffffffffffffffff). That value is not a valid ERC-721 serial number.
delete from contract_log cl
where cl.topic3 = '\xffffffffffffffff'
  and cl.topic0 = '\xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef'
  and exists (
        select 1
        from token t
        where t.token_id = cl.contract_id
          and t.type = 'NON_FUNGIBLE_UNIQUE'
  );
