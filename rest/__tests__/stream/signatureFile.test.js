// SPDX-License-Identifier: Apache-2.0

import {BYTE_SIZE} from '../../stream/constants';
import HashObject from '../../stream/hashObject';
import SignatureFile from '../../stream/signatureFile';
import testUtils from './testUtils';

describe('from signature file buffer', () => {
  Object.entries(testUtils.testSignatureFiles).forEach(([version, testSpec]) => {
    test(version, () => {
      expect(new SignatureFile(testSpec.buffer)).toEqual(testSpec.expected);
    });

    test(`${version} with extra data`, () => {
      const buffer = Buffer.alloc(testSpec.buffer.length + 1);
      testSpec.buffer.copy(buffer);
      expect(() => new SignatureFile(buffer)).toThrowErrorMatchingSnapshot();
    });

    test(`${version} with truncated data`, () => {
      const buffer = testSpec.buffer.slice(0, testSpec.buffer.length - 1);
      expect(() => new SignatureFile(buffer)).toThrowErrorMatchingSnapshot();
    });
  });

  describe('unsupported version', () => {
    [0, 1, 2, 3, 7].forEach((version) => {
      test(`mark/version ${version}`, () => {
        const buffer = Buffer.from(testUtils.testSignatureFiles.v2.buffer);
        buffer.writeInt8(version);
        expect(() => new SignatureFile(buffer)).toThrowErrorMatchingSnapshot();
      });
    });
  });

  test('incorrect signature mark', () => {
    const buffer = Buffer.from(testUtils.testSignatureFiles.v2.buffer);
    buffer.writeInt8(16, BYTE_SIZE + HashObject.SHA_384.length);
    expect(() => new SignatureFile(buffer)).toThrowErrorMatchingSnapshot();
  });
});
