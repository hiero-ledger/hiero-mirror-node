// SPDX-License-Identifier: Apache-2.0

import {CustomFee} from '../../model';
import {CustomFeeViewModel} from '../../viewmodel';

describe('CustomFeeViewModel', () => {
  const fixedFeeTestSpecs = [
    {
      name: 'HBAR',
      expectedDenominatingTokenId: null,
    },
    {
      name: 'token',
      dbDenominatingTokenId: 10012,
      expectedDenominatingTokenId: '0.0.10012',
    },
  ];

  fixedFeeTestSpecs.forEach((testSpec) => {
    test(`fixed fee in ${testSpec.name}`, () => {
      const input = new CustomFee({
        fixed_fees: [
          {
            all_collectors_are_exempt: true,
            amount: 15,
            collector_account_id: 8901,
            denominating_token_id: testSpec.dbDenominatingTokenId,
          },
        ],
      });

      const expected = {
        fixed_fees: [
          {
            all_collectors_are_exempt: true,
            amount: 15,
            collector_account_id: '0.0.8901',
            denominating_token_id: testSpec.expectedDenominatingTokenId,
          },
        ],
        fractional_fees: [],
        royalty_fees: [],
      };

      const actual = new CustomFeeViewModel(input);
      expect(actual).toEqual(expected);
    });
  });

  test('fractional fee', () => {
    const input = new CustomFee({
      fractional_fees: [
        {
          all_collectors_are_exempt: true,
          collector_account_id: 8901,
          denominator: 31,
          maximum_amount: 101,
          minimum_amount: 37,
          net_of_transfers: false,
          numerator: 15,
        },
      ],
      token_id: 10015,
    });

    const expected = {
      fixed_fees: [],
      fractional_fees: [
        {
          all_collectors_are_exempt: true,
          amount: {
            denominator: 31,
            numerator: 15,
          },
          collector_account_id: '0.0.8901',
          denominating_token_id: '0.0.10015',
          maximum: 101,
          minimum: 37,
          net_of_transfers: false,
        },
      ],
      royalty_fees: [],
    };

    const actual = new CustomFeeViewModel(input);
    expect(actual).toEqual(expected);
  });

  test('fractional fee no maximum', () => {
    const input = new CustomFee({
      fractional_fees: [
        {
          all_collectors_are_exempt: false,
          collector_account_id: 8901,
          denominator: 31,
          minimum_amount: 37,
          net_of_transfers: true,
          numerator: 15,
        },
      ],
      token_id: 10015,
    });

    const expected = {
      fixed_fees: [],
      fractional_fees: [
        {
          all_collectors_are_exempt: false,
          amount: {
            denominator: 31,
            numerator: 15,
          },
          collector_account_id: '0.0.8901',
          denominating_token_id: '0.0.10015',
          maximum: null,
          minimum: 37,
          net_of_transfers: true,
        },
      ],
      royalty_fees: [],
    };

    const actual = new CustomFeeViewModel(input);
    expect(actual).toEqual(expected);
  });

  test('royalty fee without fallback', () => {
    const input = new CustomFee({
      royalty_fees: [
        {
          all_collectors_are_exempt: true,
          collector_account_id: 8901,
          denominator: 31,
          numerator: 15,
        },
      ],
    });

    const expected = {
      fixed_fees: [],
      fractional_fees: [],
      royalty_fees: [
        {
          all_collectors_are_exempt: true,
          amount: {
            denominator: 31,
            numerator: 15,
          },
          collector_account_id: '0.0.8901',
          fallback_fee: null,
        },
      ],
    };

    const actual = new CustomFeeViewModel(input);
    expect(actual).toEqual(expected);
  });

  fixedFeeTestSpecs.forEach((testSpec) => {
    test(`royalty fee with fallback in ${testSpec.name}`, () => {
      const input = new CustomFee({
        royalty_fees: [
          {
            all_collectors_are_exempt: false,
            collector_account_id: 8901,
            denominator: 31,
            fallback_fee: {
              amount: 11,
              denominating_token_id: testSpec.dbDenominatingTokenId,
            },
            numerator: 15,
          },
        ],
      });

      const expected = {
        fixed_fees: [],
        fractional_fees: [],
        royalty_fees: [
          {
            all_collectors_are_exempt: false,
            amount: {
              denominator: 31,
              numerator: 15,
            },
            collector_account_id: '0.0.8901',
            fallback_fee: {
              amount: 11,
              denominating_token_id: testSpec.expectedDenominatingTokenId,
            },
          },
        ],
      };

      const actual = new CustomFeeViewModel(input);
      expect(actual).toEqual(expected);
    });
  });

  test('empty fee', () => {
    const input = new CustomFee({});

    const actual = new CustomFeeViewModel(input);

    expect(actual).toEqual({
      fixed_fees: [],
      fractional_fees: [],
      royalty_fees: [],
    });
  });
});
