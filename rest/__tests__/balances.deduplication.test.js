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
  const tenDaysInNs = constants.ONE_DAY_IN_NS * 10n;
  const ONE_YEAR_IN_NS = constants.ONE_DAY_IN_NS * 365n;

  const currentNs = BigInt(Date.now()) * constants.NANOSECONDS_PER_MILLISECOND;

  const SIX_MONTHS_IN_NS = constants.ONE_DAY_IN_NS * 30n * 6n;
  const HALF_WINDOW_IN_NS = SIX_MONTHS_IN_NS / 2n; // ~3 months

  // Start of our logical 6-month lookback window
  const windowStart = utils.getFirstDayOfMonth(currentNs, -6);
  const windowStartSeconds = utils.nsToSecNs(windowStart);

  // Early point in the window to exercise dedup behavior
  const tenDaysIntoWindow = windowStart + tenDaysInNs;
  const tenDaysIntoWindowSeconds = utils.nsToSecNs(tenDaysIntoWindow);

  // Midpoint of the logical 6-month window (~3 months after windowStart)
  const middleOfWindow = windowStart + HALF_WINDOW_IN_NS;
  const middleOfWindowSeconds = utils.nsToSecNs(middleOfWindow);
  const middleOfWindowSecondsMinusOne = utils.nsToSecNs(middleOfWindow - 1n);

  // End of our 6-month test window
  const windowEnd = windowStart + SIX_MONTHS_IN_NS - 1n;
  const windowEndSeconds = utils.nsToSecNs(windowEnd);
  const windowEndSecondsMinusOne = utils.nsToSecNs(windowEnd - 1n);
  const windowEndPlusOneSeconds = utils.nsToSecNs(windowEnd + 1n);

  // “Far future” / “far past” bounds to exercise clamping / empty results
  const yearFutureSeconds = utils.nsToSecNs(currentNs + ONE_YEAR_IN_NS);
  const yearPreviousSeconds = utils.nsToSecNs(currentNs - ONE_YEAR_IN_NS);

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
      // Just before the 6-month window, used to ensure lower bound behavior
      {
        timestamp: windowStart - nanoSecondsPerSecond,
        id: entityId2,
        balance: 1,
      },
      {
        timestamp: windowStart - ONE_YEAR_IN_NS,
        id: entityId16,
        balance: 16,
      },

      // Snapshot at the beginning of the window
      {
        timestamp: windowStart,
        id: entityId2,
        balance: 2,
      },
      {
        timestamp: windowStart,
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

      // Snapshot 10 days into the window
      {
        timestamp: tenDaysIntoWindow,
        id: entityId2,
        balance: 222,
      },
      {
        timestamp: tenDaysIntoWindow,
        id: entityId18,
        balance: 80,
      },
      {
        timestamp: tenDaysIntoWindow,
        id: entityId20,
        balance: 19,
        tokens: [
          {
            token_num: entityId90000,
            balance: 1000,
          },
        ],
      },

      // Snapshot at the middle of the 6-month window (~3 months in)
      {
        timestamp: middleOfWindow,
        id: entityId2,
        balance: 223,
      },
      {
        timestamp: middleOfWindow,
        id: entityId19,
        balance: 90,
      },

      // Final snapshot at the end of the 6-month window
      {
        timestamp: windowEnd,
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
        timestamp: windowEnd,
        id: entityId2,
        balance: 22,
      },
      {
        timestamp: windowEnd,
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
        `/api/v1/balances?timestamp=lt:${windowEndSeconds}&timestamp=gte:${windowStartSeconds}&timestamp=ne:${tenDaysIntoWindowSeconds}&order=asc`,
      ],
      expected: {
        timestamp: `${middleOfWindowSeconds}`,
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
          // Though 0.0.18's balance is at NE timestamp, its results are expected
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
          // Though 0.0.20's balance is at NE timestamp, its results are expected
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
        `/api/v1/balances?account.id=gte:${entityId16}&account.id=lt:${entityId21}&timestamp=lt:${windowEndSeconds}&timestamp=gte:${windowStartSeconds}&order=asc`,
      ],
      expected: {
        timestamp: `${middleOfWindowSeconds}`,
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
        `/api/v1/balances?account.id=gte:${entityId16}&account.id=lt:${entityId21}&timestamp=lte:${windowEndSeconds}&timestamp=gte:${windowStartSeconds}&order=asc`,
      ],
      expected: {
        timestamp: `${windowEndSeconds}`,
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
      urls: [`/api/v1/balances?account.id=gte:${entityId16}&account.id=lt:${entityId21}&timestamp=${windowEndSeconds}`],
      expected: {
        timestamp: `${windowEndSeconds}`,
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
      name: 'Lower bound less than window start',
      urls: [
        `/api/v1/balances?timestamp=gte:${windowStartSeconds}`,
        `/api/v1/balances?timestamp=gte:${yearPreviousSeconds}`,
      ],
      expected: {
        timestamp: `${windowEndSeconds}`,
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
      name: 'Lower bound greater than window start',
      urls: [
        `/api/v1/balances?timestamp=gt:${windowStartSeconds}`,
        `/api/v1/balances?timestamp=gte:${tenDaysIntoWindowSeconds}`,
      ],
      expected: {
        timestamp: `${windowEndSeconds}`,
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

    //
    // Upper bound at or after the last snapshot -> clamp to windowEnd snapshot
    //
    {
      name: 'Upper bound within 6 months window or later',
      urls: [
        // Directly at and beyond windowEnd
        `/api/v1/balances?timestamp=${windowEndSeconds}`,
        `/api/v1/balances?timestamp=${windowEndPlusOneSeconds}`,
        `/api/v1/balances?timestamp=${yearFutureSeconds}`,
        `/api/v1/balances?timestamp=lte:${windowEndPlusOneSeconds}`,
        `/api/v1/balances?timestamp=lte:${yearFutureSeconds}`,
        `/api/v1/balances?timestamp=lt:${windowEndPlusOneSeconds}`,
        `/api/v1/balances?timestamp=lt:${yearFutureSeconds}`,
      ],
      expected: {
        timestamp: `${windowEndSeconds}`,
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
      name: 'Upper bound in middle and near end of window',
      urls: [
        `/api/v1/balances?timestamp=${middleOfWindowSeconds}`,
        `/api/v1/balances?timestamp=${windowEndSecondsMinusOne}`,
      ],
      expected: {
        timestamp: `${middleOfWindowSeconds}`,
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
      name: 'Upper bound in middle of window minus one',
      urls: [`/api/v1/balances?timestamp=${middleOfWindowSecondsMinusOne}`],
      expected: {
        timestamp: `${tenDaysIntoWindowSeconds}`,
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

    //
    // No results when the requested time range is completely
    // beyond our last snapshot (or way in the past).
    //
    {
      name: 'Upper bound in the past and lower bound greater than end of window',
      urls: [
        // Way in the past, outside the data + 6-month lookback
        `/api/v1/balances?account.id=gte:${entityId16}&account.id=lt:${entityId21}&timestamp=1567296000.000000000`,
        `/api/v1/balances?timestamp=1567296000.000000000`,
        `/api/v1/balances?timestamp=lte:1567296000.000000000`,
        `/api/v1/balances?timestamp=lt:1567296000.000000000`,
        // Lower bound strictly greater than our last snapshot -> expect empty
        `/api/v1/balances?timestamp=gt:${windowEndSeconds}`,
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
