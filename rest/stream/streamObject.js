// SPDX-License-Identifier: Apache-2.0

import {INT_SIZE, LONG_SIZE} from './constants';

// classId, classVersion
const STREAM_OBJECT_HEADER_SIZE = LONG_SIZE + INT_SIZE;

class StreamObject {
  /**
   * Reads stream object from buffer
   * @param {Buffer} buffer - The buffer to read the stream object from
   */
  constructor(buffer) {
    this.classId = buffer.readBigInt64BE();
    this.classVersion = buffer.readInt32BE(LONG_SIZE);

    this.bodyLength = this._readBody(buffer.subarray(STREAM_OBJECT_HEADER_SIZE));
  }

  _readBody(buffer) {
    return 0;
  }

  /**
   * Gets the serialized header with fields in little-endian order.
   *
   * @return {Buffer}
   */
  getHeaderLE() {
    const header = Buffer.alloc(STREAM_OBJECT_HEADER_SIZE);
    header.writeBigInt64LE(this.classId);
    header.writeInt32LE(this.classVersion, LONG_SIZE);
    return header;
  }

  getHeaderLength() {
    return STREAM_OBJECT_HEADER_SIZE;
  }

  getLength() {
    return STREAM_OBJECT_HEADER_SIZE + this.bodyLength;
  }
}

export default StreamObject;
