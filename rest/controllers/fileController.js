// SPDX-License-Identifier: Apache-2.0

import EntityService from '../service/entityService';
import FileDataService from '../service/fileDataService';
import FileService from '../service/fileService';
import EntityId from '../entityId';
import * as utils from '../utils';
import {NotFoundError} from '../errors';
import * as constants from '../constants';

/**
 * Controller for file metadata endpoints
 */
const getFileById = async (req, res) => {
  // resolve the entity id (supports encoded id / shard.realm.num)
  const paramName = 'fileId';
  const encodedId = await EntityService.getEncodedId(req.params[paramName], true, paramName);
  // get metadata from entity table
  const meta = await FileService.getFileById(encodedId);

  // get latest file contents row for size and consensus timestamp
  const row = await FileDataService.getLatestFileDataContents(encodedId, {whereQuery: []});
  const sizeBytes =
    row && row.file_data
      ? Buffer.isBuffer(row.file_data)
        ? row.file_data.length
        : Buffer.byteLength(row.file_data)
      : 0;
  const consensusTimestamp = row ? utils.nsToSecNs(row.consensus_timestamp) : null;

  const fileIdString = EntityId.parse(encodedId).toString();

  const ret = {
    file_id: fileIdString,
    size_bytes: sizeBytes,
    consensus_timestamp: consensusTimestamp,
    memo: meta.memo,
    deleted: meta.deleted,
    expiry_timestamp: meta.expiry_timestamp,
    key: meta.key,
    created_timestamp: meta.created_timestamp,
  };

  res.locals[constants.responseDataLabel] = ret;
};

export default {
  getFileById,
};
