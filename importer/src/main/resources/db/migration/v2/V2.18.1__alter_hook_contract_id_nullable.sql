-- Allow contract_id to be null in hook
alter table hook
    alter column contract_id drop not null;
-- Allow contract_id to be null in hook_history
alter table hook_history
    alter column contract_id drop not null;