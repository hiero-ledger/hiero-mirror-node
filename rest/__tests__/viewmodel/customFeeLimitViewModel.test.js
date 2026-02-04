// SPDX-License-Identifier: Apache-2.0

import {proto} from '@hiero-ledger/proto';
import {CustomFeeLimitsViewModel} from '../../viewmodel';
import {CustomFeeLimits} from '../../model';

describe('CustomFeeLimitViewModel', () => {
  test('formats max_custom_fees correctly', () => {
    // given
    const testInput = [
      proto.CustomFeeLimit.encode({
        accountId: {accountNum: 8},
        fees: [
          {amount: 1000, denominatingTokenId: {tokenNum: 3001}},
          {amount: 2000, denominatingTokenId: {}},
        ],
      }).finish(),
      proto.CustomFeeLimit.encode({
        accountId: {accountNum: 9},
        fees: [{amount: 500}],
      }).finish(),
    ];
    const expected = [
      {
        account_id: '0.0.8',
        amount: 1000n,
        denominating_token_id: '0.0.3001',
      },
      {
        account_id: '0.0.8',
        amount: 2000n,
        denominating_token_id: null,
      },
      {
        account_id: '0.0.9',
        amount: 500n,
        denominating_token_id: null,
      },
    ];

    // when
    const actual = new CustomFeeLimitsViewModel(new CustomFeeLimits(testInput));

    // then
    expect(actual.max_custom_fees).toEqual(expected);
  });

  test('handles empty fees array', () => {
    const input = new CustomFeeLimits([]);
    const actual = new CustomFeeLimitsViewModel(input);
    expect(actual.max_custom_fees).toBeEmpty();
  });

  test('handles missing fees property', () => {
    const input = new CustomFeeLimits(undefined);
    const actual = new CustomFeeLimitsViewModel(input);
    expect(actual.max_custom_fees).toBeEmpty();
  });
});
