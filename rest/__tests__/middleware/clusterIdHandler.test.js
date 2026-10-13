// SPDX-License-Identifier: Apache-2.0

import express from 'express';
import request from 'supertest';

import {Cache} from '../../cache.js';
import config from '../../config.js';
import {clusterIdHeader} from '../../constants.js';
import extendExpress from '../../extendExpress.js';
import integrationContainerOps from '../integrationContainerOps.js';
import {slowStepTimeoutMillis} from '../integrationUtils.js';
import {clusterId, clusterIdHandler, clusterIdKey, shareClusterId} from '../../middleware/clusterIdHandler.js';

const app = extendExpress(express());
app.useExt(clusterIdHandler);
app.get('/ok', (req, res) => {
  res.status(200).json({ok: true});
});
app.get('/error', (req, res) => {
  res.status(400).json({message: 'bad'});
});

describe('Cluster id header', () => {
  test('success response', async () => {
    const response = await request(app).get('/ok');

    expect(response.status).toBe(200);
    expect(response.headers[clusterIdHeader]).toBe(clusterId);
  });

  test('error response', async () => {
    const response = await request(app).get('/error');

    expect(response.status).toBe(400);
    expect(response.headers[clusterIdHeader]).toBe(clusterId);
  });
});

describe('Shared cluster id', () => {
  let cache;
  let redisContainer;
  const redisEnabled = config.redis.enabled;
  const redisUri = config.redis.uri;

  beforeAll(async () => {
    redisContainer = await integrationContainerOps.startRedisContainer();
    config.redis.enabled = true;
    config.redis.uri = `0.0.0.0:${redisContainer.getMappedPort(6379)}`;
    cache = new Cache();

    const started = Date.now();
    while (!cache.ready && Date.now() - started < 10000) {
      await new Promise((resolve) => setTimeout(resolve, 50));
    }
    if (!cache.ready) {
      throw new Error('Redis not ready');
    }
  }, slowStepTimeoutMillis);

  afterAll(async () => {
    await cache.stop();
    await redisContainer.stop({signal: 'SIGKILL', timeout: 3000});
    config.redis.enabled = redisEnabled;
    config.redis.uri = redisUri;
  }, slowStepTimeoutMillis);

  test('pods share one id, and a cleared value is replaced by one new shared id', async () => {
    const first = await shareClusterId(cache, '111');
    const second = await shareClusterId(cache, '222');
    expect(second).toBe(first);
    expect(second).toBe('111');

    await cache.redis.del(clusterIdKey);

    const restartedFirst = await shareClusterId(cache, '333');
    const restartedSecond = await shareClusterId(cache, '444');
    expect(restartedFirst).toBe('333');
    expect(restartedSecond).toBe(restartedFirst);
    expect(restartedSecond).not.toBe(first);
  });
});
