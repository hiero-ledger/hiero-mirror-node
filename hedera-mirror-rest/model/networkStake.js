// SPDX-License-Identifier: Apache-2.0

class NetworkStake {
  static tableAlias = 'ns';
  static tableName = 'network_stake';
  static CONSENSUS_TIMESTAMP = `consensus_timestamp`;
  static EPOCH_DAY = `epoch_day`;
  static MAX_STAKE_REWARDED = `max_stake_rewarded`;
  static MAX_STAKING_REWARD_RATE_PER_HBAR = `max_staking_reward_rate_per_hbar`;
  static MAX_TOTAL_REWARD = `max_total_reward`;
  static NODE_REWARD_FEE_DENOMINATOR = `node_reward_fee_denominator`;
  static NODE_REWARD_FEE_NUMERATOR = `node_reward_fee_numerator`;
  static RESERVED_STAKING_REWARDS = `reserved_staking_rewards`;
  static REWARD_BALANCE_THRESHOLD = `reward_balance_threshold`;
  static STAKE_TOTAL = `stake_total`;
  static STAKING_PERIOD = `staking_period`;
  static STAKING_PERIOD_DURATION = `staking_period_duration`;
  static STAKING_PERIODS_STORED = `staking_periods_stored`;
  static STAKING_REWARD_FEE_DENOMINATOR = `staking_reward_fee_denominator`;
  static STAKING_REWARD_FEE_NUMERATOR = `staking_reward_fee_numerator`;
  static STAKING_REWARD_RATE = `staking_reward_rate`;
  static STAKING_START_THRESHOLD = `staking_start_threshold`;
  static UNRESERVED_STAKING_REWARD_BALANCE = `unreserved_staking_reward_balance`;

  /**
   * Parses node_stake table columns into object
   */
  constructor(networkStake) {
    this.maxStakeRewarded = networkStake.max_stake_rewarded;
    this.maxTotalReward = networkStake.max_total_reward;
    this.maxStakingRewardRatePerHbar = networkStake.max_staking_reward_rate_per_hbar;
    this.nodeRewardFeeDenominator = networkStake.node_reward_fee_denominator;
    this.nodeRewardFeeNumerator = networkStake.node_reward_fee_numerator;
    this.reservedStakingRewards = networkStake.reserved_staking_rewards;
    this.rewardBalanceThreshold = networkStake.reward_balance_threshold;
    this.stakeTotal = networkStake.stake_total;
    this.stakingPeriod = networkStake.staking_period;
    this.stakingPeriodDuration = networkStake.staking_period_duration;
    this.stakingPeriodsStored = networkStake.staking_periods_stored;
    this.stakingRewardFeeDenominator = networkStake.staking_reward_fee_denominator;
    this.stakingRewardFeeNumerator = networkStake.staking_reward_fee_numerator;
    this.stakingRewardRate = networkStake.staking_reward_rate;
    this.stakingStartThreshold = networkStake.staking_start_threshold;
    this.unreservedStakingRewardBalance = networkStake.unreserved_staking_reward_balance;
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

export default NetworkStake;
