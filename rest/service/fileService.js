// SPDX-License-Identifier: Apache-2.0

import BaseService from './baseService';
import {Entity} from '../model';
import * as utils from '../utils';
import {NotFoundError} from '../errors';

/**
 * File retrieval business logic
 */
class FileService extends BaseService {
  static fileByIdQuery = `select
    ${Entity.getFullName(Entity.ID)} as id,
    ${Entity.getFullName(Entity.MEMO)} as memo,
    ${Entity.getFullName(Entity.DELETED)} as deleted,
    ${Entity.getFullName(Entity.EXPIRATION_TIMESTAMP)} as expiration_timestamp,
    ${Entity.getFullName(Entity.AUTO_RENEW_PERIOD)} as auto_renew_period,
    ${Entity.getFullName(Entity.KEY)} as key,
    ${Entity.getFullName(Entity.CREATED_TIMESTAMP)} as created_timestamp
  from ${Entity.tableName} ${Entity.tableAlias}
  where ${Entity.getFullName(Entity.ID)} = $1 and ${Entity.getFullName(Entity.TYPE)} = 'FILE'`;

  async getFileById(id) {
    const row = await super.getSingleRow(FileService.fileByIdQuery, [id]);
    if (!row) {
      throw new NotFoundError();
    }

    // map and format
    return {
      file_id: id,
      memo: row.memo,
      deleted: row.deleted,
      expiry_timestamp: utils.calculateExpiryTimestamp(
        row.auto_renew_period,
        row.created_timestamp,
        row.expiration_timestamp
      ),
      key: utils.encodeKey(row.key),
      created_timestamp: utils.nsToSecNs(row.created_timestamp),
    };
  }
}

export default new FileService();
