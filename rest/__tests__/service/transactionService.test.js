// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';

import TransactionId from '../../transactionId';
import {TransactionService} from '../../service';
import {TransactionResult, TransactionType} from '../../model';

import integrationDomainOps from '../integrationDomainOps';
import {setupIntegrationTest} from '../integrationUtils';
import EntityId from '../../entityId';

setupIntegrationTest();

const contractCallType = TransactionType.getProtoId('CONTRACTCALL');
const contractCreateType = TransactionType.getProtoId('CONTRACTCREATEINSTANCE');
const ethereumTxType = TransactionType.getProtoId('ETHEREUMTRANSACTION');
const duplicateTransactionResult = TransactionResult.getProtoId('DUPLICATE_TRANSACTION');
const successTransactionResult = TransactionResult.getProtoId('SUCCESS');

describe('TransactionService.getTransactionDetailsFromTransactionId tests', () => {
  const defaultPayerId = EntityId.parseString('5');
  const defaultPayerEncodedId = defaultPayerId.getEncodedId();
  const duplicateValidStartNs = 10;
  const inputTransactions = [
    {consensus_timestamp: 2, payerAccountId: defaultPayerId.num}, // crypto transfer, success
    {consensus_timestamp: 6, payerAccountId: defaultPayerId.num, type: contractCreateType}, // success
    {
      consensus_timestamp: 8,
      payerAccountId: defaultPayerId.toString(),
      type: contractCallType,
      result: duplicateTransactionResult, // duplicate of the previous tx, though this is of different tx type
      valid_start_timestamp: 5,
    },
    {
      consensus_timestamp: 11,
      payerAccountId: defaultPayerId.toString(),
      type: contractCallType,
      valid_start_timestamp: duplicateValidStartNs, // success
    },
    {
      consensus_timestamp: 13,
      payerAccountId: defaultPayerId.toString(),
      type: contractCallType,
      nonce: 1,
      valid_start_timestamp: duplicateValidStartNs, // success, child
    },
    {
      consensus_timestamp: 15,
      payerAccountId: defaultPayerId.toString(),
      type: contractCallType,
      result: duplicateTransactionResult,
      valid_start_timestamp: duplicateValidStartNs, // same valid start so duplicate tx id with the 4th tx
    },
  ];

  // pick the fields of interests, otherwise expect will fail since the Transaction object has other fields
  const pickTransactionFields = (transactions) => {
    return transactions.map((t) => _.pick(t, ['consensusTimestamp', 'payerAccountId']));
  };

  beforeEach(async () => {
    await integrationDomainOps.loadTransactions(inputTransactions);
  });

  test('No match', async () => {
    await expect(
      TransactionService.getTransactionDetailsFromTransactionId(
        TransactionId.fromString('0.0.1010-1234567890-123456789')
      )
    ).resolves.toHaveLength(0);
  });

  test('Single row match', async () => {
    const actual = await TransactionService.getTransactionDetailsFromTransactionId(
      TransactionId.fromString(`${defaultPayerId.toString()}-0-1`)
    );
    expect(pickTransactionFields(actual)).toEqual([{consensusTimestamp: 2, payerAccountId: defaultPayerEncodedId}]);
  });

  test('Single row match nonce=1', async () => {
    const actual = await TransactionService.getTransactionDetailsFromTransactionId(
      TransactionId.fromString(`${defaultPayerId.toString()}-0-${duplicateValidStartNs}`),
      1
    );
    expect(pickTransactionFields(actual)).toEqual([{consensusTimestamp: 13, payerAccountId: defaultPayerEncodedId}]);
  });

  test('Latest row match with nonce', async () => {
    const actual = await TransactionService.getTransactionDetailsFromTransactionId(
      TransactionId.fromString(`${defaultPayerId.toString()}-0-5`),
      0
    );
    expect(pickTransactionFields(actual)).toIncludeSameMembers([
      {consensusTimestamp: 6, payerAccountId: defaultPayerEncodedId},
    ]);
  });

  test('Latest row match without nonce', async () => {
    const actual = await TransactionService.getTransactionDetailsFromTransactionId(
      TransactionId.fromString(`${defaultPayerId.toString()}-0-${duplicateValidStartNs}`)
    );
    expect(pickTransactionFields(actual)).toIncludeSameMembers([
      {consensusTimestamp: 11, payerAccountId: defaultPayerEncodedId},
    ]);
  });

  test('Single row match with nonce returns latest unsuccessful if no successful', async () => {
    const actual = await TransactionService.getTransactionDetailsFromTransactionId(
      TransactionId.fromString(`${defaultPayerId.toString()}-0-${duplicateValidStartNs}`),
      0
    );
    expect(pickTransactionFields(actual)).toIncludeSameMembers([
      {consensusTimestamp: 11, payerAccountId: defaultPayerEncodedId},
    ]);
  });
});

describe('TransactionService.getEthTransactionByTimestampAndPayerId tests', () => {
  const ethereumTxHash = '4a563af33c4871b51a8b108aa2fe1dd5280a30dfb7236170ae5e5e7957eb6392';
  const defaultPayerId = EntityId.parseString('10');
  const defaultPayerEncodedId = defaultPayerId.getEncodedId();

  const inputTransactions = [
    {
      consensus_timestamp: 1,
      payerAccountId: defaultPayerId.toString(),
      type: ethereumTxType,
      result: successTransactionResult,
      valid_start_timestamp: 1,
    },
    {
      consensus_timestamp: 2,
      payerAccountId: defaultPayerId.toString(),
      type: ethereumTxType,
      result: duplicateTransactionResult,
      valid_start_timestamp: 2,
    },
  ];

  const inputEthTransactions = [
    {
      consensus_timestamp: 1,
      hash: ethereumTxHash,
      payer_account_id: defaultPayerId.num,
    },
    {
      consensus_timestamp: 2,
      hash: ethereumTxHash,
      payer_account_id: defaultPayerId.toString(),
    },
  ];

  const expectedTransaction = {
    consensusTimestamp: 2,
    hash: ethereumTxHash,
  };

  const pickTransactionFields = (transactions) => {
    return transactions
      .map((tx) => _.pick(tx, ['consensusTimestamp', 'hash']))
      .map((tx) => ({...tx, hash: Buffer.from(tx.hash).toString('hex')}));
  };

  beforeEach(async () => {
    await integrationDomainOps.loadTransactions(inputTransactions);
  });

  test('No match', async () => {
    await expect(
      TransactionService.getEthTransactionByTimestampAndPayerId('1', defaultPayerEncodedId)
    ).resolves.toHaveLength(0);
  });

  test('Finds a matching eth transaction', async () => {
    await integrationDomainOps.loadEthereumTransactions(inputEthTransactions);

    const ethTransactions = await TransactionService.getEthTransactionByTimestampAndPayerId('2', defaultPayerEncodedId);

    expect(pickTransactionFields(ethTransactions)).toIncludeSameMembers([expectedTransaction]);
  });
});
