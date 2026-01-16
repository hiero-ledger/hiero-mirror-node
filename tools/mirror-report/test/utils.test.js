// SPDX-License-Identifier: Apache-2.0

import {parseAccount, toHbars, toIsoString, validateDate} from '../src/utils.js';
import {InvalidArgumentError} from 'commander';

describe('parseAccount', () => {
  test('No duplicates', async () => {
    var existing = new Set(['0.0.1']);
    expect(parseAccount('0.0.1', existing)).toEqual(existing);
  });

  test('Multiple', async () => {
    var existing = new Set(['0.0.1']);
    expect(parseAccount('0.0.2', existing)).toEqual(new Set(['0.0.1', '0.0.2']));
  });

  it.each([
    ['0.0.1', ['0.0.1']],
    ['0.0.1-0.0.2', ['0.0.1', '0.0.2']],
    ['0.0.1-0.0.3', ['0.0.1', '0.0.2', '0.0.3']],
  ])("'%s' = %s", (account, expected) => {
    expect(parseAccount(account)).toEqual(new Set(expected));
  });

  it.each([
    null,
    '',
    'x',
    '0-1',
    '100',
    '0.100',
    'x.0.100',
    '0.y.100',
    '0.0.z',
    '0.0.2-0.0.2',
    '0.0.2-0.0.1',
    '0.0.100-0.1.101',
  ])("'%s' throws error", (account) => {
    expect(() => parseAccount(account)).toThrow(InvalidArgumentError);
  });
});

describe('toHbars', () => {
  it.each([
    [undefined, ''],
    [null, ''],
    [0n, '0.0'],
    [1n, '0.00000001'],
    [-1n, '-0.00000001'],
    [10n, '0.00000010'],
    [99999999n, '0.99999999'],
    [-99999999n, '-0.99999999'],
    [100000000n, '1.0'],
    [-100000000n, '-1.0'],
    [10000000001n, '100.00000001'],
    [4999999999999999999n, '49999999999.99999999'],
    [-4999999999999999999n, '-49999999999.99999999'],
    [5000000000000000000n, '50000000000.0'],
  ])("toHbars(%s) = '%s'", (tinybars, expected) => {
    expect(toHbars(tinybars)).toBe(expected);
  });
});

describe('toIsoString', () => {
  it.each([
    [undefined, ''],
    [null, ''],
    ['0.0', '1970-01-01T00:00:00.000000000Z'],
    ['0.', '1970-01-01T00:00:00.000000000Z'],
    ['0.000000001', '1970-01-01T00:00:00.000000001Z'],
    ['1.000000001', '1970-01-01T00:00:01.000000001Z'],
    ['1.1', '1970-01-01T00:00:01.000000001Z'],
    ['1733181006', '2024-12-02T23:10:06.000000000Z'],
    ['1733181006.483579814', '2024-12-02T23:10:06.483579814Z'],
  ])("toIsoString(%s) = '%s'", (timestamp, expected) => {
    expect(toIsoString(timestamp)).toBe(expected);
  });
});

describe('validateDate', () => {
  test('Valid', async () => {
    const date = '2024-12-17';
    expect(validateDate(date)).toBe(date);
  });

  it.each([
    null,
    '',
    '1970-01-01T00:00:00.000000000Z',
    '19700101',
    '1970-13-01',
    '1970-12-32',
    '99999-01-01',
    '999-01-01',
  ])("'%s' throws error", (date) => {
    expect(() => validateDate(date)).toThrow(InvalidArgumentError);
  });
});
