alter table if exists schedule
  add column if not exists expiration_time bigint null,
  add column if not exists wait_for_expiry boolean not null default false;
