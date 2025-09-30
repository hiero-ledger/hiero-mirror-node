// SPDX-License-Identifier: Apache-2.0

import config from '../config';
import {Cache} from '../cache';
import integrationContainerOps from './integrationContainerOps';
import {slowStepTimeoutMillis} from './integrationUtils';

let cache;
let redisContainer;

beforeAll(async () => {
  config.redis.enabled = true;
  config.redis.uri = 'redis://invalid:6379';
  redisContainer = await integrationContainerOps.startRedisContainer();
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
}, slowStepTimeoutMillis);

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
