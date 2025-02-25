// SPDX-License-Identifier: Apache-2.0

import * as constants from '../constants';
import EntityId from '../entityId';
import {InvalidArgumentError} from '../errors';

describe('EntityId isValidEntityId tests', () => {
  test('Verify invalid for null', () => {
    expect(EntityId.isValidEntityId(null)).toBe(false);
  });
  test('Verify invalid for empty input', () => {
    expect(EntityId.isValidEntityId('')).toBe(false);
  });
  test('Verify invalid for more than 3 "." separated number parts', () => {
    expect(EntityId.isValidEntityId('0.1.2.3')).toBe(false);
  });
  test('Verify invalid for negative shard', () => {
    expect(EntityId.isValidEntityId('-1.0.1')).toBe(false);
  });
  test('Verify invalid for negative realm', () => {
    expect(EntityId.isValidEntityId('0.-1.1')).toBe(false);
  });
  test('Verify invalid for negative entity_num', () => {
    expect(EntityId.isValidEntityId('0.0.-1')).toBe(false);
  });
  test('Verify invalid for shard too big', () => {
    expect(EntityId.isValidEntityId('100000.65535.000000001')).toBe(false);
  });
  test('Verify invalid for realm too big', () => {
    expect(EntityId.isValidEntityId('100000.000000001')).toBe(false);
  });
  test('Verify invalid for negative num', () => {
    expect(EntityId.isValidEntityId('-1')).toBe(false);
  });
  test('Verify invalid for float number', () => {
    expect(EntityId.isValidEntityId(123.321)).toBe(false);
  });
  test('Verify valid for entity_num only', () => {
    expect(EntityId.isValidEntityId('3')).toBe(true);
  });
  test('Verify valid for realm.num', () => {
    expect(EntityId.isValidEntityId('65535.000000001')).toBe(true);
  });
  test('Verify valid for full entity', () => {
    expect(EntityId.isValidEntityId('1.2.3')).toBe(true);
  });
  test('Verify valid for full entity 2', () => {
    expect(EntityId.isValidEntityId('0.2.3')).toBe(true);
  });
  test('Verify valid for integer number', () => {
    expect(EntityId.isValidEntityId(123)).toBe(true);
  });
  test('Verify valid for encoded ID', () => {
    expect(EntityId.isValidEntityId('2814792716779530')).toBe(true);
  });
  test('Verify valid for max encoded ID', () => {
    expect(EntityId.isValidEntityId(2n ** 63n - 1n)).toBe(true);
  });
  test('Verify valid for parsable evm address', () => {
    expect(EntityId.isValidEntityId('0x0000000100000000000000020000000000000007')).toBe(true);
    expect(EntityId.isValidEntityId('0000000100000000000000020000000000000007')).toBe(true);
  });
  test('Verify valid for parsable evm address', () => {
    expect(EntityId.isValidEntityId('0x0000000100000000000000020000000000000007')).toBe(true);
    expect(EntityId.isValidEntityId('0000000100000000000000020000000000000007')).toBe(true);
  });
  test('Verify valid for non-parsable evm address', () => {
    expect(EntityId.isValidEntityId('0x71eaa748d5252be68c1185588beca495459fdba4')).toBe(true);
    expect(EntityId.isValidEntityId('71eaa748d5252be68c1185588beca495459fdba4')).toBe(true);
  });
  test('Verify valid for parsable evm address with shard and realm', () => {
    expect(EntityId.isValidEntityId('0.0.0000000100000000000000020000000000000007')).toBe(true);
  });
  test('Verify valid for non-parsable evm address with shard and realm', () => {
    expect(EntityId.isValidEntityId('0.0.71eaa748d5252be68c1185588beca495459fdba4')).toBe(true);
  });
  test('Verify invalid for parsable evm address and disallow evm address', () => {
    expect(EntityId.isValidEntityId('0x0000000100000000000000020000000000000007', false)).toBe(false);
    expect(EntityId.isValidEntityId('0000000100000000000000020000000000000007', false)).toBe(false);
  });
  test('Verify valid for non-parsable evm address and disallow evm address', () => {
    expect(EntityId.isValidEntityId('0x71eaa748d5252be68c1185588beca495459fdba4', false)).toBe(false);
    expect(EntityId.isValidEntityId('71eaa748d5252be68c1185588beca495459fdba4', false)).toBe(false);
  });
});

describe('EntityId parse from entityId string', () => {
  const specs = [
    {
      entityIdStr: '0.0.0',
      expected: EntityId.of(0, 0, 0, null),
    },
    {
      entityIdStr: '0.0.4294967295',
      expected: EntityId.of(0, 0, 4294967295),
    },
    {
      entityIdStr: '32767.65535.4294967295',
      expected: EntityId.of(32767, 65535, 4294967295),
    },
    {
      entityIdStr: '0',
      expected: EntityId.of(0, 0, 0),
    },
    {
      entityIdStr: '10',
      expected: EntityId.of(0, 0, 10),
    },
    {
      entityIdStr: '4294967295',
      expected: EntityId.of(0, 0, 4294967295),
    },
    {
      entityIdStr: '24294967295',
      expected: EntityId.of(0, 5, 2820130815),
    },
    {
      entityIdStr: '0.1',
      expected: EntityId.of(0, 0, 1),
    },
    {
      entityIdStr: '0x0000000000000000000000000000000000000001',
      options: {paramName: constants.filterKeys.FROM},
      expected: EntityId.of(0, 0, 1),
    },
    {
      entityIdStr: '0000000000000000000000000000000000000001',
      options: {paramName: constants.filterKeys.CONTRACT_ID},
      expected: EntityId.of(0, 0, 1),
    },
    {
      entityIdStr: '0x0000000100000000000000020000000000000003',
      options: {paramName: constants.filterKeys.FROM},
      expected: EntityId.of(1, 2, 3),
    },
    {
      entityIdStr: '0x00007fff000000000000ffff00000000ffffffff',
      options: {paramName: constants.filterKeys.FROM},
      expected: EntityId.of(32767, 65535, 4294967295),
    },
    {
      entityIdStr: '0.0.000000000000000000000000000000000186Fb1b',
      expected: EntityId.of(0, 0, 25623323),
    },
    {
      entityIdStr: '0.000000000000000000000000000000000186Fb1b',
      expected: EntityId.of(0, 0, 25623323),
    },
    {
      entityIdStr: '000000000000000000000000000000000186Fb1b',
      expected: EntityId.of(0, 0, 25623323),
    },
    {
      entityIdStr: '0x000000000000000000000000000000000186Fb1b',
      options: {paramName: constants.filterKeys.FROM},
      expected: EntityId.of(0, 0, 25623323),
    },
    {
      entityIdStr: null,
      options: {isNullable: true},
      expected: EntityId.of(null, null, null),
    },
    {
      options: {isNullable: true},
      expected: EntityId.of(null, null, null),
    },
    {
      entityIdStr: '0000000100000000000000020000000000000007',
      expected: EntityId.of(1, 2, 7),
    },
    {
      entityIdStr: '0x0000000100000000000000020000000000000007',
      expected: EntityId.of(1, 2, 7),
    },
    {
      entityIdStr: '1.2.0000000100000000000000020000000000000007',
      expected: EntityId.of(1, 2, 7),
    },
    {
      entityIdStr: '71eaa748d5252be68c1185588beca495459fdba4',
      expected: EntityId.of(null, null, null, '71eaa748d5252be68c1185588beca495459fdba4'),
    },
    {
      entityIdStr: '0x71eaa748d5252be68c1185588beca495459fdba4',
      expected: EntityId.of(null, null, null, '71eaa748d5252be68c1185588beca495459fdba4'),
    },
    {
      entityIdStr: '1.2.71eaa748d5252be68c1185588beca495459fdba4',
      expected: EntityId.of(1, 2, null, '71eaa748d5252be68c1185588beca495459fdba4'),
    },
    {
      entityIdStr: null,
      options: {isNullable: false},
      expectErr: true,
    },
    {
      options: {isNullable: false},
      expectErr: true,
    },
    {
      entityIdStr: '0.1.x',
      options: {paramName: 'demo param'},
      expectErr: true,
    },
    {
      entityIdStr: 'a',
      expectErr: true,
    },
    {
      entityIdStr: '0.1.2.3',
      expectErr: true,
    },
    {
      entityIdStr: 'a.b.c',
      expectErr: true,
    },
    {
      entityIdStr: '-1.-1.-1',
      expectErr: true,
    },
    {
      entityIdStr: '-1',
      expectErr: true,
    },
    {
      entityIdStr: '32768.65536.4294967296',
      expectErr: true,
    },
    {
      entityIdStr: '0x',
      options: {paramName: constants.filterKeys.FROM},
      expectErr: true,
    },
    {
      entityIdStr: '0x010203',
      options: {paramName: constants.filterKeys.FROM},
      expectErr: true,
    },
    {
      entityIdStr: '0x00000001000000000000000200000000000000034',
      options: {paramName: constants.filterKeys.FROM},
      expectErr: true,
    },
    {
      entityIdStr: '0x2540be3f6001fffffffffffff001fffffffffffff', // 9999999990, Number.MAX_SAFE_INTEGER, Number.MAX_SAFE_INTEGER
      options: {paramName: constants.filterKeys.FROM},
      expectErr: true,
    },
    {
      entityIdStr: '0x10000000000000000000000000000000000000000', // ffffffffffffffffffffffffffffffffffffffff + 1
      options: {paramName: constants.filterKeys.FROM},
      expectErr: true,
    },
    {
      entityIdStr: '2.3.0000000100000000000000020000000000000007',
      expectErr: true,
    },
  ];

  for (const spec of specs) {
    let {entityIdStr, options, expected, expectErr} = spec;
    options = options || {};
    test(`${entityIdStr}`, () => {
      if (!expectErr) {
        expect(EntityId.parse(entityIdStr, options).toString()).toEqual(expected.toString());
      } else {
        expect(() => {
          EntityId.parse(entityIdStr, options);
        }).toThrowErrorMatchingSnapshot();
      }
    });
  }
});

describe('EntityId cache', function () {
  test('cache hit from entity ID string', () => {
    const original = EntityId.parse('0.1.158');
    const cached = EntityId.parse('0.1.158');
    expect(cached).toBe(original);
  });

  test('cache hit from encoded entity ID', () => {
    const original = EntityId.parse(4294967454);
    const cached = EntityId.parse(4294967454);
    expect(cached).toBe(original);
  });

  test('cache hit from BigInt / number / string', () => {
    const original = EntityId.parse('2005');

    expect(EntityId.parse(2005n)).toBe(original);
    expect(EntityId.parse(2005)).toBe(original);
    expect(EntityId.parse('2005')).toBe(original);
  });
});

describe('EntityId parse from encoded entityId', () => {
  const specs = [
    {
      encodedId: 0,
      expected: EntityId.of(0, 0, 0),
    },
    {
      encodedId: 4294967295,
      expected: EntityId.of(0, 0, 4294967295),
    },
    {
      encodedId: 2814792716779530,
      expected: EntityId.of(10, 10, 10),
    },
    {
      encodedId: BigInt(0),
      expected: EntityId.of(0, 0, 0),
    },
    {
      encodedId: BigInt(4294967295),
      expected: EntityId.of(0, 0, 4294967295),
    },
    {
      encodedId: '4294967295',
      expected: EntityId.of(0, 0, 4294967295),
    },
    {
      encodedId: BigInt(2814792716779530),
      expected: EntityId.of(10, 10, 10),
    },
    {
      encodedId: 9223372036854775807n,
      expected: EntityId.of(32767, 65535, 4294967295),
    },
    {
      encodedId: 9223090561878065152n,
      expected: EntityId.of(32767, 0, 0),
    },
    {
      encodedId: 'a',
      expectErr: true,
    },
    {
      encodedId: true,
      expectErr: true,
    },
    {
      encodedId: 2n ** 63n,
      expectErr: true,
    },
    {
      encodedId: 9223372036854775808n, // 32768.0.0
      expectErr: true,
    },
    {
      encodedId: null,
      options: {isNullable: true},
      expected: EntityId.of(null, null, null),
    },
    {
      options: {isNullable: true},
      expected: EntityId.of(null, null, null),
    },
    {
      encodedId: null,
      expectErr: true,
    },
    {
      expectErr: true,
    },
  ];

  for (const spec of specs) {
    let {encodedId, options, expectErr, expected} = spec;
    options = options || {};
    test(`${encodedId}`, () => {
      if (!expectErr) {
        expect(EntityId.parse(encodedId, options).toString()).toEqual(expected.toString());
      } else {
        expect(() => {
          EntityId.parse(encodedId, options);
        }).toThrow(InvalidArgumentError);
      }
    });
  }
});

describe('EntityId toEvmAddress', () => {
  const evmAddress = '71eaa748d5252be68c1185588beca495459fdba4';

  test('0.0.0', () => {
    expect(EntityId.of(0, 0, 0).toEvmAddress()).toEqual(null);
  });

  test('0.0.7', () => {
    expect(EntityId.of(1, 2, 7).toEvmAddress()).toEqual('0x0000000100000000000000020000000000000007');
  });

  test('32767.65535.4294967295', () => {
    expect(EntityId.of(32767n, 65535n, 4294967295n).toEvmAddress()).toEqual(
      '0x00007fff000000000000ffff00000000ffffffff'
    );
  });

  test(evmAddress, () => {
    const actual = EntityId.of(null, null, null, evmAddress).toEvmAddress();
    expect(actual).toEqual(`0x${evmAddress}`);
  });

  test(`0.1.${evmAddress}`, () => {
    const actual = EntityId.of(0, 1, null, evmAddress).toEvmAddress();
    expect(actual).toEqual(`0x${evmAddress}`);
  });
});

describe('EntityId toString', () => {
  test('null', () => {
    expect(EntityId.of(null, null, null).toString()).toEqual(null);
  });

  test('0.0.0', () => {
    expect(EntityId.of(0n, 0n, 0n).toString()).toEqual(null);
  });

  test('32767.65535.4294967295', () => {
    expect(EntityId.of(32767, 65535, 4294967295).toString()).toEqual('32767.65535.4294967295');
  });

  test('71eaa748d5252be68c1185588beca495459fdba4', () => {
    expect(EntityId.of(null, null, null, '71eaa748d5252be68c1185588beca495459fdba4').toString()).toEqual(
      '71eaa748d5252be68c1185588beca495459fdba4'
    );
  });

  test('0.0.71eaa748d5252be68c1185588beca495459fdba4', () => {
    expect(EntityId.of(0n, 0n, null, '71eaa748d5252be68c1185588beca495459fdba4').toString()).toEqual(
      '0.0.71eaa748d5252be68c1185588beca495459fdba4'
    );
  });
});

describe('EntityId encoding', () => {
  test('0.0.0', () => {
    expect(EntityId.parse('0.0.0').getEncodedId()).toBe(0);
  });

  test('0.0.10', () => {
    expect(EntityId.parse('0.0.10').getEncodedId()).toBe(10);
  });

  test('0.0.4294967295', () => {
    expect(EntityId.parse('0.0.4294967295').getEncodedId()).toBe(4294967295);
  });

  test('10.10.10', () => {
    expect(EntityId.parse('10.10.10').getEncodedId()).toBe(2814792716779530);
  });

  test('31.65535.4294967295', () => {
    expect(EntityId.parse('31.65535.4294967295').getEncodedId()).toBe(Number.MAX_SAFE_INTEGER);
  });

  test('32.0.0', () => {
    expect(EntityId.parse('32.0.0').getEncodedId()).toBe(2n ** 53n);
  });

  test('32767.65535.4294967295', () => {
    expect(EntityId.parse('32767.65535.4294967295').getEncodedId()).toBe(9223372036854775807n);
  });

  test('32767.0.0', () => {
    expect(EntityId.parse('32767.0.0').getEncodedId()).toBe(9223090561878065152n);
  });

  test('nullable', () => {
    expect(EntityId.parse(null, {isNullable: true}).getEncodedId()).toBeNull();
  });
});

describe('computeContractIdPartsFromContractIdValue', () => {
  const specs = [
    {input: '0.0.500', expected: {shard: '0', realm: '0', num: '500'}},
    {
      input: '500',
      expected: {shard: null, realm: null, num: '500'},
    },
    {
      input: '0.0.71eaa748d5252be68c1185588beca495459fdba4',
      expected: {shard: '0', realm: '0', create2_evm_address: '71eaa748d5252be68c1185588beca495459fdba4'},
    },
    {
      input: '71eaa748d5252be68c1185588beca495459fdba4',
      expected: {shard: null, realm: null, create2_evm_address: '71eaa748d5252be68c1185588beca495459fdba4'},
    },
    {
      input: '0.0.0x71eaa748d5252be68c1185588beca495459fdba4',
      expected: {shard: '0', realm: '0', create2_evm_address: '71eaa748d5252be68c1185588beca495459fdba4'},
    },
    {
      input: '0x71eaa748d5252be68c1185588beca495459fdba4',
      expected: {shard: null, realm: null, create2_evm_address: '71eaa748d5252be68c1185588beca495459fdba4'},
    },
  ];

  for (const spec of specs) {
    test(spec.input, () => {
      expect(EntityId.computeContractIdPartsFromContractIdValue(spec.input)).toEqual(spec.expected);
    });
  }
});
