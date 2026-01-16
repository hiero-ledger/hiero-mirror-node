create or replace function get_transaction_info_by_hash(transactionHash bytea)
  returns table
    (
      consensus_timestamp bigint,
      hash                bytea,
      payer_account_id    bigint
    )
as
$$
declare
  distributionId smallint;
begin
  distributionId := ('x' || encode(substring(transactionHash from 1 for 2), 'hex'))::bit(32)::int >> 16;
  return query execute 'select consensus_timestamp, hash, payer_account_id from transaction_hash ' ||
    'where distribution_id = $1 and substring(hash from 1 for 32) = $2' using distributionId, transactionHash;
end
$$ language plpgsql;
