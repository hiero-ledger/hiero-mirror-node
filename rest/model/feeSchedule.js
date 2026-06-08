// SPDX-License-Identifier: Apache-2.0

import {fromBinary} from '@bufbuild/protobuf';
import isNumber from 'lodash/isNumber';
import {CurrentAndNextFeeScheduleSchema, HederaFunctionality} from '../gen/services/basic_types_pb.js';
import {FileDecodeError} from '../errors';
import {bigIntMax} from '../utils';
import TransactionType from './transactionType';

const FUNCTIONALITY_TO_TYPE = {
  [HederaFunctionality.ContractCall]: 'ContractCall',
  [HederaFunctionality.ContractCreate]: 'ContractCreate',
  [HederaFunctionality.EthereumTransaction]: 'EthereumTransaction',
};

class FeeSchedule {
  static FEE_DIVISOR_FACTOR = 1000n;

  #feeSchedule;

  constructor(feeScheduleFile) {
    let feeSchedule;

    try {
      feeSchedule = fromBinary(CurrentAndNextFeeScheduleSchema, feeScheduleFile.file_data);
    } catch (error) {
      throw new FileDecodeError(error.message);
    }

    this.#feeSchedule = feeSchedule;
    this.fees = {};
  }

  setExchangeRate(exchangeRate, refTimestampNanos) {
    const effectiveFeeSchedule = this.getEffectiveFeeSchedule(this.#feeSchedule, refTimestampNanos);
    const effectiveExchangeRate = this.getEffectiveExchangeRate(exchangeRate, refTimestampNanos);
    this.fees = this.mapFees(effectiveFeeSchedule, effectiveExchangeRate);
  }

  getEffectiveFeeSchedule(feeSchedules, refTimestampNanos) {
    const currentFeeSchedule = feeSchedules.currentFeeSchedule;
    const feeScheduleExpirationTime = currentFeeSchedule.expiryTime?.seconds;

    if (feeScheduleExpirationTime != null && refTimestampNanos > feeScheduleExpirationTime * 1_000_000_000n) {
      return feeSchedules.nextFeeSchedule;
    }
    return currentFeeSchedule;
  }

  getEffectiveExchangeRate(exchangeRate, refTimestampNanos) {
    const currentRateExpirationTime = exchangeRate.current_expiration;

    if (currentRateExpirationTime != null && refTimestampNanos > BigInt(currentRateExpirationTime) * 1_000_000_000n) {
      return {hbarEquiv: exchangeRate.next_hbar, centEquiv: exchangeRate.next_cent};
    }
    return {hbarEquiv: exchangeRate.current_hbar, centEquiv: exchangeRate.current_cent};
  }

  mapFees(feeSchedule, exchangeRate) {
    if (!feeSchedule?.transactionFeeSchedule) {
      return {};
    }

    const fees = {};
    for (const schedule of feeSchedule.transactionFeeSchedule) {
      const type = FUNCTIONALITY_TO_TYPE[schedule.hederaFunctionality];
      if (!type || !schedule.fees?.length) {
        continue;
      }

      const feeData = schedule.fees[0];
      const serviceData = feeData?.servicedata ?? feeData?.serviceData;
      if (!serviceData) {
        continue;
      }

      const gas = serviceData.gas;
      const tinyBars = this.convertGasPriceToTinyBars(gas, exchangeRate.hbarEquiv, exchangeRate.centEquiv);

      if (tinyBars !== null) {
        fees[type] = tinyBars;
      }
    }

    return fees;
  }

  convertGasPriceToTinyBars(gasPrice, hbars, cents) {
    if (gasPrice == null || !isNumber(hbars) || !isNumber(cents)) {
      return null;
    }

    cents = BigInt(cents);
    if (cents === 0n) {
      return null;
    }

    const fee = (BigInt(gasPrice) * BigInt(hbars)) / (cents * FeeSchedule.FEE_DIVISOR_FACTOR);
    return bigIntMax(fee, 1n);
  }

  getGasForType(type) {
    return this.fees?.[type] ?? null;
  }

  static getTransactionType(hederaTransactionType) {
    if (Number(hederaTransactionType) === Number(TransactionType.getProtoId('CONTRACTCREATEINSTANCE'))) {
      return FeeSchedule.TRANSACTION_TYPES.CONTRACT_CREATE;
    }

    return FeeSchedule.TRANSACTION_TYPES.CONTRACT_CALL;
  }

  static TRANSACTION_TYPES = {
    CONTRACT_CALL: 'ContractCall',
    CONTRACT_CREATE: 'ContractCreate',
    ETHEREUM_TRANSACTION: 'EthereumTransaction',
  };
}

export default FeeSchedule;
