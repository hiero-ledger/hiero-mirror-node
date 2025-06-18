// SPDX-License-Identifier: Apache-2.0

import {Range} from 'pg-range';
import sinon from 'sinon';

import config from '../../config';
import {NANOSECONDS_PER_MILLISECOND, orderFilterValues} from '../../constants';
import integrationDomainOps from '../integrationDomainOps';
import {setupIntegrationTest} from '../integrationUtils';
import testExports, {bindTimestampRange} from '../../timestampRange';
import * as util from '../../utils';

setupIntegrationTest();

describe('bindTimestampRange', () => {
  const {ASC, DESC} = orderFilterValues;

  describe('bindTimestampRange=true', () => {
    let clock;
    const now = Date.parse('2022-10-15T08:10:15.333Z');
    const nowInNs = BigInt(now) * NANOSECONDS_PER_MILLISECOND;
    const {
      query: {maxTransactionsTimestampRangeNs},
    } = config;

    beforeEach(async () => {
      await integrationDomainOps.loadTransactions([{consensus_timestamp: 1606123456000111222n, payerAccountId: 2000}]);
      config.query.bindTimestampRange = true;
      clock = sinon.useFakeTimers({
        now,
        shouldAdvanceTime: true,
        shouldClearNativeTimers: true,
        toFake: [
          'setTimeout',
          'clearTimeout',
          'setImmediate',
          'clearImmediate',
          'setInterval',
          'clearInterval',
          'Date',
          'hrtime',
          'performance',
        ],
      });
    });

    afterEach(() => {
      config.query.bindTimestampRange = false;
      clock.restore();
    });

    const spec = [
      {
        name: 'empty and desc',
        expected: Range(nowInNs - maxTransactionsTimestampRangeNs + 1n, nowInNs),
      },
      {
        name: 'empty and asc',
        order: ASC,
        expected: Range(1606123456000111222n, 1606123456000111222n + maxTransactionsTimestampRangeNs - 1n),
      },
      {
        name: 'no adjustment',
        hasNext: false,
        range: Range(1000n, maxTransactionsTimestampRangeNs + 999n),
        expected: Range(1000n, maxTransactionsTimestampRangeNs + 999n),
      },
      {
        name: 'adjust lower bound',
        range: Range(1000n, 1000n + maxTransactionsTimestampRangeNs),
        expected: Range(1001n, 1000n + maxTransactionsTimestampRangeNs),
      },
      {
        name: 'adjust upper bound',
        range: Range(1000n, 1000n + maxTransactionsTimestampRangeNs),
        order: ASC,
        expected: Range(1000n, 999n + maxTransactionsTimestampRangeNs),
      },
    ];
    test.each(spec)('$name', async ({hasNext = true, expected, range, order = DESC}) => {
      const expectedWithNext = {range: expected};
      if (hasNext) {
        expectedWithNext.next = util.nsToSecNs(order === DESC ? expected.begin : expected.end);
      }
      await expect(bindTimestampRange(range, order)).resolves.toEqual(expectedWithNext);
    });
  });

  describe('bindTimestampRange=false', () => {
    const spec = [
      {name: 'null', expected: {range: null}},
      {name: 'no lower bound', expected: {range: Range(undefined, 1500n)}},
      {name: 'no upper bound', expected: {range: Range(1500n, undefined)}},
      {name: 'closed range', expected: {range: Range(1500n, 3000n)}},
    ];
    test.each(spec)('$name', async ({expected, order = DESC}) => {
      await expect(bindTimestampRange(expected.range, order)).resolves.toEqual(expected);
    });
  });
});

describe('getFirstTransactionTimestamp', () => {
  test('no transaction expect 0n', async () => {
    await expect(testExports.getFirstTransactionTimestamp()).resolves.toEqual(0n);
  });

  test('first transaction timestamp', async () => {
    await integrationDomainOps.loadTransactions([
      {consensus_timestamp: 1606123456000111222n, payerAccountId: 2000},
      {consensus_timestamp: 1606137065111222333n, payerAccountId: 2000},
    ]);
    await expect(testExports.getFirstTransactionTimestamp()).resolves.toEqual(1606123456000111222n);
  });
});
