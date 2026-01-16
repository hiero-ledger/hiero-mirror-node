// SPDX-License-Identifier: Apache-2.0

import subject from '../accounts';
import base32 from '../base32';
import * as constants from '../constants';

describe('processRow', () => {
  const inputAccount = {
    alias: base32.decode('WWDOGNX3TXHD2'),
    auto_renew_period: 7890000,
    balance: 123456789,
    balance_timestamp: 9876500123456789n,
    created_timestamp: 10123456789n,
    decline_reward: false,
    ethereum_nonce: 1,
    evm_address: Buffer.from('ac384c53f03855fa1b3616052f8ba32c6c2a2fec', 'hex'),
    deleted: false,
    expiration_timestamp: '999876500123456789',
    id: 1250,
    key: Buffer.from([1, 2, 3, 4, 5, 6]),
    max_automatic_token_associations: 100,
    memo: 'entity memo',
    pending_reward: 10,
    receiver_sig_required: false,
    staked_account_id: 0,
    staked_node_id: -1,
    stake_period_start: -1,
    token_balances: [
      {
        token_id: '1500',
        balance: 2000,
      },
    ],
    type: constants.entityTypes.ACCOUNT,
  };
  const inputContract = {
    ...inputAccount,
    alias: null,
    memo: 'contract memo',
    receiver_sig_required: null,
    type: constants.entityTypes.CONTRACT,
  };
  const expectedAccount = {
    account: '0.0.1250',
    alias: 'WWDOGNX3TXHD2',
    auto_renew_period: 7890000,
    balance: {
      balance: 123456789,
      timestamp: '9876500.123456789',
      tokens: [
        {
          token_id: '0.0.1500',
          balance: 2000,
        },
      ],
    },
    created_timestamp: '10.123456789',
    decline_reward: false,
    deleted: false,
    ethereum_nonce: 1,
    evm_address: '0xac384c53f03855fa1b3616052f8ba32c6c2a2fec',
    expiry_timestamp: '999876500.123456789',
    key: {
      _type: 'ProtobufEncoded',
      key: '010203040506',
    },
    max_automatic_token_associations: 100,
    memo: 'entity memo',
    pending_reward: 10,
    receiver_sig_required: false,
    staked_account_id: null,
    staked_node_id: null,
    stake_period_start: null,
  };
  const expectedContract = {
    ...expectedAccount,
    alias: null,
    memo: 'contract memo',
    receiver_sig_required: null,
  };

  test('with balance', () => {
    expect(subject.processRow(inputAccount)).toEqual(expectedAccount);
  });

  test('undefined balance', () => {
    const inputBalanceUndefined = {
      ...inputAccount,
      balance: undefined,
      balance_timestamp: undefined,
      token_balances: undefined,
    };
    const expectedNoBalance = {
      ...expectedAccount,
      balance: null,
    };
    expect(subject.processRow(inputBalanceUndefined)).toEqual(expectedNoBalance);
  });

  test('null balance', () => {
    const inputNullBalance = {
      ...inputAccount,
      balance: null,
      balance_timestamp: null,
      token_balances: null,
    };
    const expectedNullBalance = {
      ...expectedAccount,
      balance: {
        balance: null,
        timestamp: null,
        tokens: [],
      },
    };
    expect(subject.processRow(inputNullBalance)).toEqual(expectedNullBalance);
  });

  test('null auto_renew_period', () => {
    expect(subject.processRow({...inputAccount, auto_renew_period: null})).toEqual({
      ...expectedAccount,
      auto_renew_period: null,
    });
  });

  test('null key', () => {
    expect(subject.processRow({...inputAccount, key: null})).toEqual({...expectedAccount, key: null});
  });

  test('null alias', () => {
    expect(subject.processRow({...inputAccount, alias: null})).toEqual({...expectedAccount, alias: null});
  });

  test('staked account id', () => {
    expect(subject.processRow({...inputAccount, staked_account_id: 10})).toEqual({
      ...expectedAccount,
      staked_account_id: '0.0.10',
    });
  });

  test('staked account id and stake period start', () => {
    expect(subject.processRow({...inputAccount, staked_account_id: 10, stake_period_start: 30})).toEqual({
      ...expectedAccount,
      staked_account_id: '0.0.10',
    });
  });

  test('null staked account id', () => {
    expect(subject.processRow({...inputAccount, staked_account_id: null})).toEqual(expectedAccount);
  });

  test('staked node id', () => {
    expect(subject.processRow({...inputAccount, staked_node_id: 2, stake_period_start: 30})).toEqual({
      ...expectedAccount,
      staked_node_id: 2,
      stake_period_start: '2592000.000000000',
    });

    expect(subject.processRow({...inputAccount, staked_node_id: 2, stake_period_start: 19162})).toEqual({
      ...expectedAccount,
      staked_node_id: 2,
      stake_period_start: '1655596800.000000000',
    });

    expect(subject.processRow({...inputAccount, staked_node_id: 2, stake_period_start: -1})).toEqual({
      ...expectedAccount,
      staked_node_id: 2,
      stake_period_start: null,
    });
  });

  test('default contract', () => {
    expect(subject.processRow(inputContract)).toEqual(expectedContract);
  });

  test('contract with parsable evm address', () => {
    expect(subject.processRow({...inputContract, evm_address: null})).toEqual({
      ...expectedContract,
      evm_address: '0x00000000000000000000000000000000000004e2',
    });
  });

  test('null created_timestamp', () => {
    expect(subject.processRow({...inputAccount, created_timestamp: null})).toEqual({
      ...expectedAccount,
      created_timestamp: null,
    });
  });
});

describe('getBalanceParamValue', () => {
  const key = constants.filterKeys.BALANCE;
  test('default', () => {
    expect(subject.getBalanceParamValue({})).toBeTrue();
  });
  test('single value true', () => {
    expect(subject.getBalanceParamValue({[key]: 'true'})).toBeTrue();
  });
  test('single value false', () => {
    expect(subject.getBalanceParamValue({[key]: 'false'})).toBeFalse();
  });
  test('array last value true', () => {
    expect(subject.getBalanceParamValue({[key]: ['false', 'true']})).toBeTrue();
  });
  test('array last value false', () => {
    expect(subject.getBalanceParamValue({[key]: ['true', 'false']})).toBeFalse();
  });
});
