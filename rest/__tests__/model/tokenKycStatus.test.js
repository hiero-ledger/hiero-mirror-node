// SPDX-License-Identifier: Apache-2.0

import {TokenKycStatus} from '../../model';

describe('TokenKycStatus', () => {
  describe('invalid id', () => {
    [-1, 3].forEach((value) =>
      test(`${value}`, () => {
        expect(() => new TokenKycStatus(value)).toThrowErrorMatchingSnapshot();
      })
    );
  });

  test('toJSON', () => {
    const input = [new TokenKycStatus(0), new TokenKycStatus(1), new TokenKycStatus(2)];
    expect(JSON.stringify(input)).toEqual(JSON.stringify(['NOT_APPLICABLE', 'GRANTED', 'REVOKED']));
  });
});
