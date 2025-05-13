create table if not exists entity_transaction (
  consensus_timestamp bigint not null,
  entity_id           bigint not null,
  payer_account_id    bigint not null,
  result              smallint not null,
  type                smallint not null,
  primary key (entity_id, consensus_timestamp)
);