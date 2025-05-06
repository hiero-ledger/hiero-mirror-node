-- Remove Events
drop table if exists t_events;
drop sequence if exists s_events_id_seq;

DELETE FROM t_application_status WHERE status_code = 'LAST_VALID_DOWNLOADED_EVENT_FILE';
DELETE FROM t_application_status WHERE status_code = 'LAST_VALID_DOWNLOADED_EVENT_FILE_HASH';
DELETE FROM t_application_status WHERE status_code = 'EVENT_HASH_MISMATCH_BYPASS_UNTIL_AFTER';
DELETE FROM t_application_status WHERE status_code = 'LAST_PROCESSED_EVENT_HASH';

-- Remove unused components
drop table if exists entity_types;
drop view if exists v_entities;
drop sequence if exists s_account_balances_seq;
