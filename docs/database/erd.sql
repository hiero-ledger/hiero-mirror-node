-- ====================================================================================
-- STEP 1: TRUNCATE ALL DATA
-- Clean up test data to ensure foreign key constraints work properly
-- ====================================================================================
DO
$$
    DECLARE
        tbl text;
    BEGIN
        -- Loop through all tables in the public schema
        FOR tbl IN
            SELECT tablename
            FROM pg_tables
            WHERE schemaname = 'public'
            LOOP
                EXECUTE format('TRUNCATE TABLE %I CASCADE;', tbl);
            END LOOP;
    END;
$$;
-- ====================================================================================
-- STEP 2: DROP ALL PARTITIONS
-- WARNING: This removes all partition data. Backup before running!
-- ====================================================================================
DO
$$
    DECLARE
        r                  RECORD;
        drop_count         INTEGER := 0;
        converted_count    INTEGER := 0;
        partitioned_tables TEXT[];
    BEGIN
        RAISE NOTICE 'Starting partition removal...';
        -- Get partitioned parent tables
        SELECT ARRAY(SELECT DISTINCT parent FROM mirror_node_time_partitions ORDER BY parent) INTO partitioned_tables;
        -- Drop ALL partition tables in one combined loop
        FOR r IN (
            -- Time-based partitions from view
            SELECT name AS table_name, 'time' AS type
            FROM mirror_node_time_partitions
            UNION ALL
            -- Transaction hash sharded partitions
            SELECT tablename, 'hash'
            FROM pg_tables
            WHERE schemaname = 'public'
              AND (tablename ~ '^transaction_hash_sharded_[0-9]{2}$' OR tablename ~ '^transaction_hash_[0-9]{2}$')
            UNION ALL
            -- Orphaned time partitions not in view
            SELECT tablename, 'orphan'
            FROM pg_tables
            WHERE schemaname = 'public'
              AND tablename ~ '_p[0-9]{4}_[0-9]{2}$'
              AND NOT EXISTS (SELECT 1 FROM mirror_node_time_partitions WHERE name = tablename))
            LOOP
                BEGIN
                    EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', r.table_name);
                    drop_count := drop_count + 1;
                    RAISE NOTICE 'Dropped % partition: %', r.type, r.table_name;
                EXCEPTION
                    WHEN OTHERS THEN
                        RAISE WARNING 'Failed to drop %: %', r.table_name, SQLERRM;
                END;
            END LOOP;
        -- Convert ALL partitioned tables to regular tables
        FOR r IN (SELECT c.relname AS table_name
                  FROM pg_partitioned_table pt
                           JOIN pg_class c ON pt.partrelid = c.oid
                           JOIN pg_namespace n ON c.relnamespace = n.oid
                  WHERE n.nspname = 'public')
            LOOP
                BEGIN
                    EXECUTE format('CREATE TABLE %I_temp (LIKE %I INCLUDING ALL)', r.table_name, r.table_name);
                    EXECUTE format('DROP TABLE %I CASCADE', r.table_name);
                    EXECUTE format('alter table %I_temp RENAME TO %I', r.table_name, r.table_name);
                    converted_count := converted_count + 1;
                    RAISE NOTICE 'Converted to regular table: %', r.table_name;
                EXCEPTION
                    WHEN OTHERS THEN
                        RAISE WARNING 'Failed to convert %: %', r.table_name, SQLERRM;
                        EXECUTE format('DROP TABLE IF EXISTS %I_temp', r.table_name);
                END;
            END LOOP;
        -- Summary and verification
        RAISE NOTICE 'Complete! Dropped: % partitions, Converted: % tables', drop_count, converted_count;
        PERFORM 1 FROM mirror_node_time_partitions LIMIT 1;
        IF NOT FOUND THEN
            RAISE NOTICE 'SUCCESS: All partitions removed, tables converted to regular tables';
        ELSE
            RAISE WARNING 'WARNING: Some partitions may still exist';
        END IF;
    END
$$;
-- ====================================================================================
-- STEP 3: RECREATE COLUMN TIMESTAMP_RANGE AS BIGINT FOR ERD VISUALIZATION
-- These changes are only for ERD visualization and should not be used in production
-- ====================================================================================
alter table crypto_allowance drop column timestamp_range, add column timestamp_range bigint;
alter table crypto_allowance_history drop column timestamp_range, add column timestamp_range bigint;
alter table custom_fee drop column timestamp_range, add column timestamp_range bigint;
alter table custom_fee_history drop column timestamp_range, add column timestamp_range bigint;
alter table entity drop column timestamp_range, add column timestamp_range bigint;
alter table entity_history drop column timestamp_range, add column timestamp_range bigint;
alter table entity_stake drop column timestamp_range, add column timestamp_range bigint;
alter table entity_stake_history drop column timestamp_range, add column timestamp_range bigint;
alter table nft drop column timestamp_range, add column timestamp_range bigint;
alter table nft_allowance drop column timestamp_range, add column timestamp_range bigint;
alter table nft_allowance_history drop column timestamp_range, add column timestamp_range bigint;
alter table nft_history drop column timestamp_range, add column timestamp_range bigint;
alter table node drop column timestamp_range, add column timestamp_range bigint;
alter table node_history drop column timestamp_range, add column timestamp_range bigint;
alter table token drop column timestamp_range, add column timestamp_range bigint;
alter table token_account drop column timestamp_range, add column timestamp_range bigint;
alter table token_account_history drop column timestamp_range, add column timestamp_range bigint;
alter table token_airdrop drop column timestamp_range, add column timestamp_range bigint;
alter table token_airdrop_history drop column timestamp_range, add column timestamp_range bigint;
alter table token_allowance drop column timestamp_range, add column timestamp_range bigint;
alter table token_allowance_history drop column timestamp_range, add column timestamp_range bigint;
alter table token_history drop column timestamp_range, add column timestamp_range bigint;
alter table topic drop column timestamp_range, add column timestamp_range bigint;
alter table topic_history drop column timestamp_range, add column timestamp_range bigint;
alter table topic_message_lookup drop column timestamp_range, add column timestamp_range bigint;
-- ====================================================================================
-- STEP 4: ADD FOREIGN KEY CONSTRAINTS
-- Add all foreign key constraints for proper relationship visualization
-- Repeatable migration to add foreign key constraints for testing
-- This migration runs only during tests to validate referential integrity
-- but is not applied in production to avoid performance impact
-- ====================================================================================
alter table account_balance add constraint fk_account_balance_account_id foreign key (account_id) references entity (id);
alter table account_balance add constraint fk_account_balance_consensus_timestamp foreign key (consensus_timestamp) references transaction (consensus_timestamp);
alter table account_balance_file add constraint fk_account_balance_file_consensus_timestamp foreign key (consensus_timestamp) references transaction (consensus_timestamp);
alter table account_balance_file add constraint fk_account_balance_file_node_id foreign key (node_id) references node (node_id);
alter table address_book add constraint fk_address_book_end_consensus_timestamp foreign key (end_consensus_timestamp) references transaction (consensus_timestamp);
alter table address_book add constraint fk_address_book_file_id foreign key (file_id) references entity (id);
alter table address_book add constraint fk_address_book_start_consensus_timestamp foreign key (start_consensus_timestamp) references transaction (consensus_timestamp);
alter table address_book_entry add constraint fk_address_book_entry_consensus_timestamp foreign key (consensus_timestamp) references transaction (consensus_timestamp);
alter table address_book_entry add constraint fk_address_book_entry_node_account_id foreign key (node_account_id) references entity (id);
alter table address_book_entry add constraint fk_address_book_entry_node_id foreign key (node_id) references node (node_id);
alter table address_book_service_endpoint add constraint fk_address_book_service_endpoint_consensus_timestamp foreign key (consensus_timestamp) references transaction (consensus_timestamp);
alter table address_book_service_endpoint add constraint fk_address_book_service_endpoint_node_id foreign key (node_id) references node (node_id);
alter table assessed_custom_fee add constraint fk_assessed_custom_fee_collector_account_id foreign key (collector_account_id) references entity (id);
alter table assessed_custom_fee add constraint fk_assessed_custom_fee_consensus_timestamp foreign key (consensus_timestamp) references transaction (consensus_timestamp);
alter table assessed_custom_fee add constraint fk_assessed_custom_fee_payer_account_id foreign key (payer_account_id) references entity (id);
alter table assessed_custom_fee add constraint fk_assessed_custom_fee_token_id foreign key (token_id) references entity (id);
alter table contract add constraint fk_contract_file_id foreign key (file_id) references entity (id);
alter table contract add constraint fk_contract_id foreign key (id) references entity (id);
alter table contract_action add constraint fk_contract_action_caller foreign key (caller) references entity (id);
alter table contract_action add constraint fk_contract_action_consensus_timestamp foreign key (consensus_timestamp) references transaction (consensus_timestamp);
alter table contract_action add constraint fk_contract_action_payer_account_id foreign key (payer_account_id) references entity (id);
alter table contract_action add constraint fk_contract_action_recipient_account foreign key (recipient_account) references entity (id);
alter table contract_action add constraint fk_contract_action_recipient_contract foreign key (recipient_contract) references entity (id);
alter table contract_log add constraint fk_contract_log_consensus_timestamp foreign key (consensus_timestamp) references transaction (consensus_timestamp);
alter table contract_log add constraint fk_contract_log_contract_id foreign key (contract_id) references entity (id);
alter table contract_log add constraint fk_contract_log_payer_account_id foreign key (payer_account_id) references entity (id);
alter table contract_log add constraint fk_contract_log_root_contract_id foreign key (root_contract_id) references entity (id);
alter table contract_result add constraint fk_contract_result_consensus_timestamp foreign key (consensus_timestamp) references transaction (consensus_timestamp);
alter table contract_result add constraint fk_contract_result_contract_id foreign key (contract_id) references entity (id);
alter table contract_result add constraint fk_contract_result_payer_account_id foreign key (payer_account_id) references entity (id);
alter table contract_result add constraint fk_contract_result_sender_id foreign key (sender_id) references entity (id);
alter table contract_state add constraint fk_contract_state_contract_id foreign key (contract_id) references entity (id);
alter table contract_state add constraint fk_contract_state_created_timestamp foreign key (created_timestamp) references transaction (consensus_timestamp);
alter table contract_state add constraint fk_contract_state_modified_timestamp foreign key (modified_timestamp) references transaction (consensus_timestamp);
alter table contract_state_change add constraint fk_contract_state_change_consensus_timestamp foreign key (consensus_timestamp) references transaction (consensus_timestamp);
alter table contract_state_change add constraint fk_contract_state_change_contract_id foreign key (contract_id) references entity (id);
alter table contract_state_change add constraint fk_contract_state_change_payer_account_id foreign key (payer_account_id) references entity (id);
alter table contract_transaction add constraint fk_contract_transaction_consensus_timestamp foreign key (consensus_timestamp) references transaction (consensus_timestamp);
alter table contract_transaction add constraint fk_contract_transaction_entity_id foreign key (entity_id) references entity (id);
alter table contract_transaction add constraint fk_contract_transaction_payer_account_id foreign key (payer_account_id) references entity (id);
alter table contract_transaction_hash add constraint fk_contract_transaction_hash_consensus_timestamp foreign key (consensus_timestamp) references transaction (consensus_timestamp);
alter table contract_transaction_hash add constraint fk_contract_transaction_hash_entity_id foreign key (entity_id) references entity (id);
alter table contract_transaction_hash add constraint fk_contract_transaction_hash_payer_account_id foreign key (payer_account_id) references entity (id);
alter table crypto_allowance add constraint fk_crypto_allowance_owner foreign key (owner) references entity (id);
alter table crypto_allowance add constraint fk_crypto_allowance_payer_account_id foreign key (payer_account_id) references entity (id);
alter table crypto_allowance add constraint fk_crypto_allowance_spender foreign key (spender) references entity (id);
alter table crypto_allowance add constraint fk_crypto_allowance_timestamp_range foreign key (timestamp_range) references transaction (consensus_timestamp);
alter table crypto_allowance_history add constraint fk_crypto_allowance_history_owner foreign key (owner) references entity (id);
alter table crypto_allowance_history add constraint fk_crypto_allowance_history_payer_account_id foreign key (payer_account_id) references entity (id);
alter table crypto_allowance_history add constraint fk_crypto_allowance_history_spender foreign key (spender) references entity (id);
alter table crypto_allowance_history add constraint fk_crypto_allowance_history_timestamp_range foreign key (timestamp_range) references transaction (consensus_timestamp);
alter table crypto_transfer add constraint fk_crypto_transfer_consensus_timestamp foreign key (consensus_timestamp) references transaction (consensus_timestamp);
alter table crypto_transfer add constraint fk_crypto_transfer_entity_id foreign key (entity_id) references entity (id);
alter table crypto_transfer add constraint fk_crypto_transfer_payer_account_id foreign key (payer_account_id) references entity (id);
alter table custom_fee add constraint fk_custom_fee_entity_id foreign key (entity_id) references entity (id);
alter table custom_fee add constraint fk_custom_fee_timestamp_range foreign key (timestamp_range) references transaction (consensus_timestamp);
alter table custom_fee_history add constraint fk_custom_fee_history_entity_id foreign key (entity_id) references entity (id);
alter table custom_fee_history add constraint fk_custom_fee_history_timestamp_range foreign key (timestamp_range) references transaction (consensus_timestamp);
alter table entity add constraint fk_entity_auto_renew_account_id foreign key (auto_renew_account_id) references entity (id);
alter table entity add constraint fk_entity_balance_timestamp foreign key (balance_timestamp) references transaction (consensus_timestamp);
alter table entity add constraint fk_entity_created_timestamp foreign key (created_timestamp) references transaction (consensus_timestamp);
alter table entity add constraint fk_entity_obtainer_id foreign key (obtainer_id) references entity (id);
alter table entity add constraint fk_entity_proxy_account_id foreign key (proxy_account_id) references entity (id);
alter table entity add constraint fk_entity_staked_account_id foreign key (staked_account_id) references entity (id);
alter table entity add constraint fk_entity_staked_node_id foreign key (staked_node_id) references node (node_id);
alter table entity add constraint fk_entity_timestamp_range foreign key (timestamp_range) references transaction (consensus_timestamp);
alter table entity_history add constraint fk_entity_history_auto_renew_account_id foreign key (auto_renew_account_id) references entity (id);
alter table entity_history add constraint fk_entity_history_balance_timestamp foreign key (balance_timestamp) references transaction (consensus_timestamp);
alter table entity_history add constraint fk_entity_history_created_timestamp foreign key (created_timestamp) references transaction (consensus_timestamp);
alter table entity_history add constraint fk_entity_history_obtainer_id foreign key (obtainer_id) references entity (id);
alter table entity_history add constraint fk_entity_history_proxy_account_id foreign key (proxy_account_id) references entity (id);
alter table entity_history add constraint fk_entity_history_staked_account_id foreign key (staked_account_id) references entity (id);
alter table entity_history add constraint fk_entity_history_staked_node_id foreign key (staked_node_id) references node (node_id);
alter table entity_history add constraint fk_entity_history_timestamp_range foreign key (timestamp_range) references transaction (consensus_timestamp);
alter table entity_stake add constraint fk_entity_stake_id foreign key (id) references entity (id);
alter table entity_stake add constraint fk_entity_stake_timestamp_range foreign key (timestamp_range) references transaction (consensus_timestamp);
alter table entity_stake add constraint fk_entity_stake_staked_node_id_start foreign key (staked_node_id_start) references node (node_id);
alter table entity_stake_history add constraint fk_entity_stake_history_id foreign key (id) references entity (id);
alter table entity_stake_history add constraint fk_entity_stake_history_timestamp_range foreign key (timestamp_range) references transaction (consensus_timestamp);
alter table entity_stake_history add constraint fk_entity_stake_history_staked_node_id_start foreign key (staked_node_id_start) references node (node_id);
alter table entity_transaction add constraint fk_entity_transaction_consensus_timestamp foreign key (consensus_timestamp) references transaction (consensus_timestamp);
alter table entity_transaction add constraint fk_entity_transaction_entity_id foreign key (entity_id) references entity (id);
alter table entity_transaction add constraint fk_entity_transaction_payer_account_id foreign key (payer_account_id) references entity (id);
alter table ethereum_transaction add constraint fk_ethereum_transaction_call_data_id foreign key (call_data_id) references entity (id);
alter table ethereum_transaction add constraint fk_ethereum_transaction_consensus_timestamp foreign key (consensus_timestamp) references transaction (consensus_timestamp);
alter table ethereum_transaction add constraint fk_ethereum_transaction_payer_account_id foreign key (payer_account_id) references entity (id);
alter table file_data add constraint fk_file_data_consensus_timestamp foreign key (consensus_timestamp) references transaction (consensus_timestamp);
alter table file_data add constraint fk_file_data_entity_id foreign key (entity_id) references entity (id);
alter table live_hash add constraint fk_live_hash_consensus_timestamp foreign key (consensus_timestamp) references transaction (consensus_timestamp);
alter table network_freeze add constraint fk_network_freeze_consensus_timestamp foreign key (consensus_timestamp) references transaction (consensus_timestamp);
alter table network_freeze add constraint fk_network_freeze_file_id foreign key (file_id) references entity (id);
alter table network_freeze add constraint fk_network_freeze_payer_account_id foreign key (payer_account_id) references entity (id);
alter table network_stake add constraint fk_network_stake_consensus_timestamp foreign key (consensus_timestamp) references transaction (consensus_timestamp);
alter table nft add constraint fk_nft_account_id foreign key (account_id) references entity (id);
alter table nft add constraint fk_nft_created_timestamp foreign key (created_timestamp) references transaction (consensus_timestamp);
alter table nft add constraint fk_nft_delegating_spender foreign key (delegating_spender) references entity (id);
alter table nft add constraint fk_nft_spender foreign key (spender) references entity (id);
alter table nft add constraint fk_nft_timestamp_range foreign key (timestamp_range) references transaction (consensus_timestamp);
alter table nft add constraint fk_nft_token_id foreign key (token_id) references entity (id);
alter table nft_allowance add constraint fk_nft_allowance_owner foreign key (owner) references entity (id);
alter table nft_allowance add constraint fk_nft_allowance_payer_account_id foreign key (payer_account_id) references entity (id);
alter table nft_allowance add constraint fk_nft_allowance_spender foreign key (spender) references entity (id);
alter table nft_allowance add constraint fk_nft_allowance_timestamp_range foreign key (timestamp_range) references transaction (consensus_timestamp);
alter table nft_allowance add constraint fk_nft_allowance_token_id foreign key (token_id) references entity (id);
alter table nft_allowance_history add constraint fk_nft_allowance_history_owner foreign key (owner) references entity (id);
alter table nft_allowance_history add constraint fk_nft_allowance_history_payer_account_id foreign key (payer_account_id) references entity (id);
alter table nft_allowance_history add constraint fk_nft_allowance_history_spender foreign key (spender) references entity (id);
alter table nft_allowance_history add constraint fk_nft_allowance_history_timestamp_range foreign key (timestamp_range) references transaction (consensus_timestamp);
alter table nft_allowance_history add constraint fk_nft_allowance_history_token_id foreign key (token_id) references entity (id);
alter table nft_history add constraint fk_nft_history_account_id foreign key (account_id) references entity (id);
alter table nft_history add constraint fk_nft_history_created_timestamp foreign key (created_timestamp) references transaction (consensus_timestamp);
alter table nft_history add constraint fk_nft_history_delegating_spender foreign key (delegating_spender) references entity (id);
alter table nft_history add constraint fk_nft_history_spender foreign key (spender) references entity (id);
alter table nft_history add constraint fk_nft_history_timestamp_range foreign key (timestamp_range) references transaction (consensus_timestamp);
alter table nft_history add constraint fk_nft_history_token_id foreign key (token_id) references entity (id);
alter table node add constraint fk_node_created_timestamp foreign key (created_timestamp) references transaction (consensus_timestamp);
alter table node add constraint fk_node_timestamp_range foreign key (timestamp_range) references transaction (consensus_timestamp);
alter table node_history add constraint fk_node_history_created_timestamp foreign key (created_timestamp) references transaction (consensus_timestamp);
alter table node_history add constraint fk_node_history_timestamp_range foreign key (timestamp_range) references transaction (consensus_timestamp);
alter table node_stake add constraint fk_node_stake_consensus_timestamp foreign key (consensus_timestamp) references transaction (consensus_timestamp);
alter table node_stake add constraint fk_node_stake_node_id foreign key (node_id) references node (node_id);
alter table non_fee_transfer add constraint fk_non_fee_transfer_consensus_timestamp foreign key (consensus_timestamp) references transaction (consensus_timestamp);
alter table non_fee_transfer add constraint fk_non_fee_transfer_entity_id foreign key (entity_id) references entity (id);
alter table non_fee_transfer add constraint fk_non_fee_transfer_payer_account_id foreign key (payer_account_id) references entity (id);
alter table prng add constraint fk_prng_consensus_timestamp foreign key (consensus_timestamp) references transaction (consensus_timestamp);
alter table prng add constraint fk_prng_payer_account_id foreign key (payer_account_id) references entity (id);
alter table reconciliation_job add constraint fk_reconciliation_job_consensus_timestamp foreign key (consensus_timestamp) references transaction (consensus_timestamp);
alter table schedule add constraint fk_schedule_consensus_timestamp foreign key (consensus_timestamp) references transaction (consensus_timestamp);
alter table schedule add constraint fk_schedule_creator_account_id foreign key (creator_account_id) references entity (id);
alter table schedule add constraint fk_schedule_executed_timestamp foreign key (executed_timestamp) references transaction (consensus_timestamp);
alter table schedule add constraint fk_schedule_payer_account_id foreign key (payer_account_id) references entity (id);
alter table schedule add constraint fk_schedule_schedule_id foreign key (schedule_id) references entity (id);
alter table sidecar_file add constraint fk_sidecar_file_consensus_end foreign key (consensus_end) references record_file (consensus_end);
alter table staking_reward_transfer add constraint fk_staking_reward_transfer_account_id foreign key (account_id) references entity (id);
alter table staking_reward_transfer add constraint fk_staking_reward_transfer_consensus_timestamp foreign key (consensus_timestamp) references transaction (consensus_timestamp);
alter table staking_reward_transfer add constraint fk_staking_reward_transfer_payer_account_id foreign key (payer_account_id) references entity (id);
alter table token add constraint fk_token_created_timestamp foreign key (created_timestamp) references transaction (consensus_timestamp);
alter table token add constraint fk_token_timestamp_range foreign key (timestamp_range) references transaction (consensus_timestamp);
alter table token add constraint fk_token_token_id foreign key (token_id) references entity (id);
alter table token add constraint fk_token_treasury_account_id foreign key (treasury_account_id) references entity (id);
alter table token_account add constraint fk_token_account_account_id foreign key (account_id) references entity (id);
alter table token_account add constraint fk_token_account_balance_timestamp foreign key (balance_timestamp) references transaction (consensus_timestamp);
alter table token_account add constraint fk_token_account_created_timestamp foreign key (created_timestamp) references transaction (consensus_timestamp);
alter table token_account add constraint fk_token_account_timestamp_range foreign key (timestamp_range) references transaction (consensus_timestamp);
alter table token_account add constraint fk_token_account_token_id foreign key (token_id) references entity (id);
alter table token_account_history add constraint fk_token_account_history_account_id foreign key (account_id) references entity (id);
alter table token_account_history add constraint fk_token_account_history_balance_timestamp foreign key (balance_timestamp) references transaction (consensus_timestamp);
alter table token_account_history add constraint fk_token_account_history_created_timestamp foreign key (created_timestamp) references transaction (consensus_timestamp);
alter table token_account_history add constraint fk_token_account_history_timestamp_range foreign key (timestamp_range) references transaction (consensus_timestamp);
alter table token_account_history add constraint fk_token_account_history_token_id foreign key (token_id) references entity (id);
alter table token_airdrop add constraint fk_token_airdrop_receiver_account_id foreign key (receiver_account_id) references entity (id);
alter table token_airdrop add constraint fk_token_airdrop_sender_account_id foreign key (sender_account_id) references entity (id);
alter table token_airdrop add constraint fk_token_airdrop_timestamp_range foreign key (timestamp_range) references transaction (consensus_timestamp);
alter table token_airdrop add constraint fk_token_airdrop_token_id foreign key (token_id) references entity (id);
alter table token_airdrop_history add constraint fk_token_airdrop_history_receiver_account_id foreign key (receiver_account_id) references entity (id);
alter table token_airdrop_history add constraint fk_token_airdrop_history_sender_account_id foreign key (sender_account_id) references entity (id);
alter table token_airdrop_history add constraint fk_token_airdrop_history_timestamp_range foreign key (timestamp_range) references transaction (consensus_timestamp);
alter table token_airdrop_history add constraint fk_token_airdrop_history_token_id foreign key (token_id) references entity (id);
alter table token_allowance add constraint fk_token_allowance_owner foreign key (owner) references entity (id);
alter table token_allowance add constraint fk_token_allowance_payer_account_id foreign key (payer_account_id) references entity (id);
alter table token_allowance add constraint fk_token_allowance_spender foreign key (spender) references entity (id);
alter table token_allowance add constraint fk_token_allowance_timestamp_range foreign key (timestamp_range) references transaction (consensus_timestamp);
alter table token_allowance add constraint fk_token_allowance_token_id foreign key (token_id) references entity (id);
alter table token_allowance_history add constraint fk_token_allowance_history_owner foreign key (owner) references entity (id);
alter table token_allowance_history add constraint fk_token_allowance_history_payer_account_id foreign key (payer_account_id) references entity (id);
alter table token_allowance_history add constraint fk_token_allowance_history_spender foreign key (spender) references entity (id);
alter table token_allowance_history add constraint fk_token_allowance_history_timestamp_range foreign key (timestamp_range) references transaction (consensus_timestamp);
alter table token_allowance_history add constraint fk_token_allowance_history_token_id foreign key (token_id) references entity (id);
alter table token_balance add constraint fk_token_balance_account_id foreign key (account_id) references entity (id);
alter table token_balance add constraint fk_token_balance_consensus_timestamp foreign key (consensus_timestamp) references transaction (consensus_timestamp);
alter table token_balance add constraint fk_token_balance_token_id foreign key (token_id) references entity (id);
alter table token_history add constraint fk_token_history_created_timestamp foreign key (created_timestamp) references transaction (consensus_timestamp);
alter table token_history add constraint fk_token_history_timestamp_range foreign key (timestamp_range) references transaction (consensus_timestamp);
alter table token_history add constraint fk_token_history_token_id foreign key (token_id) references entity (id);
alter table token_history add constraint fk_token_history_treasury_account_id foreign key (treasury_account_id) references entity (id);
alter table token_transfer add constraint fk_token_transfer_account_id foreign key (account_id) references entity (id);
alter table token_transfer add constraint fk_token_transfer_consensus_timestamp foreign key (consensus_timestamp) references transaction (consensus_timestamp);
alter table token_transfer add constraint fk_token_transfer_payer_account_id foreign key (payer_account_id) references entity (id);
alter table token_transfer add constraint fk_token_transfer_token_id foreign key (token_id) references entity (id);
alter table topic add constraint fk_topic_created_timestamp foreign key (created_timestamp) references transaction (consensus_timestamp);
alter table topic add constraint fk_topic_id foreign key (id) references entity (id);
alter table topic add constraint fk_topic_timestamp_range foreign key (timestamp_range) references transaction (consensus_timestamp);
alter table topic_history add constraint fk_topic_history_created_timestamp foreign key (created_timestamp) references transaction (consensus_timestamp);
alter table topic_history add constraint fk_topic_history_id foreign key (id) references entity (id);
alter table topic_history add constraint fk_topic_history_timestamp_range foreign key (timestamp_range) references transaction (consensus_timestamp);
alter table topic_message add constraint fk_topic_message_consensus_timestamp foreign key (consensus_timestamp) references transaction (consensus_timestamp);
alter table topic_message add constraint fk_topic_message_payer_account_id foreign key (payer_account_id) references entity (id);
alter table topic_message add constraint fk_topic_message_topic_id foreign key (topic_id) references entity (id);
alter table topic_message_lookup add constraint fk_topic_message_lookup_timestamp_range foreign key (timestamp_range) references transaction (consensus_timestamp);
alter table topic_message_lookup add constraint fk_topic_message_lookup_topic_id foreign key (topic_id) references entity (id);
alter table transaction add constraint fk_transaction_entity_id foreign key (entity_id) references entity (id);
alter table transaction add constraint fk_transaction_node_account_id foreign key (node_account_id) references entity (id);
alter table transaction add constraint fk_transaction_parent_consensus_timestamp foreign key (parent_consensus_timestamp) references transaction (consensus_timestamp);
alter table transaction add constraint fk_transaction_payer_account_id foreign key (payer_account_id) references entity (id);
alter table transaction_hash add constraint fk_transaction_hash_consensus_timestamp foreign key (consensus_timestamp) references transaction (consensus_timestamp);
alter table transaction_hash add constraint fk_transaction_hash_payer_account_id foreign key (payer_account_id) references entity (id);
alter table transaction_signature add constraint fk_transaction_signature_consensus_timestamp foreign key (consensus_timestamp) references transaction (consensus_timestamp);
alter table transaction_signature add constraint fk_transaction_signature_entity_id foreign key (entity_id) references entity (id);
-- ====================================================================================
-- STEP 5: BIGINT[] ARRAY FOREIGN KEYS
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
-- ====================================================================================
-- COMPLETION MESSAGE AND VERIFICATION
-- ====================================================================================
DO
$$
    DECLARE
        fk_count    INTEGER;
        table_count INTEGER;
    BEGIN
        -- Count foreign key constraints
        SELECT COUNT(*)
        INTO fk_count
        FROM information_schema.table_constraints
        WHERE constraint_type = 'FOREIGN KEY'
          AND table_schema = 'public';
        -- Count tables
        SELECT COUNT(*)
        INTO table_count
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_type = 'BASE TABLE';
        RAISE NOTICE 'ERD preparation complete! Database is ready for ERD generation.';
        RAISE NOTICE '1. Data truncated from all tables';
        RAISE NOTICE '2. All partitions dropped and converted to regular tables';
        RAISE NOTICE '3. All foreign key constraints added';
        RAISE NOTICE 'Database contains % tables and % foreign key constraints', table_count, fk_count;
        RAISE NOTICE 'You can now generate the ERD using IntelliJ IDEA or other ERD tools.';
        IF fk_count = 0 THEN
            RAISE WARNING 'No foreign key constraints found! Check for errors in constraint creation.';
        END IF;
    END
$$;