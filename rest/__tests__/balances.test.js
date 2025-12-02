// SPDX-License-Identifier: Apache-2.0

import sinon from 'sinon';

import balances from '../balances';
import {MAX_LONG} from '../constants';
import {opsMap} from '../utils';

describe('getOptimizedTimestampRange', () => {
  let clock;
  // Set now, so Date.now() returns the expected epoch millis
  const now = Date.parse('2022-10-15T08:10:15.333Z');

  beforeAll(() => {
    clock = sinon.useFakeTimers({
      now,
      shouldAdvanceTime: true,
      shouldClearNativeTimers: true,
    });
  });

  afterAll(() => {
    clock.restore();
  });

  const toNs = (value) => {
    if (typeof value !== 'string') {
      return value;
    }

    return BigInt(Date.parse(value)) * 10n ** 6n;
  };

  const convertBound = (bound) => {
    if (typeof bound !== 'object') {
      return toNs(bound);
    }

    const {value, delta} = bound;
    return toNs(value) + (delta ?? 0n);
  };

  const {lt, lte, gt, gte, ne} = opsMap;
  const spec = [
    {operators: [], params: [], expected: {lowerBound: '2022-04-01Z', upperBound: MAX_LONG, neParams: []}},
    {operators: [gt, lt], params: ['2022-10-11Z', '2022-10-10Z'], expected: {}},
    {
      operators: [gte, lte],
      params: ['2022-01-05Z', '2022-07-12Z'],
      expected: {lowerBound: '2022-01-05Z', upperBound: '2022-07-12Z', neParams: []},
    },
    {
      operators: [gte, lte],
      params: ['2022-01-05Z', '2022-10-02Z'],
      expected: {lowerBound: '2022-04-01Z', upperBound: '2022-10-02Z', neParams: []},
    },
    {
      operators: [gte, lte],
      params: ['2022-01-05Z', '2022-10-16Z'],
      expected: {lowerBound: '2022-04-01Z', upperBound: '2022-10-16Z', neParams: []},
    },
    {
      operators: [gte, lte],
      params: ['2022-09-10Z', '2022-10-16Z'],
      expected: {lowerBound: '2022-09-10Z', upperBound: '2022-10-16Z', neParams: []},
    },
    {
      operators: [gte, lte],
      params: ['2022-10-02Z', '2022-10-16Z'],
      expected: {lowerBound: '2022-10-02Z', upperBound: '2022-10-16Z', neParams: []},
    },
    {
      operators: [gte, lte],
      params: ['2022-10-18Z', '2023-02-16Z'],
      expected: {lowerBound: '2022-10-18Z', upperBound: '2023-02-16Z', neParams: []},
    },
    {
      operators: [gt],
      params: ['2022-10-10Z'],
      expected: {lowerBound: {value: '2022-10-10Z', delta: 1n}, upperBound: MAX_LONG, neParams: []},
    },
    {
      operators: [gt],
      params: ['2022-01-01Z'],
      expected: {lowerBound: '2022-04-01Z', upperBound: MAX_LONG, neParams: []},
    },
    {
      operators: [gte],
      params: ['2022-10-10Z'],
      expected: {lowerBound: '2022-10-10Z', upperBound: MAX_LONG, neParams: []},
    },
    {
      operators: [lt],
      params: ['2022-12-15Z'],
      expected: {lowerBound: '2022-04-01Z', upperBound: {value: '2022-12-15Z', delta: -1n}, neParams: []},
    },
    {
      operators: [lt, ne],
      params: ['2022-10-10Z', '2022-10-02Z'],
      expected: {lowerBound: '2022-04-01Z', upperBound: {value: '2022-10-10Z', delta: -1n}, neParams: ['2022-10-02Z']},
    },
    {
      operators: [lte],
      params: ['2022-10-10Z'],
      expected: {lowerBound: '2022-04-01Z', upperBound: '2022-10-10Z', neParams: []},
    },
    {
      operators: [lte],
      params: ['2022-09-20Z'],
      expected: {lowerBound: '2022-03-01Z', upperBound: '2022-09-20Z', neParams: []},
    },
    {
      operators: [gt, gte, lt, lte],
      params: ['2022-01-05Z', '2022-02-10Z', '2022-10-02Z', '2022-09-20Z'],
      expected: {lowerBound: '2022-03-1Z', upperBound: '2022-09-20Z', neParams: []},
    },
  ].map((s) => ({
    name: s.operators.map((op, index) => `consensus_timestamp${op}${s.params[index]}`).join(' and '),
    query: s.operators.map((op) => `consensus_timestamp ${op} ?`).join(' and '),
    params: s.params.map(toNs),
    expected: {
      lowerBound: convertBound(s.expected.lowerBound),
      upperBound: convertBound(s.expected.upperBound),
      neParams: s.expected.neParams?.map(toNs),
    },
  }));

  test.each(spec)('$name', ({query, params, expected}) => {
    const actual = balances.getOptimizedTimestampRange(query, params);
    expect(actual).toEqual(expected);
  });
});
