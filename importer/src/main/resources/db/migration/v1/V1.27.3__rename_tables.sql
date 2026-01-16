alter table t_file_data rename to file_data;
alter table t_record_files rename to record_file;
alter table t_livehashes rename to live_hash;
alter table t_contract_result rename to contract_result;
alter table account_balances rename to account_balance;
alter table contract_result rename column function_params to function_parameters;
