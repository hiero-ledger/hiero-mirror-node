alter table if exists registered_node
  add column if not exists type smallint[] not null default '{}';

alter table if exists registered_node_history
  add column if not exists type smallint[] not null default '{}';

create index if not exists registered_node__type
      on registered_node using gin (type) where deleted is false;