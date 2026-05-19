-- SPDX-License-Identifier: Apache-2.0

DELETE FROM contract_log cl_dup
WHERE cl_dup.consensus_timestamp > 1772316000000000000
AND cl_dup.topic0 = '\xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef'
AND LENGTH(cl_dup.data) != 32
  AND EXISTS (
    SELECT 1
    FROM contract_log cl_orig
    WHERE cl_orig.consensus_timestamp = cl_dup.consensus_timestamp
      AND cl_orig.consensus_timestamp > 1772316000000000000
      AND cl_orig.topic0 = '\xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef'
      AND cl_orig.contract_id = cl_dup.contract_id
      AND cl_orig.transaction_hash != cl_dup.transaction_hash
      AND cl_orig.bloom != cl_dup.bloom
      AND LENGTH(cl_orig.data) = 32
      AND LTRIM(encode(cl_orig.data, 'hex'), '0') = LTRIM(encode(cl_dup.data, 'hex'), '0')
      AND (
        cl_orig.topic1 IS NOT DISTINCT FROM cl_dup.topic1
        OR trim(leading '\x00'::bytea from cl_orig.topic1) = trim(leading '\x00'::bytea from cl_dup.topic1)
        OR (
          octet_length(trim(leading '\x00'::bytea from cl_dup.topic1)) <= 8
          AND EXISTS (
            SELECT 1 FROM entity e
            WHERE e.id = ('x' || lpad(encode(trim(leading '\x00'::bytea from cl_dup.topic1), 'hex'), 16, '0'))::bit(64)::bigint
              AND (
                trim(leading '\x00'::bytea from e.evm_address) = trim(leading '\x00'::bytea from cl_orig.topic1)
                OR trim(leading '\x00'::bytea from e.alias) = trim(leading '\x00'::bytea from cl_orig.topic1)
              )
          )
        )
      )
      AND (
        cl_orig.topic2 IS NOT DISTINCT FROM cl_dup.topic2
        OR trim(leading '\x00'::bytea from cl_orig.topic2) = trim(leading '\x00'::bytea from cl_dup.topic2)
        OR (
          octet_length(trim(leading '\x00'::bytea from cl_dup.topic2)) <= 8
          AND EXISTS (
            SELECT 1 FROM entity e
            WHERE e.id = ('x' || lpad(encode(trim(leading '\x00'::bytea from cl_dup.topic2), 'hex'), 16, '0'))::bit(64)::bigint
              AND (
                trim(leading '\x00'::bytea from e.evm_address) = trim(leading '\x00'::bytea from cl_orig.topic2)
                OR trim(leading '\x00'::bytea from e.alias) = trim(leading '\x00'::bytea from cl_orig.topic2)
              )
          )
        )
      )
      AND cl_orig.topic3 IS NOT DISTINCT FROM cl_dup.topic3
);
