// SPDX-License-Identifier: Apache-2.0

import BaseService from './baseService';
import {StakingRewardTransfer} from '../model';
import {OrderSpec} from '../sql';

/**
 * Staking Reward Transfer retrieval business logic
 */
class StakingRewardTransferService extends BaseService {
  static listStakingRewardsByAccountIdQuery = `
    select ${StakingRewardTransfer.getFullName(StakingRewardTransfer.ACCOUNT_ID)},
    ${StakingRewardTransfer.getFullName(StakingRewardTransfer.AMOUNT)},
    ${StakingRewardTransfer.getFullName(StakingRewardTransfer.CONSENSUS_TIMESTAMP)}
    from ${StakingRewardTransfer.tableName} ${StakingRewardTransfer.tableAlias}
    where ${StakingRewardTransfer.getFullName(StakingRewardTransfer.ACCOUNT_ID)} = $1`;

  async getRewards(order, limit, conditions, initParams) {
    const {query, params} = this.getRewardsQuery(order, limit, conditions, initParams);
    const rows = await super.getRows(query, params);
    return rows.map((srt) => new StakingRewardTransfer(srt));
  }

  getRewardsQuery(order, limit, conditions, params) {
    const query = [
      StakingRewardTransferService.listStakingRewardsByAccountIdQuery,
      conditions.length > 0 ? `and ${conditions.join(' and ')}` : '', // "and" since we already have "where account_id = $1" at the end of the above line
      super.getOrderByQuery(
        OrderSpec.from(StakingRewardTransfer.getFullName(StakingRewardTransfer.CONSENSUS_TIMESTAMP), order)
      ),
      super.getLimitQuery(2), // limit is specified in $2 (not necessarily a limit *of* 2)
    ].join('\n');

    return {query, params};
  }
}

export default new StakingRewardTransferService();
