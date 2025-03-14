// SPDX-License-Identifier: Apache-2.0

import {getResponseLimit} from '../config';
import * as constants from '../constants';
import * as utils from '../utils';
import {opsMap} from '../utils';
import {assertSqlQueryEqual} from './testutils';
import tokens from '../tokens';
import _ from 'lodash';
import CustomFee from '../model/customFee.js';
import Entity from '../model/entity.js';
import Token from '../model/token.js';

const {default: defaultLimit} = getResponseLimit();

describe('token formatTokenRow tests', () => {
  const rowInput = {
    key: [3, 3, 3],
    public_key: '3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be',
    symbol: 'YBTJBOAZ',
    token_id: '7',
    decimals: 10,
    metadata: null,
    name: 'Token name',
    type: 'FUNGIBLE_COMMON',
  };

  const expectedFormat = {
    token_id: '0.0.7',
    symbol: 'YBTJBOAZ',
    admin_key: {
      _type: 'ProtobufEncoded',
      key: '030303',
    },
    decimals: 10,
    metadata: '',
    name: 'Token name',
    type: 'FUNGIBLE_COMMON',
  };

  test('Verify formatTokenRow', () => {
    expect(tokens.formatTokenRow(rowInput)).toStrictEqual(expectedFormat);
  });
});

describe('token extractSqlFromTokenRequest tests', () => {
  const accountQueryCondition = 'ta.associated is true';

  test('Verify simple discovery query', () => {
    const initialQuery = [tokens.tokensSelectQuery, tokens.entityIdJoinQuery].join('\n');
    const initialParams = [];
    const filters = [];

    const expectedQuery = `
      select
        t.decimals,
        t.freeze_status,
        e.key,
        t.kyc_status,
        t.metadata,
        t.name,
        t.symbol,
        t.token_id,
        t.type
      from token t
      join entity e on e.id = t.token_id
      order by t.token_id asc
      limit $1`;
    const expectedParams = [defaultLimit];
    const expectedOrder = constants.orderFilterValues.ASC;
    const expectedLimit = defaultLimit;

    verifyExtractSqlFromTokenRequest(
      initialQuery,
      initialParams,
      filters,
      null,
      expectedQuery,
      expectedParams,
      expectedOrder,
      expectedLimit
    );
  });

  test('Verify multiple token ids query', () => {
    const initialQuery = tokens.tokensSelectQuery;
    const initialParams = [];
    const filters = [
      {
        key: constants.filterKeys.TOKEN_ID,
        operator: ' = ',
        value: '111',
      },
      {
        key: constants.filterKeys.TOKEN_ID,
        operator: ' = ',
        value: '222',
      },
      {
        key: constants.filterKeys.TOKEN_ID,
        operator: ' = ',
        value: '333',
      },
    ];

    const expectedQuery = `
        select
            t.decimals,
            t.freeze_status,
            e.key,
            t.kyc_status,
            t.metadata,
            t.name,
            t.symbol,
            t.token_id,
            t.type
        from token t
        where t.token_id = any($1)
        order by t.token_id asc
            limit $2`;
    const expectedParams = [['111', '222', '333'], defaultLimit];
    const expectedOrder = constants.orderFilterValues.ASC;
    const expectedLimit = defaultLimit;

    verifyExtractSqlFromTokenRequest(
      initialQuery,
      initialParams,
      filters,
      undefined,
      expectedQuery,
      expectedParams,
      expectedOrder,
      expectedLimit
    );
  });

  test('Verify public key filter', () => {
    const initialQuery = [tokens.tokensSelectQuery, tokens.entityIdJoinQuery].join('\n');
    const initialParams = [];
    const filters = [
      {
        key: constants.filterKeys.ENTITY_PUBLICKEY,
        operator: ' = ',
        value: '3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be',
      },
    ];

    const expectedQuery = `
      select
        t.decimals,
        t.freeze_status,
        e.key,
        t.kyc_status,
        t.metadata,
        t.name,
        t.symbol,
        t.token_id,
        t.type
      from token t
             join entity e on e.id = t.token_id
      where e.public_key = $1
      order by t.token_id asc
      limit $2`;
    const expectedParams = ['3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be', defaultLimit];
    const expectedOrder = constants.orderFilterValues.ASC;
    const expectedLimit = defaultLimit;

    verifyExtractSqlFromTokenRequest(
      initialQuery,
      initialParams,
      filters,
      undefined,
      expectedQuery,
      expectedParams,
      expectedOrder,
      expectedLimit
    );
  });

  test('Verify account id filter', () => {
    const initialQuery = [
      tokens.tokenAccountCte,
      tokens.tokensSelectQuery,
      tokens.tokenAccountJoinQuery,
      tokens.entityIdJoinQuery,
    ].join('\n');
    const initialParams = [5];
    const filters = [
      {
        key: constants.filterKeys.ACCOUNT_ID,
        operator: ' = ',
        value: '5',
      },
    ];

    const expectedQuery = `with ta as (
                             select *
                             from token_account
                             where account_id = $1
                             order by token_id
                           )
                           select
                             t.decimals,
                             t.freeze_status,
                             e.key,
                             t.kyc_status,
                             t.metadata,
                             t.name,
                             t.symbol,
                             t.token_id,
                             t.type
                           from token t
                                  join ta on ta.token_id = t.token_id
                                  join entity e on e.id = t.token_id
                           where ta.associated is true
                           order by t.token_id asc
                           limit $2`;
    const expectedParams = [5, defaultLimit];
    const expectedOrder = constants.orderFilterValues.ASC;
    const expectedLimit = defaultLimit;

    verifyExtractSqlFromTokenRequest(
      initialQuery,
      initialParams,
      filters,
      [accountQueryCondition],
      expectedQuery,
      expectedParams,
      expectedOrder,
      expectedLimit
    );
  });

  test('Verify token type filter', () => {
    const initialQuery = [
      tokens.tokenAccountCte,
      tokens.tokensSelectQuery,
      tokens.tokenAccountJoinQuery,
      tokens.entityIdJoinQuery,
    ].join('\n');
    const initialParams = [5];
    const tokenType = 'NON_FUNGIBLE_UNIQUE';
    const filters = [
      {
        key: constants.filterKeys.TOKEN_TYPE,
        operator: ' = ',
        value: tokenType,
      },
    ];

    const expectedQuery = `with ta as (
                             select *
                             from token_account
                             where account_id = $1
                             order by token_id
                           )
                           select
                             t.decimals,
                             t.freeze_status,
                             e.key,
                             t.kyc_status,
                             t.metadata,
                             t.name,
                             t.symbol,
                             t.token_id,
                             t.type
                           from token t
                                  join ta on ta.token_id = t.token_id
                                  join entity e on e.id = t.token_id
                           where ta.associated is true and t.type = $2
                           order by t.token_id asc
                           limit $3`;
    const expectedParams = [5, tokenType, defaultLimit];
    const expectedOrder = constants.orderFilterValues.ASC;
    const expectedLimit = defaultLimit;

    verifyExtractSqlFromTokenRequest(
      initialQuery,
      initialParams,
      filters,
      [accountQueryCondition],
      expectedQuery,
      expectedParams,
      expectedOrder,
      expectedLimit
    );
  });

  test('Verify all filters', () => {
    const initialQuery = [
      tokens.tokenAccountCte,
      tokens.tokensSelectQuery,
      tokens.tokenAccountJoinQuery,
      tokens.entityIdJoinQuery,
    ].join('\n');
    const initialParams = [5];
    const tokenType = 'FUNGIBLE_COMMON';
    const filters = [
      {
        key: constants.filterKeys.ACCOUNT_ID,
        operator: ' = ',
        value: '5',
      },
      {
        key: constants.filterKeys.ENTITY_PUBLICKEY,
        operator: ' = ',
        value: '3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be',
      },
      {key: constants.filterKeys.TOKEN_ID, operator: ' > ', value: '2'},
      {key: constants.filterKeys.TOKEN_ID, operator: ' =< ', value: '98'},
      {key: constants.filterKeys.TOKEN_ID, operator: ' = ', value: '3'},
      {key: constants.filterKeys.TOKEN_ID, operator: ' = ', value: '100'},
      {
        key: constants.filterKeys.TOKEN_TYPE,
        operator: ' = ',
        value: tokenType,
      },
      {key: constants.filterKeys.LIMIT, operator: ' = ', value: '3'},
      {key: constants.filterKeys.ORDER, operator: ' = ', value: constants.orderFilterValues.DESC},
    ];

    const expectedQuery = `with ta as (
                             select *
                             from token_account
                             where account_id = $1
                             order by token_id
                           )
                           select
                             t.decimals,
                             t.freeze_status,
                             e.key,
                             t.kyc_status,
                             t.metadata,
                             t.name,
                             t.symbol,
                             t.token_id,
                             t.type
                           from token t
                                  join ta on ta.token_id = t.token_id
                                  join entity e on e.id = t.token_id
                           where ta.associated is true
                             and e.public_key = $2
                             and t.token_id > $3
                             and t.token_id =< $4
                             and t.type = $5
                             and t.token_id = any($6)
                           order by t.token_id desc
                           limit $7`;
    const expectedParams = [
      5,
      '3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be',
      '2',
      '98',
      tokenType,
      ['3', '100'],
      '3',
    ];
    const expectedOrder = constants.orderFilterValues.DESC;
    const expectedLimit = 3;

    verifyExtractSqlFromTokenRequest(
      initialQuery,
      initialParams,
      filters,
      [accountQueryCondition],
      expectedQuery,
      expectedParams,
      expectedOrder,
      expectedLimit
    );
  });
});

const verifyExtractSqlFromTokenRequest = (
  pgSqlQuery,
  pgSqlParams,
  filters,
  extraConditions,
  expectedQuery,
  expectedParams,
  expectedOrder,
  expectedLimit
) => {
  const {query, params, order, limit} = tokens.extractSqlFromTokenRequest(
    pgSqlQuery,
    pgSqlParams,
    filters,
    extraConditions
  );

  assertSqlQueryEqual(query, expectedQuery);
  expect(params).toStrictEqual(expectedParams);
  expect(order).toStrictEqual(expectedOrder);
  expect(limit).toStrictEqual(expectedLimit);
};

describe('token formatNftTransactionHistoryRow', () => {
  const defaultRow = {
    consensus_timestamp: 123456000987654,
    nonce: 0,
    nft_transfer: [
      {
        is_approval: true,
        receiver_account_id: 2000,
        serial_number: 1,
        sender_account_id: 3000,
        token_id: 4000,
      },
    ],
    payer_account_id: '500',
    type: 14,
    valid_start_ns: 123450000111222,
    is_approval: true,
  };
  const expected = {
    consensus_timestamp: '123456.000987654',
    nonce: 0,
    receiver_account_id: '0.0.2000',
    sender_account_id: '0.0.3000',
    transaction_id: '0.0.500-123450-000111222',
    type: 'CRYPTOTRANSFER',
    is_approval: true,
  };

  const testSpecs = [
    {
      name: 'default',
      row: defaultRow,
      expected: expected,
    },
    {
      name: 'nonce',
      row: {
        ...defaultRow,
        nonce: 1,
      },
      expected: {
        ...expected,
        nonce: 1,
      },
    },
    {
      name: 'tokendeletion',
      row: {
        ...defaultRow,
        nft_transfer: [
          {
            is_approval: false,
            receiver_account_id: null,
            serial_number: 1,
            sender_account_id: null,
            token_id: 4000,
          },
        ],
        type: 35,
      },
      expected: {
        ...expected,
        is_approval: false,
        receiver_account_id: null,
        sender_account_id: null,
        type: 'TOKENDELETION',
      },
    },
  ];

  for (const testSpec of testSpecs) {
    test(testSpec.name, () => {
      expect(tokens.formatNftTransactionHistoryRow(testSpec.row)).toEqual(testSpec.expected);
    });
  }
});

describe('token formatTokenBalanceRow tests', () => {
  test('Verify formatTokenBalanceRow', () => {
    const rowInput = {
      account_id: '193',
      balance: 200,
    };
    const expectedOutput = {
      account: '0.0.193',
      balance: 200,
      decimals: 5000,
    };

    expect(tokens.formatTokenBalanceRow(rowInput, 5000)).toEqual(expectedOutput);
  });
});

describe('token extractSqlFromTokenBalancesRequest tests', () => {
  const operators = Object.values(opsMap);
  const tokenId = '1009'; // encoded
  const accountId = '960'; // encoded
  const balance = '2000';
  const publicKey = '3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be';

  const testSpecs = [
    {
      name: 'no filters',
      tokenId,
      filters: [],
      expected: {
        query: `
          with filtered_token_accounts as (
            select ti.account_id, ti.balance, ti.balance_timestamp
              from token_account as ti
              where ti.token_id = $1 and ti.associated = true
              order by ti.account_id desc
              limit $2
          )
          select 
            tif.account_id,
            tif.balance,
            (select MAX(balance_timestamp) from filtered_token_accounts) as consensus_timestamp
          from filtered_token_accounts as tif
        `,
        params: [tokenId, defaultLimit],
        order: constants.orderFilterValues.DESC,
        limit: defaultLimit,
      },
    },
    {
      name: 'limit = 30',
      tokenId,
      filters: [
        {
          key: constants.filterKeys.LIMIT,
          operator: opsMap.eq,
          value: 30,
        },
      ],
      expected: {
        query: `
          with filtered_token_accounts as (
            select ti.account_id, ti.balance, ti.balance_timestamp
              from token_account as ti
              where ti.token_id = $1 and ti.associated = true
              order by ti.account_id desc
              limit $2
          )
          select 
            tif.account_id,
            tif.balance,
            (select MAX(balance_timestamp) from filtered_token_accounts) as consensus_timestamp
          from filtered_token_accounts as tif
        `,
        params: [tokenId, 30],
        order: constants.orderFilterValues.DESC,
        limit: 30,
      },
    },
    ...operators.map((op) => {
      return {
        name: `account.id ${op} ${accountId}`,
        tokenId,
        filters: [
          {
            key: constants.filterKeys.ACCOUNT_ID,
            operator: op,
            value: accountId,
          },
        ],
        expected: {
          query: `
            with filtered_token_accounts as (
              select ti.account_id, ti.balance, ti.balance_timestamp
              from token_account as ti
                where ti.token_id = $1
                  and ti.account_id ${op} $2
                  and ti.associated = true
                order by ti.account_id desc
                limit $3
            )
            select 
              tif.account_id,
              tif.balance,
              (select MAX(balance_timestamp) from filtered_token_accounts) as consensus_timestamp
            from filtered_token_accounts as tif
          `,
          params: [tokenId, accountId, defaultLimit],
          order: constants.orderFilterValues.DESC,
          limit: defaultLimit,
        },
      };
    }),
    ...operators.map((op) => {
      return {
        name: `balance ${op} ${balance}`,
        tokenId,
        filters: [
          {
            key: constants.filterKeys.ACCOUNT_BALANCE,
            operator: op,
            value: balance,
          },
        ],
        expected: {
          query: `
            with filtered_token_accounts as (
              select ti.account_id, ti.balance, ti.balance_timestamp
                from token_account as ti
                where ti.token_id = $1
                  and ti.associated = true
                  and ti.balance ${op} $2
                order by ti.account_id desc
                limit $3
            )
            select 
              tif.account_id,
              tif.balance,
              (select MAX(balance_timestamp) from filtered_token_accounts) as consensus_timestamp
            from filtered_token_accounts as tif
          `,
          params: [tokenId, balance, defaultLimit],
          order: constants.orderFilterValues.DESC,
          limit: defaultLimit,
        },
      };
    }),
    ...Object.values(constants.orderFilterValues).map((order) => {
      return {
        name: `order ${order}`,
        tokenId,
        filters: [
          {
            key: constants.filterKeys.ORDER,
            operator: opsMap.eq,
            value: order,
          },
        ],
        expected: {
          query: `
          with filtered_token_accounts as (
            select ti.account_id, ti.balance, ti.balance_timestamp
              from token_account as ti
              where ti.token_id = $1 and ti.associated = true
              order by ti.account_id ${order}
              limit $2
          )
          select 
            tif.account_id,
            tif.balance,
            (select MAX(balance_timestamp) from filtered_token_accounts) as consensus_timestamp
          from filtered_token_accounts as tif
          `,
          params: [tokenId, defaultLimit],
          order,
          limit: defaultLimit,
        },
      };
    }),
    {
      name: `account publickey "${publicKey}"`,
      tokenId,
      filters: [
        {
          key: constants.filterKeys.ACCOUNT_PUBLICKEY,
          operator: opsMap.eq,
          value: publicKey,
        },
      ],
      expected: {
        query: `
          with filtered_token_accounts as (
            select ti.account_id, ti.balance, ti.balance_timestamp
              from token_account as ti
              join entity as e
                on e.type = '${constants.entityTypes.ACCOUNT}'
                and e.id = ti.account_id
                and e.public_key = $2
              where ti.token_id = $1 and ti.associated = true
              order by ti.account_id desc
              limit $3
          )
          select 
            tif.account_id,
            tif.balance,
            (select MAX(balance_timestamp) from filtered_token_accounts) as consensus_timestamp
          from filtered_token_accounts as tif
        `,
        params: [tokenId, publicKey, defaultLimit],
        order: constants.orderFilterValues.DESC,
        limit: defaultLimit,
      },
    },
    {
      name: 'all filters except timestamp',
      tokenId,
      filters: [
        {
          key: constants.filterKeys.ACCOUNT_ID,
          operator: opsMap.eq,
          value: accountId,
        },
        {
          key: constants.filterKeys.ACCOUNT_BALANCE,
          operator: opsMap.eq,
          value: balance,
        },
        {
          key: constants.filterKeys.ACCOUNT_PUBLICKEY,
          operator: opsMap.eq,
          value: publicKey,
        },
        {
          key: constants.filterKeys.LIMIT,
          operator: opsMap.eq,
          value: 1,
        },
        {
          key: constants.filterKeys.ORDER,
          operator: opsMap.eq,
          value: constants.orderFilterValues.ASC,
        },
      ],
      expected: {
        query: `
          with filtered_token_accounts as (
            select ti.account_id, ti.balance, ti.balance_timestamp
            from token_account as ti
            join entity as e
              on e.type = '${constants.entityTypes.ACCOUNT}'
                and e.id = ti.account_id
                and e.public_key = $4
            where ti.token_id = $1
              and ti.account_id = $2
              and ti.associated = true
              and ti.balance = $3
            order by ti.account_id asc
            limit $5
          )
          select 
            tif.account_id,
            tif.balance,
            (select MAX(balance_timestamp) from filtered_token_accounts) as consensus_timestamp
          from filtered_token_accounts as tif
        `,
        params: [tokenId, accountId, balance, publicKey, 1],
        order: constants.orderFilterValues.ASC,
        limit: 1,
      },
    },
  ];

  for (const spec of testSpecs) {
    const {name, tokenId, filters, expected} = spec;
    test(name, async () => {
      const actual = await tokens.extractSqlFromTokenBalancesRequest(tokenId, filters);
      assertSqlQueryEqual(actual.query, expected.query);
      expect(actual).toEqual(
        expect.objectContaining({
          params: spec.expected.params,
          order: spec.expected.order,
          limit: spec.expected.limit,
        })
      );
    });
  }
});

describe('token formatTokenInfoRow tests', () => {
  const rowInput = {
    token_id: '7',
    symbol: 'YBTJBOAZ',
    treasury_account_id: '3',
    freeze_default: true,
    key: [1, 1, 1],
    kyc_key: [2, 2, 2],
    fee_schedule_key: [6, 6, 6],
    freeze_key: [3, 3, 3],
    metadata_key: null,
    pause_key: [7, 7, 7],
    pause_status: 'UNPAUSED',
    supply_key: [4, 4, 4],
    wipe_key: [5, 5, 5],
    created_timestamp: 10,
    decimals: 10,
    deleted: true,
    initial_supply: 1000000,
    total_supply: 2000000,
    expiration_timestamp: 1594431063696143000,
    auto_renew_account_id: '98',
    auto_renew_period: 7890000,
    modified_timestamp: 1603394416676293000,
    name: 'Token name',
    type: 'FUNGIBLE_COMMON',
    max_supply: '9000000',
    supply_type: 'FINITE',
    memo: 'token.memo',
    metadata: null,
    custom_fee: {
      created_timestamp: 10,
      fixed_fees: [
        {
          amount: 55,
          collector_account_id: 8901,
        },
        {
          amount: 59,
          collector_account_id: 8901,
          denominating_token_id: 19502,
        },
      ],
      fractional_fees: [
        {
          collector_account_id: 8902,
          denominator: 77,
          maximum_amount: 150,
          minimum_amount: 43,
          numerator: 66,
        },
        {
          collector_account_id: 8903,
          denominator: 94,
          minimum_amount: 1,
          numerator: 83,
        },
      ],
      token_id: '7',
    },
  };

  const expected = {
    admin_key: {
      _type: 'ProtobufEncoded',
      key: '010101',
    },
    auto_renew_account: '0.0.98',
    auto_renew_period: 7890000,
    created_timestamp: '0.000000010',
    decimals: '10',
    deleted: true,
    expiry_timestamp: 1594431063696143000,
    fee_schedule_key: {
      _type: 'ProtobufEncoded',
      key: '060606',
    },
    freeze_default: true,
    freeze_key: {
      _type: 'ProtobufEncoded',
      key: '030303',
    },
    initial_supply: '1000000',
    kyc_key: {
      _type: 'ProtobufEncoded',
      key: '020202',
    },
    max_supply: '9000000',
    memo: 'token.memo',
    metadata: '',
    metadata_key: null,
    modified_timestamp: '1603394416.676293000',
    name: 'Token name',
    pause_key: {
      _type: 'ProtobufEncoded',
      key: '070707',
    },
    pause_status: 'UNPAUSED',
    supply_key: {
      _type: 'ProtobufEncoded',
      key: '040404',
    },
    supply_type: 'FINITE',
    symbol: 'YBTJBOAZ',
    token_id: '0.0.7',
    total_supply: '2000000',
    treasury_account_id: '0.0.3',
    type: 'FUNGIBLE_COMMON',
    wipe_key: {
      _type: 'ProtobufEncoded',
      key: '050505',
    },
    custom_fees: {
      created_timestamp: '0.000000010',
      fixed_fees: [
        {
          all_collectors_are_exempt: false,
          amount: 55,
          collector_account_id: '0.0.8901',
          denominating_token_id: null,
        },
        {
          all_collectors_are_exempt: false,
          amount: 59,
          collector_account_id: '0.0.8901',
          denominating_token_id: '0.0.19502',
        },
      ],
      fractional_fees: [
        {
          all_collectors_are_exempt: false,
          amount: {
            numerator: 66,
            denominator: 77,
          },
          collector_account_id: '0.0.8902',
          denominating_token_id: '0.0.7',
          maximum: 150,
          minimum: 43,
          net_of_transfers: false,
        },
        {
          all_collectors_are_exempt: false,
          amount: {
            numerator: 83,
            denominator: 94,
          },
          collector_account_id: '0.0.8903',
          denominating_token_id: '0.0.7',
          minimum: 1,
          maximum: null,
          net_of_transfers: false,
        },
      ],
    },
  };

  const rowInputWithEmptyMetadata = {
    ...rowInput,
    metadata: [],
  };

  const rowInputWithMetadataAndKey = {
    ...rowInput,
    metadata: Uint8Array.of(1, 2, 3),
    metadata_key: [8, 8, 8],
  };

  const expectedWithMetadataAndKey = {
    ...expected,
    metadata: '1,2,3',
    metadata_key: {
      _type: 'ProtobufEncoded',
      key: '080808',
    },
  };

  test('Verify formatTokenRow', () => {
    const actual = tokens.formatTokenInfoRow(rowInput);
    expect(actual).toEqual(expected);
  });

  test('Verify formatTokenRowWithEmptyMetadata', () => {
    const actual = tokens.formatTokenInfoRow(rowInputWithEmptyMetadata);
    expect(actual).toEqual(expected);
  });

  test('Verify formatTokenRowWithMetadataAndKey', () => {
    const actual = tokens.formatTokenInfoRow(rowInputWithMetadataAndKey);
    expect(actual).toEqual(expectedWithMetadataAndKey);
  });
});

const verifyInvalidAndValidTokensFilters = (invalidQueries, validQueries, validator) => {
  const acceptedParameters = new Set([
    constants.filterKeys.ACCOUNT_ID,
    constants.filterKeys.TIMESTAMP,
    constants.filterKeys.SERIAL_NUMBER,
    constants.filterKeys.TOKEN_TYPE,
  ]);

  invalidQueries.forEach((query) => {
    test(`Verify buildAndValidateFilters for invalid ${JSON.stringify(query)}`, () => {
      expect(() => utils.buildAndValidateFilters(query, acceptedParameters, validator)).toThrowErrorMatchingSnapshot();
    });
  });

  validQueries.forEach((query) => {
    test(`Verify buildAndValidateFilters for valid ${JSON.stringify(query)}`, () => {
      utils.buildAndValidateFilters(query, acceptedParameters, validator);
    });
  });
};

const makeQueries = (key, values) => {
  return values.map((v) => ({[key]: v}));
};

describe('utils buildAndValidateFilters token type tests', () => {
  const key = constants.filterKeys.TOKEN_TYPE;
  const invalidQueries = makeQueries(key, [
    // invalid format
    'cred',
    'deb',
    // erroneous data
    '-1',
  ]);
  const validQueries = makeQueries(key, [
    'all',
    'ALL',
    'NON_FUNGIBLE_UNIQUE',
    'non_fungible_unique',
    'FUNGIBLE_COMMON',
    'fungible_common',
  ]);

  verifyInvalidAndValidTokensFilters(invalidQueries, validQueries, tokens.validateTokenQueryFilter);
});

describe('utils buildAndValidateFilters serialnumbers tests', () => {
  const key = constants.filterKeys.SERIAL_NUMBER;
  const invalidQueries = makeQueries(key, [
    // invalid format
    '-1',
    'deb',
    // erroneous data
    '0',
  ]);
  const validQueries = makeQueries(key, [
    '1',
    '21',
    '9007199254740991',
    'eq:324',
    'gt:324',
    'gte:324',
    'lt:324',
    'lte:324',
  ]);

  verifyInvalidAndValidTokensFilters(invalidQueries, validQueries, tokens.validateTokenQueryFilter);
});

describe('utils validateAndParseFilters account.id tests', () => {
  const key = constants.filterKeys.ACCOUNT_ID;
  const invalidQueries = makeQueries(key, [
    // invalid format
    'L',
    '@.#.$',
    // erroneous data
    '-92233720368547758080',
    '0.1.2.3',
    '-1.-1.-1',
  ]);
  const validQueries = makeQueries(key, [
    '1001',
    '0.1001',
    '0.0.1001',
    '21',
    '1234567890',
    'eq:0.0.1001',
    'lt:0.0.1001',
    'lte:0.0.1001',
    'gt:0.0.1001',
    'gte:0.0.1001',
    'eq:1001',
    'lt:324',
    'lte:324',
    'gt:324',
    'gte:324',
  ]);

  verifyInvalidAndValidTokensFilters(invalidQueries, validQueries, tokens.validateTokenQueryFilter);
});

describe('utils buildAndValidateFilters token info query timestamp filter tests', () => {
  const key = constants.filterKeys.TIMESTAMP;
  const invalidQueries = makeQueries(key, ['abc', '', 'ne:1234', 'gt:1234', 'gte:1234']).concat(
    makeQueries(constants.filterKeys.SERIAL_NUMBER, ['123456'])
  );
  const validQueries = makeQueries(key, ['1', '123456789.000111', ['1', 'lt:123456789.000111']]);

  verifyInvalidAndValidTokensFilters(invalidQueries, validQueries, tokens.validateTokenInfoFilter);
});

describe('token extractSqlFromNftTokensRequest tests', () => {
  const verifyExtractSqlFromNftTokensRequest = (
    tokenId,
    pgSqlQuery,
    filters,
    expectedquery,
    expectedparams,
    expectedorder,
    expectedlimit
  ) => {
    const {query, params, order, limit} = tokens.extractSqlFromNftTokensRequest(tokenId, pgSqlQuery, filters);

    assertSqlQueryEqual(query, expectedquery);
    expect(params).toStrictEqual(expectedparams);
    expect(order).toStrictEqual(expectedorder);
    expect(limit).toStrictEqual(expectedlimit);
  };

  test('Verify simple discovery query', () => {
    const tokenId = '1009'; // encoded
    const initialQuery = tokens.nftSelectQuery;
    const filters = [];

    const expectedQuery = `select nft.account_id,
                                  nft.created_timestamp,
                                  nft.delegating_spender,
                                  nft.deleted or coalesce(e.deleted, false) as deleted,
                                  nft.metadata,
                                  nft.serial_number,
                                  nft.spender,
                                  nft.timestamp_range,
                                  nft.token_id
                           from nft
                                  left join entity e on e.id = nft.token_id
                           where nft.token_id = $1
                           order by nft.serial_number desc
                           limit $2`;
    const expectedParams = [tokenId, defaultLimit];
    const expectedOrder = constants.orderFilterValues.DESC;
    const expectedLimit = defaultLimit;

    verifyExtractSqlFromNftTokensRequest(
      tokenId,
      initialQuery,
      filters,
      expectedQuery,
      expectedParams,
      expectedOrder,
      expectedLimit
    );
  });

  test('Verify account id', () => {
    const tokenId = '1009'; // encoded
    const accountId = '111'; // encoded
    const initialQuery = [tokens.nftSelectQuery].join('\n');
    const filters = [
      {
        key: constants.filterKeys.ACCOUNT_ID,
        operator: ' = ',
        value: accountId,
      },
    ];

    const expectedQuery = `select nft.account_id,
                                  nft.created_timestamp,
                                  nft.delegating_spender,
                                  nft.deleted or coalesce(e.deleted, false) as deleted,
                                  nft.metadata,
                                  nft.serial_number,
                                  nft.spender,
                                  nft.timestamp_range,
                                  nft.token_id
                           from nft
                                  left join entity e on e.id = nft.token_id
                           where nft.token_id = $1
                             and nft.account_id = $2
                           order by nft.serial_number desc
                           limit $3`;
    const expectedParams = [tokenId, accountId, defaultLimit];
    const expectedOrder = constants.orderFilterValues.DESC;
    const expectedLimit = defaultLimit;
    verifyExtractSqlFromNftTokensRequest(
      tokenId,
      initialQuery,
      filters,
      expectedQuery,
      expectedParams,
      expectedOrder,
      expectedLimit
    );
  });

  test('Verify serial number id', () => {
    const tokenId = '1009'; // encoded
    const serialFilter = 'gt:111';
    const initialQuery = [tokens.nftSelectQuery].join('\n');
    const filters = [
      {
        key: constants.filterKeys.SERIAL_NUMBER,
        operator: ' = ',
        value: serialFilter,
      },
    ];

    const expectedQuery = `select nft.account_id,
                                  nft.created_timestamp,
                                  nft.delegating_spender,
                                  nft.deleted or coalesce(e.deleted, false) as deleted,
                                  nft.metadata,
                                  nft.serial_number,
                                  nft.spender,
                                  nft.timestamp_range,
                                  nft.token_id
                           from nft
                                  left join entity e on e.id = nft.token_id
                           where nft.token_id = $1
                             and nft.serial_number = $2
                           order by nft.serial_number desc
                           limit $3`;
    const expectedParams = [tokenId, serialFilter, defaultLimit];
    const expectedOrder = constants.orderFilterValues.DESC;
    const expectedLimit = defaultLimit;
    verifyExtractSqlFromNftTokensRequest(
      tokenId,
      initialQuery,
      filters,
      expectedQuery,
      expectedParams,
      expectedOrder,
      expectedLimit
    );
  });

  test('Verify all filters', () => {
    const initialQuery = [tokens.nftSelectQuery].join('\n');
    const tokenId = '1009'; // encoded
    const accountId = '5';
    const serialNum = '2';
    const limit = '3';
    const order = constants.orderFilterValues.ASC;
    const filters = [
      {
        key: constants.filterKeys.ACCOUNT_ID,
        operator: ' = ',
        value: accountId,
      },
      {
        key: constants.filterKeys.SERIAL_NUMBER,
        operator: ' = ',
        value: serialNum,
      },
      {key: constants.filterKeys.LIMIT, operator: ' = ', value: limit},
      {key: constants.filterKeys.ORDER, operator: ' = ', value: order},
    ];

    const expectedQuery = `select nft.account_id,
                                  nft.created_timestamp,
                                  nft.delegating_spender,
                                  nft.deleted or coalesce(e.deleted, false) as deleted,
                                  nft.metadata,
                                  nft.serial_number,
                                  nft.spender,
                                  nft.timestamp_range,
                                  nft.token_id
                           from nft
                                  left join entity e on e.id = nft.token_id
                           where nft.token_id = $1
                             and nft.account_id = $2
                             and nft.serial_number = $3
                           order by nft.serial_number asc
                           limit $4`;
    const expectedParams = [tokenId, accountId, serialNum, limit];
    const expectedOrder = order;
    const expectedLimit = 3;

    verifyExtractSqlFromNftTokensRequest(
      tokenId,
      initialQuery,
      filters,
      expectedQuery,
      expectedParams,
      expectedOrder,
      expectedLimit
    );
  });
});

describe('token extractSqlFromNftTokenInfoRequest tests', () => {
  const verifyExtractSqlFromNftTokenInfoRequest = (
    tokenId,
    serialNumber,
    pgSqlQuery,
    filters,
    expectedquery,
    expectedparams
  ) => {
    const {query, params} = tokens.extractSqlFromNftTokenInfoRequest(tokenId, serialNumber, pgSqlQuery, filters);

    assertSqlQueryEqual(query, expectedquery);
    expect(params).toStrictEqual(expectedparams);
  };

  test('Verify simple serial query', () => {
    const tokenId = '1009'; // encoded
    const serialNumber = '960';
    const initialQuery = [tokens.nftSelectQuery].join('\n');
    const filters = [];

    const expectedQuery = `select nft.account_id,
                                  nft.created_timestamp,
                                  nft.delegating_spender,
                                  nft.deleted or coalesce(e.deleted, false) as deleted,
                                  nft.metadata,
                                  nft.serial_number,
                                  nft.spender,
                                  nft.timestamp_range,
                                  nft.token_id
                           from nft
                           left join entity e on e.id = nft.token_id
                           where nft.token_id = $1
                             and nft.serial_number = $2`;
    const expectedParams = [tokenId, serialNumber];
    verifyExtractSqlFromNftTokenInfoRequest(
      tokenId,
      serialNumber,
      initialQuery,
      filters,
      expectedQuery,
      expectedParams
    );
  });
});

describe('token validateSerialNumberParam tests', () => {
  const invalidSerialNums = ['', '-1', null, undefined, 'end', 0, '0'];
  const validSerialNums = [1, '1', '9007199254740991'];

  invalidSerialNums.forEach((serialNum) => {
    test(`Verify validateSerialNumberParam for invalid ${serialNum}`, () => {
      expect(() => tokens.validateSerialNumberParam(serialNum)).toThrowErrorMatchingSnapshot();
    });
  });

  validSerialNums.forEach((serialNum) => {
    test(`Verify validateSerialNumberParam for valid ${serialNum}`, () => {
      tokens.validateSerialNumberParam(serialNum);
    });
  });
});

describe('token extractSqlFromNftTransferHistoryRequest tests', () => {
  const getExpectedQuery = (order = constants.orderFilterValues.DESC, timestampFilters = []) => {
    let index = 3;
    const timestampConditions = timestampFilters.map(({operator}) => `lower(timestamp_range) ${operator} $${index++}`);
    const nftCondition = ['token_id = $1', 'serial_number = $2', ...timestampConditions].join(' and ');
    const tokenDeleteCondition = ['id = $1', 'deleted is true', ...timestampConditions].join(' and ');
    const limitQuery = `limit $${index}`;
    return `select timestamp
      from (
        (
          select lower(timestamp_range) as timestamp
          from nft
          where ${nftCondition}
          union all (
            select lower(timestamp_range) as timestamp
            from nft_history
            where ${nftCondition}
            order by timestamp ${order}
            ${limitQuery})
        ) union all (
          select lower(timestamp_range) as timestamp
          from entity
          where ${tokenDeleteCondition}
        )) as nft_event
        order by timestamp ${order}
        ${limitQuery}`;
  };

  const tokenId = 1009; // encoded
  const serialNumber = 9886299254743552n; // larger than MAX_SAFE_INTEGER
  const defaultParams = [tokenId, serialNumber];

  test('Verify simple query', () => {
    const expectedQuery = getExpectedQuery();
    const expectedParams = [...defaultParams, defaultLimit];

    const actual = tokens.extractSqlFromNftTransferHistoryRequest(tokenId, serialNumber, []);
    assertSqlQueryEqual(actual.query, expectedQuery);
    expect(actual.params).toStrictEqual(expectedParams);
  });

  test('Verify limit and order query', () => {
    const limit = '3';
    const order = constants.orderFilterValues.ASC;
    const filters = [
      {key: constants.filterKeys.LIMIT, operator: utils.opsMap.eq, value: limit},
      {key: constants.filterKeys.ORDER, operator: utils.opsMap.eq, value: order},
    ];

    const expectedQuery = getExpectedQuery(order);
    const expectedParams = [...defaultParams, limit];

    const actual = tokens.extractSqlFromNftTransferHistoryRequest(tokenId, serialNumber, filters);
    assertSqlQueryEqual(actual.query, expectedQuery);
    expect(actual.params).toStrictEqual(expectedParams);
  });

  test('Verify timestamp query', () => {
    const timestamp = 5;
    const filters = [{key: constants.filterKeys.TIMESTAMP, operator: utils.opsMap.gt, value: timestamp}];

    const expectedQuery = getExpectedQuery(constants.orderFilterValues.DESC, filters);
    const expectedParams = [...defaultParams, timestamp, defaultLimit];

    const actual = tokens.extractSqlFromNftTransferHistoryRequest(tokenId, serialNumber, filters);
    assertSqlQueryEqual(actual.query, expectedQuery);
    expect(actual.params).toStrictEqual(expectedParams);
  });
});

describe('token extractSqlFromTokenInfoRequest tests', () => {
  const getExpectedQuery = (timestampCondition = '') => {
    let tokenQuery = 'token';
    let entityQuery = 'entity';
    let customFeeQuery = 'custom_fee';

    if (!_.isEmpty(timestampCondition)) {
      tokenQuery = tokens.buildHistoryQuery(
        tokens.tokenSelectFields,
        `token_id = $1 and lower(timestamp_range) ${timestampCondition}`,
        Token.tableName,
        Token.tableAlias
      );
      entityQuery = tokens.buildHistoryQuery(
        tokens.entitySelectFields,
        `id = $1 and lower(timestamp_range) ${timestampCondition}`,
        Entity.tableName,
        Entity.tableAlias
      );
      customFeeQuery = tokens.buildHistoryQuery(
        tokens.customFeeSelectFields,
        `entity_id = $1 and lower(timestamp_range) ${timestampCondition}`,
        CustomFee.tableName,
        CustomFee.tableAlias
      );
    }

    return `${tokens.tokenInfoOuterSelect}
            from ${tokenQuery} as ${Token.tableAlias}
            join ${entityQuery} as ${Entity.tableAlias} on ${Entity.getFullName(Entity.ID)} = ${Token.getFullName(
      Token.TOKEN_ID
    )}
            left join ${customFeeQuery} as ${CustomFee.tableAlias} on 
                 ${CustomFee.getFullName(CustomFee.ENTITY_ID)} = ${Token.getFullName(Token.TOKEN_ID)}
            where token_id = $1`;
  };

  const verifyExtractSqlFromTokenInfoRequest = (tokenId, filters, expectedQuery, expectedParams) => {
    const {query, params} = tokens.extractSqlFromTokenInfoRequest(tokenId, filters);

    assertSqlQueryEqual(query, expectedQuery);
    expect(params).toStrictEqual(expectedParams);
  };

  const timestamp = '123456789000111222';
  const encodedTokenId = '1009'; // encoded

  test('Verify simple query', () => {
    verifyExtractSqlFromTokenInfoRequest(encodedTokenId, [], getExpectedQuery(), [encodedTokenId]);
  });

  [opsMap.lt, opsMap.lte].forEach((op) =>
    test(`Verify query with timestamp and op ${op}`, () => {
      const expectedQuery = getExpectedQuery(`${op} $2`);
      const expectedParams = [encodedTokenId, timestamp];
      const filters = [
        {
          key: constants.filterKeys.TIMESTAMP,
          operator: op,
          value: timestamp,
        },
      ];

      verifyExtractSqlFromTokenInfoRequest(encodedTokenId, filters, expectedQuery, expectedParams);
    })
  );

  test('Verify query with multiple timestamp filters', () => {
    const expectedQuery = getExpectedQuery('<= $2');
    const expectedParams = [encodedTokenId, timestamp];
    // honor the last one
    const filters = [
      {
        key: constants.filterKeys.TIMESTAMP,
        operator: opsMap.lt,
        value: timestamp,
      },
      {
        key: constants.filterKeys.TIMESTAMP,
        operator: opsMap.lte,
        value: timestamp,
      },
    ];

    verifyExtractSqlFromTokenInfoRequest(encodedTokenId, filters, expectedQuery, expectedParams);
  });
});
