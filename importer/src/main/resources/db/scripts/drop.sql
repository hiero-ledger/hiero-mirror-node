-- SPDX-License-Identifier: Apache-2.0

do $$
declare
    obj record;
begin
    -- drop all tables from public and temporary schema
    for obj in
        select schemaname, tablename
        from pg_tables
        where schemaname in ('public', 'temporary')
    loop
        execute format('drop table if exists %I.%I cascade', obj.schemaname, obj.tablename);
    end loop;

    if exists (select 1 from pg_roles where rolname = 'mirror_api') then
        execute 'drop owned by mirror_api cascade';
        drop role mirror_api;
    end if;

    -- drop all views in public schema starting with mirror_
    for obj in
        select table_name
        from information_schema.views
        where table_schema = 'public'
          and table_name like 'mirror_%'
    loop
        execute format('drop view if exists public.%I cascade', obj.table_name);
    end loop;

    -- drop all materialized views
    for obj in
        select matviewname from pg_matviews where schemaname = 'public'
    loop
        execute format('drop materialized view if exists public.%I cascade', obj.matviewname);
    end loop;

    -- drop types owned by mirror_node in public schema
    for obj in
        select t.typname
        from pg_type t
        join pg_namespace n on n.oid = t.typnamespace
        where n.nspname = 'public'
          and t.typowner = (select oid from pg_roles where rolname = 'mirror_node')
    loop
        execute format('drop type if exists public.%I cascade', obj.typname);
    end loop;

    -- drop functions and procedures owned by mirror_node from public schema
    for obj in
        select
            n.nspname as schema_name,
            p.proname as function_name,
            pg_get_function_identity_arguments(p.oid) as args
        from pg_proc p
        join pg_namespace n on p.pronamespace = n.oid
        join pg_language l on p.prolang = l.oid
        where n.nspname = 'public'
          and p.prokind in ('f', 'p')
          and p.proowner = (select oid from pg_roles where rolname = 'mirror_node')
    loop
        execute format(
            'drop routine if exists %I.%I(%s) cascade',
            obj.schema_name,
            obj.function_name,
            obj.args
        );
    end loop;
end;
$$;
