// SPDX-License-Identifier: Apache-2.0

import crypto from 'crypto';

import {INT_SIZE} from './constants';
import CompactRecordFile from './compactRecordFile';
import HashObject from './hashObject';
import RecordStreamObject from './recordStreamObject';

// version, hapi version major/minor/patch, object stream version
const V5_START_HASH_OFFSET = INT_SIZE + (INT_SIZE + INT_SIZE + INT_SIZE) + INT_SIZE;

class RecordFileV5 extends CompactRecordFile {
  static version = 5;

  _calculateMetadataHash() {
    return crypto
      .createHash(HashObject.SHA_384.name)
      .update(this.head)
      .update(this.startRunningHashObject)
      .update(this.endRunningHashObject)
      .digest();
  }

  _hasRecordStreamObjects() {
    return this._recordStreamObjects !== undefined;
  }

  _getRecordStreamObject(index) {
    return this._recordStreamObjects[index];
  }

  _parseFromBuffer(buffer) {
    // only the REST service parses full record file v5 data and only the fields in the compact format response are
    // needed, so don't calculate full file hash and metadata hash

    // skip the bytes before the start hash object to read a list of stream objects organized as follows:
    //
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |  Start Object Running Hash  |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |    Record Stream Object     |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |    ...                      |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |    Record Stream Object     |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |   End Object Running Hash   |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    //
    // Note the start object running hash and the end object running hash are of the same type HashObject and
    // they have the same classId an classVersion.
    this.head = buffer.subarray(0, V5_START_HASH_OFFSET);
    this.blockNumber = null; // always null for v5

    buffer = buffer.subarray(V5_START_HASH_OFFSET);
    const startHashObject = new HashObject(buffer);
    this.startRunningHashObject = buffer.subarray(0, startHashObject.getLength());

    buffer = buffer.subarray(startHashObject.getLength());
    this._recordStreamObjects = []; // store the record stream object raw buffer
    while (buffer.readBigInt64BE() !== startHashObject.classId) {
      // record stream objects are between the start hash object and the end hash object
      const recordStreamObject = new RecordStreamObject(buffer);
      this._addTransaction(recordStreamObject.record, this._recordStreamObjects.length);
      this._recordStreamObjects.push(buffer.subarray(0, recordStreamObject.getLength()));
      buffer = buffer.subarray(recordStreamObject.getLength());
    }

    this._hashes = Array.from({length: this._recordStreamObjects.length});
    const endHashObject = new HashObject(buffer);
    this.endRunningHashObject = buffer.subarray(0, endHashObject.getLength());
    if (buffer.length !== endHashObject.getLength()) {
      throw new Error('Extra data discovered in record file');
    }
  }
}

export default RecordFileV5;
