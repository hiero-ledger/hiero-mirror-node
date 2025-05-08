// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';

class FileData {
  /**
   * Parses file_data table columns into object
   */
  constructor(fileData) {
    Object.assign(
      this,
      _.mapKeys(fileData, (v, k) => _.camelCase(k))
    );
  }

  static tableAlias = 'f';
  static tableName = 'file_data';

  static FILE_DATA = 'file_data';
  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';
  static ENTITY_ID = 'entity_id';
  static TRANSACTION_TYPE = 'transaction_type';

  /**
   * Gets full column name with table alias prepended.
   *
   * @param {string} columnName
   * @private
   */
  static getFullName(columnName) {
    return `${this.tableAlias}.${columnName}`;
  }
}

export default FileData;
