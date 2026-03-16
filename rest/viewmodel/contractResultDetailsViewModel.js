// SPDX-License-Identifier: Apache-2.0

import isEmpty from 'lodash/isEmpty';
import isNil from 'lodash/isNil';

import ContractLogResultsViewModel from './contractResultLogViewModel';
import ContractResultStateChangeViewModel from './contractResultStateChangeViewModel';
import ContractResultViewModel from './contractResultViewModel';
import EntityId from '../entityId';
import {TransactionResult} from '../model';
import * as utils from '../utils';
import {WEIBARS_TO_TINYBARS} from '../constants';

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
    if (!isNil(contractLogs)) {
      this.logs = contractLogs.map((contractLog) => new ContractLogResultsViewModel(contractLog));
    }
    this.result = TransactionResult.getName(contractResult.transactionResult);
    this.transaction_index = contractResult.transactionIndex;
    if (!isNil(contractStateChanges)) {
      this.state_changes = contractStateChanges.map((csc) => new ContractResultStateChangeViewModel(csc));
    }
    const isTransactionSuccessful = ContractResultDetailsViewModel._SUCCESS_PROTO_IDS.includes(
      contractResult.transactionResult
    );
    this.status = isTransactionSuccessful
      ? ContractResultDetailsViewModel._SUCCESS_RESULT
      : ContractResultDetailsViewModel._FAIL_RESULT;
    if (!isEmpty(contractResult.failedInitcode)) {
      this.failed_initcode = utils.toHexStringNonQuantity(contractResult.failedInitcode);
    } else if (
      this.status === ContractResultDetailsViewModel._FAIL_RESULT &&
      !isNil(ethTransaction) &&
      !isEmpty(ethTransaction.callData)
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

    if (!isNil(ethTransaction)) {
      this.access_list = utils.toHexStringNonQuantity(ethTransaction.accessList);
      this.chain_id = utils.toHexStringQuantity(ethTransaction.chainId);

      if (!isTransactionSuccessful && isEmpty(contractResult.errorMessage)) {
        this.error_message = this.result;
      }

      if (!isNil(contractResult.senderId)) {
        this.from = EntityId.parse(contractResult.senderId).toEvmAddress();
      }

      if (!isNil(ethTransaction.gasLimit)) {
        this.gas_limit = ethTransaction.gasLimit;
      }

      // Handle all weibar/tinybar conversions based on convertToHbar parameter
      // After migration, DB contains weibar values
      if (convertToHbar) {
        // Convert from weibar to tinybar for backward compatibility
        this.amount = ContractResultDetailsViewModel._convertWeibarToTinybar(ethTransaction.value, true);
        this.gas_price = ContractResultDetailsViewModel._convertWeibarBytesToHex(ethTransaction.gasPrice);
        this.max_fee_per_gas = ContractResultDetailsViewModel._convertWeibarBytesToHex(ethTransaction.maxFeePerGas);
        this.max_priority_fee_per_gas = ContractResultDetailsViewModel._convertWeibarBytesToHex(
          ethTransaction.maxPriorityFeePerGas
        );
      } else {
        // Return raw weibar values from DB
        this.amount = BigInt(utils.addHexPrefix(ethTransaction.value));
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

      if (!isEmpty(ethTransaction.callData)) {
        this.function_parameters = utils.toHexStringNonQuantity(ethTransaction.callData);
      } else if (!contractResult.functionParameters.length && !isNil(fileData)) {
        this.function_parameters = utils.toHexStringNonQuantity(fileData.file_data);
      }
    } else if (!convertToHbar && !isNil(contractResult.amount)) {
      // ethTransaction is null but caller wants weibar; convert tinybar to weibar
      this.amount = BigInt(contractResult.amount) * WEIBARS_TO_TINYBARS;
    }
  }

  /**
   * Converts weibar value to tinybar BigInt
   * Divides by 10,000,000,000 (WEIBARS_TO_TINYBARS)
   * @param {Buffer|string|null} weibarValue - Buffer or hex string representation of weibar
   * @param {boolean} signed - If true, interpret as signed (two's complement)
   * @returns {BigInt|null}
   */
  static _convertWeibarToTinybar(weibarValue, signed = false) {
    if (!weibarValue || weibarValue.length === 0) {
      return null;
    }

    // ---- Step 1: Java BigInteger constructor behavior ----
    let value;
    if (signed) {
      // Interpret as two's complement (like new BigInteger(bytes))
      const weibarBytes = Buffer.isBuffer(weibarValue)
        ? weibarValue
        : Buffer.from(weibarValue.replace('0x', ''), 'hex');
      value = this._bytesToSignedBigInt(weibarBytes);
    } else {
      // Interpret as unsigned (like new BigInteger(1, bytes))
      const weibarHex = `0x${
        Buffer.isBuffer(weibarValue) ? weibarValue.toString('hex') : weibarValue.replace('0x', '')
      }`;
      value = BigInt(weibarHex);
    }

    // ---- Step 2: divide (truncates toward zero like Java) ----
    return value / WEIBARS_TO_TINYBARS;
  }

  static _bytesToSignedBigInt(buffer) {
    if (buffer.length === 0) {
      return 0n;
    }

    const value = BigInt(`0x${buffer.toString('hex')}`);
    return buffer[0] & 0x80 ? value - (1n << BigInt(buffer.length * 8)) : value;
  }

  /**
   * Converts weibar bytes to hex string, converting to tinybar in the process
   * @param {Buffer|string|null} weibarValue - Buffer or hex string representation of weibar
   * @returns {string|null} Hex string representation of tinybar value
   */
  static _convertWeibarBytesToHex(weibarValue) {
    if (isNil(weibarValue) || weibarValue.length === 0) {
      return '0x';
    }

    const tinybarBigInt = ContractResultDetailsViewModel._convertWeibarToTinybar(weibarValue, false);
    if (tinybarBigInt === null) {
      return '0x';
    }

    // Gas prices are always unsigned, so direct conversion to hex
    return utils.toHexStringQuantity(tinybarBigInt);
  }
}

export default ContractResultDetailsViewModel;
