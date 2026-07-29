// SPDX-License-Identifier: Apache-2.0

import {jest} from '@jest/globals';
import {create, toBinary} from '@bufbuild/protobuf';
import {Extra, ExtraFeeDefinitionSchema, FeeScheduleSchema} from '../../gen/fees/fee_schedule_pb.js';
import {ExchangeRate, FeeSchedule, FileData} from '../../model';
import {FileDataService} from '../../service';
import integrationDomainOps from '../integrationDomainOps';
import {setupIntegrationTest} from '../integrationUtils';
import EntityId from '../../entityId';

setupIntegrationTest();

const exchangeRateEntityId = EntityId.parseString('112');
const feeScheduleEntityId = EntityId.parseString('113');
const exchangeRateFiles = [
  {
    consensus_timestamp: 1,
    entity_id: exchangeRateEntityId.toString(),
    file_data: Buffer.from('0a1008b0ea0110cac1181a0608a0a1d09306121008b0ea0110e18e191a0608b0bdd09306', 'hex'),
    transaction_type: 17,
  },
  {
    consensus_timestamp: 2,
    entity_id: exchangeRateEntityId.toString(),
    file_data: Buffer.from('0a1008b0ea0110f5f3191a06089085d09306121008b0ea0110cac1181a0608a0a1d09306', 'hex'),
    transaction_type: 16,
  },
  {
    consensus_timestamp: 3,
    entity_id: exchangeRateEntityId.toString(),
    file_data: Buffer.from('0a1008b0ea0110e9c81a1a060880e9cf9306121008b0ea0110f5f3191a06089085d09306', 'hex'),
    transaction_type: 19,
  },
  {
    consensus_timestamp: 4,
    entity_id: exchangeRateEntityId.toString(),
    file_data: Buffer.from('0a1008b0ea0110f9bb1b1a0608f0cccf9306121008b0ea0110e9c81a1a060880e9cf9306', 'hex'),
    transaction_type: 19,
  },
];

const makeFeeScheduleFileData = (gasTinycents) => {
  return Buffer.from(
    toBinary(
      FeeScheduleSchema,
      create(FeeScheduleSchema, {
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

// max(1, (gasTinycents * hbarEquiv) / centEquiv)
const gasPriceInTinybars = (gasTinycents, centEquiv = 200, hbarEquiv = 100) => {
  const fee = (BigInt(gasTinycents) * BigInt(hbarEquiv)) / BigInt(centEquiv);
  return fee > 0n ? fee : 1n;
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

  const previousGasTinycents = 123;
  const latestGasTinycents = 789;

  const feeScheduleFiles = [
    {
      consensus_timestamp: 11,
      entity_id: feeScheduleEntityId.toString(),
      file_data: makeFeeScheduleFileData(previousGasTinycents),
      transaction_type: 17,
    },
    {
      consensus_timestamp: 13,
      entity_id: feeScheduleEntityId.toString(),
      file_data: makeFeeScheduleFileData(latestGasTinycents),
      transaction_type: 19,
    },
  ];

  // GAS tinycents to tinybars: max(1, gasTinycents * hbarEquiv / centEquiv)
  // Latest: gas=789, next rate (now > current_expiration): hbar=30000, cent=435305 → 54n
  const expectedLatestGasPrice = 54n;
  // At consensus_timestamp <= 12: fee file at 11 (gas=123), current rate cent=450041 → 8n
  const expectedPreviousGasPrice = 8n;

  test('FileDataService.getGasPrice - No match', async () => {
    await expect(FileDataService.getGasPrice()).resolves.toBeNull();
  });

  test('FileDataService.getGasPrice - Row match w latest', async () => {
    await integrationDomainOps.loadFileData(feeScheduleFiles);
    await integrationDomainOps.loadFileData(exchangeRateFiles);

    const result = await FileDataService.getGasPrice();
    expect(result).toBe(expectedLatestGasPrice);
  });

  test('FileDataService.getGasPrice - Row match w previous latest', async () => {
    await integrationDomainOps.loadFileData(feeScheduleFiles);
    await integrationDomainOps.loadFileData(exchangeRateFiles);

    const result = await FileDataService.getGasPrice(12n);
    expect(result).toBe(expectedPreviousGasPrice);
  });

  test('FileDataService.getGasPrice - Returns null when exchange rate is missing', async () => {
    await integrationDomainOps.loadFileData(feeScheduleFiles);
    // no exchange rate data loaded

    await expect(FileDataService.getGasPrice()).resolves.toBeNull();
  });

  test('FileDataService.getGasPrice - Returns null when fee schedule is missing', async () => {
    await integrationDomainOps.loadFileData(exchangeRateFiles);
    // no fee schedule data loaded

    await expect(FileDataService.getGasPrice()).resolves.toBeNull();
  });

  test('FileDataService.getGasPrice - Returns cached result on repeated call with same filter', async () => {
    await integrationDomainOps.loadFileData(feeScheduleFiles);
    await integrationDomainOps.loadFileData(exchangeRateFiles);

    const spy = jest.spyOn(FileDataService, 'getLatestFileDataContents');

    const first = await FileDataService.getGasPrice(12n);
    const second = await FileDataService.getGasPrice(12n);

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

    const gasPriceMap = await FileDataService.getGasPrices([12n, 12n, 12n]);

    expect(gasPriceMap.get(12n)).toBe(expectedPreviousGasPrice);
    // one fee schedule + one exchange rate load for the same hour bucket
    expect(spy).toHaveBeenCalledTimes(2);

    spy.mockRestore();
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

  test('converts GAS extra using current exchange rate within the expiry hour', () => {
    const feeSchedule = new FeeSchedule({
      file_data: makeFeeScheduleFileData(1000),
      consensus_timestamp: 1,
    });
    const refTimestamp = 7_200_000_000_000n;

    const gasPrice = FileDataService.getGasPriceForType(feeSchedule, exchangeRate, refTimestamp);

    expect(gasPrice).toBe(gasPriceInTinybars(1000, 200, 100));
  });

  test('converts GAS extra using next exchange rate after the expiry hour', () => {
    const feeSchedule = new FeeSchedule({
      file_data: makeFeeScheduleFileData(1000),
      consensus_timestamp: 1,
    });
    const refTimestamp = 10_800_000_000_000n;

    const gasPrice = FileDataService.getGasPriceForType(feeSchedule, exchangeRate, refTimestamp);

    expect(gasPrice).toBe(gasPriceInTinybars(1000, 400, 300));
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
  });
});
