// SPDX-License-Identifier: Apache-2.0

import ContractLogResultsViewModel from './contractResultLogViewModel';
import EntityId from '../entityId';
import {addHexPrefix, nsToSecNs, toHexStringNonQuantity} from '../utils';

/**
 * Contract log view model
 */
class ContractLogViewModel extends ContractLogResultsViewModel {
  /**
   * Constructs contractLog view model
   *
   * @param {ContractLog} contractLog
   */
  constructor(contractLog) {
    super(contractLog);
    Object.assign(this, {
      block_hash: addHexPrefix(contractLog.blockHash),
      block_number: contractLog.blockNumber,
      root_contract_id: EntityId.parse(contractLog.rootContractId, {isNullable: true}).toString(),
      timestamp: nsToSecNs(contractLog.consensusTimestamp),
      transaction_hash: toHexStringNonQuantity(contractLog.transactionHash),
      transaction_index: contractLog.transactionIndex,
    });
  }
}

export default ContractLogViewModel;
