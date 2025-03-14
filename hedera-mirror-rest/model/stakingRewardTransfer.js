// SPDX-License-Identifier: Apache-2.0

class StakingRewardTransfer {
  /**
   * Parses staking_reward_transfer table columns into object
   */
  constructor(stakingRewardTransfer) {
    this.accountId = stakingRewardTransfer.account_id;
    this.amount = stakingRewardTransfer.amount;
    this.consensusTimestamp = stakingRewardTransfer.consensus_timestamp;
    this.payerAccountId = stakingRewardTransfer.payer_account_id;
  }

  static tableAlias = 'srt';
  static tableName = 'staking_reward_transfer';

  static ACCOUNT_ID = 'account_id';
  static AMOUNT = 'amount';
  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';
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

export default StakingRewardTransfer;
