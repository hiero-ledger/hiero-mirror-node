// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';

import ContractLogResultsViewModel from './contractResultLogViewModel';
import ContractResultStateChangeViewModel from './contractResultStateChangeViewModel';
import ContractResultViewModel from './contractResultViewModel';
import EntityId from '../entityId';
import {TransactionResult} from '../model';
import * as utils from '../utils';

/**
 * Contract result details view model
 */
class ContractResultDetailsViewModel extends ContractResultViewModel {
  static _LEGACY_TYPE = 0;
  static _SUCCESS_PROTO_IDS = TransactionResult.getSuccessProtoIds();
  static _SUCCESS_RESULT = '0x1';
  static _FAIL_RESULT = '0x0';

  /**
   * Constructs contractResultDetails view model
   *
   * @param {ContractResult} contractResult
   * @param {RecordFile} recordFile
   * @param {EthereumTransaction} ethTransaction
   * @param {ContractLog[]} contractLogs
   * @param {ContractStateChange[]} contractStateChanges
   * @param {FileData} fileData
   * @param {boolean} convertToHbar - If true, convert weibar to tinybar; if false, return raw weibar
   */
  constructor(
    contractResult,
    recordFile,
    ethTransaction,
    contractLogs = null,
    contractStateChanges = null,
    fileData = null,
    convertToHbar = true
  ) {
    super(contractResult);

    this.block_hash = utils.addHexPrefix(recordFile?.hash);
    this.block_number = recordFile?.index ?? null;
    this.hash = utils.toHexStringNonQuantity(contractResult.transactionHash);
    if (!_.isNil(contractLogs)) {
      this.logs = contractLogs.map((contractLog) => new ContractLogResultsViewModel(contractLog));
    }
    this.result = TransactionResult.getName(contractResult.transactionResult);
    this.transaction_index = contractResult.transactionIndex;
    if (!_.isNil(contractStateChanges)) {
      this.state_changes = contractStateChanges.map((csc) => new ContractResultStateChangeViewModel(csc));
    }
    const isTransactionSuccessful = ContractResultDetailsViewModel._SUCCESS_PROTO_IDS.includes(
      contractResult.transactionResult
    );
    this.status = isTransactionSuccessful
      ? ContractResultDetailsViewModel._SUCCESS_RESULT
      : ContractResultDetailsViewModel._FAIL_RESULT;
    if (!_.isEmpty(contractResult.failedInitcode)) {
      this.failed_initcode = utils.toHexStringNonQuantity(contractResult.failedInitcode);
    } else if (
      this.status === ContractResultDetailsViewModel._FAIL_RESULT &&
      !_.isNil(ethTransaction) &&
      !_.isEmpty(ethTransaction.callData)
    ) {
      this.failed_initcode = utils.toHexStringNonQuantity(ethTransaction.callData);
    } else {
      this.failed_initcode = null;
    }

    // default eth related values
    this.access_list = null;
    this.block_gas_used = recordFile?.gasUsed != null && recordFile.gasUsed !== -1 ? recordFile.gasUsed : null;
    this.chain_id = null;
    this.gas_price = null;
    this.max_fee_per_gas = null;
    this.max_priority_fee_per_gas = null;
    this.r = null;
    this.s = null;
    this.type = null;
    this.v = null;
    this.nonce = null;

    if (!_.isNil(ethTransaction)) {
      this.access_list = utils.toHexStringNonQuantity(ethTransaction.accessList);

      // Handle value/amount conversion based on convertToHbar parameter
      // After migration, DB contains weibar values
      if (convertToHbar) {
        // Convert from weibar to tinybar for backward compatibility
        this.amount = ContractResultDetailsViewModel._convertWeibarToTinybar(ethTransaction.value);
      } else {
        // Return raw weibar values from DB
        this.amount = BigInt(utils.addHexPrefix(ethTransaction.value));
      }

      this.chain_id = utils.toHexStringQuantity(ethTransaction.chainId);

      if (!isTransactionSuccessful && _.isEmpty(contractResult.errorMessage)) {
        this.error_message = this.result;
      }

      if (!_.isNil(contractResult.senderId)) {
        this.from = EntityId.parse(contractResult.senderId).toEvmAddress();
      }

      if (!_.isNil(ethTransaction.gasLimit)) {
        this.gas_limit = ethTransaction.gasLimit;
      }

      // Convert gas fields based on convertToHbar parameter
      if (convertToHbar) {
        // Convert from weibar to tinybar for backward compatibility
        this.gas_price = ContractResultDetailsViewModel._convertWeibarBytesToHex(ethTransaction.gasPrice);
        this.max_fee_per_gas = ContractResultDetailsViewModel._convertWeibarBytesToHex(ethTransaction.maxFeePerGas);
        this.max_priority_fee_per_gas = ContractResultDetailsViewModel._convertWeibarBytesToHex(
          ethTransaction.maxPriorityFeePerGas
        );
      } else {
        // Return raw weibar values from DB
        this.gas_price = utils.toHexStringQuantity(ethTransaction.gasPrice);
        this.max_fee_per_gas = utils.toHexStringQuantity(ethTransaction.maxFeePerGas);
        this.max_priority_fee_per_gas = utils.toHexStringQuantity(ethTransaction.maxPriorityFeePerGas);
      }

      this.nonce = ethTransaction.nonce;
      this.r = utils.toHexStringNonQuantity(ethTransaction.signatureR);
      this.s = utils.toHexStringNonQuantity(ethTransaction.signatureS);
      if (ethTransaction.toAddress?.length) {
        this.to = utils.toHexStringNonQuantity(ethTransaction.toAddress);
      }
      this.type = ethTransaction.type;
      this.v =
        this.type === ContractResultDetailsViewModel._LEGACY_TYPE && ethTransaction.signatureV
          ? BigInt(utils.toHexStringNonQuantity(ethTransaction.signatureV))
          : ethTransaction.recoveryId;

      if (!_.isEmpty(ethTransaction.callData)) {
        this.function_parameters = utils.toHexStringNonQuantity(ethTransaction.callData);
      } else if (!contractResult.functionParameters.length && !_.isNil(fileData)) {
        this.function_parameters = utils.toHexStringNonQuantity(fileData.file_data);
      }
    }
  }

  /**
   * Converts weibar byte array to tinybar BigInt
   * Divides by 10,000,000,000 (WEIBARS_TO_TINYBARS)
   * @param {Buffer|null} weibarBytes
   * @returns {BigInt|null}
   */
  static _convertWeibarToTinybar(weibarBytes) {
    if (_.isNil(weibarBytes) || weibarBytes.length === 0) {
      return null;
    }

    const weibar = BigInt(utils.addHexPrefix(Buffer.from(weibarBytes).toString('hex')));
    const divisor = 10_000_000_000n;
    return weibar / divisor;
  }

  /**
   * Converts weibar byte array to hex string after converting to tinybar
   * @param {Buffer|null} weibarBytes
   * @returns {string|null}
   */
  static _convertWeibarBytesToHex(weibarBytes) {
    if (_.isNil(weibarBytes) || weibarBytes.length === 0) {
      return null;
    }

    const tinybar = ContractResultDetailsViewModel._convertWeibarToTinybar(weibarBytes);
    if (tinybar === null) {
      return null;
    }

    // Convert tinybar BigInt to hex bytes
    let hex = tinybar.toString(16);
    if (hex === '0') {
      return '0x0';
    }

    // Ensure even length for proper hex encoding
    if (hex.length % 2 !== 0) {
      hex = '0' + hex;
    }

    return utils.toHexStringQuantity(Buffer.from(hex, 'hex'));
  }
}

export default ContractResultDetailsViewModel;
