// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';

class TokenAllowance {
  static historyTableName = 'token_allowance_history';
  static tableAlias = 'ta';
  static tableName = 'token_allowance';
  static AMOUNT = 'amount';
  static AMOUNT_GRANTED = 'amount_granted';
  static OWNER = 'owner';
  static PAYER_ACCOUNT_ID = 'payer_account_id';
  static SPENDER = 'spender';
  static TIMESTAMP_RANGE = 'timestamp_range';
  static TOKEN_ID = 'token_id';

  /**
   * Parses token_allowance table columns into object
   */
  constructor(tokenAllowance) {
    Object.assign(
      this,
      _.mapKeys(tokenAllowance, (v, k) => _.camelCase(k))
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

export default TokenAllowance;
