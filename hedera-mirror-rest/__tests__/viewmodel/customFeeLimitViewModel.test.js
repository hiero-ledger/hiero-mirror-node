/*
 * Copyright (C) 2021-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import CustomFeeLimitViewModel from '../../viewmodel/CustomFeeLimitViewModel';

describe('CustomFeeLimitViewModel', () => {
  const testCases = [
    {
      name: 'Single fixed fee',
      input: [
        {
          accountId: '0.0.1234',
          fixedFees: [{amount: 100, denominatingTokenId: '0.0.5678'}],
        },
      ],
      expected: {
        max_custom_fees: [
          {
            account_id: '0.0.1234',
            amount: 100,
            denominating_token_id: '0.0.5678',
          },
        ],
      },
    },
    {
      name: 'Multiple fixed fees',
      input: [
        {
          accountId: '0.0.1234',
          fixedFees: [{amount: 100, denominatingTokenId: '0.0.5678'}],
        },
        {
          accountId: '0.0.4321',
          fixedFees: [{amount: 200, denominatingTokenId: null}],
        },
      ],
      expected: {
        max_custom_fees: [
          {
            account_id: '0.0.1234',
            amount: 100,
            denominating_token_id: '0.0.5678',
          },
          {
            account_id: '0.0.4321',
            amount: 200,
            denominating_token_id: null,
          },
        ],
      },
    },
    {
      name: 'Fixed fee with missing fields',
      input: [
        {
          accountId: '0.0.5555',
          fixedFees: [{}],
        },
      ],
      expected: {
        max_custom_fees: [
          {
            account_id: '0.0.5555',
            amount: 0, // Default value when amount is missing
            denominating_token_id: null, // Default value when token ID is missing
          },
        ],
      },
    },
    {
      name: 'Empty input',
      input: [],
      expected: {
        max_custom_fees: [],
      },
    },
  ];

  testCases.forEach(({name, input, expected}) => {
    test(name, () => {
      const actual = new CustomFeeLimitViewModel(input);
      expect(actual).toEqual(expected);
    });
  });
});
