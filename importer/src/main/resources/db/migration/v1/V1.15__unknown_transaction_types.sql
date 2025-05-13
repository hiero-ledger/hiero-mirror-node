-- Update t_transactions to replace foreign key id fields with proto_id integer
alter table if exists t_transactions add column result smallint;
alter table if exists t_transactions add column type smallint;

update
  t_transactions t
set
  result = r.proto_id,
  type = tt.proto_id
from
  t_transaction_results r,
  t_transaction_types tt
where
  r.id = t.fk_result_id and
  tt.id = t.fk_trans_type_id;

alter table if exists t_transactions alter column result set not null;
alter table if exists t_transactions alter column type set not null;
alter table if exists t_transactions drop column if exists fk_result_id;
alter table if exists t_transactions drop column if exists fk_trans_type_id;

-- Update t_transaction_results
alter table if exists t_transaction_results drop column if exists id;
alter table if exists t_transaction_results add primary key (proto_id);
alter table if exists t_transaction_results add constraint t_transaction_results_name unique (result);
drop index if exists idx_t_trans_result_unq;
drop sequence if exists s_transaction_results_seq;
delete from t_transaction_results where proto_id < 0;

-- Update t_transaction_types
alter table if exists t_transaction_types drop column if exists id;
alter table if exists t_transaction_types add primary key (proto_id);
alter table if exists t_transaction_types add constraint t_transaction_types_name unique (name);
drop index if exists idx_t_trans_type_unq;
drop sequence if exists s_transaction_types_seq;
delete from t_transaction_types where proto_id < 0;
