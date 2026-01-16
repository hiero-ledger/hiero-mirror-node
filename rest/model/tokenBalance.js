// SPDX-License-Identifier: Apache-2.0

class TokenBalance {
  static tableAlias = 'tb';
  static tableName = 'token_balance';
  static TOKEN_ID = `token_id`;
  static ACCOUNT_ID = `account_id`;
  static BALANCE = 'balance';
  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';

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

export default TokenBalance;
