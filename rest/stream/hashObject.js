// SPDX-License-Identifier: Apache-2.0

import {INT_SIZE} from './constants';
import StreamObject from './streamObject';
import {readLengthAndBytes} from './utils';

class HashObject extends StreamObject {
  // properties of SHA-384 hash algorithm
  static SHA_384 = {
    encoding: 'hex',
    length: 48,
    name: 'sha384',
  };

  /**
   * Reads the body of the hash object
   * @param {Buffer} buffer
   * @returns {Number} The size of the body in bytes
   */
  _readBody(buffer) {
    // always SHA-384
    const hashLength = HashObject.SHA_384.length;
    this.digestType = buffer.readInt32BE();
    const {length, bytes} = readLengthAndBytes(buffer.subarray(INT_SIZE), hashLength, hashLength, false);
    this.hash = bytes;

    return INT_SIZE + length;
  }
}

export default HashObject;
