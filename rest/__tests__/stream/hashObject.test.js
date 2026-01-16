// SPDX-License-Identifier: Apache-2.0

import HashObject from '../../stream/hashObject';

describe('HashObject', () => {
  let buffer;
  const classId = BigInt.asIntN(64, BigInt('0xf422da83a251741e'));
  const classVersion = 1;
  const digestType = 0x58ff811b;
  const hash = new Array(48).fill(0xef);

  beforeEach(() => {
    buffer = Buffer.from(
      [].concat(
        [0xf4, 0x22, 0xda, 0x83, 0xa2, 0x51, 0x74, 0x1e], // classId
        [0, 0, 0, 1], // classVersion
        [0x58, 0xff, 0x81, 0x1b], // digest type, sha-384
        [0, 0, 0, 0x30], // length, for sha-384, 48 bytes
        hash // 48-byte hash
      )
    );
  });

  test('getLength', () => {
    const expected = buffer.length;
    const hashObject = new HashObject(buffer);
    expect(hashObject.getLength()).toEqual(expected);
  });

  test('classId', () => {
    const hashObject = new HashObject(buffer);
    expect(hashObject.classId).toEqual(classId);
  });

  test('classVersion', () => {
    const hashObject = new HashObject(buffer);
    expect(hashObject.classVersion).toEqual(classVersion);
  });

  test('digestType', () => {
    const hashObject = new HashObject(buffer);
    expect(hashObject.digestType).toEqual(digestType);
  });

  test('hash', () => {
    const hashObject = new HashObject(buffer);
    expect(hashObject.hash).toEqual(Buffer.from(hash));
  });

  test('truncated buffer', () => {
    expect(() => new HashObject(buffer.slice(0, buffer.length - 4))).toThrowErrorMatchingSnapshot();
  });
});
