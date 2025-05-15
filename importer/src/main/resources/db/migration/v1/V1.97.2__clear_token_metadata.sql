-- clear token metadata and metadata_key accidentally set prior to services 0.49.0
with timestamp_info as (
    -- make it closed-open, if nothing found, it's int8range(0, 0) which is empty
    -- 1709229598938101716 is the last 0.46.x transaction's timestamp in testnet, mainnet's is at a later time
    select int8range(coalesce(min(consensus_start), 0), coalesce(max(consensus_end) + 1, 0)) as affected_range
    from record_file
    where hapi_version_major = 0 and hapi_version_minor = 47 and consensus_end > 1709229598938101716
), clear_token_history as (
  update token_history
  set metadata = null,
      metadata_key = null
  from timestamp_info
  -- the two ranges overlap and the token timestamp range does not extend to the left of the affected range, i.e.
  -- the lower bound of the token timestamp range (when the token was created / updated), is in affected_range.
  -- this is faster since it can utilize the gist index on timestamp_range
  where timestamp_range && affected_range and timestamp_range &> affected_range
)
update token
set metadata = null,
    metadata_key = null
from timestamp_info
where affected_range @> lower(timestamp_range);
