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
import {FeeSchedule} from '../../model';

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

describe('FeeSchedule effective schedule selection', () => {
  const exchangeRate = {
    current_hbar: 100,
    current_cent: 200,
    current_expiration: 7200,
    next_hbar: 300,
    next_cent: 400,
  };

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

    feeSchedule.setExchangeRate(exchangeRate, refTimestamp);

    expect(feeSchedule.fees.ContractCall).toBe(gasPriceInTinybars(1000, 200, 100));
  });

  test('uses next fee schedule and exchange rate after the expiry hour', () => {
    const feeSchedule = new FeeSchedule({
      file_data: makeCurrentAndNextFeeScheduleFileData(1000, 5000, 7200),
      consensus_timestamp: 1,
    });
    const refTimestamp = 10_800_000_000_000n;

    feeSchedule.setExchangeRate(exchangeRate, refTimestamp);

    expect(feeSchedule.fees.ContractCall).toBe(gasPriceInTinybars(5000, 400, 300));
  });
});

describe('FeeSchedule.getTransactionType', () => {
  test('returns ContractCreate for CONTRACTCREATEINSTANCE transaction type', () => {
    expect(FeeSchedule.getTransactionType(8)).toBe(FeeSchedule.TRANSACTION_TYPES.CONTRACT_CREATE);
  });

  test('returns ContractCall for other transaction types', () => {
    expect(FeeSchedule.getTransactionType(7)).toBe(FeeSchedule.TRANSACTION_TYPES.CONTRACT_CALL);
    expect(FeeSchedule.getTransactionType(null)).toBe(FeeSchedule.TRANSACTION_TYPES.CONTRACT_CALL);
  });
});
