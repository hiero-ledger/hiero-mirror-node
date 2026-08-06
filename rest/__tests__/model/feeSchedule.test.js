// SPDX-License-Identifier: Apache-2.0

import {create, toBinary} from '@bufbuild/protobuf';
import {
  FeeDataSchema,
  FeeComponentsSchema,
  TransactionFeeScheduleSchema,
  FeeScheduleSchema as LegacyFeeScheduleSchema,
  CurrentAndNextFeeScheduleSchema,
  HederaFunctionality,
} from '../../gen/services/basic_types_pb.js';
import {TimestampSecondsSchema} from '../../gen/services/timestamp_pb.js';
import {
  Extra,
  ExtraFeeDefinitionSchema,
  FeeScheduleSchema as SimpleFeeScheduleSchema,
} from '../../gen/fees/fee_schedule_pb.js';
import {FileDecodeError} from '../../errors';
import FeeSchedule from '../../model/feeSchedule';

const makeSimpleFeeScheduleFileData = (extras) => {
  return Buffer.from(
    toBinary(
      SimpleFeeScheduleSchema,
      create(SimpleFeeScheduleSchema, {
        extras,
      })
    )
  );
};

const makeGasExtra = (gasTinycents) =>
  create(ExtraFeeDefinitionSchema, {
    name: Extra.GAS,
    fee: BigInt(gasTinycents),
  });

const makeLegacyFeeScheduleFileData = (gas, expirySeconds = 2000000000) => {
  const feeComponents = create(FeeComponentsSchema, {gas});
  const feeData = create(FeeDataSchema, {servicedata: feeComponents});
  const transactionFeeSchedule = create(TransactionFeeScheduleSchema, {
    hederaFunctionality: HederaFunctionality.ContractCall,
    fees: [feeData],
  });
  const feeSchedule = create(LegacyFeeScheduleSchema, {
    transactionFeeSchedule: [transactionFeeSchedule],
    expiryTime: create(TimestampSecondsSchema, {seconds: BigInt(expirySeconds)}),
  });
  return Buffer.from(
    toBinary(
      CurrentAndNextFeeScheduleSchema,
      create(CurrentAndNextFeeScheduleSchema, {
        currentFeeSchedule: feeSchedule,
      })
    )
  );
};

describe('FeeSchedule', () => {
  describe('constructor', () => {
    test('parses simple FeeSchedule with extras', () => {
      const feeSchedule = new FeeSchedule({
        file_data: makeSimpleFeeScheduleFileData([makeGasExtra(1234)]),
        consensus_timestamp: 42,
      });

      expect(feeSchedule.consensus_timestamp).toBe(42);
      expect(feeSchedule.simpleFeeSchedule).toBeDefined();
      expect(feeSchedule.feeSchedule).toBeUndefined();
      expect(feeSchedule.simpleFeeSchedule.extras).toHaveLength(1);
    });

    test('falls back to legacy CurrentAndNextFeeSchedule when simple extras are empty', () => {
      const feeSchedule = new FeeSchedule({
        file_data: makeLegacyFeeScheduleFileData(852000),
        consensus_timestamp: 7,
      });

      expect(feeSchedule.consensus_timestamp).toBe(7);
      expect(feeSchedule.simpleFeeSchedule).toBeUndefined();
      expect(feeSchedule.feeSchedule).toBeDefined();
      expect(feeSchedule.feeSchedule.currentFeeSchedule).toBeDefined();
    });

    test('treats non-empty simple FeeSchedule without GAS as simple format', () => {
      const feeSchedule = new FeeSchedule({
        file_data: makeSimpleFeeScheduleFileData([
          create(ExtraFeeDefinitionSchema, {
            name: Extra.SIGNATURES,
            fee: 10n,
          }),
        ]),
        consensus_timestamp: 1,
      });

      expect(feeSchedule.simpleFeeSchedule).toBeDefined();
      expect(feeSchedule.feeSchedule).toBeUndefined();
    });

    test('throws FileDecodeError for unparseable file data', () => {
      expect(
        () =>
          new FeeSchedule({
            file_data: Buffer.from('not-a-valid-protobuf'),
            consensus_timestamp: 1,
          })
      ).toThrow(FileDecodeError);
    });
  });

  describe('getGasPriceTinycents', () => {
    test('returns GAS extra fee in tinycents', () => {
      const feeSchedule = new FeeSchedule({
        file_data: makeSimpleFeeScheduleFileData([
          create(ExtraFeeDefinitionSchema, {
            name: Extra.SIGNATURES,
            fee: 99n,
          }),
          makeGasExtra(555),
        ]),
        consensus_timestamp: 1,
      });

      expect(feeSchedule.getGasPriceTinycents()).toBe(555n);
    });

    test('returns null when GAS extra is missing', () => {
      const feeSchedule = new FeeSchedule({
        file_data: makeSimpleFeeScheduleFileData([
          create(ExtraFeeDefinitionSchema, {
            name: Extra.KEYS,
            fee: 10n,
          }),
        ]),
        consensus_timestamp: 1,
      });

      expect(feeSchedule.getGasPriceTinycents()).toBeNull();
    });

    test('returns null for legacy fee schedules', () => {
      const feeSchedule = new FeeSchedule({
        file_data: makeLegacyFeeScheduleFileData(852000),
        consensus_timestamp: 1,
      });

      expect(feeSchedule.getGasPriceTinycents()).toBeNull();
    });

    test('matches GAS when name is the string "GAS"', () => {
      const feeSchedule = new FeeSchedule({
        file_data: makeSimpleFeeScheduleFileData([makeGasExtra(1)]),
        consensus_timestamp: 1,
      });
      feeSchedule.simpleFeeSchedule = {
        extras: [{name: 'GAS', fee: 777n}],
      };

      expect(feeSchedule.getGasPriceTinycents()).toBe(777n);
    });
  });
});
