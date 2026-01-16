alter table if exists contract
    drop column if exists parent_id;

alter table if exists contract_history
    drop column if exists parent_id;
