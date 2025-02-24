// SPDX-License-Identifier: Apache-2.0

// external libraries
import crypto from 'crypto';

import {BYTE_SIZE, INT_SIZE} from './constants';
import HashObject from './hashObject';
import RecordFile from './recordFile';
import RecordStreamObject from './recordStreamObject';
import {readLengthAndBytes} from './utils';

const {MAX_TRANSACTION_LENGTH, MAX_RECORD_LENGTH} = RecordStreamObject;
const {SHA_384} = HashObject;

// version, hapiVersion, previous hash marker, SHA-384 hash
const PRE_V5_HEADER_LENGTH = INT_SIZE + INT_SIZE + BYTE_SIZE + SHA_384.length;

class RecordFilePreV5 extends RecordFile {
  /**
   * Parses rcd file storing hash and transactionId map for future verification
   * @param {Buffer} buffer
   */
  constructor(buffer) {
    super();

    if (RecordFilePreV5._support(buffer)) {
      this._parsePreV5RecordFile(buffer);
    } else {
      throw new Error(`Unsupported record file`);
    }
  }

  static _support(bufferOrObj) {
    if (!Buffer.isBuffer(bufferOrObj)) {
      return false;
    }

    const version = RecordFile._readVersion(bufferOrObj);
    return version === 1 || version === 2;
  }

  getVersion() {
    return this._version;
  }

  _parsePreV5RecordFile(buffer) {
    this._version = RecordFile._readVersion(buffer);
    this._calculatePreV5FileHash(buffer.readInt32BE(), buffer);

    buffer = buffer.subarray(PRE_V5_HEADER_LENGTH);
    let index = 0;
    while (buffer.length !== 0) {
      const marker = buffer.readInt8();
      if (marker !== 2) {
        throw new Error(`Unsupported marker ${marker}, expect 2`);
      }

      buffer = buffer.subarray(BYTE_SIZE);
      const transaction = readLengthAndBytes(buffer, BYTE_SIZE, MAX_TRANSACTION_LENGTH, false);
      const record = readLengthAndBytes(buffer.subarray(transaction.length), BYTE_SIZE, MAX_RECORD_LENGTH, false);
      this._addTransaction(record.bytes, index);
      index++;

      buffer = buffer.subarray(transaction.length + record.length);
    }
  }

  _calculatePreV5FileHash(version, buffer) {
    const fileDigest = crypto.createHash(SHA_384.name);

    if (version === 1) {
      fileDigest.update(buffer);
    } else {
      // version 2
      const contentHash = crypto.createHash(SHA_384.name).update(buffer.subarray(PRE_V5_HEADER_LENGTH)).digest();
      fileDigest.update(buffer.subarray(0, PRE_V5_HEADER_LENGTH)).update(contentHash);
    }

    this._fileHash = fileDigest.digest();
  }
}

export default RecordFilePreV5;
