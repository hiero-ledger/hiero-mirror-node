alter table t_entities drop column topic_valid_start_time;
alter table t_entities drop column admin_key__deprecated;
alter table t_entities drop column exp_time_seconds;
alter table t_entities drop column exp_time_nanos;
alter table t_entities add column auto_renew_account_id bigint null;
alter table t_entities add constraint autorenew_account foreign key (auto_renew_account_id) references t_entities(id);

delete from t_transaction_results where proto_id >= 150 and proto_id <= 159;
insert into t_transaction_results (proto_id, result) values (150, 'INVALID_TOPIC_ID');
insert into t_transaction_results (proto_id, result) values (155, 'INVALID_ADMIN_KEY'); -- 151-154 were deleted in proto
insert into t_transaction_results (proto_id, result) values (156, 'INVALID_SUBMIT_KEY');
insert into t_transaction_results (proto_id, result) values (157, 'UNAUTHORIZED');
insert into t_transaction_results (proto_id, result) values (158, 'INVALID_TOPIC_MESSAGE');
insert into t_transaction_results (proto_id, result) values (159, 'INVALID_AUTORENEW_ACCOUNT');
insert into t_transaction_results (proto_id, result) values (160, 'AUTORENEW_ACCOUNT_NOT_ALLOWED');
insert into t_transaction_results (proto_id, result) values (162, 'TOPIC_EXPIRED');
