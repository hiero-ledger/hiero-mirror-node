// SPDX-License-Identifier: Apache-2.0

import camelCase from 'lodash/camelCase';
import mapKeys from 'lodash/mapKeys';

class ContractState {
  /**
   * Parses contract_state table columns into object
   */
  constructor(contractState) {
    Object.assign(
      this,
      mapKeys(contractState, (v, k) => camelCase(k))
    );
  }

  static tableAlias = 'cs';
  static tableName = 'contract_state';

  static CREATED_TIMESTAMP = 'created_timestamp';
  static MODIFIED_TIMESTAMP = 'modified_timestamp';
  static CONTRACT_ID = 'contract_id';
  static SLOT = 'slot';
  static VALUE = 'value';

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

export default ContractState;
