// SPDX-License-Identifier: Apache-2.0

import {jest} from '@jest/globals';
import httpContext from 'express-http-context';
import sinon from 'sinon';

import config, {getMirrorConfig} from '../config';
import * as constants from '../constants';
import EntityId from '../entityId';
import * as utils from '../utils';

const {
  common: {realm, shard},
} = getMirrorConfig();

describe('utils buildAndValidateFilters test', () => {
  const defaultMaxRepeatedQueryParameters = config.maxRepeatedQueryParameters;

  afterEach(() => {
    config.maxRepeatedQueryParameters = defaultMaxRepeatedQueryParameters;
  });

  const accountEntityId = EntityId.parseString('6560');
  const query = {
    [constants.filterKeys.ACCOUNT_ID]: `${accountEntityId.num}`,
    [constants.filterKeys.LIMIT]: ['80', '1560'],
    [constants.filterKeys.TIMESTAMP]: '12345.001',
    [constants.filterKeys.TOPIC0]: [
      '0x92fca5e4d85f0880053c1eb3853951369b8a96d61ea5b5abccfac7f043686ed2',
      '0xda463731fd25a0eeaeb1aead27aacf5a8ff456c1c8ad32ff88d678aff3e11455',
    ],
  };

  test('validator passes', () => {
    const fakeValidator = sinon.fake.returns(true);
    const expected = [
      {
        key: constants.filterKeys.ACCOUNT_ID,
        operator: utils.opsMap.eq,
        value: accountEntityId.getEncodedId(),
      },
      {
        key: constants.filterKeys.LIMIT,
        operator: utils.opsMap.eq,
        value: 80,
      },
      {
        key: constants.filterKeys.LIMIT,
        operator: utils.opsMap.eq,
        value: 100,
      },
      {
        key: constants.filterKeys.TIMESTAMP,
        operator: utils.opsMap.eq,
        value: '12345001000000',
      },
      {
        key: constants.filterKeys.TOPIC0,
        operator: utils.opsMap.eq,
        value: '0x92fca5e4d85f0880053c1eb3853951369b8a96d61ea5b5abccfac7f043686ed2',
      },
      {
        key: constants.filterKeys.TOPIC0,
        operator: utils.opsMap.eq,
        value: '0xda463731fd25a0eeaeb1aead27aacf5a8ff456c1c8ad32ff88d678aff3e11455',
      },
    ];

    expect(utils.buildAndValidateFilters(query, acceptedParameters, fakeValidator)).toStrictEqual(expected);
    expect(fakeValidator.callCount).toEqual(6);
  });

  test('validator fails', () => {
    const fakeValidator = sinon.fake.returns(false);

    expect(() =>
      utils.buildAndValidateFilters(query, acceptedParameters, fakeValidator)
    ).toThrowErrorMatchingSnapshot();
    expect(fakeValidator.callCount).toEqual(6);
  });

  test('exceeded max number of repeated parameters', () => {
    config.query.maxRepeatedQueryParameters = 4;
    const fakeValidator = sinon.fake.returns(true);
    const repeatedParamQuery = {
      [constants.filterKeys.ACCOUNT_ID]: ['6560', '6561'],
      [constants.filterKeys.LIMIT]: ['80', '1560', '90', '10', '11'],
      [constants.filterKeys.TIMESTAMP]: '12345.001',
    };

    expect(() =>
      utils.buildAndValidateFilters(repeatedParamQuery, acceptedParameters, fakeValidator)
    ).toThrowErrorMatchingSnapshot();
    expect(fakeValidator.callCount).toEqual(3);
  });
});

describe('utils buildComparatorFilter tests', () => {
  test('Verify buildComparatorFilter for scheduled=true', () => {
    verifyBuildComparatorFilter(constants.filterKeys.SCHEDULED, 'true', {
      key: constants.filterKeys.SCHEDULED,
      operator: 'eq',
      value: 'true',
    });
  });

  test('Verify buildComparatorFilter for scheduled=false', () => {
    verifyBuildComparatorFilter(constants.filterKeys.SCHEDULED, 'false', {
      key: constants.filterKeys.SCHEDULED,
      operator: 'eq',
      value: 'false',
    });
  });

  test('Verify buildComparatorFilter for sequencenumber=lt:2', () => {
    verifyBuildComparatorFilter(constants.filterKeys.SEQUENCE_NUMBER, 'lt:2', {
      key: constants.filterKeys.SEQUENCE_NUMBER,
      operator: 'lt',
      value: '2',
    });
  });

  test('Verify buildComparatorFilter for sequencenumber=lte:3', () => {
    verifyBuildComparatorFilter(constants.filterKeys.SEQUENCE_NUMBER, 'lte:3', {
      key: constants.filterKeys.SEQUENCE_NUMBER,
      operator: 'lte',
      value: '3',
    });
  });

  test('Verify buildComparatorFilter for sequencenumber=4', () => {
    verifyBuildComparatorFilter(constants.filterKeys.SEQUENCE_NUMBER, '4', {
      key: constants.filterKeys.SEQUENCE_NUMBER,
      operator: 'eq',
      value: '4',
    });
  });

  test('Verify buildComparatorFilter for timestamp=gte:1234567890.000000004', () => {
    verifyBuildComparatorFilter(constants.filterKeys.TIMESTAMP, 'gte:1234567890.000000005', {
      key: constants.filterKeys.TIMESTAMP,
      operator: 'gte',
      value: '1234567890.000000005',
    });
  });

  test('Verify buildComparatorFilter for timestamp=gt:6', () => {
    verifyBuildComparatorFilter(constants.filterKeys.TIMESTAMP, 'gt:1234567890.000000006', {
      key: constants.filterKeys.TIMESTAMP,
      operator: 'gt',
      value: '1234567890.000000006',
    });
  });
});

const verifyBuildComparatorFilter = (key, val, expectedFilter) => {
  const filter = utils.buildComparatorFilter(key, val);

  verifyFilter(filter, expectedFilter.key, expectedFilter.operator, expectedFilter.value);
};

const verifyFilter = (filter, key, op, val) => {
  expect(filter.key).toStrictEqual(key);
  expect(filter.operator).toStrictEqual(op);
  expect(filter.value).toStrictEqual(val);
};

describe('utils buildFilters tests', () => {
  test('Verify buildFilters for /api/v1/topic/7/messages?sequencenumber=2', () => {
    const filters = {
      sequencenumber: '2',
    };

    const {badParams, filters: formattedFilters} = utils.buildFilters(filters);

    expect(badParams).toBeEmpty();
    expect(formattedFilters).toHaveLength(1);
    verifyFilter(formattedFilters[0], constants.filterKeys.SEQUENCE_NUMBER, 'eq', '2');
  });

  test('Verify buildFilters for /api/v1/topic/7/messages?timestamp=1234567890.000000004', () => {
    const filters = {
      timestamp: '1234567890.000000004',
    };

    const {badParams, filters: formattedFilters} = utils.buildFilters(filters);

    expect(badParams).toBeEmpty();
    expect(formattedFilters).toHaveLength(1);
    verifyFilter(formattedFilters[0], constants.filterKeys.TIMESTAMP, 'eq', '1234567890.000000004');
  });

  test('Verify buildFilters for /api/v1/topic/7/messages?sequencenumber=lt:2&sequencenumber=gte:3&timestamp=1234567890.000000004&order=desc&limit=5', () => {
    const filters = {
      sequencenumber: ['lt:2', 'gte:3'],
      timestamp: '1234567890.000000004',
      limit: '5',
      order: 'desc',
    };

    const {badParams, filters: formattedFilters} = utils.buildFilters(filters);

    expect(badParams).toBeEmpty();
    expect(formattedFilters).toHaveLength(5);
    verifyFilter(formattedFilters[0], constants.filterKeys.SEQUENCE_NUMBER, 'lt', '2');
    verifyFilter(formattedFilters[1], constants.filterKeys.SEQUENCE_NUMBER, 'gte', '3');
    verifyFilter(formattedFilters[2], constants.filterKeys.TIMESTAMP, 'eq', '1234567890.000000004');
    verifyFilter(formattedFilters[3], constants.filterKeys.LIMIT, 'eq', '5');
    verifyFilter(formattedFilters[4], constants.filterKeys.ORDER, 'eq', 'desc');
  });

  test('Verify buildFilters for /api/v1/transactions/0.0.3-1234567890-000000123/stateproof?scheduled=true', () => {
    const filters = {
      scheduled: 'true',
    };

    const {badParams, filters: formattedFilters} = utils.buildFilters(filters);

    expect(badParams).toBeEmpty();
    expect(formattedFilters).toHaveLength(1);
    verifyFilter(formattedFilters[0], constants.filterKeys.SCHEDULED, 'eq', 'true');
  });

  test('Verify buildFilters for /api/v1/transactions/0.0.3-1234567890-000000123/stateproof?scheduled=true&scheduled=false', () => {
    const filters = {
      scheduled: ['true', 'false'],
    };

    const {badParams, filters: formattedFilters} = utils.buildFilters(filters);

    expect(badParams).toBeEmpty();
    expect(formattedFilters).toHaveLength(2);
    verifyFilter(formattedFilters[0], constants.filterKeys.SCHEDULED, 'eq', 'true');
    verifyFilter(formattedFilters[1], constants.filterKeys.SCHEDULED, 'eq', 'false');
  });

  test('Verify buildFilters for /api/v1/schedules?account.id=0.0.1024&schedule.id=gte:4000&order=desc&limit=10', () => {
    const filters = {
      'account.id': 'lt:0.0.1024',
      'schedule.id': 'gte:4000',
      order: 'desc',
      limit: '10',
    };

    const {badParams, filters: formattedFilters} = utils.buildFilters(filters);

    expect(badParams).toBeEmpty();
    expect(formattedFilters).toHaveLength(4);
    verifyFilter(formattedFilters[0], constants.filterKeys.ACCOUNT_ID, 'lt', '0.0.1024');
    verifyFilter(formattedFilters[1], constants.filterKeys.SCHEDULE_ID, 'gte', '4000');
    verifyFilter(formattedFilters[2], constants.filterKeys.ORDER, 'eq', 'desc');
    verifyFilter(formattedFilters[3], constants.filterKeys.LIMIT, 'eq', '10');
  });

  test('Verify buildFilters for /api/v1/contracts/results/logs?timestamp=gt:1651061427&timestamp=lt:1651061600&topic0=0x92fca5e4d85f0880053c1eb3853951369b8a96d61ea5b5abccfac7f043686ed2&topic0=0xda463731fd25a0eeaeb1aead27aacf5a8ff456c1c8ad32ff88d678aff3e11455', () => {
    const filters = {
      topic0: [
        '0x92fca5e4d85f0880053c1eb3853951369b8a96d61ea5b5abccfac7f043686ed2',
        '0xda463731fd25a0eeaeb1aead27aacf5a8ff456c1c8ad32ff88d678aff3e11455',
      ],
      timestamp: ['gt:1651061427', 'lt:1651061600'],
    };

    const {badParams, filters: formattedFilters} = utils.buildFilters(filters);

    expect(badParams).toBeEmpty();
    expect(formattedFilters).toHaveLength(4);
    verifyFilter(
      formattedFilters[0],
      constants.filterKeys.TOPIC0,
      'eq',
      '0x92fca5e4d85f0880053c1eb3853951369b8a96d61ea5b5abccfac7f043686ed2'
    );
    verifyFilter(
      formattedFilters[1],
      constants.filterKeys.TOPIC0,
      'eq',
      '0xda463731fd25a0eeaeb1aead27aacf5a8ff456c1c8ad32ff88d678aff3e11455'
    );
    verifyFilter(formattedFilters[2], constants.filterKeys.TIMESTAMP, 'gt', '1651061427');
    verifyFilter(formattedFilters[3], constants.filterKeys.TIMESTAMP, 'lt', '1651061600');
  });
});

describe('utils formatComparator tests', () => {
  test('Verify formatComparator for sequencenumber=lt:2', () => {
    const filter = utils.buildComparatorFilter(constants.filterKeys.SEQUENCE_NUMBER, 'lt:2');
    utils.formatComparator(filter);
    verifyFilter(filter, constants.filterKeys.SEQUENCE_NUMBER, ' < ', '2');
  });

  test('Verify formatComparator for timestamp=lte:1234567890.000000004', () => {
    const filter = utils.buildComparatorFilter(constants.filterKeys.TIMESTAMP, 'lte:1234567890.000000004');
    utils.formatComparator(filter);
    verifyFilter(filter, constants.filterKeys.TIMESTAMP, ' <= ', '1234567890000000004');
  });

  test('Verify formatComparator for account.id=5', () => {
    const accountEntityId = EntityId.parseString('5');
    const filter = utils.buildComparatorFilter(constants.filterKeys.ACCOUNT_ID, `${accountEntityId.num}`);
    utils.formatComparator(filter);
    verifyFilter(filter, constants.filterKeys.ACCOUNT_ID, ' = ', accountEntityId.getEncodedId());
  });

  test('Verify formatComparator for account.id=0.2.5', () => {
    const entityIdStr = '0.2.5';
    const entityId = EntityId.parse(entityIdStr);
    const filter = utils.buildComparatorFilter(constants.filterKeys.ACCOUNT_ID, entityIdStr);
    utils.formatComparator(filter);
    verifyFilter(filter, constants.filterKeys.ACCOUNT_ID, ' = ', entityId.getEncodedId());
  });

  test('Verify formatComparator for timestamp=1234567890.000000004', () => {
    const filter = utils.buildComparatorFilter(constants.filterKeys.TIMESTAMP, '1234567890.000000005');
    utils.formatComparator(filter);
    verifyFilter(filter, constants.filterKeys.TIMESTAMP, ' = ', '1234567890000000005');
  });

  test('Verify formatComparator for account.id=gte:6', () => {
    const accountEntityId = EntityId.parseString('6');
    const filter = utils.buildComparatorFilter(constants.filterKeys.ACCOUNT_ID, `gte:${accountEntityId.num}`);
    utils.formatComparator(filter);
    verifyFilter(filter, constants.filterKeys.ACCOUNT_ID, ' >= ', accountEntityId.getEncodedId());
  });

  test('Verify formatComparator for timestamp=gt:1234567890.000000004', () => {
    const filter = utils.buildComparatorFilter(constants.filterKeys.TIMESTAMP, 'gt:1234567890.000000007');
    utils.formatComparator(filter);
    verifyFilter(filter, constants.filterKeys.TIMESTAMP, ' > ', '1234567890000000007');
  });

  test('Verify formatComparator for scheduled=true', () => {
    const filter = utils.buildComparatorFilter(constants.filterKeys.SCHEDULED, 'true');
    utils.formatComparator(filter);
    verifyFilter(filter, constants.filterKeys.SCHEDULED, ' = ', true);
  });

  test('Verify formatComparator for scheduled=false', () => {
    const filter = utils.buildComparatorFilter(constants.filterKeys.SCHEDULED, 'false');
    utils.formatComparator(filter);
    verifyFilter(filter, constants.filterKeys.SCHEDULED, ' = ', false);
  });

  test('Verify formatComparator for limit=10', () => {
    const filter = utils.buildComparatorFilter(constants.filterKeys.LIMIT, '10');
    utils.formatComparator(filter);
    verifyFilter(filter, constants.filterKeys.LIMIT, ' = ', 10);
  });

  test('Verify formatComparator caps limit at default max', () => {
    jest.spyOn(httpContext, 'get').mockReturnValue(undefined);
    const filter = utils.buildComparatorFilter(constants.filterKeys.LIMIT, '150');
    utils.formatComparator(filter);
    verifyFilter(filter, constants.filterKeys.LIMIT, ' = ', 100);
    jest.restoreAllMocks();
  });

  test('Verify formatComparator caps limit at custom max for authenticated user', () => {
    jest.spyOn(httpContext, 'get').mockReturnValue(200);
    const filter = utils.buildComparatorFilter(constants.filterKeys.LIMIT, '300');
    utils.formatComparator(filter);
    verifyFilter(filter, constants.filterKeys.LIMIT, ' = ', 200);
    jest.restoreAllMocks();
  });
});

describe('utils filterDependencyCheck tests', () => {
  test('Verify formatComparator for isolated transaction.index', () => {
    const query = {};
    query[constants.filterKeys.TRANSACTION_INDEX] = 'eq:1';
    try {
      utils.filterDependencyCheck(query);
      expect(true).toEqual('Should throw error');
    } catch (err) {
      expect(err.toString()).toEqual(
        'Error: Invalid parameter usage: transaction.index - transaction.index requires block.number or block.hash filter to be specified'
      );
    }
  });

  test('Verify formatComparator for transaction.index with block.number', () => {
    const query = {};
    query[constants.filterKeys.TRANSACTION_INDEX] = 'eq:1';
    query[constants.filterKeys.BLOCK_NUMBER] = 'eq:1';
    try {
      utils.filterDependencyCheck(query);
    } catch (err) {
      expect(err).toBeUndefined();
    }
  });

  test('Verify formatComparator for transaction.index with block.hash', () => {
    const query = {};
    query[constants.filterKeys.TRANSACTION_INDEX] = 'eq:1';
    query[constants.filterKeys.BLOCK_HASH] = 'eq:1';
    try {
      utils.filterDependencyCheck(query);
    } catch (err) {
      expect(err).toBeUndefined();
    }
  });
});

const verifyInvalidFilters = (filters) => {
  const expectedInvalidParams = filters.map((filter) => filter.key);
  const expected = {invalidParams: expectedInvalidParams, unknownParams: []};
  expect(utils.validateAndParseFilters(filters, utils.filterValidityChecks, acceptedParameters)).toStrictEqual(
    expected
  );
};

const validateAndParseFiltersNoExMessage = 'Verify validateAndParseFilters for valid filters does not throw exception';
const verifyValidAndInvalidFilters = (invalidFilters, validFilters) => {
  invalidFilters.forEach((filter) => {
    const filterString = Array.isArray(filter)
      ? `${JSON.stringify(filter[0])} ${filter.length} times`
      : `${JSON.stringify(filter)}`;
    test(`Verify validateAndParseFilters for invalid ${filterString}`, () => {
      verifyInvalidFilters([filter]);
    });
  });

  validFilters.forEach((filter) => {
    test(`${validateAndParseFiltersNoExMessage} for ${JSON.stringify(filter)}`, () => {
      expect(utils.validateAndParseFilters([filter], utils.filterValidityChecks, acceptedParameters)).toEqual({
        invalidParams: [],
        unknownParams: [],
      });
    });
  });
};

describe('utils validateAndParseFilters boolean key tests', () => {
  const booleanFilterKeys = [constants.filterKeys.SCHEDULED];
  booleanFilterKeys.forEach((key) => {
    const invalidFilters = [
      // erroneous data
      utils.buildComparatorFilter(key, 'lt:true'),
      utils.buildComparatorFilter(key, 'lte:true'),
      utils.buildComparatorFilter(key, 'ne:true'),
      utils.buildComparatorFilter(key, 'gte:true'),
      utils.buildComparatorFilter(key, 'gt:true'),
      // invalid format
      utils.buildComparatorFilter(key, 'invalid'),
    ];

    const filters = [
      utils.buildComparatorFilter(key, 'true'),
      utils.buildComparatorFilter(key, 'false'),
      utils.buildComparatorFilter(key, 'True'),
      utils.buildComparatorFilter(key, 'False'),
    ];

    verifyValidAndInvalidFilters(invalidFilters, filters);
  });
});

describe('utils validateAndParseFilters timestamp key tests', () => {
  const key = constants.filterKeys.TIMESTAMP;
  const invalidFilters = [
    // erroneous data
    utils.buildComparatorFilter(key, 'lte:today'),
    // invalid format
    utils.buildComparatorFilter(key, 'lte:23456789012345678901234'),
  ];

  const filters = [
    utils.buildComparatorFilter(key, '1234567890.000000003'),
    utils.buildComparatorFilter(key, 'eq:1234567890.000000003'),
    utils.buildComparatorFilter(key, 'gt:1234567890.000000003'),
    utils.buildComparatorFilter(key, 'gte:1234567890.000000003'),
    utils.buildComparatorFilter(key, 'lt:1234567890.000000003'),
    utils.buildComparatorFilter(key, 'lte:1234567890.000000003'),
  ];

  verifyValidAndInvalidFilters(invalidFilters, filters);
});

describe('utils validateAndParseFilters order key tests', () => {
  const key = constants.filterKeys.ORDER;
  const invalidFilters = [
    // erroneous data
    utils.buildComparatorFilter(key, 'chronological'),
  ];

  const filters = [utils.buildComparatorFilter(key, 'asc'), utils.buildComparatorFilter(key, 'desc')];

  verifyValidAndInvalidFilters(invalidFilters, filters);
});

describe('utils validateAndParseFilters limit key tests', () => {
  const key = constants.filterKeys.ORDER;
  const invalidFilters = [
    // erroneous data
    utils.buildComparatorFilter(key, '-1'),
    // invalid format
    utils.buildComparatorFilter(key, '2.3'),
  ];

  const filters = [
    utils.buildComparatorFilter(key, 'asc'),
    utils.buildComparatorFilter(key, 'desc'),
    utils.buildComparatorFilter(key, 'ASC'),
    utils.buildComparatorFilter(key, 'DESC'),
  ];

  verifyValidAndInvalidFilters(invalidFilters, filters);
});

describe('utils validateAndParseFilters entity key tests', () => {
  const booleanFilterKeys = [
    constants.filterKeys.ACCOUNT_ID,
    constants.filterKeys.TOKEN_ID,
    constants.filterKeys.SCHEDULE_ID,
    constants.filterKeys.FROM,
  ];
  booleanFilterKeys.forEach((key) => {
    const invalidFilters = [
      // erroneous data
      utils.buildComparatorFilter(key, 'lt:-92233720368547758080'),
      // invalid format
      utils.buildComparatorFilter(key, 'lt:0.1.23456789012345'),
    ];

    const filters = [
      utils.buildComparatorFilter(key, '123'),
      utils.buildComparatorFilter(key, '1.2.3'),
      utils.buildComparatorFilter(key, '0.0.2000'),
      utils.buildComparatorFilter(key, 'eq:1234567890'),
      utils.buildComparatorFilter(key, 'gt:1234567890'),
      utils.buildComparatorFilter(key, 'gte:1234567890'),
      utils.buildComparatorFilter(key, 'lt:1234567890'),
      utils.buildComparatorFilter(key, 'lte:1234567890'),
    ];

    verifyValidAndInvalidFilters(invalidFilters, filters);
  });
});

describe('utils validateAndParseFilters integer key tests', () => {
  const integerFilterKeys = [constants.filterKeys.SEQUENCE_NUMBER];
  integerFilterKeys.forEach((key) => {
    const invalidFilters = [
      // erroneous data
      utils.buildComparatorFilter(key, 'test'),
      // invalid format
      utils.buildComparatorFilter(key, '0.45678901234'),
      utils.buildComparatorFilter(key, '<=:2'),
    ];

    const filters = [
      utils.buildComparatorFilter(key, '1'),
      utils.buildComparatorFilter(key, '9007199254740991'), // MAX_SAFE_INTEGER
    ];

    verifyValidAndInvalidFilters(invalidFilters, filters);
  });
});

describe('utils validateAndParseFilters credit type key tests', () => {
  const key = constants.filterKeys.CREDIT_TYPE;
  const invalidFilters = [
    // erroneous data
    utils.buildComparatorFilter(key, '-1'),
    // invalid format
    utils.buildComparatorFilter(key, 'cred'),
    utils.buildComparatorFilter(key, 'deb'),
  ];

  const filters = [
    utils.buildComparatorFilter(key, 'credit'),
    utils.buildComparatorFilter(key, 'debit'),
    utils.buildComparatorFilter(key, 'CREDIT'),
    utils.buildComparatorFilter(key, 'DEBIT'),
  ];

  verifyValidAndInvalidFilters(invalidFilters, filters);
});

describe('utils validateAndParseFilters result type key tests', () => {
  const key = constants.filterKeys.RESULT;
  const invalidFilters = [
    // erroneous data
    utils.buildComparatorFilter(key, '-1'),
    // invalid format
    utils.buildComparatorFilter(key, 'y'),
    utils.buildComparatorFilter(key, 'n'),
  ];

  const filters = [
    utils.buildComparatorFilter(key, 'success'),
    utils.buildComparatorFilter(key, 'fail'),
    utils.buildComparatorFilter(key, 'SUCCESS'),
    utils.buildComparatorFilter(key, 'FAIL'),
  ];

  verifyValidAndInvalidFilters(invalidFilters, filters);
});

describe('utils validateAndParseFilters account balance key tests', () => {
  const key = constants.filterKeys.ACCOUNT_BALANCE;
  const invalidFilters = [
    // erroneous data
    utils.buildComparatorFilter(key, '-1'),
    // invalid format
    utils.buildComparatorFilter(key, 'y'),
    utils.buildComparatorFilter(key, '23456789012345678901234'),
  ];

  const filters = [
    utils.buildComparatorFilter(key, '0'),
    utils.buildComparatorFilter(key, '1000000000'),
    utils.buildComparatorFilter(key, '9223372036854775807'),
  ];

  verifyValidAndInvalidFilters(invalidFilters, filters);
});

describe('utils validateAndParseFilters encoding key tests', () => {
  const key = constants.filterKeys.ENCODING;
  const invalidFilters = [
    // erroneous data
    utils.buildComparatorFilter(key, 'encrypt'),
    // invalid format
    utils.buildComparatorFilter(key, 'utf'),
    utils.buildComparatorFilter(key, 'b64'),
  ];

  const filters = [utils.buildComparatorFilter(key, 'utf-8'), utils.buildComparatorFilter(key, 'base64')];

  verifyValidAndInvalidFilters(invalidFilters, filters);
});

describe('utils validateAndParseFilters crypto key tests', () => {
  const booleanFilterKeys = [constants.filterKeys.ACCOUNT_PUBLICKEY, constants.filterKeys.ENTITY_PUBLICKEY];
  booleanFilterKeys.forEach((key) => {
    const invalidFilters = [
      // erroneous data
      utils.buildComparatorFilter(key, 'key'),
      // invalid format
      utils.buildComparatorFilter(key, '3c3d546321ff6f63d701d2ec5c2'),
    ];

    const filters = [
      utils.buildComparatorFilter(key, '3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be'),
    ];

    verifyValidAndInvalidFilters(invalidFilters, filters);
  });
});

describe('utils validateAndParseFilters FROM key tests', () => {
  const key = constants.filterKeys.FROM;
  const invalidFilters = [
    // erroneous data
    utils.buildComparatorFilter(key, '-92233720368547758080'),
    // invalid format
    utils.buildComparatorFilter(key, '0x'),
    utils.buildComparatorFilter(key, '0x00000000000000000000000000000000000000012'),
  ];

  const filters = [
    utils.buildComparatorFilter(key, '0x0000000000000000000000000000000000000001'),
    utils.buildComparatorFilter(key, '0x0000000100000000000000020000000000000003'),
  ];

  verifyValidAndInvalidFilters(invalidFilters, filters);
});

describe('utils validateAndParseFilters nonce key tests', () => {
  const key = constants.filterKeys.NONCE;
  const invalidFilters = [
    // erroneous data
    utils.buildComparatorFilter(key, '-1'),
    utils.buildComparatorFilter(key, '2147483648'),
    // invalid format
    utils.buildComparatorFilter(key, 'x'),
    // invalid op
    utils.buildComparatorFilter(key, 'ge:0'),
    utils.buildComparatorFilter(key, 'gte:0'),
    utils.buildComparatorFilter(key, 'le:0'),
    utils.buildComparatorFilter(key, 'lte:0'),
  ];

  const filters = [
    utils.buildComparatorFilter(key, '0'),
    utils.buildComparatorFilter(key, '2147483647'),
    utils.buildComparatorFilter(key, 'eq:0'),
  ];

  verifyValidAndInvalidFilters(invalidFilters, filters);
});

describe('utils validateAndParseFilters address book file id tests', () => {
  const key = constants.filterKeys.FILE_ID;
  const invalidFilters = [
    // erroneous data
    utils.buildComparatorFilter(key, 'lt:-1'),
    utils.buildComparatorFilter(key, '123'),
    utils.buildComparatorFilter(key, '101102'),
    utils.buildComparatorFilter(key, '1.2.3'),
    utils.buildComparatorFilter(key, '0.0.2000'),
    utils.buildComparatorFilter(key, 'eq:1234567890'),
    utils.buildComparatorFilter(key, 'gt:100'),
    utils.buildComparatorFilter(key, 'gte:101'),
    utils.buildComparatorFilter(key, 'lt:103'),
    utils.buildComparatorFilter(key, 'lte:102'),
    // invalid format
    utils.buildComparatorFilter(key, 'lt:0.1.23456789012345'),
  ];

  const filters = [
    utils.buildComparatorFilter(key, '101'),
    utils.buildComparatorFilter(key, `${realm}.101`),
    utils.buildComparatorFilter(key, `${shard}.${realm}.101`),
    utils.buildComparatorFilter(key, '102'),
    utils.buildComparatorFilter(key, `${realm}.102`),
    utils.buildComparatorFilter(key, `${shard}.${realm}.102`),
    utils.buildComparatorFilter(key, 'eq:101'),
    utils.buildComparatorFilter(key, 'eq:102'),
  ];

  verifyValidAndInvalidFilters(invalidFilters, filters);
});

describe('validateAndParseFilters slot', () => {
  const key = constants.filterKeys.SLOT;
  const invalidFilters = [
    utils.buildComparatorFilter(key, 'g'),
    utils.buildComparatorFilter(key, '00000000000000000000000000000000000000000000000000000000000000011'),
    utils.buildComparatorFilter(key, '0x00000000000000000000000000000000000000000000000000000000000000011'),
    utils.buildComparatorFilter(key, 'ne:1'),
  ];

  const filters = [
    utils.buildComparatorFilter(key, '1'),
    utils.buildComparatorFilter(key, 'a'),
    utils.buildComparatorFilter(key, 'A'),
    utils.buildComparatorFilter(key, '0000000000000000000000000000000000000000000000000000000000000001'),
    utils.buildComparatorFilter(key, '0x0000000000000000000000000000000000000000000000000000000000000001'),
    utils.buildComparatorFilter(key, 'eq:1'),
    utils.buildComparatorFilter(key, 'gt:1'),
    utils.buildComparatorFilter(key, 'gte:1'),
    utils.buildComparatorFilter(key, 'lt:1'),
    utils.buildComparatorFilter(key, 'lte:1'),
  ];

  verifyValidAndInvalidFilters(invalidFilters, filters);
});

describe('invalid parameters', () => {
  test('Verify invalid parameter validateAndParseFilters', () => {
    const filter = utils.buildComparatorFilter(constants.filterKeys.SCHEDULED, 'true');
    const expected = {invalidParams: [], unknownParams: [{code: 'unknownParamUsage', key: 'scheduled'}]};
    expect(utils.validateAndParseFilters([filter], utils.filterValidityChecks, new Set())).toEqual(expected);
  });

  test('Verify invalid parameter buildAndValidateFilters', () => {
    const query = {[constants.filterKeys.ACCOUNT_ID]: '6560'};
    const fakeValidator = sinon.fake.returns(true);
    expect(() => utils.buildAndValidateFilters(query, new Set(), fakeValidator)).toThrow(
      'Unknown query parameter: account.id'
    );
  });
});

const acceptedParameters = new Set([
  constants.filterKeys.ACCOUNT_ID,
  constants.filterKeys.ACCOUNT_BALANCE,
  constants.filterKeys.ACCOUNT_PUBLICKEY,
  constants.filterKeys.ENCODING,
  constants.filterKeys.ENTITY_PUBLICKEY,
  constants.filterKeys.FILE_ID,
  constants.filterKeys.FROM,
  constants.filterKeys.LIMIT,
  constants.filterKeys.NODE_ID,
  constants.filterKeys.NONCE,
  constants.filterKeys.ORDER,
  constants.filterKeys.RESULT,
  constants.filterKeys.SEQUENCE_NUMBER,
  constants.filterKeys.SCHEDULED,
  constants.filterKeys.SCHEDULE_ID,
  constants.filterKeys.SLOT,
  constants.filterKeys.TIMESTAMP,
  constants.filterKeys.TOKEN_ID,
  constants.filterKeys.TOPIC0,
  constants.filterKeys.TOKEN_TYPE,
]);
