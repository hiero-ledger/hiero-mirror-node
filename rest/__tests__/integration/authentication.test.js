// SPDX-License-Identifier: Apache-2.0

import request from 'supertest';

import config from '../../config';
import integrationDomainOps from '../integrationDomainOps';
import {setupIntegrationTest} from '../integrationUtils';
import server from '../../server';

setupIntegrationTest();

describe('Authentication custom limits', () => {
  const testUser = {
    username: 'testuser',
    password: 'testpass',
    limit: 5,
  };

  const premiumUser = {
    username: 'premium',
    password: 'secret',
    limit: 10,
  };

  let originalUsers, originalDefault, originalMax;

  beforeAll(async () => {
    // save original config
    originalUsers = config.users;
    originalDefault = config.response.limit.default;
    originalMax = config.response.limit.max;

    // configure users and set limits to make testing easier
    config.users = [testUser, premiumUser];
    config.response.limit.default = 1;
    config.response.limit.max = 2;
  });

  afterAll(() => {
    config.users = originalUsers;
    config.response.limit.default = originalDefault;
    config.response.limit.max = originalMax;
  });

  test('Unauthenticated request uses default max limit', async () => {
    // create test data
    const transactions = [];
    for (let i = 0; i < 5; i++) {
      transactions.push({
        consensus_timestamp: 1000000000n + BigInt(i),
        payerAccountId: 2,
        type: 14,
      });
    }
    await integrationDomainOps.loadTransactions(transactions);

    const response = await request(server).get('/api/v1/transactions?limit=10');

    expect(response.status).toBe(200);
    expect(response.body.transactions.length).toBeLessThanOrEqual(2); // capped at default max (2)
  });

  test('Authenticated user can exceed default max up to custom limit', async () => {
    // create test data
    const transactions = [];
    for (let i = 0; i < 10; i++) {
      transactions.push({
        consensus_timestamp: 2000000000n + BigInt(i),
        payerAccountId: 2,
        type: 14,
      });
    }
    await integrationDomainOps.loadTransactions(transactions);

    const credentials = Buffer.from(`${testUser.username}:${testUser.password}`).toString('base64');
    const response = await request(server)
      .get('/api/v1/transactions?limit=5')
      .set('Authorization', `Basic ${credentials}`);

    expect(response.status).toBe(200);
    expect(response.body.transactions.length).toBeGreaterThan(2); // exceeds default max
    expect(response.body.transactions.length).toBeLessThanOrEqual(5); // capped at custom limit (5)
  });

  test('Authenticated user limit is enforced', async () => {
    const credentials = Buffer.from(`${testUser.username}:${testUser.password}`).toString('base64');
    const response = await request(server)
      .get('/api/v1/transactions?limit=20')
      .set('Authorization', `Basic ${credentials}`);

    expect(response.status).toBe(200);
    expect(response.body.transactions.length).toBeLessThanOrEqual(5); // capped at custom limit (5)
  });

  test('Premium user can request higher limit', async () => {
    // create test data with more transactions
    const transactions = [];
    for (let i = 0; i < 15; i++) {
      transactions.push({
        consensus_timestamp: 3000000000n + BigInt(i),
        payerAccountId: 2,
        type: 14,
      });
    }
    await integrationDomainOps.loadTransactions(transactions);

    const credentials = Buffer.from(`${premiumUser.username}:${premiumUser.password}`).toString('base64');
    const response = await request(server)
      .get('/api/v1/transactions?limit=10')
      .set('Authorization', `Basic ${credentials}`);

    expect(response.status).toBe(200);
    expect(response.body.transactions.length).toBeGreaterThan(5); // exceeds test user limit
    expect(response.body.transactions.length).toBeLessThanOrEqual(10); // capped at premium limit (10)
  });

  test('Blocks endpoint respects custom limit', async () => {
    // create test data - record files for blocks
    const recordFiles = [];
    for (let i = 0; i < 10; i++) {
      recordFiles.push({
        consensus_start: 4000000000n + BigInt(i * 1000),
        consensus_end: 4000000000n + BigInt(i * 1000 + 500),
        index: i + 100,
        hash: `0000000000000000000000000000000000000000000000000000000000000${i.toString().padStart(3, '0')}`,
      });
    }
    await integrationDomainOps.loadRecordFiles(recordFiles);

    const credentials = Buffer.from(`${testUser.username}:${testUser.password}`).toString('base64');
    const response = await request(server).get('/api/v1/blocks?limit=5').set('Authorization', `Basic ${credentials}`);

    expect(response.status).toBe(200);
    expect(response.body.blocks.length).toBeGreaterThan(2); // exceeds default max
    expect(response.body.blocks.length).toBeLessThanOrEqual(5); // capped at custom limit (5)
  });
});
