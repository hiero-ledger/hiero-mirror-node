// SPDX-License-Identifier: Apache-2.0

import {Range} from 'pg-range';
import {ContractBytecodeViewModel, ContractViewModel} from '../../viewmodel';

const defaultContract = {
  fileId: 2800n,
  id: 3001n,
};
const defaultEntity = {
  autoRenewAccountId: 2009n,
  autoRenewPeriod: 1000n,
  createdTimestamp: 999123456789n,
  deleted: false,
  evmAddress: Buffer.from([
    0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f, 0x20, 0x21, 0x22,
    0x23,
  ]),
  expirationTimestamp: 99999999000000000n,
  id: 3001n,
  key: Buffer.from([0xaa, 0xbb, 0xcc, 0x77]),
  memo: 'sample contract',
  obtainerId: 2005n,
  permanentRemoval: null,
  proxyAccountId: 2002n,
  timestampRange: Range('1000123456789', '2000123456789', '[)'),
};
const defaultExpected = {
  admin_key: {
    _type: 'ProtobufEncoded',
    key: 'aabbcc77',
  },
  auto_renew_account: '0.0.2009',
  auto_renew_period: 1000n,
  contract_id: '0.0.3001',
  created_timestamp: '999.123456789',
  deleted: false,
  evm_address: '0x101112131415161718191a1b1c1d1e1f20212223',
  expiration_timestamp: '99999999.000000000',
  file_id: '0.0.2800',
  memo: 'sample contract',
  obtainer_id: '0.0.2005',
  permanent_removal: null,
  proxy_account_id: '0.0.2002',
  timestamp: {
    from: '1000.123456789',
    to: '2000.123456789',
  },
};
describe('ContractViewModel', () => {
  test('default', () => {
    expect(new ContractViewModel(defaultContract, defaultEntity)).toEqual(defaultExpected);
  });

  test('null auto renew account', () => {
    expect(
      new ContractViewModel(defaultContract, {
        ...defaultEntity,
        autoRenewAccountId: null,
      })
    ).toEqual({
      ...defaultExpected,
      auto_renew_account: null,
    });
  });

  test('0 auto renew account', () => {
    expect(
      new ContractViewModel(defaultContract, {
        ...defaultEntity,
        autoRenewAccountId: 0,
      })
    ).toEqual({
      ...defaultExpected,
      auto_renew_account: null,
    });
  });

  test('null evm address', () => {
    expect(
      new ContractViewModel(defaultContract, {
        ...defaultEntity,
        evmAddress: null,
      })
    ).toEqual({
      ...defaultExpected,
      evm_address: '0x0000000000000000000000000000000000000bb9',
    });
  });

  test('permanent removal', () => {
    expect(
      new ContractViewModel(defaultContract, {
        ...defaultEntity,
        deleted: true,
        permanentRemoval: false,
      })
    ).toEqual({
      ...defaultExpected,
      deleted: true,
      permanent_removal: false,
    });
  });

  test('open-ended timestamp range', () => {
    expect(
      new ContractViewModel(defaultContract, {
        ...defaultEntity,
        timestampRange: Range('1000123456789', null, '[)'),
      })
    ).toEqual({
      ...defaultExpected,
      timestamp: {
        from: '1000.123456789',
        to: null,
      },
    });
  });
});

describe('ContractBytecodeViewModel', function () {
  test('default', () => {
    expect(new ContractBytecodeViewModel(defaultContract, defaultEntity)).toEqual({
      ...defaultExpected,
      bytecode: '0x',
      runtime_bytecode: '0x',
    });
  });

  test('bytecode', () => {
    expect(
      new ContractBytecodeViewModel(
        {
          ...defaultContract,
          bytecode: Buffer.from(
            '6080604052348015600f57600080fd5b5060405160c838038060c8833981016040819052602a91604e565b600080546001600160a01b0319166001600160a01b0392909216919091179055607c565b600060208284031215605f57600080fd5b81516001600160a01b0381168114607557600080fd5b9392505050565b603f8060896000396000f3fe6080604052600080fdfea2646970667358221220cef90c1de1bfe76c35095b7f0d6a94d1ca71f144f8da70eb9816329ab76eda3564736f6c634300080a0033',
            'utf8'
          ),
          runtimeBytecode: Buffer.from('60806040', 'utf8'),
        },
        defaultEntity
      )
    ).toEqual({
      ...defaultExpected,
      bytecode:
        '0x6080604052348015600f57600080fd5b5060405160c838038060c8833981016040819052602a91604e565b600080546001600160a01b0319166001600160a01b0392909216919091179055607c565b600060208284031215605f57600080fd5b81516001600160a01b0381168114607557600080fd5b9392505050565b603f8060896000396000f3fe6080604052600080fdfea2646970667358221220cef90c1de1bfe76c35095b7f0d6a94d1ca71f144f8da70eb9816329ab76eda3564736f6c634300080a0033',
      runtime_bytecode: '0x3630383036303430',
    });
  });

  test('null bytecode', () => {
    expect(
      new ContractBytecodeViewModel(
        {
          ...defaultContract,
          bytecode: null,
          runtimeBytecode: null,
        },
        defaultEntity
      )
    ).toEqual({...defaultExpected, bytecode: '0x', runtime_bytecode: '0x'});
  });

  test('bytecode with 0x prefix', () => {
    expect(
      new ContractBytecodeViewModel(
        {
          ...defaultContract,
          bytecode: Buffer.from(
            '0x6080604052348015600f57600080fd5b5060405160c838038060c8833981016040819052602a91604e565b600080546001600160a01b0319166001600160a01b0392909216919091179055607c565b600060208284031215605f57600080fd5b81516001600160a01b0381168114607557600080fd5b9392505050565b603f8060896000396000f3fe6080604052600080fdfea2646970667358221220cef90c1de1bfe76c35095b7f0d6a94d1ca71f144f8da70eb9816329ab76eda3564736f6c634300080a0033',
            'utf8'
          ),
          runtimeBytecode: Buffer.from('0x60806040', 'utf8'),
        },
        defaultEntity
      )
    ).toEqual({
      ...defaultExpected,
      bytecode:
        '0x6080604052348015600f57600080fd5b5060405160c838038060c8833981016040819052602a91604e565b600080546001600160a01b0319166001600160a01b0392909216919091179055607c565b600060208284031215605f57600080fd5b81516001600160a01b0381168114607557600080fd5b9392505050565b603f8060896000396000f3fe6080604052600080fdfea2646970667358221220cef90c1de1bfe76c35095b7f0d6a94d1ca71f144f8da70eb9816329ab76eda3564736f6c634300080a0033',
      runtime_bytecode: '0x30783630383036303430',
    });
  });
});
