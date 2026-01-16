// SPDX-License-Identifier: Apache-2.0

class AssessedCustomFee {
  /**
   * Parses assessed_custom_fee table columns into object
   */
  constructor(assessedCustomFee) {
    this.amount = assessedCustomFee.amount;
    this.collectorAccountId = assessedCustomFee.collector_account_id;
    this.consensusTimestamp = assessedCustomFee.consensus_timestamp;
    this.effectivePayerAccountIds = assessedCustomFee.effective_payer_account_ids;
    this.tokenId = assessedCustomFee.token_id;
  }

  static tableAlias = 'acf';
  static tableName = 'assessed_custom_fee';

  static AMOUNT = `amount`;
  static COLLECTOR_ACCOUNT_ID = `collector_account_id`;
  static CONSENSUS_TIMESTAMP = `consensus_timestamp`;
  static EFFECTIVE_PAYER_ACCOUNT_IDS = `effective_payer_account_ids`;
  static PAYER_ACCOUNT_ID = 'payer_account_id';
  static TOKEN_ID = `token_id`;

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

export default AssessedCustomFee;
