-- Script to drop all partitions in the mirror node database
-- This will drop ALL partition tables but keep the original parent tables as regular tables
-- WARNING: This will remove all data in the partitions
-- Make sure to backup your data before running this script

DO
$$
DECLARE
    partition_record RECORD;
    parent_table_record RECORD;
    drop_count INTEGER := 0;
    converted_count INTEGER := 0;
    partitioned_tables TEXT[];
BEGIN
    -- Log start of partition removal
    RAISE NOTICE 'Starting partition removal process...';
    
    -- First, collect all unique parent tables that are partitioned
    SELECT ARRAY(SELECT DISTINCT parent FROM mirror_node_time_partitions ORDER BY parent) INTO partitioned_tables;
    RAISE NOTICE 'Found % partitioned parent tables: %', array_length(partitioned_tables, 1), partitioned_tables;
    
    -- Drop all partition tables first
    FOR partition_record IN 
        SELECT name, parent 
        FROM mirror_node_time_partitions 
        ORDER BY parent, name
    LOOP
        -- Drop the partition table
        BEGIN
            EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', partition_record.name);
            drop_count := drop_count + 1;
            RAISE NOTICE 'Dropped partition: % (parent: %)', partition_record.name, partition_record.parent;
        EXCEPTION
            WHEN OTHERS THEN
                RAISE WARNING 'Failed to drop partition %: %', partition_record.name, SQLERRM;
        END;
    END LOOP;
    
    -- Drop transaction_hash sharded partitions (transaction_hash_sharded_XX and transaction_hash_XX)
    FOR partition_record IN
        SELECT schemaname, tablename
        FROM pg_tables 
        WHERE schemaname = 'public' 
        AND (tablename ~ '^transaction_hash_sharded_[0-9]{2}$'  -- Pattern matches transaction_hash_sharded_XX
             OR tablename ~ '^transaction_hash_[0-9]{2}$')      -- Pattern matches transaction_hash_XX
    LOOP
        BEGIN
            EXECUTE format('DROP TABLE IF EXISTS %I.%I CASCADE', partition_record.schemaname, partition_record.tablename);
            drop_count := drop_count + 1;
            RAISE NOTICE 'Dropped transaction_hash shard: %', partition_record.tablename;
        EXCEPTION
            WHEN OTHERS THEN
                RAISE WARNING 'Failed to drop transaction_hash shard %: %', partition_record.tablename, SQLERRM;
        END;
    END LOOP;
    
    -- Also drop any orphaned time-based partition tables that might not be in the view
    FOR partition_record IN
        SELECT schemaname, tablename
        FROM pg_tables 
        WHERE schemaname = 'public' 
        AND tablename ~ '_p[0-9]{4}_[0-9]{2}$'  -- Pattern matches time partition naming convention
        AND NOT EXISTS (
            SELECT 1 FROM mirror_node_time_partitions WHERE name = tablename
        )
    LOOP
        BEGIN
            EXECUTE format('DROP TABLE IF EXISTS %I.%I CASCADE', partition_record.schemaname, partition_record.tablename);
            drop_count := drop_count + 1;
            RAISE NOTICE 'Dropped orphaned partition: %', partition_record.tablename;
        EXCEPTION
            WHEN OTHERS THEN
                RAISE WARNING 'Failed to drop orphaned partition %: %', partition_record.tablename, SQLERRM;
        END;
    END LOOP;
    
    -- Handle transaction_hash_sharded table specifically
    BEGIN
        -- Check if transaction_hash_sharded exists and is partitioned
        PERFORM 1 FROM pg_partitioned_table pt
        JOIN pg_class c ON pt.partrelid = c.oid
        WHERE c.relname = 'transaction_hash_sharded';
        
        IF FOUND THEN
            -- Create a regular table with the same structure as transaction_hash_sharded
            EXECUTE 'CREATE TABLE transaction_hash_sharded_temp_unpartitioned (LIKE transaction_hash_sharded INCLUDING ALL)';
            
            -- Drop the partitioned table
            EXECUTE 'DROP TABLE transaction_hash_sharded CASCADE';
            
            -- Rename the temporary table
            EXECUTE 'ALTER TABLE transaction_hash_sharded_temp_unpartitioned RENAME TO transaction_hash_sharded';
            
            converted_count := converted_count + 1;
            RAISE NOTICE 'Converted transaction_hash_sharded to regular table';
        END IF;
    EXCEPTION
        WHEN OTHERS THEN
            RAISE WARNING 'Failed to convert transaction_hash_sharded: %', SQLERRM;
            -- Clean up temp table if it exists
            EXECUTE 'DROP TABLE IF EXISTS transaction_hash_sharded_temp_unpartitioned';
    END;
    
    -- Convert time-partitioned parent tables back to regular tables
    -- This is done by creating a new regular table and dropping the partitioned one
    FOR parent_table_record IN
        SELECT DISTINCT t.table_name
        FROM information_schema.tables t
        WHERE t.table_schema = 'public'
        AND t.table_name = ANY(partitioned_tables)
        AND EXISTS (
            SELECT 1 FROM pg_partitioned_table pt
            JOIN pg_class c ON pt.partrelid = c.oid
            WHERE c.relname = t.table_name
        )
    LOOP
        BEGIN
            DECLARE
                table_name TEXT := parent_table_record.table_name;
                temp_table_name TEXT := table_name || '_temp_unpartitioned';
            BEGIN
                -- Create a new regular table with the same structure as the partitioned table
                EXECUTE format('CREATE TABLE %I (LIKE %I INCLUDING ALL)', temp_table_name, table_name);
                
                -- Drop the partitioned table
                EXECUTE format('DROP TABLE %I CASCADE', table_name);
                
                -- Rename the temporary table to the original name
                EXECUTE format('ALTER TABLE %I RENAME TO %I', temp_table_name, table_name);
                
                converted_count := converted_count + 1;
                RAISE NOTICE 'Converted partitioned table % to regular table', table_name;
            EXCEPTION
                WHEN OTHERS THEN
                    RAISE WARNING 'Failed to convert partitioned table %: %', table_name, SQLERRM;
                    -- Clean up temp table if it exists
                    EXECUTE format('DROP TABLE IF EXISTS %I', temp_table_name);
            END;
        END;
    END LOOP;
    
    -- Summary
    RAISE NOTICE 'Partition removal completed. Partitions dropped: %, Tables converted: %', drop_count, converted_count;
    
    -- Verify no partitions remain
    PERFORM 1 FROM mirror_node_time_partitions LIMIT 1;
    IF NOT FOUND THEN
        RAISE NOTICE 'SUCCESS: All partitions have been removed and parent tables converted to regular tables';
    ELSE
        RAISE WARNING 'WARNING: Some partitions may still exist';
    END IF;
    
    -- List the converted tables
    RAISE NOTICE 'Converted tables (now regular tables): %', partitioned_tables;
END;
$$;

-- Analyze the converted tables to update statistics for query planning
DO
$$
DECLARE
    table_name TEXT;
BEGIN
    FOR table_name IN
        SELECT t.table_name
        FROM information_schema.tables t
        WHERE t.table_schema = 'public'
        AND t.table_name IN ('account_balance', 'token_balance', 'transaction_hash_sharded')
    LOOP
        EXECUTE format('ANALYZE %I', table_name);
        RAISE NOTICE 'Analyzed table: %', table_name;
    END LOOP;
END;
$$;