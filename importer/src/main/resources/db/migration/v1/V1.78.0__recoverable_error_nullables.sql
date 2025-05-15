alter table contract_action
    alter column call_type drop not null;
alter table contract_action
    alter column caller drop not null;
alter table contract_action
    alter column caller_type drop not null;
