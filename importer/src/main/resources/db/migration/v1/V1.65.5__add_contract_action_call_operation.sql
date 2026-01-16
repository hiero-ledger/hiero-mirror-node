alter table if exists contract_action
    add column if not exists call_operation_type integer null;
