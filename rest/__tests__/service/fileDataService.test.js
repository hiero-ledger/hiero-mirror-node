// SPDX-License-Identifier: Apache-2.0

import {FileData} from '../../model';
import {FileDataService} from '../../service';
import integrationDomainOps from '../integrationDomainOps';
import {setupIntegrationTest} from '../integrationUtils';
import EntityId from '../../entityId';

setupIntegrationTest();

const entityId = EntityId.parseString('112');
const files = [
  {
    consensus_timestamp: 1,
    entity_id: entityId.toString(),
    file_data: Buffer.from('0a1008b0ea0110cac1181a0608a0a1d09306121008b0ea0110e18e191a0608b0bdd09306', 'hex'),
    transaction_type: 17,
  },
  {
    consensus_timestamp: 2,
    entity_id: entityId.toString(),
    file_data: Buffer.from('0a1008b0ea0110f5f3191a06089085d09306121008b0ea0110cac1181a0608a0a1d09306', 'hex'),
    transaction_type: 16,
  },
  {
    consensus_timestamp: 3,
    entity_id: entityId.toString(),
    file_data: Buffer.from('0a1008b0ea0110e9c81a1a060880e9cf9306121008b0ea0110f5f3191a06089085d09306', 'hex'),
    transaction_type: 19,
  },
  {
    consensus_timestamp: 4,
    entity_id: entityId.toString(),
    file_data: Buffer.from('0a1008b0ea0110f9bb1b1a0608f0cccf9306121008b0ea0110e9c81a1a060880e9cf9306', 'hex'),
    transaction_type: 19,
  },
];

const fileId = entityId.getEncodedId();
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
    await integrationDomainOps.loadFileData(files);

    await expect(FileDataService.getExchangeRate({whereQuery: []})).resolves.toMatchObject(expectedLatestFile);
  });

  test('FileDataService.getExchangeRate - Row match w previous latest', async () => {
    await integrationDomainOps.loadFileData(files);

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
    await expect(FileDataService.getLatestFileDataContents(fileId, {whereQuery: []})).resolves.toBeNull();
  });

  const expectedPreviousFile = {
    consensus_timestamp: 2,
    file_data: Buffer.concat([files[0].file_data, files[1].file_data]),
  };

  const expectedLatestFile = {
    consensus_timestamp: 4,
    file_data: Buffer.from('0a1008b0ea0110f9bb1b1a0608f0cccf9306121008b0ea0110e9c81a1a060880e9cf9306', 'hex'),
  };

  test('FileDataService.getLatestFileDataContents - Row match w latest', async () => {
    await integrationDomainOps.loadFileData(files);
    await expect(FileDataService.getLatestFileDataContents(fileId, {whereQuery: []})).resolves.toMatchObject(
      expectedLatestFile
    );
  });

  test('FileDataService.getLatestFileDataContents - Row match w previous latest', async () => {
    await integrationDomainOps.loadFileData(files);

    const where = [
      {
        query: `${FileData.CONSENSUS_TIMESTAMP} <= `,
        param: expectedPreviousFile.consensus_timestamp,
      },
    ];
    await expect(FileDataService.getLatestFileDataContents(fileId, {whereQuery: where})).resolves.toMatchObject(
      expectedPreviousFile
    );
  });
});

const feeScheduleFiles = [
  {
    consensus_timestamp: 1,
    entity_id: 111,
    file_data: Buffer.from('0a280a0a08541a061a0440a8953a0a0a08061a061a0440a8aa330a0a', 'hex'),
    transaction_type: 17,
  },
  {
    consensus_timestamp: 2,
    entity_id: 111,
    file_data: Buffer.from('08071a061a0440c0843d120208011200', 'hex'),
    transaction_type: 16,
  },
  {
    consensus_timestamp: 3,
    entity_id: 111,
    file_data: Buffer.from('0a220a0808541a041a0240150a0808061a041a0240010a0808071a041a02400112020801', 'hex'),
    transaction_type: 19,
  },
  {
    consensus_timestamp: 4,
    entity_id: 111,
    file_data: Buffer.from(
      '0a280a0a08541a061a04408888340a0a08061a061a0440889d2d0a0a08071a061a0440b0b63c120208011200',
      'hex'
    ),
    transaction_type: 19,
  },
];

const expectedFeeSchedulePreviousFile = {
  timestamp: 2,
  current_feeSchedule: [
    {
      fees: [{servicedata: {gas: {low: 953000}}}],
      hederaFunctionality: 84,
    },
    {
      fees: [{servicedata: {gas: {low: 841000}}}],
      hederaFunctionality: 6,
    },
    {
      fees: [{servicedata: {gas: {low: 1000000}}}],
      hederaFunctionality: 7,
    },
  ],
  next_feeSchedule: [],
};

const expectedFeeScheduleLatestFile = {
  timestamp: 4,
  current_feeSchedule: [
    {
      fees: [{servicedata: {gas: {low: 853000}}}],
      hederaFunctionality: 84,
    },
    {
      fees: [{servicedata: {gas: {low: 741000}}}],
      hederaFunctionality: 6,
    },
    {
      fees: [{servicedata: {gas: {low: 990000}}}],
      hederaFunctionality: 7,
    },
  ],
  next_feeSchedule: [],
};

describe('FileDataService.getFeeSchedule', () => {
  test('FileDataService.getFeeSchedule - No match', async () => {
    await expect(FileDataService.getFeeSchedule({whereQuery: []})).resolves.toBeNull();
  });

  test('FileDataService.getFeeSchedule - Row match w latest', async () => {
    await integrationDomainOps.loadFileData(feeScheduleFiles);
    await expect(FileDataService.getFeeSchedule({whereQuery: []})).resolves.toMatchObject(
      expectedFeeScheduleLatestFile
    );
  });

  test('FileDataService.getFeeSchedule - Row match w previous latest', async () => {
    await integrationDomainOps.loadFileData(feeScheduleFiles);

    const where = [
      {
        query: `${FileData.CONSENSUS_TIMESTAMP} <= `,
        param: expectedFeeSchedulePreviousFile.timestamp,
      },
    ];
    await expect(FileDataService.getFeeSchedule({whereQuery: where})).resolves.toMatchObject(
      expectedFeeSchedulePreviousFile
    );
  });
});
