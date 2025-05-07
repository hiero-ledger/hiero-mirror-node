-- Add index for state retrieval by
create index if not exists contract_state_change__id_slot_timestamp
    on contract_state_change (contract_id, slot, consensus_timestamp);
