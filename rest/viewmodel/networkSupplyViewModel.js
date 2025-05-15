// SPDX-License-Identifier: Apache-2.0

import {nsToSecNs} from '../utils';

/**
 * Network supply view model
 */
class NetworkSupplyViewModel {
  static totalSupply = 5000000000000000000n;

  /**
   * Constructs network supply view model
   *
   * @param {Object} networkSupply
   */
  constructor(networkSupply) {
    const unreleasedSupply = BigInt(networkSupply.unreleased_supply);
    const releasedSupply = NetworkSupplyViewModel.totalSupply - unreleasedSupply;

    // Convert numbers to string since Express doesn't support BigInt
    this.released_supply = `${releasedSupply}`;
    this.timestamp = nsToSecNs(networkSupply.consensus_timestamp);
    this.total_supply = `${NetworkSupplyViewModel.totalSupply}`;
  }
}

export default NetworkSupplyViewModel;
