-- update transaction_types
UPDATE t_transaction_types set name = 'CRYPTOADDLIVEHASH' WHERE proto_id = 10;
UPDATE t_transaction_types set name = 'CRYPTODELETELIVEHASH' WHERE proto_id = 13;

-- update transaction_results
UPDATE t_transaction_results set result = 'EMPTY_LIVE_HASH_BODY' WHERE proto_id = 53;
UPDATE t_transaction_results set result = 'EMPTY_LIVE_HASH' WHERE proto_id = 54;
UPDATE t_transaction_results set result = 'EMPTY_LIVE_HASH_KEYS' WHERE proto_id = 55;
UPDATE t_transaction_results set result = 'INVALID_LIVE_HASH_SIZE' WHERE proto_id = 56;
UPDATE t_transaction_results set result = 'EMPTY_LIVE_HASH_QUERY' WHERE proto_id = 58;
UPDATE t_transaction_results set result = 'LIVE_HASH_NOT_FOUND' WHERE proto_id = 59;
UPDATE t_transaction_results set result = 'LIVE_HASH_ALREADY_EXISTS' WHERE proto_id = 61;
