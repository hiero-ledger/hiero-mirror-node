-- Move to using t_transactions.consensus_ns instead of id.

--
-- t_livehashes
--

-- Migrate t_livehashes to that new PK column (consensus_ns)
alter table t_livehashes
    add column if not exists consensus_timestamp nanos_timestamp null;
drop index if exists idx_livehash_tx_id;
alter table t_livehashes
    drop constraint if exists fk_cd_tx_id;

-- Migrate the data into t_livehashes.
update t_livehashes
    set consensus_timestamp = t_transactions.consensus_ns
    from t_transactions
    where t_transactions.id = fk_trans_id
    and consensus_timestamp is null;
alter table t_livehashes
    alter column consensus_timestamp set not null;

-- Drop the column.
alter table t_livehashes
    drop column if exists fk_trans_id;

create index if not exists idx__t_livehashes__consensus
    on t_livehashes (consensus_timestamp desc);

--
-- t_contract_result
--

-- Migrate t_contract_result to that new PK column (consensus_ns)
alter table t_contract_result
    add column if not exists consensus_timestamp nanos_timestamp null;
drop index if exists idx_contract_result_tx_id;
alter table t_contract_result
    drop constraint if exists fk_cr_tx_id;

-- Migrate the data into t_contract_result.
update t_contract_result
    set consensus_timestamp = t_transactions.consensus_ns
    from t_transactions
    where t_transactions.id = fk_trans_id
    and consensus_timestamp is null;
alter table t_contract_result
    alter column consensus_timestamp set not null;

-- Drop the column.
alter table t_contract_result
    drop column if exists fk_trans_id;

create index if not exists idx__t_contract_result__consensus
    on t_contract_result (consensus_timestamp desc);

--
-- t_file_data
--

-- Migrate t_file_data to that new PK column (consensus_ns)
alter table t_file_data
    add column if not exists consensus_timestamp nanos_timestamp null;
drop index if exists idx_file_data_tx_id;
alter table t_file_data
    drop constraint if exists fk_fd_tx_id;

-- Migrate the data into t_file_data.
update t_file_data
    set consensus_timestamp = t_transactions.consensus_ns
    from t_transactions
    where t_transactions.id = fk_trans_id
    and consensus_timestamp is null;
alter table t_file_data
    alter column consensus_timestamp set not null;

-- Drop the column.
alter table t_file_data
    drop column if exists fk_trans_id;

create index if not exists idx__t_file_data__consensus
    on t_file_data (consensus_timestamp desc);

--
-- t_cryptotransferlists
--

-- Remove duplicate foreign key constraint (there were 2 on the same field)
alter table t_cryptotransferlists
    drop constraint if exists fk_ctl_acc_id;

-- Migrate t_cryptotransferlists to the new PK column (consensus_ns)
alter table t_cryptotransferlists
    add column if not exists consensus_timestamp nanos_timestamp null;
drop index if exists idx_cryptotransferslist_tx_id;
drop index if exists idx_t_cryptotransferlist_tx_id_account;
drop index if exists idx_cryptotransferlist_account; -- too general
alter table t_cryptotransferlists
    drop constraint if exists fk_ctl_tx_id;

-- Migrate the data into t_cryptotransferlists. Long step.
update t_cryptotransferlists
    set consensus_timestamp = t_transactions.consensus_ns
    from t_transactions
    where t_transactions.id = fk_trans_id
    and consensus_timestamp is null;
alter table t_cryptotransferlists
    alter column consensus_timestamp set not null;

-- Drop the column.
alter table t_cryptotransferlists
    drop column if exists fk_trans_id;

create index if not exists idx__t_cryptotransferlists__consensus_and_account
    on t_cryptotransferlists (consensus_timestamp desc, account_id);
create index if not exists idx__t_cryptotransferlists__account_and_consensus
    on t_cryptotransferlists (account_id, consensus_timestamp desc);

--
-- t_transactions
--

drop index if exists idx__t_transactions__consensus_ns__id;
drop index if exists idx_t_transactions_cs_ns;
drop index if exists idx_t_transactions_crud_entity; -- unused

alter table t_transactions
    drop constraint if exists t_transactions_pkey;
alter table t_transactions
    drop column if exists id;
-- Other columns that are deprecated and should be dropped.
alter table t_transactions
    drop column if exists consensus_seconds;
alter table t_transactions
    drop column if exists consensus_nanos;

drop sequence if exists s_transactions_seq;
alter table t_transactions
    add constraint pk__t_transactions__consensus_ns primary key (consensus_ns);

drop index if exists idx_t_transactions_id;

--
-- Add back in the foreign key constraints
--
alter table t_cryptotransferlists
    add constraint fk__t_transactions foreign key (consensus_timestamp) references t_transactions (consensus_ns);
alter table t_livehashes
    add constraint fk__t_transactions foreign key (consensus_timestamp) references t_transactions (consensus_ns);
alter table t_contract_result
    add constraint fk__t_transactions foreign key (consensus_timestamp) references t_transactions (consensus_ns);
alter table t_file_data
    add constraint fk__t_transactions foreign key (consensus_timestamp) references t_transactions (consensus_ns);

--
-- Other redundant indexes
--
drop index if exists idx_t_entities_id; -- Duplicate
drop index if exists idx_t_entities_id_num; -- Covered by idx_t_entities_id_num_id
drop index if exists idx_t_entities_exp_t_ns; -- unused
drop index if exists idx_t_rec_file; -- unused
