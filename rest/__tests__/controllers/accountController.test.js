// SPDX-License-Identifier: Apache-2.0

import {getResponseLimit} from '../../config';
import * as constants from '../../constants';
import * as utils from '../../utils';

const {default: defaultLimit} = getResponseLimit();

const {
  filterKeys: {SERIAL_NUMBER, SPENDER_ID, TOKEN_ID, ORDER, LIMIT, TIMESTAMP},
} = constants;
const {
  opsMap: {eq, gt, gte, lt, lte, ne},
} = utils;

import Bound from '../../controllers/bound';
import {AccountController} from '../../controllers';

const ownerAccountId = BigInt(1);

const serialEqFilter = {key: SERIAL_NUMBER, operator: eq, value: 5};
const serialGtFilter = {key: SERIAL_NUMBER, operator: gt, value: 6};
const serialGteFilter = {key: SERIAL_NUMBER, operator: gte, value: 7};
const serialLtFilter = {key: SERIAL_NUMBER, operator: lt, value: 15};
const serialLteFilter = {key: SERIAL_NUMBER, operator: lte, value: 16};

const spenderEqFilter = {key: SPENDER_ID, operator: eq, value: 1000};
const spenderGtFilter = {key: SPENDER_ID, operator: gt, value: 2000};
const spenderGteFilter = {key: SPENDER_ID, operator: gte, value: 3000};
const spenderLtFilter = {key: SPENDER_ID, operator: lt, value: 4000};
const spenderLteFilter = {key: SPENDER_ID, operator: lte, value: 5000};
const spenderEqInFilter = {key: SPENDER_ID, operator: eq, value: 1050};
const spenderEqInFilter2 = {key: SPENDER_ID, operator: eq, value: 1100};

const tokenIdEqFilter = {key: TOKEN_ID, operator: eq, value: 100};
const tokenIdGtFilter = {key: TOKEN_ID, operator: gt, value: 101};
const tokenIdGteFilter = {key: TOKEN_ID, operator: gte, value: 102};
const tokenIdLtFilter = {key: TOKEN_ID, operator: lt, value: 150};
const tokenIdLteFilter = {key: TOKEN_ID, operator: lte, value: 151};

// only used by staking rewards tests
const timestampEqFilter = {key: TIMESTAMP, operator: eq, value: 1000};
const timestampGtFilter = {key: TIMESTAMP, operator: gt, value: 2000};
const timestampGteFilter = {key: TIMESTAMP, operator: gte, value: 3000};
const timestampLtFilter = {key: TIMESTAMP, operator: lt, value: 4000};
const timestampLteFilter = {key: TIMESTAMP, operator: lte, value: 5000};
const timestampNeFilter = {key: TIMESTAMP, operator: ne, value: 6000};
const timestampEqFilter2 = {key: TIMESTAMP, operator: eq, value: 2000};
const timestampEqFilter3 = {key: TIMESTAMP, operator: eq, value: 3000};

describe('extractNftMultiUnionQuery', () => {
  const defaultExpected = {
    bounds: {
      primary: new Bound(TOKEN_ID, 'token_id'),
      secondary: new Bound(SERIAL_NUMBER, 'serial_number'),
    },
    lower: [],
    inner: [],
    upper: [],
    ownerAccountId,
    order: constants.orderFilterValues.DESC,
    limit: defaultLimit,
    spenderIdFilters: [],
    spenderIdInFilters: [],
  };

  const specs = [
    {
      name: 'empty',
      filters: [],
      expected: defaultExpected,
    },
    {
      name: 'order asc',
      filters: [{key: ORDER, operator: eq, value: 'asc'}],
      expected: {...defaultExpected, order: 'asc'},
    },
    {
      name: 'limit',
      filters: [{key: LIMIT, operator: eq, value: 60}],
      expected: {...defaultExpected, limit: 60},
    },
    {
      name: 'token eq',
      filters: [tokenIdEqFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          primary: Bound.create({equal: tokenIdEqFilter, filterKey: TOKEN_ID, viewModelKey: 'token_id'}),
          secondary: new Bound(SERIAL_NUMBER, 'serial_number'),
        },
        lower: [tokenIdEqFilter],
      },
    },
    {
      name: 'token gt',
      filters: [tokenIdGtFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          primary: Bound.create({lower: tokenIdGtFilter, filterKey: TOKEN_ID, viewModelKey: 'token_id'}),
          secondary: new Bound(SERIAL_NUMBER, 'serial_number'),
        },
        lower: [tokenIdGtFilter],
      },
    },
    {
      name: 'token lt',
      filters: [tokenIdLtFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          primary: Bound.create({upper: tokenIdLtFilter, filterKey: TOKEN_ID, viewModelKey: 'token_id'}),
          secondary: new Bound(SERIAL_NUMBER, 'serial_number'),
        },
        lower: [tokenIdLtFilter],
      },
    },
    {
      name: 'token gte',
      filters: [tokenIdGteFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          primary: Bound.create({lower: tokenIdGteFilter, filterKey: TOKEN_ID, viewModelKey: 'token_id'}),
          secondary: new Bound(SERIAL_NUMBER, 'serial_number'),
        },
        lower: [tokenIdGteFilter],
      },
    },
    {
      name: 'token lte',
      filters: [tokenIdLteFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          primary: Bound.create({upper: tokenIdLteFilter, filterKey: TOKEN_ID, viewModelKey: 'token_id'}),
          secondary: new Bound(SERIAL_NUMBER, 'serial_number'),
        },
        lower: [tokenIdLteFilter],
      },
    },
    {
      name: 'token lt and gt',
      filters: [tokenIdLtFilter, tokenIdGtFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          primary: Bound.create({
            lower: tokenIdGtFilter,
            upper: tokenIdLtFilter,
            filterKey: TOKEN_ID,
            viewModelKey: 'token_id',
          }),
          secondary: new Bound(SERIAL_NUMBER, 'serial_number'),
        },
        lower: [tokenIdGtFilter, tokenIdLtFilter],
      },
    },
    {
      name: 'token eq and serial eq',
      filters: [serialEqFilter, tokenIdEqFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          primary: Bound.create({equal: tokenIdEqFilter, filterKey: TOKEN_ID, viewModelKey: 'token_id'}),
          secondary: Bound.create({equal: serialEqFilter, filterKey: SERIAL_NUMBER, viewModelKey: 'serial_number'}),
        },
        lower: [tokenIdEqFilter, serialEqFilter],
      },
    },
    {
      name: 'token gt and serial eq',
      filters: [serialEqFilter, tokenIdGtFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          secondary: Bound.create({equal: serialEqFilter, filterKey: SERIAL_NUMBER, viewModelKey: 'serial_number'}),
          primary: Bound.create({lower: tokenIdGtFilter, filterKey: TOKEN_ID, viewModelKey: 'token_id'}),
        },
        lower: [tokenIdGtFilter, serialEqFilter],
      },
    },
    {
      name: 'token lt and serial eq',
      filters: [serialEqFilter, tokenIdLtFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          secondary: Bound.create({equal: serialEqFilter, filterKey: SERIAL_NUMBER, viewModelKey: 'serial_number'}),
          primary: Bound.create({upper: tokenIdLtFilter, filterKey: TOKEN_ID, viewModelKey: 'token_id'}),
        },
        lower: [tokenIdLtFilter, serialEqFilter],
      },
    },
    {
      name: 'token gte and lte, serial eq',
      filters: [serialEqFilter, tokenIdGteFilter, tokenIdLteFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          secondary: Bound.create({equal: serialEqFilter, filterKey: SERIAL_NUMBER, viewModelKey: 'serial_number'}),
          primary: Bound.create({
            lower: tokenIdGteFilter,
            upper: tokenIdLteFilter,
            filterKey: TOKEN_ID,
            viewModelKey: 'token_id',
          }),
        },
        lower: [tokenIdGteFilter, tokenIdLteFilter, serialEqFilter],
      },
    },
    {
      name: 'token gte and serial gte',
      filters: [serialGteFilter, tokenIdGteFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          secondary: Bound.create({lower: serialGteFilter, filterKey: SERIAL_NUMBER, viewModelKey: 'serial_number'}),
          primary: Bound.create({lower: tokenIdGteFilter, filterKey: TOKEN_ID, viewModelKey: 'token_id'}),
        },
        lower: [{...tokenIdGteFilter, operator: eq}, serialGteFilter],
        inner: [{...tokenIdGtFilter, value: tokenIdGtFilter.value + 1}],
      },
    },
    {
      name: 'token lte and serial lte',
      filters: [serialLteFilter, tokenIdLteFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          secondary: Bound.create({upper: serialLteFilter, filterKey: SERIAL_NUMBER, viewModelKey: 'serial_number'}),
          primary: Bound.create({upper: tokenIdLteFilter, filterKey: TOKEN_ID, viewModelKey: 'token_id'}),
        },
        inner: [{...tokenIdLteFilter, operator: lt}],
        upper: [{...tokenIdLteFilter, operator: eq}, serialLteFilter],
      },
    },
    {
      name: 'token gte and lte, serial gte and lte',
      filters: [serialGteFilter, serialLteFilter, tokenIdGteFilter, tokenIdLteFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          secondary: Bound.create({
            lower: serialGteFilter,
            upper: serialLteFilter,
            filterKey: SERIAL_NUMBER,
            viewModelKey: 'serial_number',
          }),
          primary: Bound.create({
            lower: tokenIdGteFilter,
            upper: tokenIdLteFilter,
            filterKey: TOKEN_ID,
            viewModelKey: 'token_id',
          }),
        },
        lower: [{...tokenIdGteFilter, operator: eq}, serialGteFilter],
        inner: [
          {...tokenIdGteFilter, operator: gt},
          {...tokenIdLteFilter, operator: lt},
        ],
        upper: [{...tokenIdLteFilter, operator: eq}, serialLteFilter],
      },
    },
    {
      name: 'token eq and serial lt',
      filters: [serialLtFilter, tokenIdEqFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          primary: Bound.create({equal: tokenIdEqFilter, filterKey: TOKEN_ID, viewModelKey: 'token_id'}),
          secondary: Bound.create({upper: serialLtFilter, filterKey: SERIAL_NUMBER, viewModelKey: 'serial_number'}),
        },
        lower: [tokenIdEqFilter, serialLtFilter],
      },
    },
    {
      name: 'token eq and serial gt',
      filters: [serialGtFilter, tokenIdEqFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          primary: Bound.create({equal: tokenIdEqFilter, filterKey: TOKEN_ID, viewModelKey: 'token_id'}),
          secondary: Bound.create({lower: serialGtFilter, filterKey: SERIAL_NUMBER, viewModelKey: 'serial_number'}),
        },
        lower: [tokenIdEqFilter, serialGtFilter],
      },
    },
    {
      name: 'spender eq le gte',
      filters: [spenderEqFilter, spenderEqInFilter, spenderEqInFilter2, spenderLtFilter, spenderGteFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          primary: new Bound(TOKEN_ID, 'token_id'),
          secondary: new Bound(SERIAL_NUMBER, 'serial_number'),
        },
        spenderIdFilters: [spenderLtFilter, spenderGteFilter],
        spenderIdInFilters: [spenderEqFilter, spenderEqInFilter, spenderEqInFilter2],
      },
    },
    {
      name: 'token eq spender eq le gte',
      filters: [
        tokenIdEqFilter,
        spenderEqFilter,
        spenderEqInFilter,
        spenderEqInFilter2,
        spenderLtFilter,
        spenderGteFilter,
      ],
      expected: {
        ...defaultExpected,
        bounds: {
          primary: Bound.create({equal: tokenIdEqFilter, filterKey: TOKEN_ID, viewModelKey: 'token_id'}),
          secondary: new Bound(SERIAL_NUMBER, 'serial_number'),
        },
        lower: [tokenIdEqFilter],
        spenderIdFilters: [spenderLtFilter, spenderGteFilter],
        spenderIdInFilters: [spenderEqFilter, spenderEqInFilter, spenderEqInFilter2],
      },
    },
    {
      name: 'token gte and lte, serial gte and lte, spender eq lte gt',
      filters: [
        serialGteFilter,
        serialLteFilter,
        tokenIdGteFilter,
        tokenIdLteFilter,
        spenderEqFilter,
        spenderEqInFilter,
        spenderLteFilter,
        spenderGtFilter,
      ],
      expected: {
        ...defaultExpected,
        bounds: {
          secondary: Bound.create({
            lower: serialGteFilter,
            upper: serialLteFilter,
            filterKey: SERIAL_NUMBER,
            viewModelKey: 'serial_number',
          }),
          primary: Bound.create({
            lower: tokenIdGteFilter,
            upper: tokenIdLteFilter,
            filterKey: TOKEN_ID,
            viewModelKey: 'token_id',
          }),
        },
        lower: [{...tokenIdGteFilter, operator: eq}, serialGteFilter],
        inner: [
          {...tokenIdGteFilter, operator: gt},
          {...tokenIdLteFilter, operator: lt},
        ],
        upper: [{...tokenIdLteFilter, operator: eq}, serialLteFilter],
        spenderIdFilters: [spenderLteFilter, spenderGtFilter],
        spenderIdInFilters: [spenderEqFilter, spenderEqInFilter],
      },
    },
  ];

  specs.forEach((spec) => {
    test(spec.name, () => {
      expect(AccountController.extractNftMultiUnionQuery(spec.filters, ownerAccountId)).toEqual(spec.expected);
    });
  });
});

describe('extractTokenMultiUnionQuery throw', () => {
  const specs = [
    {
      name: 'no token.id',
      filters: [{key: SERIAL_NUMBER, operator: eq, value: 1}],
    },
    {
      name: 'serial number ne',
      filters: [{key: SERIAL_NUMBER, operator: ne, value: 1}],
    },
    {
      name: 'serial number range and equal',
      filters: [serialEqFilter, serialGtFilter, serialLtFilter],
    },
    {
      name: 'serial number multiple equal',
      filters: [serialEqFilter, serialEqFilter],
    },
    {
      name: 'serial number multiple lower bounds',
      filters: [serialGtFilter, serialGteFilter],
    },
    {
      name: 'serial number multiple upper bounds',
      filters: [serialLtFilter, serialLteFilter],
    },
    {
      name: 'token.id ne',
      filters: [{key: TOKEN_ID, operator: ne, value: 1}],
    },
    {
      name: 'token.id multiple equal',
      filters: [tokenIdEqFilter, tokenIdEqFilter],
    },
    {
      name: 'token.id multiple lower bounds',
      filters: [tokenIdGtFilter, tokenIdGteFilter],
    },
    {
      name: 'token.id multiple upper bounds',
      filters: [tokenIdLtFilter, tokenIdLteFilter],
    },
    {
      name: 'token gt and serial gte',
      filters: [serialGteFilter, tokenIdGtFilter],
    },
    {
      name: 'token lt and serial lte',
      filters: [serialLteFilter, tokenIdLtFilter],
    },
    {
      name: 'spender le lte',
      filters: [tokenIdEqFilter, spenderLtFilter, spenderLteFilter],
    },
    {
      name: 'spender ge gte',
      filters: [tokenIdEqFilter, spenderGtFilter, spenderGteFilter],
    },
    {
      name: 'spender ne',
      filters: [{key: SPENDER_ID, operator: ne, value: 1}],
    },
  ];

  specs.forEach((spec) => {
    test(spec.name, () => {
      expect(() =>
        AccountController.extractNftMultiUnionQuery(spec.filters, ownerAccountId)
      ).toThrowErrorMatchingSnapshot();
    });
  });
});

describe('extractStakingRewardsQuery', () => {
  const defaultExpected = {
    order: constants.orderFilterValues.DESC,
    limit: defaultLimit,
    conditions: [],
    params: [],
  };

  // the params in the following specs are just the "dynamic" parts of the query; the function
  // "listStakingReardsByAccountId" in AccountController inserts the accountId at $1 and the limit at $2,
  // at which point the conditions reflect the proper parameters.
  const specs = [
    {
      name: 'empty',
      filters: [],
      expected: defaultExpected,
    },
    {
      name: 'order asc',
      filters: [{key: ORDER, operator: eq, value: 'asc'}],
      expected: {...defaultExpected, order: 'asc'},
    },
    {
      name: 'limit',
      filters: [{key: LIMIT, operator: eq, value: 60}],
      expected: {...defaultExpected, limit: 60},
    },
    {
      name: 'timestamp eq',
      filters: [timestampEqFilter],
      expected: {
        ...defaultExpected,
        conditions: ['srt.consensus_timestamp in ($3)'],
        params: [1000],
      },
    },
    {
      name: 'timestamp gt',
      filters: [timestampGtFilter],
      expected: {
        ...defaultExpected,
        conditions: ['srt.consensus_timestamp > $3'],
        params: [2000],
      },
    },
    {
      name: 'timestamp gte',
      filters: [timestampGteFilter],
      expected: {
        ...defaultExpected,
        conditions: ['srt.consensus_timestamp >= $3'],
        params: [3000],
      },
    },
    {
      name: 'timestamp lt',
      filters: [timestampLtFilter],
      expected: {
        ...defaultExpected,
        conditions: ['srt.consensus_timestamp < $3'],
        params: [4000],
      },
    },
    {
      name: 'timestamp lte',
      filters: [timestampLteFilter],
      expected: {
        ...defaultExpected,
        conditions: ['srt.consensus_timestamp <= $3'],
        params: [5000],
      },
    },
    {
      name: 'timestamp eq 1 and 2',
      filters: [timestampEqFilter, timestampEqFilter2],
      expected: {
        ...defaultExpected,
        conditions: ['srt.consensus_timestamp in ($3,$4)'],
        params: [1000, 2000],
      },
    },
    {
      name: 'timestamp eq 1 and 2 and 3',
      filters: [timestampEqFilter, timestampEqFilter2, timestampEqFilter3],
      expected: {
        ...defaultExpected,
        conditions: ['srt.consensus_timestamp in ($3,$4,$5)'],
        params: [1000, 2000, 3000],
      },
    },
    {
      name: 'all params',
      filters: [
        timestampGteFilter,
        timestampLteFilter,
        {key: LIMIT, operator: eq, value: 60},
        {key: ORDER, operator: eq, value: 'asc'},
      ],
      expected: {
        ...defaultExpected,
        conditions: ['srt.consensus_timestamp >= $3', 'srt.consensus_timestamp <= $4'],
        params: [3000, 5000],
        order: 'asc',
        limit: 60,
      },
    },
  ];

  specs.forEach((spec) => {
    test(spec.name, () => {
      expect(AccountController.extractStakingRewardsQuery(spec.filters, ownerAccountId)).toEqual(spec.expected);
    });
  });
});

describe('extractStakingRewardsQuery throw', () => {
  const specs = [
    {
      name: 'timestamp ne',
      filters: [timestampNeFilter],
    },
  ];

  specs.forEach((spec) => {
    test(spec.name, () => {
      expect(() =>
        AccountController.extractStakingRewardsQuery(spec.filters, ownerAccountId)
      ).toThrowErrorMatchingSnapshot();
    });
  });
});
