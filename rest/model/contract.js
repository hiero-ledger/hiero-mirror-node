// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';

class Contract {
  static tableAlias = 'c';
  static tableName = 'contract';

  static FILE_ID = 'file_id';
  static ID = 'id';
  static INITCODE = 'initcode';
  static RUNTIME_BYTECODE = 'runtime_bytecode';

  /**
   * Parses contract table columns into object
   */
  constructor(contract) {
    Object.assign(
      this,
      _.mapKeys(contract, (v, k) => _.camelCase(k))
    );
  }

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

export default Contract;
