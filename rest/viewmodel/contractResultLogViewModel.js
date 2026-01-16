// SPDX-License-Identifier: Apache-2.0

import EntityId from '../entityId';
import {filterKeys, ZERO_UINT256} from '../constants';
import {toHexString} from '../utils';

/**
 * Contract results log view model
 */
class ContractResultLogViewModel {
  /**
   * Constructs contractResultLogs view model
   *
   * @param {ContractLog} contractLog
   */
  constructor(contractLog, extended = true) {
    const contractId = EntityId.parse(contractLog.contractId, {paramName: filterKeys.CONTRACTID});
    Object.assign(this, {
      address: contractLog.evmAddress ? toHexString(contractLog.evmAddress, true) : contractId.toEvmAddress(),
      bloom: toHexString(contractLog.bloom, true),
      contract_id: contractId.toString(),
      data: toHexString(contractLog.data, true, 64),
      index: contractLog.index,
      topics: this._formatTopics([contractLog.topic0, contractLog.topic1, contractLog.topic2, contractLog.topic3]),
    });
  }

  _formatTopics(topics) {
    return topics
      .filter((topic) => topic !== null)
      .map((topic) => {
        const hex = toHexString(topic, true, 64);
        return hex === '0x' ? ZERO_UINT256 : hex;
      });
  }
}

export default ContractResultLogViewModel;
