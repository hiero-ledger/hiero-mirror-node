// SPDX-License-Identifier: Apache-2.0

import config from '../config';
import {Cache} from '../cache';
import integrationContainerOps from './integrationContainerOps';
import {slowStepTimeoutMillis} from './integrationUtils';

let cache;
let redisContainer;

beforeAll(async () => {
  config.redis.enabled = true;
  redisContainer = await integrationContainerOps.startRedisContainer();
  config.redis.uri = `0.0.0.0:${redisContainer.getMappedPort(6379)}`;
  logger.info('Started Redis container');
}, slowStepTimeoutMillis);

afterAll(async () => {
  await cache.stop();
  await redisContainer.stop({signal: 'SIGKILL', timeout: 3000});
  logger.info('Stopped Redis container');
  config.redis.enabled = false;
}, slowStepTimeoutMillis);

beforeEach(async () => {
  cache = new Cache();
  await cache.clear();
}, slowStepTimeoutMillis);

const loader = (keys) => keys.map((key) => `v${key}`);
const keyMapper = (key) => `k${key}`;

describe('get', () => {
  test('All keys from database', async () => {
    const values = await cache.get(['1', '2', '3'], loader, keyMapper);
    expect(values).toEqual(['v1', 'v2', 'v3']);
  });

  test('Some keys from database', async () => {
    const values = await cache.get(['1', '2', '3'], loader, keyMapper);
    expect(values).toEqual(['v1', 'v2', 'v3']);

    const newValues = await cache.get(['2', '3', '4'], loader, keyMapper);
    expect(newValues).toEqual(['v2', 'v3', 'v4']);
  });

  test('No keys from database', async () => {
    const values = await cache.get(['1', '2', '3'], loader, keyMapper);
    expect(values).toEqual(['v1', 'v2', 'v3']);

    const newValues = await cache.get(['1', '2', '3'], (k) => [], keyMapper);
    expect(newValues).toEqual(['v1', 'v2', 'v3']);
  });

  test('No keys provided', async () => {
    const values = await cache.get([], loader, keyMapper);
    expect(values).toEqual([]);
  });
});

describe('Single key get/set', () => {
  test('Get undefined key', async () => {
    const value = await cache.getSingleWithTtl(undefined);
    expect(value).toBeUndefined();
  });

  test('Get non-existent key', async () => {
    const key = 'myKeyDoesNotExist';
    const value = await cache.getSingleWithTtl(key);
    expect(value).toBeUndefined();
  });

  test('Set and get object', async () => {
    const key = 'myKey';
    const objectToCache = {a: 5, b: 'some string', c: 'another string'};
    const setResult = await cache.setSingle(key, 5, objectToCache);
    expect(setResult).toEqual('OK');
    const objectWithTtlFromCache = await cache.getSingleWithTtl(key);
    expect(objectWithTtlFromCache.value).toEqual(objectToCache);
    expect(objectWithTtlFromCache.ttl).toBeGreaterThan(0);
  });
});
