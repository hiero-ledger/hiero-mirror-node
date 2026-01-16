alter table if exists nft add column if not exists timestamp_range int8range;

update nft set timestamp_range = int8range(modified_timestamp, null);

alter table if exists nft
  alter column metadata drop default,
  alter column timestamp_range set not null,
  drop column modified_timestamp;

create table if not exists nft_history (like nft including defaults);

-- add nft history row based on nft_transfer
with full_history as (
  select
    receiver_account_id as account_id,
    serial_number,
    token_id,
    int8range(
      consensus_timestamp,
      (select i.consensus_timestamp
       from nft_transfer i
       where i.token_id = o.token_id and i.serial_number = o.serial_number
        and i.consensus_timestamp > o.consensus_timestamp
       order by i.consensus_timestamp
       limit 1)) as timestamp_range
  from nft_transfer o
)
insert into nft_history (account_id, created_timestamp, deleted, metadata, serial_number, token_id, timestamp_range)
select h.account_id, n.created_timestamp, false, n.metadata, h.serial_number, h.token_id, h.timestamp_range
from full_history h
join nft n on n.token_id = h.token_id and n.serial_number = h.serial_number
where upper(h.timestamp_range) is not null;

create index if not exists nft_history__token_serial_lower_timestamp
  on nft_history (token_id, serial_number, lower(timestamp_range));
create index if not exists nft_history__timestamp_range on nft_history using gist (timestamp_range);

drop table nft_transfer;
