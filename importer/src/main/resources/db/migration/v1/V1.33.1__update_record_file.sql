--- add columns to support record file format v5
alter table if exists record_file add column if not exists digest_algorithm int not null default 0;
alter table if exists record_file add column if not exists end_running_hash varchar(96);
alter table if exists record_file add column if not exists hapi_version_major int;
alter table if exists record_file add column if not exists hapi_version_minor int;
alter table if exists record_file add column if not exists hapi_version_patch int;
alter table if exists record_file add column if not exists version int not null default 2;

alter table if exists record_file alter column digest_algorithm drop default;
alter table if exists record_file alter column version drop default;
