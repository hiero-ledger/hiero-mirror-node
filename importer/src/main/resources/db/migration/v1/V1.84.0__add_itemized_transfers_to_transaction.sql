alter table if exists transaction
    add column if not exists itemized_transfer jsonb null;

with nested_itemized_transfer as (
    select consensus_timestamp, jsonb_agg(jsonb_build_object(
      'entity_id', entity_id,
      'amount', amount,
      'is_approval', is_approval
      )) as transfer
    from non_fee_transfer
    group by consensus_timestamp
)

update transaction
set itemized_transfer = transfer
from nested_itemized_transfer
where nested_itemized_transfer.consensus_timestamp = transaction.consensus_timestamp;
