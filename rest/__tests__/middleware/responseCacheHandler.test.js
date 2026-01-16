// SPDX-License-Identifier: Apache-2.0

import {jest} from '@jest/globals';

import config from '../../config';
import {Cache} from '../../cache';
import integrationContainerOps from '../integrationContainerOps';
import {slowStepTimeoutMillis} from '../integrationUtils';
import {cacheKeyGenerator, responseCacheCheckHandler, responseCacheUpdateHandler} from '../../middleware';
import {CachedApiResponse} from '../../model';
import {httpStatusCodes, responseBodyLabel, responseCacheKeyLabel} from '../../constants';
import {JSONStringify} from '../../utils';

let cache;
let compressEnabled;
let redisContainer;

const cacheControlMaxAge = 60;

beforeAll(async () => {
  config.redis.enabled = true;
  compressEnabled = config.cache.response.compress;
  redisContainer = await integrationContainerOps.startRedisContainer();
  logger.info('Started Redis container');

  config.redis.uri = `0.0.0.0:${redisContainer.getMappedPort(6379)}`;
  cache = new Cache();
}, slowStepTimeoutMillis);

afterAll(async () => {
  await cache.stop();
  await redisContainer.stop({signal: 'SIGKILL', timeout: 3000});
  logger.info('Stopped Redis container');
  config.cache.response.compress = compressEnabled;
  config.redis.enabled = false;
}, slowStepTimeoutMillis);

beforeEach(async () => {
  await cache.clear();
}, slowStepTimeoutMillis);

describe('Response cache middleware', () => {
  let mockRequest, mockResponse;
  const cachedHeaders = {
    'cache-control': `public, max-age=${cacheControlMaxAge}`,
    'content-type': 'application/json; charset=utf-8',
    vary: 'accept-encoding',
  };

  beforeEach(() => {
    mockRequest = {
      headers: {'accept-encoding': 'gzip'},
      ip: '127.0.0.1',
      method: 'GET',
      originalUrl: '/api/v1/accounts?account.id=gte:0.0.18&account.id=lt:0.0.21&limit=3',
      query: {'account.id': ['gte:0.0.18', 'lt:0.0.21'], limit: 3},
      requestStartTime: Date.now() - 5,
      route: {
        path: '/api/v1/accounts',
      },
      get: function (headerName) {
        return this.headers[headerName];
      },
    };

    mockResponse = {
      end: jest.fn(),
      getHeaders: jest.fn(),
      headers: {
        'cache-control': `public, max-age=${cacheControlMaxAge}`,
        'content-encoding': 'gzip',
        'content-type': 'application/json; charset=utf-8',
      },
      locals: [],
      removeHeader: jest.fn(),
      send: jest.fn(),
      set: jest.fn(),
      status: jest.fn(),
      setHeader: jest.fn(),
    };
  });

  describe('no compression', () => {
    beforeAll(() => {
      config.cache.response.compress = false;
    });

    describe('Cache check', () => {
      test('Cache miss', async () => {
        // The cache is empty, thus a cache miss is expected.
        await responseCacheCheckHandler(mockRequest, mockResponse, null);

        // Middleware must provide cache key in locals[] to be utilized downstream.
        const expectedCacheKey = cacheKeyGenerator(mockRequest);
        const cacheKey = mockResponse.locals[responseCacheKeyLabel];
        expect(cacheKey).toEqual(expectedCacheKey);

        // Middleware must not have handled the response directly.
        expect(mockResponse.send).not.toHaveBeenCalled();
        expect(mockResponse.set).not.toHaveBeenCalled();
        expect(mockResponse.status).not.toHaveBeenCalled();
      });

      test('Cache hit - client not cached - GET', async () => {
        const cachedBody = JSONStringify({a: 'b'});
        const cachedResponse = new CachedApiResponse(httpStatusCodes.OK.code, cachedHeaders, cachedBody, false);
        const cacheKey = cacheKeyGenerator(mockRequest);
        await cache.setSingle(cacheKey, cacheControlMaxAge, cachedResponse);

        await responseCacheCheckHandler(mockRequest, mockResponse, null);
        expect(mockResponse.send).toHaveBeenCalledWith(cachedBody);
        expect(mockResponse.set).toHaveBeenNthCalledWith(1, cachedHeaders);
        expect(mockResponse.status).toHaveBeenCalledWith(httpStatusCodes.OK.code);
      });

      test('Cache hit - client not cached - HEAD', async () => {
        const cachedBody = JSONStringify({a: 'b'});
        const cachedResponse = new CachedApiResponse(httpStatusCodes.OK.code, cachedHeaders, cachedBody, false);
        const cacheKey = cacheKeyGenerator(mockRequest);
        await cache.setSingle(cacheKey, cacheControlMaxAge, cachedResponse);

        mockRequest.method = 'HEAD';
        await responseCacheCheckHandler(mockRequest, mockResponse, null);
        expect(mockResponse.end).toHaveBeenCalled();
        expect(mockResponse.set).toHaveBeenNthCalledWith(1, cachedHeaders);
        expect(mockResponse.status).toHaveBeenCalledWith(httpStatusCodes.OK.code);
      });
    });

    describe('Cache update', () => {
      test('No cache key in locals', async () => {
        const cacheKey = cacheKeyGenerator(mockRequest);

        // No cache key in locals means don't cache the response
        await responseCacheUpdateHandler(mockRequest, mockResponse, null);
        const cachedResponse = await cache.getSingleWithTtl(cacheKey);
        expect(cachedResponse).toBeUndefined();
      });

      test('Do not cache negative results - 503', async () => {
        const cacheKey = cacheKeyGenerator(mockRequest);
        mockResponse.locals[responseCacheKeyLabel] = cacheKey;
        mockResponse.statusCode = 503;

        await responseCacheUpdateHandler(mockRequest, mockResponse, null);
        const cachedResponse = await cache.getSingleWithTtl(cacheKey);
        expect(cachedResponse).toBeUndefined();
      });

      test('Do not cache empty body', async () => {
        const cacheKey = cacheKeyGenerator(mockRequest);
        mockResponse.locals[responseCacheKeyLabel] = cacheKey;
        mockResponse.locals[responseBodyLabel] = '';
        mockResponse.statusCode = httpStatusCodes.OK.code;

        await responseCacheUpdateHandler(mockRequest, mockResponse, null);
        const cachedResponse = await cache.getSingleWithTtl(cacheKey);
        expect(cachedResponse).toBeUndefined();
      });

      test('Do not cache zero max-age', async () => {
        const cacheKey = cacheKeyGenerator(mockRequest);
        mockResponse.locals[responseCacheKeyLabel] = cacheKey;
        mockResponse.statusCode = httpStatusCodes.OK.code;
        mockResponse.headers = {'cache-control': `public, max-age=0`};
        mockResponse.getHeaders.mockImplementation(() => mockResponse.headers);

        await responseCacheUpdateHandler(mockRequest, mockResponse, null);
        const cachedResponse = await cache.getSingleWithTtl(cacheKey);
        expect(cachedResponse).toBeUndefined();
      });
    });
  });
});
