// SPDX-License-Identifier: Apache-2.0

import isEmpty from 'lodash/isEmpty';
import isNil from 'lodash/isNil';
import toArray from 'lodash/toArray';
import config from '../config';
import {EVM_ADDRESS_LENGTH, EVM_SLOT_LENGTH} from '../constants';
import EntityId from '../entityId';
import {fromBinary} from '@bufbuild/protobuf';
import {ContractFunctionResultSchema} from '../gen/services/contract_types_pb.js';
import {nsToSecNs, toHexString} from '../utils';

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
    if (config.response.enableDelegationAddress && !isEmpty(contractResult.authorizationList)) {
      this.authorization_list = ContractResultViewModel.padAuthorizationList(contractResult.authorizationList);
    }
    this.bloom = this.#encodeBloom(contractResult.bloom);
    this.call_result = toHexString(contractResult.callResult, true);
    this.contract_id = contractId.toString();
    this.created_contract_ids = toArray(contractResult.createdContractIds).map((id) => EntityId.parse(id).toString());
    this.error_message = isEmpty(contractResult.errorMessage) ? null : contractResult.errorMessage;
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
    if (isNil(contractResult.senderId) && contractResult.functionResult) {
      try {
        const functionResult = fromBinary(ContractFunctionResultSchema, contractResult.functionResult);
        const senderAlias = functionResult.senderId?.account;
        return senderAlias?.case === 'alias' && senderAlias.value?.length ? toHexString(senderAlias.value, true) : null;
      } catch (error) {
        logger.warn('Error decoding function result', error);
      }
    }

    return null;
  }

  /**
   * Left-pads access list address to 20 bytes and storage keys to 32 bytes for REST responses.
   * @param {Array|null|undefined} accessList
   * @return {Array}
   */
  static padAccessList(accessList) {
    if (!Array.isArray(accessList) || accessList.length === 0) {
      return accessList ?? [];
    }

    return accessList.map((entry) => {
      const storageKeys = entry.storage_keys ?? entry.storageKeys;
      return {
        ...entry,
        address: toHexString(entry.address, true, EVM_ADDRESS_LENGTH * 2),
        storage_keys: Array.isArray(storageKeys)
          ? storageKeys.map((key) => toHexString(key, true, EVM_SLOT_LENGTH * 2))
          : [],
      };
    });
  }

  /**
   * Left-pads authorization list address to 20 bytes and r/s to 32 bytes for REST responses.
   * @param {Array|null|undefined} authorizationList
   * @return {Array}
   */
  static padAuthorizationList(authorizationList) {
    if (!Array.isArray(authorizationList) || authorizationList.length === 0) {
      return authorizationList ?? [];
    }

    return authorizationList.map((authorization) => ({
      ...authorization,
      address: toHexString(authorization.address, true, EVM_ADDRESS_LENGTH * 2),
      r: toHexString(authorization.r, true, EVM_SLOT_LENGTH * 2),
      s: toHexString(authorization.s, true, EVM_SLOT_LENGTH * 2),
    }));
  }
}

export default ContractResultViewModel;
