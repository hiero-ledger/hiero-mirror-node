// SPDX-License-Identifier: Apache-2.0

import {BYTE_SIZE} from './constants';
import StreamObject from './streamObject';
import {readLengthAndBytes} from './utils';

class RecordStreamObject extends StreamObject {
  static MAX_RECORD_LENGTH = 64 * 1024;
  static MAX_TRANSACTION_LENGTH = 64 * 1024;

  /**
   * Reads the body of the record stream object
   * @param {Buffer} buffer
   * @returns {Number} The size of the body in bytes
   */
  _readBody(buffer) {
    const record = readLengthAndBytes(buffer, BYTE_SIZE, RecordStreamObject.MAX_RECORD_LENGTH, false);
    const transaction = readLengthAndBytes(
      buffer.subarray(record.length),
      BYTE_SIZE,
      RecordStreamObject.MAX_TRANSACTION_LENGTH,
      false
    );
    this.record = record.bytes;
    this.transaction = transaction.bytes;

    return record.length + transaction.length;
  }
}

export default RecordStreamObject;
