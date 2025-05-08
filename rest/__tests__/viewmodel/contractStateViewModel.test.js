// SPDX-License-Identifier: Apache-2.0

import {ContractStateViewModel} from '../../viewmodel';

describe('ContractStateViewModel', () => {
  const defaultContractState = {
    contractId: 1500,
    modifiedTimestamp: 1651770056616171000,
    slot: Buffer.from([0x1]),
    value: Buffer.from([0x1]),
  };

  const defaultExpected = {
    contract_id: '0.0.1500',
    timestamp: '1651770056.616171000',
    address: '0x00000000000000000000000000000000000005dc',
    slot: '0x0000000000000000000000000000000000000000000000000000000000000001',
    value: '0x0000000000000000000000000000000000000000000000000000000000000001',
  };

  test('default', () => {
    expect(new ContractStateViewModel(defaultContractState)).toEqual(defaultExpected);
  });
});
