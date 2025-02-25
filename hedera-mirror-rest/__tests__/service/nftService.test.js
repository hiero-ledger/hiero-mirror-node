// SPDX-License-Identifier: Apache-2.0

import {filterKeys} from '../../constants';
import {NftService} from '../../service';
import {assertSqlQueryEqual} from '../testutils';
import {opsMap} from '../../utils';

const {SERIAL_NUMBER, SPENDER_ID, TOKEN_ID} = filterKeys;
const {eq, gt, gte, lt, lte} = opsMap;

describe('getQuery', () => {
  const defaultQuery = {
    lower: [],
    inner: [],
    upper: [],
    spenderIdFilters: [],
    spenderIdInFilters: [],
    order: 'desc',
    ownerAccountId: 1,
    limit: 20,
  };

  const selectColumnsStatement = `select
    account_id,
    nft.created_timestamp,
    delegating_spender,
    nft.deleted or coalesce(e.deleted, false) as deleted,
    metadata,
    serial_number,
    spender,
    nft.timestamp_range,
    token_id`;

  const specs = [
    {
      name: 'default',
      query: defaultQuery,
      expected: {
        sqlQuery: `${selectColumnsStatement}
            from nft
            left join entity e on e.id = nft.token_id
            where account_id = $1
            order by token_id desc,serial_number desc
            limit $2`,
        params: [1, 20],
      },
    },
    {
      name: 'order asc',
      query: {...defaultQuery, order: 'asc'},
      expected: {
        sqlQuery: `${selectColumnsStatement}
            from nft
            left join entity e on e.id = nft.token_id
            where account_id = $1
            order by token_id asc,serial_number asc
            limit $2`,
        params: [1, 20],
      },
    },
    {
      name: 'token eq',
      query: {
        ...defaultQuery,
        lower: [{key: TOKEN_ID, operator: eq, value: 2}],
      },
      expected: {
        sqlQuery: `${selectColumnsStatement}
            from nft
            left join entity e on e.id = nft.token_id
            where account_id = $1 and token_id = $3
            order by token_id desc,serial_number desc
            limit $2`,
        params: [1, 20, 2],
      },
    },
    {
      name: 'token eq and serial eq',
      query: {
        ...defaultQuery,
        lower: [
          {key: TOKEN_ID, operator: eq, value: 2},
          {key: SERIAL_NUMBER, operator: eq, value: 3},
        ],
      },
      expected: {
        sqlQuery: `${selectColumnsStatement}
            from nft
            left join entity e on e.id = nft.token_id
            where account_id = $1
            and token_id = $3
            and serial_number = $4
            order by token_id desc,serial_number desc
            limit $2`,
        params: [1, 20, 2, 3],
      },
    },
    {
      name: 'token lower bound and serial lower bound',
      query: {
        ...defaultQuery,
        lower: [{key: SERIAL_NUMBER, operator: gt, value: 3}],
        inner: [{key: TOKEN_ID, operator: gt, value: 2}],
      },
      expected: {
        sqlQuery: `(${selectColumnsStatement}
              from nft
              left join entity e on e.id = nft.token_id
              where account_id = $1
              and serial_number > $3
              order by token_id desc,serial_number desc
              limit $2
            ) union all (
              ${selectColumnsStatement}
              from nft
              left join entity e on e.id = nft.token_id
              where account_id = $1
              and token_id > $4
              order by token_id desc,serial_number desc
              limit $2
            )
            order by token_id desc,serial_number desc limit $2`,
        params: [1, 20, 3, 2],
      },
    },
    {
      name: 'token upper bound and serial upper bound',
      query: {
        ...defaultQuery,
        inner: [{key: TOKEN_ID, operator: lt, value: 2}],
        upper: [
          {key: TOKEN_ID, operator: eq, value: 3},
          {key: SERIAL_NUMBER, operator: lt, value: 4},
        ],
      },
      expected: {
        sqlQuery: `(${selectColumnsStatement}
              from nft
              left join entity e on e.id = nft.token_id
              where account_id = $1
              and token_id < $3
              order by token_id desc,serial_number desc limit $2
            ) union all (
              ${selectColumnsStatement}
              from nft
              left join entity e on e.id = nft.token_id
              where account_id = $1
              and token_id = $4
              and serial_number < $5
              order by token_id desc,serial_number desc limit $2
            )
            order by token_id desc,serial_number desc
            limit $2`,
        params: [1, 20, 2, 3, 4],
      },
    },
    {
      name: 'token closed range and serial eq',
      query: {
        ...defaultQuery,
        lower: [
          {key: TOKEN_ID, operator: gt, value: 2},
          {key: TOKEN_ID, operator: lt, value: 10},
          {key: SERIAL_NUMBER, operator: eq, value: 3},
        ],
      },
      expected: {
        sqlQuery: `${selectColumnsStatement}
            from nft
            left join entity e on e.id = nft.token_id
            where account_id = $1
            and token_id > $3
            and token_id < $4
            and serial_number = $5
            order by token_id desc,serial_number desc
            limit $2`,
        params: [1, 20, 2, 10, 3],
      },
    },
    {
      name: 'token closed range and serial gt',
      query: {
        ...defaultQuery,
        lower: [
          {key: TOKEN_ID, operator: eq, value: 2},
          {key: SERIAL_NUMBER, operator: gt, value: 30},
        ],
        inner: [
          {key: TOKEN_ID, operator: gte, value: 3},
          {key: TOKEN_ID, operator: lt, value: 10},
        ],
      },
      expected: {
        sqlQuery: `(${selectColumnsStatement}
              from nft
              left join entity e on e.id = nft.token_id
              where account_id = $1
              and token_id = $3
              and serial_number > $4
              order by token_id desc,serial_number desc limit $2
            ) union all (
              ${selectColumnsStatement}
              from nft
              left join entity e on e.id = nft.token_id
              where account_id = $1
              and token_id >= $5
              and token_id < $6
              order by token_id desc,serial_number desc limit $2
            )
            order by token_id desc,serial_number desc
            limit $2`,
        params: [1, 20, 2, 30, 3, 10],
      },
    },
    {
      name: 'token closed range and serial lt',
      query: {
        ...defaultQuery,
        upper: [
          {key: TOKEN_ID, operator: eq, value: 50},
          {key: SERIAL_NUMBER, operator: lt, value: 30},
        ],
        inner: [
          {key: TOKEN_ID, operator: gte, value: 3},
          {key: TOKEN_ID, operator: lt, value: 10},
        ],
      },
      expected: {
        sqlQuery: `(${selectColumnsStatement}
              from nft
              left join entity e on e.id = nft.token_id
              where account_id = $1
              and token_id >= $3
              and token_id < $4
              order by token_id desc,serial_number desc limit $2
            ) union all (
              ${selectColumnsStatement}
              from nft
              left join entity e on e.id = nft.token_id
              where account_id = $1
              and token_id = $5
              and serial_number < $6
              order by token_id desc,serial_number desc limit $2
            )
            order by token_id desc,serial_number desc
            limit $2`,
        params: [1, 20, 3, 10, 50, 30],
      },
    },
    {
      name: 'token closed range and serial closed range',
      query: {
        ...defaultQuery,
        lower: [
          {key: TOKEN_ID, operator: eq, value: 2},
          {key: SERIAL_NUMBER, operator: gte, value: 100},
        ],
        inner: [
          {key: TOKEN_ID, operator: gt, value: 2},
          {key: TOKEN_ID, operator: lt, value: 8},
        ],
        upper: [
          {key: TOKEN_ID, operator: eq, value: 8},
          {key: SERIAL_NUMBER, operator: lte, value: 200},
        ],
      },
      expected: {
        sqlQuery: `(${selectColumnsStatement}
              from nft
              left join entity e on e.id = nft.token_id
              where account_id = $1
              and token_id = $3
              and serial_number >= $4
              order by token_id desc,serial_number desc limit $2
            ) union all (
              ${selectColumnsStatement}
              from nft
              left join entity e on e.id = nft.token_id
              where account_id = $1
              and token_id > $5
              and token_id < $6
              order by token_id desc,serial_number desc limit $2
            ) union all (
              ${selectColumnsStatement}
              from nft
              left join entity e on e.id = nft.token_id
              where account_id = $1
              and token_id = $7
              and serial_number <= $8
              order by token_id desc,serial_number desc limit $2
            )
            order by token_id desc,serial_number desc
            limit $2`,
        params: [1, 20, 2, 100, 2, 8, 8, 200],
      },
    },
    {
      name: 'spender eq in gte lte',
      query: {
        ...defaultQuery,
        spenderIdInFilters: [
          {key: SPENDER_ID, operator: eq, value: 15},
          {key: SPENDER_ID, operator: eq, value: 17},
          {key: SPENDER_ID, operator: eq, value: 22},
        ],
        spenderIdFilters: [
          {key: SPENDER_ID, operator: lte, value: 10},
          {key: SPENDER_ID, operator: gte, value: 30},
        ],
      },
      expected: {
        sqlQuery: `${selectColumnsStatement}
            from nft
            left join entity e on e.id = nft.token_id
            where account_id = $1
            and spender <= $3
            and spender >= $4
            and spender in ($5,$6,$7)
            order by token_id desc,serial_number desc
            limit $2`,
        params: [1, 20, 10, 30, 15, 17, 22],
      },
    },
    {
      name: 'spender single eq lt',
      query: {
        ...defaultQuery,
        spenderIdInFilters: [{key: SPENDER_ID, operator: eq, value: 15}],
        spenderIdFilters: [{key: SPENDER_ID, operator: lte, value: 10}],
      },
      expected: {
        sqlQuery: `${selectColumnsStatement}
            from nft
            left join entity e on e.id = nft.token_id
            where account_id = $1
            and spender <= $3
            and spender in ($4)
            order by token_id desc,serial_number desc
            limit $2`,
        params: [1, 20, 10, 15],
      },
    },
  ];

  specs.forEach((spec) => {
    test(spec.name, () => {
      const actual = NftService.getQuery(spec.query);
      assertSqlQueryEqual(actual.sqlQuery, spec.expected.sqlQuery);
      expect(actual.params).toEqual(spec.expected.params);
    });
  });
});
