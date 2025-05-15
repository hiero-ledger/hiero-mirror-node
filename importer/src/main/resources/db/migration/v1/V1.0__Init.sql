
CREATE USER ${api-user} WITH
	LOGIN
	NOCREATEDB
	NOCREATEROLE
	NOINHERIT
	NOREPLICATION
	CONNECTION LIMIT -1
	PASSWORD '${api-password}';

CREATE SEQUENCE s_transaction_types_seq;
CREATE SEQUENCE s_record_files_seq;
CREATE SEQUENCE s_transactions_seq;
CREATE SEQUENCE s_entities_seq;
CREATE SEQUENCE s_transaction_results_seq;
CREATE SEQUENCE s_account_balances_seq;
CREATE SEQUENCE s_events_id_seq;

CREATE TABLE t_transaction_types (
	id			  INTEGER PRIMARY KEY NOT NULL DEFAULT nextval('s_transaction_types_seq')
	,proto_id INTEGER
	,name  VARCHAR(30)
);

INSERT INTO t_transaction_types (proto_id, name) values (-1,'UNKNOWN');
INSERT INTO t_transaction_types (proto_id, name) values (1,'CRYPTOTRANSFER');
INSERT INTO t_transaction_types (proto_id, name) values (2,'CRYPTOUPDATEACCOUNT');
INSERT INTO t_transaction_types (proto_id, name) values (3,'CRYPTODELETE');
INSERT INTO t_transaction_types (proto_id, name) values (4,'CRYPTOADDCLAIM');
INSERT INTO t_transaction_types (proto_id, name) values (5,'CRYPTODELETECLAIM');
INSERT INTO t_transaction_types (proto_id, name) values (6,'CONTRACTCALL');
INSERT INTO t_transaction_types (proto_id, name) values (7,'CONTRACTCREATEINSTANCE');
INSERT INTO t_transaction_types (proto_id, name) values (8,'CONTRACTUPDATEINSTANCE');
INSERT INTO t_transaction_types (proto_id, name) values (9,'FILECREATE');
INSERT INTO t_transaction_types (proto_id, name) values (10,'FILEAPPEND');
INSERT INTO t_transaction_types (proto_id, name) values (11,'FILEUPDATE');
INSERT INTO t_transaction_types (proto_id, name) values (12,'FILEDELETE');
INSERT INTO t_transaction_types (proto_id, name) values (27,'CRYPTOCREATEACCOUNT');
INSERT INTO t_transaction_types (proto_id, name) values (28,'SYSTEMDELETE');
INSERT INTO t_transaction_types (proto_id, name) values (29,'SYSTEMUNDELETE');
INSERT INTO t_transaction_types (proto_id, name) values (30,'CONTRACTDELETEINSTANCE');

CREATE TABLE t_transaction_results (
	id                   INTEGER PRIMARY KEY NOT NULL DEFAULT nextval('s_transaction_results_seq')
	,proto_id            INTEGER
	,result              VARCHAR(100)
);

INSERT INTO t_transaction_results (result, proto_id) VALUES ('not_known_to_db', -1);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('OK', 0);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INVALID_TRANSACTION',1);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('PAYER_ACCOUNT_NOT_FOUND',2);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INVALID_NODE_ACCOUNT',3);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('TRANSACTION_EXPIRED',4);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INVALID_TRANSACTION_START',5);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INVALID_TRANSACTION_DURATION',6);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INVALID_SIGNATURE',7);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('MEMO_TOO_LONG',8);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INSUFFICIENT_TX_FEE',9);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INSUFFICIENT_PAYER_BALANCE',10);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('DUPLICATE_TRANSACTION',11);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('BUSY',12);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('NOT_SUPPORTED',13);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INVALID_FILE_ID',14);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INVALID_ACCOUNT_ID',15);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INVALID_CONTRACT_ID',16);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INVALID_TRANSACTION_ID',17);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('RECEIPT_NOT_FOUND',18);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('RECORD_NOT_FOUND',19);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INVALID_SOLIDITY_ID',20);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('UNKNOWN',21);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('SUCCESS',22);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('FAIL_INVALID',23);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('FAIL_FEE',24);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('FAIL_BALANCE',25);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('KEY_REQUIRED',26);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('BAD_ENCODING',27);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INSUFFICIENT_ACCOUNT_BALANCE',28);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INVALID_SOLIDITY_ADDRESS',29);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INSUFFICIENT_GAS',30);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('CONTRACT_SIZE_LIMIT_EXCEEDED',31);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('LOCAL_CALL_MODIFICATION_EXCEPTION',32);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('CONTRACT_REVERT_EXECUTED',33);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('CONTRACT_EXECUTION_EXCEPTION',34);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INVALID_RECEIVING_NODE_ACCOUNT',35);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('MISSING_QUERY_HEADER',36);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('ACCOUNT_UPDATE_FAILED',37);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INVALID_KEY_ENCODING',38);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('NULL_SOLIDITY_ADDRESS',39);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('CONTRACT_UPDATE_FAILED',40);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INVALID_QUERY_HEADER',41);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INVALID_FEE_SUBMITTED',42);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INVALID_PAYER_SIGNATURE',43);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('KEY_NOT_PROVIDED',44);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INVALID_EXPIRATION_TIME',45);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('NO_WACL_KEY',46);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('FILE_CONTENT_EMPTY',47);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INVALID_ACCOUNT_AMOUNTS',48);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('EMPTY_TRANSACTION_BODY',49);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INVALID_TRANSACTION_BODY',50);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INVALID_SIGNATURE_TYPE_MISMATCHING_KEY',51);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INVALID_SIGNATURE_COUNT_MISMATCHING_KEY',52);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('EMPTY_CLAIM_BODY',53);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('EMPTY_CLAIM_HASH',54);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('EMPTY_CLAIM_KEYS',55);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INVALID_CLAIM_HASH_SIZE',56);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('EMPTY_QUERY_BODY',57);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('EMPTY_CLAIM_QUERY',58);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('CLAIM_NOT_FOUND',59);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('ACCOUNT_ID_DOES_NOT_EXIST',60);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('CLAIM_ALREADY_EXISTS',61);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INVALID_FILE_WACL',62);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('SERIALIZATION_FAILED',63);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('TRANSACTION_OVERSIZE',64);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('TRANSACTION_TOO_MANY_LAYERS',65);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('CONTRACT_DELETED',66);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('PLATFORM_NOT_ACTIVE',67);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('KEY_PREFIX_MISMATCH',68);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('PLATFORM_TRANSACTION_NOT_CREATED',69);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INVALID_RENEWAL_PERIOD',70);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INVALID_PAYER_ACCOUNT_ID',71);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('ACCOUNT_DELETED',72);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('FILE_DELETED',73);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS',74);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('SETTING_NEGATIVE_ACCOUNT_BALANCE',75);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('OBTAINER_REQUIRED',76);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('OBTAINER_SAME_CONTRACT_ID',77);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('OBTAINER_DOES_NOT_EXIST',78);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('MODIFYING_IMMUTABLE_CONTRACT',79);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('FILE_SYSTEM_EXCEPTION',80);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('AUTORENEW_DURATION_NOT_IN_RANGE',81);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('ERROR_DECODING_BYTESTRING',82);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('CONTRACT_FILE_EMPTY',83);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('CONTRACT_BYTECODE_EMPTY',84);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INVALID_INITIAL_BALANCE',85);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INVALID_RECEIVE_RECORD_THRESHOLD',86);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('INVALID_SEND_RECORD_THRESHOLD',87);
INSERT INTO t_transaction_results (result, proto_id) VALUES ('ACCOUNT_IS_NOT_GENESIS_ACCOUNT',88);

CREATE TABLE t_record_files (
  id                   BIGINT PRIMARY KEY NOT NULL DEFAULT nextval('s_record_files_seq')
  ,name                VARCHAR(250) NOT NULL
	,load_start          BIGINT
	,load_end            BIGINT
	,file_hash           VARCHAR(96)
	,prev_hash           VARCHAR(96)
);

CREATE TABLE t_entity_types (
	id                  INT PRIMARY KEY NOT NULL
	,name               VARCHAR(8)
);

INSERT INTO t_entity_types (id, name) VALUES (1, 'account');
INSERT INTO t_entity_types (id, name) VALUES (2, 'contract');
INSERT INTO t_entity_types (id, name) VALUES (3, 'file');

CREATE TABLE t_entities (
  id                  BIGINT PRIMARY KEY NOT NULL DEFAULT nextval('s_entities_seq')
  ,entity_num         BIGINT NOT NULL
	,entity_realm 		  BIGINT NOT NULL
	,entity_shard       BIGINT NOT NULL
  ,fk_entity_type_id  INT NOT NULL
	,exp_time_seconds	  BIGINT
	,exp_time_nanos	    BIGINT
	,auto_renew_period  BIGINT
	,admin_key          BYTEA
	,key                BYTEA
	,fk_prox_acc_id     BIGINT
	,deleted            BOOLEAN DEFAULT false
	,balance            BIGINT
);

CREATE TABLE t_account_balance_refresh_time (
  seconds             BIGINT NOT NULL
  ,nanos              BIGINT NOT NULL
);

INSERT INTO t_account_balance_refresh_time
VALUES (0,0);

CREATE TABLE t_account_balances (
	id			             BIGINT PRIMARY KEY NOT NULL DEFAULT nextval('s_account_balances_seq')
	,shard               BIGINT NOT NULL
	,realm               BIGINT NOT NULL
	,num                 BIGINT NOT NULL
  ,balance             BIGINT NOT NULL
);

CREATE TABLE t_account_balance_history (
  snapshot_time        TIMESTAMP NOT NULL
	,seconds             BIGINT NOT NULL
  ,nanos               BIGINT NOT NULL
  ,balance             BIGINT NOT NULL
  ,fk_balance_id       BIGINT NOT NULL
);

CREATE TABLE t_transactions (
  id                   BIGINT PRIMARY KEY NOT NULL DEFAULT nextval('s_transactions_seq')
  ,fk_node_acc_id      BIGINT NOT NULL
  ,memo		             BYTEA
  ,vs_seconds          BIGINT NOT NULL
  ,vs_nanos            INT
  ,fk_trans_type_id    INT
  ,fk_result_id        INT
  ,consensus_seconds   BIGINT NULL
  ,consensus_nanos     BIGINT NULL
  ,fk_payer_acc_id     BIGINT NOT NULL
  ,charged_tx_fee      BIGINT
  ,initial_balance     BIGINT DEFAULT 0
  ,fk_cud_entity_id    BIGINT
	,fk_rec_file_id      BIGINT NOT NULL
);

CREATE TABLE t_cryptotransferlists (
  fk_trans_id          BIGINT NOT NULL
  ,account_id          BIGINT NOT NULL
  ,amount              BIGINT NOT NULL
);

CREATE TABLE t_file_data (
	fk_trans_id          BIGINT NOT NULL
	,file_data           BYTEA
);

CREATE TABLE t_contract_result (
	fk_trans_id          BIGINT NOT NULL
  ,function_params     BYTEA
	,gas_supplied				 BIGINT
	,call_result         BYTEA
	,gas_used            BIGINT
);

CREATE TABLE t_livehashes (
	fk_trans_id          BIGINT NOT NULL
	,livehash          BYTEA
);

CREATE TABLE t_events
(
    id                       BIGINT  NOT NULL DEFAULT nextval('s_events_id_seq')
    ,consensus_order         BIGINT  NOT NULL
    ,creator_node_id         BIGINT  NOT NULL
    ,creator_seq             BIGINT  NOT NULL
    ,other_node_id           BIGINT
    ,other_seq               BIGINT
    ,signature               BYTEA   NOT NULL
    ,hash                    BYTEA   NOT NULL
    ,self_parent_id          BIGINT
    ,other_parent_id         BIGINT
    ,self_parent_hash        BYTEA
    ,other_parent_hash       BYTEA
    ,self_parent_generation  BIGINT
    ,other_parent_generation BIGINT
    ,generation              BIGINT  NOT NULL
    ,created_timestamp_ns    BIGINT  NOT NULL
    ,consensus_timestamp_ns  BIGINT  NOT NULL
    ,latency_ns              BIGINT  NOT NULL
    ,txs_bytes_count         INTEGER NOT NULL
    ,platform_tx_count       INTEGER NOT NULL
    ,app_tx_count            INTEGER NOT NULL
);

-- CONSTRAINTS
-- t_entities
ALTER TABLE t_entities ADD CONSTRAINT fk_ent_type_id FOREIGN KEY (fk_entity_type_id) REFERENCES t_entity_types (id) ON DELETE CASCADE ON UPDATE CASCADE;

--t_account_balance_history
ALTER TABLE t_account_balance_history ADD CONSTRAINT fk_acc_bal_id FOREIGN KEY (fk_balance_id) REFERENCES t_account_balances (id) ON DELETE CASCADE ON UPDATE CASCADE;

-- t_transactions
ALTER TABLE t_transactions ADD CONSTRAINT fk_trans_type_id FOREIGN KEY (fk_trans_type_id) REFERENCES t_transaction_types (id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE t_transactions ADD CONSTRAINT fk_node_account_id FOREIGN KEY (fk_node_acc_id) REFERENCES t_entities (id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE t_transactions ADD CONSTRAINT fk_payer_account_id FOREIGN KEY (fk_payer_acc_id) REFERENCES t_entities (id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE t_transactions ADD CONSTRAINT fk_cud_entity_id FOREIGN KEY (fk_cud_entity_id) REFERENCES t_entities (id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE t_transactions ADD CONSTRAINT fk_result_id FOREIGN KEY (fk_result_id) REFERENCES t_transaction_results (id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE t_transactions ADD CONSTRAINT fk_rec_file_id FOREIGN KEY (fk_rec_file_id) REFERENCES t_record_files (id) ON DELETE CASCADE ON UPDATE CASCADE;

-- t_cryptotransferlists
ALTER TABLE t_cryptotransferlists ADD CONSTRAINT fk_ctl_tx_id FOREIGN KEY (fk_trans_id) REFERENCES t_transactions (id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE t_cryptotransferlists ADD CONSTRAINT fk_ctl_account_id FOREIGN KEY (account_id) REFERENCES t_entities (id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE t_cryptotransferlists ADD CONSTRAINT fk_ctl_acc_id FOREIGN KEY (account_id) REFERENCES t_entities (id) ON DELETE CASCADE ON UPDATE CASCADE;

-- t_file_data
ALTER TABLE t_file_data ADD CONSTRAINT fk_fd_tx_id FOREIGN KEY (fk_trans_id) REFERENCES t_transactions (id) ON DELETE CASCADE ON UPDATE CASCADE;

-- t_contract_result
ALTER TABLE t_contract_result ADD CONSTRAINT fk_cr_tx_id FOREIGN KEY (fk_trans_id) REFERENCES t_transactions (id) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE t_livehashes ADD CONSTRAINT fk_cd_tx_id FOREIGN KEY (fk_trans_id) REFERENCES t_transactions (id) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE t_events ADD CONSTRAINT pk_events_id PRIMARY KEY (id);
ALTER TABLE t_events ADD CONSTRAINT fk_events_self_parent_id FOREIGN KEY (self_parent_id) REFERENCES t_events (id);
ALTER TABLE t_events ADD CONSTRAINT fk_events_other_parent_id FOREIGN KEY (other_parent_id) REFERENCES t_events (id);

-- INDICES
-- t_transactions
CREATE INDEX idx_t_transactions_id ON t_transactions (id);
CREATE INDEX idx_t_transactions_seconds ON t_transactions (vs_seconds);
CREATE UNIQUE INDEX idx_t_transactions_transaction_id_unq ON t_transactions (vs_seconds, vs_nanos, fk_payer_acc_id);
CREATE INDEX idx_t_transactions_payer_id ON t_transactions (fk_payer_acc_id);
CREATE INDEX idx_t_transactions_node_account ON t_transactions (fk_node_acc_id);
CREATE INDEX idx_t_transactions_crud_entity ON t_transactions (fk_cud_entity_id);
CREATE INDEX idx_t_transactions_rec_file ON t_transactions (fk_rec_file_id);

-- t_cryptotransferlists
CREATE INDEX idx_cryptotransferslist_tx_id ON t_cryptotransferlists (fk_trans_id);
CREATE INDEX idx_cryptotransferlist_account ON t_cryptotransferlists (account_id);
CREATE INDEX idx_t_cryptotransferlist_amount ON t_cryptotransferlists (amount);
CREATE INDEX idx_t_cryptotransferlist_tx_id_account ON t_cryptotransferlists (fk_trans_id, account_id);

-- t_record_files
CREATE UNIQUE INDEX idx_t_record_files_name ON t_record_files (name);
CREATE UNIQUE INDEX idx_file_data_hash_unq ON t_record_files (file_hash);
CREATE INDEX idx_file_data_prev_hash_unq ON t_record_files (prev_hash);

--t_account_balance_history
CREATE UNIQUE INDEX t_acc_bal_hist_unique ON t_account_balance_history (seconds, fk_balance_id);
CREATE UNIQUE INDEX t_acc_bal_hist_unique2 ON t_account_balance_history (snapshot_time, seconds, fk_balance_id);
CREATE INDEX t_acc_bal_hist_sec ON t_account_balance_history (seconds);

--t_account_balances
CREATE UNIQUE INDEX idx_t_account_bal_unq ON t_account_balances (shard, realm, num);
CREATE INDEX idx_t_account_bal_id_num ON t_account_balances (id, num);
CREATE UNIQUE INDEX idx_t_account_bal_unq2 ON t_account_balances (id, shard, realm, num);

--t_entities
CREATE UNIQUE INDEX idx_t_entities_unq ON t_entities (entity_shard, entity_realm, entity_num, fk_entity_type_id);
CREATE INDEX idx_t_entities_id_num ON t_entities (id, entity_num);
CREATE INDEX idx_t_entities_id_num_id ON t_entities (id, entity_num, fk_entity_type_id);
CREATE INDEX idx_t_entities_id ON t_entities (id);

--t_transaction_types
CREATE INDEX idx_t_trans_type_id ON t_transaction_types (id);
CREATE UNIQUE INDEX idx_t_trans_type_unq ON t_transaction_types (proto_id, name);

-- t_file_data
CREATE INDEX idx_file_data_tx_id ON t_file_data (fk_trans_id);

-- t_contract_result
CREATE INDEX idx_contract_result_tx_id ON t_contract_result (fk_trans_id);

CREATE INDEX idx_livehash_tx_id ON t_livehashes (fk_trans_id);

--t_transaction_results
CREATE INDEX idx_t_trans_result_id ON t_transaction_results (id);
CREATE UNIQUE INDEX idx_t_trans_result_unq ON t_transaction_results (proto_id, result);

CREATE OR REPLACE VIEW v_entities AS
SELECT e.id, e.entity_num, et.name AS type
FROM t_entities e
, t_entity_types et
WHERE e.fk_entity_type_id = et.id;

GRANT USAGE ON SCHEMA public TO ${db-user};
GRANT CONNECT ON DATABASE ${db-name} TO ${db-user};
GRANT ALL PRIVILEGES ON DATABASE ${db-name} TO ${db-user};
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO ${db-user};
GRANT ALL ON t_record_files TO ${db-user};
GRANT ALL ON t_transactions TO ${db-user};
GRANT ALL ON t_cryptotransferlists TO ${db-user};
GRANT ALL ON t_transaction_types TO ${db-user};
GRANT ALL ON t_entities TO ${db-user};
GRANT ALL ON t_account_balance_history TO ${db-user};
GRANT ALL ON t_account_balances TO ${db-user};
GRANT ALL ON t_account_balance_refresh_time to ${db-user};
GRANT ALL ON t_file_data TO ${db-user};
GRANT ALL ON t_contract_result TO ${db-user};
GRANT ALL ON t_livehashes TO ${db-user};
GRANT ALL ON t_transaction_results TO ${db-user};
GRANT ALL ON t_entity_types TO ${db-user};
GRANT ALL ON t_events TO ${db-user};

GRANT ALL ON s_transaction_types_seq TO ${db-user};
GRANT ALL ON s_record_files_seq TO ${db-user};
GRANT ALL ON s_transactions_seq TO ${db-user};
GRANT ALL ON s_entities_seq TO ${db-user};
GRANT ALL ON s_transaction_results_seq TO ${db-user};
GRANT ALL ON s_account_balances_seq TO ${db-user};
GRANT ALL ON s_events_id_seq TO ${db-user};

GRANT ALL ON v_entities to ${db-user};

GRANT SELECT ON t_record_files TO ${api-user};
GRANT SELECT ON t_transactions TO ${api-user};
GRANT SELECT ON t_cryptotransferlists TO ${api-user};
GRANT SELECT ON t_transaction_types TO ${api-user};
GRANT SELECT ON t_entities TO ${api-user};
GRANT SELECT ON t_account_balance_refresh_time to ${api-user};
GRANT SELECT ON t_account_balance_history TO ${api-user};
GRANT SELECT ON t_account_balances TO ${api-user};
GRANT SELECT ON t_file_data TO ${api-user};
GRANT SELECT ON t_contract_result TO ${api-user};
GRANT SELECT ON t_livehashes TO ${api-user};
GRANT SELECT ON t_transaction_results TO ${api-user};
GRANT SELECT ON t_entity_types TO ${api-user};
GRANT SELECT ON t_events TO ${api-user};

GRANT SELECT ON v_entities TO ${api-user};
