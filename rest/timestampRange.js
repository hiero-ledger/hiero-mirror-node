// SPDX-License-Identifier: Apache-2.0

import {Range} from 'pg-range';

import {orderFilterValues} from './constants';
import config from './config';
import {isTestEnv, nowInNs, nsToSecNs} from './utils';

const queryConfig = config.query;

/**
 * If enabled in config, ensure the returned timestamp range is fully bound; contains both a begin
 * and end timestamp value. The provided Range is not modified. If changes are made a copy is returned.
 *
 * @param {Range} range timestamp range, typically based on query parameters. Note the bounds should be '[]'
 * @param {string} order the order in the http request
 * @return {Object} fully bound timestamp range and next timestamp
 */
const bindTimestampRange = async (range, order) => {
  if (!queryConfig.bindTimestampRange) {
    return {range};
  }

  const {maxTransactionsTimestampRangeNs} = queryConfig;
  const boundRange = Range(range?.begin ?? (await getFirstTransactionTimestamp()), range?.end ?? nowInNs(), '[]');
  if (boundRange.end - boundRange.begin + 1n <= maxTransactionsTimestampRangeNs) {
    return {range: boundRange};
  }

  let next;
  if (order === orderFilterValues.DESC) {
    next = boundRange.begin = boundRange.end - maxTransactionsTimestampRangeNs + 1n;
  } else {
    next = boundRange.end = boundRange.begin + maxTransactionsTimestampRangeNs - 1n;
  }

  return {range: boundRange, next: nsToSecNs(next)};
};

/**
 * Get the first transaction's consensus timestamp from the database. Note the db query runs once and the timestamp is
 * cached for subsequent calls.
 *
 * @return {Promise<bigint>} the first transaction's consensus timestamp
 */
const getFirstTransactionTimestamp = (() => {
  let timestamp;

  const func = async () => {
    if (timestamp === undefined) {
      const {rows} = await pool.queryQuietly(`select consensus_timestamp
                                              from transaction
                                              order by consensus_timestamp
                                              limit 1`);
      if (rows.length !== 1) {
        return 0n; // fallback to 0
      }

      timestamp = rows[0].consensus_timestamp;
      logger.info(`First transaction's consensus timestamp is ${timestamp}`);
    }

    return BigInt(timestamp);
  };

  if (isTestEnv()) {
    func.reset = () => (timestamp = undefined);
  }

  return func;
})();

export {bindTimestampRange};

const testExports = {};
if (isTestEnv()) {
  testExports.getFirstTransactionTimestamp = getFirstTransactionTimestamp;
}

export default testExports;
