// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';
import crypto from 'crypto';
import log4js from 'log4js';

const logger = log4js.getLogger();

/**
 * Verifies given hash was signed with the provided public key
 * Formats public key into acceptable format for usage
 * @param publicKeyHex
 * @param hash
 * @param signature
 * @returns {boolean}
 */
const verifySignature = (publicKeyHex, hash, signature) => {
  const verify = crypto.createVerify('RSA-SHA384');
  verify.update(hash);

  const publicKey = `-----BEGIN PUBLIC KEY-----\n${Buffer.from(publicKeyHex, 'hex').toString(
    'base64'
  )}\n-----END PUBLIC KEY-----`;
  return verify.verify(publicKey, signature);
};

/**
 * given map of addressBook node -> public Key & map of signatureFiles node -> Signature, verifySignature()
 * for every signature from a file against the appropriate public key from the it's match node in the address book
 * ensure the number of validations is at least 1/3 on the number of nodes
 * @param nodePublicKeyMap
 * @param signatureFileMap
 * @returns consensus hashes
 */
const verifySignatures = (nodePublicKeyMap, signatureFileMap) => {
  const validatedSignatureFilesMap = {};
  const consensusHashMap = {hashes: {}, count: 0};
  let maxHashCount = 0;

  // create a map of hash -> nodeId to show alignment
  for (const [nodeAccountId, signatureFile] of Object.entries(signatureFileMap)) {
    const {fileHash, fileHashSignature, metadataHash, metadataHashSignature} = signatureFile;
    logger.info(`Verify signatures passed for node ${nodeAccountId}`);
    const publicKey = nodePublicKeyMap[nodeAccountId];

    if (!verifySignature(publicKey, fileHash, fileHashSignature)) {
      logger.error(`Failed to verify fileHash signature for node ${nodeAccountId}!`);
      continue;
    }

    if (metadataHash && !verifySignature(publicKey, metadataHash, metadataHashSignature)) {
      logger.error(`Failed to verify metadataHash signature for node ${nodeAccountId}!`);
      continue;
    }

    if (_.isEmpty(validatedSignatureFilesMap[fileHash])) {
      validatedSignatureFilesMap[fileHash] = [nodeAccountId];
    } else {
      validatedSignatureFilesMap[fileHash].push(nodeAccountId);
      const nodeCount = validatedSignatureFilesMap[fileHash].length;

      // update max. Sufficient to do here as you'd never want the max to occur in the if where the count would be 1
      if (nodeCount > maxHashCount) {
        maxHashCount = nodeCount;
        consensusHashMap.hashes = {
          fileHash,
          metadataHash,
        };
        consensusHashMap.count = maxHashCount;
      }
    }
  }

  // return hash if it was observed by a super majority
  return maxHashCount >= Math.ceil(Object.keys(nodePublicKeyMap).length / 3.0) ? consensusHashMap.hashes : null;
};

/**
 * compare the hash of data file with Hash which has been agreed on by valid signatures
 * @param recordFileHashes
 * @param consensusValidatedHashes
 * @returns {boolean}
 */
const validateRecordFileHash = (recordFileHashes, consensusValidatedHashes) => {
  const {fileHash: actualFileHash, metadataHash: actualMetadataHash} = recordFileHashes;
  const {fileHash, metadataHash} = consensusValidatedHashes;

  if (actualFileHash && actualFileHash.equals(fileHash)) {
    logger.info('fileHash of record file was successfully matched with signature files');
    return true;
  }

  if (actualMetadataHash && actualMetadataHash.equals(metadataHash)) {
    logger.info('metadataHash of record file was successfully matched with signature files');
    return true;
  }

  logger.error(
    `Hash mismatch: record file - ${JSON.stringify(recordFileHashes)}, signature files - ${JSON.stringify(
      consensusValidatedHashes
    )}`
  );
  return false;
};

/**
 * For signature files with the same file name:
 * (1) verify that the signature files are signed by corresponding node's PublicKey provided by addressBook
 * (2) For valid signature files, we compare their Hashes to see if at least 1/3 of hashes match.
 * (3) We compare the hash of data file with Hash which has been agreed on by valid signatures,
 * if match return true otherwise false for stateProof
 */
const performStateProof = (nodePublicKeyMap, signatureFileMap, recordFileHashes) => {
  const consensusValidatedHashes = verifySignatures(nodePublicKeyMap, signatureFileMap);
  if (_.isNull(consensusValidatedHashes)) {
    logger.error(`Unable to validate signature files!`);
    return false;
  }

  return validateRecordFileHash(recordFileHashes, consensusValidatedHashes);
};

export {performStateProof, validateRecordFileHash};
