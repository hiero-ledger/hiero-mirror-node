-- Changes to optimize write performance
-- Drop foreign keys in t_transactions and t_entities
-- Drop foreign keys referencing t_transactions(consensus_timestamp) in t_cryptotransferlists, t_file_data,
--   t_livehashes and t_contract_result.
-- Drop trigger in topic_message and associated function
-- Drop unused indexes: idx_t_transactions_rec_file, idx__t_cryptotransferlist_amount

alter table t_transactions
    drop constraint if exists fk_cud_entity_id;
alter table t_transactions
    drop constraint if exists fk_node_account_id;
alter table t_transactions
    drop constraint if exists fk_payer_account_id;
alter table t_transactions
    drop constraint if exists fk_rec_file_id;

alter table t_entities
    drop constraint if exists autorenew_account;
alter table t_entities
    drop constraint if exists fk_ent_type_id;

alter table t_cryptotransferlists
    drop constraint if exists fk__t_transactions;
alter table t_file_data
    drop constraint if exists fk__t_transactions;
alter table t_livehashes
    drop constraint if exists fk__t_transactions;
alter table t_contract_result
    drop constraint if exists fk__t_transactions;

drop trigger if exists topic_message_trigger on topic_message;
drop function if exists topic_message_notifier();

drop index if exists idx_t_transactions_rec_file;
drop index if exists idx__t_cryptotransferlist_amount;
