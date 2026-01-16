-------------------
-- crypto_transfer table
-------------------

create table if not exists crypto_transfer (
    entity_id entity_id not null,
    consensus_timestamp nanos_timestamp not null,
    amount hbar_tinybars not null
);

-- Current assumption in t_cryptotransferlists is that shard = 0. Below migration builds on that assumption.
insert into crypto_transfer
select encodeEntityId(0, realm_num, entity_num), consensus_timestamp, amount
from t_cryptotransferlists;

drop index if exists idx__t_cryptotransferlists__consensus_and_realm_and_num; -- drop indexes explicitly for history
drop index if exists idx__t_cryptotransferlists__realm_and_num_and_consensus;
drop table if exists t_cryptotransferlists;

create index if not exists crypto_transfer__consensus_timestamp
    on crypto_transfer (consensus_timestamp);
create index if not exists crypto_transfer__entity_id_consensus_timestamp
    on crypto_transfer (entity_id, consensus_timestamp)
    where entity_id != 98; -- id corresponding to treasury address 0.0.98

-------------------
-- non_fee_transfer table
-------------------

create table if not exists non_fee_transfer ( -- renamed to remove 's' from previous name
    entity_id entity_id not null,
    consensus_timestamp nanos_timestamp not null,
    amount hbar_tinybars not null
);

-- Current assumption in non_fee_transfers is that shard = 0. Below migration builds on that assumption.
insert into non_fee_transfer
select encodeEntityId(0, realm_num, entity_num), consensus_timestamp, amount
from non_fee_transfers;

drop table if exists non_fee_transfers;
drop index if exists idx__non_fee_transfers__cts; -- drop index explicitly for history

create index non_fee_transfer__consensus_timestamp
    on non_fee_transfer (consensus_timestamp);
