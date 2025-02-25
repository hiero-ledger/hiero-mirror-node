// SPDX-License-Identifier: Apache-2.0

import crypto from 'crypto';
import _ from 'lodash';

import HashObject from './hashObject';
import RecordFile from './recordFile';
import RecordStreamObject from './recordStreamObject';
import {calculateRunningHash} from './runningHash';

const {SHA_384} = HashObject;

class CompactRecordFile extends RecordFile {
  static compactObjectFields = [
    'head',
    'startRunningHashObject',
    'hashesBefore',
    'recordStreamObject',
    'hashesAfter',
    'endRunningHashObject',
    'blockNumber',
  ];

  constructor(bufferOrObj) {
    super();

    if (!this.constructor._support(bufferOrObj)) {
      throw new Error(`Unsupported record file version, expect ${this.constructor.version}`);
    }

    if (Buffer.isBuffer(bufferOrObj)) {
      this._parseFromBuffer(bufferOrObj);
    } else {
      this._parseFromObj(bufferOrObj);
    }
  }

  static _support(bufferOrObj) {
    const buffer = Buffer.isBuffer(bufferOrObj) ? bufferOrObj : bufferOrObj.head;
    return RecordFile._readVersion(buffer) === this.version;
  }

  static canCompact(buffer) {
    return Buffer.isBuffer(buffer) && this._support(buffer);
  }

  toCompactObject(transactionId, nonce = 0, scheduled = false) {
    if (!this.containsTransaction(transactionId, nonce, scheduled)) {
      throw new Error(`Transaction ${transactionId} not found in the successful transactions map`);
    }

    if (this._hasRecordStreamObjects()) {
      // parsed from a full record file
      const transactionKey = RecordFile._getTransactionKey(transactionId, nonce, scheduled);
      const index = this._transactionMap[transactionKey];
      this.recordStreamObject = this._getRecordStreamObject(index);

      this._hashes.forEach((value, current) => {
        if (!value && current !== index) {
          // calculate and cache the hash if not found and this is not the transaction of interest
          const recordStreamObject = this._getRecordStreamObject(current);
          this._hashes[current] = crypto.createHash(SHA_384.name).update(recordStreamObject).digest();
        }
      });
      this.hashesBefore = this._hashes.slice(0, index);
      this.hashesAfter = this._hashes.slice(index + 1);
    }

    return _.pick(this, this.constructor.compactObjectFields);
  }

  /**
   * Calculates the record file metadata hash
   *
   * @returns {Buffer}
   */
  _calculateMetadataHash() {
    throw new Error('Unsupported operation');
  }

  /**
   * Gets the RecordStreamObject at the index
   *
   * @param {Number} index
   * @returns {Buffer}
   */
  _getRecordStreamObject(index) {
    throw new Error('Unsupported operation');
  }

  /**
   * Whether the record file object has RecordStreamObjects
   *
   * @return {boolean}
   */
  _hasRecordStreamObjects() {
    return false;
  }

  /**
   * Parses the record file from the raw buffer
   *
   * @param {Buffer} buffer
   * @throws {Error}
   */
  _parseFromBuffer(buffer) {
    throw new Error('Unsupported operation');
  }

  /**
   * Parses the record file from the compact format object
   *
   * @param obj
   */
  _parseFromObj(obj) {
    Object.assign(this, _.pick(obj, this.constructor.compactObjectFields));

    this._verifyEndRunningHash();

    // add the transaction to the map
    const recordStreamObject = new RecordStreamObject(this.recordStreamObject);
    this._addTransaction(recordStreamObject.record);

    // calculate metadata hash
    this._metadataHash = this._calculateMetadataHash();
  }

  /**
   * Verifies the calculated end running hash matches what's in the record file
   *
   * @throws {Error} Will throw if calculated hash doesn't match
   */
  _verifyEndRunningHash() {
    const startHashObject = new HashObject(this.startRunningHashObject);
    const endHashObject = new HashObject(this.endRunningHashObject);
    const hashes = [
      startHashObject.hash,
      ...this.hashesBefore,
      crypto.createHash(SHA_384.name).update(this.recordStreamObject).digest(),
      ...this.hashesAfter,
    ];

    // when calculating running hash, classId and classVersion are digested in little-endian
    const header = startHashObject.getHeaderLE();
    const actualHash = hashes.reduce((runningHash, nextHash) =>
      calculateRunningHash(
        {
          header,
          hash: runningHash,
        },
        {
          header,
          hash: nextHash,
        },
        SHA_384.name
      )
    );
    if (!actualHash.equals(endHashObject.hash)) {
      throw new Error('End object running hash mismatch');
    }
  }
}

export default CompactRecordFile;
