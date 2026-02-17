// SPDX-License-Identifier: Apache-2.0

import Redis from 'ioredis';
import config from './config';
import _ from 'lodash';
import {JSONParse, JSONStringify} from './utils';

export class Cache {
  constructor() {
    const {redis: redisConfig} = config;
    const {sentinel, uri} = redisConfig;
    const sentinelOptions = sentinel.enabled
      ? {
          name: sentinel.name,
          sentinelPassword: sentinel.password,
          sentinels: [{host: sentinel.host, port: sentinel.port}],
        }
      : {};
    const options = {
      commandTimeout: redisConfig.commandTimeout,
      connectTimeout: redisConfig.connectTimeout,
      enableAutoPipelining: true,
      enableOfflineQueue: true,
      enableReadyCheck: true,
      keepAlive: 30000,
      lazyConnect: !redisConfig.enabled,
      maxRetriesPerRequest: redisConfig.maxRetriesPerRequest,
      retryStrategy: (attempt) => {
        this.ready = false;

        if (!redisConfig.enabled) {
          return null;
        }

        return Math.min(attempt * 2000, redisConfig.maxBackoff);
      },
      ...sentinelOptions,
    };
    const uriSanitized = uri.replaceAll(RegExp('(?<=//).*:.+@', 'g'), '***:***@');
    this.ready = false;

    this.redis = new Redis(uri, options)
      .on('connect', () => logger.info(`Connected to ${uriSanitized}`))
      .on('error', (err) => logger.error(`Error connecting to ${uriSanitized}: ${err.message}`))
      .on('ready', () => {
        this.#setConfig('maxmemory', redisConfig.maxMemory);
        this.#setConfig('maxmemory-policy', redisConfig.maxMemoryPolicy);
        this.ready = true;
      });
  }

  #setConfig(key, value) {
    this.redis
      .config('SET', key, value)
      .catch((e) => logger.warn(`Unable to set Redis ${key} to ${value}: ${e.message}`));
  }

  async clear() {
    return this.redis.flushall();
  }

  async getSingleWithTtl(key) {
    if (!this.ready) {
      return undefined;
    }

    const result = await this.redis
      .multi()
      .ttl(key)
      .get(key)
      .exec()
      .catch((err) => logger.warn(`Redis error during ttl/get: ${err.message}`));

    // result is [[null, ttl], [null, value]], with value === null on cache miss.
    const rawValue = result[1][1];
    if (rawValue) {
      return {ttl: result[0][1], value: JSONParse(rawValue)};
    }

    return undefined;
  }

  async setSingle(key, expiry, value) {
    if (!this.ready) {
      return undefined;
    }

    return this.redis
      .setex(key, expiry, JSONStringify(value))
      .catch((err) => logger.warn(`Redis error during set: ${err.message}`));
  }

  async get(keys, loader, keyMapper = (k) => (k ? k.toString() : k)) {
    if (_.isEmpty(keys)) {
      return [];
    }
    if (!this.ready) {
      return loader(keys);
    }

    const buffers =
      (await this.redis
        .mgetBuffer(_.map(keys, keyMapper))
        .catch((err) => logger.warn(`Redis error during mget: ${err.message}`))) || new Array(keys.length);
    const values = buffers.map((t) => JSONParse(t));

    let i = 0;
    const missingKeys = keys.filter(() => _.isNil(values[i++]));

    if (missingKeys.length > 0) {
      const missing = await loader(missingKeys);
      const newValues = [];
      let j = 0;

      missing.forEach((value) => {
        // Update missing values in Redis array
        for (; j < values.length; j++) {
          if (_.isNil(values[j])) {
            values[j] = value;
            newValues.push(keyMapper(keys[j]));
            newValues.push(JSONStringify(value));
            break;
          }
        }
      });

      if (newValues.length > 0) {
        this.redis.mset(newValues).catch((err) => logger.warn(`Redis error during mset: ${err.message}`));
      }
    }

    if (logger.isDebugEnabled()) {
      const count = keys.length - missingKeys.length;
      logger.debug(`Redis returned ${count} of ${keys.length} keys`);
    }

    return values;
  }

  async stop() {
    return this.redis.quit();
  }
}
