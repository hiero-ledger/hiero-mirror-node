-- Recalculate all end timestamps since we accidentally set the 0.0.102 end timestamp instead of the 0.0.101's in GH1229
update address_book as ab
set end_consensus_timestamp = (
  select min(next.start_consensus_timestamp) - 1
  from address_book as next
  where next.file_id = ab.file_id and next.start_consensus_timestamp > ab.start_consensus_timestamp
);
