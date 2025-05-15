-- add evm_address to tables contract and contract_history

alter table contract add column evm_address bytea null;
create index if not exists contract__evm_address on contract (evm_address) where evm_address is not null;

alter table contract_history add column evm_address bytea null;
