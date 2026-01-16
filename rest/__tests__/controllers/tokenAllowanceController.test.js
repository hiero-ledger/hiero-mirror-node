// SPDX-License-Identifier: Apache-2.0

import {getResponseLimit} from '../../config';
import * as constants from '../../constants';
import * as utils from '../../utils';

const {default: defaultLimit} = getResponseLimit();
const {
  filterKeys: {SPENDER_ID, TOKEN_ID, ORDER, LIMIT},
} = constants;
const {
  opsMap: {eq, gt, gte, lt, lte, ne},
} = utils;

import Bound from '../../controllers/bound';
import {TokenAllowanceController} from '../../controllers';

const ownerAccountId = BigInt(1);

const spenderEqFilter = {key: SPENDER_ID, operator: eq, value: 5};
const spenderGtFilter = {key: SPENDER_ID, operator: gt, value: 6};
const spenderGteFilter = {key: SPENDER_ID, operator: gte, value: 7};
const spenderLtFilter = {key: SPENDER_ID, operator: lt, value: 15};
const spenderLteFilter = {key: SPENDER_ID, operator: lte, value: 16};

const tokenIdEqFilter = {key: TOKEN_ID, operator: eq, value: 100};
const tokenIdGtFilter = {key: TOKEN_ID, operator: gt, value: 101};
const tokenIdGteFilter = {key: TOKEN_ID, operator: gte, value: 102};
const tokenIdLtFilter = {key: TOKEN_ID, operator: lt, value: 150};
const tokenIdLteFilter = {key: TOKEN_ID, operator: lte, value: 151};

describe('extractTokenMultiUnionQuery', () => {
  const defaultExpected = {
    bounds: {
      primary: new Bound(SPENDER_ID, 'spender'),
      secondary: new Bound(TOKEN_ID, 'token_id'),
    },
    lower: [],
    inner: [],
    upper: [],
    ownerAccountId,
    order: constants.orderFilterValues.ASC,
    limit: defaultLimit,
  };

  const specs = [
    {
      name: 'empty',
      filters: [],
      expected: defaultExpected,
    },
    {
      name: 'order desc',
      filters: [{key: ORDER, operator: eq, value: 'desc'}],
      expected: {...defaultExpected, order: 'desc'},
    },
    {
      name: 'limit',
      filters: [{key: LIMIT, operator: eq, value: 60}],
      expected: {...defaultExpected, limit: 60},
    },
    {
      name: 'spender eq',
      filters: [spenderEqFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          primary: Bound.create({equal: spenderEqFilter, filterKey: SPENDER_ID, viewModelKey: 'spender'}),
          secondary: new Bound(TOKEN_ID, 'token_id'),
        },
        lower: [spenderEqFilter],
      },
    },
    {
      name: 'spender gt',
      filters: [spenderGtFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          primary: Bound.create({lower: spenderGtFilter, filterKey: SPENDER_ID, viewModelKey: 'spender'}),
          secondary: new Bound(TOKEN_ID, 'token_id'),
        },
        lower: [spenderGtFilter],
      },
    },
    {
      name: 'spender gte',
      filters: [spenderGteFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          primary: Bound.create({lower: spenderGteFilter, filterKey: SPENDER_ID, viewModelKey: 'spender'}),
          secondary: new Bound(TOKEN_ID, 'token_id'),
        },
        lower: [spenderGteFilter],
      },
    },
    {
      name: 'spender lt',
      filters: [spenderLtFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          primary: Bound.create({upper: spenderLtFilter, filterKey: SPENDER_ID, viewModelKey: 'spender'}),
          secondary: new Bound(TOKEN_ID, 'token_id'),
        },
        lower: [spenderLtFilter],
      },
    },
    {
      name: 'spender lte',
      filters: [spenderLteFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          primary: Bound.create({upper: spenderLteFilter, filterKey: SPENDER_ID, viewModelKey: 'spender'}),
          secondary: new Bound(TOKEN_ID, 'token_id'),
        },
        lower: [spenderLteFilter],
      },
    },
    {
      name: 'spender gt and lt',
      filters: [spenderGtFilter, spenderLtFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          primary: Bound.create({
            lower: spenderGtFilter,
            upper: spenderLtFilter,
            filterKey: SPENDER_ID,
            viewModelKey: 'spender',
          }),
          secondary: new Bound(TOKEN_ID, 'token_id'),
        },
        lower: [spenderGtFilter, spenderLtFilter],
      },
    },
    {
      name: 'spender eq and token eq',
      filters: [spenderEqFilter, tokenIdEqFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          primary: Bound.create({equal: spenderEqFilter, filterKey: SPENDER_ID, viewModelKey: 'spender'}),
          secondary: Bound.create({equal: tokenIdEqFilter, filterKey: TOKEN_ID, viewModelKey: 'token_id'}),
        },
        lower: [spenderEqFilter, tokenIdEqFilter],
      },
    },
    {
      name: 'spender gt and token eq',
      filters: [spenderGtFilter, tokenIdEqFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          primary: Bound.create({lower: spenderGtFilter, filterKey: SPENDER_ID, viewModelKey: 'spender'}),
          secondary: Bound.create({equal: tokenIdEqFilter, filterKey: TOKEN_ID, viewModelKey: 'token_id'}),
        },
        lower: [spenderGtFilter, tokenIdEqFilter],
      },
    },
    {
      name: 'spender lt and token eq',
      filters: [spenderLtFilter, tokenIdEqFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          primary: Bound.create({upper: spenderLtFilter, filterKey: SPENDER_ID, viewModelKey: 'spender'}),
          secondary: Bound.create({equal: tokenIdEqFilter, filterKey: TOKEN_ID, viewModelKey: 'token_id'}),
        },
        lower: [spenderLtFilter, tokenIdEqFilter],
      },
    },
    {
      name: 'spender gt and lt, token eq',
      filters: [spenderGtFilter, spenderLtFilter, tokenIdEqFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          primary: Bound.create({
            lower: spenderGtFilter,
            upper: spenderLtFilter,
            filterKey: SPENDER_ID,
            viewModelKey: 'spender',
          }),
          secondary: Bound.create({equal: tokenIdEqFilter, filterKey: TOKEN_ID, viewModelKey: 'token_id'}),
        },
        lower: [spenderGtFilter, spenderLtFilter, tokenIdEqFilter],
      },
    },
    {
      name: 'spender gte and lte, token eq',
      filters: [spenderGteFilter, spenderLteFilter, tokenIdEqFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          primary: Bound.create({
            lower: spenderGteFilter,
            upper: spenderLteFilter,
            filterKey: SPENDER_ID,
            viewModelKey: 'spender',
          }),
          secondary: Bound.create({equal: tokenIdEqFilter, filterKey: TOKEN_ID, viewModelKey: 'token_id'}),
        },
        lower: [spenderGteFilter, spenderLteFilter, tokenIdEqFilter],
      },
    },
    {
      name: 'spender gte and token gte',
      filters: [spenderGteFilter, tokenIdGteFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          primary: Bound.create({lower: spenderGteFilter, filterKey: SPENDER_ID, viewModelKey: 'spender'}),
          secondary: Bound.create({lower: tokenIdGteFilter, filterKey: TOKEN_ID, viewModelKey: 'token_id'}),
        },
        lower: [{...spenderGteFilter, operator: eq}, tokenIdGteFilter],
        inner: [{...spenderGteFilter, operator: gt}],
      },
    },
    {
      name: 'spender gte and token gt',
      filters: [spenderGteFilter, tokenIdGtFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          primary: Bound.create({lower: spenderGteFilter, filterKey: SPENDER_ID, viewModelKey: 'spender'}),
          secondary: Bound.create({lower: tokenIdGtFilter, filterKey: TOKEN_ID, viewModelKey: 'token_id'}),
        },
        lower: [{...spenderGteFilter, operator: eq}, tokenIdGtFilter],
        inner: [{...spenderGteFilter, operator: gt}],
      },
    },
    {
      name: 'spender lte and token lte',
      filters: [spenderLteFilter, tokenIdLteFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          primary: Bound.create({upper: spenderLteFilter, filterKey: SPENDER_ID, viewModelKey: 'spender'}),
          secondary: Bound.create({upper: tokenIdLteFilter, filterKey: TOKEN_ID, viewModelKey: 'token_id'}),
        },
        inner: [{...spenderLteFilter, operator: lt}],
        upper: [{...spenderLteFilter, operator: eq}, tokenIdLteFilter],
      },
    },
    {
      name: 'spender lte and token lt',
      filters: [spenderLteFilter, tokenIdLtFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          primary: Bound.create({upper: spenderLteFilter, filterKey: SPENDER_ID, viewModelKey: 'spender'}),
          secondary: Bound.create({upper: tokenIdLtFilter, filterKey: TOKEN_ID, viewModelKey: 'token_id'}),
        },
        inner: [{...spenderLteFilter, operator: lt}],
        upper: [{...spenderLteFilter, operator: eq}, tokenIdLtFilter],
      },
    },
    {
      name: 'spender gte and lte, token gt',
      filters: [spenderGteFilter, spenderLteFilter, tokenIdGtFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          primary: Bound.create({
            lower: spenderGteFilter,
            upper: spenderLteFilter,
            filterKey: SPENDER_ID,
            viewModelKey: 'spender',
          }),
          secondary: Bound.create({lower: tokenIdGtFilter, filterKey: TOKEN_ID, viewModelKey: 'token_id'}),
        },
        lower: [{...spenderGteFilter, operator: eq}, tokenIdGtFilter],
        inner: [{...spenderGteFilter, operator: gt}, spenderLteFilter],
      },
    },
    {
      name: 'spender gte and lte, token lt',
      filters: [spenderGteFilter, spenderLteFilter, tokenIdLtFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          primary: Bound.create({
            lower: spenderGteFilter,
            upper: spenderLteFilter,
            filterKey: SPENDER_ID,
            viewModelKey: 'spender',
          }),
          secondary: Bound.create({upper: tokenIdLtFilter, filterKey: TOKEN_ID, viewModelKey: 'token_id'}),
        },
        inner: [
          {...spenderGteFilter, operator: gte},
          {...spenderLteFilter, operator: lt},
        ],
        upper: [{...spenderLteFilter, operator: eq}, tokenIdLtFilter],
      },
    },
    {
      name: 'spender gte and lte, token gt and lt',
      filters: [spenderGteFilter, spenderLteFilter, tokenIdGtFilter, tokenIdLtFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          primary: Bound.create({
            lower: spenderGteFilter,
            upper: spenderLteFilter,
            filterKey: SPENDER_ID,
            viewModelKey: 'spender',
          }),
          secondary: Bound.create({
            lower: tokenIdGtFilter,
            upper: tokenIdLtFilter,
            filterKey: TOKEN_ID,
            viewModelKey: 'token_id',
          }),
        },
        lower: [{...spenderGteFilter, operator: eq}, tokenIdGtFilter],
        inner: [
          {...spenderGteFilter, operator: gt},
          {...spenderLteFilter, operator: lt},
        ],
        upper: [{...spenderLteFilter, operator: eq}, tokenIdLtFilter],
      },
    },
    {
      name: 'spender gte and lte, token gte and lte',
      filters: [spenderGteFilter, spenderLteFilter, tokenIdGteFilter, tokenIdLteFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          primary: Bound.create({
            lower: spenderGteFilter,
            upper: spenderLteFilter,
            filterKey: SPENDER_ID,
            viewModelKey: 'spender',
          }),
          secondary: Bound.create({
            lower: tokenIdGteFilter,
            upper: tokenIdLteFilter,
            filterKey: TOKEN_ID,
            viewModelKey: 'token_id',
          }),
        },
        lower: [{...spenderGteFilter, operator: eq}, tokenIdGteFilter],
        inner: [
          {...spenderGteFilter, operator: gt},
          {...spenderLteFilter, operator: lt},
        ],
        upper: [{...spenderLteFilter, operator: eq}, tokenIdLteFilter],
      },
    },
  ];

  specs.forEach((spec) => {
    test(spec.name, () => {
      expect(TokenAllowanceController.extractTokenMultiUnionQuery(spec.filters, ownerAccountId)).toEqual(spec.expected);
    });
  });
});

describe('extractTokenMultiUnionQuery throw', () => {
  const specs = [
    {
      name: 'spender.id ne',
      filters: [{key: SPENDER_ID, operator: ne, value: 1}],
    },
    {
      name: 'spender.id range and equal',
      filters: [spenderEqFilter, spenderGtFilter, spenderLtFilter],
    },
    {
      name: 'spender.id multiple equal',
      filters: [spenderEqFilter, spenderEqFilter],
    },
    {
      name: 'spender.id multiple lower bounds',
      filters: [spenderGtFilter, spenderGteFilter],
    },
    {
      name: 'spender.id multiple upper bounds',
      filters: [spenderLtFilter, spenderLteFilter],
    },
    {
      name: 'token.id ne',
      filters: [{key: TOKEN_ID, operator: ne, value: 1}],
    },
    {
      name: 'token.id range and equal',
      filters: [tokenIdEqFilter, tokenIdGtFilter, tokenIdLtFilter],
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
      name: 'token.id eq without spender.id query',
      filters: [tokenIdEqFilter],
    },
    {
      name: 'token.id gt without spender.id query',
      filters: [tokenIdGtFilter],
    },
    {
      name: 'token.id gt with spender.id gt',
      filters: [tokenIdGtFilter, spenderGtFilter],
    },
    {
      name: 'token.id lt without spender.id query',
      filters: [tokenIdLtFilter],
    },
    {
      name: 'token.id lt with spender.id lt',
      filters: [tokenIdLtFilter, spenderLtFilter],
    },
  ];

  specs.forEach((spec) => {
    test(spec.name, () => {
      expect(() =>
        TokenAllowanceController.extractTokenMultiUnionQuery(spec.filters, ownerAccountId)
      ).toThrowErrorMatchingSnapshot();
    });
  });
});
