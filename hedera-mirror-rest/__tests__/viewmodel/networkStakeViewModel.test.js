// SPDX-License-Identifier: Apache-2.0

import {NetworkStakeViewModel} from '../../viewmodel';

describe('NetworkStakeViewModel', () => {
  const defaultNetworkStake = {
    maxStakeRewarded: 10,
    maxStakingRewardRatePerHbar: 17808,
    maxTotalReward: 20,
    nodeRewardFeeDenominator: 0,
    nodeRewardFeeNumerator: 100,
    reservedStakingRewards: 30,
    rewardBalanceThreshold: 40,
    stakeTotal: '35000000000000000',
    stakingPeriod: '1654991999999999999',
    stakingPeriodDuration: 1440,
    stakingPeriodsStored: 365,
    stakingRewardFeeDenominator: 100,
    stakingRewardFeeNumerator: 10,
    stakingRewardRate: '100000000000',
    stakingStartThreshold: '25000000000000000',
    unreservedStakingRewardBalance: 50,
  };

  const defaultExpected = {
    max_stake_rewarded: 10,
    max_staking_reward_rate_per_hbar: 17808,
    max_total_reward: 20,
    node_reward_fee_fraction: 0.0,
    reserved_staking_rewards: 30,
    reward_balance_threshold: 40,
    stake_total: '35000000000000000',
    staking_period: {
      from: '1654992000.000000000',
      to: '1655078400.000000000',
    },
    staking_period_duration: 1440,
    staking_periods_stored: 365,
    staking_reward_fee_fraction: 0.1,
    staking_reward_rate: '100000000000',
    staking_start_threshold: '25000000000000000',
    unreserved_staking_reward_balance: 50,
  };

  test('default', () => {
    expect(new NetworkStakeViewModel(defaultNetworkStake)).toEqual(defaultExpected);
  });

  test('null fields', () => {
    expect(
      new NetworkStakeViewModel({
        maxStakeRewarded: null,
        maxStakingRewardRatePerHbar: null,
        maxTotalReward: null,
        nodeRewardFeeDenominator: 0,
        nodeRewardFeeNumerator: 0,
        reservedStakingRewards: null,
        rewardBalanceThreshold: null,
        stakeTotal: null,
        stakingPeriod: null,
        stakingPeriodDuration: null,
        stakingPeriodsStored: null,
        stakingRewardFeeDenominator: 0,
        stakingRewardFeeNumerator: 0,
        stakingRewardRate: null,
        stakingStartThreshold: null,
        unreservedStakingRewardBalance: null,
      })
    ).toEqual({
      max_stake_rewarded: null,
      max_staking_reward_rate_per_hbar: null,
      max_total_reward: null,
      node_reward_fee_fraction: 0.0,
      reserved_staking_rewards: null,
      reward_balance_threshold: null,
      stake_total: null,
      staking_period: null,
      staking_period_duration: null,
      staking_periods_stored: null,
      staking_reward_fee_fraction: 0.0,
      staking_reward_rate: null,
      staking_start_threshold: null,
      unreserved_staking_reward_balance: null,
    });
  });
});
