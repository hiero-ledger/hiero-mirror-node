// SPDX-License-Identifier: Apache-2.0

import {ContractResultViewModel} from '../../viewmodel';

describe('ContractResultViewModel', () => {
  const defaultContractResult = {
    amount: 10,
    bloom: Buffer.from([0x1, 0x2, 0x3, 0x4]),
    callResult: Buffer.from([0xa, 0xb, 0xc, 0xd]),
    consensusTimestamp: '900123456789',
    contractId: 1500,
    createdContractIds: [1501, 1502],
    errorMessage: 'unknown error',
    senderId: 1100,
    functionParameters: Buffer.from([0x1, 0x2, 0x3, 0x4]),
    gasLimit: 6000,
    gasUsed: 3500,
    transactionHash: Buffer.from([...Array(32).keys()]),
  };
  const defaultExpected = {
    address: '0x00000000000000000000000000000000000005dc',
    amount: 10,
    bloom: '0x01020304',
    call_result: '0x0a0b0c0d',
    contract_id: '0.0.1500',
    created_contract_ids: ['0.0.1501', '0.0.1502'],
    error_message: 'unknown error',
    from: '0x000000000000000000000000000000000000044c',
    function_parameters: '0x01020304',
    gas_limit: 6000,
    gas_used: 3500,
    hash: '0x000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f',
    timestamp: '900.123456789',
    to: '0x00000000000000000000000000000000000005dc',
  };

  test('default', () => {
    expect(new ContractResultViewModel(defaultContractResult)).toEqual(defaultExpected);
  });

  test.each`
    source      | bloom
    ${'array'}  | ${[]}
    ${'buffer'} | ${Buffer.alloc(0)}
  `('empty bloom from $source', ({source, bloom}) => {
    const input = {
      ...defaultContractResult,
      bloom,
    };
    const expected = {
      ...defaultExpected,
      bloom: `0x${'00'.repeat(256)}`,
    };
    expect(new ContractResultViewModel(input)).toEqual(expected);
  });

  test('null fields', () => {
    expect(
      new ContractResultViewModel({
        ...defaultContractResult,
        amount: null,
        bloom: null,
        callResult: null,
        contractId: null,
        createdContractIds: null,
        errorMessage: null,
        functionParameters: null,
        gasUsed: null,
        transactionHash: null,
      })
    ).toEqual({
      ...defaultExpected,
      address: null,
      amount: null,
      bloom: '0x',
      call_result: '0x',
      contract_id: null,
      created_contract_ids: [],
      error_message: null,
      function_parameters: '0x',
      gas_used: null,
      hash: '0x',
      to: null,
    });
  });
});
