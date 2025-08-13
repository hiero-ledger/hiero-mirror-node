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
-- Remove all partitions to ensure foreign key constraints work with regular tables
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
                    EXECUTE format('ALTER TABLE %I_temp RENAME TO %I', r.table_name, r.table_name);
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

        -- Analyze converted tables
        FOR r IN (SELECT table_name
                  FROM information_schema.tables
                  WHERE table_schema = 'public'
                    AND table_name IN ('account_balance', 'token_balance', 'transaction_hash_sharded'))
            LOOP
                EXECUTE format('ANALYZE %I', r.table_name);
                RAISE NOTICE 'Analyzed: %', r.table_name;
            END LOOP;
    END
$$;

-- ====================================================================================
-- STEP 3: RECREATE TABLES WITH TIMESTAMP_RANGE AS BIGINT FOR ERD VISUALIZATION
-- Drop and recreate tables with timestamp_range as bigint to show relationships
-- These changes are only for ERD visualization and should not be used in production
-- ====================================================================================

DO
$$
    DECLARE
        table_names TEXT[] := ARRAY [
            'crypto_allowance', 'crypto_allowance_history', 'custom_fee', 'custom_fee_history',
            'entity_history', 'entity_stake', 'entity_stake_history', 'nft', 'nft_allowance',
            'nft_allowance_history', 'nft_history', 'node', 'node_history', 'token', 'token_account',
            'token_account_history', 'token_airdrop', 'token_airdrop_history', 'token_allowance',
            'token_allowance_history', 'token_history', 'topic', 'topic_history'
            ];
        tbl_name    TEXT;
        create_stmt TEXT;
        col_info    RECORD;
    BEGIN
        -- Loop through each table that has timestamp_range column
        FOREACH tbl_name IN ARRAY table_names
            LOOP
                -- Check if table exists and has timestamp_range column
                IF EXISTS (SELECT 1
                           FROM information_schema.columns
                           WHERE information_schema.columns.table_name = tbl_name
                             AND column_name = 'timestamp_range'
                             AND table_schema = 'public') THEN
                    RAISE NOTICE 'Recreating table % with timestamp_range as bigint', tbl_name;

                    -- Build CREATE TABLE statement with timestamp_range as bigint
                    create_stmt := 'CREATE TABLE ' || quote_ident(tbl_name || '_new') || ' (';

                    -- Add all columns except timestamp_range
                    FOR col_info IN
                        SELECT a.attname                                       AS column_name,
                               format_type(a.atttypid, a.atttypmod)            AS data_type,
                               CASE WHEN a.attnotnull THEN 'NO' ELSE 'YES' END AS is_nullable,
                               pg_get_expr(d.adbin, d.adrelid)                 AS column_default
                        FROM pg_attribute a
                                 JOIN pg_class c ON a.attrelid = c.oid
                                 JOIN pg_namespace n ON c.relnamespace = n.oid
                                 LEFT JOIN pg_attrdef d ON d.adrelid = c.oid AND d.adnum = a.attnum
                        WHERE n.nspname = 'public'
                          AND c.relname = tbl_name
                          AND a.attname != 'timestamp_range'
                          AND a.attnum > 0
                          AND NOT a.attisdropped
                        ORDER BY a.attnum
                        LOOP
                            create_stmt :=
                                create_stmt || quote_ident(col_info.column_name) || ' ' || col_info.data_type;

                            IF col_info.is_nullable = 'NO' THEN
                                create_stmt := create_stmt || ' NOT NULL';
                            END IF;

                            IF col_info.column_default IS NOT NULL THEN
                                create_stmt := create_stmt || ' DEFAULT ' || col_info.column_default;
                            END IF;

                            create_stmt := create_stmt || ', ';
                        END LOOP;

                    -- Add timestamp_range as bigint
                    create_stmt := create_stmt || 'timestamp_range bigint';
                    create_stmt := create_stmt || ')';

                    RAISE NOTICE 'CREATE TABLE statement: %', create_stmt;

                    -- Execute the CREATE TABLE statement
                    EXECUTE create_stmt;

                    -- Drop the original table and rename the new one
                    EXECUTE 'DROP TABLE ' || quote_ident(tbl_name) || ' CASCADE';
                    EXECUTE 'ALTER TABLE ' || quote_ident(tbl_name || '_new') || ' RENAME TO ' || quote_ident(tbl_name);

                    RAISE NOTICE 'Successfully recreated table %', tbl_name;
                ELSE
                    RAISE NOTICE 'Table % does not exist or does not have timestamp_range column, skipping', tbl_name;
                END IF;
            END LOOP;
    END
$$;

-- update the column type for timestamp_range to bigint for entity table
ALTER TABLE public.entity DROP COLUMN timestamp_range;
ALTER TABLE public.entity ADD COLUMN timestamp_range bigint;

-- ====================================================================================
-- STEP 4: ADD FOREIGN KEY CONSTRAINTS
-- Add all foreign key constraints for proper relationship visualization
-- Repeatable migration to add foreign key constraints for testing
-- This migration runs only during tests to validate referential integrity
-- but is not applied in production to avoid performance impact
-- ====================================================================================

ALTER TABLE account_balance ADD CONSTRAINT fk_account_balance_account_id FOREIGN KEY (account_id) REFERENCES entity (id);
ALTER TABLE account_balance ADD CONSTRAINT fk_account_balance_consensus_timestamp FOREIGN KEY (consensus_timestamp) REFERENCES transaction (consensus_timestamp);
ALTER TABLE account_balance_file ADD CONSTRAINT fk_account_balance_file_consensus_timestamp FOREIGN KEY (consensus_timestamp) REFERENCES transaction (consensus_timestamp);
ALTER TABLE address_book_entry ADD CONSTRAINT fk_address_book_entry_consensus_timestamp FOREIGN KEY (consensus_timestamp) REFERENCES transaction (consensus_timestamp);
ALTER TABLE address_book_entry ADD CONSTRAINT fk_address_book_entry_node_account_id FOREIGN KEY (node_account_id) REFERENCES entity (id);
ALTER TABLE address_book_service_endpoint ADD CONSTRAINT fk_address_book_service_endpoint_consensus_timestamp FOREIGN KEY (consensus_timestamp) REFERENCES transaction (consensus_timestamp);
ALTER TABLE assessed_custom_fee ADD CONSTRAINT fk_assessed_custom_fee_collector_account_id FOREIGN KEY (collector_account_id) REFERENCES entity (id);
ALTER TABLE assessed_custom_fee ADD CONSTRAINT fk_assessed_custom_fee_consensus_timestamp FOREIGN KEY (consensus_timestamp) REFERENCES transaction (consensus_timestamp);
ALTER TABLE assessed_custom_fee ADD CONSTRAINT fk_assessed_custom_fee_payer_account_id FOREIGN KEY (payer_account_id) REFERENCES entity (id);
ALTER TABLE assessed_custom_fee ADD CONSTRAINT fk_assessed_custom_fee_token_id FOREIGN KEY (token_id) REFERENCES entity (id);
ALTER TABLE contract ADD CONSTRAINT fk_contract_file_id FOREIGN KEY (file_id) REFERENCES entity (id);
ALTER TABLE contract ADD CONSTRAINT fk_contract_id FOREIGN KEY (id) REFERENCES entity (id);
ALTER TABLE contract_action ADD CONSTRAINT fk_contract_action_caller FOREIGN KEY (caller) REFERENCES entity (id);
ALTER TABLE contract_action ADD CONSTRAINT fk_contract_action_consensus_timestamp FOREIGN KEY (consensus_timestamp) REFERENCES transaction (consensus_timestamp);
ALTER TABLE contract_action ADD CONSTRAINT fk_contract_action_payer_account_id FOREIGN KEY (payer_account_id) REFERENCES entity (id);
ALTER TABLE contract_action ADD CONSTRAINT fk_contract_action_recipient_account FOREIGN KEY (recipient_account) REFERENCES entity (id);
ALTER TABLE contract_action ADD CONSTRAINT fk_contract_action_recipient_contract FOREIGN KEY (recipient_contract) REFERENCES entity (id);
ALTER TABLE contract_log ADD CONSTRAINT fk_contract_log_consensus_timestamp FOREIGN KEY (consensus_timestamp) REFERENCES transaction (consensus_timestamp);
ALTER TABLE contract_log ADD CONSTRAINT fk_contract_log_contract_id FOREIGN KEY (contract_id) REFERENCES entity (id);
ALTER TABLE contract_log ADD CONSTRAINT fk_contract_log_payer_account_id FOREIGN KEY (payer_account_id) REFERENCES entity (id);
ALTER TABLE contract_log ADD CONSTRAINT fk_contract_log_root_contract_id FOREIGN KEY (root_contract_id) REFERENCES entity (id);
ALTER TABLE contract_result ADD CONSTRAINT fk_contract_result_consensus_timestamp FOREIGN KEY (consensus_timestamp) REFERENCES transaction (consensus_timestamp);
ALTER TABLE contract_result ADD CONSTRAINT fk_contract_result_contract_id FOREIGN KEY (contract_id) REFERENCES entity (id);
ALTER TABLE contract_result ADD CONSTRAINT fk_contract_result_payer_account_id FOREIGN KEY (payer_account_id) REFERENCES entity (id);
ALTER TABLE contract_result ADD CONSTRAINT fk_contract_result_sender_id FOREIGN KEY (sender_id) REFERENCES entity (id);
ALTER TABLE contract_state ADD CONSTRAINT fk_contract_state_contract_id FOREIGN KEY (contract_id) REFERENCES entity (id);
ALTER TABLE contract_state_change ADD CONSTRAINT fk_contract_state_change_consensus_timestamp FOREIGN KEY (consensus_timestamp) REFERENCES transaction (consensus_timestamp);
ALTER TABLE contract_state_change ADD CONSTRAINT fk_contract_state_change_contract_id FOREIGN KEY (contract_id) REFERENCES entity (id);
ALTER TABLE contract_state_change ADD CONSTRAINT fk_contract_state_change_payer_account_id FOREIGN KEY (payer_account_id) REFERENCES entity (id);
ALTER TABLE contract_transaction ADD CONSTRAINT fk_contract_transaction_consensus_timestamp FOREIGN KEY (consensus_timestamp) REFERENCES transaction (consensus_timestamp);
ALTER TABLE contract_transaction ADD CONSTRAINT fk_contract_transaction_entity_id FOREIGN KEY (entity_id) REFERENCES entity (id);
ALTER TABLE contract_transaction ADD CONSTRAINT fk_contract_transaction_payer_account_id FOREIGN KEY (payer_account_id) REFERENCES entity (id);
ALTER TABLE contract_transaction_hash ADD CONSTRAINT fk_contract_transaction_hash_consensus_timestamp FOREIGN KEY (consensus_timestamp) REFERENCES transaction (consensus_timestamp);
ALTER TABLE contract_transaction_hash ADD CONSTRAINT fk_contract_transaction_hash_entity_id FOREIGN KEY (entity_id) REFERENCES entity (id);
ALTER TABLE contract_transaction_hash ADD CONSTRAINT fk_contract_transaction_hash_payer_account_id FOREIGN KEY (payer_account_id) REFERENCES entity (id);
ALTER TABLE crypto_allowance ADD CONSTRAINT fk_crypto_allowance_owner FOREIGN KEY (owner) REFERENCES entity (id);
ALTER TABLE crypto_allowance ADD CONSTRAINT fk_crypto_allowance_payer_account_id FOREIGN KEY (payer_account_id) REFERENCES entity (id);
ALTER TABLE crypto_allowance ADD CONSTRAINT fk_crypto_allowance_spender FOREIGN KEY (spender) REFERENCES entity (id);
ALTER TABLE crypto_allowance ADD CONSTRAINT fk_crypto_allowance_timestamp_range FOREIGN KEY (timestamp_range) REFERENCES transaction (consensus_timestamp);
ALTER TABLE crypto_allowance_history ADD CONSTRAINT fk_crypto_allowance_history_owner FOREIGN KEY (owner) REFERENCES entity (id);
ALTER TABLE crypto_allowance_history ADD CONSTRAINT fk_crypto_allowance_history_payer_account_id FOREIGN KEY (payer_account_id) REFERENCES entity (id);
ALTER TABLE crypto_allowance_history ADD CONSTRAINT fk_crypto_allowance_history_spender FOREIGN KEY (spender) REFERENCES entity (id);
ALTER TABLE crypto_allowance_history ADD CONSTRAINT fk_crypto_allowance_history_timestamp_range FOREIGN KEY (timestamp_range) REFERENCES transaction (consensus_timestamp);
ALTER TABLE crypto_transfer ADD CONSTRAINT fk_crypto_transfer_consensus_timestamp FOREIGN KEY (consensus_timestamp) REFERENCES transaction (consensus_timestamp);
ALTER TABLE crypto_transfer ADD CONSTRAINT fk_crypto_transfer_entity_id FOREIGN KEY (entity_id) REFERENCES entity (id);
ALTER TABLE crypto_transfer ADD CONSTRAINT fk_crypto_transfer_payer_account_id FOREIGN KEY (payer_account_id) REFERENCES entity (id);
ALTER TABLE custom_fee ADD CONSTRAINT fk_custom_fee_entity_id FOREIGN KEY (entity_id) REFERENCES entity (id);
ALTER TABLE custom_fee ADD CONSTRAINT fk_custom_fee_timestamp_range FOREIGN KEY (timestamp_range) REFERENCES transaction (consensus_timestamp);
ALTER TABLE custom_fee_history ADD CONSTRAINT fk_custom_fee_history_entity_id FOREIGN KEY (entity_id) REFERENCES entity (id);
ALTER TABLE custom_fee_history ADD CONSTRAINT fk_custom_fee_history_timestamp_range FOREIGN KEY (timestamp_range) REFERENCES transaction (consensus_timestamp);
ALTER TABLE entity ADD CONSTRAINT fk_entity_auto_renew_account_id FOREIGN KEY (auto_renew_account_id) REFERENCES entity (id);
ALTER TABLE entity ADD CONSTRAINT fk_entity_obtainer_id FOREIGN KEY (obtainer_id) REFERENCES entity (id);
ALTER TABLE entity ADD CONSTRAINT fk_entity_proxy_account_id FOREIGN KEY (proxy_account_id) REFERENCES entity (id);
ALTER TABLE entity ADD CONSTRAINT fk_entity_staked_account_id FOREIGN KEY (staked_account_id) REFERENCES entity (id);
ALTER TABLE entity ADD CONSTRAINT fk_entity_timestamp_range FOREIGN KEY (timestamp_range) REFERENCES transaction (consensus_timestamp);
ALTER TABLE entity_history ADD CONSTRAINT fk_entity_history_auto_renew_account_id FOREIGN KEY (auto_renew_account_id) REFERENCES entity (id);
ALTER TABLE entity_history ADD CONSTRAINT fk_entity_history_obtainer_id FOREIGN KEY (obtainer_id) REFERENCES entity (id);
ALTER TABLE entity_history ADD CONSTRAINT fk_entity_history_proxy_account_id FOREIGN KEY (proxy_account_id) REFERENCES entity (id);
ALTER TABLE entity_history ADD CONSTRAINT fk_entity_history_staked_account_id FOREIGN KEY (staked_account_id) REFERENCES entity (id);
ALTER TABLE entity_history ADD CONSTRAINT fk_entity_history_timestamp_range FOREIGN KEY (timestamp_range) REFERENCES transaction (consensus_timestamp);
ALTER TABLE entity_stake ADD CONSTRAINT fk_entity_stake_id FOREIGN KEY (id) REFERENCES entity (id);
ALTER TABLE entity_stake ADD CONSTRAINT fk_entity_stake_timestamp_range FOREIGN KEY (timestamp_range) REFERENCES transaction (consensus_timestamp);
ALTER TABLE entity_stake_history ADD CONSTRAINT fk_entity_stake_history_id FOREIGN KEY (id) REFERENCES entity (id);
ALTER TABLE entity_stake_history ADD CONSTRAINT fk_entity_stake_history_timestamp_range FOREIGN KEY (timestamp_range) REFERENCES transaction (consensus_timestamp);
ALTER TABLE entity_transaction ADD CONSTRAINT fk_entity_transaction_consensus_timestamp FOREIGN KEY (consensus_timestamp) REFERENCES transaction (consensus_timestamp);
ALTER TABLE entity_transaction ADD CONSTRAINT fk_entity_transaction_entity_id FOREIGN KEY (entity_id) REFERENCES entity (id);
ALTER TABLE entity_transaction ADD CONSTRAINT fk_entity_transaction_payer_account_id FOREIGN KEY (payer_account_id) REFERENCES entity (id);
ALTER TABLE ethereum_transaction ADD CONSTRAINT fk_ethereum_transaction_consensus_timestamp FOREIGN KEY (consensus_timestamp) REFERENCES transaction (consensus_timestamp);
ALTER TABLE ethereum_transaction ADD CONSTRAINT fk_ethereum_transaction_payer_account_id FOREIGN KEY (payer_account_id) REFERENCES entity (id);
ALTER TABLE file_data ADD CONSTRAINT fk_file_data_consensus_timestamp FOREIGN KEY (consensus_timestamp) REFERENCES transaction (consensus_timestamp);
ALTER TABLE file_data ADD CONSTRAINT fk_file_data_entity_id FOREIGN KEY (entity_id) REFERENCES entity (id);
ALTER TABLE live_hash ADD CONSTRAINT fk_live_hash_consensus_timestamp FOREIGN KEY (consensus_timestamp) REFERENCES transaction (consensus_timestamp);
ALTER TABLE network_freeze ADD CONSTRAINT fk_network_freeze_consensus_timestamp FOREIGN KEY (consensus_timestamp) REFERENCES transaction (consensus_timestamp);
ALTER TABLE network_freeze ADD CONSTRAINT fk_network_freeze_file_id FOREIGN KEY (file_id) REFERENCES entity (id);
ALTER TABLE network_freeze ADD CONSTRAINT fk_network_freeze_payer_account_id FOREIGN KEY (payer_account_id) REFERENCES entity (id);
ALTER TABLE network_stake ADD CONSTRAINT fk_network_stake_consensus_timestamp FOREIGN KEY (consensus_timestamp) REFERENCES transaction (consensus_timestamp);
ALTER TABLE nft ADD CONSTRAINT fk_nft_account_id FOREIGN KEY (account_id) REFERENCES entity (id);
ALTER TABLE nft ADD CONSTRAINT fk_nft_delegating_spender FOREIGN KEY (delegating_spender) REFERENCES entity (id);
ALTER TABLE nft ADD CONSTRAINT fk_nft_spender FOREIGN KEY (spender) REFERENCES entity (id);
ALTER TABLE nft ADD CONSTRAINT fk_nft_timestamp_range FOREIGN KEY (timestamp_range) REFERENCES transaction (consensus_timestamp);
ALTER TABLE nft ADD CONSTRAINT fk_nft_token_id FOREIGN KEY (token_id) REFERENCES entity (id);
ALTER TABLE nft_allowance ADD CONSTRAINT fk_nft_allowance_owner FOREIGN KEY (owner) REFERENCES entity (id);
ALTER TABLE nft_allowance ADD CONSTRAINT fk_nft_allowance_payer_account_id FOREIGN KEY (payer_account_id) REFERENCES entity (id);
ALTER TABLE nft_allowance ADD CONSTRAINT fk_nft_allowance_spender FOREIGN KEY (spender) REFERENCES entity (id);
ALTER TABLE nft_allowance ADD CONSTRAINT fk_nft_allowance_timestamp_range FOREIGN KEY (timestamp_range) REFERENCES transaction (consensus_timestamp);
ALTER TABLE nft_allowance ADD CONSTRAINT fk_nft_allowance_token_id FOREIGN KEY (token_id) REFERENCES entity (id);
ALTER TABLE nft_allowance_history ADD CONSTRAINT fk_nft_allowance_history_owner FOREIGN KEY (owner) REFERENCES entity (id);
ALTER TABLE nft_allowance_history ADD CONSTRAINT fk_nft_allowance_history_payer_account_id FOREIGN KEY (payer_account_id) REFERENCES entity (id);
ALTER TABLE nft_allowance_history ADD CONSTRAINT fk_nft_allowance_history_spender FOREIGN KEY (spender) REFERENCES entity (id);
ALTER TABLE nft_allowance_history ADD CONSTRAINT fk_nft_allowance_history_timestamp_range FOREIGN KEY (timestamp_range) REFERENCES transaction (consensus_timestamp);
ALTER TABLE nft_allowance_history ADD CONSTRAINT fk_nft_allowance_history_token_id FOREIGN KEY (token_id) REFERENCES entity (id);
ALTER TABLE nft_history ADD CONSTRAINT fk_nft_history_account_id FOREIGN KEY (account_id) REFERENCES entity (id);
ALTER TABLE nft_history ADD CONSTRAINT fk_nft_history_delegating_spender FOREIGN KEY (delegating_spender) REFERENCES entity (id);
ALTER TABLE nft_history ADD CONSTRAINT fk_nft_history_spender FOREIGN KEY (spender) REFERENCES entity (id);
ALTER TABLE nft_history ADD CONSTRAINT fk_nft_history_timestamp_range FOREIGN KEY (timestamp_range) REFERENCES transaction (consensus_timestamp);
ALTER TABLE nft_history ADD CONSTRAINT fk_nft_history_token_id FOREIGN KEY (token_id) REFERENCES entity (id);
ALTER TABLE node ADD CONSTRAINT fk_node_timestamp_range FOREIGN KEY (timestamp_range) REFERENCES transaction (consensus_timestamp);
ALTER TABLE node_history ADD CONSTRAINT fk_node_history_timestamp_range FOREIGN KEY (timestamp_range) REFERENCES transaction (consensus_timestamp);
ALTER TABLE node_stake ADD CONSTRAINT fk_node_stake_consensus_timestamp FOREIGN KEY (consensus_timestamp) REFERENCES transaction (consensus_timestamp);
ALTER TABLE non_fee_transfer ADD CONSTRAINT fk_non_fee_transfer_consensus_timestamp FOREIGN KEY (consensus_timestamp) REFERENCES transaction (consensus_timestamp);
ALTER TABLE non_fee_transfer ADD CONSTRAINT fk_non_fee_transfer_entity_id FOREIGN KEY (entity_id) REFERENCES entity (id);
ALTER TABLE non_fee_transfer ADD CONSTRAINT fk_non_fee_transfer_payer_account_id FOREIGN KEY (payer_account_id) REFERENCES entity (id);
ALTER TABLE prng ADD CONSTRAINT fk_prng_consensus_timestamp FOREIGN KEY (consensus_timestamp) REFERENCES transaction (consensus_timestamp);
ALTER TABLE prng ADD CONSTRAINT fk_prng_payer_account_id FOREIGN KEY (payer_account_id) REFERENCES entity (id);
ALTER TABLE reconciliation_job ADD CONSTRAINT fk_reconciliation_job_consensus_timestamp FOREIGN KEY (consensus_timestamp) REFERENCES transaction (consensus_timestamp);
ALTER TABLE schedule ADD CONSTRAINT fk_schedule_consensus_timestamp FOREIGN KEY (consensus_timestamp) REFERENCES transaction (consensus_timestamp);
ALTER TABLE schedule ADD CONSTRAINT fk_schedule_creator_account_id FOREIGN KEY (creator_account_id) REFERENCES entity (id);
ALTER TABLE schedule ADD CONSTRAINT fk_schedule_payer_account_id FOREIGN KEY (payer_account_id) REFERENCES entity (id);
ALTER TABLE schedule ADD CONSTRAINT fk_schedule_schedule_id FOREIGN KEY (schedule_id) REFERENCES entity (id);
ALTER TABLE staking_reward_transfer ADD CONSTRAINT fk_staking_reward_transfer_account_id FOREIGN KEY (account_id) REFERENCES entity (id);
ALTER TABLE staking_reward_transfer ADD CONSTRAINT fk_staking_reward_transfer_consensus_timestamp FOREIGN KEY (consensus_timestamp) REFERENCES transaction (consensus_timestamp);
ALTER TABLE staking_reward_transfer ADD CONSTRAINT fk_staking_reward_transfer_payer_account_id FOREIGN KEY (payer_account_id) REFERENCES entity (id);
ALTER TABLE token ADD CONSTRAINT fk_token_timestamp_range FOREIGN KEY (timestamp_range) REFERENCES transaction (consensus_timestamp);
ALTER TABLE token ADD CONSTRAINT fk_token_token_id FOREIGN KEY (token_id) REFERENCES entity (id);
ALTER TABLE token ADD CONSTRAINT fk_token_treasury_account_id FOREIGN KEY (treasury_account_id) REFERENCES entity (id);
ALTER TABLE token_account ADD CONSTRAINT fk_token_account_account_id FOREIGN KEY (account_id) REFERENCES entity (id);
ALTER TABLE token_account ADD CONSTRAINT fk_token_account_timestamp_range FOREIGN KEY (timestamp_range) REFERENCES transaction (consensus_timestamp);
ALTER TABLE token_account ADD CONSTRAINT fk_token_account_token_id FOREIGN KEY (token_id) REFERENCES entity (id);
ALTER TABLE token_account_history ADD CONSTRAINT fk_token_account_history_account_id FOREIGN KEY (account_id) REFERENCES entity (id);
ALTER TABLE token_account_history ADD CONSTRAINT fk_token_account_history_timestamp_range FOREIGN KEY (timestamp_range) REFERENCES transaction (consensus_timestamp);
ALTER TABLE token_account_history ADD CONSTRAINT fk_token_account_history_token_id FOREIGN KEY (token_id) REFERENCES entity (id);
ALTER TABLE token_airdrop ADD CONSTRAINT fk_token_airdrop_receiver_account_id FOREIGN KEY (receiver_account_id) REFERENCES entity (id);
ALTER TABLE token_airdrop ADD CONSTRAINT fk_token_airdrop_sender_account_id FOREIGN KEY (sender_account_id) REFERENCES entity (id);
ALTER TABLE token_airdrop ADD CONSTRAINT fk_token_airdrop_timestamp_range FOREIGN KEY (timestamp_range) REFERENCES transaction (consensus_timestamp);
ALTER TABLE token_airdrop ADD CONSTRAINT fk_token_airdrop_token_id FOREIGN KEY (token_id) REFERENCES entity (id);
ALTER TABLE token_airdrop_history ADD CONSTRAINT fk_token_airdrop_history_receiver_account_id FOREIGN KEY (receiver_account_id) REFERENCES entity (id);
ALTER TABLE token_airdrop_history ADD CONSTRAINT fk_token_airdrop_history_sender_account_id FOREIGN KEY (sender_account_id) REFERENCES entity (id);
ALTER TABLE token_airdrop_history ADD CONSTRAINT fk_token_airdrop_history_timestamp_range FOREIGN KEY (timestamp_range) REFERENCES transaction (consensus_timestamp);
ALTER TABLE token_airdrop_history ADD CONSTRAINT fk_token_airdrop_history_token_id FOREIGN KEY (token_id) REFERENCES entity (id);
ALTER TABLE token_allowance ADD CONSTRAINT fk_token_allowance_owner FOREIGN KEY (owner) REFERENCES entity (id);
ALTER TABLE token_allowance ADD CONSTRAINT fk_token_allowance_payer_account_id FOREIGN KEY (payer_account_id) REFERENCES entity (id);
ALTER TABLE token_allowance ADD CONSTRAINT fk_token_allowance_spender FOREIGN KEY (spender) REFERENCES entity (id);
ALTER TABLE token_allowance ADD CONSTRAINT fk_token_allowance_timestamp_range FOREIGN KEY (timestamp_range) REFERENCES transaction (consensus_timestamp);
ALTER TABLE token_allowance ADD CONSTRAINT fk_token_allowance_token_id FOREIGN KEY (token_id) REFERENCES entity (id);
ALTER TABLE token_allowance_history ADD CONSTRAINT fk_token_allowance_history_owner FOREIGN KEY (owner) REFERENCES entity (id);
ALTER TABLE token_allowance_history ADD CONSTRAINT fk_token_allowance_history_payer_account_id FOREIGN KEY (payer_account_id) REFERENCES entity (id);
ALTER TABLE token_allowance_history ADD CONSTRAINT fk_token_allowance_history_spender FOREIGN KEY (spender) REFERENCES entity (id);
ALTER TABLE token_allowance_history ADD CONSTRAINT fk_token_allowance_history_timestamp_range FOREIGN KEY (timestamp_range) REFERENCES transaction (consensus_timestamp);
ALTER TABLE token_allowance_history ADD CONSTRAINT fk_token_allowance_history_token_id FOREIGN KEY (token_id) REFERENCES entity (id);
ALTER TABLE token_balance ADD CONSTRAINT fk_token_balance_account_id FOREIGN KEY (account_id) REFERENCES entity (id);
ALTER TABLE token_balance ADD CONSTRAINT fk_token_balance_consensus_timestamp FOREIGN KEY (consensus_timestamp) REFERENCES transaction (consensus_timestamp);
ALTER TABLE token_balance ADD CONSTRAINT fk_token_balance_token_id FOREIGN KEY (token_id) REFERENCES entity (id);
ALTER TABLE token_history ADD CONSTRAINT fk_token_history_timestamp_range FOREIGN KEY (timestamp_range) REFERENCES transaction (consensus_timestamp);
ALTER TABLE token_history ADD CONSTRAINT fk_token_history_token_id FOREIGN KEY (token_id) REFERENCES entity (id);
ALTER TABLE token_history ADD CONSTRAINT fk_token_history_treasury_account_id FOREIGN KEY (treasury_account_id) REFERENCES entity (id);
ALTER TABLE token_transfer ADD CONSTRAINT fk_token_transfer_account_id FOREIGN KEY (account_id) REFERENCES entity (id);
ALTER TABLE token_transfer ADD CONSTRAINT fk_token_transfer_consensus_timestamp FOREIGN KEY (consensus_timestamp) REFERENCES transaction (consensus_timestamp);
ALTER TABLE token_transfer ADD CONSTRAINT fk_token_transfer_payer_account_id FOREIGN KEY (payer_account_id) REFERENCES entity (id);
ALTER TABLE token_transfer ADD CONSTRAINT fk_token_transfer_token_id FOREIGN KEY (token_id) REFERENCES entity (id);
ALTER TABLE topic ADD CONSTRAINT fk_topic_id FOREIGN KEY (id) REFERENCES entity (id);
ALTER TABLE topic ADD CONSTRAINT fk_topic_timestamp_range FOREIGN KEY (timestamp_range) REFERENCES transaction (consensus_timestamp);
ALTER TABLE topic_history ADD CONSTRAINT fk_topic_history_id FOREIGN KEY (id) REFERENCES entity (id);
ALTER TABLE topic_history ADD CONSTRAINT fk_topic_history_timestamp_range FOREIGN KEY (timestamp_range) REFERENCES transaction (consensus_timestamp);
ALTER TABLE topic_message ADD CONSTRAINT fk_topic_message_consensus_timestamp FOREIGN KEY (consensus_timestamp) REFERENCES transaction (consensus_timestamp);
ALTER TABLE topic_message ADD CONSTRAINT fk_topic_message_payer_account_id FOREIGN KEY (payer_account_id) REFERENCES entity (id);
ALTER TABLE topic_message ADD CONSTRAINT fk_topic_message_topic_id FOREIGN KEY (topic_id) REFERENCES entity (id);
ALTER TABLE topic_message_lookup ADD CONSTRAINT fk_topic_message_lookup_topic_id FOREIGN KEY (topic_id) REFERENCES entity (id);
ALTER TABLE transaction ADD CONSTRAINT fk_transaction_entity_id FOREIGN KEY (entity_id) REFERENCES entity (id);
ALTER TABLE transaction ADD CONSTRAINT fk_transaction_node_account_id FOREIGN KEY (node_account_id) REFERENCES entity (id);
ALTER TABLE transaction ADD CONSTRAINT fk_transaction_parent_consensus_timestamp FOREIGN KEY (parent_consensus_timestamp) REFERENCES transaction (consensus_timestamp);
ALTER TABLE transaction ADD CONSTRAINT fk_transaction_payer_account_id FOREIGN KEY (payer_account_id) REFERENCES entity (id);
ALTER TABLE transaction_hash ADD CONSTRAINT fk_transaction_hash_consensus_timestamp FOREIGN KEY (consensus_timestamp) REFERENCES transaction (consensus_timestamp);
ALTER TABLE transaction_hash ADD CONSTRAINT fk_transaction_hash_payer_account_id FOREIGN KEY (payer_account_id) REFERENCES entity (id);
ALTER TABLE transaction_signature ADD CONSTRAINT fk_transaction_signature_consensus_timestamp FOREIGN KEY (consensus_timestamp) REFERENCES transaction (consensus_timestamp);
ALTER TABLE transaction_signature ADD CONSTRAINT fk_transaction_signature_entity_id FOREIGN KEY (entity_id) REFERENCES entity (id);


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
