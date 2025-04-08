// SPDX-License-Identifier: Apache-2.0

import * as utils from '../utils.js';
import * as math from 'mathjs';

/**
 * Network stake view model
 */
class NetworkStakeViewModel {
  /**
   * Constructs network stake view model
   *
   * @param {NetworkStake} networkStake
   */
  constructor(networkStake) {
    this.max_stake_rewarded = networkStake.maxStakeRewarded;
    this.max_staking_reward_rate_per_hbar = networkStake.maxStakingRewardRatePerHbar;
    this.max_total_reward = networkStake.maxTotalReward;
    this.node_reward_fee_fraction = this.calculateFeeFraction(
      networkStake.nodeRewardFeeNumerator,
      networkStake.nodeRewardFeeDenominator
    );
    this.reserved_staking_rewards = networkStake.reservedStakingRewards;
    this.reward_balance_threshold = networkStake.rewardBalanceThreshold;
    this.stake_total = networkStake.stakeTotal;
    this.staking_period = utils.getStakingPeriod(networkStake.stakingPeriod);
    this.staking_period_duration = networkStake.stakingPeriodDuration;
    this.staking_periods_stored = networkStake.stakingPeriodsStored;
    this.staking_reward_fee_fraction = this.calculateFeeFraction(
      networkStake.stakingRewardFeeNumerator,
      networkStake.stakingRewardFeeDenominator
    );
    this.staking_reward_rate = networkStake.stakingRewardRate;
    this.staking_start_threshold = networkStake.stakingStartThreshold;
    this.unreserved_staking_reward_balance = networkStake.unreservedStakingRewardBalance;
  }

  calculateFeeFraction(feeNumerator, feeDenominator) {
    return feeDenominator !== 0
      ? math.divide(math.bignumber(feeNumerator), math.bignumber(feeDenominator)).toNumber()
      : 0;
  }
}

export default NetworkStakeViewModel;
