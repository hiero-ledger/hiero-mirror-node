// SPDX-License-Identifier: Apache-2.0

class TransactionHash {
  static tableAlias = 'th';
  static tableName = 'transaction_hash';
  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';
  static HASH = 'hash';
  static PAYER_ACCOUNT_ID = 'payer_account_id';

  /**
   * Parses transaction_hash table columns into object
   */
  constructor(transactionHash) {
    this.consensusTimestamp = transactionHash.consensus_timestamp;
    this.hash = transactionHash.hash;
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

export default TransactionHash;
