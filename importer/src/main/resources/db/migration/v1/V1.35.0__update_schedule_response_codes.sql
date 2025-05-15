-------------------
-- Support new response codes
-------------------

-- Update existing transaction result types since SCHEDULE_WAS_DELETED was deleted
update t_transaction_results set result = 'NO_NEW_VALID_SIGNATURES' where proto_id = 205;
update t_transaction_results set result = 'INVALID_SCHEDULE_ACCOUNT_ID' where proto_id = 204;
update t_transaction_results set result = 'INVALID_SCHEDULE_PAYER_ID' where proto_id = 203;

-- Add new schedule transaction result types

insert into t_transaction_results (proto_id, result)
values (206, 'UNRESOLVABLE_REQUIRED_SIGNERS'),
       (207, 'UNPARSEABLE_SCHEDULED_TRANSACTION'),
       (208, 'UNSCHEDULABLE_TRANSACTION'),
       (209, 'SOME_SIGNATURES_WERE_INVALID'),
       (210, 'TRANSACTION_ID_FIELD_NOT_ALLOWED');
