// SPDX-License-Identifier: Apache-2.0

import console from 'console';
import fetchMock, {manageFetchMockGlobally} from '@fetch-mock/jest';
import {InvalidArgumentError} from 'commander';
import {jest} from '@jest/globals';
import {report} from '../src/report.js';
import {ReportFile} from '../src/reportfile.js';
import fs from 'fs';

manageFetchMockGlobally(jest);
global.console = console;

const fromDate = '2024-12-17';
let outputFile;

beforeEach(() => {
  outputFile = `report-${fromDate}.csv`;
  fs.rmSync(outputFile, {force: true});
  fetchMock.mockReset({includeSticky: true});
});

describe('Generate report', () => {
  test('From date equal to to date', async () => {
    const r = report({account: ['0.0.1000'], combined: true, network: 'testnet', fromDate, toDate: fromDate});
    expect(r).rejects.toThrow(InvalidArgumentError);
    expect(fs.existsSync(outputFile)).toBeFalsy();
  });

  test('From date after to date', async () => {
    const r = report({account: ['0.0.1000'], combined: true, network: 'testnet', fromDate, toDate: '2024-12-16'});
    expect(r).rejects.toThrow(InvalidArgumentError);
    expect(fs.existsSync(outputFile)).toBeFalsy();
  });

  test('No accounts', async () => {
    await report({account: [], combined: true, network: 'testnet', fromDate, toDate: '2024-12-18'});
    expect(fs.existsSync(outputFile)).toBeFalsy();
  });

  test('Missing account', async () => {
    const accountUrl = 'https://testnet.mirrornode.hedera.com/api/v1/accounts/0.0.1000?timestamp=1734393600';

    fetchMock.mockGlobal().get(accountUrl, {_status: {messages: 'Not found'}});

    await report({account: ['0.0.1000'], combined: true, network: 'testnet', fromDate, toDate: '2024-12-18'});
    expect(fs.existsSync(outputFile)).toBeFalsy();
  });

  test('No transactions', async () => {
    const accountUrl = 'https://testnet.mirrornode.hedera.com/api/v1/accounts/0.0.1000?timestamp=1734393600';
    const testnetUrl =
      'https://testnet.mirrornode.hedera.com/api/v1/transactions?account.id=0.0.1000&limit=100&order=asc&timestamp=gt:1734393500&timestamp=lt:1734480000';

    fetchMock.mockGlobal().get(accountUrl, {balance: {balance: 100, timestamp: 1734393500}});
    fetchMock.mockGlobal().get(testnetUrl, {_status: {messages: 'Not found'}});

    await report({account: ['0.0.1000'], combined: true, network: 'testnet', fromDate, toDate: '2024-12-18'});
    expect(fs.existsSync(outputFile)).toBeFalsy();
  });

  test('Single account', async () => {
    const accountUrl = 'https://testnet.mirrornode.hedera.com/api/v1/accounts/0.0.1000?timestamp=1734393600';
    const testnetUrl =
      'https://testnet.mirrornode.hedera.com/api/v1/transactions?account.id=0.0.1000&limit=100&order=asc&timestamp=gt:1734393500&timestamp=lt:1734480000';

    fetchMock.mockGlobal().get(accountUrl, {balance: {balance: 10000, timestamp: 1734393500}});
    fetchMock.mockGlobal().get(testnetUrl, {
      transactions: [
        {
          charged_tx_fee: 15,
          consensus_timestamp: '1734393500.000000001', // This transaction will not show up in output since it's before ts
          transfers: [
            {account: '0.0.3', amount: 1},
            {account: '0.0.98', amount: 2},
            {account: '0.0.800', amount: 3},
            {account: '0.0.801', amount: 4},
            {account: '0.0.802', amount: 5},
            {account: '0.0.1000', amount: -15},
          ],
          transaction_id: '0.0.1000-1734415200-000000000',
        },
        {
          charged_tx_fee: 15,
          consensus_timestamp: '1734415200.000000001',
          transfers: [
            {account: '0.0.3', amount: 1},
            {account: '0.0.98', amount: 2},
            {account: '0.0.800', amount: 3},
            {account: '0.0.801', amount: 4},
            {account: '0.0.802', amount: 5},
            {account: '0.0.1000', amount: -1015},
            {account: '0.0.1001', amount: 1000},
          ],
          transaction_id: '0.0.1000-1734415200-000000000',
        },
        {
          charged_tx_fee: 15,
          consensus_timestamp: '1734415200.000000003',
          transfers: [
            {account: '0.0.3', amount: 1},
            {account: '0.0.98', amount: 2},
            {account: '0.0.800', amount: 3},
            {account: '0.0.801', amount: 4},
            {account: '0.0.802', amount: 5},
            {account: '0.0.1000', amount: 985},
            {account: '0.0.1001', amount: -1000},
          ],
          transaction_id: '0.0.1000-1734415200-000000002',
        },
        {
          charged_tx_fee: 15,
          consensus_timestamp: '1734415200.000000005',
          transfers: [
            {account: '0.0.3', amount: 1},
            {account: '0.0.98', amount: 2},
            {account: '0.0.800', amount: 3},
            {account: '0.0.801', amount: 4},
            {account: '0.0.802', amount: 5},
            {account: '0.0.1000', amount: -15},
          ],
          transaction_id: '0.0.1000-1734415200-000000004',
        },
      ],
      links: {next: null},
    });

    await report({account: ['0.0.1000'], combined: true, network: 'testnet', fromDate, toDate: '2024-12-18'});
    const data = fs.readFileSync(outputFile, 'utf8');
    expect(data).toBe(
      ReportFile.HEADER +
        '2024-12-17T06:00:00.000000001Z,0.0.1000,0.0.1001,0.00000015,-0.00001015,0.00008970,https://hashscan.io/testnet/transaction/1734415200.000000001\n' +
        '2024-12-17T06:00:00.000000003Z,0.0.1001,0.0.1000,0.00000015,0.00000985,0.00009955,https://hashscan.io/testnet/transaction/1734415200.000000003\n' +
        '2024-12-17T06:00:00.000000005Z,0.0.1000,fee accounts,0.00000015,-0.00000015,0.00009940,https://hashscan.io/testnet/transaction/1734415200.000000005\n'
    );
  });

  test('Multiple accounts, separate reports', async () => {
    const accountUrl1 = 'https://testnet.mirrornode.hedera.com/api/v1/accounts/0.0.1000?timestamp=1734393600';
    const accountUrl2 = 'https://testnet.mirrornode.hedera.com/api/v1/accounts/0.0.1001?timestamp=1734393600';
    const testnetUrl1 =
      'https://testnet.mirrornode.hedera.com/api/v1/transactions?account.id=0.0.1000&limit=100&order=asc&timestamp=gt:1734393500&timestamp=lt:1734480000';
    const testnetUrl2 =
      'https://testnet.mirrornode.hedera.com/api/v1/transactions?account.id=0.0.1001&limit=100&order=asc&timestamp=gt:1734393500&timestamp=lt:1734480000';

    fetchMock.mockGlobal().get(accountUrl1, {balance: {balance: 10000, timestamp: 1734393500}});
    fetchMock.mockGlobal().get(accountUrl2, {balance: {balance: 20000, timestamp: 1734393500}});
    fetchMock.mockGlobal().get(testnetUrl1, {
      transactions: [
        {
          charged_tx_fee: 15,
          consensus_timestamp: '1734415200.000000001',
          transfers: [
            {account: '0.0.3', amount: 1},
            {account: '0.0.98', amount: 2},
            {account: '0.0.800', amount: 3},
            {account: '0.0.801', amount: 4},
            {account: '0.0.802', amount: 5},
            {account: '0.0.1000', amount: -1015},
            {account: '0.0.1001', amount: 1000},
          ],
          transaction_id: '0.0.1000-1734415200-000000000',
        },
      ],
      links: {next: null},
    });
    fetchMock.mockGlobal().get(testnetUrl2, {
      transactions: [
        {
          charged_tx_fee: 15,
          consensus_timestamp: '1734415200.000000003',
          transfers: [
            {account: '0.0.3', amount: 1},
            {account: '0.0.98', amount: 2},
            {account: '0.0.800', amount: 3},
            {account: '0.0.801', amount: 4},
            {account: '0.0.802', amount: 5},
            {account: '0.0.1001', amount: -15},
          ],
          transaction_id: '0.0.1001-1734415200-000000002',
        },
      ],
      links: {next: null},
    });

    await report({account: ['0.0.1000', '0.0.1001'], network: 'testnet', fromDate, toDate: '2024-12-18'});
    const report1 = fs.readFileSync(`report-${fromDate}-0.0.1000.csv`, 'utf8');
    expect(report1).toBe(
      ReportFile.HEADER +
        '2024-12-17T06:00:00.000000001Z,0.0.1000,0.0.1001,0.00000015,-0.00001015,0.00008985,https://hashscan.io/testnet/transaction/1734415200.000000001\n'
    );

    const report2 = fs.readFileSync(`report-${fromDate}-0.0.1001.csv`, 'utf8');
    expect(report2).toBe(
      ReportFile.HEADER +
        '2024-12-17T06:00:00.000000003Z,0.0.1001,fee accounts,0.00000015,-0.00000015,0.00019985,https://hashscan.io/testnet/transaction/1734415200.000000003\n'
    );
  });

  test('Multiple accounts, combined report', async () => {
    const accountUrl1 = 'https://testnet.mirrornode.hedera.com/api/v1/accounts/0.0.1000?timestamp=1734393600';
    const accountUrl2 = 'https://testnet.mirrornode.hedera.com/api/v1/accounts/0.0.1001?timestamp=1734393600';
    const testnetUrl1 =
      'https://testnet.mirrornode.hedera.com/api/v1/transactions?account.id=0.0.1000&limit=100&order=asc&timestamp=gt:1734393500&timestamp=lt:1734480000';
    const testnetUrl2 =
      'https://testnet.mirrornode.hedera.com/api/v1/transactions?account.id=0.0.1001&limit=100&order=asc&timestamp=gt:1734393500&timestamp=lt:1734480000';

    const transactionsResponse = {
      transactions: [
        {
          charged_tx_fee: 15,
          consensus_timestamp: '1734415200.000000001',
          transfers: [
            {account: '0.0.3', amount: 1},
            {account: '0.0.98', amount: 2},
            {account: '0.0.800', amount: 3},
            {account: '0.0.801', amount: 4},
            {account: '0.0.802', amount: 5},
            {account: '0.0.1000', amount: -1015},
            {account: '0.0.1001', amount: 1000},
          ],
          transaction_id: '0.0.1000-1734415200-000000000',
        },
      ],
      links: {next: null},
    };

    fetchMock.mockGlobal().get(accountUrl1, {balance: {balance: 10000, timestamp: 1734393500}});
    fetchMock.mockGlobal().get(accountUrl2, {balance: {balance: 20000, timestamp: 1734393500}});
    fetchMock.mockGlobal().get(testnetUrl1, transactionsResponse);
    fetchMock.mockGlobal().get(testnetUrl2, transactionsResponse);

    await report({
      account: ['0.0.1000', '0.0.1001'],
      combined: true,
      network: 'testnet',
      fromDate,
      toDate: '2024-12-18',
    });
    const report1 = fs.readFileSync(outputFile, 'utf8');
    expect(report1).toBe(
      ReportFile.HEADER +
        '2024-12-17T06:00:00.000000001Z,0.0.1000,0.0.1001,0.0,0.00001000,0.00021000,https://hashscan.io/testnet/transaction/1734415200.000000001\n' +
        '2024-12-17T06:00:00.000000001Z,0.0.1000,0.0.1001,0.00000015,-0.00001015,0.00008985,https://hashscan.io/testnet/transaction/1734415200.000000001\n'
    );
  });
});
