// SPDX-License-Identifier: Apache-2.0

import {FeeScheduleViewModel} from '../../viewmodel/index.js';

describe('FeeScheduleViewModel', () => {
  const exchangeRate = {
    current_cent: 450041,
    current_expiration: 1651762800,
    current_hbar: 30000,
    next_cent: 435305,
    next_expiration: 1651766400,
    next_hbar: 30000,
    timestamp: 1653644164060000000,
  };
  const txFees = {
    EthereumTransaction: {
      fees: [{servicedata: {gas: {toNumber: () => 853000}}}],
      hederaFunctionality: 84,
    },
    ContractCall: {
      fees: [{servicedata: {gas: {toNumber: () => 741000}}}],
      hederaFunctionality: 6,
    },
    ContractCreate: {
      fees: [{servicedata: {gas: {toNumber: () => 990000}}}],
      hederaFunctionality: 7,
    },
  };
  const feeSchedule = {
    timestamp: 1653644164060000000,
    current_feeSchedule: Object.values(txFees),
    next_feeSchedule: [],
  };

  const feesResult = {
    EthereumTransaction: {
      gas: 56n,
      transaction_type: 'EthereumTransaction',
    },
    ContractCreate: {
      gas: 65n,
      transaction_type: 'ContractCreate',
    },
    ContractCall: {
      gas: 49n,
      transaction_type: 'ContractCall',
    },
  };

  test('default asc', () => {
    expect(new FeeScheduleViewModel(feeSchedule, exchangeRate, 'asc')).toEqual({
      fees: [feesResult.ContractCall, feesResult.ContractCreate, feesResult.EthereumTransaction],
      timestamp: '1653644164.060000000',
    });
  });

  test('default desc', () => {
    expect(new FeeScheduleViewModel(feeSchedule, exchangeRate, 'desc')).toEqual({
      fees: [feesResult.EthereumTransaction, feesResult.ContractCreate, feesResult.ContractCall],
      timestamp: '1653644164.060000000',
    });
  });

  test('EthereumTransaction has no fees prop', () => {
    const EthereumTransaction = {...txFees.EthereumTransaction};
    delete EthereumTransaction.fees[0];

    expect(
      new FeeScheduleViewModel(
        {
          ...feeSchedule,
          current_feeSchedule: [EthereumTransaction, txFees.ContractCall, txFees.ContractCreate],
        },
        exchangeRate,
        'desc'
      )
    ).toEqual({
      fees: [feesResult.ContractCreate, feesResult.ContractCall],
      timestamp: '1653644164.060000000',
    });
  });
});
