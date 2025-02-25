// SPDX-License-Identifier: Apache-2.0

import {TokenFreezeStatus, TokenKycStatus} from '../../model';
import {TokenRelationshipViewModel} from '../../viewmodel';

describe('TokenRelationshipViewModel', () => {
  const specs = [
    {
      name: 'simple',
      input: {
        automaticAssociation: false,
        balance: 10,
        createdTimestamp: 100111222333,
        decimals: 2,
        freezeStatus: 0,
        kycStatus: 0,
        tokenId: 100,
      },
      expected: {
        automatic_association: false,
        balance: 10,
        created_timestamp: '100.111222333',
        decimals: 2,
        freeze_status: new TokenFreezeStatus(0),
        kyc_status: new TokenKycStatus(0),
        token_id: '0.0.100',
      },
    },
    {
      name: 'nullable fields',
      input: {
        automaticAssociation: null,
        balance: 0,
        createdTimestamp: null,
        decimals: null,
        freezeStatus: null,
        kycStatus: null,
        tokenId: 100,
      },
      expected: {
        automatic_association: null,
        balance: 0,
        created_timestamp: null,
        decimals: null,
        freeze_status: null,
        kyc_status: null,
        token_id: '0.0.100',
      },
    },
  ];

  test.each(specs)('$name', ({input, expected}) => {
    expect(new TokenRelationshipViewModel(input)).toEqual(expected);
  });
});
