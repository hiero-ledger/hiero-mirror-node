// SPDX-License-Identifier: Apache-2.0

import {proto} from '@hashgraph/proto';
import {CustomFeeLimitViewModel} from '../../viewmodel';
import {CustomFeeLimits} from '../../model';

describe('CustomFeeLimitViewModel', () => {
  test('formats max_custom_fees correctly', () => {
    // Construct the test input using protobuf encoding
    const testInput = [
      proto.CustomFeeLimit.encode({
        accountId: {shardNum: 0, realmNum: 0, accountNum: 8},
        fees: [{amount: 1000, denominatingTokenId: {shardNum: 0, realmNum: 0, tokenNum: 3001}}],
      }).finish(),

      proto.CustomFeeLimit.encode({
        accountId: {shardNum: 0, realmNum: 0, accountNum: 9},
        fees: [{amount: 500, denominatingTokenId: null}],
      }).finish(),
    ];

    const customFeeLimits = new CustomFeeLimits(testInput);

    const expected = new CustomFeeLimitViewModel({
      fees: [
        {
          accountId: {shardNum: 0, realmNum: 0, accountNum: 8},
          fixedFees: [{amount: 1000, denominatingTokenId: {shardNum: 0, realmNum: 0, tokenNum: 3001}}],
        },
        {
          accountId: {shardNum: 0, realmNum: 0, accountNum: 9},
          fixedFees: [{amount: 500, denominatingTokenId: null}],
        },
      ],
    });

    // Execute
    const actual = new CustomFeeLimitViewModel(customFeeLimits);

    // Expect the objects to be equal
    expect(actual).toEqual(expected);
  });

  test('handles empty fees array', () => {
    const input = new CustomFeeLimits([]);
    const expected = new CustomFeeLimitViewModel({fees: []});

    const actual = new CustomFeeLimitViewModel(input);
    expect(actual).toEqual(expected);
  });

  test('handles missing fees property', () => {
    const input = new CustomFeeLimits(undefined);
    const expected = new CustomFeeLimitViewModel({fees: []});

    const actual = new CustomFeeLimitViewModel(input);
    expect(actual).toEqual(expected);
  });
});
