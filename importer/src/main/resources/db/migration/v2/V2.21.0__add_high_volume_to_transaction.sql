alter table if exists transaction
    add column if not exists high_volume boolean default null;
