alter table if exists contract add column if not exists max_automatic_token_associations integer;
alter table if exists contract_history add column if not exists max_automatic_token_associations integer;

update contract set max_automatic_token_associations = 0;
update contract_history set max_automatic_token_associations = 0;
