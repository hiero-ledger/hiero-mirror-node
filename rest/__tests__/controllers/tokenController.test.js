// SPDX-License-Identifier: Apache-2.0

import * as constants from '../../constants';
import * as utils from '../../utils';
import tokenController from '../../controllers/tokenController';

const ownerAccountId = BigInt(98);
const TOKEN_ID = constants.filterKeys.TOKEN_ID;
const tokenIdEqFilter = {key: TOKEN_ID, operator: utils.opsMap.eq, value: 10};
const tokenIdGtFilter = {key: TOKEN_ID, operator: utils.opsMap.gt, value: 101};
const tokenIdGteFilter = {key: TOKEN_ID, operator: utils.opsMap.gte, value: 102};
const tokenIdLtFilter = {key: TOKEN_ID, operator: utils.opsMap.lt, value: 150};
const tokenIdLteFilter = {key: TOKEN_ID, operator: utils.opsMap.lte, value: 151};

describe('extractTokenRelationshipQuery', () => {
  const defaultExpected = {
    conditions: [],
    inConditions: [],
    ownerAccountId: ownerAccountId,
    order: constants.orderFilterValues.ASC,
    limit: 25,
  };

  const specs = [
    {
      name: 'limit',
      input: {
        filters: [
          {
            key: constants.filterKeys.LIMIT,
            operator: utils.opsMap.eq,
            value: 20,
          },
        ],
      },
      expected: {
        ...defaultExpected,
        limit: 20,
      },
    },
    {
      name: 'order desc',
      input: {
        filters: [
          {
            key: constants.filterKeys.ORDER,
            operator: utils.opsMap.eq,
            value: constants.orderFilterValues.DESC,
          },
        ],
      },
      expected: {
        ...defaultExpected,
        order: constants.orderFilterValues.DESC,
      },
    },
    {
      name: 'token eq',
      input: {
        filters: [tokenIdEqFilter],
      },
      expected: {
        ...defaultExpected,
        inConditions: [
          {
            key: 'token_id',
            operator: ' = ',
            value: 10,
          },
        ],
      },
    },
    {
      name: 'token gt',
      input: {
        filters: [tokenIdGtFilter],
      },
      expected: {
        ...defaultExpected,
        conditions: [
          {
            key: 'token_id',
            operator: ' > ',
            value: 101,
          },
        ],
      },
    },
    {
      name: 'token gte',
      input: {
        filters: [tokenIdGteFilter],
      },
      expected: {
        ...defaultExpected,
        conditions: [
          {
            key: 'token_id',
            operator: ' >= ',
            value: 102,
          },
        ],
      },
    },
    {
      name: 'token lt',
      input: {
        filters: [tokenIdLtFilter],
      },
      expected: {
        ...defaultExpected,
        conditions: [
          {
            key: 'token_id',
            operator: ' < ',
            value: 150,
          },
        ],
      },
    },
    {
      name: 'token lte',
      input: {
        filters: [tokenIdLteFilter],
      },
      expected: {
        ...defaultExpected,
        conditions: [
          {
            key: 'token_id',
            operator: ' <= ',
            value: 151,
          },
        ],
      },
    },
    {
      name: 'token gt and token lte',
      input: {
        filters: [tokenIdGtFilter, tokenIdLteFilter],
      },
      expected: {
        ...defaultExpected,
        conditions: [
          {
            key: 'token_id',
            operator: ' > ',
            value: 101,
          },
          {
            key: 'token_id',
            operator: ' <= ',
            value: 151,
          },
        ],
      },
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name}`, () => {
      expect(tokenController.extractTokensRelationshipQuery(spec.input.filters, ownerAccountId)).toEqual(spec.expected);
    });
  });
});
