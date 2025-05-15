// SPDX-License-Identifier: Apache-2.0

import {proto} from '@hashgraph/proto';

import {BYTE_SIZE, INT_SIZE} from './constants';
import HashObject from './hashObject';
import SignatureObject from './signatureObject';
import {readLengthAndBytes, readNBytes} from './utils';

// version, object stream signature version
const V5_FILE_HASH_OFFSET = BYTE_SIZE + INT_SIZE;

class SignatureFile {
  /**
   * Parses signature file buffer, retrieves hashes and the corresponding signatures.
   * @param {Buffer} buffer
   */
  constructor(buffer) {
    const version = buffer.readInt8();
    switch (version) {
      case 4:
        this.version = 2;
        this._parseV2SignatureFile(buffer);
        break;
      case 5:
        this.version = 5;
        this._parseV5SignatureFile(buffer);
        break;
      case 6:
        this.version = 6;
        this._parseV6SignatureFile(buffer);
        break;
      default:
        throw new Error(`Unexpected signature file version '${version}'`);
    }
  }

  _parseV2SignatureFile(buffer) {
    // skip type, already checked
    buffer = buffer.subarray(BYTE_SIZE);
    this.fileHash = readNBytes(buffer, HashObject.SHA_384.length);

    buffer = buffer.subarray(HashObject.SHA_384.length);
    const type = buffer.readInt8();
    if (type !== 3) {
      throw new Error(`Unexpected type delimiter '${type}' in signature file`);
    }

    buffer = buffer.subarray(BYTE_SIZE);
    const {length, bytes} = readLengthAndBytes(buffer, BYTE_SIZE, SignatureObject.SHA_384_WITH_RSA.maxLength, false);
    this.fileHashSignature = bytes;

    buffer = buffer.subarray(length);
    if (buffer.length !== 0) {
      throw new Error('Extra data discovered in signature file ');
    }
  }

  _parseV5SignatureFile(buffer) {
    buffer = buffer.subarray(V5_FILE_HASH_OFFSET);
    const fileHashObject = new HashObject(buffer);
    const fileHashSignatureObject = new SignatureObject(buffer.subarray(fileHashObject.getLength()));

    buffer = buffer.subarray(fileHashObject.getLength() + fileHashSignatureObject.getLength());
    const metadataHashObject = new HashObject(buffer);
    const metadataHashSignatureObject = new SignatureObject(buffer.subarray(metadataHashObject.getLength()));

    if (buffer.length !== metadataHashObject.getLength() + metadataHashSignatureObject.getLength()) {
      throw new Error('Extra data discovered in signature file');
    }

    this.fileHash = fileHashObject.hash;
    this.fileHashSignature = fileHashSignatureObject.signature;
    this.metadataHash = metadataHashObject.hash;
    this.metadataHashSignature = metadataHashSignatureObject.signature;
  }

  _parseV6SignatureFile(buffer) {
    const signatureFile = proto.SignatureFile.decode(buffer.subarray(1));
    this.fileHash = signatureFile.fileSignature.hashObject.hash;
    this.fileHashSignature = signatureFile.fileSignature.signature;
    this.metadataHash = signatureFile.metadataSignature.hashObject.hash;
    this.metadataHashSignature = signatureFile.metadataSignature.signature;
  }
}

export default SignatureFile;
