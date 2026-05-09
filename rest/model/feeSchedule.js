// SPDX-License-Identifier: Apache-2.0

import {fromBinary} from '@bufbuild/protobuf';
import {CurrentAndNextFeeScheduleSchema, HederaFunctionality} from '../gen/services/basic_types_pb.js';
import {FileDecodeError} from '../errors';

const FEE_DIVISOR_FACTOR = 1000n;

const FUNCTIONALITY_TO_TYPE = {
  [HederaFunctionality.ContractCall]: 'ContractCall',
  [HederaFunctionality.ContractCreate]: 'ContractCreate',
  [HederaFunctionality.EthereumTransaction]: 'EthereumTransaction',
};

class FeeSchedule {
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
    this.timestamp = feeScheduleFile.consensus_timestamp;
  }

  setExchangeRate(exchangeRate) {
    const effectiveFeeSchedule = this.getEffectiveFeeSchedule(this.#feeSchedule, this.timestamp);
    const effectiveExchangeRate = this.getEffectiveExchangeRate(exchangeRate, this.timestamp);
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
    return this.fees?.[type] ?? null;
  }

  static TRANSACTION_TYPES = {
    CONTRACT_CALL: 'ContractCall',
    CONTRACT_CREATE: 'ContractCreate',
    ETHEREUM_TRANSACTION: 'EthereumTransaction',
  };
}

export default FeeSchedule;
