// SPDX-License-Identifier: Apache-2.0

import EntityId from '../entityId.js';
import {nsToSecNs} from '../utils.js';

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
