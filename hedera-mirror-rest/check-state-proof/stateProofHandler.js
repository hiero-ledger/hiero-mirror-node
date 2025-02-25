// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';
import log4js from 'log4js';
import path from 'path';

import AddressBook from './addressBook';
import {CompositeRecordFile, SignatureFile} from '../stream';
import TransactionId from '../transactionId';
import {performStateProof} from './transactionValidator';
import {makeStateProofDir, storeFile} from './utils';

const logger = log4js.getLogger();

// responsible for parsing response data to valid AddressBook, recordFile and SignFiles objects
class StateProofHandler {
  constructor(stateProofJson, transactionId, nonce = 0, scheduled = false) {
    this.transactionId = TransactionId.fromString(transactionId);
    this.nonce = nonce;
    this.scheduled = scheduled;
    this.outputDir = makeStateProofDir(transactionId, stateProofJson);
    this.setStateProofComponents(stateProofJson);
  }

  setStateProofComponents(stateProofJson) {
    const stateProof = this.responseJsonToObj(stateProofJson);
    this.addressBooks = this.parseAddressBooks(stateProof.addressBooks);
    this.recordFile = this.parseRecordFile(stateProof.recordFile);
    this.signatureFileMap = this.parseSignatureFiles(stateProof.signatureFiles);
  }

  responseJsonToObj(json) {
    // change keys to camelCase
    const camelCasify = (obj) => {
      const ret = _.mapKeys(obj, (v, k) => _.camelCase(k));
      if (ret.recordFile && _.isPlainObject(ret.recordFile)) {
        ret.recordFile = _.mapKeys(ret.recordFile, (v, k) => _.camelCase(k));
      }
      return ret;
    };

    const base64Decode = (obj) => {
      for (const [k, v] of Object.entries(obj)) {
        if (typeof v === 'string') {
          obj[k] = Buffer.from(v, 'base64');
        } else {
          obj[k] = base64Decode(v);
        }
      }
      return obj;
    };

    return base64Decode(camelCasify(json));
  }

  parseAddressBooks(addressBookBuffers) {
    const addressBooks = addressBookBuffers.map((addressBookBuffer, index) => {
      storeFile(addressBookBuffer, path.join(this.outputDir, `addressBook-${index + 1}`), 'txt');
      return new AddressBook(addressBookBuffer);
    });

    logger.debug(`Parsed ${addressBooks.length} address books`);
    return addressBooks;
  }

  parseRecordFile(recordFileBufferOrObj) {
    storeFile(recordFileBufferOrObj, path.join(this.outputDir, 'recordFile'), 'rcd');

    const rcdFile = new CompositeRecordFile(recordFileBufferOrObj);

    logger.debug(`Parsed record and found ${Object.keys(rcdFile.getTransactionMap()).length} transactions`);
    return rcdFile;
  }

  parseSignatureFiles(signatureFiles) {
    const signatureFileMap = Object.fromEntries(
      _.map(signatureFiles, (signatureFileBuffer, nodeAccountId) => {
        storeFile(signatureFileBuffer, path.join(this.outputDir, `signatureFile-${nodeAccountId}`), 'rcd_sig');
        return [nodeAccountId, new SignatureFile(signatureFileBuffer)];
      })
    );
    logger.debug(`Parsed ${Object.keys(signatureFileMap).length} signature files`);
    return signatureFileMap;
  }

  runStateProof() {
    const {nodeAccountIdPublicKeyPairs} = _.last(this.addressBooks);

    // verify transactionId is in recordFile
    const transactionInRecordFile = this.recordFile.containsTransaction(this.transactionId, this.nonce, this.scheduled);
    if (!transactionInRecordFile) {
      logger.error(
        `Transaction ID ${this.transactionId} not present in record file. Available transaction IDs: ${Object.keys(
          this.recordFile.getTransactionMap()
        )}`
      );
      return false;
    }
    logger.info(`Matching transaction was found in record file`);

    return performStateProof(nodeAccountIdPublicKeyPairs, this.signatureFileMap, {
      fileHash: this.recordFile.getFileHash(),
      metadataHash: this.recordFile.getMetadataHash(),
    });
  }
}

export default StateProofHandler;
