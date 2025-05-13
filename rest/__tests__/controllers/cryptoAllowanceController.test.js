// SPDX-License-Identifier: Apache-2.0

import {getResponseLimit} from '../../config';
import * as constants from '../../constants';
import {CryptoAllowanceController} from '../../controllers';
import * as utils from '../../utils';

const {default: defaultLimit} = getResponseLimit();

const amountFilter = 'amount > 0';
const ownerIdFilter = 'owner = $1';

describe('extractCryptoAllowancesQuery', () => {
  const defaultExpected = {
    conditions: [ownerIdFilter, amountFilter],
    params: [1],
    order: constants.orderFilterValues.DESC,
    limit: defaultLimit,
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
        accountId: 1,
      },
      expected: {
        ...defaultExpected,
        limit: 20,
      },
    },
    {
      name: 'order asc',
      input: {
        filters: [
          {
            key: constants.filterKeys.ORDER,
            operator: utils.opsMap.eq,
            value: constants.orderFilterValues.ASC,
          },
        ],
        accountId: 1,
      },
      expected: {
        ...defaultExpected,
        order: constants.orderFilterValues.ASC,
      },
    },
    {
      name: 'spender.id single',
      input: {
        filters: [
          {
            key: constants.filterKeys.SPENDER_ID,
            operator: utils.opsMap.eq,
            value: '1000',
          },
        ],
        accountId: 3,
      },
      expected: {
        ...defaultExpected,
        conditions: [ownerIdFilter, 'spender in ($2)', amountFilter],
        params: [3, '1000'],
      },
    },
    {
      name: 'spender.id multiple',
      input: {
        filters: [
          {
            key: constants.filterKeys.SPENDER_ID,
            operator: utils.opsMap.eq,
            value: '1000',
          },
          {
            key: constants.filterKeys.SPENDER_ID,
            operator: utils.opsMap.eq,
            value: '1001',
          },
          {
            key: constants.filterKeys.SPENDER_ID,
            operator: utils.opsMap.eq,
            value: '1002',
          },
        ],
        accountId: 3,
      },
      expected: {
        ...defaultExpected,
        conditions: [ownerIdFilter, 'spender in ($2,$3,$4)', amountFilter],
        params: [3, '1000', '1001', '1002'],
      },
    },
    {
      name: 'spender.id all allowed operators',
      input: {
        filters: [
          {
            key: constants.filterKeys.SPENDER_ID,
            operator: utils.opsMap.eq,
            value: '1000',
          },
          {
            key: constants.filterKeys.SPENDER_ID,
            operator: utils.opsMap.gt,
            value: '200',
          },
          {
            key: constants.filterKeys.SPENDER_ID,
            operator: utils.opsMap.gte,
            value: '202',
          },
          {
            key: constants.filterKeys.SPENDER_ID,
            operator: utils.opsMap.lt,
            value: '3000',
          },
          {
            key: constants.filterKeys.SPENDER_ID,
            operator: utils.opsMap.lte,
            value: '3005',
          },
        ],
        accountId: 3,
      },
      expected: {
        ...defaultExpected,
        conditions: [
          ownerIdFilter,
          'spender > $2',
          'spender >= $3',
          'spender < $4',
          'spender <= $5',
          'spender in ($6)',
          amountFilter,
        ],
        params: [3, '200', '202', '3000', '3005', '1000'],
      },
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name}`, () => {
      expect(CryptoAllowanceController.extractCryptoAllowancesQuery(spec.input.filters, spec.input.accountId)).toEqual(
        spec.expected
      );
    });
  });
});

describe('validateExtractCryptoAllowancesQuery throw', () => {
  const specs = [
    {
      name: 'spender.id ne',
      input: {
        filters: [
          {
            key: constants.filterKeys.SPENDER_ID,
            operator: utils.opsMap.ne,
            value: '1000',
          },
        ],
        accountId: 3,
      },
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name}`, () => {
      expect(() =>
        CryptoAllowanceController.extractCryptoAllowancesQuery(spec.input.filters, spec.input.accountId)
      ).toThrowErrorMatchingSnapshot();
    });
  });
});
