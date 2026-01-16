-------------------
-- Create distributed tables for tables that have mostly insert logic.
-- Colocate where possible with matching entity.
-- Create reference tables for tables used only to describe with rare insert/updates.
-------------------


-- Tables distributed on id
select create_distributed_table('entity', 'id', shard_count := ${shardCount});

-- Tables distributed on hash
select create_distributed_table('contract_transaction_hash', 'hash', shard_count := ${hashShardCount});
select create_distributed_table('transaction_hash', 'hash', shard_count := ${hashShardCount});

-- Tables that will be colocated with tables above
select create_distributed_table('assessed_custom_fee', 'payer_account_id', colocate_with => 'entity');

select create_distributed_table('account_balance', 'account_id', colocate_with => 'entity');

select create_distributed_table('contract', 'id', colocate_with => 'entity');

select create_distributed_table('contract_action', 'payer_account_id', colocate_with => 'entity');

select create_distributed_table('contract_log', 'contract_id', colocate_with => 'entity');

select create_distributed_table('contract_result', 'contract_id', colocate_with => 'entity');

select create_distributed_table('contract_state', 'contract_id', colocate_with => 'entity');

select create_distributed_table('contract_state_change', 'contract_id', colocate_with => 'entity');

select create_distributed_table('contract_transaction', 'entity_id', colocate_with => 'entity');

select create_distributed_table('crypto_allowance', 'owner', colocate_with => 'entity');

select create_distributed_table('crypto_allowance_history', 'owner', colocate_with => 'crypto_allowance');

select create_distributed_table('crypto_transfer', 'payer_account_id', colocate_with => 'entity');

select create_distributed_table('custom_fee', 'token_id', colocate_with => 'entity');

select create_distributed_table('custom_fee_history', 'token_id', colocate_with => 'custom_fee');

select create_distributed_table('entity_history', 'id', colocate_with => 'entity');

select create_distributed_table('entity_stake', 'id', colocate_with => 'entity');

select create_distributed_table('entity_stake_history', 'id', colocate_with => 'entity_stake');

select create_distributed_table('entity_transaction', 'entity_id', colocate_with => 'entity');

select create_distributed_table('ethereum_transaction', 'payer_account_id', colocate_with => 'entity');

select create_distributed_table('file_data', 'entity_id', colocate_with => 'entity');

select create_distributed_table('network_freeze', 'payer_account_id', colocate_with => 'entity');

select create_distributed_table('nft', 'token_id', colocate_with => 'entity');

select create_distributed_table('nft_history', 'token_id', colocate_with => 'nft');

select create_distributed_table('nft_allowance', 'owner', colocate_with => 'entity');

select create_distributed_table('nft_allowance_history', 'owner', colocate_with => 'nft_allowance');

select create_distributed_table('prng', 'payer_account_id', colocate_with => 'entity');

select create_distributed_table('schedule', 'schedule_id', colocate_with => 'entity');

select create_distributed_table('staking_reward_transfer', 'account_id', colocate_with => 'entity');

select create_distributed_table('token', 'token_id', colocate_with => 'entity');

select create_distributed_table('token_history', 'token_id', colocate_with => 'token');

select create_distributed_table('token_account', 'account_id', colocate_with => 'entity');

select create_distributed_table('token_account_history', 'account_id', colocate_with => 'token_account');

select create_distributed_table('token_allowance', 'owner', colocate_with => 'entity');

select create_distributed_table('token_allowance_history', 'owner', colocate_with => 'token_allowance');

select create_distributed_table('token_balance', 'account_id', colocate_with => 'entity');

select create_distributed_table('token_transfer', 'payer_account_id', colocate_with => 'entity');

select create_distributed_table('topic_message', 'topic_id', colocate_with => 'entity');

select create_distributed_table('topic_message_lookup', 'topic_id', colocate_with => 'entity');

select create_distributed_table('transaction', 'payer_account_id', colocate_with => 'entity');

select create_distributed_table('transaction_signature', 'entity_id', colocate_with => 'entity');

-- Reference tables
select create_reference_table('account_balance_file');
