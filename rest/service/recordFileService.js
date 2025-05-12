// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';

import BaseService from './baseService';
import config from '../config';
import {RecordFile} from '../model';
import {orderFilterValues} from '../constants';

const buildWhereSqlStatement = (whereQuery) => {
  let where = '';
  const params = [];
  for (let i = 1; i <= whereQuery.length; i++) {
    where += `${i === 1 ? 'where' : 'and'} ${whereQuery[i - 1].query} $${i} `;
    params.push(whereQuery[i - 1].param);
  }

  return {where, params};
};

/**
 * RecordFile retrieval business logic
 */
class RecordFileService extends BaseService {
  static recordFileBlockDetailsFromTimestampArrayQuery = `select
      ${RecordFile.CONSENSUS_END},
      ${RecordFile.CONSENSUS_START},
      ${RecordFile.INDEX},
      ${RecordFile.HASH},
      ${RecordFile.GAS_USED}
    from ${RecordFile.tableName}
    where ${RecordFile.CONSENSUS_END} in (
      select
       (
         select ${RecordFile.CONSENSUS_END}
         from ${RecordFile.tableName}
         where ${RecordFile.CONSENSUS_END} >= timestamp and
           ${RecordFile.CONSENSUS_END} >= $2 and
           ${RecordFile.CONSENSUS_END} <= $3
         order by ${RecordFile.CONSENSUS_END}
         limit 1
       ) as consensus_end
    from (select unnest($1::bigint[]) as timestamp) as tmp
      group by consensus_end
    ) and ${RecordFile.CONSENSUS_END} >= $2 and ${RecordFile.CONSENSUS_END} <= $3`;

  static recordFileBlockDetailsFromTimestampQuery = `select
    ${RecordFile.CONSENSUS_END}, ${RecordFile.GAS_USED}, ${RecordFile.HASH}, ${RecordFile.INDEX}
    from ${RecordFile.tableName}
    where  ${RecordFile.CONSENSUS_END} >= $1
    order by ${RecordFile.CONSENSUS_END}
    limit 1`;

  static recordFileBlockDetailsFromIndexQuery = `select
    ${RecordFile.CONSENSUS_START}, ${RecordFile.CONSENSUS_END}, ${RecordFile.HASH}, ${RecordFile.INDEX}
    from ${RecordFile.tableName}
    where  ${RecordFile.INDEX} = $1
    limit 1`;

  static recordFileBlockDetailsFromHashQuery = `select
    ${RecordFile.CONSENSUS_START}, ${RecordFile.CONSENSUS_END}, ${RecordFile.HASH}, ${RecordFile.INDEX}
    from ${RecordFile.tableName}
    where  ${RecordFile.HASH} like $1
    limit 1`;

  static blocksQuery = `select
    ${RecordFile.COUNT}, ${RecordFile.HASH}, ${RecordFile.NAME}, ${RecordFile.PREV_HASH},
    ${RecordFile.HAPI_VERSION_MAJOR}, ${RecordFile.HAPI_VERSION_MINOR}, ${RecordFile.HAPI_VERSION_PATCH},
    ${RecordFile.INDEX}, ${RecordFile.CONSENSUS_START}, ${RecordFile.CONSENSUS_END}, ${RecordFile.GAS_USED},
    ${RecordFile.LOGS_BLOOM}, coalesce(${RecordFile.SIZE}, length(${RecordFile.BYTES})) as size
    from ${RecordFile.tableName}
  `;

  /**
   * Retrieves the recordFile containing the transaction of the given timestamp
   *
   * @param {string|Number|BigInt} timestamp consensus timestamp
   * @return {Promise<RecordFile>} recordFile subset
   */
  async getRecordFileBlockDetailsFromTimestamp(timestamp) {
    const row = await super.getSingleRow(RecordFileService.recordFileBlockDetailsFromTimestampQuery, [timestamp]);

    return _.isNull(row) ? null : new RecordFile(row);
  }

  /**
   * Retrieves the recordFiles containing the transactions of the given timestamps
   *
   * The timestamps must be ordered, either ACS or DESC.
   *
   * @param {(string|Number|BigInt)[]} timestamps consensus timestamp array
   * @return {Promise<Map>} A map from the consensus timestamp to its record file
   */
  async getRecordFileBlockDetailsFromTimestampArray(timestamps) {
    const recordFileMap = new Map();
    if (timestamps.length === 0) {
      return recordFileMap;
    }

    const {maxTimestamp, minTimestamp, order} = this.getTimestampArrayContext(timestamps);
    const query = `${RecordFileService.recordFileBlockDetailsFromTimestampArrayQuery}
      order by consensus_end ${order}`;
    const params = [timestamps, minTimestamp, BigInt(maxTimestamp) + config.query.maxRecordFileCloseIntervalNs];

    const rows = await super.getRows(query, params);

    let index = 0;
    for (const row of rows) {
      const recordFile = new RecordFile(row);
      const {consensusEnd, consensusStart} = recordFile;
      for (; index < timestamps.length; index++) {
        const timestamp = timestamps[index];
        if (consensusStart <= timestamp && consensusEnd >= timestamp) {
          recordFileMap.set(timestamp, recordFile);
        } else if (
          (order === orderFilterValues.ASC && timestamp > consensusEnd) ||
          (order === orderFilterValues.DESC && timestamp < consensusStart)
        ) {
          break;
        }
      }
    }

    return recordFileMap;
  }

  /**
   * Retrieves the recordFile with the given index
   *
   * @param {number} index Int8
   * @return {Promise<RecordFile>} recordFile subset
   */
  async getRecordFileBlockDetailsFromIndex(index) {
    const row = await super.getSingleRow(RecordFileService.recordFileBlockDetailsFromIndexQuery, [index]);

    return _.isNull(row) ? null : new RecordFile(row);
  }

  /**
   * Retrieves the recordFile with the given index
   *
   * @param {string} hash
   * @return {Promise<RecordFile>} recordFile subset
   */
  async getRecordFileBlockDetailsFromHash(hash) {
    const row = await super.getSingleRow(RecordFileService.recordFileBlockDetailsFromHashQuery, [`${hash}%`]);

    return _.isNull(row) ? null : new RecordFile(row);
  }

  async getBlocks(filters) {
    const {where, params} = buildWhereSqlStatement(filters.whereQuery);

    const query =
      RecordFileService.blocksQuery +
      `
      ${where}
      order by ${filters.orderBy} ${filters.order}
      limit ${filters.limit}
    `;

    const rows = await super.getRows(query, params);
    return rows.map((recordFile) => new RecordFile(recordFile));
  }

  async getByHashOrNumber(hash, number) {
    let whereStatement = '';
    const params = [];
    if (hash) {
      hash = hash.toLowerCase();
      whereStatement += `${RecordFile.HASH} like $1`;
      params.push(hash + '%');
    } else {
      whereStatement += `${RecordFile.INDEX} = $1`;
      params.push(number);
    }

    const query = `${RecordFileService.blocksQuery} where ${whereStatement}`;
    const row = await super.getSingleRow(query, params);
    return row ? new RecordFile(row) : null;
  }

  /**
   * Gets the timestamp context from a sorted timestamp array. Note the timestamps array must not be empty.
   *
   * @param timestamps
   * @returns {{maxTimestamp: *, minTimestamp: *, order: string}
   */
  getTimestampArrayContext(timestamps) {
    const first = timestamps[0];
    const last = timestamps[timestamps.length - 1];
    return first > last
      ? {
          maxTimestamp: first,
          minTimestamp: last,
          order: orderFilterValues.DESC,
        }
      : {
          maxTimestamp: last,
          minTimestamp: first,
          order: orderFilterValues.ASC,
        };
  }

  pool() {
    return primaryPool;
  }
}

export default new RecordFileService();
