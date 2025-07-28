-- SPDX-License-Identifier: Apache-2.0

do $$
declare
    obj record;
begin
    -- drop all foreign key constraints in public
    for obj in
        select conname, conrelid::regclass
        from pg_constraint c
        join pg_class r on c.conrelid = r.oid
        join pg_namespace n on r.relnamespace = n.oid
        where contype = 'f'
          and n.nspname = 'public'
    loop
        execute format('alter table %s drop constraint %I', obj.conrelid, obj.conname);
    end loop;

    -- drop all tables from public schema
    for obj in
        select tablename from pg_tables where schemaname = 'public'
    loop
        execute format('drop table if exists public.%I cascade', obj.tablename);
    end loop;

    -- drop all tables from temporary schema
    for obj in
        select tablename from pg_tables where schemaname = 'temporary'
    loop
        execute format('drop table if exists temporary.%I cascade', obj.tablename);
    end loop;

    if exists (select 1 from pg_roles where rolname = 'mirror_api') then
        execute 'alter default privileges for role mirror_node in schema public revoke all on tables from mirror_api';
        execute 'alter default privileges for role mirror_node in schema public revoke all on sequences from mirror_api';
        execute 'alter default privileges for role mirror_node in schema public revoke all on functions from mirror_api';
        execute 'alter default privileges for role mirror_node in schema public revoke all on types from mirror_api';

        -- revoke privileges on mirror_node_time_partitions only if the table exists
        if exists (
            select 1
            from pg_class c
            join pg_namespace n on n.oid = c.relnamespace
            where c.relname = 'mirror_node_time_partitions'
              and n.nspname = 'public'
        ) then
            execute 'revoke all privileges on public.mirror_node_time_partitions from mirror_api';
        end if;
    end if;

    drop role if exists mirror_api;

    -- drop all views only in the public schema that do not start with 'citus' or 'pg_'
    for obj in
        select table_schema, table_name
        from information_schema.views
        where table_schema = 'public'
          and table_name like 'mirror_%'
    loop
        execute format('drop view if exists %I.%I cascade', obj.table_schema, obj.table_name);
    end loop;

    -- drop all materialized views
    for obj in
        select matviewname from pg_matviews where schemaname = 'public'
    loop
        execute format('drop materialized view if exists public.%I cascade', obj.matviewname);
    end loop;

    -- drop user-defined types in public schema that are not:
    -- 1. Named with pg_/citus/gbtree
    -- 2. Used by views
    -- 3. Owned by extensions (like pg_trgm)
    for obj in
        select t.typname
        from pg_type t
        join pg_namespace n on n.oid = t.typnamespace
        where n.nspname = 'public'
          and t.typtype in ('c', 'e', 'r', 'd')
          and t.typname not like '%pg_%'
          and t.typname not like '%citus%'
          and t.typname not like '%gbtree%'
          -- exclude types owned by extensions
          and not exists (
              select 1
              from pg_depend d
              join pg_extension e on d.refobjid = e.oid
              where d.classid = 'pg_type'::regclass
                and d.objid = t.oid
                and d.deptype = 'e'
          )
          -- exclude types used by views
          and not exists (
              select 1
              from pg_depend d
              join pg_class c on d.refobjid = t.oid
              where d.classid = 'pg_class'::regclass
                and c.relkind = 'v'
                and d.deptype != 'i'
          )
    loop
        execute format('drop type if exists public.%I cascade', obj.typname);
    end loop;

    -- drop all plpgsql functions from the public schema
    for obj in
        select
            n.nspname as schema_name,
            p.proname as function_name,
            pg_get_function_identity_arguments(p.oid) as args
        from pg_proc p
        join pg_namespace n on p.pronamespace = n.oid
        join pg_language l on p.prolang = l.oid
        where n.nspname = 'public'
          and l.lanname = 'plpgsql'
          and p.prokind = 'f'
    loop
        execute format(
            'drop function if exists %I.%I(%s) cascade',
            obj.schema_name,
            obj.function_name,
            obj.args
        );
    end loop;

    -- drop all plpgsql procedures from the public schema
    for obj in
        select
            n.nspname as schema_name,
            p.proname as procedure_name,
            pg_get_function_identity_arguments(p.oid) as args
        from pg_proc p
        join pg_namespace n on p.pronamespace = n.oid
        join pg_language l on p.prolang = l.oid
        where n.nspname = 'public'
          and l.lanname = 'plpgsql'
          and p.prokind = 'p'
    loop
        execute format(
            'drop procedure if exists %I.%I(%s) cascade',
            obj.schema_name,
            obj.procedure_name,
            obj.args
        );
    end loop;

end;
$$;
