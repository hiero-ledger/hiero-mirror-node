// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';
import EntityId from '../entityId';
import {nsToSecNs, toHexString} from '../utils';
import {proto} from '@hashgraph/proto';

/**
 * Contract results view model
 */
class ContractResultViewModel {
  static #BLOOM_SIZE = 256;
  static #EMPTY_BLOOM = `0x${'00'.repeat(ContractResultViewModel.#BLOOM_SIZE)}`;

  /**
   * Constructs contractResult view model
   *
   * @param {ContractResult} contractResult
   */
  constructor(contractResult) {
    const contractId = EntityId.parse(contractResult.contractId, {isNullable: true});
    this.address = contractResult?.evmAddress?.length
      ? toHexString(contractResult.evmAddress, true)
      : contractId.toEvmAddress();
    this.amount = contractResult.amount;
    this.bloom = this.#encodeBloom(contractResult.bloom);
    this.call_result = toHexString(contractResult.callResult, true);
    this.contract_id = contractId.toString();
    this.created_contract_ids = _.toArray(contractResult.createdContractIds).map((id) => EntityId.parse(id).toString());
    this.error_message = _.isEmpty(contractResult.errorMessage) ? null : contractResult.errorMessage;
    this.from =
      EntityId.parse(contractResult.senderId, {isNullable: true}).toEvmAddress() ||
      this.#extractSenderFromFunctionResult(contractResult);
    this.function_parameters = toHexString(contractResult.functionParameters, true);
    this.gas_consumed = contractResult.gasConsumed;
    this.gas_limit = contractResult.gasLimit;
    this.gas_used = contractResult.gasUsed;
    this.timestamp = nsToSecNs(contractResult.consensusTimestamp);
    this.to = contractId.toEvmAddress();
    this.hash = toHexString(contractResult.transactionHash, true);
  }

  #encodeBloom(bloom) {
    return bloom?.length === 0 ? ContractResultViewModel.#EMPTY_BLOOM : toHexString(bloom, true);
  }

  #extractSenderFromFunctionResult(contractResult) {
    if (!contractResult.sender_id && contractResult.functionResult) {
      try {
        const functionResult = proto.ContractFunctionResult.decode(contractResult.functionResult);
        return functionResult?.senderId?.alias?.length ? toHexString(functionResult.senderId.alias, true) : null;
      } catch (error) {
        logger.warn('Error decoding function result', error);
      }
    }

    return null;
  }
}

export default ContractResultViewModel;
