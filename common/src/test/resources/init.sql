-- SPDX-License-Identifier: Apache-2.0

create role readonly;
create role readwrite in role readonly;
create role temporary_admin in role readwrite;

-- Create users
create user mirror_graphql with login password 'mirror_graphql_pass' in role readonly;
create user mirror_grpc with login password 'mirror_grpc_pass' in role readonly;
create user mirror_importer with login password 'mirror_importer_pass' in role readwrite;
create user mirror_rest with login password 'mirror_rest_pass' in role readonly;
create user mirror_rest_java with login password 'mirror_rest_java_pass' in role readonly;
create user mirror_rosetta with login password 'mirror_rosetta_pass' in role readonly;
create user mirror_web3 with login password 'mirror_web3_pass' in role readonly;
alter user mirror_node with createrole;
grant readwrite to mirror_node;

-- Grant temp schema admin privileges
grant temporary_admin to mirror_node;
grant temporary_admin to mirror_importer;

-- Create schema
create schema if not exists public authorization mirror_node;
grant usage on schema public to public;
revoke create on schema public from public;

-- Create temp table schema
create schema if not exists temporary authorization temporary_admin;
grant usage on schema temporary to public;
revoke create on schema temporary from public;

-- Add extensions
create extension if not exists btree_gist;
create extension if not exists pg_stat_statements;
create extension if not exists pg_trgm;

-- Grant readonly privileges
grant connect on database mirror_node to readonly;
grant select on all tables in schema public, temporary to readonly;
grant usage on schema public, temporary to readonly;
alter default privileges in schema public, temporary grant select on tables to readonly;

-- Grant readwrite privileges
grant insert, update, delete on all tables in schema public to readwrite;
alter default privileges in schema public grant insert, update, delete on tables to readwrite;

-- Grant owner privileges
grant all privileges on database mirror_node to mirror_node;
grant create on database mirror_node to mirror_node;
grant all on schema public, temporary to mirror_node;
grant temporary on database mirror_node to mirror_node;
alter type timestamptz owner to mirror_node;

alter database mirror_node set search_path = public, temporary;
