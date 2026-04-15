alter table if exists contract_log
  add column if not exists synthetic boolean null;
