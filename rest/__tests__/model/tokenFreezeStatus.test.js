// SPDX-License-Identifier: Apache-2.0

import {TokenFreezeStatus} from '../../model';

describe('TokenFreezeStatus', () => {
  describe('invalid id', () => {
    [-1, 3].forEach((value) =>
      test(`${value}`, () => {
        expect(() => new TokenFreezeStatus(value)).toThrowErrorMatchingSnapshot();
      })
    );
  });

  test('toJSON', () => {
    const input = [new TokenFreezeStatus(0), new TokenFreezeStatus(1), new TokenFreezeStatus(2)];
    expect(JSON.stringify(input)).toEqual(JSON.stringify(['NOT_APPLICABLE', 'FROZEN', 'UNFROZEN']));
  });
});
