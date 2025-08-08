-- Script to drop all partitions and convert parent tables to regular tables
-- WARNING: This removes all partition data. Backup before running!

DO $$
DECLARE
    r RECORD;
    drop_count INTEGER := 0;
    converted_count INTEGER := 0;
    partitioned_tables TEXT[];
BEGIN
    RAISE NOTICE 'Starting partition removal...';
    
    -- Get partitioned parent tables
    SELECT ARRAY(SELECT DISTINCT parent FROM mirror_node_time_partitions ORDER BY parent) INTO partitioned_tables;
    
    -- Drop ALL partition tables in one combined loop
    FOR r IN (
        -- Time-based partitions from view
        SELECT name AS table_name, 'time' AS type FROM mirror_node_time_partitions
        UNION ALL
        -- Transaction hash sharded partitions
        SELECT tablename, 'hash' FROM pg_tables 
        WHERE schemaname = 'public' 
        AND (tablename ~ '^transaction_hash_sharded_[0-9]{2}$' OR tablename ~ '^transaction_hash_[0-9]{2}$')
        UNION ALL
        -- Orphaned time partitions not in view
        SELECT tablename, 'orphan' FROM pg_tables 
        WHERE schemaname = 'public' AND tablename ~ '_p[0-9]{4}_[0-9]{2}$'
        AND NOT EXISTS (SELECT 1 FROM mirror_node_time_partitions WHERE name = tablename)
    ) LOOP
        BEGIN
            EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', r.table_name);
            drop_count := drop_count + 1;
            RAISE NOTICE 'Dropped % partition: %', r.type, r.table_name;
        EXCEPTION WHEN OTHERS THEN
            RAISE WARNING 'Failed to drop %: %', r.table_name, SQLERRM;
        END;
    END LOOP;
    
    -- Convert ALL partitioned tables to regular tables
    FOR r IN (
        SELECT c.relname AS table_name 
        FROM pg_partitioned_table pt 
        JOIN pg_class c ON pt.partrelid = c.oid 
        JOIN pg_namespace n ON c.relnamespace = n.oid 
        WHERE n.nspname = 'public'
    ) LOOP
        BEGIN
            EXECUTE format('CREATE TABLE %I_temp (LIKE %I INCLUDING ALL)', r.table_name, r.table_name);
            EXECUTE format('DROP TABLE %I CASCADE', r.table_name);
            EXECUTE format('ALTER TABLE %I_temp RENAME TO %I', r.table_name, r.table_name);
            converted_count := converted_count + 1;
            RAISE NOTICE 'Converted to regular table: %', r.table_name;
        EXCEPTION WHEN OTHERS THEN
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
    FOR r IN (
        SELECT table_name FROM information_schema.tables 
        WHERE table_schema = 'public' 
        AND table_name IN ('account_balance', 'token_balance', 'transaction_hash_sharded')
    ) LOOP
        EXECUTE format('ANALYZE %I', r.table_name);
        RAISE NOTICE 'Analyzed: %', r.table_name;
    END LOOP;
END $$;