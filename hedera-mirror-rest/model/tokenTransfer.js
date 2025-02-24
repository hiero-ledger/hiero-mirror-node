// SPDX-License-Identifier: Apache-2.0

class TokenTransfer {
  /**
   * Parses token_transfer table columns into object
   */
  constructor(tokenTransfer) {
    this.accountId = tokenTransfer.account_id;
    this.amount = tokenTransfer.amount;
    this.consensusTimestamp = tokenTransfer.consensus_timestamp;
    this.isApproval = tokenTransfer.is_approval;
    this.tokenId = tokenTransfer.token_id;
  }

  static tableAlias = 'tk_tr';
  static tableName = 'token_transfer';

  static ACCOUNT_ID = 'account_id';
  static AMOUNT = 'amount';
  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';
  static IS_APPROVAL = `is_approval`;
  static PAYER_ACCOUNT_ID = 'payer_account_id';
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

export default TokenTransfer;
