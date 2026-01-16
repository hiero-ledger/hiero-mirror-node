alter table if exists transaction
    add column if not exists nft_transfer jsonb null;

create temp table nested_nft_transfer (consensus_timestamp bigint not null, transfer jsonb not null) on commit drop;

with special_nft_transfer as (
  select nt.consensus_timestamp, jsonb_build_object(
    'is_approval', false,
    'receiver_account_id', receiver_account_id,
    'sender_account_id', sender_account_id,
    'serial_number', case when t.type = 41 then -count(*) else -1 end,
    'token_id', token_id
    ) as transfer
  from nft_transfer nt
  join transaction t on t.consensus_timestamp = nt.consensus_timestamp
  where t.type in (36, 41) -- type 36 - TOKENUPDATE, type 41 - TOKENDISSOCIATE
  group by nt.consensus_timestamp, nt.sender_account_id, nt.receiver_account_id, nt.token_id, t.type
), aggregated_special_nft_transfer as (
  select consensus_timestamp, jsonb_agg(transfer) as transfer
  from special_nft_transfer
  group by consensus_timestamp
), nested_normal_nft_transfer as (
  select nt.consensus_timestamp, jsonb_agg(jsonb_build_object(
    'is_approval', is_approval,
    'receiver_account_id', receiver_account_id,
    'sender_account_id', sender_account_id,
    'serial_number', serial_number,
    'token_id', token_id
    )) as transfer
  from nft_transfer nt
  left join aggregated_special_nft_transfer at on at.consensus_timestamp = nt.consensus_timestamp
  where at.consensus_timestamp is null
  group by nt.consensus_timestamp
)
insert into nested_nft_transfer (consensus_timestamp, transfer)
select consensus_timestamp, transfer
from aggregated_special_nft_transfer
union all
select consensus_timestamp, transfer
from nested_normal_nft_transfer
order by consensus_timestamp;

create unique index on nested_nft_transfer (consensus_timestamp);

update transaction
set nft_transfer = transfer
from nested_nft_transfer
where nested_nft_transfer.consensus_timestamp = transaction.consensus_timestamp;
