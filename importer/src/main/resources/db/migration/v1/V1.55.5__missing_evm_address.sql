-- fill the missing evm address due to the value not merged into the partial contract domain object in importer

with history as (
    select distinct id, evm_address
    from contract_history
    where evm_address is not null
), current_updated as (
    update contract c
        set evm_address = h.evm_address
    from history h
    where c.id = h.id and c.evm_address is null
    returning c.id, c.evm_address
)
update contract_history ch
set evm_address = cu.evm_address
from current_updated cu
where ch.id = cu.id and ch.evm_address is null;
