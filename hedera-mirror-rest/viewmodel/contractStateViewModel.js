// SPDX-License-Identifier: Apache-2.0

import {filterKeys} from '../constants.js';
import EntityId from '../entityId.js';
import {toHexString, nsToSecNs, toUint256} from '../utils.js';

/**
 * Contract result state view model
 */
class ContractStateViewModel {
  /**
   * Constructs contractResultStates view model
   *
   * @param {ContractState} contractState
   */
  constructor(contractState) {
    const contractId = EntityId.parse(contractState.contractId, {paramName: filterKeys.CONTRACTID});
    this.address = contractState?.evmAddress?.length
      ? toHexString(contractState.evmAddress, true)
      : contractId.toEvmAddress();
    this.contract_id = contractId.toString();
    this.timestamp = nsToSecNs(contractState.modifiedTimestamp);
    this.slot = toUint256(contractState.slot);
    this.value = toUint256(contractState.value);
  }
}

export default ContractStateViewModel;
