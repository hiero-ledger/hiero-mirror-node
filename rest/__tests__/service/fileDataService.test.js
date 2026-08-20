// SPDX-License-Identifier: Apache-2.0

import {jest} from '@jest/globals';
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
import config from '../../config';
import {ExchangeRate, FeeSchedule, FileData} from '../../model';
import {FileDataService} from '../../service';
import integrationDomainOps from '../integrationDomainOps';
import {setupIntegrationTest} from '../integrationUtils';
import EntityId from '../../entityId';

setupIntegrationTest();

const MAINNET_SIMPLE_FEES_SWITCHOVER_TIMESTAMP = 1779296400389248896n;
const TESTNET_SIMPLE_FEES_SWITCHOVER_TIMESTAMP = 1777482002529719510n;
const SIMPLE_FEES_SWITCHOVER_TIMESTAMP = TESTNET_SIMPLE_FEES_SWITCHOVER_TIMESTAMP;

const exchangeRateEntityId = EntityId.systemEntity.exchangeRateFile;
const feeScheduleEntityId = EntityId.systemEntity.feeScheduleFile;
const simpleFeeScheduleEntityId = EntityId.systemEntity.simpleFeeScheduleFile;

const exchangeRateFiles = [
  {
    consensus_timestamp: 1,
    entity_id: exchangeRateEntityId.getEncodedId().toString(),
    file_data: Buffer.from('0a1008b0ea0110cac1181a0608a0a1d09306121008b0ea0110e18e191a0608b0bdd09306', 'hex'),
    transaction_type: 17,
  },
  {
    consensus_timestamp: 2,
    entity_id: exchangeRateEntityId.getEncodedId().toString(),
    file_data: Buffer.from('0a1008b0ea0110f5f3191a06089085d09306121008b0ea0110cac1181a0608a0a1d09306', 'hex'),
    transaction_type: 16,
  },
  {
    consensus_timestamp: 3,
    entity_id: exchangeRateEntityId.getEncodedId().toString(),
    file_data: Buffer.from('0a1008b0ea0110e9c81a1a060880e9cf9306121008b0ea0110f5f3191a06089085d09306', 'hex'),
    transaction_type: 19,
  },
  {
    consensus_timestamp: 4,
    entity_id: exchangeRateEntityId.getEncodedId().toString(),
    file_data: Buffer.from('0a1008b0ea0110f9bb1b1a0608f0cccf9306121008b0ea0110e9c81a1a060880e9cf9306', 'hex'),
    transaction_type: 19,
  },
];

const makeFeeScheduleFileData = (gas, expirySeconds, hederaFunctionality = HederaFunctionality.ContractCall) => {
  const feeSchedule = create(LegacyFeeScheduleSchema, {
    transactionFeeSchedule: [makeTransactionFeeSchedule(hederaFunctionality, gas)],
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

const makeSimpleFeeScheduleFileData = (gasTinycents) => {
  return Buffer.from(
    toBinary(
      SimpleFeeScheduleSchema,
      create(SimpleFeeScheduleSchema, {
        extras: [
          create(ExtraFeeDefinitionSchema, {
            name: Extra.GAS,
            fee: BigInt(gasTinycents),
          }),
        ],
      })
    )
  );
};

const makeSimpleFeeScheduleWithoutGas = () => {
  return Buffer.from(
    toBinary(
      SimpleFeeScheduleSchema,
      create(SimpleFeeScheduleSchema, {
        extras: [
          create(ExtraFeeDefinitionSchema, {
            name: Extra.SIGNATURES,
            fee: 10n,
          }),
        ],
      })
    )
  );
};

const makeCurrentAndNextFeeScheduleFileData = (currentGas, nextGas, currentExpirySeconds) => {
  const currentFeeSchedule = create(LegacyFeeScheduleSchema, {
    transactionFeeSchedule: [makeTransactionFeeSchedule(HederaFunctionality.ContractCall, currentGas)],
    expiryTime: create(TimestampSecondsSchema, {seconds: BigInt(currentExpirySeconds)}),
  });
  const nextFeeSchedule = create(LegacyFeeScheduleSchema, {
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

const makeMultiTypeFeeScheduleFileData = (gasByFunctionality, expirySeconds) => {
  const feeSchedule = create(LegacyFeeScheduleSchema, {
    transactionFeeSchedule: Object.entries(gasByFunctionality).map(([functionality, gas]) =>
      makeTransactionFeeSchedule(Number(functionality), gas)
    ),
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

// max(1, (gasTinycents * hbarEquiv) / centEquiv) — matches convertGasPriceToTinyBars
const gasPriceInTinybars = (gasTinycents, centEquiv = 200, hbarEquiv = 100) => {
  const fee = (BigInt(gasTinycents) * BigInt(hbarEquiv)) / BigInt(centEquiv);
  return fee > 0n ? fee : 1n;
};

// Legacy fee components store gas * 1000; convertLegacyGasPriceToTinyBars divides first
const legacyGasPriceInTinybars = (legacyGas, centEquiv = 200, hbarEquiv = 100) => {
  return gasPriceInTinybars(BigInt(legacyGas) / 1000n, centEquiv, hbarEquiv);
};

const exchangeRateFileId = exchangeRateEntityId.getEncodedId();
describe('FileDataService.getExchangeRate tests', () => {
  test('FileDataService.getExchangeRate - No match', async () => {
    await expect(FileDataService.getExchangeRate({whereQuery: []})).resolves.toBeNull();
  });

  const expectedPreviousFile = {
    current_cent: 435305,
    current_expiration: 1651766400,
    current_hbar: 30000,
    next_cent: 424437,
    next_expiration: 1651770000,
    next_hbar: 30000,
    timestamp: 3,
  };

  const expectedLatestFile = {
    current_cent: 450041,
    current_expiration: 1651762800,
    current_hbar: 30000,
    next_cent: 435305,
    next_expiration: 1651766400,
    next_hbar: 30000,
    timestamp: 4,
  };

  test('FileDataService.getExchangeRate - Row match w latest', async () => {
    await integrationDomainOps.loadFileData(exchangeRateFiles);

    await expect(FileDataService.getExchangeRate({whereQuery: []})).resolves.toMatchObject(expectedLatestFile);
  });

  test('FileDataService.getExchangeRate - Row match w previous latest', async () => {
    await integrationDomainOps.loadFileData(exchangeRateFiles);

    const where = [
      {
        query: `${FileData.CONSENSUS_TIMESTAMP} <= `,
        param: expectedPreviousFile.timestamp,
      },
    ];
    await expect(FileDataService.getExchangeRate({whereQuery: where})).resolves.toMatchObject(expectedPreviousFile);
  });
});

describe('FileDataService.getLatestFileDataContents tests', () => {
  test('FileDataService.getLatestFileDataContents - No match', async () => {
    await expect(FileDataService.getLatestFileDataContents(exchangeRateFileId, {whereQuery: []})).resolves.toBeNull();
  });

  const expectedPreviousFile = {
    consensus_timestamp: 2,
    file_data: Buffer.concat([exchangeRateFiles[0].file_data, exchangeRateFiles[1].file_data]),
  };

  const expectedLatestFile = {
    consensus_timestamp: 4,
    file_data: Buffer.from('0a1008b0ea0110f9bb1b1a0608f0cccf9306121008b0ea0110e9c81a1a060880e9cf9306', 'hex'),
  };

  test('FileDataService.getLatestFileDataContents - Row match w latest', async () => {
    await integrationDomainOps.loadFileData(exchangeRateFiles);
    await expect(
      FileDataService.getLatestFileDataContents(exchangeRateFileId, {whereQuery: []})
    ).resolves.toMatchObject(expectedLatestFile);
  });

  test('FileDataService.getLatestFileDataContents - Row match w previous latest', async () => {
    await integrationDomainOps.loadFileData(exchangeRateFiles);

    const where = [
      {
        query: `${FileData.CONSENSUS_TIMESTAMP} <= `,
        param: expectedPreviousFile.consensus_timestamp,
      },
    ];
    await expect(
      FileDataService.getLatestFileDataContents(exchangeRateFileId, {whereQuery: where})
    ).resolves.toMatchObject(expectedPreviousFile);
  });
});

describe('FileDataService.getGasPrice tests', () => {
  beforeEach(() => {
    FileDataService.clearFeeScheduleCache();
  });

  const postSwitchoverTs = SIMPLE_FEES_SWITCHOVER_TIMESTAMP + 1000n;
  const preSwitchoverTs = SIMPLE_FEES_SWITCHOVER_TIMESTAMP - 1000n;
  const atSwitchoverTs = SIMPLE_FEES_SWITCHOVER_TIMESTAMP;

  const latestGasTinycents = 789;
  const previousLegacyGas = 123000;

  const feeScheduleFiles = [
    {
      consensus_timestamp: preSwitchoverTs.toString(),
      entity_id: feeScheduleEntityId.getEncodedId().toString(),
      file_data: makeFeeScheduleFileData(previousLegacyGas, 2000000000),
      transaction_type: 17,
    },
    {
      consensus_timestamp: atSwitchoverTs.toString(),
      entity_id: simpleFeeScheduleEntityId.getEncodedId().toString(),
      file_data: makeSimpleFeeScheduleFileData(latestGasTinycents),
      transaction_type: 19,
    },
  ];

  // Post switchover: gas=789 tinycents, next rate (now > current_expiration): hbar=30000, cent=435305 → 54n
  const expectedLatestGasPrice = 54n;
  // Pre switchover: legacy gas=123000 / 1000 = 123 tinycents → 8n
  const expectedPreviousGasPrice = 8n;

  test('FileDataService.getGasPrice - No match', async () => {
    await expect(FileDataService.getGasPrice()).resolves.toBeNull();
  });

  test('FileDataService.getGasPrice - Row match w latest simple fees', async () => {
    await integrationDomainOps.loadFileData(feeScheduleFiles);
    await integrationDomainOps.loadFileData(exchangeRateFiles);

    const result = await FileDataService.getGasPrice(postSwitchoverTs);
    expect(result).toBe(expectedLatestGasPrice);
  });

  test('FileDataService.getGasPrice - uses simple fees at exact switchover timestamp', async () => {
    await integrationDomainOps.loadFileData(feeScheduleFiles);
    await integrationDomainOps.loadFileData(exchangeRateFiles);

    const result = await FileDataService.getGasPrice(atSwitchoverTs);
    expect(result).toBe(expectedLatestGasPrice);
  });

  test('FileDataService.getGasPrice - Row match w previous legacy fees', async () => {
    await integrationDomainOps.loadFileData(feeScheduleFiles);
    await integrationDomainOps.loadFileData(exchangeRateFiles);

    const result = await FileDataService.getGasPrice(preSwitchoverTs);
    expect(result).toBe(expectedPreviousGasPrice);
  });

  test('FileDataService.getGasPrice - Returns null when exchange rate is missing', async () => {
    await integrationDomainOps.loadFileData(feeScheduleFiles);
    // no exchange rate data loaded

    await expect(FileDataService.getGasPrice(postSwitchoverTs)).resolves.toBeNull();
  });

  test('FileDataService.getGasPrice - Returns null when fee schedule is missing', async () => {
    await integrationDomainOps.loadFileData(exchangeRateFiles);
    // no fee schedule data loaded

    await expect(FileDataService.getGasPrice(postSwitchoverTs)).resolves.toBeNull();
  });

  test('FileDataService.getGasPrice - Returns cached result on repeated call with same filter', async () => {
    await integrationDomainOps.loadFileData(feeScheduleFiles);
    await integrationDomainOps.loadFileData(exchangeRateFiles);

    const spy = jest.spyOn(FileDataService, 'getLatestFileDataContents');

    const first = await FileDataService.getGasPrice(postSwitchoverTs);
    const second = await FileDataService.getGasPrice(postSwitchoverTs);

    expect(first).not.toBeNull();
    expect(second).toEqual(first); // same value served from cache
    // DB was called for each of feeSchedule + exchangeRate on first call only
    expect(spy).toHaveBeenCalledTimes(2);

    spy.mockRestore();
  });

  test('getGasPrices deduplicates lookups by hour bucket', async () => {
    await integrationDomainOps.loadFileData(feeScheduleFiles);
    await integrationDomainOps.loadFileData(exchangeRateFiles);

    const spy = jest.spyOn(FileDataService, 'getLatestFileDataContents');

    const gasPriceMap = await FileDataService.getGasPrices([postSwitchoverTs, postSwitchoverTs, postSwitchoverTs]);

    expect(gasPriceMap.get(postSwitchoverTs)).toBe(expectedLatestGasPrice);
    // one fee schedule + one exchange rate load for the same hour bucket
    expect(spy).toHaveBeenCalledTimes(2);

    spy.mockRestore();
  });

  test('getGasPrices resolves pre and post switchover timestamps in different hours', async () => {
    // Cache keys are hour-truncated, so choose timestamps far enough apart to avoid sharing a bucket
    // across the legacy → simple fee schedule switchover.
    const nanosPerHour = 3_600_000_000_000n;
    const preTs = SIMPLE_FEES_SWITCHOVER_TIMESTAMP - nanosPerHour;
    const postTs = SIMPLE_FEES_SWITCHOVER_TIMESTAMP + nanosPerHour;
    const spacedFeeScheduleFiles = [
      {
        consensus_timestamp: preTs.toString(),
        entity_id: feeScheduleEntityId.getEncodedId().toString(),
        file_data: makeFeeScheduleFileData(previousLegacyGas, 2000000000),
        transaction_type: 17,
      },
      {
        consensus_timestamp: postTs.toString(),
        entity_id: simpleFeeScheduleEntityId.getEncodedId().toString(),
        file_data: makeSimpleFeeScheduleFileData(latestGasTinycents),
        transaction_type: 19,
      },
    ];

    await integrationDomainOps.loadFileData(spacedFeeScheduleFiles);
    await integrationDomainOps.loadFileData(exchangeRateFiles);

    const gasPriceMap = await FileDataService.getGasPrices([preTs, postTs]);

    expect(gasPriceMap.get(preTs)).toBe(expectedPreviousGasPrice);
    expect(gasPriceMap.get(postTs)).toBe(expectedLatestGasPrice);
  });

  test('FileDataService.getGasPrice - uses mainnet switchover when network is MAINNET', async () => {
    const originalNetwork = config.network;
    config.network = 'MAINNET';
    FileDataService.clearFeeScheduleCache();

    try {
      const nanosPerHour = 3_600_000_000_000n;
      const mainnetPre = MAINNET_SIMPLE_FEES_SWITCHOVER_TIMESTAMP - nanosPerHour;
      const mainnetPost = MAINNET_SIMPLE_FEES_SWITCHOVER_TIMESTAMP + nanosPerHour;
      const mainnetFiles = [
        {
          consensus_timestamp: mainnetPre.toString(),
          entity_id: feeScheduleEntityId.getEncodedId().toString(),
          file_data: makeFeeScheduleFileData(previousLegacyGas, 2000000000),
          transaction_type: 17,
        },
        {
          consensus_timestamp: mainnetPost.toString(),
          entity_id: simpleFeeScheduleEntityId.getEncodedId().toString(),
          file_data: makeSimpleFeeScheduleFileData(latestGasTinycents),
          transaction_type: 19,
        },
      ];

      await integrationDomainOps.loadFileData(mainnetFiles);
      await integrationDomainOps.loadFileData(exchangeRateFiles);

      await expect(FileDataService.getGasPrice(mainnetPre)).resolves.toBe(expectedPreviousGasPrice);
      await expect(FileDataService.getGasPrice(mainnetPost)).resolves.toBe(expectedLatestGasPrice);
    } finally {
      config.network = originalNetwork;
      FileDataService.clearFeeScheduleCache();
    }
  });
});

describe('FileDataService.truncateToStartOfHour', () => {
  test('rounds consensus timestamp down to start of hour in nanoseconds', () => {
    const refTimestamp = 1_654_321_987_654_321_987n;

    expect(FileDataService.truncateToStartOfHour(refTimestamp)).toBe(1_654_318_800_000_000_000n);
  });
});

describe('FileDataService.getGasPriceForType', () => {
  const exchangeRate = makeExchangeRate();

  test('converts GAS extra using current exchange rate within the expiry hour (Simple FeeSchedule)', () => {
    const feeSchedule = new FeeSchedule({
      file_data: makeSimpleFeeScheduleFileData(1000),
      consensus_timestamp: 1,
    });
    const refTimestamp = 7_200_000_000_000n;

    const gasPrice = FileDataService.getGasPriceForType(feeSchedule, exchangeRate, refTimestamp);

    expect(gasPrice).toBe(gasPriceInTinybars(1000, 200, 100));
  });

  test('converts GAS extra using next exchange rate after the expiry hour (Simple FeeSchedule)', () => {
    const feeSchedule = new FeeSchedule({
      file_data: makeSimpleFeeScheduleFileData(1000),
      consensus_timestamp: 1,
    });
    const refTimestamp = 10_800_000_000_000n;

    const gasPrice = FileDataService.getGasPriceForType(feeSchedule, exchangeRate, refTimestamp);

    expect(gasPrice).toBe(gasPriceInTinybars(1000, 400, 300));
  });

  test('converts gas from legacy CurrentAndNextFeeSchedule', () => {
    const feeSchedule = new FeeSchedule({
      file_data: makeFeeScheduleFileData(852000, 2000000000),
      consensus_timestamp: 1,
    });
    const refTimestamp = 7_200_000_000_000n;

    const gasPrice = FileDataService.getGasPriceForType(feeSchedule, exchangeRate, refTimestamp);

    // 852000 legacy gas / 1000 = 852 tinycents
    expect(gasPrice).toBe(legacyGasPriceInTinybars(852000, 200, 100));
  });

  test('uses current fee schedule and exchange rate within the expiry hour', () => {
    const feeSchedule = new FeeSchedule({
      file_data: makeCurrentAndNextFeeScheduleFileData(1000000, 5000000, 7200),
      consensus_timestamp: 1,
    });
    const refTimestamp = 7_200_000_000_000n;

    const gasPrice = FileDataService.getGasPriceForType(feeSchedule, exchangeRate, refTimestamp);

    // 1000000 / 1000 = 1000 tinycents
    expect(gasPrice).toBe(legacyGasPriceInTinybars(1000000, 200, 100));
  });

  test('uses next fee schedule and exchange rate after the expiry hour', () => {
    const feeSchedule = new FeeSchedule({
      file_data: makeCurrentAndNextFeeScheduleFileData(1000000, 5000000, 7200),
      consensus_timestamp: 1,
    });
    const refTimestamp = 10_800_000_000_000n;

    const gasPrice = FileDataService.getGasPriceForType(feeSchedule, exchangeRate, refTimestamp);

    // 5000000 / 1000 = 5000 tinycents
    expect(gasPrice).toBe(legacyGasPriceInTinybars(5000000, 400, 300));
  });

  test('returns null when simple schedule has no GAS extra', () => {
    const feeSchedule = new FeeSchedule({
      file_data: makeSimpleFeeScheduleWithoutGas(),
      consensus_timestamp: 1,
    });

    expect(FileDataService.getGasPriceForType(feeSchedule, exchangeRate, 1n)).toBeNull();
  });

  test('skips non-ContractCall functionalities in legacy schedules', () => {
    const feeSchedule = new FeeSchedule({
      file_data: makeMultiTypeFeeScheduleFileData(
        {
          [HederaFunctionality.CryptoTransfer]: 999000,
          [HederaFunctionality.ContractCall]: 852000,
        },
        2000000000
      ),
      consensus_timestamp: 1,
    });

    const gasPrice = FileDataService.getGasPriceForType(feeSchedule, exchangeRate, 7_200_000_000_000n);

    expect(gasPrice).toBe(legacyGasPriceInTinybars(852000, 200, 100));
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
    expect(FileDataService.convertGasPriceToTinyBars(10, 100, 200)).toBe(5n);
  });

  test('returns minimum fee of 1 tinybar', () => {
    expect(FileDataService.convertGasPriceToTinyBars(1, 1, 1000)).toBe(1n);
  });

  test('returns null for invalid input', () => {
    expect(FileDataService.convertGasPriceToTinyBars(null, 100, 200)).toBeNull();
    expect(FileDataService.convertGasPriceToTinyBars(10, 100, 0)).toBeNull();
    expect(FileDataService.convertGasPriceToTinyBars(10, 'abc', 200)).toBeNull();
    expect(FileDataService.convertGasPriceToTinyBars(10, 100, undefined)).toBeNull();
  });
});

describe('FileDataService.convertLegacyGasPriceToTinyBars', () => {
  test('converts legacy gas price by dividing by FEE_DIVISOR_FACTOR', () => {
    expect(FileDataService.convertLegacyGasPriceToTinyBars(852000, 100, 200)).toBe(426n);
  });

  test('returns minimum fee of 1 tinybar for tiny legacy gas values', () => {
    expect(FileDataService.convertLegacyGasPriceToTinyBars(500, 1, 1000)).toBe(1n);
  });
});
