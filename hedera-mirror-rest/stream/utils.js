// SPDX-License-Identifier: Apache-2.0

import log4js from 'log4js';

import {INT_SIZE} from './constants';
import TransactionId from '../transactionId';

const logger = log4js.getLogger();

// the sum of the length field and the checksum field
const SIMPLE_SUM = 101;

/**
 * Converts a proto.TransactionID object to TransactionId
 *
 * @param {proto.TransactionID} transactionId
 * @return {TransactionId}
 */
const protoTransactionIdToTransactionId = (transactionId) => {
  const {accountID, transactionValidStart} = transactionId;
  const transactionIdStr = [
    [accountID.shardNum, accountID.realmNum, accountID.accountNum].join('.'),
    transactionValidStart.seconds,
    transactionValidStart.nanos,
  ].join('-');
  return TransactionId.fromString(transactionIdStr);
};

/**
 * Reads the length field, an optional checksum, and the byte array from buffer
 *
 * @param {Buffer} buffer - The buffer to read data from
 * @param {Number} minLength - The minimum allowed length
 * @param {Number} maxLength - The maximum allowed length
 * @param {boolean} hasChecksum - If there is a checksum field
 * @return {Object} The length read and the bytes
 */
const readLengthAndBytes = (buffer, minLength, maxLength, hasChecksum) => {
  const message = 'Error reading length and bytes';
  const length = buffer.readInt32BE();
  let offset = INT_SIZE;
  if (minLength === maxLength) {
    if (length !== minLength) {
      throw new Error(`${message}, expect length ${minLength} got ${length}`);
    }
  } else if (length < minLength || length > maxLength) {
    throw new Error(`${message}, expect length ${length} within [${minLength}, ${maxLength}]`);
  }

  if (hasChecksum) {
    const checksum = buffer.readInt32BE(offset);
    const expected = SIMPLE_SUM - length;
    offset += INT_SIZE;
    if (checksum !== expected) {
      throw new Error(`${message}, expect checksum ${checksum} to be ${expected}`);
    }
  }

  return {
    length: offset + length,
    bytes: readNBytes(buffer.subarray(offset), length),
  };
};

/**
 * Reads a byte array from buffer
 *
 * @param {Buffer} buffer
 * @param {Number} length
 * @return {Buffer}
 */
const readNBytes = (buffer, length) => {
  if (length < 0 || buffer.length < length) {
    throw new Error(`Error reading byte array, expect ${length}-byte data got ${buffer.length}-byte`);
  }

  return buffer.subarray(0, length);
};

export {logger, protoTransactionIdToTransactionId, readLengthAndBytes, readNBytes};
