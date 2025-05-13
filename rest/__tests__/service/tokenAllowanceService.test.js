// SPDX-License-Identifier: Apache-2.0

import {filterKeys} from '../../constants';
import {opsMap} from '../../utils';
import {assertSqlQueryEqual} from '../testutils';
import {TokenAllowanceService} from '../../service';

const {SPENDER_ID, TOKEN_ID} = filterKeys;
const {eq, gt, gte, lt, lte} = opsMap;

describe('getQuery', () => {
  const defaultQuery = {
    lower: [],
    inner: [],
    upper: [],
    order: 'asc',
    ownerAccountId: 1,
    limit: 25,
  };

  const specs = [
    {
      name: 'default',
      query: defaultQuery,
      expected: {
        sqlQuery:
          'select * from token_allowance where owner = $1 and amount > 0 order by spender asc, token_id asc limit $2',
        params: [1, 25],
      },
    },
    {
      name: 'order desc',
      query: {...defaultQuery, order: 'desc'},
      expected: {
        sqlQuery:
          'select * from token_allowance where owner = $1 and amount > 0 order by spender desc, token_id desc limit $2',
        params: [1, 25],
      },
    },
    {
      name: 'spender eq',
      query: {...defaultQuery, lower: [{key: SPENDER_ID, operator: eq, value: 2}]},
      expected: {
        sqlQuery: `select * from token_allowance
          where owner = $1 and amount > 0 and spender = $3
          order by spender asc, token_id asc
          limit $2`,
        params: [1, 25, 2],
      },
    },
    {
      name: 'spender eq and token eq',
      query: {
        ...defaultQuery,
        lower: [
          {key: SPENDER_ID, operator: eq, value: 2},
          {key: TOKEN_ID, operator: eq, value: 3},
        ],
      },
      expected: {
        sqlQuery: `select * from token_allowance
          where owner = $1 and amount > 0 and spender = $3 and token_id = $4
          order by spender asc, token_id asc
          limit $2`,
        params: [1, 25, 2, 3],
      },
    },
    {
      name: 'spender lower bound and token lower bound',
      query: {
        ...defaultQuery,
        lower: [
          {key: SPENDER_ID, operator: eq, value: 2},
          {key: TOKEN_ID, operator: gt, value: 3},
        ],
        inner: [{key: SPENDER_ID, operator: gt, value: 2}],
      },
      expected: {
        sqlQuery: `(select * from token_allowance
            where owner = $1 and amount > 0 and spender = $3 and token_id > $4
            order by spender asc, token_id asc
            limit $2
          ) union all (
            select * from token_allowance
            where owner = $1 and amount > 0 and spender > $5
            order by spender asc, token_id asc
            limit $2
          )
          order by spender asc, token_id asc
          limit $2
          `,
        params: [1, 25, 2, 3, 2],
      },
    },
    {
      name: 'spender upper bound and token upper bound',
      query: {
        ...defaultQuery,
        inner: [{key: SPENDER_ID, operator: lt, value: 2}],
        upper: [
          {key: SPENDER_ID, operator: eq, value: 2},
          {key: TOKEN_ID, operator: lt, value: 3},
        ],
      },
      expected: {
        sqlQuery: `(select * from token_allowance
            where owner = $1 and amount > 0 and spender < $3
            order by spender asc, token_id asc
            limit $2
          ) union all (
            select * from token_allowance
            where owner = $1 and amount > 0 and spender = $4 and token_id < $5
            order by spender asc, token_id asc
            limit $2
          )
          order by spender asc, token_id asc
          limit $2`,
        params: [1, 25, 2, 2, 3],
      },
    },
    {
      name: 'spender closed range and token eq',
      query: {
        ...defaultQuery,
        lower: [
          {key: SPENDER_ID, operator: gt, value: 2},
          {key: SPENDER_ID, operator: lt, value: 10},
          {key: TOKEN_ID, operator: eq, value: 3},
        ],
      },
      expected: {
        sqlQuery: `select * from token_allowance
          where owner = $1 and amount > 0 and spender > $3 and spender < $4 and token_id = $5
          order by spender asc, token_id asc
          limit $2`,
        params: [1, 25, 2, 10, 3],
      },
    },
    {
      name: 'spender closed range and token gt',
      query: {
        ...defaultQuery,
        lower: [
          {key: SPENDER_ID, operator: eq, value: 2},
          {key: TOKEN_ID, operator: gt, value: 3},
        ],
        inner: [
          {key: SPENDER_ID, operator: gt, value: 2},
          {key: SPENDER_ID, operator: lte, value: 11},
        ],
      },
      expected: {
        sqlQuery: `(select * from token_allowance
            where owner = $1 and amount > 0 and spender = $3 and token_id > $4
            order by spender asc, token_id asc
            limit $2
          ) union all (
            select * from token_allowance
            where owner = $1 and amount > 0 and spender > $5 and spender <= $6
            order by spender asc, token_id asc
            limit $2
          )
          order by spender asc, token_id asc
          limit $2`,
        params: [1, 25, 2, 3, 2, 11],
      },
    },
    {
      name: 'spender closed range and token lt',
      query: {
        ...defaultQuery,
        inner: [
          {key: SPENDER_ID, operator: gte, value: 2},
          {key: SPENDER_ID, operator: lt, value: 11},
        ],
        upper: [
          {key: SPENDER_ID, operator: eq, value: 11},
          {key: TOKEN_ID, operator: lt, value: 3},
        ],
      },
      expected: {
        sqlQuery: `(select * from token_allowance
            where owner = $1 and amount > 0 and spender >= $3 and spender < $4
            order by spender asc, token_id asc
            limit $2
          ) union all (
            select * from token_allowance
            where owner = $1 and amount > 0 and spender = $5 and token_id < $6
            order by spender asc, token_id asc
            limit $2
          )
          order by spender asc, token_id asc
          limit $2`,
        params: [1, 25, 2, 11, 11, 3],
      },
    },
    {
      name: 'spender closed range and token closed range',
      query: {
        ...defaultQuery,
        lower: [
          {key: SPENDER_ID, operator: eq, value: 2},
          {key: TOKEN_ID, operator: gte, value: 100},
        ],
        inner: [
          {key: SPENDER_ID, operator: gt, value: 2},
          {key: SPENDER_ID, operator: lt, value: 8},
        ],
        upper: [
          {key: SPENDER_ID, operator: eq, value: 8},
          {key: TOKEN_ID, operator: lte, value: 200},
        ],
      },
      expected: {
        sqlQuery: `(select * from token_allowance
            where owner = $1 and amount > 0 and spender = $3 and token_id >= $4
            order by spender asc, token_id asc
            limit $2
          ) union all (
            select * from token_allowance
            where owner = $1 and amount > 0 and spender > $5 and spender < $6
            order by spender asc, token_id asc
            limit $2
          ) union all (
            select * from token_allowance
            where owner = $1 and amount > 0 and spender = $7 and token_id <= $8
            order by spender asc, token_id asc
            limit $2
          )
          order by spender asc, token_id asc
          limit $2`,
        params: [1, 25, 2, 100, 2, 8, 8, 200],
      },
    },
  ];

  specs.forEach((spec) => {
    test(spec.name, () => {
      const actual = TokenAllowanceService.getQuery(spec.query);
      assertSqlQueryEqual(actual.sqlQuery, spec.expected.sqlQuery);
      expect(actual.params).toEqual(spec.expected.params);
    });
  });
});
