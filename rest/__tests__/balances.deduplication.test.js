// SPDX-License-Identifier: Apache-2.0

import {setupIntegrationTest} from './integrationUtils';
import integrationDomainOps from './integrationDomainOps';
import * as utils from '../utils';
import request from 'supertest';
import server from '../server';
import * as constants from '../constants';
import EntityId from '../entityId';

setupIntegrationTest();

describe('Balances deduplicate tests', () => {
  const nanoSecondsPerSecond = 10n ** 9n;
  const fifteenDaysInNs = constants.ONE_DAY_IN_NS * 15n;
  const tenDaysInNs = constants.ONE_DAY_IN_NS * 10n;
  const currentNs = BigInt(Date.now()) * constants.NANOSECONDS_PER_MILLISECOND;

  const beginningOfCurrentMonth = utils.getFirstDayOfMonth(currentNs);
  const beginningOfCurrentMonthSeconds = utils.nsToSecNs(beginningOfCurrentMonth);

  const middleOfCurrentMonth = beginningOfCurrentMonth + fifteenDaysInNs;
  const middleOfCurrentMonthSeconds = utils.nsToSecNs(middleOfCurrentMonth);

  const beginningOfNextMonth = utils.getFirstDayOfMonth(beginningOfCurrentMonth + 3n * fifteenDaysInNs);
  const beginningOfNextMonthSeconds = utils.nsToSecNs(beginningOfNextMonth);

  // About a year in the future
  const yearFutureSeconds = utils.nsToSecNs(beginningOfNextMonth + 24n * fifteenDaysInNs);

  const endOfPreviousMonth = beginningOfCurrentMonth - 1n;
  const endOfPreviousMonthSeconds = utils.nsToSecNs(endOfPreviousMonth);
  const endOfPreviousMonthSecondsMinusOne = utils.nsToSecNs(beginningOfCurrentMonth - 2n);

  const beginningOfPreviousMonth = utils.getFirstDayOfMonth(endOfPreviousMonth);
  const beginningOfPreviousMonthSeconds = utils.nsToSecNs(beginningOfPreviousMonth);

  // About a year in the past
  const yearPreviousSeconds = utils.nsToSecNs(beginningOfNextMonth - 24n * fifteenDaysInNs);

  const tenDaysInToPreviousMonth = beginningOfPreviousMonth + tenDaysInNs;
  const tenDaysInToPreviousMonthSeconds = utils.nsToSecNs(tenDaysInToPreviousMonth);

  const middleOfPreviousMonth = beginningOfPreviousMonth + fifteenDaysInNs;
  const middleOfPreviousMonthSeconds = utils.nsToSecNs(middleOfPreviousMonth);
  const middleOfPreviousMonthSecondsMinusOne = utils.nsToSecNs(middleOfPreviousMonth - 1n);

  const entityId2 = EntityId.parseString('2').toString();
  const entityId16 = EntityId.parseString('16').toString();
  const entityId17 = EntityId.parseString('17').toString();
  const entityId18 = EntityId.parseString('18').toString();
  const entityId19 = EntityId.parseString('19').toString();
  const entityId20 = EntityId.parseString('20').toString();
  const entityId21 = EntityId.parseString('21').toString();
  const entityId70000 = EntityId.parseString('70000').toString();
  const entityId70007 = EntityId.parseString('70007').toString();
  const entityId90000 = EntityId.parseString('90000').toString();

  beforeEach(async () => {
    await integrationDomainOps.loadBalances([
      {
        timestamp: beginningOfPreviousMonth - nanoSecondsPerSecond,
        id: entityId2,
        balance: 1,
      },
      {
        timestamp: beginningOfPreviousMonth - nanoSecondsPerSecond,
        id: entityId16,
        balance: 16,
      },
      {
        timestamp: beginningOfPreviousMonth,
        id: entityId2,
        balance: 2,
      },
      {
        timestamp: beginningOfPreviousMonth,
        id: entityId17,
        balance: 70,
        tokens: [
          {
            token_num: entityId70000,
            balance: 7,
          },
          {
            token_num: 70007,
            balance: 700,
          },
        ],
      },
      {
        timestamp: tenDaysInToPreviousMonth,
        id: entityId2,
        balance: 222,
      },
      {
        timestamp: tenDaysInToPreviousMonth,
        id: entityId18,
        balance: 80,
      },
      {
        timestamp: tenDaysInToPreviousMonth,
        id: entityId20,
        balance: 19,
        tokens: [
          {
            token_num: entityId90000,
            balance: 1000,
          },
        ],
      },
      {
        timestamp: middleOfPreviousMonth,
        id: entityId2,
        balance: 223,
      },
      {
        timestamp: middleOfPreviousMonth,
        id: entityId19,
        balance: 90,
      },
      {
        timestamp: endOfPreviousMonth,
        id: entityId20,
        balance: 20,
        tokens: [
          {
            token_num: entityId90000,
            balance: 1001,
          },
        ],
      },
      {
        timestamp: endOfPreviousMonth,
        id: entityId2,
        balance: 22,
      },
      {
        timestamp: endOfPreviousMonth,
        id: entityId21,
        realm_num: 1,
        balance: 21,
      },
    ]);
  });

  const testSpecs = [
    {
      name: 'Accounts with upper and lower bounds and ne',
      urls: [
        `/api/v1/balances?timestamp=lt:${endOfPreviousMonthSeconds}&timestamp=gte:${beginningOfPreviousMonthSeconds}&timestamp=ne:${tenDaysInToPreviousMonthSeconds}&order=asc`,
      ],
      expected: {
        timestamp: `${middleOfPreviousMonthSeconds}`,
        balances: [
          {
            account: entityId2,
            balance: 223,
            tokens: [],
          },
          {
            account: entityId17,
            balance: 70,
            tokens: [
              {
                balance: 7,
                token_id: entityId70000,
              },
              {
                balance: 700,
                token_id: entityId70007,
              },
            ],
          },
          // Though 0.1.18's balance is at NE timestamp, its results are expected
          {
            account: entityId18,
            balance: 80,
            tokens: [],
          },
          {
            account: entityId19,
            balance: 90,
            tokens: [],
          },
          // Though 0.1.20's balance is at NE timestamp, its results are expected
          {
            account: entityId20,
            balance: 19,
            tokens: [
              {
                balance: 1000,
                token_id: entityId90000,
              },
            ],
          },
        ],
        links: {
          next: null,
        },
      },
    },
    {
      name: 'Accounts with upper and lower bounds lt',
      urls: [
        `/api/v1/balances?account.id=gte:${entityId16}&account.id=lt:${entityId21}&timestamp=lt:${endOfPreviousMonthSeconds}&timestamp=gte:${beginningOfPreviousMonthSeconds}&order=asc`,
      ],
      expected: {
        timestamp: `${middleOfPreviousMonthSeconds}`,
        balances: [
          {
            account: entityId17,
            balance: 70,
            tokens: [
              {
                balance: 7,
                token_id: entityId70000,
              },
              {
                balance: 700,
                token_id: entityId70007,
              },
            ],
          },
          {
            account: entityId18,
            balance: 80,
            tokens: [],
          },
          {
            account: entityId19,
            balance: 90,
            tokens: [],
          },
          {
            account: entityId20,
            balance: 19,
            tokens: [
              {
                balance: 1000,
                token_id: entityId90000,
              },
            ],
          },
        ],
        links: {
          next: null,
        },
      },
    },
    {
      name: 'Accounts with upper and lower bounds lte',
      urls: [
        `/api/v1/balances?account.id=gte:${entityId16}&account.id=lt:${entityId21}&timestamp=lte:${endOfPreviousMonthSeconds}&timestamp=gte:${beginningOfPreviousMonthSeconds}&order=asc`,
      ],
      expected: {
        timestamp: `${endOfPreviousMonthSeconds}`,
        balances: [
          {
            account: entityId17,
            balance: 70,
            tokens: [
              {
                balance: 7,
                token_id: entityId70000,
              },
              {
                balance: 700,
                token_id: entityId70007,
              },
            ],
          },
          {
            account: entityId18,
            balance: 80,
            tokens: [],
          },
          {
            account: entityId19,
            balance: 90,
            tokens: [],
          },
          {
            account: entityId20,
            balance: 20,
            tokens: [
              {
                balance: 1001,
                token_id: entityId90000,
              },
            ],
          },
        ],
        links: {
          next: null,
        },
      },
    },
    {
      name: 'Account and timestamp equals',
      urls: [
        `/api/v1/balances?account.id=gte:${entityId16}&account.id=lt:${entityId21}&timestamp=${endOfPreviousMonthSeconds}`,
      ],
      expected: {
        timestamp: `${endOfPreviousMonthSeconds}`,
        balances: [
          {
            account: entityId20,
            balance: 20,
            tokens: [
              {
                balance: 1001,
                token_id: entityId90000,
              },
            ],
          },
          {
            account: entityId19,
            balance: 90,
            tokens: [],
          },
          {
            account: entityId18,
            balance: 80,
            tokens: [],
          },
          {
            account: entityId17,
            balance: 70,
            tokens: [
              {
                balance: 700,
                token_id: entityId70007,
              },
              {
                balance: 7,
                token_id: entityId70000,
              },
            ],
          },
        ],
        links: {
          next: null,
        },
      },
    },
    {
      name: 'Lower bound less than beginning of previous month',
      urls: [
        `/api/v1/balances?timestamp=gte:${beginningOfPreviousMonthSeconds}`,
        `/api/v1/balances?timestamp=gte:${yearPreviousSeconds}`,
      ],
      expected: {
        timestamp: `${endOfPreviousMonthSeconds}`,
        balances: [
          {
            account: entityId21,
            balance: 21,
            tokens: [],
          },
          {
            account: entityId20,
            balance: 20,
            tokens: [
              {
                balance: 1001,
                token_id: entityId90000,
              },
            ],
          },
          {
            account: entityId19,
            balance: 90,
            tokens: [],
          },
          {
            account: entityId18,
            balance: 80,
            tokens: [],
          },
          {
            account: entityId17,
            balance: 70,
            tokens: [
              {
                balance: 700,
                token_id: entityId70007,
              },
              {
                balance: 7,
                token_id: entityId70000,
              },
            ],
          },
          {
            account: entityId2,
            balance: 22,
            tokens: [],
          },
        ],
        links: {
          next: null,
        },
      },
    },
    {
      name: 'Lower bound greater than beginning of previous month',
      urls: [
        `/api/v1/balances?timestamp=gt:${beginningOfPreviousMonthSeconds}`,
        `/api/v1/balances?timestamp=gte:${tenDaysInToPreviousMonthSeconds}`,
      ],
      expected: {
        timestamp: `${endOfPreviousMonthSeconds}`,
        balances: [
          {
            account: entityId21,
            balance: 21,
            tokens: [],
          },
          {
            account: entityId20,
            balance: 20,
            tokens: [
              {
                balance: 1001,
                token_id: entityId90000,
              },
            ],
          },
          {
            account: entityId19,
            balance: 90,
            tokens: [],
          },
          {
            account: entityId18,
            balance: 80,
            tokens: [],
          },
          {
            account: entityId17,
            balance: 70,
            tokens: [
              {
                balance: 700,
                token_id: entityId70007,
              },
              {
                balance: 7,
                token_id: entityId70000,
              },
            ],
          },
          {
            account: entityId2,
            balance: 22,
            tokens: [],
          },
        ],
        links: {
          next: null,
        },
      },
    },
    {
      name: 'Upper bound within current month or later',
      urls: [
        `/api/v1/balances?timestamp=${beginningOfCurrentMonthSeconds}`,
        `/api/v1/balances?timestamp=${middleOfCurrentMonthSeconds}`,
        `/api/v1/balances?timestamp=${beginningOfNextMonthSeconds}`,
        `/api/v1/balances?timestamp=${yearFutureSeconds}`,
        `/api/v1/balances?timestamp=lte:${beginningOfCurrentMonthSeconds}`,
        `/api/v1/balances?timestamp=lte:${middleOfCurrentMonthSeconds}`,
        `/api/v1/balances?timestamp=lte:${beginningOfNextMonthSeconds}`,
        `/api/v1/balances?timestamp=lte:${yearFutureSeconds}`,
        `/api/v1/balances?timestamp=lt:${beginningOfCurrentMonthSeconds}`,
        `/api/v1/balances?timestamp=lt:${middleOfCurrentMonthSeconds}`,
        `/api/v1/balances?timestamp=lt:${beginningOfNextMonthSeconds}`,
        `/api/v1/balances?timestamp=lt:${yearFutureSeconds}`,
      ],
      expected: {
        timestamp: `${endOfPreviousMonthSeconds}`,
        balances: [
          {
            account: entityId21,
            balance: 21,
            tokens: [],
          },
          {
            account: entityId20,
            balance: 20,
            tokens: [
              {
                balance: 1001,
                token_id: entityId90000,
              },
            ],
          },
          {
            account: entityId19,
            balance: 90,
            tokens: [],
          },
          {
            account: entityId18,
            balance: 80,
            tokens: [],
          },
          {
            account: entityId17,
            balance: 70,
            tokens: [
              {
                balance: 700,
                token_id: entityId70007,
              },
              {
                balance: 7,
                token_id: entityId70000,
              },
            ],
          },
          {
            account: entityId2,
            balance: 22,
            tokens: [],
          },
        ],
        links: {
          next: null,
        },
      },
    },
    {
      name: 'Upper bound in middle and near end of previous month',
      urls: [
        `/api/v1/balances?timestamp=${middleOfPreviousMonthSeconds}`,
        `/api/v1/balances?timestamp=${endOfPreviousMonthSecondsMinusOne}`,
      ],
      expected: {
        timestamp: `${middleOfPreviousMonthSeconds}`,
        balances: [
          {
            account: entityId20,
            balance: 19,
            tokens: [
              {
                balance: 1000,
                token_id: entityId90000,
              },
            ],
          },
          {
            account: entityId19,
            balance: 90,
            tokens: [],
          },
          {
            account: entityId18,
            balance: 80,
            tokens: [],
          },
          {
            account: entityId17,
            balance: 70,
            tokens: [
              {
                balance: 700,
                token_id: entityId70007,
              },
              {
                balance: 7,
                token_id: entityId70000,
              },
            ],
          },
          {
            account: entityId2,
            balance: 223,
            tokens: [],
          },
        ],
        links: {
          next: null,
        },
      },
    },
    {
      name: 'Upper bound in middle of previous month minus one',
      urls: [`/api/v1/balances?timestamp=${middleOfPreviousMonthSecondsMinusOne}`],
      expected: {
        timestamp: `${tenDaysInToPreviousMonthSeconds}`,
        balances: [
          {
            account: entityId20,
            balance: 19,
            tokens: [
              {
                balance: 1000,
                token_id: entityId90000,
              },
            ],
          },
          {
            account: entityId18,
            balance: 80,
            tokens: [],
          },
          {
            account: entityId17,
            balance: 70,
            tokens: [
              {
                balance: 700,
                token_id: entityId70007,
              },
              {
                balance: 7,
                token_id: entityId70000,
              },
            ],
          },
          {
            account: entityId2,
            balance: 222,
            tokens: [],
          },
        ],
        links: {
          next: null,
        },
      },
    },
    {
      name: 'Upper bound in the past and lower bound greater than end of previous month',
      urls: [
        `/api/v1/balances?account.id=gte:${entityId16}&account.id=lt:${entityId21}&timestamp=1567296000.000000000`,
        `/api/v1/balances?timestamp=1567296000.000000000`,
        `/api/v1/balances?timestamp=lte:1567296000.000000000`,
        `/api/v1/balances?timestamp=lt:1567296000.000000000`,
        `/api/v1/balances?timestamp=gte:${beginningOfCurrentMonthSeconds}`,
        `/api/v1/balances?timestamp=gt:${endOfPreviousMonthSeconds}`,
        `/api/v1/balances?timestamp=gte:${yearFutureSeconds}`,
        `/api/v1/balances?timestamp=gte:${yearFutureSeconds}&timestamp=lt:${yearPreviousSeconds}`,
      ],
      expected: {
        timestamp: null,
        balances: [],
        links: {
          next: null,
        },
      },
    },
  ];

  testSpecs.forEach((spec) => {
    spec.urls.forEach((url) => {
      test(spec.name, async () => {
        const response = await request(server).get(url);
        expect(response.status).toEqual(200);
        expect(response.body).toEqual(spec.expected);
      });
    });
  });
});
