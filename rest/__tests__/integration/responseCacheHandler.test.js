// SPDX-License-Identifier: Apache-2.0

import request from 'supertest';

import {Cache} from '../../cache';
import config from '../../config';
import integrationContainerOps from '../integrationContainerOps';
import integrationDomainOps from '../integrationDomainOps';
import {slowStepTimeoutMillis, setupIntegrationTest} from '../integrationUtils';

let cache;
let redisContainer;
let server;

setupIntegrationTest();

beforeAll(async () => {
  redisContainer = await integrationContainerOps.startRedisContainer();
  logger.info('Started Redis container');

  config.redis.enabled = true;
  config.redis.uri = `0.0.0.0:${redisContainer.getMappedPort(6379)}`;
  config.cache.response.enabled = true;
  cache = new Cache();

  // Loading server module will replace the global pool with a new one created from the default db configuration, so
  // set it back
  const {pool} = global;
  server = (await import('../../server')).default;
  await global.pool.end();
  global.pool = pool;
}, slowStepTimeoutMillis);

afterAll(async () => {
  await cache.stop();
  await redisContainer.stop({signal: 'SIGKILL', timeout: 3000});
  logger.info('Stopped Redis container');
  config.cache.response.enabled = false;
  config.redis.enabled = false;
}, slowStepTimeoutMillis);

describe('Response cache', () => {
  const expectedBody = JSON.stringify({
    rewards: [
      {
        account_id: '0.0.12345',
        amount: 100,
        timestamp: '1746816110.123456700',
      },
      {
        account_id: '0.0.12345',
        amount: 101,
        timestamp: '1746229911.123456780',
      },
      {
        account_id: '0.0.12345',
        amount: 102,
        timestamp: '1746143511.123456780',
      },
      {
        account_id: '0.0.12345',
        amount: 102,
        timestamp: '1746057111.123456780',
      },
    ],
    links: {next: null},
  });
  const url = '/api/v1/accounts/0.0.12345/rewards';

  const cleanupData = async () => {
    await ownerPool.query('truncate entity;');
    await ownerPool.query('truncate staking_reward_transfer');
  };

  const loadStakingRewardTransfers = async () => {
    const stakingRewardTransfers = [
      {
        account_id: 12345,
        amount: 100,
        consensus_timestamp: 1746816110123456700n,
        payer_account_id: 12345,
      },
      {
        account_id: 12345,
        amount: 101,
        consensus_timestamp: 1746229911123456780n,
        payer_account_id: 12345,
      },
      {
        account_id: 12345,
        amount: 102,
        consensus_timestamp: 1746143511123456780n,
        payer_account_id: 12345,
      },
      {
        account_id: 12345,
        amount: 102,
        consensus_timestamp: 1746057111123456780n,
        payer_account_id: 12345,
      },
    ];
    await integrationDomainOps.loadStakingRewardTransfers(stakingRewardTransfers);
  };

  const warmUpCache = async () => {
    const response = await request(server).get(url);
    expect(response.statusCode).toEqual(200);
    return response.get('etag');
  };

  beforeEach(async () => {
    await cache.clear();

    // The endpoint checks if the account exists
    await integrationDomainOps.loadAccounts([
      {
        id: 12345,
        num: 12345,
        realm: 0,
        shard: 0,
        timestamp_range: '[0,)',
      },
    ]);
  });

  test.each([
    ['GET - empty accept-encoding, from cache', ''],
    ['GET - gzip not in accept-encoding, from cache', 'deflate'],
    ['GET - gzip explicitly disabled in accept-encoding, from cache', 'gzip;q=0,deflate'],
  ])('%s', async (_, encodings) => {
    // given
    await loadStakingRewardTransfers();
    const etag = await warmUpCache();
    await cleanupData();

    // when
    const response = await request(server).get(url).set('accept-encoding', encodings);

    // then
    expect(response.statusCode).toEqual(200);
    expect(response.text).toEqual(expectedBody);
    expect(response.header).toMatchObject({
      'content-length': `${expectedBody.length}`,
      'content-type': 'application/json; charset=utf-8',
      etag,
      vary: 'accept-encoding',
    });
    expect(response.get('cache-control')).toMatch(/^public, max-age=\d+$/);
    expect(response.header).not.toHaveProperty('content-encoding');
  });

  test('GET - response with compressed body from cache', async () => {
    // given
    await loadStakingRewardTransfers();
    const etag = await warmUpCache();
    await cleanupData();

    // when
    const response = await request(server).get(url).set('accept-encoding', 'gzip');

    // then
    expect(response.statusCode).toEqual(200);
    expect(response.text).toEqual(expectedBody);
    expect(response.header).toMatchObject({
      'content-encoding': 'gzip',
      'content-type': 'application/json; charset=utf-8',
      etag,
      vary: 'accept-encoding',
    });
    expect(response.get('cache-control')).toMatch(/^public, max-age=\d+$/);
    expect(Number(response.get('content-length'))).toBeLessThan(expectedBody.length);
  });

  test('GET - no compression since response size below threshold, from cache', async () => {
    // given empty staking rewards transfer table, response body size is below threshold
    const expectedBody = '{"rewards":[],"links":{"next":null}}';
    const etag = await warmUpCache();
    await cleanupData();

    // when, GET with gzip supported
    const response = await request(server).get(url).set('accept-encoding', 'gzip');

    // then response body not compressed
    expect(response.statusCode).toEqual(200);
    expect(response.text).toEqual(expectedBody);
    expect(response.header).toMatchObject({
      'content-length': `${expectedBody.length}`,
      'content-type': 'application/json; charset=utf-8',
      etag,
      vary: 'accept-encoding',
    });
    expect(response.get('cache-control')).toMatch(/^public, max-age=\d+$/);
  });

  test('GET - not modified from cache', async () => {
    // given
    await loadStakingRewardTransfers();
    const etag = await warmUpCache();
    await cleanupData();

    // when
    const response = await request(server).get(url).set('if-none-match', etag);

    // then
    expect(response.statusCode).toEqual(304);
    expect(response.body).toBeEmpty();
    expect(response.get('etag')).toEqual(etag);
    expect(response.get('cache-control')).toMatch(/^public, max-age=\d+$/);
    expect(response.headers).not.toHaveProperty('content-encoding');
    expect(response.headers).not.toHaveProperty('content-length');
    expect(response.headers).not.toHaveProperty('vary');
  });

  test('GET - cache expire, query with if-none-match, query again without if-none-match', async () => {
    // given
    // warmup
    await loadStakingRewardTransfers();
    const etag = await warmUpCache();

    // when clear cache and query with if-none-match, the handler will query the database and get the exact same
    // response, thus expressjs
    // - calculate the same etag
    // - set status code to 304
    // - empty response body
    // - remove content-length, content-type, and vary headers
    await cache.clear();
    let response = await request(server).get(url).set('if-none-match', etag);

    // then
    expect(response.statusCode).toEqual(304);
    expect(response.body).toBeEmpty();
    expect(response.get('etag')).toEqual(etag);
    expect(response.get('cache-control')).toMatch(/^public, max-age=\d+$/);
    expect(response.headers).not.toHaveProperty('content-encoding');
    expect(response.headers).not.toHaveProperty('content-length');
    expect(response.headers).not.toHaveProperty('vary');

    // when query again without if-none-match, response from cache
    await cleanupData();
    response = await request(server).get(url).set('accept-encoding', '');

    // then
    expect(response.statusCode).toEqual(200);
    expect(response.text).toEqual(expectedBody);
    expect(response.header).toMatchObject({
      'content-length': `${expectedBody.length}`,
      'content-type': 'application/json; charset=utf-8',
      etag,
      vary: 'accept-encoding',
    });
    expect(response.get('cache-control')).toMatch(/^public, max-age=\d+$/);
  });

  test('HEAD - response from cache', async () => {
    // given
    await loadStakingRewardTransfers();
    const etag = await warmUpCache();
    await cleanupData();

    // when
    const response = await request(server).head(url).set('accept-encoding', '');

    // then
    expect(response.statusCode).toEqual(200);
    expect(response.body).toBeEmpty();
    expect(response.header).toMatchObject({
      'content-length': `${expectedBody.length}`,
      'content-type': 'application/json; charset=utf-8',
      etag,
      vary: 'accept-encoding',
    });
    expect(response.get('cache-control')).toMatch(/^public, max-age=\d+$/);
  });

  test('HEAD - response with compressed length from cache', async () => {
    // given
    await loadStakingRewardTransfers();
    const etag = await warmUpCache();
    await cleanupData();

    // when
    const response = await request(server).head(url).set('accept-encoding', 'gzip');

    // then
    expect(response.statusCode).toEqual(200);
    expect(response.body).toBeEmpty();
    expect(response.header).toMatchObject({
      'content-encoding': 'gzip',
      etag,
      vary: 'accept-encoding',
    });
    expect(response.get('cache-control')).toMatch(/^public, max-age=\d+$/);
    expect(Number(response.get('content-length'))).toBeLessThan(expectedBody.length);
  });

  test('HEAD - not modified from cache', async () => {
    // given
    await loadStakingRewardTransfers();
    const etag = await warmUpCache();
    await cleanupData();

    // when
    const response = await request(server).head(url).set('accept-encoding', '').set('if-none-match', etag);

    // then
    expect(response.statusCode).toEqual(304);
    expect(response.body).toBeEmpty();
    expect(response.get('etag')).toEqual(etag);
    expect(response.get('cache-control')).toMatch(/^public, max-age=\d+$/);
    expect(response.headers).not.toHaveProperty('content-encoding');
    expect(response.headers).not.toHaveProperty('content-length');
    expect(response.headers).not.toHaveProperty('vary');
  });
});
