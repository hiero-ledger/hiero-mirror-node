--
-- Repeatable migration to add foreign key constraints for testing
-- This migration runs only during tests to validate referential integrity
-- but is not applied in production to avoid performance impact
--
-- Generated from analysis of Spring Boot repository objects and database schema
-- These constraints enforce referential integrity that is implied by the application code
--

-- ====================================================================================
-- 1. CONSENSUS_TIMESTAMP FOREIGN KEYS
-- All consensus_timestamp columns should reference transaction.consensus_timestamp
-- ====================================================================================

-- Tables with consensus_timestamp that should reference transaction table
ALTER TABLE public.account_balance_file
    ADD CONSTRAINT fk_account_balance_file_consensus_timestamp
        FOREIGN KEY (consensus_timestamp) REFERENCES public.transaction (consensus_timestamp);

ALTER TABLE public.address_book_entry
    ADD CONSTRAINT fk_address_book_entry_consensus_timestamp
        FOREIGN KEY (consensus_timestamp) REFERENCES public.transaction (consensus_timestamp);

ALTER TABLE public.address_book_service_endpoint
    ADD CONSTRAINT fk_address_book_service_endpoint_consensus_timestamp
        FOREIGN KEY (consensus_timestamp) REFERENCES public.transaction (consensus_timestamp);

ALTER TABLE public.assessed_custom_fee
    ADD CONSTRAINT fk_assessed_custom_fee_consensus_timestamp
        FOREIGN KEY (consensus_timestamp) REFERENCES public.transaction (consensus_timestamp);

ALTER TABLE public.contract_action
    ADD CONSTRAINT fk_contract_action_consensus_timestamp
        FOREIGN KEY (consensus_timestamp) REFERENCES public.transaction (consensus_timestamp);

ALTER TABLE public.contract_log
    ADD CONSTRAINT fk_contract_log_consensus_timestamp
        FOREIGN KEY (consensus_timestamp) REFERENCES public.transaction (consensus_timestamp);

ALTER TABLE public.contract_result
    ADD CONSTRAINT fk_contract_result_consensus_timestamp
        FOREIGN KEY (consensus_timestamp) REFERENCES public.transaction (consensus_timestamp);

ALTER TABLE public.contract_state_change
    ADD CONSTRAINT fk_contract_state_change_consensus_timestamp
        FOREIGN KEY (consensus_timestamp) REFERENCES public.transaction (consensus_timestamp);

ALTER TABLE public.contract_transaction
    ADD CONSTRAINT fk_contract_transaction_consensus_timestamp
        FOREIGN KEY (consensus_timestamp) REFERENCES public.transaction (consensus_timestamp);

ALTER TABLE public.contract_transaction_hash
    ADD CONSTRAINT fk_contract_transaction_hash_consensus_timestamp
        FOREIGN KEY (consensus_timestamp) REFERENCES public.transaction (consensus_timestamp);

ALTER TABLE public.crypto_transfer
    ADD CONSTRAINT fk_crypto_transfer_consensus_timestamp
        FOREIGN KEY (consensus_timestamp) REFERENCES public.transaction (consensus_timestamp);

ALTER TABLE public.entity_transaction
    ADD CONSTRAINT fk_entity_transaction_consensus_timestamp
        FOREIGN KEY (consensus_timestamp) REFERENCES public.transaction (consensus_timestamp);

ALTER TABLE public.ethereum_transaction
    ADD CONSTRAINT fk_ethereum_transaction_consensus_timestamp
        FOREIGN KEY (consensus_timestamp) REFERENCES public.transaction (consensus_timestamp);

ALTER TABLE public.file_data
    ADD CONSTRAINT fk_file_data_consensus_timestamp
        FOREIGN KEY (consensus_timestamp) REFERENCES public.transaction (consensus_timestamp);

ALTER TABLE public.live_hash
    ADD CONSTRAINT fk_live_hash_consensus_timestamp
        FOREIGN KEY (consensus_timestamp) REFERENCES public.transaction (consensus_timestamp);

ALTER TABLE public.network_freeze
    ADD CONSTRAINT fk_network_freeze_consensus_timestamp
        FOREIGN KEY (consensus_timestamp) REFERENCES public.transaction (consensus_timestamp);

ALTER TABLE public.network_stake
    ADD CONSTRAINT fk_network_stake_consensus_timestamp
        FOREIGN KEY (consensus_timestamp) REFERENCES public.transaction (consensus_timestamp);

ALTER TABLE public.node_stake
    ADD CONSTRAINT fk_node_stake_consensus_timestamp
        FOREIGN KEY (consensus_timestamp) REFERENCES public.transaction (consensus_timestamp);

ALTER TABLE public.non_fee_transfer
    ADD CONSTRAINT fk_non_fee_transfer_consensus_timestamp
        FOREIGN KEY (consensus_timestamp) REFERENCES public.transaction (consensus_timestamp);

ALTER TABLE public.prng
    ADD CONSTRAINT fk_prng_consensus_timestamp
        FOREIGN KEY (consensus_timestamp) REFERENCES public.transaction (consensus_timestamp);

ALTER TABLE public.reconciliation_job
    ADD CONSTRAINT fk_reconciliation_job_consensus_timestamp
        FOREIGN KEY (consensus_timestamp) REFERENCES public.transaction (consensus_timestamp);

ALTER TABLE public.schedule
    ADD CONSTRAINT fk_schedule_consensus_timestamp
        FOREIGN KEY (consensus_timestamp) REFERENCES public.transaction (consensus_timestamp);

ALTER TABLE public.staking_reward_transfer
    ADD CONSTRAINT fk_staking_reward_transfer_consensus_timestamp
        FOREIGN KEY (consensus_timestamp) REFERENCES public.transaction (consensus_timestamp);

ALTER TABLE public.token_transfer
    ADD CONSTRAINT fk_token_transfer_consensus_timestamp
        FOREIGN KEY (consensus_timestamp) REFERENCES public.transaction (consensus_timestamp);

ALTER TABLE public.topic_message
    ADD CONSTRAINT fk_topic_message_consensus_timestamp
        FOREIGN KEY (consensus_timestamp) REFERENCES public.transaction (consensus_timestamp);

ALTER TABLE public.transaction_hash
    ADD CONSTRAINT fk_transaction_hash_consensus_timestamp
        FOREIGN KEY (consensus_timestamp) REFERENCES public.transaction (consensus_timestamp);

ALTER TABLE public.transaction_signature
    ADD CONSTRAINT fk_transaction_signature_consensus_timestamp
        FOREIGN KEY (consensus_timestamp) REFERENCES public.transaction (consensus_timestamp);

-- Self reference for parent_consensus_timestamp in transaction table
ALTER TABLE public.transaction
    ADD CONSTRAINT fk_transaction_parent_consensus_timestamp
        FOREIGN KEY (parent_consensus_timestamp) REFERENCES public.transaction (consensus_timestamp);

-- Account and token balance tables with consensus_timestamp
ALTER TABLE public.account_balance
    ADD CONSTRAINT fk_account_balance_consensus_timestamp
        FOREIGN KEY (consensus_timestamp) REFERENCES public.transaction (consensus_timestamp);

ALTER TABLE public.token_balance
    ADD CONSTRAINT fk_token_balance_consensus_timestamp
        FOREIGN KEY (consensus_timestamp) REFERENCES public.transaction (consensus_timestamp);

-- ====================================================================================
-- 2. ENTITY ID FOREIGN KEYS
-- All columns ending with _id (except node_id) should reference entity.id
-- ====================================================================================

-- Transaction table entity references
ALTER TABLE public.transaction
    ADD CONSTRAINT fk_transaction_payer_account_id
        FOREIGN KEY (payer_account_id) REFERENCES public.entity (id);

ALTER TABLE public.transaction
    ADD CONSTRAINT fk_transaction_node_account_id
        FOREIGN KEY (node_account_id) REFERENCES public.entity (id);

ALTER TABLE public.transaction
    ADD CONSTRAINT fk_transaction_entity_id
        FOREIGN KEY (entity_id) REFERENCES public.entity (id);

-- Crypto transfer entity references
ALTER TABLE public.crypto_transfer
    ADD CONSTRAINT fk_crypto_transfer_entity_id
        FOREIGN KEY (entity_id) REFERENCES public.entity (id);

ALTER TABLE public.crypto_transfer
    ADD CONSTRAINT fk_crypto_transfer_payer_account_id
        FOREIGN KEY (payer_account_id) REFERENCES public.entity (id);

-- Non-fee transfer entity references
ALTER TABLE public.non_fee_transfer
    ADD CONSTRAINT fk_non_fee_transfer_entity_id
        FOREIGN KEY (entity_id) REFERENCES public.entity (id);

ALTER TABLE public.non_fee_transfer
    ADD CONSTRAINT fk_non_fee_transfer_payer_account_id
        FOREIGN KEY (payer_account_id) REFERENCES public.entity (id);

-- Token transfer entity references
ALTER TABLE public.token_transfer
    ADD CONSTRAINT fk_token_transfer_token_id
        FOREIGN KEY (token_id) REFERENCES public.entity (id);

ALTER TABLE public.token_transfer
    ADD CONSTRAINT fk_token_transfer_account_id
        FOREIGN KEY (account_id) REFERENCES public.entity (id);

ALTER TABLE public.token_transfer
    ADD CONSTRAINT fk_token_transfer_payer_account_id
        FOREIGN KEY (payer_account_id) REFERENCES public.entity (id);

-- Token table entity references
ALTER TABLE public.token
    ADD CONSTRAINT fk_token_treasury_account_id
        FOREIGN KEY (treasury_account_id) REFERENCES public.entity (id);

-- Token is also an entity itself (one-to-one relationship)
ALTER TABLE public.token
    ADD CONSTRAINT fk_token_token_id
        FOREIGN KEY (token_id) REFERENCES public.entity (id);

-- Token account entity references
ALTER TABLE public.token_account
    ADD CONSTRAINT fk_token_account_account_id
        FOREIGN KEY (account_id) REFERENCES public.entity (id);

ALTER TABLE public.token_account
    ADD CONSTRAINT fk_token_account_token_id
        FOREIGN KEY (token_id) REFERENCES public.entity (id);

-- Token account history entity references
ALTER TABLE public.token_account_history
    ADD CONSTRAINT fk_token_account_history_account_id
        FOREIGN KEY (account_id) REFERENCES public.entity (id);

ALTER TABLE public.token_account_history
    ADD CONSTRAINT fk_token_account_history_token_id
        FOREIGN KEY (token_id) REFERENCES public.entity (id);

-- NFT entity references
ALTER TABLE public.nft
    ADD CONSTRAINT fk_nft_account_id
        FOREIGN KEY (account_id) REFERENCES public.entity (id);

ALTER TABLE public.nft
    ADD CONSTRAINT fk_nft_token_id
        FOREIGN KEY (token_id) REFERENCES public.entity (id);

ALTER TABLE public.nft
    ADD CONSTRAINT fk_nft_spender
        FOREIGN KEY (spender) REFERENCES public.entity (id);

ALTER TABLE public.nft
    ADD CONSTRAINT fk_nft_delegating_spender
        FOREIGN KEY (delegating_spender) REFERENCES public.entity (id);

-- NFT history entity references
ALTER TABLE public.nft_history
    ADD CONSTRAINT fk_nft_history_account_id
        FOREIGN KEY (account_id) REFERENCES public.entity (id);

ALTER TABLE public.nft_history
    ADD CONSTRAINT fk_nft_history_token_id
        FOREIGN KEY (token_id) REFERENCES public.entity (id);

ALTER TABLE public.nft_history
    ADD CONSTRAINT fk_nft_history_spender
        FOREIGN KEY (spender) REFERENCES public.entity (id);

ALTER TABLE public.nft_history
    ADD CONSTRAINT fk_nft_history_delegating_spender
        FOREIGN KEY (delegating_spender) REFERENCES public.entity (id);

-- Contract entity references
ALTER TABLE public.contract
    ADD CONSTRAINT fk_contract_id
        FOREIGN KEY (id) REFERENCES public.entity (id);

ALTER TABLE public.contract
    ADD CONSTRAINT fk_contract_file_id
        FOREIGN KEY (file_id) REFERENCES public.entity (id);

-- Contract result entity references
ALTER TABLE public.contract_result
    ADD CONSTRAINT fk_contract_result_contract_id
        FOREIGN KEY (contract_id) REFERENCES public.entity (id);

ALTER TABLE public.contract_result
    ADD CONSTRAINT fk_contract_result_payer_account_id
        FOREIGN KEY (payer_account_id) REFERENCES public.entity (id);

ALTER TABLE public.contract_result
    ADD CONSTRAINT fk_contract_result_sender_id
        FOREIGN KEY (sender_id) REFERENCES public.entity (id);

-- Contract log entity references
ALTER TABLE public.contract_log
    ADD CONSTRAINT fk_contract_log_contract_id
        FOREIGN KEY (contract_id) REFERENCES public.entity (id);

ALTER TABLE public.contract_log
    ADD CONSTRAINT fk_contract_log_root_contract_id
        FOREIGN KEY (root_contract_id) REFERENCES public.entity (id);

ALTER TABLE public.contract_log
    ADD CONSTRAINT fk_contract_log_payer_account_id
        FOREIGN KEY (payer_account_id) REFERENCES public.entity (id);

-- Contract state change entity references
ALTER TABLE public.contract_state_change
    ADD CONSTRAINT fk_contract_state_change_contract_id
        FOREIGN KEY (contract_id) REFERENCES public.entity (id);

ALTER TABLE public.contract_state_change
    ADD CONSTRAINT fk_contract_state_change_payer_account_id
        FOREIGN KEY (payer_account_id) REFERENCES public.entity (id);

-- Contract state entity references
ALTER TABLE public.contract_state
    ADD CONSTRAINT fk_contract_state_contract_id
        FOREIGN KEY (contract_id) REFERENCES public.entity (id);

-- Token allowance entity references
ALTER TABLE public.token_allowance
    ADD CONSTRAINT fk_token_allowance_owner
        FOREIGN KEY (owner) REFERENCES public.entity (id);

ALTER TABLE public.token_allowance
    ADD CONSTRAINT fk_token_allowance_spender
        FOREIGN KEY (spender) REFERENCES public.entity (id);

ALTER TABLE public.token_allowance
    ADD CONSTRAINT fk_token_allowance_token_id
        FOREIGN KEY (token_id) REFERENCES public.entity (id);

ALTER TABLE public.token_allowance
    ADD CONSTRAINT fk_token_allowance_payer_account_id
        FOREIGN KEY (payer_account_id) REFERENCES public.entity (id);

-- Token allowance history entity references
ALTER TABLE public.token_allowance_history
    ADD CONSTRAINT fk_token_allowance_history_owner
        FOREIGN KEY (owner) REFERENCES public.entity (id);

ALTER TABLE public.token_allowance_history
    ADD CONSTRAINT fk_token_allowance_history_spender
        FOREIGN KEY (spender) REFERENCES public.entity (id);

ALTER TABLE public.token_allowance_history
    ADD CONSTRAINT fk_token_allowance_history_token_id
        FOREIGN KEY (token_id) REFERENCES public.entity (id);

ALTER TABLE public.token_allowance_history
    ADD CONSTRAINT fk_token_allowance_history_payer_account_id
        FOREIGN KEY (payer_account_id) REFERENCES public.entity (id);

-- Crypto allowance entity references
ALTER TABLE public.crypto_allowance
    ADD CONSTRAINT fk_crypto_allowance_owner
        FOREIGN KEY (owner) REFERENCES public.entity (id);

ALTER TABLE public.crypto_allowance
    ADD CONSTRAINT fk_crypto_allowance_spender
        FOREIGN KEY (spender) REFERENCES public.entity (id);

ALTER TABLE public.crypto_allowance
    ADD CONSTRAINT fk_crypto_allowance_payer_account_id
        FOREIGN KEY (payer_account_id) REFERENCES public.entity (id);

-- Crypto allowance history entity references
ALTER TABLE public.crypto_allowance_history
    ADD CONSTRAINT fk_crypto_allowance_history_owner
        FOREIGN KEY (owner) REFERENCES public.entity (id);

ALTER TABLE public.crypto_allowance_history
    ADD CONSTRAINT fk_crypto_allowance_history_spender
        FOREIGN KEY (spender) REFERENCES public.entity (id);

ALTER TABLE public.crypto_allowance_history
    ADD CONSTRAINT fk_crypto_allowance_history_payer_account_id
        FOREIGN KEY (payer_account_id) REFERENCES public.entity (id);

-- NFT allowance entity references
ALTER TABLE public.nft_allowance
    ADD CONSTRAINT fk_nft_allowance_owner
        FOREIGN KEY (owner) REFERENCES public.entity (id);

ALTER TABLE public.nft_allowance
    ADD CONSTRAINT fk_nft_allowance_spender
        FOREIGN KEY (spender) REFERENCES public.entity (id);

ALTER TABLE public.nft_allowance
    ADD CONSTRAINT fk_nft_allowance_token_id
        FOREIGN KEY (token_id) REFERENCES public.entity (id);

ALTER TABLE public.nft_allowance
    ADD CONSTRAINT fk_nft_allowance_payer_account_id
        FOREIGN KEY (payer_account_id) REFERENCES public.entity (id);

-- NFT allowance history entity references
ALTER TABLE public.nft_allowance_history
    ADD CONSTRAINT fk_nft_allowance_history_owner
        FOREIGN KEY (owner) REFERENCES public.entity (id);

ALTER TABLE public.nft_allowance_history
    ADD CONSTRAINT fk_nft_allowance_history_spender
        FOREIGN KEY (spender) REFERENCES public.entity (id);

ALTER TABLE public.nft_allowance_history
    ADD CONSTRAINT fk_nft_allowance_history_token_id
        FOREIGN KEY (token_id) REFERENCES public.entity (id);

ALTER TABLE public.nft_allowance_history
    ADD CONSTRAINT fk_nft_allowance_history_payer_account_id
        FOREIGN KEY (payer_account_id) REFERENCES public.entity (id);

-- Assessed custom fee entity references
ALTER TABLE public.assessed_custom_fee
    ADD CONSTRAINT fk_assessed_custom_fee_collector_account_id
        FOREIGN KEY (collector_account_id) REFERENCES public.entity (id);

ALTER TABLE public.assessed_custom_fee
    ADD CONSTRAINT fk_assessed_custom_fee_payer_account_id
        FOREIGN KEY (payer_account_id) REFERENCES public.entity (id);

ALTER TABLE public.assessed_custom_fee
    ADD CONSTRAINT fk_assessed_custom_fee_token_id
        FOREIGN KEY (token_id) REFERENCES public.entity (id);

-- Custom fee entity references
ALTER TABLE public.custom_fee
    ADD CONSTRAINT fk_custom_fee_entity_id
        FOREIGN KEY (entity_id) REFERENCES public.entity (id);

-- Custom fee history entity references
ALTER TABLE public.custom_fee_history
    ADD CONSTRAINT fk_custom_fee_history_entity_id
        FOREIGN KEY (entity_id) REFERENCES public.entity (id);

-- Entity transaction entity references
ALTER TABLE public.entity_transaction
    ADD CONSTRAINT fk_entity_transaction_entity_id
        FOREIGN KEY (entity_id) REFERENCES public.entity (id);

ALTER TABLE public.entity_transaction
    ADD CONSTRAINT fk_entity_transaction_payer_account_id
        FOREIGN KEY (payer_account_id) REFERENCES public.entity (id);

-- Topic message entity references
ALTER TABLE public.topic_message
    ADD CONSTRAINT fk_topic_message_topic_id
        FOREIGN KEY (topic_id) REFERENCES public.entity (id);

ALTER TABLE public.topic_message
    ADD CONSTRAINT fk_topic_message_payer_account_id
        FOREIGN KEY (payer_account_id) REFERENCES public.entity (id);

-- Topic message lookup entity references
ALTER TABLE public.topic_message_lookup
    ADD CONSTRAINT fk_topic_message_lookup_topic_id
        FOREIGN KEY (topic_id) REFERENCES public.entity (id);

-- Schedule entity references
ALTER TABLE public.schedule
    ADD CONSTRAINT fk_schedule_schedule_id
        FOREIGN KEY (schedule_id) REFERENCES public.entity (id);

ALTER TABLE public.schedule
    ADD CONSTRAINT fk_schedule_creator_account_id
        FOREIGN KEY (creator_account_id) REFERENCES public.entity (id);

ALTER TABLE public.schedule
    ADD CONSTRAINT fk_schedule_payer_account_id
        FOREIGN KEY (payer_account_id) REFERENCES public.entity (id);

-- File data entity references
ALTER TABLE public.file_data
    ADD CONSTRAINT fk_file_data_entity_id
        FOREIGN KEY (entity_id) REFERENCES public.entity (id);

-- Token balance entity references
ALTER TABLE public.token_balance
    ADD CONSTRAINT fk_token_balance_account_id
        FOREIGN KEY (account_id) REFERENCES public.entity (id);

ALTER TABLE public.token_balance
    ADD CONSTRAINT fk_token_balance_token_id
        FOREIGN KEY (token_id) REFERENCES public.entity (id);

-- Account balance entity references
ALTER TABLE public.account_balance
    ADD CONSTRAINT fk_account_balance_account_id
        FOREIGN KEY (account_id) REFERENCES public.entity (id);

-- Entity self-referential relationships
ALTER TABLE public.entity
    ADD CONSTRAINT fk_entity_auto_renew_account_id
        FOREIGN KEY (auto_renew_account_id) REFERENCES public.entity (id);

ALTER TABLE public.entity
    ADD CONSTRAINT fk_entity_obtainer_id
        FOREIGN KEY (obtainer_id) REFERENCES public.entity (id);

ALTER TABLE public.entity
    ADD CONSTRAINT fk_entity_proxy_account_id
        FOREIGN KEY (proxy_account_id) REFERENCES public.entity (id);

ALTER TABLE public.entity
    ADD CONSTRAINT fk_entity_staked_account_id
        FOREIGN KEY (staked_account_id) REFERENCES public.entity (id);

-- Entity history self-referential relationships
ALTER TABLE public.entity_history
    ADD CONSTRAINT fk_entity_history_auto_renew_account_id
        FOREIGN KEY (auto_renew_account_id) REFERENCES public.entity (id);

ALTER TABLE public.entity_history
    ADD CONSTRAINT fk_entity_history_obtainer_id
        FOREIGN KEY (obtainer_id) REFERENCES public.entity (id);

ALTER TABLE public.entity_history
    ADD CONSTRAINT fk_entity_history_proxy_account_id
        FOREIGN KEY (proxy_account_id) REFERENCES public.entity (id);

ALTER TABLE public.entity_history
    ADD CONSTRAINT fk_entity_history_staked_account_id
        FOREIGN KEY (staked_account_id) REFERENCES public.entity (id);

-- Address book entity references
ALTER TABLE public.address_book_entry
    ADD CONSTRAINT fk_address_book_entry_node_account_id
        FOREIGN KEY (node_account_id) REFERENCES public.entity (id);

-- address_book_service_endpoint.node_id is excluded from FK constraints as per review

-- Ethereum transaction entity references
ALTER TABLE public.ethereum_transaction
    ADD CONSTRAINT fk_ethereum_transaction_payer_account_id
        FOREIGN KEY (payer_account_id) REFERENCES public.entity (id);

-- Staking reward transfer entity references
ALTER TABLE public.staking_reward_transfer
    ADD CONSTRAINT fk_staking_reward_transfer_account_id
        FOREIGN KEY (account_id) REFERENCES public.entity (id);

ALTER TABLE public.staking_reward_transfer
    ADD CONSTRAINT fk_staking_reward_transfer_payer_account_id
        FOREIGN KEY (payer_account_id) REFERENCES public.entity (id);

-- Contract action entity references
ALTER TABLE public.contract_action
    ADD CONSTRAINT fk_contract_action_caller
        FOREIGN KEY (caller) REFERENCES public.entity (id);

ALTER TABLE public.contract_action
    ADD CONSTRAINT fk_contract_action_recipient_account
        FOREIGN KEY (recipient_account) REFERENCES public.entity (id);

ALTER TABLE public.contract_action
    ADD CONSTRAINT fk_contract_action_recipient_contract
        FOREIGN KEY (recipient_contract) REFERENCES public.entity (id);

ALTER TABLE public.contract_action
    ADD CONSTRAINT fk_contract_action_payer_account_id
        FOREIGN KEY (payer_account_id) REFERENCES public.entity (id);

-- Entity stake entity references
ALTER TABLE public.entity_stake
    ADD CONSTRAINT fk_entity_stake_id
        FOREIGN KEY (id) REFERENCES public.entity (id);

-- Entity stake history entity references
ALTER TABLE public.entity_stake_history
    ADD CONSTRAINT fk_entity_stake_history_id
        FOREIGN KEY (id) REFERENCES public.entity (id);

-- Contract transaction hash entity references
ALTER TABLE public.contract_transaction_hash
    ADD CONSTRAINT fk_contract_transaction_hash_entity_id
        FOREIGN KEY (entity_id) REFERENCES public.entity (id);

ALTER TABLE public.contract_transaction_hash
    ADD CONSTRAINT fk_contract_transaction_hash_payer_account_id
        FOREIGN KEY (payer_account_id) REFERENCES public.entity (id);

-- Contract transaction entity references
ALTER TABLE public.contract_transaction
    ADD CONSTRAINT fk_contract_transaction_entity_id
        FOREIGN KEY (entity_id) REFERENCES public.entity (id);

ALTER TABLE public.contract_transaction
    ADD CONSTRAINT fk_contract_transaction_payer_account_id
        FOREIGN KEY (payer_account_id) REFERENCES public.entity (id);

-- Transaction hash entity references
ALTER TABLE public.transaction_hash
    ADD CONSTRAINT fk_transaction_hash_payer_account_id
        FOREIGN KEY (payer_account_id) REFERENCES public.entity (id);

-- Transaction signature entity references
ALTER TABLE public.transaction_signature
    ADD CONSTRAINT fk_transaction_signature_entity_id
        FOREIGN KEY (entity_id) REFERENCES public.entity (id);

-- Token airdrop entity references
ALTER TABLE public.token_airdrop
    ADD CONSTRAINT fk_token_airdrop_receiver_account_id
        FOREIGN KEY (receiver_account_id) REFERENCES public.entity (id);

ALTER TABLE public.token_airdrop
    ADD CONSTRAINT fk_token_airdrop_sender_account_id
        FOREIGN KEY (sender_account_id) REFERENCES public.entity (id);

ALTER TABLE public.token_airdrop
    ADD CONSTRAINT fk_token_airdrop_token_id
        FOREIGN KEY (token_id) REFERENCES public.entity (id);

-- Token airdrop history entity references
ALTER TABLE public.token_airdrop_history
    ADD CONSTRAINT fk_token_airdrop_history_receiver_account_id
        FOREIGN KEY (receiver_account_id) REFERENCES public.entity (id);

ALTER TABLE public.token_airdrop_history
    ADD CONSTRAINT fk_token_airdrop_history_sender_account_id
        FOREIGN KEY (sender_account_id) REFERENCES public.entity (id);

ALTER TABLE public.token_airdrop_history
    ADD CONSTRAINT fk_token_airdrop_history_token_id
        FOREIGN KEY (token_id) REFERENCES public.entity (id);

-- Token history entity references
ALTER TABLE public.token_history
    ADD CONSTRAINT fk_token_history_treasury_account_id
        FOREIGN KEY (treasury_account_id) REFERENCES public.entity (id);

ALTER TABLE public.token_history
    ADD CONSTRAINT fk_token_history_token_id
        FOREIGN KEY (token_id) REFERENCES public.entity (id);

-- Node entity references (node_id is excluded from FK constraints as per review)

-- Topic entity references (topic is also an entity)
ALTER TABLE public.topic
    ADD CONSTRAINT fk_topic_id
        FOREIGN KEY (id) REFERENCES public.entity (id);

-- Topic history entity references
ALTER TABLE public.topic_history
    ADD CONSTRAINT fk_topic_history_id
        FOREIGN KEY (id) REFERENCES public.entity (id);

-- Network freeze entity references
ALTER TABLE public.network_freeze
    ADD CONSTRAINT fk_network_freeze_file_id
        FOREIGN KEY (file_id) REFERENCES public.entity (id);

ALTER TABLE public.network_freeze
    ADD CONSTRAINT fk_network_freeze_payer_account_id
        FOREIGN KEY (payer_account_id) REFERENCES public.entity (id);

-- PRNG entity references
ALTER TABLE public.prng
    ADD CONSTRAINT fk_prng_payer_account_id
        FOREIGN KEY (payer_account_id) REFERENCES public.entity (id);

-- ====================================================================================
-- 3. TIMESTAMP_RANGE CONSTRAINTS
-- Note: PostgreSQL doesn't directly support foreign keys on timestamp ranges
-- These would require custom check constraints or triggers to validate that
-- the lower and upper bounds of timestamp ranges exist in the transaction table
-- ====================================================================================
-- 
-- Tables with timestamp_range that should contain valid transaction timestamps:
-- - crypto_allowance.timestamp_range
-- - crypto_allowance_history.timestamp_range  
-- - custom_fee.timestamp_range
-- - custom_fee_history.timestamp_range
-- - entity.timestamp_range
-- - entity_history.timestamp_range
-- - entity_stake.timestamp_range
-- - entity_stake_history.timestamp_range
-- - nft.timestamp_range
-- - nft_allowance.timestamp_range
-- - nft_allowance_history.timestamp_range
-- - nft_history.timestamp_range
-- - node.timestamp_range
-- - node_history.timestamp_range
-- - token_account.timestamp_range
-- - token_account_history.timestamp_range
-- - token_airdrop.timestamp_range
-- - token_airdrop_history.timestamp_range
-- - token_allowance.timestamp_range
-- - token_allowance_history.timestamp_range
-- - token_history.timestamp_range
-- - topic.timestamp_range
-- - topic_history.timestamp_range

-- ====================================================================================
-- 4. BIGINT[] ARRAY FOREIGN KEYS
-- Note: PostgreSQL doesn't directly support foreign keys on array elements
-- These would require custom triggers or check constraints to validate array elements
-- ====================================================================================
--
-- Arrays that should contain valid entity.id values:
-- - contract_result.created_contract_ids[]
-- - assessed_custom_fee.effective_payer_account_ids[]
-- - contract_transaction.contract_ids[]
--
-- Arrays that should contain valid transaction.consensus_timestamp values:
-- - transaction.inner_transactions[]