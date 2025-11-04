alter table hook_storage
    add column deleted boolean not null default false;

alter table hook_storage_change
    add column deleted boolean not null default false;