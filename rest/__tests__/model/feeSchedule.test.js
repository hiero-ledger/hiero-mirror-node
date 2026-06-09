// SPDX-License-Identifier: Apache-2.0

import {create, toBinary} from '@bufbuild/protobuf';
import {
  FeeDataSchema,
  FeeComponentsSchema,
  TransactionFeeScheduleSchema,
  FeeScheduleSchema,
  CurrentAndNextFeeScheduleSchema,
  HederaFunctionality,
} from '../../gen/services/basic_types_pb.js';
import {TimestampSecondsSchema} from '../../gen/services/timestamp_pb.js';
import {ExchangeRate, FeeSchedule} from '../../model';
import {FileDataService} from '../../service';

const makeExchangeRate = (overrides = {}) => {
  const exchangeRate = Object.create(ExchangeRate.prototype);
  return Object.assign(exchangeRate, {
    current_hbar: 100,
    current_cent: 200,
    current_expiration: 7200,
    next_hbar: 300,
    next_cent: 400,
    ...overrides,
  });
};

const makeTransactionFeeSchedule = (hederaFunctionality, gas) => {
  const feeComponents = create(FeeComponentsSchema, {gas});
  const feeData = create(FeeDataSchema, {servicedata: feeComponents});
  return create(TransactionFeeScheduleSchema, {
    hederaFunctionality,
    fees: [feeData],
  });
};

// max(1, (gas * hbarEquiv) / (centEquiv * 1000))
const gasPriceInTinybars = (gas, centEquiv = 200, hbarEquiv = 100) => {
  const fee = (BigInt(gas) * BigInt(hbarEquiv)) / (BigInt(centEquiv) * 1000n);
  return fee > 0n ? fee : 1n;
};

describe('FileDataService effective schedule selection', () => {
  const exchangeRate = makeExchangeRate();

  const makeCurrentAndNextFeeScheduleFileData = (currentGas, nextGas, currentExpirySeconds) => {
    const currentFeeSchedule = create(FeeScheduleSchema, {
      transactionFeeSchedule: [makeTransactionFeeSchedule(HederaFunctionality.ContractCall, currentGas)],
      expiryTime: create(TimestampSecondsSchema, {seconds: BigInt(currentExpirySeconds)}),
    });
    const nextFeeSchedule = create(FeeScheduleSchema, {
      transactionFeeSchedule: [makeTransactionFeeSchedule(HederaFunctionality.ContractCall, nextGas)],
      expiryTime: create(TimestampSecondsSchema, {seconds: BigInt(currentExpirySeconds + 3600)}),
    });
    return Buffer.from(
      toBinary(
        CurrentAndNextFeeScheduleSchema,
        create(CurrentAndNextFeeScheduleSchema, {
          currentFeeSchedule,
          nextFeeSchedule,
        })
      )
    );
  };

  test('uses current fee schedule and exchange rate within the expiry hour', () => {
    const feeSchedule = new FeeSchedule({
      file_data: makeCurrentAndNextFeeScheduleFileData(1000, 5000, 7200),
      consensus_timestamp: 1,
    });
    const refTimestamp = 7_200_000_000_000n;

    const gasPrice = FileDataService.getGasPriceForType(feeSchedule, exchangeRate, refTimestamp, 'ContractCall');

    expect(gasPrice).toBe(gasPriceInTinybars(1000, 200, 100));
  });

  test('uses next fee schedule and exchange rate after the expiry hour', () => {
    const feeSchedule = new FeeSchedule({
      file_data: makeCurrentAndNextFeeScheduleFileData(1000, 5000, 7200),
      consensus_timestamp: 1,
    });
    const refTimestamp = 10_800_000_000_000n;

    const gasPrice = FileDataService.getGasPriceForType(feeSchedule, exchangeRate, refTimestamp, 'ContractCall');

    expect(gasPrice).toBe(gasPriceInTinybars(5000, 400, 300));
  });
});

describe('FileDataService.getEffectiveExchangeRate', () => {
  test('returns current rate within the expiry hour', () => {
    const exchangeRate = makeExchangeRate();

    expect(FileDataService.getEffectiveExchangeRate(exchangeRate, 7_200_000_000_000n)).toEqual({
      hbarEquiv: 100,
      centEquiv: 200,
    });
  });

  test('returns next rate after the expiry hour', () => {
    const exchangeRate = makeExchangeRate();

    expect(FileDataService.getEffectiveExchangeRate(exchangeRate, 10_800_000_000_000n)).toEqual({
      hbarEquiv: 300,
      centEquiv: 400,
    });
  });
});

describe('FileDataService.convertGasPriceToTinyBars', () => {
  test('converts gas price using hbar and cent equivalents', () => {
    expect(FileDataService.convertGasPriceToTinyBars(10000, 100, 200)).toBe(5n);
  });

  test('returns minimum fee of 1 tinybar', () => {
    expect(FileDataService.convertGasPriceToTinyBars(1, 1, 1000)).toBe(1n);
  });

  test('returns null for invalid input', () => {
    expect(FileDataService.convertGasPriceToTinyBars(null, 100, 200)).toBeNull();
    expect(FileDataService.convertGasPriceToTinyBars(1000, 100, 0)).toBeNull();
  });
});
