// SPDX-License-Identifier: Apache-2.0

import {isEmpty, isNil} from 'lodash-es';

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
    }
  }

  /**
   * Converts weibar byte array to tinybar BigInt
   * Divides by 10,000,000,000 (WEIBARS_TO_TINYBARS)
   * @param {Buffer|null} weibarBytes
   * @param {boolean} signed - If true, interpret as signed (two's complement)
   * @returns {BigInt|null}
   */
  static _convertWeibarToTinybar(weibarBytes, signed = false) {
    if (!weibarBytes || weibarBytes.length === 0) {
      return null;
    }
    // Ensure Buffer
    let input;
    if (Buffer.isBuffer(weibarBytes)) {
      input = weibarBytes;
    } else {
      let hexString = weibarBytes.replace('0x', '');
      // Pad to even length (Buffer.from requires even number of hex chars)
      if (hexString.length % 2 !== 0) {
        hexString = '0' + hexString;
      }
      input = Buffer.from(hexString, 'hex');
    }
    // ---- Step 1: Java BigInteger constructor behavior ----
    let value;
    if (signed) {
      // Interpret as two's complement (like new BigInteger(bytes))
      value = this._bytesToSignedBigInt(input);
    } else {
      // Interpret as unsigned (like new BigInteger(1, bytes))
      value = BigInt('0x' + input.toString('hex'));
    }
    // ---- Step 2: divide (truncates toward zero like Java) ----
    return value / WEIBARS_TO_TINYBARS;
  }

  static _bytesToSignedBigInt(buffer) {
    if (buffer.length === 0) {
      return 0n;
    }

    const hex = buffer.toString('hex');
    let value = BigInt('0x' + hex);

    const bits = BigInt(buffer.length * 8);
    const signBit = 1n << (bits - 1n);

    if (value & signBit) {
      value -= 1n << bits;
    }

    return value;
  }

  static _bigIntToMinimalTwosComplementBytes(value) {
    if (value === 0n) {
      return Buffer.from([0]);
    }

    const negative = value < 0n;

    if (!negative) {
      let hex = value.toString(16);
      if (hex.length % 2) {
        hex = '0' + hex;
      }

      let bytes = Buffer.from(hex, 'hex');

      if (bytes[0] & 0x80) {
        bytes = Buffer.concat([Buffer.from([0]), bytes]);
      }

      return bytes;
    }

    let abs = -value;
    let hex = abs.toString(16);
    if (hex.length % 2) {
      hex = '0' + hex;
    }

    let bytes = Buffer.from(hex, 'hex');

    for (let i = 0; i < bytes.length; i++) {
      bytes[i] = ~bytes[i] & 0xff;
    }

    for (let i = bytes.length - 1; i >= 0; i--) {
      bytes[i]++;
      if (bytes[i] <= 0xff) {
        break;
      }
      bytes[i] = 0;
    }

    if (!(bytes[0] & 0x80)) {
      bytes = Buffer.concat([Buffer.from([0xff]), bytes]);
    }

    return bytes;
  }

  /**
   * Converts weibar bytes to hex string, converting to tinybar in the process
   * @param {Buffer|null} weibarBytes
   * @returns {string|null} Hex string representation of tinybar value
   */
  static _convertWeibarBytesToHex(weibarBytes) {
    if (isNil(weibarBytes) || weibarBytes.length === 0) {
      return '0x';
    }

    const tinybarBigInt = ContractResultDetailsViewModel._convertWeibarToTinybar(weibarBytes, false);
    if (tinybarBigInt === null) {
      return '0x';
    }

    // Convert tinybar BigInt to minimal two's complement bytes, then to hex string
    const tinybarBytes = ContractResultDetailsViewModel._bigIntToMinimalTwosComplementBytes(tinybarBigInt);
    return utils.toHexStringQuantity(tinybarBytes);
  }
}

export default ContractResultDetailsViewModel;
