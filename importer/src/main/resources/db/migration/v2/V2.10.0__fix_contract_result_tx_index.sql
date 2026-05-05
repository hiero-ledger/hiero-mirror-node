-- Fix contract_result and contract_log transaction_index to reflect only top-level EVM transactions

with top_level_evm as (
    select
        t.consensus_timestamp,
        row_number() over (partition by rf.index order by t.index) - 1 as evm_index
    from transaction t
    join record_file rf on t.consensus_timestamp >= rf.consensus_start and t.consensus_timestamp <= rf.consensus_end
    where t.parent_consensus_timestamp is null and t.type in (7, 8, 50)
),
tx_with_evm_index as (
    select
        t.consensus_timestamp,
        coalesce(tl.evm_index, p1.evm_index, p2.evm_index) as evm_index
    from transaction t
    left join top_level_evm tl on t.consensus_timestamp = tl.consensus_timestamp
    left join top_level_evm p1 on t.parent_consensus_timestamp = p1.consensus_timestamp
    left join transaction t2 on t.parent_consensus_timestamp = t2.consensus_timestamp
    left join top_level_evm p2 on t2.parent_consensus_timestamp = p2.consensus_timestamp
)
update contract_result cr
set transaction_index = idx.evm_index
from tx_with_evm_index idx
where cr.consensus_timestamp = idx.consensus_timestamp
and cr.transaction_index is distinct from idx.evm_index;

with top_level_evm as (
    select
        t.consensus_timestamp,
        row_number() over (partition by rf.index order by t.index) - 1 as evm_index
    from transaction t
    join record_file rf on t.consensus_timestamp >= rf.consensus_start and t.consensus_timestamp <= rf.consensus_end
    where t.parent_consensus_timestamp is null and t.type in (7, 8, 50)
),
tx_with_evm_index as (
    select
        t.consensus_timestamp,
        coalesce(tl.evm_index, p1.evm_index, p2.evm_index) as evm_index
    from transaction t
    left join top_level_evm tl on t.consensus_timestamp = tl.consensus_timestamp
    left join top_level_evm p1 on t.parent_consensus_timestamp = p1.consensus_timestamp
    left join transaction t2 on t.parent_consensus_timestamp = t2.consensus_timestamp
    left join top_level_evm p2 on t2.parent_consensus_timestamp = p2.consensus_timestamp
)
update contract_log cl
set transaction_index = coalesce(idx.evm_index, 0)
from tx_with_evm_index idx
where cl.consensus_timestamp = idx.consensus_timestamp
and cl.transaction_index is distinct from coalesce(idx.evm_index, 0);
