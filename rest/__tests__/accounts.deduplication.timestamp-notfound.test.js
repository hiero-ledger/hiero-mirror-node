// SPDX-License-Identifier: Apache-2.0

import {setupIntegrationTest} from './integrationUtils';
import integrationDomainOps from './integrationDomainOps';
import * as utils from '../utils';
import request from 'supertest';
import server from '../server';
import * as constants from '../constants';
import EntityId from '../entityId';

setupIntegrationTest();

describe('Accounts deduplicate timestamp not found tests', () => {
  const nanoSecondsPerSecond = 10n ** 9n;
  const fifteenDaysInNs = constants.ONE_DAY_IN_NS * 15n;
  const tenDaysInNs = constants.ONE_DAY_IN_NS * 10n;
  const currentNs = BigInt(Date.now()) * constants.NANOSECONDS_PER_MILLISECOND;
  const beginningOfCurrentMonth = utils.getFirstDayOfMonth(currentNs);
  const beginningOfPreviousMonth = utils.getFirstDayOfMonth(beginningOfCurrentMonth - 1n);
  const tenDaysInToPreviousMonth = beginningOfPreviousMonth + tenDaysInNs;
  const middleOfPreviousMonth = beginningOfPreviousMonth + fifteenDaysInNs;

  const balanceTimestamp1 = middleOfPreviousMonth + nanoSecondsPerSecond * 4n;
  const balanceTimestamp2 = tenDaysInToPreviousMonth + nanoSecondsPerSecond * 4n;
  const timestampRange1 = middleOfPreviousMonth + nanoSecondsPerSecond * 7n;

  beforeEach(async () => {
    await integrationDomainOps.loadAccounts([
      {
        num: 3,
      },
      {
        num: 7,
      },
      {
        balance: 80,
        balance_timestamp: balanceTimestamp1,
        num: 8,
        alias: 'KGNABD5L3ZGSRVUCSPDR7TONZSRY3D5OMEBKQMVTD2AC6JL72HMQ',
        public_key: '519a008fabde4d28d68293c71fcdcdcca38d8fae6102a832b31e802f257fd1d9',
        timestamp_range: `[${timestampRange1},)`,
        staked_node_id: 1,
        staked_account_id: 1,
      },
      {
        balance: 30,
        balance_timestamp: balanceTimestamp2,
        num: 8,
        alias: 'KGNABD5L3ZGSRVUCSPDR7TONZSRY3D5OMEBKQMVTD2AC6JL72HMQ',
        public_key: '519a008fabde4d28d68293c71fcdcdcca38d8fae6102a832b31e802f257fd1d9',
        timestamp_range: `[${beginningOfPreviousMonth}, ${timestampRange1})`,
        staked_node_id: 2,
        staked_account_id: 2,
      },
      {
        balance: 75,
        balance_timestamp: '5000000000',
        num: 9,
        alias: 'HIQQEXWKW53RKN4W6XXC4Q232SYNZ3SZANVZZSUME5B5PRGXL663UAQA',
        public_key: '519a008fabde4d28d68293c71fcdcdcca38d8fae6102a832b31e802f257fd1d8',
        timestamp_range: `[${timestampRange1},)`,
        staked_node_id: 1,
        staked_account_id: 1,
      },
      {
        num: 98,
      },
    ]);
    await integrationDomainOps.loadBalances([
      {
        timestamp: balanceTimestamp2,
        id: 2,
        balance: 2,
      },
      {
        timestamp: balanceTimestamp2,
        id: 8,
        balance: 555,
      },
      {
        timestamp: '5000000000',
        id: 2,
        balance: 2,
      },
      {
        timestamp: '5000000000',
        id: 9,
        balance: 400,
      },
    ]);
  });
  const accountId = EntityId.parseString('8');
  const testSpecs = [
    {
      name: 'Accounts not found',
      urls: [
        `/api/v1/accounts/${accountId.toString()}?timestamp=lte:${utils.nsToSecNs(beginningOfPreviousMonth - 1n)}`,
        `/api/v1/accounts/${accountId.toString()}?timestamp=lt:${utils.nsToSecNs(beginningOfPreviousMonth)}`,
        `/api/v1/accounts/${accountId.shard}.${
          accountId.realm
        }.KGNABD5L3ZGSRVUCSPDR7TONZSRY3D5OMEBKQMVTD2AC6JL72HMQ?timestamp=lte:${utils.nsToSecNs(
          beginningOfPreviousMonth - 1n
        )}`,
        `/api/v1/accounts/${accountId.shard}.${
          accountId.realm
        }.KGNABD5L3ZGSRVUCSPDR7TONZSRY3D5OMEBKQMVTD2AC6JL72HMQ?timestamp=lt:${utils.nsToSecNs(
          beginningOfPreviousMonth
        )}`,
      ],
      expected: {
        message: 'Not found',
      },
    },
  ];

  testSpecs.forEach((spec) => {
    spec.urls.forEach((url) => {
      test(url, async () => {
        const response = await request(server).get(url);
        expect(response.status).toEqual(404);
        expect(response.body._status.messages[0]).toEqual(spec.expected);
      });
    });
  });
});
