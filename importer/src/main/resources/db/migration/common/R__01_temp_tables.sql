---- ${flyway:timestamp} trigger to always run

create or replace procedure create_temp_table_safe(name text, variadic index_columns text[]) as
$$
declare
  temp_name text;
begin
  if not exists(select * from information_schema.tables where table_name = name and table_schema = '${schema}') then
    return;
  end if;

  temp_name := name || '_temp';
  execute format('drop table if exists ${tempSchema}.%I', temp_name);
  execute format('create unlogged table if not exists ${tempSchema}.%I as table %I limit 0', temp_name, name);
  execute format('alter table if exists ${tempSchema}.%I owner to temporary_admin, set (autovacuum_enabled = false)', temp_name);

  if array_length(index_columns, 1) > 0 then
    execute format('create index if not exists %I_idx on ${tempSchema}.%I (%s)', temp_name, temp_name, array_to_string(index_columns, ','));
  end if;
end
$$ language plpgsql;

call create_temp_table_safe('contract_state', 'contract_id', 'slot');
call create_temp_table_safe('crypto_allowance', 'owner', 'spender');
call create_temp_table_safe('custom_fee', 'entity_id');
call create_temp_table_safe('entity', 'id');
call create_temp_table_safe('entity_stake', variadic array[]::text[]);
call create_temp_table_safe('nft_allowance', 'owner', 'spender', 'token_id');
call create_temp_table_safe('nft', 'token_id', 'serial_number');
call create_temp_table_safe('node', 'node_id');
call create_temp_table_safe('schedule', 'schedule_id');
call create_temp_table_safe('token_account', 'account_id', 'token_id');
call create_temp_table_safe('token_airdrop', 'receiver_account_id', 'sender_account_id', 'serial_number', 'token_id');
call create_temp_table_safe('token_allowance', 'owner', 'spender', 'token_id');
call create_temp_table_safe('token', 'token_id');
call create_temp_table_safe('topic', 'id');
call create_temp_table_safe('topic_message_lookup', 'topic_id', 'partition');

drop table if exists ${tempSchema}.dissociate_token_transfer;
call create_temp_table_safe('token_transfer', 'token_id');
alter table if exists ${tempSchema}.token_transfer_temp rename to dissociate_token_transfer;

drop table if exists ${tempSchema}.entity_state_start;
create unlogged table if not exists ${tempSchema}.entity_state_start (
  balance            bigint not null,
  id                 bigint not null,
  staked_account_id  bigint not null,
  staked_node_id     bigint not null,
  stake_period_start bigint not null
);
alter table if exists ${tempSchema}.entity_state_start
  owner to temporary_admin,
  set (autovacuum_enabled = false);
