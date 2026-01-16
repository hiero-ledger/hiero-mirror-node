-------------------
-- Support revised scheduled transactions design
-------------------

-- New / missing transaction result codes
delete from t_transaction_results where proto_id >= 207;
insert into t_transaction_results (proto_id, result) values
  (197, 'TOKEN_ID_REPEATED_IN_TOKEN_LIST'),
  (198, 'TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED'),
  (199, 'EMPTY_TOKEN_TRANSFER_BODY'),
  (200, 'EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS'),
  (207, 'SCHEDULED_TRANSACTION_NOT_IN_WHITELIST'),
  (208, 'SOME_SIGNATURES_WERE_INVALID'),
  (209, 'TRANSACTION_ID_FIELD_NOT_ALLOWED'),
  (210, 'IDENTICAL_SCHEDULE_ALREADY_CREATED'),
  (211, 'INVALID_ZERO_BYTE_IN_STRING'),
  (212, 'SCHEDULE_ALREADY_DELETED'),
  (213, 'SCHEDULE_ALREADY_EXECUTED'),
  (214, 'MESSAGE_SIZE_TOO_LARGE');

-- Make schedule_signature generic
alter table if exists schedule_signature alter column schedule_id drop not null;
alter table if exists schedule_signature rename column schedule_id to entity_id;
alter table if exists schedule_signature rename to transaction_signature;
alter index if exists schedule_signature__schedule_id rename to transaction_signature__entity_id;
alter index if exists schedule_signature__timestamp_public_key_prefix
    rename to transaction_signature__timestamp_public_key_prefix;
