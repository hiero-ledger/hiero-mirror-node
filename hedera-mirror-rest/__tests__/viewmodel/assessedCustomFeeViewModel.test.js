// SPDX-License-Identifier: Apache-2.0

import {AssessedCustomFee} from '../../model';
import {AssessedCustomFeeViewModel} from '../../viewmodel';

describe('AssessedCustomFeeViewModel', () => {
  const effectivePayersTestSpecs = [
    {
      name: 'empty effective payers',
      payersModel: [],
      expectedPayers: [],
    },
    {
      name: 'null empty effective payers',
      expectedPayers: [],
    },
    {
      name: 'non-empty effective payers',
      payersModel: [9000, 9001, 9002],
      expectedPayers: ['0.0.9000', '0.0.9001', '0.0.9002'],
    },
  ];

  effectivePayersTestSpecs.forEach((testSpec) => {
    test(`fee charged in hbar with ${testSpec.name}`, () => {
      const model = new AssessedCustomFee({
        amount: 13,
        collector_account_id: 8901,
        consensus_timestamp: '1',
        effective_payer_account_ids: testSpec.payersModel,
      });
      const expected = {
        amount: 13,
        collector_account_id: '0.0.8901',
        effective_payer_account_ids: testSpec.expectedPayers,
        token_id: null,
      };

      expect(new AssessedCustomFeeViewModel(model)).toEqual(expected);
    });

    test(`fee charged in token with ${testSpec.name}`, () => {
      const model = new AssessedCustomFee({
        amount: 13,
        collector_account_id: 8901,
        consensus_timestamp: '1',
        effective_payer_account_ids: testSpec.payersModel,
        token_id: 10013,
      });
      const expected = {
        amount: 13,
        collector_account_id: '0.0.8901',
        effective_payer_account_ids: testSpec.expectedPayers,
        token_id: '0.0.10013',
      };

      expect(new AssessedCustomFeeViewModel(model)).toEqual(expected);
    });
  });
});
