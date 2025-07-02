// SPDX-License-Identifier: Apache-2.0

import AddressBookServiceEndpointViewModel from './addressBookServiceEndpointViewModel';
import EntityId from '../entityId';
import * as utils from '../utils';

/**
 * Network node view model
 */
class NetworkNodeViewModel {
  /**
   * Constructs network node view model
   *
   * @param {NetworkNode} networkNode
   */
  constructor(networkNode) {
    const {addressBookEntry, nodeStake, node} = networkNode;
    this.admin_key = utils.encodeKey(node.adminKey);
    this.decline_reward = node.declineReward;
    this.description = addressBookEntry.description;
    this.file_id = EntityId.parse(networkNode.addressBook.fileId).toString();
    this.grpc_proxy_endpoint = node.grpcProxyEndpoint
      ? new AddressBookServiceEndpointViewModel(node.grpcProxyEndpoint)
      : null;
    this.max_stake = utils.asNullIfDefault(nodeStake.maxStake, -1);
    this.memo = addressBookEntry.memo;
    this.min_stake = utils.asNullIfDefault(nodeStake.minStake, -1);
    this.node_id = EntityId.parse(addressBookEntry.nodeId).num;
    this.node_account_id = EntityId.parse(addressBookEntry.nodeAccountId).toString();
    this.node_cert_hash = utils.addHexPrefix(utils.encodeUtf8(addressBookEntry.nodeCertHash), true);
    this.public_key = utils.addHexPrefix(addressBookEntry.publicKey, true);
    this.reward_rate_start = nodeStake.rewardRate;
    this.service_endpoints = networkNode.addressBookServiceEndpoints.map(
      (x) => new AddressBookServiceEndpointViewModel(x)
    );
    this.stake = nodeStake.stake;
    this.stake_not_rewarded = utils.asNullIfDefault(nodeStake.stakeNotRewarded, -1);
    this.stake_rewarded = nodeStake.stakeRewarded;
    this.staking_period = utils.getStakingPeriod(nodeStake.stakingPeriod);

    this.timestamp = {
      from: utils.nsToSecNs(networkNode.addressBook.startConsensusTimestamp),
      to: utils.nsToSecNs(networkNode.addressBook.endConsensusTimestamp),
    };
  }
}

export default NetworkNodeViewModel;
