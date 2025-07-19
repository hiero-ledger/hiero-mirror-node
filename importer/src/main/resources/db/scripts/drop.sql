-- SPDX-License-Identifier: Apache-2.0

do
$$
declare
    obj record;
begin
    -- drop all foreign key constraints
    for obj in
        select conname, conrelid::regclass
        from pg_constraint
        where contype = 'f'
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
    end if;

    if exists (
        select 1
        from pg_class c
        join pg_namespace n on n.oid = c.relnamespace
        where c.relname = 'mirror_node_time_partitions'
          and n.nspname = 'public'
    ) then
        execute 'revoke all privileges on public.mirror_node_time_partitions from mirror_api';
    end if;

    drop role if exists mirror_api;

    -- drop all views in non-system schemas
    for obj in
        select table_schema, table_name
        from information_schema.views
        where table_schema not in ('pg_catalog', 'information_schema')
    loop
        execute format('drop view if exists %I.%I cascade', obj.table_schema, obj.table_name);
    end loop;

    -- drop all materialized views
    for obj in
        select matviewname from pg_matviews where schemaname = 'public'
    loop
        execute format('drop materialized view if exists public.%I cascade', obj.matviewname);
    end loop;

    -- delete all types
    for obj in
        select t.typname
        from pg_type t
        join pg_namespace n on n.oid = t.typnamespace
        where n.nspname = 'public'
          and t.typtype in ('c', 'e', 'r', 'd')
    loop
        execute format('drop type if exists public.%I cascade', obj.typname);
    end loop;

    -- drop all sequences
    for obj in
        select sequence_name from information_schema.sequences where sequence_schema = 'public'
    loop
        execute format('drop sequence if exists public.%I cascade', obj.sequence_name);
    end loop;

    -- drop all indexes
    for obj in
        select indexname from pg_indexes where schemaname = 'public'
    loop
        execute format('drop index if exists public.%I cascade', obj.indexname);
    end loop;

    -- delete all types
    for obj in
        select t.typname
        from pg_type t
        join pg_namespace n on n.oid = t.typnamespace
        where n.nspname = 'public'
          and t.typtype in ('c', 'e', 'r')
    loop
        execute format('drop type if exists public.%I cascade', obj.typname);
    end loop;

    -- delete functions
    drop function if exists f_file_create(_file_name character varying) cascade;
    drop function if exists f_file_complete(_file_id bigint, _file_hash character varying, _prev_hash character varying) cascade;
    drop function if exists f_entity_create(_shard bigint, _realm bigint, _num bigint, _type_id integer, _exp_time_sec bigint, _exp_time_nanos bigint, _exp_time_ns bigint, _auto_renew bigint, _admin_key bytea, _key bytea, _proxy_acc_id bigint) cascade;

end;
$$
