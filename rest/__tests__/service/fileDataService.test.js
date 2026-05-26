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
import {FileData} from '../../model';
import {FileDataService} from '../../service';
import integrationDomainOps from '../integrationDomainOps';
import {setupIntegrationTest} from '../integrationUtils';
import EntityId from '../../entityId';

setupIntegrationTest();

const exchangeRateEntityId = EntityId.parseString('112');
const feeScheduleEntityId = EntityId.parseString('111');
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

const makeFeeScheduleFileData = (gas, expirySeconds) => {
  const feeComponents = create(FeeComponentsSchema, {
    gas,
  });
  const feeData = create(FeeDataSchema, {
    servicedata: feeComponents,
  });
  const transactionFeeSchedule = create(TransactionFeeScheduleSchema, {
    hederaFunctionality: HederaFunctionality.ContractCall,
    fees: [feeData],
  });
  const feeSchedule = create(FeeScheduleSchema, {
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

describe('FileDataService.getFeeSchedule tests', () => {
  const previousGas = 123456;
  const latestGas = 789012;

  const previousExpiry = 2000000000;
  const latestExpiry = 3000000000;

  const previousFeeScheduleData = makeFeeScheduleFileData(previousGas, previousExpiry);
  const latestFeeScheduleData = makeFeeScheduleFileData(latestGas, latestExpiry);

  const feeScheduleFiles = [
    {
      consensus_timestamp: 1,
      entity_id: feeScheduleEntityId.toString(),
      file_data: previousFeeScheduleData,
      transaction_type: 17,
    },
    {
      consensus_timestamp: 3,
      entity_id: feeScheduleEntityId.toString(),
      file_data: latestFeeScheduleData,
      transaction_type: 19,
    },
  ];

  test('FileDataService.getFeeSchedule - No match', async () => {
    await expect(FileDataService.getFeeSchedule({whereQuery: []})).resolves.toBeNull();
  });

  test('FileDataService.getFeeSchedule - Row match w latest', async () => {
    await integrationDomainOps.loadFileData(feeScheduleFiles);
    await integrationDomainOps.loadFileData(exchangeRateFiles);

    const result = await FileDataService.getFeeSchedule({whereQuery: []});
    expect(result).not.toBeNull();
    expect(result).toBeGreaterThan(0n);
  });

  test('FileDataService.getFeeSchedule - Row match w previous latest', async () => {
    await integrationDomainOps.loadFileData(feeScheduleFiles);
    await integrationDomainOps.loadFileData(exchangeRateFiles);

    const where = [
      {
        query: `${FileData.CONSENSUS_TIMESTAMP} <= `,
        param: 2,
      },
    ];
    const result = await FileDataService.getFeeSchedule({whereQuery: where});
    expect(result).not.toBeNull();
    expect(result).toBeGreaterThan(0n);
  });

  test('FileDataService.getFeeSchedule - Returns null when exchange rate is missing', async () => {
    await integrationDomainOps.loadFileData(feeScheduleFiles);
    // no exchange rate data loaded

    await expect(FileDataService.getFeeSchedule({whereQuery: []})).resolves.toBeNull();
  });

  test('FileDataService.getFeeSchedule - Returns null when fee schedule is missing', async () => {
    await integrationDomainOps.loadFileData(exchangeRateFiles);
    // no fee schedule data loaded

    await expect(FileDataService.getFeeSchedule({whereQuery: []})).resolves.toBeNull();
  });

  test('FileDataService.getFeeSchedule - Returns cached result on repeated call with same filter', async () => {
    await integrationDomainOps.loadFileData(feeScheduleFiles);
    await integrationDomainOps.loadFileData(exchangeRateFiles);

    const where = [{query: `${FileData.CONSENSUS_TIMESTAMP} <= `, param: 2}];
    const filterQueries = {whereQuery: where};

    const spy = jest.spyOn(FileDataService, 'getLatestFileDataContents');

    const first = await FileDataService.getFeeSchedule(filterQueries);
    const second = await FileDataService.getFeeSchedule(filterQueries);

    expect(first).not.toBeNull();
    expect(second).toEqual(first); // same value served from cache
    // DB was called for each of feeSchedule + exchangeRate on first call only
    expect(spy).toHaveBeenCalledTimes(2);

    spy.mockRestore();
  });
});
