// SPDX-License-Identifier: Apache-2.0

import {ContractActionViewModel} from '../../viewmodel';

describe('ContractActionViewModel', () => {
  const defaultContractAction = {
    callDepth: 0,
    callOperationType: 1,
    callType: 1,
    caller: 7,
    callerType: 'ACCOUNT',
    consensusTimestamp: 900123456789,
    gas: 6000,
    gasUsed: 3500,
    index: 0,
    input: Buffer.from([0x10, 0x11, 0x12, 0x13]),
    recipientAccount: 9,
    recipientAddress: null,
    recipientContract: null,
    resultData: Buffer.from([0x20, 0x21, 0x22, 0x23]),
    resultDataType: 11,
    value: 8000,
  };

  const defaultExpected = {
    call_depth: 0,
    call_operation_type: 'CALL',
    call_type: 'CALL',
    caller: '0.0.7',
    caller_type: 'ACCOUNT',
    from: '0x0000000000000000000000000000000000000007',
    gas: 6000,
    gas_used: 3500,
    index: 0,
    input: '0x10111213',
    recipient: '0.0.9',
    recipient_type: 'ACCOUNT',
    result_data: '0x20212223',
    result_data_type: 'OUTPUT',
    timestamp: '900.123456789',
    to: '0x0000000000000000000000000000000000000009',
    value: 8000,
  };

  test('default', () => {
    expect(new ContractActionViewModel(defaultContractAction)).toEqual(defaultExpected);
  });

  test('recipient contract', () => {
    expect(
      new ContractActionViewModel({
        ...defaultContractAction,
        recipientAccount: null,
        recipientContract: 9,
      })
    ).toEqual({
      ...defaultExpected,
      recipient_type: 'CONTRACT',
    });
  });

  test('recipient address', () => {
    expect(
      new ContractActionViewModel({
        ...defaultContractAction,
        recipientAccount: null,
        recipientAddress: Buffer.from([0xab, 0xcd, 0xef]),
      })
    ).toEqual({
      ...defaultExpected,
      recipient: null,
      recipient_type: null,
      to: '0x0000000000000000000000000000000000abcdef',
    });
  });

  test('null fields', () => {
    expect(
      new ContractActionViewModel({
        ...defaultContractAction,
        callOperationType: 5,
        callType: 2,
        recipientAccount: null,
      })
    ).toEqual({
      ...defaultExpected,
      call_operation_type: 'CREATE',
      call_type: 'CREATE',
      recipient: null,
      recipient_type: null,
      to: null,
    });
  });
});
