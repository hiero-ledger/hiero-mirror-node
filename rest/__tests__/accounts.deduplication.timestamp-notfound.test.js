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

  const entityId2 = EntityId.parseString('2');
  const entityId3 = EntityId.parseString('3');
  const entityId7 = EntityId.parseString('7');
  const entityId8 = EntityId.parseString('8');
  const entityId9 = EntityId.parseString('9');
  const entityId98 = EntityId.systemEntity.feeCollector;

  beforeEach(async () => {
    await integrationDomainOps.loadAccounts([
      {
        num: entityId3.num,
      },
      {
        num: entityId7.num,
      },
      {
        balance: 80,
        balance_timestamp: balanceTimestamp1,
        num: entityId8.num,
        alias: 'KGNABD5L3ZGSRVUCSPDR7TONZSRY3D5OMEBKQMVTD2AC6JL72HMQ',
        public_key: '519a008fabde4d28d68293c71fcdcdcca38d8fae6102a832b31e802f257fd1d9',
        timestamp_range: `[${timestampRange1},)`,
        staked_node_id: 1,
        staked_account_id: 1,
      },
      {
        balance: 30,
        balance_timestamp: balanceTimestamp2,
        num: entityId8.num,
        alias: 'KGNABD5L3ZGSRVUCSPDR7TONZSRY3D5OMEBKQMVTD2AC6JL72HMQ',
        public_key: '519a008fabde4d28d68293c71fcdcdcca38d8fae6102a832b31e802f257fd1d9',
        timestamp_range: `[${beginningOfPreviousMonth}, ${timestampRange1})`,
        staked_node_id: entityId2.num,
        staked_account_id: 2,
      },
      {
        balance: 75,
        balance_timestamp: '5000000000',
        num: entityId9.num,
        alias: 'HIQQEXWKW53RKN4W6XXC4Q232SYNZ3SZANVZZSUME5B5PRGXL663UAQA',
        public_key: '519a008fabde4d28d68293c71fcdcdcca38d8fae6102a832b31e802f257fd1d8',
        timestamp_range: `[${timestampRange1},)`,
        staked_node_id: 1,
        staked_account_id: 1,
      },
      {
        num: entityId98.num,
      },
    ]);
    await integrationDomainOps.loadBalances([
      {
        timestamp: balanceTimestamp2,
        id: entityId2.num,
        balance: 2,
      },
      {
        timestamp: balanceTimestamp2,
        id: entityId8.num,
        balance: 555,
      },
      {
        timestamp: '5000000000',
        id: entityId2.num,
        balance: 2,
      },
      {
        timestamp: '5000000000',
        id: entityId9.num,
        balance: 400,
      },
    ]);
  });
  const testSpecs = [
    {
      name: 'Accounts not found',
      urls: [
        `/api/v1/accounts/${entityId8.toString()}?timestamp=lte:${utils.nsToSecNs(beginningOfPreviousMonth - 1n)}`,
        `/api/v1/accounts/${entityId8.toString()}?timestamp=lt:${utils.nsToSecNs(beginningOfPreviousMonth)}`,
        `/api/v1/accounts/${entityId8.shard}.${
          entityId8.realm
        }.KGNABD5L3ZGSRVUCSPDR7TONZSRY3D5OMEBKQMVTD2AC6JL72HMQ?timestamp=lte:${utils.nsToSecNs(
          beginningOfPreviousMonth - 1n
        )}`,
        `/api/v1/accounts/${entityId8.shard}.${
          entityId8.realm
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
      test(spec.name, async () => {
        const response = await request(server).get(url);
        expect(response.status).toEqual(404);
        expect(response.body._status.messages[0]).toEqual(spec.expected);
      });
    });
  });
});
