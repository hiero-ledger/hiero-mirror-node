alter table if exists contract_result
    add column if not exists failed_initcode bytea null;
