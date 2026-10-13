// SPDX-License-Identifier: Apache-2.0

import crypto from 'crypto';

import config from '../config.js';
import {clusterIdHeader} from '../constants.js';

// Shared by every REST pod through Redis, with no expiry. Pod restarts reuse it. A new id is created only after Redis
// loses the key, and the next sync makes the pods agree on that new id again.
const clusterIdKey = 'rest:clusterId';
const refreshMillis = 30_000;

let clusterId = String(crypto.randomInt(1, 2 ** 31));
let refreshTimer;

const shareClusterId = async (cache, localId) => {
  const shared = await cache.setIfAbsent(clusterIdKey, localId);
  return shared ?? localId;
};

const syncClusterId = async (cache) => {
  clusterId = await shareClusterId(cache, clusterId);
  return clusterId;
};

const waitUntilReady = (cache) =>
  new Promise((resolve) => {
    if (cache.ready || cache.redis.status === 'ready') {
      resolve();
      return;
    }

    cache.redis.once('ready', () => resolve());
  });

const initClusterId = async () => {
  if (!config.redis.enabled) {
    logger.info(`Cluster id: ${clusterId}`);
    return clusterId;
  }

  const {Cache} = await import('../cache.js');
  const cache = new Cache();
  await waitUntilReady(cache);
  await syncClusterId(cache);

  if (!refreshTimer) {
    refreshTimer = setInterval(() => {
      if (!cache.ready) {
        return;
      }
      syncClusterId(cache).catch((err) => logger.warn(`Unable to refresh cluster id: ${err.message}`));
    }, refreshMillis);
    refreshTimer.unref();
  }

  logger.info(`Cluster id: ${clusterId}`);
  return clusterId;
};

const clusterIdHandler = async (req, res) => {
  res.set(clusterIdHeader, clusterId);
};

export {clusterId, clusterIdHandler, clusterIdKey, initClusterId, shareClusterId};
