alter table if exists transaction
    add column if not exists high_volume boolean not null default false;
