// SPDX-License-Identifier: Apache-2.0

class CryptoTransfer {
  /**
   * Parses crypto_transfer table columns into object
   */
  constructor(cryptoTransfer) {
    this.amount = cryptoTransfer.amount;
    this.consensusTimestamp = cryptoTransfer.consensus_timestamp;
    this.entityId = cryptoTransfer.entity_id;
    this.isApproval = cryptoTransfer.is_approval;
  }

  static tableAlias = 'ctr';
  static tableName = 'crypto_transfer';

  static AMOUNT = 'amount';
  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';
  static ENTITY_ID = 'entity_id';
  static IS_APPROVAL = 'is_approval';
  static PAYER_ACCOUNT_ID = 'payer_account_id';

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

export default CryptoTransfer;
