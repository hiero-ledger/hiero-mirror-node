// SPDX-License-Identifier: Apache-2.0

import crypto from 'crypto';
import {proto} from '@hiero-ledger/proto';

import {INT_SIZE} from './constants';
import CompactRecordFile from './compactRecordFile';
import HashObject from './hashObject';

class RecordFileV6 extends CompactRecordFile {
  // hash object class ID (long) and class version (int), in big-endian
  static #HASH_OBJECT_HEAD = Buffer.from([0xf4, 0x22, 0xda, 0x83, 0xa2, 0x51, 0x74, 0x1e, 0x00, 0x00, 0x00, 0x01]);
  // digest type (int) - SHA384, length (int) - 48, in big-endian
  static #HASH_OBJECT_TYPE_LENGTH = Buffer.from([0x58, 0xff, 0x81, 0x1b, 0x00, 0x00, 0x00, 0x30]);
  // head is composed of version, hapiVersionMajor, hapiVersionMinor, and hapiVersionPatch, all are of type int
  static #HEAD_SIZE = INT_SIZE * 4;
  // record stream object class ID (long) and class version (int), in big-endian
  static #RECORD_STREAM_OBJECT_HEAD = Buffer.from([
    0xe3, 0x70, 0x92, 0x9b, 0xa5, 0x42, 0x9d, 0x8b, 0x00, 0x00, 0x00, 0x01,
  ]);

  static version = 6;

  _calculateMetadataHash() {
    const startHashObject = new HashObject(this.startRunningHashObject);
    const endHashObject = new HashObject(this.endRunningHashObject);
    return crypto
      .createHash(HashObject.SHA_384.name)
      .update(this.head)
      .update(startHashObject.hash)
      .update(endHashObject.hash)
      .update(this.blockNumber)
      .digest();
  }

  _hasRecordStreamObjects() {
    return this._recordStreamItems !== undefined;
  }

  _getRecordStreamObject(index) {
    const recordStreamItem = this._recordStreamItems[index];
    const recordBuffer = proto.TransactionRecord.encode(recordStreamItem.record).finish();
    const recordBufferLength = Buffer.allocUnsafe(INT_SIZE);
    recordBufferLength.writeInt32BE(recordBuffer.length);

    const transactionBuffer = proto.Transaction.encode(recordStreamItem.transaction).finish();
    const transactionBufferLength = Buffer.allocUnsafe(INT_SIZE);
    transactionBufferLength.writeInt32BE(transactionBuffer.length);

    return Buffer.concat([
      this.constructor.#RECORD_STREAM_OBJECT_HEAD,
      recordBufferLength,
      recordBuffer,
      transactionBufferLength,
      transactionBuffer,
    ]);
  }

  _parseFromBuffer(buffer) {
    // remove the version
    const recordStreamFile = proto.RecordStreamFile.decode(buffer.subarray(INT_SIZE));
    this.head = Buffer.allocUnsafe(this.constructor.#HEAD_SIZE);
    let offset = this.head.writeInt32BE(this.constructor.version);
    offset = this.head.writeInt32BE(recordStreamFile.hapiProtoVersion.major, offset);
    offset = this.head.writeInt32BE(recordStreamFile.hapiProtoVersion.minor, offset);
    this.head.writeInt32BE(recordStreamFile.hapiProtoVersion.patch, offset);

    this.startObjectRunningHash = recordStreamFile.startObjectRunningHash;
    this.startRunningHashObject = this.constructor.#getHashObject(this.startObjectRunningHash);
    this.endObjectRunningHash = recordStreamFile.endObjectRunningHash;
    this.endRunningHashObject = this.constructor.#getHashObject(this.endObjectRunningHash);
    this.blockNumber = Buffer.from(recordStreamFile.blockNumber.toBytesBE());

    this._recordStreamItems = recordStreamFile.recordStreamItems;
    this._hashes = Array.from({length: this._recordStreamItems.length});

    this._recordStreamItems.forEach((item, index) => {
      this._addTransaction(item.record, index);
    });
  }

  /**
   * Gets the serialized bytes of a platform HashObject from a protobuf HashObject
   *
   * @param {proto.IHashObject} protoHashObject
   * @returns {Buffer}
   */
  static #getHashObject(protoHashObject) {
    return Buffer.concat([this.#HASH_OBJECT_HEAD, this.#HASH_OBJECT_TYPE_LENGTH, protoHashObject.hash]);
  }
}

export default RecordFileV6;
