// SPDX-License-Identifier: Apache-2.0

import {create, toBinary} from '@bufbuild/protobuf';
import {AccountIDSchema, TokenIDSchema} from '../../gen/services/basic_types_pb.js';
import {CustomFeeLimitSchema, FixedFeeSchema} from '../../gen/services/custom_fees_pb.js';
import {CustomFeeLimitsViewModel} from '../../viewmodel';
import {CustomFeeLimits} from '../../model';

describe('CustomFeeLimitViewModel', () => {
  test('formats max_custom_fees correctly', () => {
    // given
    const testInput = [
      Buffer.from(
        toBinary(
          CustomFeeLimitSchema,
          create(CustomFeeLimitSchema, {
            accountId: create(AccountIDSchema, {
              account: {case: 'accountNum', value: 8n},
            }),
            fees: [
              create(FixedFeeSchema, {
                amount: 1000n,
                denominatingTokenId: create(TokenIDSchema, {
                  tokenNum: 3001n,
                }),
              }),
              create(FixedFeeSchema, {amount: 2000n}),
            ],
          })
        )
      ),
      Buffer.from(
        toBinary(
          CustomFeeLimitSchema,
          create(CustomFeeLimitSchema, {
            accountId: create(AccountIDSchema, {
              account: {case: 'accountNum', value: 9n},
            }),
            fees: [create(FixedFeeSchema, {amount: 500n})],
          })
        )
      ),
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
