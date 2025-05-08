// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';

class ContractStateChange {
  /**
   * Parses contract_state_change table columns into object
   */
  constructor(contractStateChange) {
    Object.assign(
      this,
      _.mapKeys(contractStateChange, (v, k) => _.camelCase(k))
    );
  }

  static tableAlias = 'csc';
  static tableName = 'contract_state_change';

  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';
  static CONTRACT_ID = 'contract_id';
  static MIGRATION = 'migration';
  static PAYER_ACCOUNT_ID = 'payer_account_id';
  static SLOT = 'slot';
  static VALUE_READ = 'value_read';
  static VALUE_WRITTEN = 'value_written';

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

export default ContractStateChange;
