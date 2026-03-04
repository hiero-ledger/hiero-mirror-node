// SPDX-License-Identifier: Apache-2.0

import * as constants from '../../constants';
import {NetworkController} from '../../controllers';
import networkCtrl from '../../controllers/networkController';
import * as utils from '../../utils';
import EntityId from '../../entityId.js';

describe('extractNetworkNodesQuery', () => {
  const defaultExpected = {
    conditions: [],
    params: [],
    order: constants.orderFilterValues.ASC,
    limit: 10,
  };
  const defaultNodeId = EntityId.parseString('10').getEncodedId();
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
        params: [EntityId.systemEntity.addressBookFile102.getEncodedId()],
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
        conditions: [],
        params: [EntityId.systemEntity.addressBookFile102.getEncodedId()],
      },
    },
    {
      name: 'node.id',
      input: {
        filters: [
          {
            key: constants.filterKeys.NODE_ID,
            operator: utils.opsMap.eq,
            value: defaultNodeId,
          },
        ],
      },
      expected: {
        ...defaultExpected,
        conditions: ['abe.node_id in ($2)'],
        params: [EntityId.systemEntity.addressBookFile102.getEncodedId(), defaultNodeId],
      },
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name}`, () => {
      expect(networkCtrl.extractNetworkNodesQuery(spec.input.filters)).toEqual(spec.expected);
    });
  });
});

describe('validateExtractNetworkNodesQuery throw', () => {
  const specs = [
    {
      name: 'file.id ne',
      input: {
        filters: [
          {
            key: constants.filterKeys.FILE_ID,
            operator: utils.opsMap.ne,
            value: '1000',
          },
        ],
      },
    },
    {
      name: 'file.id gt',
      input: {
        filters: [
          {
            key: constants.filterKeys.FILE_ID,
            operator: utils.opsMap.gt,
            value: '1000',
          },
        ],
      },
    },
    {
      name: 'file.id gte',
      input: {
        filters: [
          {
            key: constants.filterKeys.FILE_ID,
            operator: utils.opsMap.gte,
            value: '1000',
          },
        ],
      },
    },
    {
      name: 'file.id lte',
      input: {
        filters: [
          {
            key: constants.filterKeys.FILE_ID,
            operator: utils.opsMap.lt,
            value: '1000',
          },
        ],
      },
    },
    {
      name: 'file.id lte',
      input: {
        filters: [
          {
            key: constants.filterKeys.FILE_ID,
            operator: utils.opsMap.lte,
            value: '1000',
          },
        ],
      },
    },
    {
      name: 'multi file.id eq',
      input: {
        filters: [
          {
            key: constants.filterKeys.FILE_ID,
            operator: utils.opsMap.eq,
            value: '101',
          },
          {
            key: constants.filterKeys.FILE_ID,
            operator: utils.opsMap.eq,
            value: '102',
          },
        ],
      },
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name}`, () => {
      expect(() => NetworkController.extractNetworkNodesQuery(spec.input.filters)).toThrowErrorMatchingSnapshot();
    });
  });
});
