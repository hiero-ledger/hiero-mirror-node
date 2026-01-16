alter table if exists record_file add column sidecar_count int not null default 0;

create table if not exists sidecar_file (
  bytes          bytea null,
  count          int null,
  consensus_end  bigint not null,
  hash_algorithm int not null,
  hash           bytea not null,
  id             int not null,
  name           character varying(250) not null,
  size           int null,
  types          int[] not null,
  primary key (consensus_end, id)
);
