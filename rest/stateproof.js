// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';
import {gunzipSync} from 'zlib';
import {NoSuchKey} from '@aws-sdk/client-s3';

import config from './config';
import * as constants from './constants';
import EntityId from './entityId';
import {DbError, FileDownloadError, NotFoundError} from './errors';
import s3client from './s3client';
import {CompositeRecordFile} from './stream';
import TransactionId from './transactionId';
import * as utils from './utils';

const fileQuery = `select file_data,
                         node_count,
                         string_agg(memo, ',')                                 as memos,
                         string_agg(cast(abe.node_account_id as varchar), ',') as node_account_ids
                  from address_book ab
                           left join address_book_entry abe
                                     on ab.start_consensus_timestamp = abe.consensus_timestamp
                  where start_consensus_timestamp <= $1
                    and file_id = 102
                  group by start_consensus_timestamp
                  order by start_consensus_timestamp`;

const recordFileSuffixRegex = /\.rcd(\.gz)?$/;
const recordFileQuery = `select bytes, name, node_account_id, version
                    from record_file rf
                             join address_book ab
                                  on (ab.end_consensus_timestamp is null and
                                      ab.start_consensus_timestamp <
                                      rf.consensus_start) or
                                     (ab.start_consensus_timestamp <
                                      rf.consensus_start and
                                      rf.consensus_start <=
                                      ab.end_consensus_timestamp)
                             join address_book_entry abe
                                  on rf.node_id = abe.node_id and
                                     abe.consensus_timestamp =
                                     ab.start_consensus_timestamp
                    where consensus_end >= $1 and consensus_end <= $2
                    order by consensus_end
                    limit 1`;

const transactionIdQuery = `select consensus_timestamp
                    from transaction
                    where payer_account_id = $1
                      and valid_start_ns = $2
                      and nonce = $3
                      and scheduled = $4
                      and result = 22
                      and consensus_timestamp >= $2
                      and consensus_timestamp <= $5`;

// lazy create s3client singleton
const getS3Client = (() => {
  let client;
  return () => {
    if (client === undefined) {
      client = s3client.createS3Client();
    }
    return client;
  };
})();

/**
 * Get the consensus_timestamp of the transaction. Throws exception if no such successful transaction found or multiple such
 * transactions found.
 * @param {TransactionId} transactionId
 * @param {Number} nonce
 * @param {Boolean} scheduled
 * @returns {Promise<BigInt>} consensus_timestamp of the successful transaction if found
 */
const getSuccessfulTransactionConsensusTimestamp = async (transactionId, nonce, scheduled) => {
  const validStart = BigInt(transactionId.getValidStartNs());
  const maxConsensusTimestamp = validStart + config.query.maxTransactionConsensusTimestampRangeNs;
  const sqlParams = [transactionId.getEntityId().getEncodedId(), validStart, nonce, scheduled, maxConsensusTimestamp];
  const {rows} = await pool.queryQuietly(transactionIdQuery, sqlParams);

  if (_.isEmpty(rows)) {
    throw new NotFoundError('Transaction not found');
  } else if (rows.length > 1) {
    throw new DbError('Invalid state, more than one transaction found');
  }

  return _.first(rows).consensus_timestamp;
};

/**
 * Get the RCD file name, raw bytes if available, and the account ID of the node it was downloaded from, where
 * consensusTimestamp is in the range [consensus_start, consensus_end]. Throws exception if no such RCD file found.
 *
 * @param {BigInt} consensusTimestamp consensus timestamp within the range of the RCD file to search
 * @returns {Promise<{Buffer, String, String}>} RCD file name, raw bytes, and the account ID of the node the file was
 *                                              downloaded from
 */
const getRCDFileInfoByConsensusTimestamp = async (consensusTimestamp) => {
  const upperBound = consensusTimestamp + config.query.maxRecordFileCloseIntervalNs;
  const {rows} = await primaryPool.queryQuietly(recordFileQuery, [consensusTimestamp, upperBound]);
  if (_.isEmpty(rows)) {
    throw new NotFoundError(`No matching RCD file found with ${consensusTimestamp} in the range`);
  }

  const info = rows[0];
  logger.debug(`Found RCD file ${info.name} for consensus timestamp ${consensusTimestamp}`);
  return {
    bytes: info.bytes,
    name: info.name,
    nodeAccountId: EntityId.parse(info.node_account_id).toString(),
    version: info.version,
  };
};

/**
 * Get the chain of address books and node account IDs at or before consensusTimestamp.
 * @param {BigInt} consensusTimestamp
 * @returns {Promise<Object>} List of base64 address book data in chronological order and list of node account IDs.
 */
const getAddressBooksAndNodeAccountIdsByConsensusTimestamp = async (consensusTimestamp) => {
  // Get the chain of address books whose start_consensus_timestamp <= consensusTimestamp, also aggregate the corresponding
  // memo and node account ids from table address_book_entry
  let sqlQuery = fileQuery;

  if (!config.stateproof.addressBookHistory) {
    sqlQuery += ` desc limit 1`;
  }

  const {rows} = await primaryPool.queryQuietly(sqlQuery, consensusTimestamp);
  if (_.isEmpty(rows)) {
    throw new NotFoundError('No address book found');
  }

  const lastAddressBook = _.last(rows);
  let nodeAccountIds = [];
  if (lastAddressBook.node_account_ids) {
    nodeAccountIds = _.map(lastAddressBook.node_account_ids.split(','), (id) => EntityId.parse(id).toString());
  } else if (lastAddressBook.memos) {
    nodeAccountIds = lastAddressBook.memos.split(',');
  }

  if (nodeAccountIds.length !== parseInt(lastAddressBook.node_count, 10)) {
    throw new DbError('Number of nodes found mismatch node_count in latest address book');
  }

  logger.debug(`Found the list of nodes "${nodeAccountIds}" for consensus timestamp ${consensusTimestamp}`);
  const addressBooks = _.map(rows, (row) => Buffer.from(row.file_data).toString('base64'));
  return {
    addressBooks,
    nodeAccountIds,
  };
};

/**
 * Download the file objects (record stream file and signature files) from object storage service
 * @param partialFilePaths list of partial file path to download. partial file path is the path with bucket name and
 *                         record stream prefix stripped.
 * @returns {Promise<Array>} Array of file buffers
 */
const downloadRecordStreamFilesFromObjectStorage = async (...partialFilePaths) => {
  const {bucketName} = config.stateproof.streams;
  const s3Client = getS3Client();

  const fileObjects = await Promise.all(
    _.map(partialFilePaths, async (partialFilePath) => {
      const params = {
        Bucket: bucketName,
        Key: `${constants.recordStreamPrefix}${partialFilePath}`,
        RequestPayer: 'requester',
      };

      try {
        const response = await s3Client.getObject(params);
        const data = Buffer.from(await response.Body.transformToByteArray());
        return {
          partialFilePath,
          data,
        };
      } catch (error) {
        if (error instanceof NoSuchKey) {
          logger.warn(`Failed to download ${utils.JSONStringify(params)}`, error.message);
        } else {
          logger.error(`Failed to download ${utils.JSONStringify(params)}`, error);
        }

        return {
          error,
          partialFilePath,
        };
      }
    })
  );

  const downloaded = _.filter(fileObjects, (fileObject) => !fileObject.error);
  logger.debug(
    `Downloaded ${downloaded.length} file objects from bucket ${bucketName}: ${_.map(
      downloaded,
      (fileObj) => fileObj.partialFilePath
    )}`
  );
  return downloaded;
};

/**
 * Check if consensus can be reached given actualCount and totalCount.
 * @param {Number} actualCount
 * @param {Number} totalCount
 * @returns {boolean} if consensus can be reached
 */
const canReachConsensus = (actualCount, totalCount) => actualCount >= Math.ceil(totalCount / 3.0);

/**
 * Get the value of nonce and scheduled from query filters.
 * @param {Array} filters
 * @returns {{nonce: number, scheduled: boolean}}
 */
const getQueryParamValues = (filters) => {
  const ret = {nonce: 0, scheduled: false}; // the default
  for (const filter of filters) {
    switch (filter.key) {
      case constants.filterKeys.NONCE:
        ret.nonce = filter.value;
        break;
      case constants.filterKeys.SCHEDULED:
        ret.scheduled = filter.value;
        break;
      default:
        break;
    }
  }

  return ret;
};

/**
 * Formats the compactable record file. The compact object's keys are in snake case, and values are base64 encoded
 * strings.
 *
 * @param recordFile
 * @param transactionId
 * @param nonce
 * @param scheduled
 * @return {{}}
 */
const formatCompactableRecordFile = (recordFile, transactionId, nonce, scheduled) => {
  const base64Encode = (obj) => {
    for (const [k, v] of Object.entries(obj)) {
      if (Buffer.isBuffer(v)) {
        obj[k] = v.toString('base64');
      } else if (Array.isArray(v)) {
        obj[k] = v.map((b) => b.toString('base64'));
      }
    }

    return obj;
  };

  const compactObject = recordFile.toCompactObject(transactionId, nonce, scheduled);
  return _.mapKeys(base64Encode(compactObject), (v, k) => _.snakeCase(k));
};

/**
 * Formats the record file in either the full format or the compact format depending on its version.
 *
 * @param {Buffer} data
 * @param {TransactionId} transactionId
 * @param {Number} nonce
 * @param {boolean} scheduled
 * @returns {string|Object}
 */
const formatRecordFile = (data, transactionId, nonce, scheduled) => {
  if (!CompositeRecordFile.canCompact(data)) {
    return data.toString('base64');
  }

  return formatCompactableRecordFile(new CompositeRecordFile(data), transactionId, nonce, scheduled);
};

/**
 * Handler function for /transactions/:transactionId/stateproof API.
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @returns none
 */
const getStateProofForTransaction = async (req, res) => {
  const filters = utils.buildAndValidateFilters(req.query, acceptedStateProofParameters);

  const transactionId = TransactionId.fromString(req.params.transactionId);
  const {nonce, scheduled} = getQueryParamValues(filters);
  const consensusTimestamp = await getSuccessfulTransactionConsensusTimestamp(transactionId, nonce, scheduled);
  const rcdFileInfo = await getRCDFileInfoByConsensusTimestamp(consensusTimestamp);
  const {addressBooks, nodeAccountIds} = await getAddressBooksAndNodeAccountIdsByConsensusTimestamp(consensusTimestamp);

  const sigFilename = rcdFileInfo.name.replace(recordFileSuffixRegex, '.rcd_sig');
  const sigFileObjects = await downloadRecordStreamFilesFromObjectStorage(
    ..._.map(nodeAccountIds, (accountId) => `${accountId}/${sigFilename}`)
  );

  if (!canReachConsensus(sigFileObjects.length, nodeAccountIds.length)) {
    throw new FileDownloadError(
      `Require at least 1/3 signature files to prove consensus, got ${sigFileObjects.length}` +
        ` out of ${nodeAccountIds.length} for file ${sigFilename}`
    );
  }

  // download the record file from the stored node if it's not in db
  let fileData = rcdFileInfo.bytes;
  if (!fileData) {
    const partialPath = `${rcdFileInfo.nodeAccountId}/${rcdFileInfo.name}`;
    const rcdFileObjects = await downloadRecordStreamFilesFromObjectStorage(partialPath);
    if (_.isEmpty(rcdFileObjects)) {
      throw new FileDownloadError(
        `Failed to download record file ${rcdFileInfo.name} from node ${rcdFileInfo.nodeAccountId}`
      );
    }
    fileData = _.first(rcdFileObjects).data;
  }

  const rcdFile = rcdFileInfo.version !== 6 ? fileData : gunzipSync(fileData);

  const sigFilesMap = {};
  _.forEach(sigFileObjects, (sigFileObject) => {
    const nodeAccountIdStr = _.first(sigFileObject.partialFilePath.split('/'));
    sigFilesMap[nodeAccountIdStr] = sigFileObject.data.toString('base64');
  });

  res.locals[constants.responseDataLabel] = {
    address_books: addressBooks,
    record_file: formatRecordFile(rcdFile, transactionId, nonce, scheduled),
    signature_files: sigFilesMap,
    version: rcdFileInfo.version,
  };
};

const stateproof = {
  getStateProofForTransaction,
};

const acceptedStateProofParameters = new Set([constants.filterKeys.NONCE, constants.filterKeys.SCHEDULED]);

if (utils.isTestEnv()) {
  Object.assign(stateproof, {
    canReachConsensus,
    downloadRecordStreamFilesFromObjectStorage,
    formatCompactableRecordFile,
    getAddressBooksAndNodeAccountIdsByConsensusTimestamp,
    getQueryParamValues,
    getRCDFileInfoByConsensusTimestamp,
    getSuccessfulTransactionConsensusTimestamp,
  });
}

export default stateproof;
