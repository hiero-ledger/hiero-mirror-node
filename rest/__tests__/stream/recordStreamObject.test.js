// SPDX-License-Identifier: Apache-2.0

import RecordStreamObject from '../../stream/recordStreamObject';

describe('RecordStreamObject', () => {
  const classId = BigInt.asIntN(64, BigInt('0xe370929ba5429d8b'));
  const classVersion = 1;
  const recordLength = 16;
  const recordBytes = new Array(recordLength).fill(0xa);
  const transactionLength = 32;
  const transactionBytes = new Array(transactionLength).fill(0xc);

  const buffer = Buffer.from(
    [].concat(
      [0xe3, 0x70, 0x92, 0x9b, 0xa5, 0x42, 0x9d, 0x8b], // classId
      [0, 0, 0, 1], // classVersion
      [0, 0, 0, recordLength],
      recordBytes,
      [0, 0, 0, transactionLength],
      transactionBytes
    )
  );

  test('getLength', () => {
    const expected = buffer.length;
    const recordStreamObject = new RecordStreamObject(buffer);
    expect(recordStreamObject.getLength()).toEqual(expected);
  });

  test('classId', () => {
    const recordStreamObject = new RecordStreamObject(buffer);
    expect(recordStreamObject.classId).toEqual(classId);
  });

  test('classVersion', () => {
    const recordStreamObject = new RecordStreamObject(buffer);
    expect(recordStreamObject.classVersion).toEqual(classVersion);
  });

  test('recordBytes', () => {
    const recordStreamObject = new RecordStreamObject(buffer);
    expect(recordStreamObject.record).toEqual(Buffer.from(recordBytes));
  });

  test('transactionBytes', () => {
    const recordStreamObject = new RecordStreamObject(buffer);
    expect(recordStreamObject.transaction).toEqual(Buffer.from(transactionBytes));
  });

  test('truncated buffer', () => {
    expect(() => new RecordStreamObject(buffer.slice(0, buffer.length - 4))).toThrowErrorMatchingSnapshot();
  });
});
