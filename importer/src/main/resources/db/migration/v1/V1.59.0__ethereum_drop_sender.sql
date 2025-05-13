-------------------
-- Drop ethereum_transaction.from_address as sender can be null and duplicates contract_result.sender_id
-------------------

alter table if exists ethereum_transaction
    drop column if exists from_address;
