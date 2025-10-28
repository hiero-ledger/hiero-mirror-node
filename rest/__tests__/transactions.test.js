// SPDX-License-Identifier: Apache-2.0

import config from '../config';
import * as constants from '../constants';
import * as testutils from './testutils';
import subject from '../transactions';
import * as utils from '../utils';
import {Transaction} from '../model/';
import EntityId from '../entityId';

const {
  query: {
    maxRecordFileCloseIntervalNs,
    maxScheduledTransactionConsensusTimestampRangeNs,
    maxTransactionConsensusTimestampRangeNs,
  },
} = config;

const {
  buildWhereClause,
  convertStakingRewardTransfers,
  createAssessedCustomFeeList,
  createCryptoTransferList,
  createNftTransferList,
  extractSqlFromTransactionsByIdOrHashRequest,
  extractSqlFromTransactionsRequest,
  formatTransactionRows,
  getStakingRewardTimestamps,
  getTransactionsByIdOrHashCacheControlHeader,
  getTransactionsByTransactionIdsSql,
  isValidTransactionHash,
  mayMissLongTermScheduledTransaction,
} = subject;

describe('buildWhereClause', () => {
  const testSpecs = [
    {
      conditions: [],
      expected: '',
    },
    {
      conditions: [''],
      expected: '',
    },
    {
      conditions: ['a>?'],
      expected: 'where a>?',
    },
    {
      conditions: ['a>?', 'b<?', 'c<>?'],
      expected: 'where a>? and b<? and c<>?',
    },
    {
      conditions: ['a>?', '', 'b<?', 'c<>?', ''],
      expected: 'where a>? and b<? and c<>?',
    },
  ];

  testSpecs.forEach((testSpec) => {
    const {conditions, expected} = testSpec;
    test(utils.JSONStringify(conditions), () => {
      const whereClause = buildWhereClause(...conditions);
      expect(whereClause.toLowerCase()).toEqual(expected);
    });
  });
});

describe('createAssessedCustomFeeList', () => {
  test('From null', () => {
    expect(createAssessedCustomFeeList(null)).toEqual(undefined);
  });

  test('From undefined', () => {
    expect(createAssessedCustomFeeList(undefined)).toEqual(undefined);
  });

  test('Simple createAssessedCustomFeeList', () => {
    const rowsFromDb = [
      {
        amount: 8,
        collector_account_id: 9901,
        effective_payer_account_ids: [],
        token_id: null,
      },
      {
        amount: 9,
        collector_account_id: 9901,
        effective_payer_account_ids: [7000],
        token_id: 10001,
      },
      {
        amount: 29,
        collector_account_id: 9902,
        effective_payer_account_ids: [7000, 7001],
        token_id: 10002,
      },
    ];
    const expected = [
      {
        amount: 8,
        collector_account_id: '0.0.9901',
        effective_payer_account_ids: [],
        token_id: null,
      },
      {
        amount: 9,
        collector_account_id: '0.0.9901',
        effective_payer_account_ids: ['0.0.7000'],
        token_id: '0.0.10001',
      },
      {
        amount: 29,
        collector_account_id: '0.0.9902',
        effective_payer_account_ids: ['0.0.7000', '0.0.7001'],
        token_id: '0.0.10002',
      },
    ];

    expect(createAssessedCustomFeeList(rowsFromDb)).toEqual(expected);
  });
});

describe('createCryptoTransferList', () => {
  test('From null', () => {
    expect(createCryptoTransferList(null)).toEqual([]);
  });

  test('From undefined', () => {
    expect(createCryptoTransferList(undefined)).toEqual([]);
  });

  test('Simple createCryptoTransferList', () => {
    const rowsFromDb = [
      {
        amount: 8,
        entity_id: 3,
        is_approval: null,
      },
      {
        amount: -27,
        entity_id: 9001,
        is_approval: true,
      },
      {
        amount: 19,
        entity_id: 98,
        is_approval: true,
      },
    ];
    const expected = [
      {
        account: '0.0.3',
        amount: 8,
        is_approval: false,
      },
      {
        account: '0.0.9001',
        amount: -27,
        is_approval: true,
      },
      {
        account: '0.0.98',
        amount: 19,
        is_approval: true,
      },
    ];

    expect(createCryptoTransferList(rowsFromDb)).toEqual(expected);
  });
});

describe('createNftTransferList', () => {
  test('From null', () => {
    expect(createNftTransferList(null)).toEqual([]);
  });

  test('From undefined', () => {
    expect(createNftTransferList(undefined)).toEqual([]);
  });

  test('Simple createNftTransferList', () => {
    const rowsFromDb = [
      {
        consensus_timestamp: 1,
        receiver_account_id: 1000,
        sender_account_id: 98,
        serial_number: 1,
        token_id: 2000,
        is_approval: null,
      },
      {
        consensus_timestamp: 10,
        receiver_account_id: 1005,
        sender_account_id: 98,
        serial_number: 2,
        token_id: 2000,
        is_approval: false,
      },
      {
        consensus_timestamp: 100,
        receiver_account_id: 98,
        sender_account_id: 1005,
        serial_number: 2,
        token_id: 2000,
        is_approval: true,
      },
    ];

    const expectedFormat = [
      {
        receiver_account_id: '0.0.1000',
        sender_account_id: '0.0.98',
        serial_number: 1,
        token_id: '0.0.2000',
        is_approval: false,
      },
      {
        receiver_account_id: '0.0.1005',
        sender_account_id: '0.0.98',
        serial_number: 2,
        token_id: '0.0.2000',
        is_approval: false,
      },
      {
        receiver_account_id: '0.0.98',
        sender_account_id: '0.0.1005',
        serial_number: 2,
        token_id: '0.0.2000',
        is_approval: true,
      },
    ];

    expect(createNftTransferList(rowsFromDb)).toEqual(expectedFormat);
  });
});

describe('formatTransactionRows', () => {
  test('Simple nftTransferList', async () => {
    const nftTransfersFromDb = [
      {
        consensus_timestamp: 1,
        receiver_account_id: 1000,
        sender_account_id: 98,
        serial_number: 1,
        token_id: 2000,
        is_approval: null,
      },
      {
        consensus_timestamp: 10,
        receiver_account_id: 1005,
        sender_account_id: 98,
        serial_number: 2,
        token_id: 2000,
        is_approval: true,
      },
      {
        consensus_timestamp: 100,
        receiver_account_id: 98,
        sender_account_id: 1005,
        serial_number: 2,
        token_id: 2000,
        is_approval: true,
      },
    ];

    const transactionsFromDb = [
      {
        consensus_timestamp: 1,
        entity_id: 98,
        memo: null,
        charged_tx_fee: 5,
        max_custom_fees: [],
        max_fee: 33,
        nonce: 0,
        parent_consensus_timestamp: null,
        token_transfers: [],
        transfers: [],
        result: 22,
        scheduled: false,
        transaction_hash: 'hash',
        type: 14,
        valid_start_ns: 1623787159737799966n,
        transaction_bytes: 'bytes',
        node_account_id: 2,
        payer_account_id: 3,
        crypto_transfer_list: [{amount: 100, entity_id: 98, is_approval: true}],
        nft_transfer: nftTransfersFromDb,
      },
      {
        consensus_timestamp: 2,
        entity_id: 100,
        memo: null,
        charged_tx_fee: 5,
        max_custom_fees: [],
        max_fee: 33,
        nonce: 1,
        parent_consensus_timestamp: 1,
        token_transfers: [],
        transfers: [],
        result: 22,
        scheduled: false,
        transaction_hash: 'hash',
        type: 14,
        valid_start_ns: 1623787159737799966n,
        transaction_bytes: 'bytes',
        node_account_id: 2,
        payer_account_id: 3,
        crypto_transfer_list: [{amount: 100, entity_id: 100, is_approval: true}],
        nft_transfer: [],
      },
    ];

    const expectedNftTransfers = [
      {
        receiver_account_id: '0.0.1000',
        sender_account_id: '0.0.98',
        serial_number: 1,
        token_id: '0.0.2000',
        is_approval: false,
      },
      {
        receiver_account_id: '0.0.1005',
        sender_account_id: '0.0.98',
        serial_number: 2,
        token_id: '0.0.2000',
        is_approval: true,
      },
      {
        receiver_account_id: '0.0.98',
        sender_account_id: '0.0.1005',
        serial_number: 2,
        token_id: '0.0.2000',
        is_approval: true,
      },
    ];

    const expectedFormat = [
      {
        assessed_custom_fees: undefined,
        bytes: 'bytes',
        consensus_timestamp: '0.000000001',
        charged_tx_fee: 5,
        entity_id: '0.0.98',
        max_custom_fees: [],
        max_fee: '33',
        memo_base64: null,
        name: 'CRYPTOTRANSFER',
        node: '0.0.2',
        nonce: 0,
        parent_consensus_timestamp: null,
        result: 'SUCCESS',
        scheduled: false,
        staking_reward_transfers: [],
        token_transfers: [],
        transaction_hash: 'hash',
        transaction_id: '0.0.3-1623787159-737799966',
        transfers: [
          {
            account: '0.0.98',
            amount: 100,
            is_approval: true,
          },
        ],
        nft_transfers: expectedNftTransfers,
        valid_duration_seconds: null,
        valid_start_timestamp: '1623787159.737799966',
      },
      {
        assessed_custom_fees: undefined,
        bytes: 'bytes',
        consensus_timestamp: '0.000000002',
        charged_tx_fee: 5,
        entity_id: '0.0.100',
        max_custom_fees: [],
        max_fee: '33',
        memo_base64: null,
        name: 'CRYPTOTRANSFER',
        nft_transfers: [],
        node: '0.0.2',
        nonce: 1,
        parent_consensus_timestamp: '0.000000001',
        result: 'SUCCESS',
        scheduled: false,
        staking_reward_transfers: [],
        token_transfers: [],
        transaction_hash: 'hash',
        transaction_id: '0.0.3-1623787159-737799966',
        transfers: [
          {
            account: '0.0.100',
            amount: 100,
            is_approval: true,
          },
        ],
        valid_duration_seconds: null,
        valid_start_timestamp: '1623787159.737799966',
      },
    ];
    expect(await formatTransactionRows(transactionsFromDb)).toEqual(expectedFormat);
  });
});

const getTransactionIdQuery = (extraConditions, entityKeys, isTransactionHash = false) => {
  let paramsCount = 0;
  const payerParams = [];
  const field = isTransactionHash ? 'consensus_timestamp' : 'valid_start_ns';
  const transactionConditionsArray = [];

  for (let i = 0; i < entityKeys.length; i += 2) {
    if (entityKeys[i]) {
      payerParams.push(`$${++paramsCount}`);
      transactionConditionsArray.push(`(payer_account_id = $${paramsCount} and ${field} = $${++paramsCount})`);
    } else {
      transactionConditionsArray.push(`${field} = $${++paramsCount}`);
    }
  }

  const transactionConditions = transactionConditionsArray.join(' or ');
  const payerConditions = payerParams && payerParams.length ? `and payer_account_id in (${payerParams.join(',')})` : '';

  return `
    select
      t.batch_key,
      t.charged_tx_fee,
      t.consensus_timestamp,
      t.entity_id,
      t.inner_transactions,
      t.max_custom_fees,
      t.max_fee,
      t.memo,
      t.nft_transfer,
      t.node_account_id,
      t.nonce,
      t.parent_consensus_timestamp,
      t.payer_account_id,
      t.result,
      t.scheduled,
      t.transaction_bytes,
      t.transaction_hash,
      t.type,
      t.valid_duration_seconds,
      t.valid_start_ns,
      t.index,
      (
          select jsonb_agg(jsonb_build_object('amount', amount, 'entity_id', entity_id, 'is_approval', is_approval) order by entity_id, amount)
          from crypto_transfer
          where consensus_timestamp = t.consensus_timestamp and payer_account_id = t.payer_account_id and consensus_timestamp >= $${
            paramsCount + 1
          } and consensus_timestamp <= $${paramsCount + 2} ${payerConditions}
      ) as crypto_transfer_list,
      (
          select jsonb_agg(
              jsonb_build_object('account_id', account_id, 'amount', amount, 'token_id', token_id, 'is_approval', is_approval)
              order by token_id, account_id)
          from token_transfer
          where consensus_timestamp = t.consensus_timestamp and payer_account_id = t.payer_account_id and consensus_timestamp >= $${
            paramsCount + 1
          } and consensus_timestamp <= $${paramsCount + 2} ${payerConditions}
      ) as token_transfer_list,
      (
          select jsonb_agg(
              jsonb_build_object(
                  'amount', amount, 'collector_account_id', collector_account_id,
                  'effective_payer_account_ids', effective_payer_account_ids,
                  'token_id', token_id) order by collector_account_id, amount)
          from assessed_custom_fee
          where consensus_timestamp = t.consensus_timestamp and payer_account_id = t.payer_account_id and consensus_timestamp >= $${
            paramsCount + 1
          } and consensus_timestamp <= $${paramsCount + 2} ${payerConditions}
      ) as assessed_custom_fees
    from transaction t
    where consensus_timestamp >= $${paramsCount + 1} and consensus_timestamp <= $${
    paramsCount + 2
  } and (${transactionConditions})
      ${(extraConditions && 'and ' + extraConditions) || ''}
    order by consensus_timestamp`;
};

const addTimestampIndices = (arr, minIndex, maxIndex) => {
  arr.lowerConsensusTimestampIndex = minIndex === undefined ? arr.length - 2 : minIndex;
  arr.upperConsensusTimestampIndex = maxIndex === undefined ? arr.length - 1 : maxIndex;
  return arr;
};

describe('extractSqlFromTransactionsByIdOrHashRequest', () => {
  describe('success', () => {
    const defaultTransactionIdStr = '0.0.200-123456789-987654321';
    const validStartNs = 123456789987654321n;
    const minConsensusTimestamp = validStartNs - maxRecordFileCloseIntervalNs;
    const maxConsensusTimestamp = validStartNs + maxTransactionConsensusTimestampRangeNs;
    const defaultParams = addTimestampIndices([200n, validStartNs, minConsensusTimestamp, maxConsensusTimestamp]);
    const transactionKeys = [200n, validStartNs];

    const testSpecs = [
      {
        name: 'emptyFilter',
        input: {
          transactionIdOrHash: defaultTransactionIdStr,
          filters: [],
        },
        expected: {
          query: getTransactionIdQuery(null, transactionKeys),
          params: defaultParams,
          scheduled: undefined,
          isTransactionHash: false,
        },
      },
      {
        name: 'nonceFilter',
        input: {
          transactionIdOrHash: defaultTransactionIdStr,
          filters: [{key: constants.filterKeys.NONCE, op: 'eq', value: 1}],
        },
        expected: {
          query: getTransactionIdQuery('nonce = $5', transactionKeys),
          params: addTimestampIndices([...defaultParams, 1], 2, 3),
          scheduled: undefined,
          isTransactionHash: false,
        },
      },
      {
        name: 'repeatedNonceFilters',
        input: {
          transactionIdOrHash: defaultTransactionIdStr,
          filters: [
            {key: constants.filterKeys.NONCE, op: 'eq', value: 1},
            {key: constants.filterKeys.NONCE, op: 'eq', value: 2},
          ],
        },
        expected: {
          query: getTransactionIdQuery('nonce = $5', transactionKeys),
          params: addTimestampIndices([...defaultParams, 2], 2, 3),
          scheduled: undefined,
          isTransactionHash: false,
        },
      },
      {
        name: 'scheduledFilter',
        input: {
          transactionIdOrHash: defaultTransactionIdStr,
          filters: [{key: constants.filterKeys.SCHEDULED, op: 'eq', value: true}],
        },
        expected: {
          query: getTransactionIdQuery('scheduled = $5', transactionKeys),
          params: addTimestampIndices([...defaultParams, true], 2, 3),
          scheduled: true,
          isTransactionHash: false,
        },
      },
      {
        name: 'repeatedScheduledFilters',
        input: {
          transactionIdOrHash: defaultTransactionIdStr,
          filters: [
            {key: constants.filterKeys.SCHEDULED, op: 'eq', value: true},
            {key: constants.filterKeys.SCHEDULED, op: 'eq', value: false},
          ],
        },
        expected: {
          query: getTransactionIdQuery('scheduled = $5', transactionKeys),
          params: addTimestampIndices([...defaultParams, false], 2, 3),
          scheduled: false,
          isTransactionHash: false,
        },
      },
      {
        name: 'nonceAndScheduledFilters',
        input: {
          transactionIdOrHash: defaultTransactionIdStr,
          filters: [
            {key: constants.filterKeys.NONCE, op: 'eq', value: 1},
            {key: constants.filterKeys.SCHEDULED, op: 'eq', value: true},
          ],
        },
        expected: {
          query: getTransactionIdQuery('nonce = $5 and scheduled = $6', transactionKeys),
          params: addTimestampIndices([...defaultParams, 1, true], 2, 3),
          scheduled: true,
          isTransactionHash: false,
        },
      },
      {
        name: 'repeatedNonceAndScheduledFilters',
        input: {
          transactionIdOrHash: defaultTransactionIdStr,
          filters: [
            {key: constants.filterKeys.NONCE, op: 'eq', value: 1},
            {key: constants.filterKeys.SCHEDULED, op: 'eq', value: true},
            {key: constants.filterKeys.NONCE, op: 'eq', value: 2},
            {key: constants.filterKeys.SCHEDULED, op: 'eq', value: false},
            {key: constants.filterKeys.NONCE, op: 'eq', value: 3},
            {key: constants.filterKeys.SCHEDULED, op: 'eq', value: false},
          ],
        },
        expected: {
          query: getTransactionIdQuery('nonce = $5 and scheduled = $6', transactionKeys),
          params: addTimestampIndices([...defaultParams, 3, false], 2, 3),
          scheduled: false,
          isTransactionHash: false,
        },
      },
    ];

    for (const testSpec of testSpecs) {
      test(testSpec.name, async () => {
        const actual = await extractSqlFromTransactionsByIdOrHashRequest(
          testSpec.input.transactionIdOrHash,
          testSpec.input.filters
        );

        testutils.assertSqlQueryEqual(actual.query, testSpec.expected.query);
        expect(actual.params).toStrictEqual(testSpec.expected.params);
        expect(actual.scheduled).toEqual(testSpec.expected.scheduled);
        expect(actual.isTransactionHash).toEqual(testSpec.expected.isTransactionHash);
      });
    }
  });

  describe('invalidTransactionIdOrHash', () => {
    [
      '0.1.x-1235234-5334',
      'izUDXqZ8gOhKlL5vbFInnw2VObTXzNWEH2QOg7XOUQwl9Mp2SVil8lufZIU6xJEE====',
      '0xab4af784ae69ca3e1d17fef8491f7f89a0e8a3b80ad3748b841110f0345ada53456bc14bbe9ee6f441829c2849',
      'ab4af784ae69ca3e1d17fef8491f7f89a0e8a3b80ad3748b841110f0345ada53456bc14bbe9ee6f441829c2849',
    ].forEach((transactionIdOrHash) => {
      test(transactionIdOrHash, async () => {
        await expect(
          extractSqlFromTransactionsByIdOrHashRequest(transactionIdOrHash, [])
        ).rejects.toThrowErrorMatchingSnapshot();
      });
    });
  });
});

describe('getTransactionsByTransactionIdsSql', () => {
  describe('success', () => {
    const defaultTransactionIdStr = '0.0.200-123456789-987654321';
    const minValidStartNs = 123456789987654321n;
    const maxValidStartNs = 123456789987654322n;

    const minConsensusTimestampForId = minValidStartNs - maxRecordFileCloseIntervalNs;
    const maxConsensusTimestampForId = maxValidStartNs + maxTransactionConsensusTimestampRangeNs;

    const defaultParams = addTimestampIndices([
      200n,
      minValidStartNs,
      201n,
      maxValidStartNs,
      minConsensusTimestampForId,
      maxConsensusTimestampForId,
    ]);

    const defaultTransactionKeys = [200n, minValidStartNs, 201n, maxValidStartNs];

    const nullPayerParams = addTimestampIndices([
      minValidStartNs,
      201n,
      maxValidStartNs,
      minValidStartNs,
      maxValidStartNs,
    ]);

    const nullPayerTransactionKeys = [null, 123456789987654321n, 201n, 123456789987654322n];
    const testSpecs = [
      {
        name: 'nullPayerConsensusTimestamp',
        input: {
          transactionIdOrHash: defaultTransactionIdStr,
          filters: [],
          transactionKeys: nullPayerTransactionKeys,
        },
        expected: {
          query: getTransactionIdQuery(null, nullPayerTransactionKeys, true),
          params: nullPayerParams,
          scheduled: undefined,
          isTransactionHash: true,
        },
      },
      {
        name: 'emptyFilter',
        input: {
          transactionIdOrHash: defaultTransactionIdStr,
          filters: [],
          transactionKeys: defaultTransactionKeys,
        },
        expected: {
          query: getTransactionIdQuery(null, defaultTransactionKeys),
          params: defaultParams,
          scheduled: undefined,
          isTransactionHash: false,
        },
      },
      {
        name: 'nonceFilter',
        input: {
          transactionIdOrHash: defaultTransactionIdStr,
          filters: [{key: constants.filterKeys.NONCE, op: 'eq', value: 1}],
          transactionKeys: defaultTransactionKeys,
        },
        expected: {
          query: getTransactionIdQuery('nonce = $7', defaultTransactionKeys),
          params: addTimestampIndices([...defaultParams, 1], 4, 5),
          scheduled: undefined,
          isTransactionHash: false,
        },
      },
      {
        name: 'repeatedNonceFilters',
        input: {
          transactionIdOrHash: defaultTransactionIdStr,
          filters: [
            {key: constants.filterKeys.NONCE, op: 'eq', value: 1},
            {key: constants.filterKeys.NONCE, op: 'eq', value: 2},
          ],
          transactionKeys: defaultTransactionKeys,
        },
        expected: {
          query: getTransactionIdQuery('nonce = $7', defaultTransactionKeys),
          params: addTimestampIndices([...defaultParams, 2], 4, 5),
          scheduled: undefined,
          isTransactionHash: false,
        },
      },
      {
        name: 'scheduledFilter',
        input: {
          transactionIdOrHash: defaultTransactionIdStr,
          filters: [{key: constants.filterKeys.SCHEDULED, op: 'eq', value: true}],
          transactionKeys: defaultTransactionKeys,
        },
        expected: {
          query: getTransactionIdQuery('scheduled = $7', defaultTransactionKeys),
          params: addTimestampIndices([...defaultParams, true], 4, 5),
          scheduled: true,
          isTransactionHash: false,
        },
      },
      {
        name: 'repeatedScheduledFilters',
        input: {
          transactionIdOrHash: defaultTransactionIdStr,
          filters: [
            {key: constants.filterKeys.SCHEDULED, op: 'eq', value: true},
            {key: constants.filterKeys.SCHEDULED, op: 'eq', value: false},
          ],
          transactionKeys: defaultTransactionKeys,
        },
        expected: {
          query: getTransactionIdQuery('scheduled = $7', defaultTransactionKeys),
          params: addTimestampIndices([...defaultParams, false], 4, 5),
          scheduled: false,
          isTransactionHash: false,
        },
      },
      {
        name: 'nonceAndScheduledFilters',
        input: {
          transactionIdOrHash: defaultTransactionIdStr,
          filters: [
            {key: constants.filterKeys.NONCE, op: 'eq', value: 1},
            {key: constants.filterKeys.SCHEDULED, op: 'eq', value: true},
          ],
          transactionKeys: defaultTransactionKeys,
        },
        expected: {
          query: getTransactionIdQuery('nonce = $7 and scheduled = $8', defaultTransactionKeys),
          params: addTimestampIndices([...defaultParams, 1, true], 4, 5),
          scheduled: true,
          isTransactionHash: false,
        },
      },
      {
        name: 'nonceAndScheduledFiltersNullPayer',
        input: {
          transactionIdOrHash: defaultTransactionIdStr,
          filters: [
            {key: constants.filterKeys.NONCE, op: 'eq', value: 1},
            {key: constants.filterKeys.SCHEDULED, op: 'eq', value: true},
          ],
          transactionKeys: nullPayerTransactionKeys,
        },
        expected: {
          query: getTransactionIdQuery('nonce = $6 and scheduled = $7', nullPayerTransactionKeys, true),
          params: addTimestampIndices([...nullPayerParams, 1, true], 3, 4),
          scheduled: true,
          isTransactionHash: true,
          transactionKeys: defaultTransactionKeys,
        },
      },
      {
        name: 'repeatedNonceAndScheduledFilters',
        input: {
          transactionIdOrHash: defaultTransactionIdStr,
          filters: [
            {key: constants.filterKeys.NONCE, op: 'eq', value: 1},
            {key: constants.filterKeys.SCHEDULED, op: 'eq', value: true},
            {key: constants.filterKeys.NONCE, op: 'eq', value: 2},
            {key: constants.filterKeys.SCHEDULED, op: 'eq', value: false},
            {key: constants.filterKeys.NONCE, op: 'eq', value: 3},
            {key: constants.filterKeys.SCHEDULED, op: 'eq', value: false},
          ],
          transactionKeys: defaultTransactionKeys,
        },
        expected: {
          query: getTransactionIdQuery('nonce = $7 and scheduled = $8', defaultTransactionKeys),
          params: addTimestampIndices([...defaultParams, 3, false], 4, 5),
          scheduled: false,
          isTransactionHash: false,
        },
      },
    ];

    for (const testSpec of testSpecs) {
      test(testSpec.name, () => {
        const actual = getTransactionsByTransactionIdsSql(
          testSpec.input.transactionKeys,
          testSpec.input.filters,
          testSpec.expected.isTransactionHash ? Transaction.CONSENSUS_TIMESTAMP : Transaction.VALID_START_NS
        );

        testutils.assertSqlQueryEqual(actual.query, testSpec.expected.query);
        expect(actual.params).toStrictEqual(testSpec.expected.params);
        expect(actual.scheduled).toEqual(testSpec.expected.scheduled);
      });
    }
  });

  test('null payer with valid start', () => {
    expect(() =>
      getTransactionsByTransactionIdsSql([null, 12345], [], Transaction.VALID_START_NS)
    ).toThrowErrorMatchingSnapshot();
  });
});

describe('extractSqlFromTransactionsRequest', () => {
  const {CREDIT, DEBIT} = constants.cryptoTransferType;
  const defaultExpected = {
    accountQuery: '',
    creditDebitQuery: '',
    limit: 25,
    limitQuery: 'limit $1',
    order: constants.orderFilterValues.DESC,
    params: [25],
    resultTypeQuery: '',
    transactionTypeQuery: '',
  };

  test('empty filters', () => {
    expect(extractSqlFromTransactionsRequest([])).toEqual(defaultExpected);
  });

  describe('account id', () => {
    test('single equal', () => {
      const filters = [{key: constants.filterKeys.ACCOUNT_ID, operator: utils.opsMap.eq, value: 123}];
      const expected = {
        ...defaultExpected,
        accountQuery: 'ctl.entity_id = $1',
        limitQuery: 'limit $2',
        params: [123, 25],
      };
      expect(extractSqlFromTransactionsRequest(filters)).toEqual(expected);
    });

    test('multiple equals', () => {
      const filters = [
        {key: constants.filterKeys.ACCOUNT_ID, operator: utils.opsMap.eq, value: 123},
        {key: constants.filterKeys.ACCOUNT_ID, operator: utils.opsMap.eq, value: 124},
      ];
      const expected = {
        ...defaultExpected,
        accountQuery: 'ctl.entity_id = any($1)',
        limitQuery: 'limit $2',
        params: [[123, 124], 25],
      };
      expect(extractSqlFromTransactionsRequest(filters)).toEqual(expected);
    });

    test('range and equal', () => {
      const filters = [
        {key: constants.filterKeys.ACCOUNT_ID, operator: utils.opsMap.gt, value: 100},
        {key: constants.filterKeys.ACCOUNT_ID, operator: utils.opsMap.lt, value: 200},
        {key: constants.filterKeys.ACCOUNT_ID, operator: utils.opsMap.eq, value: 123},
      ];
      const expected = {
        ...defaultExpected,
        accountQuery: 'ctl.entity_id > $1 and ctl.entity_id < $2 and ctl.entity_id = $3',
        limitQuery: 'limit $4',
        params: [100, 200, 123, 25],
      };
      expect(extractSqlFromTransactionsRequest(filters)).toEqual(expected);
    });
  });

  describe('credit type', () => {
    test('single value', () => {
      const expected = {
        ...defaultExpected,
        creditDebitQuery: 'ctl.amount > 0',
      };
      const filters = [{key: constants.filterKeys.CREDIT_TYPE, operator: utils.opsMap.eq, value: CREDIT}];
      expect(extractSqlFromTransactionsRequest(filters)).toEqual(expected);

      expected.creditDebitQuery = 'ctl.amount < 0';
      filters[0].value = DEBIT;
      expect(extractSqlFromTransactionsRequest(filters)).toEqual(expected);
    });

    test('repeated same values', () => {
      const expected = {
        ...defaultExpected,
        creditDebitQuery: 'ctl.amount > 0',
      };
      const filters = [
        {key: constants.filterKeys.CREDIT_TYPE, operator: utils.opsMap.eq, value: CREDIT},
        {key: constants.filterKeys.CREDIT_TYPE, operator: utils.opsMap.eq, value: CREDIT},
      ];
      expect(extractSqlFromTransactionsRequest(filters)).toEqual(expected);

      expected.creditDebitQuery = 'ctl.amount < 0';
      filters.forEach((filter) => (filter.value = 'debit'));
      expect(extractSqlFromTransactionsRequest(filters)).toEqual(expected);
    });

    test('credit and debit', () => {
      const filters = [
        {key: constants.filterKeys.CREDIT_TYPE, operator: utils.opsMap.eq, value: CREDIT},
        {key: constants.filterKeys.CREDIT_TYPE, operator: utils.opsMap.eq, value: DEBIT},
      ];
      expect(extractSqlFromTransactionsRequest(filters)).toBeNull();
    });
  });

  describe('result', () => {
    test('single value', () => {
      const expected = {
        ...defaultExpected,
        limitQuery: 'limit $1',
        params: [25],
        resultTypeQuery: `t.result in (${utils.resultSuccess})`,
      };
      const filters = [{key: constants.filterKeys.RESULT, operator: utils.opsMap.eq, value: 'success'}];
      expect(extractSqlFromTransactionsRequest(filters)).toEqual(expected);

      expected.resultTypeQuery = `t.result not in (${utils.resultSuccess})`;
      filters[0].value = 'fail';
      expect(extractSqlFromTransactionsRequest(filters)).toEqual(expected);
    });

    test('multiple values', () => {
      const expected = {
        ...defaultExpected,
        limitQuery: 'limit $1',
        params: [25],
        resultTypeQuery: `t.result in (${utils.resultSuccess})`,
      };
      const filters = [
        {key: constants.filterKeys.RESULT, operator: utils.opsMap.eq, value: 'success'},
        {key: constants.filterKeys.RESULT, operator: utils.opsMap.eq, value: 'fail'},
        {key: constants.filterKeys.RESULT, operator: utils.opsMap.eq, value: 'success'},
      ];
      expect(extractSqlFromTransactionsRequest(filters)).toEqual(expected);

      expected.resultTypeQuery = `t.result not in (${utils.resultSuccess})`;
      filters[2].value = 'fail';
      expect(extractSqlFromTransactionsRequest(filters)).toEqual(expected);
    });
  });

  describe('transaction type', () => {
    test('single transaction type', () => {
      const expected = {
        ...defaultExpected,
        limitQuery: 'limit $2',
        params: ['14', 25],
        transactionTypeQuery: 'type = $1',
      };
      const filters = [
        {key: constants.filterKeys.TRANSACTION_TYPE, operator: utils.opsMap.eq, value: 'CRYPTOTRANSFER'},
      ];
      expect(extractSqlFromTransactionsRequest(filters)).toEqual(expected);
    });

    test('multiple transaction types', () => {
      const expected = {
        ...defaultExpected,
        limitQuery: 'limit $2',
        params: [['14', '15'], 25],
        transactionTypeQuery: 'type = any($1)',
      };
      const filters = [
        {key: constants.filterKeys.TRANSACTION_TYPE, operator: utils.opsMap.eq, value: 'CRYPTOTRANSFER'},
        {key: constants.filterKeys.TRANSACTION_TYPE, operator: utils.opsMap.eq, value: 'CRYPTOUPDATEACCOUNT'},
      ];
      expect(extractSqlFromTransactionsRequest(filters)).toEqual(expected);
    });
  });

  test('all filters', () => {
    const expected = {
      accountQuery: 'ctl.entity_id = $1',
      creditDebitQuery: 'ctl.amount > 0',
      limit: 30,
      limitQuery: 'limit $3',
      order: 'asc',
      params: [123, '14', 30],
      resultTypeQuery: `t.result in (${utils.resultSuccess})`,
      transactionTypeQuery: 'type = $2',
    };
    const filters = [
      {key: constants.filterKeys.ACCOUNT_ID, operator: utils.opsMap.eq, value: 123},
      {key: constants.filterKeys.CREDIT_TYPE, operator: utils.opsMap.eq, value: CREDIT},
      {key: constants.filterKeys.RESULT, operator: utils.opsMap.eq, value: 'success'},
      {key: constants.filterKeys.TRANSACTION_TYPE, operator: utils.opsMap.eq, value: 'CRYPTOTRANSFER'},
      {key: constants.filterKeys.LIMIT, operator: utils.opsMap.eq, value: 30},
      {key: constants.filterKeys.ORDER, operator: utils.opsMap.eq, value: constants.orderFilterValues.ASC},
    ];
    expect(extractSqlFromTransactionsRequest(filters)).toEqual(expected);
  });
});

describe('isValidTransactionHash', () => {
  describe('base64', () => {
    describe('valid', () => {
      [
        'rovr8cn6DzCTVuSAV/YEevfN5jA30FCdFt3Dsg4IUVi/3xTRU0XBsYsZm3L+1Kxv',
        'rovr8cn6DzCTVuSAV_YEevfN5jA30FCdFt3Dsg4IUVi_3xTRU0XBsYsZm3L-1Kxv',
      ].forEach((hash) =>
        test(`'${hash}'`, () => {
          expect(isValidTransactionHash(hash)).toBeTrue();
        })
      );
    });

    describe('invalid', () => {
      [
        'rovr8cn6DzCTVuSAV/YEevfN5jA30FCdFt3Dsg4IUVi/3xTRU0XBsYsZm3L+1===',
        'q0r3hK5pyj4dF/74SR9/iaDoo7gK03SLhBEQ8DRa2lNFa8FLvp7m9EGCnChJ',
        'q0r3hK5pyj4dF/74SR9/iaDoo7gK03SLhBEQ8DRa2lNFa8FLvp7m9EGCnChJkzrEaaaa',
        'q0r3hK5pyj4dF/74SR9/iaDoo7gK03SLhBEQ8DRa2lNFa8FLvp7m9EGCnChJ????',
      ].forEach((hash) =>
        test(`'${hash}`, () => {
          expect(isValidTransactionHash(hash)).toBeFalse();
        })
      );
    });
  });

  describe('hex', () => {
    describe('valid', () => {
      [
        'ab4af784ae69ca3e1d17fef8491f7f89a0e8a3b80ad3748b841110f0345ada53456bc14bbe9ee6f441829c2849933ac4',
        'AB4AF784AE69CA3E1D17FEF8491F7F89A0E8A3B80AD3748B841110F0345ADA53456BC14BBE9EE6F441829C2849933AC4',
        '0xab4af784ae69ca3e1d17fef8491f7f89a0e8a3b80ad3748b841110f0345ada53456bc14bbe9ee6f441829c2849933ac4',
      ].forEach((hash) =>
        test(`'${hash}'`, () => {
          expect(isValidTransactionHash(hash)).toBeTrue();
        })
      );
    });

    describe('invalid', () => {
      [
        null,
        undefined,
        '',
        'ab4af784ae69ca3e1d17fef8491f7f89a0e8a3b80ad3748b841110f0345ada53456bc14bbe9ee6f441829c2849933ac4beef',
        'ab4af784ae69ca3e1d17fef8491f7f89a0e8a3b80ad3748b841110f0345ada53456bc14bbe9ee6f441829c284993====',
        'ab4af784ae69ca3e1d17fef8491f7f89a0e8a3b80ad3748b841110f0345ada53456bc14bbe9ee6f441829c28',
        '0xab4af784ae69ca3e1d17fef8491f7f89a0e8a3b80ad3748b841110f0345ada53456bc14bbe9ee6f441829c2849933a',
      ].forEach((hash) =>
        test(`'${hash}'`, () => {
          expect(isValidTransactionHash(hash)).toBeFalse();
        })
      );
    });
  });
});

describe('convert staking reward transfers', () => {
  test('empty staking reward transfer', () => {
    const rows = [];
    const stakingRewardMap = convertStakingRewardTransfers(rows);
    expect(stakingRewardMap.get(1)).toEqual(undefined);
  });

  test('staking reward transfer', () => {
    const rows = [
      {
        consensus_timestamp: 1,
        staking_reward_transfers: [
          {account: 98, amount: 500},
          {account: 99, amount: 600},
        ],
      },
      {
        consensus_timestamp: 2,
        staking_reward_transfers: [{account: 100, amount: 0}],
      },
      {
        consensus_timestamp: 3,
        staking_reward_transfers: [],
      },
    ];

    const expected1 = [
      {
        account: '0.0.98',
        amount: 500,
      },
      {
        account: '0.0.99',
        amount: 600,
      },
    ];
    const expected2 = [
      {
        account: '0.0.100',
        amount: 0,
      },
    ];
    const expected3 = [];

    const stakingRewardMap = convertStakingRewardTransfers(rows);
    expect(stakingRewardMap.get(1)).toEqual(expected1);
    expect(stakingRewardMap.get(2)).toEqual(expected2);
    expect(stakingRewardMap.get(3)).toEqual(expected3);
  });
});

describe('getStakingRewardTimestamps', () => {
  [
    [
      {
        consensus_timestamp: 1565779604000000002,
        crypto_transfer_list: [
          {
            entity_id: 801,
          },
        ],
      },
    ],
    [
      {
        crypto_transfer_list: [
          {
            entity_id: 800,
          },
        ],
      },
    ],
    [],
  ].forEach((transactions) => {
    test(`'${transactions}'`, () => {
      expect(getStakingRewardTimestamps(transactions)).toEqual([]);
    });
  });

  test('get staking timestamps', () => {
    const transactions = [
      {
        consensus_timestamp: 1565779604000000002,
        crypto_transfer_list: [
          {
            entity_id: EntityId.systemEntity.stakingRewardAccount.getEncodedId(),
          },
        ],
      },
      {
        consensus_timestamp: 1565779602000000002,
        crypto_transfer_list: [
          {
            entity_id: EntityId.systemEntity.feeCollector.getEncodedId(),
          },
        ],
      },
      {
        consensus_timestamp: 1565779600000000002,
        crypto_transfer_list: [
          {
            entity_id: EntityId.systemEntity.stakingRewardAccount.getEncodedId(),
          },
        ],
      },
    ];

    const expected = [1565779604000000002, 1565779600000000002];
    expect(getStakingRewardTimestamps(transactions)).toEqual(expected);
  });
});

describe('getTransactionsByIdOrHashCacheControlHeader', () => {
  const shorterCacheControlHeader = {'cache-control': `public, max-age=5`};
  const testSpecs = [
    {
      name: 'query by transaction hash, no scheduled, schedulecreate transaction',
      input: {
        isTransactionHash: true,
        scheduledParamExists: false,
        transactions: [
          {
            consensus_timestamp: 1n,
            result: 22,
            scheduled: false,
            type: 42,
          },
        ],
      },
      expected: {},
    },
    {
      name: 'query by transaction hash, has scheduled, scheduled transaction',
      input: {
        isTransactionHash: true,
        scheduledParamExists: true,
        transactions: [
          {
            consensus_timestamp: 1n,
            result: 22,
            scheduled: true,
            type: 14,
          },
        ],
      },
      expected: {},
    },
    {
      name: 'query by transaction id, has scheduled, schedulecreate transaction',
      input: {
        isTransactionHash: false,
        scheduledParamExists: true,
        transactions: [
          {
            consensus_timestamp: 1n,
            result: 22,
            scheduled: false,
            type: 42,
          },
        ],
      },
      expected: {},
    },
    {
      name: 'query by transaction id, has scheduled, scheduled transaction',
      input: {
        isTransactionHash: false,
        scheduledParamExists: true,
        transactions: [
          {
            consensus_timestamp: 1n,
            result: 22,
            scheduled: true,
            type: 14,
          },
        ],
      },
      expected: {},
    },
    {
      name: 'query by transaction id, no scheduled, only scheculecreate and scheduled transaction yet to execute',
      input: {
        isTransactionHash: false,
        scheduledParamExists: false,
        transactions: [
          {
            consensus_timestamp: utils.nowInNs() - 500n,
            result: 22,
            scheduled: false,
            type: 42,
          },
        ],
      },
      expected: shorterCacheControlHeader,
    },
    {
      name: 'query by transaction id, no scheduled, only scheculecreate and schedule has expired',
      input: {
        isTransactionHash: false,
        scheduledParamExists: false,
        transactions: [
          {
            consensus_timestamp: utils.nowInNs() - maxScheduledTransactionConsensusTimestampRangeNs - 100n,
            result: 22,
            scheduled: false,
            type: 42,
          },
        ],
      },
      expected: {},
    },
    {
      name: 'query by transaction id, no scheduled, scheculecreate and scheduled transaction',
      input: {
        isTransactionHash: false,
        scheduledParamExists: false,
        transactions: [
          {
            consensus_timestamp: 100n,
            result: 22,
            scheduled: false,
            type: 42,
          },
          {
            consensus_timestamp: 110n,
            result: 22,
            scheduled: true,
            type: 14,
          },
        ],
      },
      expected: {},
    },
    {
      name: 'query by transaction id, no scheduled, only failed scheculecreate',
      input: {
        isTransactionHash: false,
        scheduledParamExists: false,
        transactions: [
          {
            consensus_timestamp: utils.nowInNs() - 500n,
            result: 10,
            scheduled: false,
            type: 42,
          },
        ],
      },
      expected: {},
    },
  ];

  testSpecs.forEach(({name, input, expected}) => {
    const {isTransactionHash, scheduledParamExists, transactions} = input;
    test(name, () => {
      expect(
        getTransactionsByIdOrHashCacheControlHeader(isTransactionHash, scheduledParamExists, transactions)
      ).toEqual(expected);
    });
  });
});

describe('mayMissLongTermScheduledTransaction', () => {
  const testSpecs = [
    {
      name: 'by transaction hash, no scheduled, empty transactions',
      input: {
        isTransactionHash: true,
        scheduled: undefined,
        transactions: [],
      },
      expected: false,
    },
    {
      name: 'by transaction hash, scheduled=true, empty transactions',
      input: {
        isTransactionHash: true,
        scheduled: true,
        transactions: [],
      },
      expected: false,
    },
    {
      name: 'by transaction hash, scheduled=false, empty transactions',
      input: {
        isTransactionHash: true,
        scheduled: false,
        transactions: [],
      },
      expected: false,
    },
    {
      name: 'by transaction id, no scheduled, empty transactions',
      input: {
        isTransactionHash: false,
        scheduled: undefined,
        transactions: [],
      },
      expected: false,
    },
    {
      name: 'by transaction id, no scheduled, successful schedulecreate',
      input: {
        isTransactionHash: false,
        scheduled: undefined,
        transactions: [
          {
            scheduled: false,
            result: 22,
            type: 42,
          },
        ],
      },
      expected: true,
    },
    {
      name: 'by transaction id, no scheduled, successful schedulecreate and scheculed transaction',
      input: {
        isTransactionHash: false,
        scheduled: undefined,
        transactions: [
          {
            scheduled: false,
            result: 22,
            type: 42,
          },
          {
            scheduled: true,
            result: 22,
            type: 14,
          },
        ],
      },
      expected: false,
    },
    {
      name: 'by transaction id, no scheduled, failed schedulecreate',
      input: {
        isTransactionHash: false,
        scheduled: undefined,
        transactions: [
          {
            scheduled: false,
            result: 10,
            type: 42,
          },
        ],
      },
      expected: false,
    },
    {
      name: 'by transaction id, scheduled=false, empty transactions',
      input: {
        isTransactionHash: false,
        scheduled: false,
        transactions: [],
      },
      expected: false,
    },
    {
      name: 'by transaction id, scheduled=false, successful schedulecreate',
      input: {
        isTransactionHash: false,
        scheduled: false,
        transactions: [
          {
            scheduled: false,
            result: 22,
            type: 42,
          },
        ],
      },
      expected: false,
    },
    {
      name: 'by transaction id, scheduled=false, failed schedulecreate',
      input: {
        isTransactionHash: false,
        scheduled: false,
        transactions: [
          {
            scheduled: false,
            result: 10,
            type: 42,
          },
        ],
      },
      expected: false,
    },
    {
      name: 'by transaction id, scheduled=true, empty transactions',
      input: {
        isTransactionHash: false,
        scheduled: true,
        transactions: [],
      },
      expected: true,
    },
    {
      name: 'by transaction id, scheduled=true, successful scheduled transactions',
      input: {
        isTransactionHash: false,
        scheduled: true,
        transactions: [
          {
            scheduled: true,
            result: 22,
            type: 14,
          },
        ],
      },
      expected: false,
    },
    {
      name: 'by transaction id, scheduled=true, failed scheduled transactions',
      input: {
        isTransactionHash: false,
        scheduled: true,
        transactions: [
          {
            scheduled: true,
            result: 10,
            type: 14,
          },
        ],
      },
      expected: false,
    },
  ];

  testSpecs.forEach(({name, input, expected}) => {
    test(name, () => {
      const {isTransactionHash, scheduled, transactions} = input;
      expect(mayMissLongTermScheduledTransaction(isTransactionHash, scheduled, transactions)).toBe(expected);
    });
  });
});
