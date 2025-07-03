// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';

import {getResponseLimit} from '../config';
import * as constants from '../constants';
import schedules from '../schedules';
import * as utils from '../utils';
import EntityId from '../entityId';

const {default: defaultLimit} = getResponseLimit();

describe('schedule formatScheduleRow tests', () => {
  const defaultInput = {
    key: [3, 3, 3],
    consensus_timestamp: '1234567890000000001',
    creator_account_id: '100',
    deleted: false,
    expiration_time: '1234567890000000000',
    executed_timestamp: '1234567890000000002',
    memo: 'Created per council decision dated 1/21/21',
    payer_account_id: '101',
    schedule_id: '102',
    signatures: [
      {
        consensus_timestamp: '1234567890000000001',
        public_key_prefix: Buffer.from([0xa1, 0xb1, 0xc1]).toString('base64'),
        signature: Buffer.from([0xa2, 0xb2, 0xc2]).toString('base64'),
        type: 3,
      },
      {
        consensus_timestamp: '1234567890000000010',
        public_key_prefix: Buffer.from([0xd1, 0xe1, 0xf1]).toString('base64'),
        signature: Buffer.from([0xd2, 0xe2, 0xf2]).toString('base64'),
        type: 6,
      },
    ],
    transaction_body: Buffer.from([0x29, 0xde, 0xad, 0xbe, 0xef]),
    wait_for_expiry: true,
  };
  const defaultExpected = {
    admin_key: {
      _type: 'ProtobufEncoded',
      key: '030303',
    },
    consensus_timestamp: '1234567890.000000001',
    creator_account_id: '0.0.100',
    deleted: false,
    expiration_time: '1234567890.000000000',
    executed_timestamp: '1234567890.000000002',
    memo: 'Created per council decision dated 1/21/21',
    payer_account_id: '0.0.101',
    schedule_id: '0.0.102',
    signatures: [
      {
        consensus_timestamp: '1234567890.000000001',
        public_key_prefix: 'obHB',
        signature: 'orLC',
        type: 'ED25519',
      },
      {
        consensus_timestamp: '1234567890.000000010',
        public_key_prefix: '0eHx',
        signature: '0uLy',
        type: 'ECDSA_SECP256K1',
      },
    ],
    transaction_body: 'Kd6tvu8=',
    wait_for_expiry: true,
  };

  const testSpecs = [
    {
      description: 'input with all fields present',
      input: {...defaultInput},
      expected: {...defaultExpected},
    },
    {
      description: 'input with "key" and "executed_timestamp" nullified',
      input: {
        ...defaultInput,
        executed_timestamp: null,
        key: null,
      },
      expected: {
        ...defaultExpected,
        executed_timestamp: null,
        admin_key: null,
      },
    },
    {
      description: 'input with null signatures',
      input: {
        ...defaultInput,
        signatures: null,
      },
      expected: {
        ...defaultExpected,
        signatures: [],
      },
    },
    {
      description: 'input with undefined signatures',
      input: {
        ...defaultInput,
        signatures: undefined,
      },
      expected: {
        ...defaultExpected,
        signatures: [],
      },
    },
    {
      description: 'input with null signature object',
      input: {
        ...defaultInput,
        signatures: [{consensus_timestamp: null, public_key_prefix: null, signature: null, type: null}],
      },
      expected: {
        ...defaultExpected,
        signatures: [],
      },
    },
    {
      description: 'null deleted',
      input: {
        ...defaultInput,
        deleted: null,
      },
      expected: {
        ...defaultExpected,
        deleted: null,
      },
    },
    {
      description: 'deleted true',
      input: {
        ...defaultInput,
        deleted: true,
        executed_timestamp: null,
        signatures: [],
      },
      expected: {
        ...defaultExpected,
        deleted: true,
        executed_timestamp: null,
        signatures: [],
      },
    },
    {
      description: 'null memo',
      input: {
        ...defaultInput,
        memo: null,
      },
      expected: {
        ...defaultExpected,
        memo: null,
      },
    },
  ];

  testSpecs.forEach((testSpec) => {
    test(testSpec.description, () => {
      expect(schedules.formatScheduleRow(testSpec.input)).toStrictEqual(testSpec.expected);
    });
  });
});

const verifyExtractSqlFromScheduleFilters = (filters, expectedQuery, expectedParams, expectedOrder, expectedLimit) => {
  const {filterQuery, params, order, limit} = schedules.extractSqlFromScheduleFilters(filters);

  expect(filterQuery).toStrictEqual(expectedQuery);
  expect(params).toStrictEqual(expectedParams);
  expect(order).toStrictEqual(expectedOrder);
  expect(limit).toStrictEqual(expectedLimit);
};

describe('getScheduleCacheControlHeader', () => {
  const LONGER_SCHEDULE_CACHE_CONTROL_HEADER = {'cache-control': 'public, max-age=3600'};
  const testSpecs = [
    {
      name: 'Schedule has executed',
      input: {
        executed_timestamp: 1234567890000000002n,
        expiration_time: 1234567890000000000n,
        consensus_timestamp: 1234567890000000001n,
        deleted: false,
      },
      expected: LONGER_SCHEDULE_CACHE_CONTROL_HEADER,
    },
    {
      name: 'Schedule is deleted',
      input: {
        executed_timestamp: 1234567890000000002n,
        expiration_time: 1234567890000000000n,
        consensus_timestamp: 1234567890000000001n,
        deleted: true,
      },
      expected: LONGER_SCHEDULE_CACHE_CONTROL_HEADER,
    },
    {
      name: 'Schedule has auto-expired (no expiration_time, past 31 minutes since consensus_timestamp)',
      input: {
        executed_timestamp: 1234567890000000002n,
        expiration_time: 1234567890000000000n,
        consensus_timestamp: 1234567890000000001n,
        deleted: false,
      },
      expected: LONGER_SCHEDULE_CACHE_CONTROL_HEADER,
    },
    {
      name: 'Schedule has not auto-expired',
      input: {
        executed_timestamp: null,
        expiration_time: null,
        consensus_timestamp: utils.nowInNs() - constants.SIXTY_SECONDS * constants.NANOS_PER_SECOND - 1n,
        deleted: false,
      },
      expected: {},
    },
    {
      name: 'Schedule has expired (expiration_time set, past expiration_time + 60 seconds)',
      input: {
        executed_timestamp: 1234567890000000002n,
        expiration_time: 1234567890000000000n,
        consensus_timestamp: 1234567890000000001n,
        deleted: false,
      },
      expected: LONGER_SCHEDULE_CACHE_CONTROL_HEADER,
    },
    {
      name: 'Schedule has not expired',
      input: {
        executed_timestamp: null,
        expiration_time: utils.nowInNs() + constants.SIXTY_SECONDS * constants.NANOS_PER_SECOND,
        consensus_timestamp: 1234567890000000001n,
        deleted: false,
      },
      expected: {},
    },
    {
      name: 'Schedule has neither executed nor expired',
      input: {
        executed_timestamp: null,
        expiration_time: utils.nowInNs() + constants.SIXTY_SECONDS * constants.NANOS_PER_SECOND,
        consensus_timestamp: utils.nowInNs(),
        deleted: false,
      },
      expected: {},
    },
  ];
  testSpecs.forEach(({name, input, expected}) => {
    test(name, () => {
      expect(schedules.getScheduleCacheControlHeader(input)).toEqual(expected);
    });
  });
});

describe('schedule extractSqlFromScheduleFilters tests', () => {
  test('Verify simple discovery query /api/v1/schedules', () => {
    const filters = [];

    const expectedquery = '';
    const expectedparams = [defaultLimit];
    const expectedorder = constants.orderFilterValues.ASC;
    const expectedlimit = defaultLimit;

    verifyExtractSqlFromScheduleFilters(filters, expectedquery, expectedparams, expectedorder, expectedlimit);
  });

  test('Verify all filter params query /api/v1/schedules?account.id=gte:0.0.123&schedule.id=lt:456&order=desc&limit=10', () => {
    const accountEntityId = EntityId.parseString('123');
    const scheduleEntityId = EntityId.parseString('456');

    const filters = [
      utils.buildComparatorFilter(constants.filterKeys.ACCOUNT_ID, `gte:${accountEntityId.num}`),
      utils.buildComparatorFilter(constants.filterKeys.SCHEDULE_ID, `lt:${scheduleEntityId.num}`),
      utils.buildComparatorFilter(constants.filterKeys.ORDER, 'desc'),
      utils.buildComparatorFilter(constants.filterKeys.LIMIT, '10'),
    ];

    for (const filter of filters) {
      utils.formatComparator(filter);
    }

    const expectedquery = 'where creator_account_id >= $1 and s.schedule_id < $2';
    const expectedparams = [accountEntityId.getEncodedId(), scheduleEntityId.getEncodedId(), 10];
    const expectedorder = constants.orderFilterValues.DESC;
    const expectedlimit = 10;

    verifyExtractSqlFromScheduleFilters(filters, expectedquery, expectedparams, expectedorder, expectedlimit);
  });
});

describe('mergeScheduleEntities', () => {
  let schedulesInput;
  let entities;
  let signatures;
  let expected;

  beforeEach(() => {
    schedulesInput = [
      {
        consensus_timestamp: 1000,
        creator_account_id: 5000,
        executed_timestamp: 1300,
        expiration_time: 2000,
        payer_account_id: 5000,
        schedule_id: 3000,
        transaction_body: Buffer.from('0x010203040506', 'hex'),
        wait_for_expiry: false,
      },
      {
        consensus_timestamp: 1100,
        creator_account_id: 5001,
        executed_timestamp: 1400,
        expiration_time: 2100,
        payer_account_id: 5001,
        schedule_id: 3001,
        transaction_body: Buffer.from('0x020304050607', 'hex'),
        wait_for_expiry: false,
      },
      {
        consensus_timestamp: 1200,
        creator_account_id: 5002,
        executed_timestamp: 1500,
        expiration_time: 2200,
        payer_account_id: 5002,
        schedule_id: 9007199254740992n,
        transaction_body: Buffer.from('0x030405060708', 'hex'),
        wait_for_expiry: false,
      },
    ];
    entities = [
      {
        deleted: true,
        id: 3000,
        key: Buffer.from('010101', 'hex'),
        memo: 'schedule 5000',
      },
      {
        deleted: false,
        id: 3001,
        key: Buffer.from('010101', 'hex'),
        memo: 'schedule 5000',
      },
      {
        deleted: true,
        id: 9007199254740992n,
        key: Buffer.from('010101', 'hex'),
        memo: 'schedule 5000',
      },
    ];
    signatures = [
      {
        entity_id: 3000,
        signatures: [
          {
            consensus_timestamp: 1290,
            public_key_prefix: Buffer.from('prefix1', 'utf8').toString('base64'),
            signature: Buffer.from('signature1', 'utf8').toString('base64'),
            type: 10,
          },
        ],
      },
      {
        entity_id: 3001,
        signatures: [
          {
            consensus_timestamp: 1390,
            public_key_prefix: Buffer.from('prefix2', 'utf8').toString('base64'),
            signature: Buffer.from('signature2', 'utf8').toString('base64'),
            type: 10,
          },
        ],
      },
      {
        entity_id: 9007199254740992n,
        signatures: [
          {
            consensus_timestamp: 1490,
            public_key_prefix: Buffer.from('prefix3', 'utf8').toString('base64'),
            signature: Buffer.from('signature3', 'utf8').toString('base64'),
            type: 10,
          },
        ],
      },
    ];
    expected = schedulesInput
      .map((schedule, index) => ({...schedule, ...entities[index], ...signatures[index]}))
      .map((schedule) => _.omit(schedule, ['entity_id', 'id']));
  });

  test('default', () => {
    expect(schedules.mergeScheduleEntities(schedulesInput, entities, signatures)).toStrictEqual(expected);
  });

  test('empty', () => {
    expect(schedules.mergeScheduleEntities([], [], [])).toStrictEqual([]);
  });

  test('no entities', () => {
    expected = expected.map((schedule) => _.omit(schedule, ['deleted', 'key', 'memo']));
    expect(schedules.mergeScheduleEntities(schedulesInput, [], signatures)).toStrictEqual(expected);
  });

  test('no signatures', () => {
    expected = expected.map((schedule) => _.omit(schedule, ['signatures']));
    expect(schedules.mergeScheduleEntities(schedulesInput, entities, [])).toStrictEqual(expected);
  });

  test('some entities / signatures missing', () => {
    entities = [entities[0], entities[1]];
    signatures = [signatures[1]];
    expected = [
      _.omit(expected[0], ['signatures']),
      expected[1],
      _.omit(expected[2], ['deleted', 'key', 'memo', 'signatures']),
    ];
    expect(schedules.mergeScheduleEntities(schedulesInput, entities, signatures)).toStrictEqual(expected);
  });
});
