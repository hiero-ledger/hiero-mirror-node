// SPDX-License-Identifier: Apache-2.0

import {jest} from '@jest/globals';
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
import {FeeSchedule, FileData} from '../../model';
import TransactionType from '../../model/transactionType';
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

const makeTransactionFeeSchedule = (hederaFunctionality, gas) => {
  const feeComponents = create(FeeComponentsSchema, {gas});
  const feeData = create(FeeDataSchema, {servicedata: feeComponents});
  return create(TransactionFeeScheduleSchema, {
    hederaFunctionality,
    fees: [feeData],
  });
};

const makeFeeScheduleFileData = (gas, expirySeconds, hederaFunctionality = HederaFunctionality.ContractCall) => {
  const feeSchedule = create(FeeScheduleSchema, {
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

const makeMultiTypeFeeScheduleFileData = (gasByFunctionality, expirySeconds) => {
  const feeSchedule = create(FeeScheduleSchema, {
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

// max(1, (gas * hbarEquiv) / (centEquiv * 1000)) with next exchange rate (cent=435305, hbar=30000)
const gasPriceInTinybars = (gas, centEquiv = 435305, hbarEquiv = 30000) => {
  const fee = (BigInt(gas) * BigInt(hbarEquiv)) / (BigInt(centEquiv) * 1000n);
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

describe('FileDataService.getFeeSchedule tests', () => {
  beforeEach(() => {
    FileDataService.clearFeeScheduleCache();
  });

  const previousGas = 123456;
  const latestGas = 789012;

  const previousExpiry = 2000000000;
  const latestExpiry = 3000000000;

  const previousFeeScheduleData = makeFeeScheduleFileData(previousGas, previousExpiry);
  const latestFeeScheduleData = makeFeeScheduleFileData(latestGas, latestExpiry);

  const feeScheduleFiles = [
    {
      consensus_timestamp: 11,
      entity_id: feeScheduleEntityId.toString(),
      file_data: previousFeeScheduleData,
      transaction_type: 17,
    },
    {
      consensus_timestamp: 13,
      entity_id: feeScheduleEntityId.toString(),
      file_data: latestFeeScheduleData,
      transaction_type: 19,
    },
  ];

  // ContractCall gas in tinybars: max(1, (gas * hbarEquiv) / (centEquiv * 1000))
  // Latest: gas=789012, next rate (now > current_expiration): hbar=30000, cent=435305 → 54n
  const expectedLatestGasPrice = 54n;
  // At consensus_timestamp <= 12: fee file at 11 (gas=123456), current rate cent=450041 → 8n
  const expectedPreviousGasPrice = 8n;

  test('FileDataService.getFeeSchedule - No match', async () => {
    await expect(FileDataService.getFeeSchedule({whereQuery: []})).resolves.toBeNull();
  });

  test('FileDataService.getFeeSchedule - Row match w latest', async () => {
    await integrationDomainOps.loadFileData(feeScheduleFiles);
    await integrationDomainOps.loadFileData(exchangeRateFiles);

    const result = await FileDataService.getFeeSchedule({whereQuery: []});
    expect(result).toBe(expectedLatestGasPrice);
  });

  test('FileDataService.getFeeSchedule - Row match w previous latest', async () => {
    await integrationDomainOps.loadFileData(feeScheduleFiles);
    await integrationDomainOps.loadFileData(exchangeRateFiles);

    const where = [
      {
        query: `${FileData.CONSENSUS_TIMESTAMP} <= `,
        param: 12,
      },
    ];
    const result = await FileDataService.getFeeSchedule({whereQuery: where});
    expect(result).toBe(expectedPreviousGasPrice);
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

    const where = [{query: `${FileData.CONSENSUS_TIMESTAMP} <= `, param: 12}];
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

  describe('by transaction type', () => {
    const contractCallGas = 100_000;
    const contractCreateGas = 500_000;
    const ethereumTransactionGas = 200_000;
    const multiTypeExpiry = 3_000_000_000;

    const multiTypeFeeScheduleFiles = [
      {
        consensus_timestamp: 13,
        entity_id: feeScheduleEntityId.toString(),
        file_data: makeMultiTypeFeeScheduleFileData(
          {
            [HederaFunctionality.ContractCall]: contractCallGas,
            [HederaFunctionality.ContractCreate]: contractCreateGas,
            [HederaFunctionality.EthereumTransaction]: ethereumTransactionGas,
          },
          multiTypeExpiry
        ),
        transaction_type: 19,
      },
    ];

    const expectedContractCallGasPrice = gasPriceInTinybars(contractCallGas);
    const expectedContractCreateGasPrice = gasPriceInTinybars(contractCreateGas);
    const expectedEthereumTransactionGasPrice = gasPriceInTinybars(ethereumTransactionGas);

    const loadMultiTypeFeeScheduleData = async () => {
      await integrationDomainOps.loadFileData(multiTypeFeeScheduleFiles);
      await integrationDomainOps.loadFileData(exchangeRateFiles);
    };

    test('returns ContractCall gas price when transaction type is ContractCall', async () => {
      await loadMultiTypeFeeScheduleData();

      const result = await FileDataService.getFeeSchedule(
        {whereQuery: []},
        FeeSchedule.TRANSACTION_TYPES.CONTRACT_CALL
      );

      expect(result).toBe(expectedContractCallGasPrice);
    });

    test('returns ContractCreate gas price when transaction type is ContractCreate', async () => {
      await loadMultiTypeFeeScheduleData();

      const result = await FileDataService.getFeeSchedule(
        {whereQuery: []},
        FeeSchedule.TRANSACTION_TYPES.CONTRACT_CREATE
      );

      expect(result).toBe(expectedContractCreateGasPrice);
    });

    test('returns EthereumTransaction gas price when transaction type is EthereumTransaction', async () => {
      await loadMultiTypeFeeScheduleData();

      const result = await FileDataService.getFeeSchedule(
        {whereQuery: []},
        FeeSchedule.TRANSACTION_TYPES.ETHEREUM_TRANSACTION
      );

      expect(result).toBe(expectedEthereumTransactionGasPrice);
    });

    test('caches gas prices separately per transaction type', async () => {
      await loadMultiTypeFeeScheduleData();

      const filterQueries = {whereQuery: []};
      const spy = jest.spyOn(FileDataService, 'getLatestFileDataContents');

      const contractCall = await FileDataService.getFeeSchedule(
        filterQueries,
        FeeSchedule.TRANSACTION_TYPES.CONTRACT_CALL
      );
      const contractCreate = await FileDataService.getFeeSchedule(
        filterQueries,
        FeeSchedule.TRANSACTION_TYPES.CONTRACT_CREATE
      );
      const contractCallCached = await FileDataService.getFeeSchedule(
        filterQueries,
        FeeSchedule.TRANSACTION_TYPES.CONTRACT_CALL
      );
      const contractCreateCached = await FileDataService.getFeeSchedule(
        filterQueries,
        FeeSchedule.TRANSACTION_TYPES.CONTRACT_CREATE
      );

      expect(contractCall).toBe(expectedContractCallGasPrice);
      expect(contractCreate).toBe(expectedContractCreateGasPrice);
      expect(contractCallCached).toBe(contractCall);
      expect(contractCreateCached).toBe(contractCreate);
      // fee schedule + exchange rate loaded once per transaction type on first access
      expect(spy).toHaveBeenCalledTimes(4);

      spy.mockRestore();
    });
  });
});

describe('FileDataService.truncateToStartOfHour', () => {
  test('rounds consensus timestamp down to start of hour in nanoseconds', () => {
    const refTimestamp = 1_654_321_987_654_321_987n;

    expect(FileDataService.truncateToStartOfHour(refTimestamp)).toBe(1_654_318_800_000_000_000n);
  });
});

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

describe('FeeSchedule.getFeeScheduleType', () => {
  test('maps Hedera transaction proto ids to fee schedule types', () => {
    expect(FeeSchedule.getFeeScheduleType(TransactionType.getProtoId('CONTRACTCALL'))).toBe(
      FeeSchedule.TRANSACTION_TYPES.CONTRACT_CALL
    );
    expect(FeeSchedule.getFeeScheduleType(TransactionType.getProtoId('CONTRACTCREATEINSTANCE'))).toBe(
      FeeSchedule.TRANSACTION_TYPES.CONTRACT_CREATE
    );
    expect(FeeSchedule.getFeeScheduleType(TransactionType.getProtoId('ETHEREUMTRANSACTION'))).toBe(
      FeeSchedule.TRANSACTION_TYPES.ETHEREUM_TRANSACTION
    );
  });

  test('defaults unknown transaction types to ContractCall', () => {
    expect(FeeSchedule.getFeeScheduleType(TransactionType.getProtoId('CRYPTOTRANSFER'))).toBe(
      FeeSchedule.TRANSACTION_TYPES.CONTRACT_CALL
    );
  });
});
