// SPDX-License-Identifier: Apache-2.0

import config from '../config';
import {Cache} from '../cache';
import {slowStepTimeoutMillis} from './integrationUtils';

let cache;

beforeAll(async () => {
  config.redis.enabled = false;
  logger.info('Redis disabled');
}, slowStepTimeoutMillis);

beforeEach(async () => {
  cache = new Cache();
}, slowStepTimeoutMillis);

const loader = (keys) => keys.map((key) => `v${key}`);
const keyMapper = (key) => `k${key}`;

describe('Redis disabled', () => {
  test('get', async () => {
    const values = await cache.get(['1', '2', '3'], loader, keyMapper);
    expect(values).toEqual(['v1', 'v2', 'v3']);
  });

  test('getSingleWithTtl', async () => {
    const key = 'myKey';
    const value = await cache.getSingleWithTtl(key);
    expect(value).toBeUndefined();
    const setResult = await cache.setSingle(key, 5, 'someValue');
    expect(setResult).toBeUndefined();
  });
});
