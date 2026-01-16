// SPDX-License-Identifier: Apache-2.0

import EntityId from '../entityId';
import {nsToSecNs} from '../utils';

/**
 * Staking Reward Transfer view model
 */
class StakingRewardTransferViewModel {
  constructor(stakingRewardTransferModel) {
    this.account_id = EntityId.parse(stakingRewardTransferModel.accountId).toString();
    this.amount = stakingRewardTransferModel.amount;
    this.timestamp = nsToSecNs(stakingRewardTransferModel.consensusTimestamp);
  }
}

export default StakingRewardTransferViewModel;
