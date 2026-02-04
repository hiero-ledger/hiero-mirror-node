// SPDX-License-Identifier: Apache-2.0

import {proto} from '@hiero-ledger/proto';
import {logger, protoTransactionIdToTransactionId} from './utils';

class RecordFile {
  static version;

  constructor() {
    this._fileHash = null;
    this._metadataHash = null;
    // a map of successful transactions, from its transaction ID to its index in the parsed transactions array
    // if the concrete class doesn't implement a transaction array, the index will be null
    this._transactionMap = {};
  }

  static _getTransactionKey(transactionId, nonce, scheduled) {
    return `${transactionId}-${nonce}-${scheduled}`;
  }

  static _readVersion(buffer) {
    return buffer.readInt32BE();
  }

  static _support(bufferOrObj) {
    return false;
  }

  static canCompact(buffer) {
    return false;
  }

  /**
   * Checks if a transaction is in the record file's successful transaction map
   *
   * @param {TransactionId} transactionId
   * @param {Number} nonce
   * @param {boolean} scheduled
   * @returns {boolean}
   */
  containsTransaction(transactionId, nonce = 0, scheduled = false) {
    return RecordFile._getTransactionKey(transactionId, nonce, scheduled) in this._transactionMap;
  }

  /**
   * Gets the record file's file hash, may return null if not available.
   *
   * @returns {Buffer}
   */
  getFileHash() {
    return this._fileHash;
  }

  /**
   * Gets the record file's metadata hash, may return null if not available.
   *
   * @returns {Buffer}
   */
  getMetadataHash() {
    return this._metadataHash;
  }

  /**
   * Gets the transaction map.
   * @returns {{}}
   */
  getTransactionMap() {
    return this._transactionMap;
  }

  /**
   * Gets the version.
   *
   * @returns {number}
   */
  getVersion() {
    return this.constructor.version;
  }

  /**
   * Converts the parsed record file to the compact format object if possible. Throws error if the transactionId is not
   * in the successful transaction map or the implementation does not support the operation.
   *
   * @param {TransactionId} transactionId
   * @param {Number} nonce
   * @param {boolean} scheduled
   * @returns {{}}
   */
  toCompactObject(transactionId, nonce = 0, scheduled = false) {
    throw new Error('Unsupported operation');
  }

  /**
   * Adds a mapping of a transaction's id to its index in the record file if the transaction response status is SUCCESS.
   *
   * @param {Buffer|proto.TransactionRecord} recordBufferOrObject
   * @param {Number} index
   * @private
   */
  _addTransaction(recordBufferOrObject, index = null) {
    const record =
      recordBufferOrObject instanceof Buffer
        ? proto.TransactionRecord.decode(recordBufferOrObject)
        : recordBufferOrObject;
    const {receipt, transactionID, scheduleRef} = record;
    const transactionId = protoTransactionIdToTransactionId(transactionID);
    const scheduled = scheduleRef !== null;
    if (
      receipt.status !== proto.ResponseCodeEnum.SUCCESS &&
      receipt.status !== proto.ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED &&
      receipt.status !== proto.ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION
    ) {
      logger.debug(`Skip non-successful transaction ${transactionId}, ${receipt.status}`);
      return;
    }

    const transactionKey = RecordFile._getTransactionKey(transactionId, transactionID.nonce, scheduled);
    logger.debug(`Add successful transaction ${transactionKey}`);
    this._transactionMap[transactionKey] = index;
  }
}

export default RecordFile;
