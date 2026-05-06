// SPDX-License-Identifier: Apache-2.0

import {fromBinary} from '@bufbuild/protobuf';
import {CurrentAndNextFeeScheduleSchema, HederaFunctionality} from '../gen/services/basic_types_pb.js';
import {ExchangeRateSetSchema} from '../gen/services/exchange_rate_pb.js';
import {FileDecodeError} from '../errors';

const FEE_DIVISOR_FACTOR = 1000n;

const FUNCTIONALITY_TO_TYPE = {
  [HederaFunctionality.ContractCall]: 'ContractCall',
  [HederaFunctionality.ContractCreate]: 'ContractCreate',
  [HederaFunctionality.EthereumTransaction]: 'EthereumTransaction',
};

class FeeSchedule {
  constructor(feeScheduleFile, exchangeRateFile) {
    let feeSchedule;
    let exchangeRateSet;

    try {
      feeSchedule = fromBinary(CurrentAndNextFeeScheduleSchema, feeScheduleFile.file_data);
      exchangeRateSet = fromBinary(ExchangeRateSetSchema, exchangeRateFile.file_data);
    } catch (error) {
      throw new FileDecodeError(error.message);
    }

    const effectiveFeeSchedule = this.getEffectiveFeeSchedule(feeSchedule, feeScheduleFile.consensus_timestamp);
    const effectiveExchangeRate = this.getEffectiveExchangeRate(exchangeRateSet, feeScheduleFile.consensus_timestamp);

    this.fees = this.mapFees(effectiveFeeSchedule, effectiveExchangeRate);
    this.timestamp = feeScheduleFile.consensus_timestamp;
  }

  getEffectiveFeeSchedule(feeSchedules, refTimestampNanos) {
    const currentFeeSchedule = feeSchedules.currentFeeSchedule;
    const feeScheduleExpirationTime = currentFeeSchedule.expiryTime?.seconds;

    if (feeScheduleExpirationTime != null && refTimestampNanos > feeScheduleExpirationTime * 1_000_000_000n) {
      return feeSchedules.nextFeeSchedule;
    }
    return currentFeeSchedule;
  }

  getEffectiveExchangeRate(exchangeRateSet, refTimestampNanos) {
    const currentRate = exchangeRateSet.currentRate;
    const currentRateExpirationTime = currentRate.expirationTime?.seconds;

    if (currentRateExpirationTime != null && refTimestampNanos > currentRateExpirationTime * 1_000_000_000n) {
      return exchangeRateSet.nextRate;
    }
    return currentRate;
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
      if (!feeData?.serviceData) {
        continue;
      }

      const gas = feeData.serviceData.gas;
      const tinyBars = this.convertGasPriceToTinyBars(gas, exchangeRate.hbarEquiv, exchangeRate.centEquiv);

      if (tinyBars !== null) {
        fees[type] = tinyBars;
      }
    }

    return fees;
  }

  convertGasPriceToTinyBars(gasPrice, hbars, cents) {
    if (cents === 0 || cents == null) {
      return null;
    }

    const gasInTinyCents = BigInt(gasPrice) / FEE_DIVISOR_FACTOR;
    const gasInTinyBars = (gasInTinyCents * BigInt(hbars)) / BigInt(cents);
    return gasInTinyBars < 1n ? 1n : gasInTinyBars;
  }

  getGasForType(type) {
    return this.fees[type] ?? null;
  }

  static TRANSACTION_TYPES = {
    CONTRACT_CALL: 'ContractCall',
    CONTRACT_CREATE: 'ContractCreate',
    ETHEREUM_TRANSACTION: 'EthereumTransaction',
  };
}

export default FeeSchedule;
