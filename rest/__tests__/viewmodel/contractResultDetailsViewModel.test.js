// SPDX-License-Identifier: Apache-2.0

import ContractResultDetailsViewModel from '../../viewmodel/contractResultDetailsViewModel.js';

describe('ContractResultDetailsViewModel', () => {
  describe('_convertWeibarToTinybar', () => {
    test('converts weibar hex string to tinybar BigInt (unsigned)', () => {
      // 860,000,000,000 weibar = 86 tinybar
      const weibarHex = 'c83bfe9800';
      const result = ContractResultDetailsViewModel._convertWeibarToTinybar(weibarHex, false);
      expect(result).toBe(86n);
    });

    test('converts weibar Buffer to tinybar BigInt (unsigned)', () => {
      // 12,500,000,000,000,000,000 weibar = 1,250,000,000 tinybar
      const weibarBuffer = Buffer.from('ad78ebc5ac620000', 'hex');
      const result = ContractResultDetailsViewModel._convertWeibarToTinybar(weibarBuffer, false);
      expect(result).toBe(1_250_000_000n);
    });

    test('converts with 0x prefix', () => {
      const weibarHex = '0xc83bfe9800';
      const result = ContractResultDetailsViewModel._convertWeibarToTinybar(weibarHex, false);
      expect(result).toBe(86n);
    });

    test('handles odd-length hex string by padding', () => {
      // 0x104c533c000 = 1,120,000,000,000 weibar = 112 tinybar
      const weibarHex = '104c533c000'; // 11 hex chars (odd)
      const result = ContractResultDetailsViewModel._convertWeibarToTinybar(weibarHex, false);
      expect(result).toBe(112n);
    });

    test('converts signed value (positive)', () => {
      // 200,000,000,000 weibar = 20 tinybar
      const weibarHex = '2e90edd000';
      const result = ContractResultDetailsViewModel._convertWeibarToTinybar(weibarHex, true);
      expect(result).toBe(20n);
    });

    test('converts signed value (negative)', () => {
      // Two's complement: -100,000,000,000,000 weibar = -10,000 tinybar
      const weibarBuffer = Buffer.from('ffffa50cef85c000', 'hex');
      const result = ContractResultDetailsViewModel._convertWeibarToTinybar(weibarBuffer, true);
      expect(result).toBe(-10000n);
    });

    test('handles zero', () => {
      const weibarHex = '00';
      const result = ContractResultDetailsViewModel._convertWeibarToTinybar(weibarHex, false);
      expect(result).toBe(0n);
    });

    test('returns null for empty buffer', () => {
      const result = ContractResultDetailsViewModel._convertWeibarToTinybar(Buffer.alloc(0), false);
      expect(result).toBeNull();
    });

    test('returns null for null input', () => {
      const result = ContractResultDetailsViewModel._convertWeibarToTinybar(null, false);
      expect(result).toBeNull();
    });

    test('handles very large value', () => {
      // 0xFFFFFFFFFFFFFFFF = 18,446,744,073,709,551,615 weibar
      const weibarHex = 'ffffffffffffffff';
      const result = ContractResultDetailsViewModel._convertWeibarToTinybar(weibarHex, false);
      expect(result).toBe(1_844_674_407n); // divided by 10 billion
    });

    test('truncates toward zero (not rounding)', () => {
      // 95 weibar / 10,000,000,000 = 0.0000000095 tinybar → truncates to 0
      const weibarHex = '5f';
      const result = ContractResultDetailsViewModel._convertWeibarToTinybar(weibarHex, false);
      expect(result).toBe(0n);
    });
  });

  describe('_convertWeibarBytesToHex', () => {
    test('converts weibar to tinybar hex string', () => {
      // 890,000,000,000 weibar = 89 tinybar = 0x59
      const weibarHex = 'cf38224400';
      const result = ContractResultDetailsViewModel._convertWeibarBytesToHex(weibarHex);
      expect(result).toBe('0x59');
    });

    test('converts to minimal hex representation', () => {
      // 510,000,000,000 weibar = 51 tinybar = 0x33
      const weibarHex = '76be5e6c00';
      const result = ContractResultDetailsViewModel._convertWeibarBytesToHex(weibarHex);
      expect(result).toBe('0x33');
    });

    test('handles zero value', () => {
      const weibarHex = '00';
      const result = ContractResultDetailsViewModel._convertWeibarBytesToHex(weibarHex);
      expect(result).toBe('0x0');
    });

    test('returns 0x for empty buffer', () => {
      const result = ContractResultDetailsViewModel._convertWeibarBytesToHex(Buffer.alloc(0));
      expect(result).toBe('0x');
    });

    test('returns 0x for null input', () => {
      const result = ContractResultDetailsViewModel._convertWeibarBytesToHex(null);
      expect(result).toBe('0x');
    });

    test('handles large value', () => {
      // 12,500,000,000,000,000,000 weibar = 1,250,000,000 tinybar = 0x4a817c80
      const weibarBuffer = Buffer.from('ad78ebc5ac620000', 'hex');
      const result = ContractResultDetailsViewModel._convertWeibarBytesToHex(weibarBuffer);
      expect(result).toBe('0x4a817c80');
    });
  });

  describe('constructor with convertToHbar parameter', () => {
    const mockContractResult = {
      amount: 20,
      bloom: Buffer.from([1, 1]),
      callResult: Buffer.from([2, 2]),
      consensusTimestamp: '187654000123456',
      contractId: 5001,
      createdContractIds: [],
      errorMessage: '',
      functionParameters: Buffer.from([3, 3]),
      functionResult: Buffer.from([4, 4]),
      gasConsumed: 987,
      gasLimit: 1234556,
      gasUsed: 987,
      payerAccountId: 5000,
      senderId: 6001,
      transactionHash: Buffer.from('185602030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f', 'hex'),
      transactionIndex: 1,
      transactionNonce: 0,
      transactionResult: 22,
    };

    const mockEthTransaction = {
      accessList: null,
      callData: null,
      chainId: '012a', // hex string from DB
      consensusTimestamp: '187654000123456',
      gasLimit: 1234556,
      gasPrice: 'ad78ebc5ac620000', // hex string: 1,250,000,000 tinybar in weibar
      hash: '185602030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f',
      maxFeePerGas: 'cf38224400', // hex string: 89 tinybar in weibar
      maxPriorityFeePerGas: '76be5e6c00', // hex string: 51 tinybar in weibar
      nonce: 5,
      payerAccountId: 5000,
      recoveryId: 1,
      signatureR: 'b5c21ab4dfd336e30ac2106cad4aa8888b1873a99bce35d50f64d2ec2cc5f6d9',
      signatureS: '1092806a99727a20c31836959133301b65a2bfa980f9795522d21a254e629110',
      signatureV: Buffer.from('1b', 'hex'),
      toAddress: '0000000000000000000000000000000000001389',
      type: 2,
      value: '2e90edd000', // hex string: 20 tinybar in weibar
    };

    const mockRecordFile = {
      gasUsed: 400000,
      hash: Buffer.from(
        'fbd921184e229e2051280d827ba3b31599117af7eafba65dc0e5a998b70c48c0492bf793a150769b1b4fb2c9b7cb4c1c',
        'hex'
      ),
      index: 10,
    };

    test('converts to tinybar when convertToHbar=true', () => {
      const viewModel = new ContractResultDetailsViewModel(
        mockContractResult,
        mockRecordFile,
        mockEthTransaction,
        [],
        [],
        null,
        true // convertToHbar
      );

      expect(viewModel.amount).toBe(20n); // Converted from weibar to tinybar
      expect(viewModel.gas_price).toBe('0x4a817c80'); // 1,250,000,000 tinybar
      expect(viewModel.max_fee_per_gas).toBe('0x59'); // 89 tinybar
      expect(viewModel.max_priority_fee_per_gas).toBe('0x33'); // 51 tinybar
    });

    test('returns raw weibar when convertToHbar=false', () => {
      const viewModel = new ContractResultDetailsViewModel(
        mockContractResult,
        mockRecordFile,
        mockEthTransaction,
        [],
        [],
        null,
        false // convertToHbar
      );

      // For hbar=false, amount comes from the value buffer directly
      // 0x2e90edd000 = 200,000,000,000
      expect(viewModel.amount).toBe(0x2e90edd000n); // Raw weibar value
      expect(viewModel.gas_price).toBe('0xad78ebc5ac620000'); // Raw weibar
      expect(viewModel.max_fee_per_gas).toBe('0xcf38224400'); // Raw weibar
      expect(viewModel.max_priority_fee_per_gas).toBe('0x76be5e6c00'); // Raw weibar
    });

    test('converts tinybar to weibar when convertToHbar=false and ethTransaction=null', () => {
      const viewModel = new ContractResultDetailsViewModel(
        mockContractResult, // amount: 20 tinybars
        mockRecordFile,
        null, // no ethTransaction
        [],
        [],
        null,
        false // convertToHbar
      );

      // 20 tinybars * 10,000,000,000 = 200,000,000,000 weibar
      expect(viewModel.amount).toBe(200_000_000_000n);
    });

    test('leaves amount as tinybar when convertToHbar=true and ethTransaction=null', () => {
      const viewModel = new ContractResultDetailsViewModel(
        mockContractResult, // amount: 20 tinybars
        mockRecordFile,
        null, // no ethTransaction
        [],
        [],
        null,
        true // convertToHbar
      );

      expect(viewModel.amount).toBe(20); // tinybars from contractResult.amount
    });

    test('defaults to convertToHbar=true when not specified', () => {
      const viewModel = new ContractResultDetailsViewModel(
        mockContractResult,
        mockRecordFile,
        mockEthTransaction,
        [],
        [],
        null
        // convertToHbar not specified, should default to true
      );

      expect(viewModel.amount).toBe(20n); // Converted from weibar to tinybar
      expect(viewModel.gas_price).toBe('0x4a817c80');
    });
  });
});
