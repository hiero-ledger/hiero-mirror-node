// SPDX-License-Identifier: Apache-2.0

import EntityId from '../entityId';
import TransactionId from '../transactionId';

describe('TransactionId from invalid transaction ID string', () => {
  const invalidTransactionIdStrs = [
    'bad-string',
    'bad.entity.id-badseconds-badnanos',
    '0.0.1',
    '0.0.1-1234567891-0000000000',
    '0.0.1-1234567891-000000000 ',
    ' 0.0.1-1234567891-000000000',
    '0.0.1- 1234567891-000000000',
    '0.0.1-1234567891- 000000000',
    '0.0.1-9223372036854775808-0',
    '0.0.1-9223372036854775809-0',
    '0.0.1--9--0',
  ];

  invalidTransactionIdStrs.forEach((invalidTransactionIdStr) => {
    test(`invalid transaction ID - ${invalidTransactionIdStr}`, () => {
      expect(() => {
        TransactionId.fromString(invalidTransactionIdStr);
      }).toThrow();
    });
  });
});

describe('TransactionId toString', () => {
  const testSpecs = [
    {
      input: '0.0.1-1234567891-000000000',
      expected: '0.0.1-1234567891-0',
    },
    {
      input: '0.0.1-1234567891-0',
    },
    {
      input: '0.0.1-0-0',
    },
    {
      input: '0.0.1-01-0',
      expected: '0.0.1-1-0',
    },
    {
      input: '1023.65535.274877906943-9223372036854775807-999999999',
    },
  ];

  testSpecs.forEach((testSpec) => {
    test(testSpec.input, () => {
      const {input} = testSpec;
      const expected = testSpec.expected ? testSpec.expected : input;
      expect(TransactionId.fromString(input).toString()).toEqual(expected);
    });
  });
});

describe('TransactionId getEntityId', () => {
  const testSpecs = [
    {
      transactionIdStr: '0.0.1-1234567891-000000000',
      entityId: EntityId.parse('0.0.1'),
    },
    {
      transactionIdStr: '1023.65535.274877906943-9223372036854775807-999999999',
      entityId: EntityId.parse('1023.65535.274877906943'),
    },
  ];

  testSpecs.forEach((testSpec) => {
    test(testSpec.transactionIdStr, () => {
      expect(TransactionId.fromString(testSpec.transactionIdStr).getEntityId()).toEqual(testSpec.entityId);
    });
  });
});

describe('TransactionId getValidStartNs', () => {
  const testSpecs = [
    {
      transactionIdStr: '0.0.1-1234567891-0',
      validStartNs: '1234567891000000000',
    },
    {
      transactionIdStr: '0.0.1-9223372036854775807-1',
      validStartNs: '9223372036854775807000000001',
    },
    {
      transactionIdStr: '0.0.1-9223372036854775807-999999999',
      validStartNs: '9223372036854775807999999999',
    },
  ];

  testSpecs.forEach((testSpec) => {
    test(testSpec.transactionIdStr, () => {
      expect(TransactionId.fromString(testSpec.transactionIdStr).getValidStartNs()).toEqual(testSpec.validStartNs);
    });
  });
});
