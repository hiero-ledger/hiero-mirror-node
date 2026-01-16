alter table if exists contract_result
    add column if not exists gas_consumed bigint null;

-- Update contract_result with gas_consumed values.
-- Use the contract_action with call_depth = 0, as this is the top-level action, and add intrinsic gas to it.
with contract_action_gas_usage as (
    select
        consensus_timestamp,
        gas_used
    from
        contract_action
    where
        call_depth = 0
), contract_transaction_info as (
    select
        cr.consensus_timestamp,
        cr.contract_id,
        e.created_timestamp,
        -- Correctly assign `payload` based on whether it's a contract creation or call.
        coalesce(
            case
                when cr.consensus_timestamp = e.created_timestamp then c.initcode
                when cr.failed_initcode is not null then cr.failed_initcode
                else cr.function_parameters
            end, ''::bytea) as payload,
        -- Contract creation has an extra cost of 32000
        case
            when cr.consensus_timestamp = e.created_timestamp then 32000
            when cr.failed_initcode is not null then 32000
            else 0
        end as creation_cost
    from
        contract_result cr
    left join contract c on cr.contract_id = c.id
    left join entity e on c.id = e.id
), gas_calculation as (
    select
        cti.consensus_timestamp,
        length(payload) as count,
        creation_cost,
        cagu.gas_used,
        case when length(payload) = 0 then 0
             else cardinality(string_to_array(encode(payload, 'escape'),'\000')) - 1
        end as zero_acount
    from
        contract_transaction_info cti
    -- Filter out only contract transactions with contract actions sidecars
    join contract_action_gas_usage cagu on cti.consensus_timestamp = cagu.consensus_timestamp
)
update contract_result cr
set gas_consumed = gc.gas_used + creation_cost + 21000 + zero_acount * 4 + (count - zero_acount) * 16
from gas_calculation gc
where cr.consensus_timestamp = gc.consensus_timestamp;
