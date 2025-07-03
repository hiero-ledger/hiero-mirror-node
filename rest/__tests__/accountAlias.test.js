// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';

import AccountAlias from '../accountAlias';
import {getAllAccountAliases, invalidBase32Strs} from './testutils';
import {InvalidArgumentError} from '../errors/index.js';
import {getMirrorConfig} from '../config';

const {common} = getMirrorConfig();

describe('AccountAlias', () => {
  describe('fromString', () => {
    describe('valid', () => {
      const testSpecs = [
        {
          name: 'no shard and realm',
          input: 'AABBCC22',
          expected: new AccountAlias(null, null, 'AABBCC22'),
        },
        {
          name: 'only realm',
          input: `${common.realm}.AABBCC22`,
          expected: new AccountAlias(null, common.realm, 'AABBCC22'),
        },
        {
          name: 'shard and realm',
          input: `${common.shard}.${common.realm}.AABBCC22`,
          expected: new AccountAlias(common.shard, common.realm, 'AABBCC22'),
        },
        {
          name: 'mismatched realm',
          input: `${common.shard}.${common.realm + 1n}.AABBCC22`,
          expected: InvalidArgumentError,
        },
        {
          name: 'mismatched shard and realm',
          input: `${common.shard + 1n}.${common.realm + 1n}.AABBCC22`,
          expected: InvalidArgumentError,
        },
        {
          name: 'mismatched shard',
          input: `${common.shard + 1n}.${common.realm}.AABBCC22`,
          expected: InvalidArgumentError,
        },
      ];

      testSpecs.forEach((spec) => {
        test(spec.input, () => {
          if (spec.expected instanceof AccountAlias) {
            expect(AccountAlias.fromString(spec.input)).toEqual(spec.expected);
          } else {
            try {
              AccountAlias.fromString(spec.input);
              throw new Error('Expected an error to be thrown');
            } catch (error) {
              expect(error).toBeInstanceOf(InvalidArgumentError);
              expect(error.message).toMatch(/^Invalid accountAlias string \d+\.\d+\.AABBCC22$/);
            }
          }
        });
      });
    });

    describe('invalid', () => {
      const inputs = _.flattenDeep([
        null,
        undefined,
        invalidBase32Strs.map((alias) => getAllAccountAliases(alias)),
        '100000.100000.AABBCC22',
        '0.0.0.AABBCC22',
      ]);

      inputs.forEach((input) => {
        test(`${input}`, () => {
          expect(() => AccountAlias.fromString(input)).toThrowErrorMatchingSnapshot();
        });
      });
    });
  });

  describe('isValid', () => {
    const shardRealmInputs = _.flattenDeep([
      {alias: '', noShardRealm: false, expected: false},
      {alias: undefined, noShardRealm: false, expected: false},
      {alias: null, noShardRealm: false, expected: false},
      {alias: '99999.99999.AABBCC22', noShardRealm: false, expected: true},
      {alias: '99999.99999.AABBCC22', noShardRealm: true, expected: false},
      {alias: 'AABBCC22', noShardRealm: true, expected: true},
      {alias: 'AABBCC22', noShardRealm: false, expected: true},
    ]);

    shardRealmInputs.forEach((input) => {
      test(`${input}`, () => {
        expect(AccountAlias.isValid(input.alias, input.noShardRealm)).toBe(input.expected);
      });
    });
  });

  describe('toString', () => {
    test('only alias', () => {
      const accountAlias = new AccountAlias(null, null, 'AABBCC22');
      expect(accountAlias.toString()).toBe('AABBCC22');
    });
    test('realm and alias', () => {
      const accountAlias = new AccountAlias(null, common.realm, 'AABBCC22');
      expect(accountAlias.toString()).toBe('AABBCC22');
    });
    test('shard, realm and alias', () => {
      const accountAlias = new AccountAlias(common.shard, common.realm, 'AABBCC22');
      expect(accountAlias.toString()).toBe('AABBCC22');
    });
  });
});
