-- access_list was bytea and since data was always empty we can drop the column and create it again to avoid
-- structural conversion overhead we would have in case of column type altering
alter table if exists ethereum_transaction
    drop column if exists access_list;

alter table if exists ethereum_transaction
    add column if not exists access_list jsonb null;
