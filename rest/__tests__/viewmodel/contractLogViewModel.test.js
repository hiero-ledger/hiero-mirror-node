// SPDX-License-Identifier: Apache-2.0

import {ContractLogViewModel} from '../../viewmodel';

describe('ContractLogViewModel', () => {
  const hexArray = Array(18).fill(0x00).concat(0x12, 0x34);
  const longHexArray = Array(40).fill(0x11).concat(0x12, 0x34, 0x56);
  const defaultContractLog = {
    bloom: Buffer.from([0xa, 0xb]),
    contractId: '1',
    data: Buffer.from(hexArray),
    consensusTimestamp: '99999999000000000',
    index: 6,
    rootContractId: '2',
    topic0: Buffer.from([0x01, 0x01]),
    topic1: Buffer.from([0x01, 0x02]),
    topic2: Buffer.from([0x01, 0x03]),
    topic3: Buffer.from([0x01, 0x04]),
    transactionHash: Buffer.from(hexArray),
    transactionIndex: 1,
    blockHash: '0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000abcd',
    blockNumber: 100,
  };
  const defaultExpected = {
    address: '0x0000000000000000000000000000000000000001',
    bloom: '0x0a0b',
    contract_id: '0.0.1',
    data: '0x0000000000000000000000000000000000000000000000000000000000001234',
    index: 6,
    root_contract_id: '0.0.2',
    timestamp: '99999999.000000000',
    topics: [
      '0x0000000000000000000000000000000000000000000000000000000000000101',
      '0x0000000000000000000000000000000000000000000000000000000000000102',
      '0x0000000000000000000000000000000000000000000000000000000000000103',
      '0x0000000000000000000000000000000000000000000000000000000000000104',
    ],
    transaction_hash: '0x0000000000000000000000000000000000001234',
    transaction_index: 1,
    block_hash: '0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000abcd',
    block_number: 100,
  };

  test('ContractLogViewModel - default', () => {
    expect(new ContractLogViewModel(defaultContractLog)).toEqual(defaultExpected);
  });

  test('ContractLogViewModel - long data field', () => {
    const expectedDataField =
      '0x11111111111111111111111111111111111111111111111111111111111111111111111111111111123456';
    expect(new ContractLogViewModel({...defaultContractLog, data: Buffer.from(longHexArray)})).toEqual({
      ...defaultExpected,
      data: expectedDataField,
    });
  });

  test('ContractLogViewModel - no topics', () => {
    expect(
      new ContractLogViewModel({
        ...defaultContractLog,
        topic0: null,
        topic1: null,
        topic2: null,
        topic3: null,
      })
    ).toEqual({
      ...defaultExpected,
      topics: [],
    });
  });

  test('ContractLogViewModel - no root contract id', () => {
    expect(
      new ContractLogViewModel({
        ...defaultContractLog,
        rootContractId: null,
      })
    ).toEqual({
      ...defaultExpected,
      root_contract_id: null,
    });
  });

  test('ContractLogViewModel - empty topic buffer is replaced with ZERO_UINT256', () => {
    expect(
      new ContractLogViewModel({
        ...defaultContractLog,
        topic0: Buffer.alloc(0), // empty buffer
        topic1: null,
        topic2: Buffer.alloc(0), // empty buffer
        topic3: null,
      })
    ).toEqual({
      ...defaultExpected,
      topics: [
        '0x0000000000000000000000000000000000000000000000000000000000000000',
        '0x0000000000000000000000000000000000000000000000000000000000000000',
      ],
    });
  });
});
