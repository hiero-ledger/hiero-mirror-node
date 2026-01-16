INSERT INTO t_transaction_types (proto_id, name) values (23,'FREEZE');

UPDATE t_transaction_types set proto_id = 14 WHERE name = 'CRYPTOTRANSFER';
UPDATE t_transaction_types set proto_id = 15 WHERE name = 'CRYPTOUPDATEACCOUNT';
UPDATE t_transaction_types set proto_id = 12 WHERE name = 'CRYPTODELETE';
UPDATE t_transaction_types set proto_id = 10 WHERE name = 'CRYPTOADDCLAIM';
UPDATE t_transaction_types set proto_id = 13 WHERE name = 'CRYPTODELETECLAIM';
UPDATE t_transaction_types set proto_id = 7  WHERE name = 'CONTRACTCALL';
UPDATE t_transaction_types set proto_id = 8  WHERE name = 'CONTRACTCREATEINSTANCE';
UPDATE t_transaction_types set proto_id = 9  WHERE name = 'CONTRACTUPDATEINSTANCE';
UPDATE t_transaction_types set proto_id = 17 WHERE name = 'FILECREATE';
UPDATE t_transaction_types set proto_id = 16 WHERE name = 'FILEAPPEND';
UPDATE t_transaction_types set proto_id = 19 WHERE name = 'FILEUPDATE';
UPDATE t_transaction_types set proto_id = 18 WHERE name = 'FILEDELETE';
UPDATE t_transaction_types set proto_id = 11 WHERE name = 'CRYPTOCREATEACCOUNT';
UPDATE t_transaction_types set proto_id = 20 WHERE name = 'SYSTEMDELETE';
UPDATE t_transaction_types set proto_id = 21 WHERE name = 'SYSTEMUNDELETE';
UPDATE t_transaction_types set proto_id = 22 WHERE name = 'CONTRACTDELETEINSTANCE';
