// SPDX-License-Identifier: Apache-2.0

import {filterKeys} from '../constants';
import EntityId from '../entityId';
import {toHexString, toUint256} from '../utils';

/**
 * Contract result state change view model
 */
class ContractResultStateChangeViewModel {
  /**
   * Constructs contractResultStateChanges view model
   *
   * @param {ContractStateChange} contractStateChange
   */
  constructor(contractStateChange) {
    const contractId = EntityId.parse(contractStateChange.contractId, {paramName: filterKeys.CONTRACTID});
    this.address = contractStateChange?.evmAddress?.length
      ? toHexString(contractStateChange.evmAddress, true)
      : contractId.toEvmAddress();
    this.contract_id = contractId.toString();
    this.slot = toUint256(contractStateChange.slot);
    this.value_read = toUint256(contractStateChange.valueRead);
    this.value_written = toUint256(contractStateChange.valueWritten);
  }
}

export default ContractResultStateChangeViewModel;
