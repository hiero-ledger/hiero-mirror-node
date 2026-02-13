// SPDX-License-Identifier: Apache-2.0

import long from 'long';
import {proto} from '@hiero-ledger/proto';
import TransactionId from '../../transactionId';
import * as utils from '../../stream/utils';

describe('protoTransactionIdToTransactionId', () => {
  const accountID = {
    shardNum: 0,
    realmNum: 0,
    accountNum: 1010,
  };

  const testSpecs = [
    {
      transactionId: proto.TransactionID.create({
        accountID,
        transactionValidStart: {
          seconds: 193823,
          nanos: 0,
        },
      }),
      expected: TransactionId.fromString('0.0.1010-193823-0'),
    },
    {
      transactionId: proto.TransactionID.create({
        accountID,
        transactionValidStart: {
          seconds: 193823,
          nanos: 999999999,
        },
      }),
      expected: TransactionId.fromString('0.0.1010-193823-999999999'),
    },
    {
      transactionId: proto.TransactionID.create({
        accountID,
        transactionValidStart: {
          seconds: long.MAX_VALUE,
          nanos: 999999999,
        },
      }),
      expected: TransactionId.fromString(`0.0.1010-${long.MAX_VALUE.toString()}-999999999`),
    },
  ];

  testSpecs.forEach((testSpec) => {
    test(`expect output ${testSpec.expected}`, () => {
      expect(utils.protoTransactionIdToTransactionId(testSpec.transactionId)).toEqual(testSpec.expected);
    });
  });
});

describe('readLengthAndBytes', () => {
  const data = new Array(16).fill(0xab);
  const bufferWithChecksum = Buffer.from([0, 0, 0, 16, 0, 0, 0, 101 - 16].concat(data));
  const bufferNoChecksum = Buffer.from([0, 0, 0, 16].concat(data));

  const testSpecs = [
    {
      name: 'read from buffer with checksum',
      buffer: bufferWithChecksum,
      hasChecksum: true,
      minLength: 1,
      maxLength: data.length,
      expected: {
        length: bufferWithChecksum.length,
        bytes: Buffer.from(data),
      },
    },
    {
      name: 'read from buffer with checksum and exact length',
      buffer: bufferWithChecksum,
      hasChecksum: true,
      minLength: data.length,
      maxLength: data.length,
      expected: {
        length: bufferWithChecksum.length,
        bytes: Buffer.from(data),
      },
    },
    {
      name: 'read from buffer without checksum',
      buffer: bufferNoChecksum,
      hasChecksum: false,
      minLength: 1,
      maxLength: data.length,
      expected: {
        length: bufferNoChecksum.length,
        bytes: Buffer.from(data),
      },
    },
    {
      name: 'read from buffer with checksum error',
      buffer: bufferNoChecksum,
      hasChecksum: true,
      minLength: 1,
      maxLength: data.length,
    },
    {
      name: 'read from buffer with length over max',
      buffer: bufferWithChecksum,
      hasChecksum: true,
      minLength: 1,
      maxLength: data.length - 1,
    },
    {
      name: 'read from buffer with length smaller than min',
      buffer: bufferWithChecksum,
      hasChecksum: true,
      minLength: data.length + 1,
      maxLength: data.length + 2,
    },
    {
      name: 'read from buffer with length not match',
      buffer: bufferWithChecksum,
      hasChecksum: true,
      minLength: data.length + 1,
      maxLength: data.length + 1,
    },
  ];

  testSpecs.forEach((testSpec) => {
    const {name, buffer, minLength, maxLength, hasChecksum, expected} = testSpec;
    test(name, () => {
      if (expected) {
        const actual = utils.readLengthAndBytes(buffer, minLength, maxLength, hasChecksum);
        expect(actual).toEqual(expected);
      } else {
        expect(() =>
          utils.readLengthAndBytes(buffer, minLength, maxLength, hasChecksum)
        ).toThrowErrorMatchingSnapshot();
      }
    });
  });
});

describe('readNBytes', () => {
  const buffer = Buffer.from(new Array(16).fill(0xab));
  const testSpecs = [
    {
      name: 'read 1 byte from 16-byte buffer',
      buffer,
      length: 1,
      expected: buffer.slice(0, 1),
    },
    {
      name: 'read 16 byte from 16-byte buffer',
      buffer,
      length: 16,
      expected: buffer,
    },
    {
      name: 'read 0 byte from 16-byte buffer',
      buffer,
      length: 0,
      expected: buffer.slice(0, 0),
    },
    {
      name: 'read 17 byte from 16-byte buffer',
      buffer,
      length: 17,
    },
    {
      name: 'read -1 byte from 16-byte buffer',
      buffer,
      length: -1,
    },
  ];

  testSpecs.forEach((testSpec) => {
    test(testSpec.name, () => {
      if (testSpec.expected) {
        expect(utils.readNBytes(testSpec.buffer, testSpec.length)).toEqual(testSpec.expected);
      } else {
        expect(() => utils.readNBytes(testSpec.buffer, testSpec.length)).toThrowErrorMatchingSnapshot();
      }
    });
  });
});
