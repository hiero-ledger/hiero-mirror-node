// SPDX-License-Identifier: Apache-2.0

import base32 from '../base32';

describe('decode', () => {
  describe('valid', () => {
    test('32W35LY', () => {
      expect(base32.decode('32W353Y')).toEqual(Uint8Array.from(Buffer.from('deadbeef', 'hex')));
    });
    test('32W35366', () => {
      expect(base32.decode('32W35366')).toEqual(Uint8Array.from(Buffer.from('deadbeefde', 'hex')));
    });
    test('null', () => {
      expect(base32.decode(null)).toBeNull();
    });
  });

  describe('invalid', () => {
    const invalidBase32Strs = [
      // A base32 group without padding can have 2, 4, 5, 7 or 8 characters from its alphabet
      'A',
      'AAA',
      'AAAAAA',
      // non-base32 characters, note due to the loose option, 0, 1, and 8 will be auto corrected to O, L, and B
      '9',
    ];
    invalidBase32Strs.forEach((invalidBase32Str) => {
      test(`${invalidBase32Str}`, () => {
        expect(() => base32.decode(invalidBase32Str)).toThrowErrorMatchingSnapshot();
      });
    });
  });
});

describe('encode', () => {
  test('0xdeadbeef', () => {
    expect(base32.encode(Buffer.from('deadbeef', 'hex'))).toBe('32W353Y');
  });
  test('0xdeadbeefde', () => {
    expect(base32.encode(Buffer.from('deadbeefde', 'hex'))).toBe('32W35366');
  });
  test('null', () => {
    expect(base32.encode(null)).toBeNull();
  });
});
