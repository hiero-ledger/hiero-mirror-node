// SPDX-License-Identifier: Apache-2.0

import crypto from 'crypto';
import _ from 'lodash';

import {ETH_HASH_LENGTH} from '../../constants';
import {getTransactionHash} from '../../transactionHash';
import {opsMap} from '../../utils';

import integrationDomainOps from '../integrationDomainOps';
import {setupIntegrationTest} from '../integrationUtils';

setupIntegrationTest();

describe('getTransactionHash', () => {
  const transactionHashes = [
    {consensus_timestamp: 1, hash: crypto.randomBytes(48), payer_account_id: 10},
    {consensus_timestamp: 2, hash: crypto.randomBytes(32), payer_account_id: 11},
  ];
  const prefix = transactionHashes[0].hash.subarray(0, ETH_HASH_LENGTH);
  const samePrefixHash = Buffer.concat([prefix, crypto.randomBytes(16)]);
  transactionHashes.push({consensus_timestamp: 3, hash: samePrefixHash, payer_account_id: 12});

  beforeEach(async () => {
    await integrationDomainOps.loadTransactionHashes(transactionHashes);
  });

  describe('simple', () => {
    const specs = [
      {
        name: 'first record',
        hash: transactionHashes[0].hash,
        expected: [transactionHashes[0]],
      },
      {
        name: 'second record',
        hash: transactionHashes[1].hash,
        expected: [transactionHashes[1]],
      },
      {
        name: '32-byte hash prefix default order',
        hash: prefix,
        expected: [transactionHashes[0], transactionHashes[2]],
      },
      {
        name: '32-byte hash prefix order desc',
        hash: prefix,
        options: {order: 'desc'},
        expected: [transactionHashes[2], transactionHashes[0]],
      },
    ];

    test.each(specs)('$name', async ({hash, options, expected}) => {
      await expect(getTransactionHash(hash, options)).resolves.toMatchObject(expected);
    });
  });

  describe('timestamp filters', () => {
    const specs = [
      {
        name: 'first record',
        hash: transactionHashes[0].hash,
        timestampFilters: [{operator: opsMap.gte, value: transactionHashes[0].consensus_timestamp}],
        expected: [transactionHashes[0]],
      },
      {
        name: 'no match',
        hash: transactionHashes[0].hash,
        timestampFilters: [{operator: opsMap.gt, value: transactionHashes[0].consensus_timestamp}],
        expected: [],
      },
      {
        name: '32-byte hash prefix',
        hash: prefix,
        timestampFilters: [
          {operator: opsMap.gte, value: transactionHashes[0].consensus_timestamp},
          {operator: opsMap.lte, value: transactionHashes[2].consensus_timestamp},
        ],
        expected: [transactionHashes[0], transactionHashes[2]],
      },
      {
        name: '32-byte hash prefix filtered by timestamp',
        hash: prefix,
        timestampFilters: [
          {operator: opsMap.gte, value: transactionHashes[0].consensus_timestamp},
          {operator: opsMap.lt, value: transactionHashes[2].consensus_timestamp},
        ],
        expected: [transactionHashes[0]],
      },
    ];

    test.each(specs)('$name', async ({hash, timestampFilters, expected}) => {
      await expect(getTransactionHash(hash, {timestampFilters})).resolves.toEqual(expected);
    });
  });
});
