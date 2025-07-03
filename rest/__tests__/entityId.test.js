// SPDX-License-Identifier: Apache-2.0

import {getMirrorConfig} from '../config';
import * as constants from '../constants';
import EntityId from '../entityId';
import {InvalidArgumentError} from '../errors';

const {
  common: {realm, shard},
} = getMirrorConfig();

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
  test('Verify invalid for encoded id too long', () => {
    expect(EntityId.isValidEntityId('92233720368547758070')).toBe(false);
    expect(EntityId.isValidEntityId('-92233720368547758080')).toBe(false);
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
      entityIdStr: '0.0.274877906943',
      expected: EntityId.of(0, 0, 274877906943),
    },
    {
      entityIdStr: '1023.65535.274877906943',
      expected: EntityId.of(1023, 65535, 274877906943),
    },
    {
      entityIdStr: '0',
      expected: EntityId.of(shard, realm, 0),
    },
    {
      entityIdStr: '10',
      expected: EntityId.of(0, 0, 10),
    },
    {
      entityIdStr: '274877906943',
      expected: EntityId.of(0, 0, 274877906943),
    },
    {
      entityIdStr: '1377209665535',
      expected: EntityId.of(0, 5, 2820130815),
    },
    {
      entityIdStr: '5.1',
      expected: EntityId.of(shard, 5, 1),
    },
    {
      entityIdStr: '0x0000000000000000000000000000000000000001',
      options: {paramName: constants.filterKeys.FROM},
      expected: EntityId.of(shard, realm, 1),
    },
    {
      entityIdStr: '0000000000000000000000000000000000000001',
      options: {paramName: constants.filterKeys.CONTRACT_ID},
      expected: EntityId.of(shard, realm, 1),
    },
    {
      entityIdStr: '0x0000000100000000000000020000000000000003',
      options: {paramName: constants.filterKeys.FROM},
      expected: EntityId.of(shard, realm, null, '0000000100000000000000020000000000000003'),
    },
    {
      entityIdStr: '0x000003ff000000000000ffff0000003fffffffff',
      options: {paramName: constants.filterKeys.FROM},
      expected: EntityId.of(shard, realm, null, '000003ff000000000000ffff0000003fffffffff'),
    },
    {
      entityIdStr: `${shard}.${realm}.000000000000000000000000000000000186Fb1b`,
      expected: EntityId.of(shard, realm, 25623323),
    },
    {
      entityIdStr: `${shard + 1n}.${realm + 1n}.000000000000000000000000000000000186Fb1b`,
      expectErr: true,
    },
    {
      entityIdStr: `${realm}.000000000000000000000000000000000186Fb1b`,
      expected: EntityId.of(shard, realm, 25623323),
    },
    {
      entityIdStr: '1.000000000000000000000000000000000186Fb1b',
      expectErr: true,
    },
    {
      entityIdStr: '000000000000000000000000000000000186Fb1b',
      expected: EntityId.of(shard, realm, 25623323),
    },
    {
      entityIdStr: '0x000000000000000000000000000000000186Fb1b',
      options: {paramName: constants.filterKeys.FROM},
      expected: EntityId.of(shard, realm, 25623323),
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
      expected: EntityId.of(shard, realm, '0000000100000000000000020000000000000007'),
    },
    {
      entityIdStr: '0x0000000100000000000000020000000000000007',
      expected: EntityId.of(shard, realm, '0000000100000000000000020000000000000007'),
    },
    {
      entityIdStr: `${shard + 1n}.${shard + 1n}.0000000100000000000000020000000000000007`,
      expectErr: true,
    },
    {
      entityIdStr: '71eaa748d5252be68c1185588beca495459fdba4',
      expected: EntityId.of(shard, realm, null, '71eaa748d5252be68c1185588beca495459fdba4'),
    },
    {
      entityIdStr: '0x71eaa748d5252be68c1185588beca495459fdba4',
      expected: EntityId.of(shard, realm, null, '71eaa748d5252be68c1185588beca495459fdba4'),
    },
    {
      entityIdStr: `${shard + 1n}.${shard + 1n}.71eaa748d5252be68c1185588beca495459fdba4`,
      expectErr: true,
    },
    {
      entityIdStr: '-1',
      expected: EntityId.of(1023, 65535, 274877906943),
    },
    {
      entityIdStr: '9223372036854775807', // max encoded id
      expected: EntityId.of(511, 65535, 274877906943),
    },
    {
      entityIdStr: '-9223372036854775808', // min encoded id
      expected: EntityId.of(512, 0, 0),
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
        }).toThrow(InvalidArgumentError);
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
      expected: EntityId.of(shard, realm, 0),
    },
    {
      encodedId: 274877906943,
      expected: EntityId.of(0, 0, 274877906943),
    },
    {
      encodedId: 180146733873889290n,
      expected: EntityId.of(10, 10, 10),
    },
    {
      encodedId: BigInt(0),
      expected: EntityId.of(shard, realm, 0),
    },
    {
      encodedId: BigInt(274877906943),
      expected: EntityId.of(0, 0, 274877906943),
    },
    {
      encodedId: '274877906943',
      expected: EntityId.of(0, 0, 274877906943),
    },
    {
      encodedId: 9223372036854775807n,
      expected: EntityId.of(511, 65535, 274877906943),
    },
    {
      encodedId: -9223372036854775808n,
      expected: EntityId.of(512, 0, 0),
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
      encodedId: 9223372036854775808n, // max + 1
      expectErr: true,
    },
    {
      encodedId: -9223372036854775809n, // min - 1
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
    expect(EntityId.of(1, 2, 7).toEvmAddress()).toEqual('0x0000000000000000000000000000000000000007');
  });

  test('32767.65535.4294967295', () => {
    expect(EntityId.of(32767n, 65535n, 4294967295n).toEvmAddress()).toEqual(
      '0x00000000000000000000000000000000ffffffff'
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

  test('0.0.274877906943', () => {
    expect(EntityId.parse('0.0.274877906943').getEncodedId()).toBe(274877906943);
  });

  test('10.10.10', () => {
    expect(EntityId.parse('10.10.10').getEncodedId()).toBe(180146733873889290n);
  });

  test('0.32767.274877906943', () => {
    expect(EntityId.parse('0.32767.274877906943').getEncodedId()).toBe(Number.MAX_SAFE_INTEGER);
  });

  test('0.32768.274877906943', () => {
    expect(EntityId.parse('0.32768.274877906943').getEncodedId()).toBe(9007474132647935n);
  });

  test('32.0.0', () => {
    expect(EntityId.parse('32.0.0').getEncodedId()).toBe(2n ** 59n);
  });

  test('511.65535.274877906943', () => {
    expect(EntityId.parse('511.65535.274877906943').getEncodedId()).toBe(9223372036854775807n);
  });

  test('512.0.0', () => {
    expect(EntityId.parse('512.0.0').getEncodedId()).toBe(-9223372036854775808n);
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
      expected: {shard: shard, realm: realm, num: '500'},
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

describe('parseString', () => {
  const specs = [
    {
      input: '0.0.500',
      expected: EntityId.of(0, 0, 500),
    },
    {
      input: '500',
      expected: EntityId.of(shard, realm, 500),
    },
    {
      input: '4.500',
      expected: EntityId.of(shard, 4, 500),
    },
    {
      input: `${realm}.71eaa748d5252be68c1185588beca495459fdba4`,
      expected: EntityId.of(shard, realm, null, '71eaa748d5252be68c1185588beca495459fdba4'),
    },
    {
      input: `${shard}.${realm}.71eaa748d5252be68c1185588beca495459fdba4`,
      expected: EntityId.of(shard, realm, null, '71eaa748d5252be68c1185588beca495459fdba4'),
    },
    {
      input: `${shard + 1n}.${realm + 1n}.71eaa748d5252be68c1185588beca495459fdba4`,
      expectErr: true,
    },
    {
      input: '71eaa748d5252be68c1185588beca495459fdba4',
      expected: EntityId.of(shard, realm, null, '71eaa748d5252be68c1185588beca495459fdba4'),
    },
    {
      input: '0x71eaa748d5252be68c1185588beca495459fdba4',
      expected: EntityId.of(shard, realm, null, '71eaa748d5252be68c1185588beca495459fdba4'),
    },
    {
      input: null,
      expectErr: true,
    },
    {
      input: undefined,
      expectErr: true,
    },
    {
      input: 1,
      expectErr: true,
    },
    {
      input: '-1.1.1',
      expectErr: true,
    },
    {
      input: '0.0.0x71eaa748d5252be68c1185588beca495459fdba4',
      expectErr: true,
    },
  ];

  for (const spec of specs) {
    test(spec.input || `Invalid - ${spec.input}`, () => {
      if (!spec.expectErr) {
        expect(EntityId.parseString(spec.input)).toEqual(spec.expected);
      } else {
        expect(() => EntityId.parseString(spec.input)).toThrow(InvalidArgumentError);
      }
    });
  }
});
