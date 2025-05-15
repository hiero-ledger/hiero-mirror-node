// SPDX-License-Identifier: Apache-2.0

import {isValidTransactionHash} from '../transactionHash';

describe('isValidTransactionHash', () => {
  describe('valid', () => {
    test.each`
      input
      ${'0xb185f7648c98a2cc823082bc8a1dce342fedbc8f776ee673498f412f27ebe02f0ad807206b8506a1f052e42dbd53be33'}
      ${'b185f7648c98a2cc823082bc8a1dce342fedbc8f776ee673498f412f27ebe02f0ad807206b8506a1f052e42dbd53be33'}
      ${'sYX3ZIyYosyCMIK8ih3ONC/tvI93buZzSY9BLyfr4C8K2Acga4UGofBS5C29U74z'}
    `('$input', ({input}) => {
      expect(isValidTransactionHash(input)).toBeTrue();
    });
  });

  describe('invalid', () => {
    test.each`
      input
      ${'0xb185f7648c98a2cc823082bc8a1dce342fedbc8f776ee673498f412f27ebe02f0ad807206b8506a1f052e42dbd53'}
      ${'b185f7648c98a2cc823082bc8a1dce342fedbc8f776ee673498f412f27ebe02f0ad807206b8506a1f052e42dbd53'}
      ${'sYX3ZIyYosyCMIK8ih3ONC/tvI93buZzSY9BLyfr4C8K2Acga4UGofBS5C29'}
      ${'0x'}
      ${''}
    `('$input', ({input}) => {
      expect(isValidTransactionHash(input)).toBeFalse();
    });
  });
});
