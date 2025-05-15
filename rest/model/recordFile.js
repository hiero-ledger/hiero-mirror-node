// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';

class RecordFile {
  /**
   * Parses record_file table columns into object
   */
  constructor(recordFile) {
    Object.assign(
      this,
      _.mapKeys(recordFile, (v, k) => _.camelCase(k))
    );
  }

  static tableAlias = 'rf';
  static tableName = 'record_file';

  static BYTES = 'bytes';
  static CONSENSUS_END = 'consensus_end';
  static CONSENSUS_START = 'consensus_start';
  static COUNT = 'count';
  static DIGEST_ALGORITHM = 'digest_algorithm';
  static FILE_HASH = 'file_hash';
  static GAS_USED = 'gas_used';
  static INDEX = 'index';
  static HAPI_VERSION_MAJOR = 'hapi_version_major';
  static HAPI_VERSION_MINOR = 'hapi_version_minor';
  static HAPI_VERSION_PATCH = 'hapi_version_patch';
  static HASH = 'hash';
  static LOAD_END = 'load_end';
  static LOAD_START = 'load_start';
  static LOGS_BLOOM = 'logs_bloom';
  static NAME = 'name';
  static NODE_ACCOUNT_ID = 'node_account_id';
  static PREV_HASH = 'prev_hash';
  static SIZE = 'size';
  static VERSION = 'version';

  /**
   * Gets full column name with table alias prepended.
   *
   * @param {string} columnName
   * @private
   */
  static getFullName(columnName) {
    return `${this.tableAlias}.${columnName}`;
  }

  getFullHapiVersion() {
    return this.hapiVersionMajor === null || this.hapiVersionMinor === null || this.hapiVersionPatch === null
      ? null
      : `${this.hapiVersionMajor}.${this.hapiVersionMinor}.${this.hapiVersionPatch}`;
  }
}

export default RecordFile;
