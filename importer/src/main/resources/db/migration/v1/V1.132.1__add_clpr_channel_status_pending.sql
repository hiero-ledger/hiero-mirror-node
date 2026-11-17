-- PENDING is pre-creation only (tracked via clpr_channel_pending_commitment); consensus should never revert an
-- existing clpr_channel row back to it, but the value is added so an unexpected occurrence can be recorded instead
-- of dropped
alter type clpr_channel_status add value if not exists 'PENDING' before 'ACTIVE';
