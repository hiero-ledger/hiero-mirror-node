alter table if exists account_balance_file drop constraint if exists account_balance_file__pk;
alter table if exists account_balance_file
  add constraint account_balance_file__pk primary key (consensus_timestamp);
