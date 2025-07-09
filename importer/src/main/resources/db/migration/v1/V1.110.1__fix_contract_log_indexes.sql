with calculated_indexes as (
    select
        cl_inner.consensus_timestamp,
        cl_inner.index as old_contract_log_index,
        (row_number() over (
            partition by rf.index
            order by cl_inner.consensus_timestamp asc, cl_inner.index asc
        ) - 1) as new_calculated_index
    from
        contract_log cl_inner
    join
        record_file rf on cl_inner.consensus_timestamp >= rf.consensus_start
                      and cl_inner.consensus_timestamp <= rf.consensus_end
	order by cl_inner.consensus_timestamp desc, cl_inner.index desc
)
update contract_log cl
set
    "index" = ci.new_calculated_index
from
    calculated_indexes ci
where
    cl.consensus_timestamp = ci.consensus_timestamp
    and cl.index = ci.old_contract_log_index;