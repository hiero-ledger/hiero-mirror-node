// SPDX-License-Identifier: Apache-2.0

class Nft {
  /**
   * Parses nft table columns into object
   */
  constructor(nft) {
    this.accountId = nft.account_id;
    this.createdTimestamp = nft.created_timestamp;
    this.delegatingSpender = nft.delegating_spender;
    this.deleted = nft.deleted;
    this.metadata = nft.metadata;
    this.serialNumber = nft.serial_number;
    this.spender = nft.spender;
    this.timestampRange = nft.timestamp_range;
    this.tokenId = nft.token_id;
  }

  static tableAlias = 'nft';
  static tableName = this.tableAlias;

  static ACCOUNT_ID = 'account_id';
  static CREATED_TIMESTAMP = 'created_timestamp';
  static DELEGATING_SPENDER = 'delegating_spender';
  static DELETED = 'deleted';
  static METADATA = 'metadata';
  static SERIAL_NUMBER = 'serial_number';
  static SPENDER = 'spender';
  static TIMESTAMP_RANGE = 'timestamp_range';
  static TOKEN_ID = 'token_id';

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

export default Nft;
