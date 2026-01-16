-------------------
-- Update schema to support partial contract_result
-------------------

alter table contract_result
    alter column gas_used drop not null;
