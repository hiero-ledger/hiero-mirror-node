-------------------
-- Support proto updates in 0.13.0-rc.1 with respect to address book end points
-------------------

-- normalize address_book_entry
-- if either node_id or node_account_id are unset, set them based on an applicable account id formatted memo value
-- only accountIds below 20 can be assumed to be offset with nodeId by 3
update address_book_entry
set node_account_id = split_part(memo, '.', 3)::int,
    node_id         = case
                          when split_part(memo, '.', 3)::int - 3 < 20 then split_part(memo, '.', 3)::int - 3
                          else node_id end
where node_account_id is null
  and memo ~ '^(\d{1,10}\.){2}\d{1,10}$';

-- address_book_service_endpoint
create table if not exists address_book_service_endpoint
(
    consensus_timestamp bigint      not null,
    ip_address_v4       varchar(15) not null,
    node_id             bigint      not null,
    port                integer     default -1 not null
);
comment on table address_book_service_endpoint is 'Network address book node service endpoints';

-- add address_book_service_endpoint index
alter table address_book_service_endpoint
    add primary key (consensus_timestamp, node_id, ip_address_v4, port);

-- migrate endpoints from address_book_entry to address_book_service_endpoint
insert into address_book_service_endpoint (consensus_timestamp, ip_address_v4, port, node_id)
select consensus_timestamp,
       ip,
       case when port is null then -1 else port end,
       node_id
from address_book_entry
where node_id is not null
  and ip is not null
  and ip != ''
order by consensus_timestamp asc;

-- collapse duplicate node_id rows in address_book_entry
-- join table on self and remove duplicates with a lower id leaving a single instance of a memo-consensus_timestamp combo
delete
from address_book_entry a
    using address_book_entry b
where a.id < b.id
  and a.memo = b.memo
  and a.consensus_timestamp = b.consensus_timestamp;

-- Update address_book_entry
-- Add description, stake columns and primary key of consensus_timestamp and node_id
-- Update node_account_id and node_id to not be null
-- Drop id, ip and port columns
alter table if exists address_book_entry
    add column if not exists description varchar(100) null,
    add column if not exists stake       bigint       null,
    alter column node_account_id set not null,
    alter column node_id set not null,
    drop column if exists id,
    drop column if exists ip,
    drop column if exists port,
    add primary key (consensus_timestamp, node_id);

-- update address_book node_count
with entry_map as (
    select consensus_timestamp, count(consensus_timestamp) from address_book_entry group by consensus_timestamp
)
update address_book
set node_count = count
from entry_map
where start_consensus_timestamp = entry_map.consensus_timestamp;
