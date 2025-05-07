
CREATE TABLE t_application_status (
  status_name VARCHAR(40)
  ,status_code VARCHAR(40)
  ,status_value VARCHAR(100)
);

INSERT INTO t_application_status (status_name, status_code) VALUES ('Last valid downloaded record file name', 'LAST_VALID_DOWNLOADED_RECORD_FILE');
INSERT INTO t_application_status (status_name, status_code) VALUES ('Last valid downloaded record file hash', 'LAST_VALID_DOWNLOADED_RECORD_FILE_HASH');

INSERT INTO t_application_status (status_name, status_code) VALUES ('Last valid downloaded balance file name', 'LAST_VALID_DOWNLOADED_BALANCE_FILE');

INSERT INTO t_application_status (status_name, status_code) VALUES ('Last valid downloaded event file name', 'LAST_VALID_DOWNLOADED_EVENT_FILE');
INSERT INTO t_application_status (status_name, status_code) VALUES ('Last valid downloaded event file hash', 'LAST_VALID_DOWNLOADED_EVENT_FILE_HASH');

INSERT INTO t_application_status (status_name, status_code) VALUES ('Event hash mismatch bypass until after', 'EVENT_HASH_MISMATCH_BYPASS_UNTIL_AFTER');
INSERT INTO t_application_status (status_name, status_code) VALUES ('Record hash mismatch bypass until after', 'RECORD_HASH_MISMATCH_BYPASS_UNTIL_AFTER');

INSERT INTO t_application_status (status_name, status_code) VALUES ('Last processed record hash', 'LAST_PROCESSED_RECORD_HASH');
INSERT INTO t_application_status (status_name, status_code) VALUES ('Last processed event hash', 'LAST_PROCESSED_EVENT_HASH');
