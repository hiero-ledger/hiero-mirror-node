---- ${flyway:timestamp} trigger to always run

create or replace procedure create_distributed_table_safe(name text, distribution_column text, colocate_table text) as
$$
begin
  if not exists(select * from information_schema.tables where table_name = name and table_schema = '${tempSchema}') then
    return;
  end if;

  execute format($sep$ select create_distributed_table('%s', '%s', colocate_with => '%s') $sep$,
    '${tempSchema}.' || name, distribution_column, colocate_table);
end;
$$ language plpgsql;

call create_distributed_table_safe('contract_state_temp', 'contract_id', 'contract_state');
call create_distributed_table_safe('crypto_allowance_temp', 'owner', 'crypto_allowance');
call create_distributed_table_safe('custom_fee_temp', 'entity_id', 'custom_fee');
call create_distributed_table_safe('entity_stake_temp', 'id', 'entity_stake');
call create_distributed_table_safe('entity_state_start', 'id', 'entity');
call create_distributed_table_safe('entity_temp', 'id', 'entity');
call create_distributed_table_safe('nft_allowance_temp', 'owner', 'nft_allowance');
call create_distributed_table_safe('nft_temp', 'token_id', 'nft');
call create_distributed_table_safe('schedule_temp', 'schedule_id', 'schedule');
call create_distributed_table_safe('token_account_temp', 'account_id', 'token_account');
call create_distributed_table_safe('token_airdrop_temp', 'receiver_account_id', 'token_airdrop');
call create_distributed_table_safe('token_allowance_temp', 'owner', 'token_allowance');
call create_distributed_table_safe('dissociate_token_transfer', 'token_id', 'nft');
call create_distributed_table_safe('token_temp', 'token_id', 'token');
call create_distributed_table_safe('topic_temp', 'id', 'topic');
call create_distributed_table_safe('topic_message_lookup_temp', 'topic_id', 'topic_message_lookup');
