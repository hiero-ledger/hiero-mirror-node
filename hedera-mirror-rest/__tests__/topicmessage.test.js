// SPDX-License-Identifier: Apache-2.0

import * as constants from '../constants';
import {assertSqlQueryEqual} from './testutils';
import topicMessage from '../topicmessage';
import * as utils from '../utils';

const {LIMIT, ORDER, SEQUENCE_NUMBER, TIMESTAMP} = constants.filterKeys;
const {eq, gt, gte, lt, lte, ne} = utils.opsMap;

describe('topicmessage validateConsensusTimestampParam tests', () => {
  test('Verify validateConsensusTimestampParam throws error for -1234567890.000000001', () => {
    verifyInvalidConsensusTimestamp(-1234567890.000000001);
  });

  test('Verify validateConsensusTimestampParam throws error for abc', () => {
    verifyInvalidConsensusTimestamp('abc');
  });

  test('Verify validateConsensusTimestampParam does not throw error for 1234567890', () => {
    verifyValidConsensusTimestamp(1234567890);
  });

  test('Verify validateConsensusTimestampParam does not throw error for 123.0001', () => {
    verifyValidConsensusTimestamp(123.0001);
  });
});

describe('topicmessage validateGetSequenceMessageParams tests', () => {
  test('Verify validateGetSequenceMessageParams throws error for -123', () => {
    verifyInvalidTopicAndSequenceNum(-92233720368547758080n, -123);
  });

  test('Verify validateGetSequenceMessageParams throws error for abc', () => {
    verifyInvalidTopicAndSequenceNum('abc', 'abc');
  });

  test('Verify validateGetSequenceMessageParams throws error for 123.0001', () => {
    verifyInvalidTopicAndSequenceNum(123.0001, 123.0001);
  });

  test('Verify validateGetSequenceMessageParams does not throw error for 0', () => {
    verifyValidTopicAndSequenceNum(0, 1);
  });

  test('Verify validateGetSequenceMessageParams does not throw error for 1234567890', () => {
    verifyValidTopicAndSequenceNum(1234567890, 1234567890);
  });

  test('Verify validateGetSequenceMessageParams does not throw error for 2', () => {
    verifyValidTopicAndSequenceNum(2, 1234567890);
  });

  test('Verify validateGetSequenceMessageParams does not throw error for "0.0.2"', () => {
    verifyValidTopicAndSequenceNum('0.0.2', 1234567890);
  });

  test('Verify validateGetSequenceMessageParams does not throw error for "0.2"', () => {
    verifyValidTopicAndSequenceNum('0.2', 1234567890);
  });
});

describe('topicmessage validateGetTopicMessagesParams tests', () => {
  test('Verify validateGetTopicMessagesParams throws error for -92233720368547758080', () => {
    verifyInvalidTopicMessages(-92233720368547758080n);
  });

  test('Verify validateGetTopicMessagesParams throws error for abc', () => {
    verifyInvalidTopicMessages('abc');
  });

  test('Verify validateGetTopicMessagesParams throws error for 123.0001', () => {
    verifyInvalidTopicMessages(123.0001);
  });

  test('Verify validateGetTopicMessagesParams does not throw error for 0', () => {
    verifyValidTopicMessages(0);
  });

  test('Verify validateGetTopicMessagesParams does not throw error for 1234567890', () => {
    verifyValidTopicMessages(1234567890);
  });

  test('Verify validateGetTopicMessagesParams does not throw error for 2', () => {
    verifyValidTopicMessages(2);
  });

  test('Verify validateGetTopicMessagesParams does not throw error for "0.0.2"', () => {
    verifyValidTopicMessages('0.0.2');
  });

  test('Verify validateGetTopicMessagesParams does not throw error for "0.2"', () => {
    verifyValidTopicMessages('0.2');
  });
});

describe('topicmessage extractSqlFromTopicMessagesRequest tests', () => {
  test('extractSqlFromTopicMessagesRequest', async () => {
    const filters = [
      {key: SEQUENCE_NUMBER, operator: ' > ', value: '2'},
      {key: TIMESTAMP, operator: ' <= ', value: '1234567890.000000006'},
      {key: LIMIT, operator: ' = ', value: '3'},
      {key: ORDER, operator: ' = ', value: constants.orderFilterValues.DESC},
    ];

    const {query, params, order, limit} = await topicMessage.extractSqlFromTopicMessagesRequest(7, filters);

    const expectedQuery = `select *
                         from topic_message
                         where topic_id = $1
                           and sequence_number > $2
                           and consensus_timestamp <= $3
                         order by consensus_timestamp desc
                         limit $4`;
    assertSqlQueryEqual(query, expectedQuery);
    expect(params).toStrictEqual([7, '2', '1234567890.000000006', '3']);
    expect(order).toStrictEqual(constants.orderFilterValues.DESC);
    expect(limit).toStrictEqual(3);
  });
});

describe('getRangeFromFilters', () => {
  const spec = [
    {name: 'expect default given empty filters', filters: [], expected: {lower: 0n, upper: constants.MAX_LONG - 1n}},
    {
      name: 'expect default with lower overridden',
      filters: [],
      defaultLower: 2n,
      expected: {lower: 2n, upper: constants.MAX_LONG - 1n},
    },
    {
      name: 'expect lower',
      filters: [
        {key: SEQUENCE_NUMBER, operator: gt, value: '100'},
        {key: SEQUENCE_NUMBER, operator: gte, value: 102},
        {key: SEQUENCE_NUMBER, operator: gt, value: 103n},
      ],
      defaultLower: 1n,
      expected: {lower: 104n, upper: constants.MAX_LONG - 1n},
    },
    {
      name: 'expect upper',
      filters: [
        {key: SEQUENCE_NUMBER, operator: lt, value: '200'},
        {key: SEQUENCE_NUMBER, operator: lte, value: 199},
        {key: SEQUENCE_NUMBER, operator: lt, value: 186n},
      ],
      defaultLower: 1n,
      expected: {lower: 1n, upper: 185n},
    },
    {
      name: 'expect both lower and upper',
      filters: [
        {key: SEQUENCE_NUMBER, operator: gt, value: '100'},
        {key: SEQUENCE_NUMBER, operator: gte, value: 102},
        {key: SEQUENCE_NUMBER, operator: gt, value: 103n},
        {key: SEQUENCE_NUMBER, operator: lt, value: '200'},
        {key: SEQUENCE_NUMBER, operator: lte, value: 199},
        {key: SEQUENCE_NUMBER, operator: lt, value: 186n},
      ],
      defaultLower: 1n,
      expected: {lower: 104n, upper: 185n},
    },
    {
      name: 'expect correct range given single equal',
      filters: [{key: SEQUENCE_NUMBER, operator: eq, value: '100'}],
      defaultLower: 1n,
      expected: {lower: 100n, upper: 100n},
    },
    {
      name: 'expect correct range given all equals with same value',
      filters: [
        {key: SEQUENCE_NUMBER, operator: eq, value: '100'},
        {key: SEQUENCE_NUMBER, operator: eq, value: '100'},
      ],
      defaultLower: 1n,
      expected: {lower: 100n, upper: 100n},
    },
    {
      name: 'expect correct range given single equal and covering range',
      filters: [
        {key: SEQUENCE_NUMBER, operator: eq, value: '100'},
        {key: SEQUENCE_NUMBER, operator: gt, value: 95},
        {key: SEQUENCE_NUMBER, operator: lt, value: 102},
      ],
      defaultLower: 1n,
      expected: {lower: 100n, upper: 100n},
    },
    {
      name: 'expect null given effectively empty range',
      filters: [
        {key: SEQUENCE_NUMBER, operator: gt, value: 102},
        {key: SEQUENCE_NUMBER, operator: lte, value: 102},
      ],
      defaultLower: 1n,
      expected: null,
    },
    {
      name: 'expect null given multiple equals',
      filters: [
        {key: SEQUENCE_NUMBER, operator: eq, value: 102},
        {key: SEQUENCE_NUMBER, operator: eq, value: 103},
      ],
      defaultLower: 1n,
      expected: null,
    },
    {
      name: 'expect null given equal then range without overlap',
      filters: [
        {key: SEQUENCE_NUMBER, operator: eq, value: 102},
        {key: SEQUENCE_NUMBER, operator: gt, value: 102},
        {key: SEQUENCE_NUMBER, operator: lt, value: 105},
      ],
      defaultLower: 1n,
      expected: null,
    },
    {
      name: 'expect null given range then equal without overlap',
      filters: [
        {key: SEQUENCE_NUMBER, operator: gt, value: 102},
        {key: SEQUENCE_NUMBER, operator: lt, value: 105},
        {key: SEQUENCE_NUMBER, operator: eq, value: 102},
      ],
      defaultLower: 1n,
      expected: null,
    },
    {
      name: 'expect null given gt, equal, and lt without overlap',
      filters: [
        {key: SEQUENCE_NUMBER, operator: gt, value: 102},
        {key: SEQUENCE_NUMBER, operator: eq, value: 102},
        {key: SEQUENCE_NUMBER, operator: lt, value: 105},
      ],
      defaultLower: 1n,
      expected: null,
    },
  ];

  test.each(spec)('$name', ({filters, defaultLower, expected}) => {
    expect(topicMessage.getRangeFromFilters(filters, defaultLower)).toStrictEqual(expected);
  });

  test('expect exception given ne operator', () => {
    const filters = [{key: SEQUENCE_NUMBER, operator: ' != ', value: '100'}];
    expect(() => topicMessage.getRangeFromFilters(filters, 1n)).toThrowErrorMatchingSnapshot();
  });
});

const verifyValidConsensusTimestamp = (timestamp) => {
  expect(() => {
    topicMessage.validateConsensusTimestampParam(timestamp);
  }).not.toThrow();
};

const verifyValidTopicAndSequenceNum = (topicId, seqNum) => {
  expect(() => {
    topicMessage.validateGetSequenceMessageParams(topicId, seqNum);
  }).not.toThrow();
};

const verifyValidTopicMessages = (topicId) => {
  expect(() => {
    topicMessage.validateGetTopicMessagesParams(topicId);
  }).not.toThrow();
};

const verifyInvalidConsensusTimestamp = (timestamp) => {
  expect(() => {
    topicMessage.validateConsensusTimestampParam(timestamp);
  }).toThrowErrorMatchingSnapshot();
};

const verifyInvalidTopicAndSequenceNum = (topicId, seqNum) => {
  expect(() => {
    topicMessage.validateGetSequenceMessageParams(topicId, seqNum);
  }).toThrowErrorMatchingSnapshot();
};

const verifyInvalidTopicMessages = (topicId) => {
  expect(() => {
    topicMessage.validateGetTopicMessagesParams(topicId);
  }).toThrowErrorMatchingSnapshot();
};
