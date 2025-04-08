// SPDX-License-Identifier: Apache-2.0

import config from '../config.js';
import {Cache} from '../cache.js';
import {RedisContainer} from '@testcontainers/redis';
import {defaultBeforeAllTimeoutMillis} from './integrationUtils';

let cache;
let redisContainer;

beforeAll(async () => {
  config.redis.enabled = true;
  config.redis.uri = 'redis://invalid:6379';
  redisContainer = await new RedisContainer().withStartupTimeout(20000).start();
  logger.info('Started Redis container');
}, defaultBeforeAllTimeoutMillis);

afterAll(async () => {
  await cache.stop();
  await redisContainer.stop({signal: 'SIGKILL', t: 5});
  logger.info('Stopped Redis container');
});

beforeEach(async () => {
  cache = new Cache();
});

const loader = (keys) => keys.map((key) => `v${key}`);
const keyMapper = (key) => `k${key}`;

describe('Misconfigured Redis URL', () => {
  test('get', async () => {
    const values = await cache.get(['1', '2', '3'], loader, keyMapper);
    expect(values).toEqual(['v1', 'v2', 'v3']);
  });

  test('getSingleWithTtl', async () => {
    const key = 'myKey';
    const value = await cache.getSingleWithTtl(key);
    expect(value).toBeUndefined();
    const setResult = await cache.setSingle(key, 'someValue');
    expect(setResult).toBeUndefined();
  });
});
