// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';

class CryptoAllowance {
  static historyTableName = 'crypto_allowance_history';
  static tableAlias = 'ca';
  static tableName = 'crypto_allowance';
  static AMOUNT = 'amount';
  static AMOUNT_GRANTED = 'amount_granted';
  static OWNER = 'owner';
  static PAYER_ACCOUNT_ID = 'payer_account_id';
  static SPENDER = 'spender';
  static TIMESTAMP_RANGE = 'timestamp_range';

  /**
   * Parses crypto_allowance table columns into object
   */
  constructor(cryptoAllowance) {
    Object.assign(
      this,
      _.mapKeys(cryptoAllowance, (v, k) => _.camelCase(k))
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

export default CryptoAllowance;
