// SPDX-License-Identifier: Apache-2.0

import {getResponseLimit} from './config';
import {ETH_HASH_LENGTH, orderFilterValues} from './constants';
import {TransactionHash} from './model';

const {default: defaultLimit} = getResponseLimit();

const limitClause = `limit ${defaultLimit}`;
const mainQuery = `select * from get_transaction_info_by_hash($1)`;
const orderClause = `order by ${TransactionHash.CONSENSUS_TIMESTAMP}`;

/**
 * Get the transaction hash rows by the hash. Note if the hash is more than 32 bytes, it's queried by the 32-byte prefix
 * then rechecked against the full hash.
 *
 * @param {Buffer} hash
 * @param {{order: string, timestampFilters: Array<{operator: string, value: any}>}} options
 * @returns {Promise<Object[]>}
 */
const getTransactionHash = async (hash, {order = orderFilterValues.ASC, timestampFilters = []} = {}) => {
  const normalized = normalizeTransactionHash(hash);
  const params = [normalized];

  const timestampConditions = [];
  for (const filter of timestampFilters) {
    timestampConditions.push(`${TransactionHash.CONSENSUS_TIMESTAMP} ${filter.operator} $${params.push(filter.value)}`);
  }

  const query = `${mainQuery}
    ${timestampConditions.length !== 0 ? `where ${timestampConditions.join(' and ')}` : ''}
    ${orderClause} ${order}
    ${limitClause}`;

  const {rows} = await pool.queryQuietly(query, params);
  return normalized !== hash ? rows.filter((row) => row.hash.equals(hash)) : rows;
};

// The first part of the regex is for the base64url encoded 48-byte transaction hash. Note base64url replaces '+' with
// '-' and '/' with '_'. The padding character '=' is not included since base64 encoding a 48-byte array always
// produces a 64-byte string without padding
const transactionHashRegex = /^([\dA-Za-z+\-\/_]{64}|(0x)?[\dA-Fa-f]{96})$/;

const isValidTransactionHash = (hash) => transactionHashRegex.test(hash);

const normalizeTransactionHash = (hash) => (hash.length > ETH_HASH_LENGTH ? hash.subarray(0, ETH_HASH_LENGTH) : hash);

export {getTransactionHash, isValidTransactionHash};
