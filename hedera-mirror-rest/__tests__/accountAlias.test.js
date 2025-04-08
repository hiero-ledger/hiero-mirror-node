// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';

import AccountAlias from '../accountAlias.js';
import {getAllAccountAliases, invalidBase32Strs} from './testutils';
import {InvalidArgumentError} from '../errors/index.js';
import {getMirrorConfig} from '../config.js';

const {common} = getMirrorConfig();

describe('AccountAlias', () => {
  describe('fromString', () => {
    describe('valid', () => {
      const testSpecs = [
        {
          input: 'AABBCC22',
          expected: new AccountAlias(null, null, 'AABBCC22'),
        },
        {
          input: '0.AABBCC22',
          expected: new AccountAlias(null, '0', 'AABBCC22'),
        },
        {
          input: '0.0.AABBCC22',
          expected: new AccountAlias('0', '0', 'AABBCC22'),
        },
        {
          input: '0.1.AABBCC22',
          expected: InvalidArgumentError,
        },
        {
          input: '99999.99999.AABBCC22',
          expected: InvalidArgumentError,
        },
        {
          input: '0.0.AABBCC22',
          expected: InvalidArgumentError,
          realm: 1,
        },
        {
          input: '0.0.AABBCC22',
          expected: InvalidArgumentError,
          shard: 1,
        },
      ];

      testSpecs.forEach((spec) => {
        test(spec.input, () => {
          const realmPrevious = common.realm;
          const shardPrevious = common.shard;
          if (spec.realm) {
            common.realm = spec.realm;
          }
          if (spec.shard) {
            common.shard = spec.shard;
          }
          if (spec.expected instanceof AccountAlias) {
            expect(AccountAlias.fromString(spec.input)).toEqual(spec.expected);
          } else {
            expect(() => AccountAlias.fromString(spec.input)).toThrowErrorMatchingSnapshot();
          }
          common.realm = realmPrevious;
          common.shard = shardPrevious;
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
      const accountAlias = new AccountAlias(null, '0', 'AABBCC22');
      expect(accountAlias.toString()).toBe('AABBCC22');
    });
    test('shard, realm and alias', () => {
      const accountAlias = new AccountAlias('0', '0', 'AABBCC22');
      expect(accountAlias.toString()).toBe('AABBCC22');
    });
  });
});
