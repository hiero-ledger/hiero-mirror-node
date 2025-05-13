// SPDX-License-Identifier: Apache-2.0

import {jest} from '@jest/globals';

import config from '../../config';
import {gzipSync, unzipSync} from 'zlib';
import {Cache} from '../../cache';
import {RedisContainer} from '@testcontainers/redis';
import {defaultBeforeAllTimeoutMillis} from '../integrationUtils';
import CachedApiResponse from '../../model/cachedApiResponse.js';
import {cacheKeyGenerator, responseCacheCheckHandler, responseCacheUpdateHandler, setCache} from '../../middleware';
import {httpStatusCodes, responseBodyLabel, responseCacheKeyLabel} from '../../constants';
import {JSONStringify} from '../../utils.js';

let cache;
let redisContainer;
let compressEnabled;

const cacheControlMaxAge = 60;

beforeAll(async () => {
  config.redis.enabled = true;
  compressEnabled = config.cache.response.compress;
  redisContainer = await new RedisContainer().withStartupTimeout(20000).start();
  logger.info('Started Redis container');
}, defaultBeforeAllTimeoutMillis);

afterAll(async () => {
  await cache.stop();
  await redisContainer.stop({signal: 'SIGKILL', t: 5});
  logger.info('Stopped Redis container');
  config.cache.response.compress = compressEnabled;
});

beforeEach(async () => {
  config.redis.uri = `0.0.0.0:${redisContainer.getMappedPort(6379)}`;
  cache = new Cache();
  setCache(cache);
  await cache.clear();
});

describe('Response cache middleware', () => {
  let mockRequest, mockResponse;
  const cachedHeaders = {
    'cache-control': `public, max-age=${cacheControlMaxAge}`,
    'content-type': 'application/json; charset=utf-8',
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
    beforeAll(async () => {
      config.cache.response.compress = false;
    }, defaultBeforeAllTimeoutMillis);

    describe('Cache check', () => {
      test('Cache miss', async () => {
        // The cache is empty, thus a cache miss is expected.
        await responseCacheCheckHandler(mockRequest, mockResponse, null);

        // Middleware must provide cache key in locals[] to be utilized downstream.
        const expectedCacheKey = cacheKeyGenerator(mockRequest);
        const cacheKey = mockResponse.locals[responseCacheKeyLabel];
        expect(cacheKey).toEqual(expectedCacheKey);

        // Middleware must not have handled the response directly.
        expect(mockResponse.send).not.toBeCalled();
        expect(mockResponse.set).not.toBeCalled();
        expect(mockResponse.status).not.toBeCalled();
      });

      test('Cache hit - client not cached - GET', async () => {
        const cachedBody = JSONStringify({a: 'b'});
        const cachedResponse = new CachedApiResponse(httpStatusCodes.OK.code, cachedHeaders, cachedBody, false);
        const cacheKey = cacheKeyGenerator(mockRequest);
        await cache.setSingle(cacheKey, cacheControlMaxAge, cachedResponse);

        await responseCacheCheckHandler(mockRequest, mockResponse, null);
        expect(mockResponse.send).toBeCalledWith(cachedBody);
        expect(mockResponse.set).toHaveBeenNthCalledWith(1, cachedHeaders);
        expect(mockResponse.status).toBeCalledWith(httpStatusCodes.OK.code);
      });

      test('Cache hit - client not cached - HEAD', async () => {
        const cachedBody = JSONStringify({a: 'b'});
        const cachedResponse = new CachedApiResponse(httpStatusCodes.OK.code, cachedHeaders, cachedBody, false);
        const cacheKey = cacheKeyGenerator(mockRequest);
        await cache.setSingle(cacheKey, cacheControlMaxAge, cachedResponse);

        mockRequest.method = 'HEAD';
        await responseCacheCheckHandler(mockRequest, mockResponse, null);
        expect(mockResponse.end).toBeCalled();
        expect(mockResponse.set).toHaveBeenNthCalledWith(1, cachedHeaders);
        expect(mockResponse.status).toBeCalledWith(httpStatusCodes.OK.code);
      });

      test('Cache hit - client cached - GET', async () => {
        cachedHeaders['etag'] = '12345';
        mockRequest.headers['if-none-match'] = cachedHeaders['etag'];
        const cachedBody = JSONStringify({a: 'b'});
        const cachedResponse = new CachedApiResponse(httpStatusCodes.OK.code, cachedHeaders, cachedBody, false);
        const cacheKey = cacheKeyGenerator(mockRequest);
        await cache.setSingle(cacheKey, cacheControlMaxAge, cachedResponse);

        await responseCacheCheckHandler(mockRequest, mockResponse, null);
        expect(mockResponse.end).toBeCalled();
        expect(mockResponse.set).toHaveBeenNthCalledWith(1, cachedHeaders);
        expect(mockResponse.status).toBeCalledWith(httpStatusCodes.UNMODIFIED.code);
      });

      test('Cache hit - client cached - HEAD', async () => {
        cachedHeaders['etag'] = '12345';
        mockRequest.headers['if-none-match'] = cachedHeaders['etag'];
        const cachedBody = JSONStringify({a: 'b'});
        const cachedResponse = new CachedApiResponse(httpStatusCodes.OK.code, cachedHeaders, cachedBody, false);
        const cacheKey = cacheKeyGenerator(mockRequest);
        await cache.setSingle(cacheKey, cacheControlMaxAge, cachedResponse);

        mockRequest.method = 'HEAD';
        await responseCacheCheckHandler(mockRequest, mockResponse, null);
        expect(mockResponse.end).toBeCalled();
        expect(mockResponse.set).toHaveBeenNthCalledWith(1, cachedHeaders);
        expect(mockResponse.status).toBeCalledWith(httpStatusCodes.UNMODIFIED.code);
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

      test.each([httpStatusCodes.OK.code, httpStatusCodes.UNMODIFIED.code])(
        'Cache successful response - %d',
        async (status) => {
          const cacheKey = cacheKeyGenerator(mockRequest);
          const expectedBody = JSONStringify({a: 'b'});

          mockResponse.locals[responseBodyLabel] = expectedBody;
          mockResponse.locals[responseCacheKeyLabel] = cacheKey;
          mockResponse.statusCode = status;
          mockResponse.getHeaders.mockImplementation(() => mockResponse.headers);

          await responseCacheUpdateHandler(mockRequest, mockResponse, null);
          const cachedResponse = await cache.getSingleWithTtl(cacheKey);
          expect(cachedResponse).not.toBeUndefined();

          expect(cachedResponse.ttl).toBeLessThanOrEqual(cacheControlMaxAge);
          expect(cachedResponse.value?.compressed).toEqual(false);
          expect(cachedResponse.value?.body).toEqual(expectedBody);

          const expectedHeaders = {'content-type': 'application/json; charset=utf-8'};
          expect(cachedResponse.value?.headers).toEqual(expectedHeaders);
        }
      );
    });
  });

  describe('with compression', () => {
    beforeAll(async () => {
      config.cache.response.compress = true;
    }, defaultBeforeAllTimeoutMillis);

    describe('Cache check', () => {
      test('Cache hit accepts gzip', async () => {
        // Place the expected response in the cache.
        const cachedBody = gzipSync(JSONStringify({a: 'b'}));
        const cachedResponse = new CachedApiResponse(httpStatusCodes.OK.code, cachedHeaders, cachedBody, true);
        const cacheKey = cacheKeyGenerator(mockRequest);
        await cache.setSingle(cacheKey, cacheControlMaxAge, cachedResponse);

        await responseCacheCheckHandler(mockRequest, mockResponse, null);
        expect(mockResponse.send).toBeCalledWith(cachedBody);
        expect(mockResponse.set).toHaveBeenNthCalledWith(1, cachedHeaders);
        expect(mockResponse.setHeader).toHaveBeenNthCalledWith(1, 'content-encoding', 'gzip');
        expect(mockResponse.status).toBeCalledWith(httpStatusCodes.OK.code);
      });

      test('Cache hit does not accept gzip', async () => {
        const cachedBody = JSONStringify({a: 'b'});

        // Place the expected response in the cache.
        const cachedResponse = new CachedApiResponse(
          httpStatusCodes.OK.code,
          cachedHeaders,
          gzipSync(cachedBody),
          true
        );
        const cacheKey = cacheKeyGenerator(mockRequest);
        await cache.setSingle(cacheKey, cacheControlMaxAge, cachedResponse);

        // accept-encoding zstd
        mockRequest.headers['accept-encoding'] = 'zstd';

        await responseCacheCheckHandler(mockRequest, mockResponse, null);
        expect(mockResponse.send).toBeCalledWith(cachedBody);
        expect(mockResponse.set).toHaveBeenNthCalledWith(1, cachedHeaders);
        expect(mockResponse.setHeader).not.toBeCalled();
        expect(mockResponse.status).toBeCalledWith(httpStatusCodes.OK.code);
      });
    });

    describe('Cache update', () => {
      test.each([httpStatusCodes.OK.code, httpStatusCodes.UNMODIFIED.code])(
        'Cache successful response - %d',
        async (status) => {
          const cacheKey = cacheKeyGenerator(mockRequest);
          const expectedBody = JSONStringify({a: 'b'});

          mockResponse.locals[responseBodyLabel] = expectedBody;
          mockResponse.locals[responseCacheKeyLabel] = cacheKey;
          mockResponse.statusCode = status;
          mockResponse.getHeaders.mockImplementation(() => mockResponse.headers);

          await responseCacheUpdateHandler(mockRequest, mockResponse, null);
          const cachedResponse = await cache.getSingleWithTtl(cacheKey);
          expect(cachedResponse).not.toBeUndefined();

          expect(cachedResponse.ttl).toBeLessThanOrEqual(cacheControlMaxAge);
          expect(cachedResponse.value?.compressed).toEqual(true);
          expect(unzipSync(Buffer.from(cachedResponse.value?.body)).toString()).toEqual(expectedBody);

          const expectedHeaders = {'content-type': 'application/json; charset=utf-8'};
          expect(cachedResponse.value?.headers).toEqual(expectedHeaders);
        }
      );
    });
  });
});
