// SPDX-License-Identifier: Apache-2.0

import camelCase from 'lodash/camelCase';
import mapKeys from 'lodash/mapKeys';

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
      mapKeys(contract, (v, k) => camelCase(k))
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
