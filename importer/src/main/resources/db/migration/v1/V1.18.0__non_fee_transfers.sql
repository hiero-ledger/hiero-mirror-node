-- No primary key.
-- Table is insert-only (not update), and there's no natural primary key so this is faster on insert and smaller
-- footprint.
create table non_fee_transfers
(
    consensus_timestamp nanos_timestamp  not null,
    realm_num           entity_realm_num not null,
    entity_num          entity_num       not null,
    amount              hbar_tinybars    not null
);
comment on table non_fee_transfers is
    'non-fee-related explicitly requested transfers for CryptoTransfer, CryptoCreate, ContractCreate, and ContractCall';

create index if not exists idx__non_fee_transfers__cts on non_fee_transfers (consensus_timestamp);
