create table if not exists contract_state
(
  contract_id        bigint not null,
  created_timestamp  bigint not null,
  modified_timestamp bigint not null,
  slot               bytea  not null,
  value              bytea  null
);

-- migrate contract_state from contract_state_changes
with contract_state_initial as (
    select
        contract_id,
        slot,
        min(consensus_timestamp) as created_timestamp
    from contract_state_change
    group by contract_id, slot
), latest_contract_state as (
    select distinct on (csc.contract_id, csc.slot)
        csc.contract_id,
        csc.slot,
        csi.created_timestamp,
        consensus_timestamp as modified_timestamp,
        coalesce(value_written, value_read) as value
    from contract_state_change csc
    join contract_state_initial csi on csi.contract_id = csc.contract_id and csi.slot = csc.slot
    where csc.migration is true
      or (csc.migration is false and csc.value_written is not null)
    order by csc.contract_id, csc.slot, consensus_timestamp desc
)
insert into contract_state (contract_id, created_timestamp, modified_timestamp, slot, value)
select
    contract_id,
    created_timestamp,
    modified_timestamp,
    case when length(slot) >= 32
        then slot
        else decode(lpad(encode(slot, 'hex'), 64, '0'), 'hex')
    end,
    value
    from latest_contract_state;

alter table contract_state add primary key (contract_id, slot);