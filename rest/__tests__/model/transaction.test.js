// SPDX-License-Identifier: Apache-2.0

import Transaction from '../../model/transaction';

describe('Transaction', () => {
  describe('constructor', () => {
    test('parses all fields including highVolume', () => {
      const input = {
        batch_key: 'batchKey123',
        charged_tx_fee: 100,
        consensus_timestamp: '1234567890000000001',
        entity_id: 1001,
        high_volume: true,
        initial_balance: 5000,
        inner_transactions: null,
        max_custom_fees: null,
        max_fee: 1000000,
        memo: 'test memo',
        nft_transfer: [],
        node_account_id: 3,
        nonce: 0,
        parent_consensus_timestamp: null,
        payer_account_id: 1000,
        result: 22,
        scheduled: false,
        transaction_hash: 'hash123',
        transaction_bytes: 'bytes123',
        type: 14,
        valid_duration_seconds: 120,
        valid_start_ns: '1234567890000000000',
        index: 1,
      };

      const transaction = new Transaction(input);

      expect(transaction.highVolume).toBe(true);
      expect(transaction.batchKey).toBe('batchKey123');
      expect(transaction.chargedTxFee).toBe(100);
      expect(transaction.consensusTimestamp).toBe('1234567890000000001');
      expect(transaction.entityId).toBe(1001);
      expect(transaction.scheduled).toBe(false);
    });

    test('handles highVolume false', () => {
      const input = {
        high_volume: false,
        nft_transfer: [],
      };

      const transaction = new Transaction(input);

      expect(transaction.highVolume).toBe(false);
    });

    test('handles highVolume null/undefined', () => {
      const input = {
        high_volume: null,
        nft_transfer: [],
      };

      const transaction = new Transaction(input);

      expect(transaction.highVolume).toBeNull();
    });
  });

  describe('static constants', () => {
    test('HIGH_VOLUME constant is defined', () => {
      expect(Transaction.HIGH_VOLUME).toBe('high_volume');
    });
  });
});
