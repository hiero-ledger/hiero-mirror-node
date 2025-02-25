// SPDX-License-Identifier: Apache-2.0

import long from 'long';
import EntityId from './entityId';
import {InvalidArgumentError} from './errors';

class TransactionId {
  constructor(entityId, validStartSeconds, validStartNanos) {
    this.entityId = entityId;
    this.validStartSeconds = validStartSeconds;
    this.validStartNanos = validStartNanos;
  }

  /**
   * @returns {EntityId} entityId of the transaction ID
   */
  getEntityId() {
    return this.entityId;
  }

  /**
   * @returns {string} validStartNs of the transaction ID
   */
  getValidStartNs() {
    return `${this.validStartSeconds}${String(this.validStartNanos).padStart(9, '0')}`;
  }

  /**
   * Convert the transaction ID to a string in the format of "shard.realm.num-ssssssssss-nnnnnnnnn"
   * @returns {string}
   */
  toString() {
    return `${this.entityId.toString()}-${this.validStartSeconds}-${this.validStartNanos}`;
  }
}

/**
 * Construct transaction ID from string. The string must be in the format of "shard.realm.num-ssssssssss-nnnnnnnnn"
 * @param {string} transactionIdStr
 */
const fromString = (transactionIdStr) => {
  const txIdMatches = transactionIdStr.match(/^(\d+)\.(\d+)\.(\d+)-(\d{1,19})-(\d{1,9})$/);
  const message =
    'Invalid Transaction id. Please use "shard.realm.num-sss-nnn" format where sss are seconds and nnn are nanoseconds';
  if (txIdMatches === null || txIdMatches.length !== 6) {
    throw new InvalidArgumentError(message);
  }

  const entityId = EntityId.parse(`${txIdMatches[1]}.${txIdMatches[2]}.${txIdMatches[3]}`);
  const seconds = long.fromString(txIdMatches[4]);
  const nanos = parseInt(txIdMatches[5], 10);
  if (seconds.lessThan(0)) {
    throw new InvalidArgumentError(message);
  }

  return new TransactionId(entityId, seconds, nanos);
};

export default {
  fromString,
};
