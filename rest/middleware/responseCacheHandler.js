// SPDX-License-Identifier: Apache-2.0

import Negotiator from 'negotiator';
import crypto from 'crypto';

import {Cache} from '../cache';
import {CachedApiResponse} from '../model';
import config from '../config';
import {
  contentTypeHeader,
  httpStatusCodes,
  requestStartTime,
  responseBodyLabel,
  responseCacheKeyLabel,
  responseHeadersLabel,
} from '../constants';

const CACHE_CONTROL_HEADER = 'cache-control';
const CACHE_CONTROL_REGEX = /^.*max-age=(\d+)/;
const CACHE_KEY_VERSION_SUFFIX = '-v1';
const CONDITIONAL_HEADER = 'if-none-match';
const CONTENT_ENCODING_HEADER = 'content-encoding';
const CONTENT_LENGTH_HEADER = 'content-length';
const DEFAULT_REDIS_EXPIRY = 1;
const ETAG_HEADER = 'etag';
const GZIP_ENCODING = 'gzip';
const VARY_HEADER = 'vary';

const getCache = (() => {
  let cache;
  return () => {
    if (!cache) {
      cache = new Cache();
    }
    return cache;
  };
})();

// Response middleware that checks for and returns cached response.
const responseCacheCheckHandler = async (req, res) => {
  const startTime = res.locals[requestStartTime] || Date.now();
  const responseCacheKey = cacheKeyGenerator(req);
  const cachedTtlAndValue = await getCache().getSingleWithTtl(responseCacheKey);

  if (!cachedTtlAndValue) {
    res.locals[responseCacheKeyLabel] = responseCacheKey;
    return;
  }

  const {ttl: redisTtl, value: redisValue} = cachedTtlAndValue;
  const cachedResponse = Object.assign(new CachedApiResponse(), redisValue);
  const conditionalHeader = req.get(CONDITIONAL_HEADER);
  const clientCached = conditionalHeader && conditionalHeader === cachedResponse.headers[ETAG_HEADER]; // 304
  const statusCode = clientCached ? httpStatusCodes.UNMODIFIED.code : cachedResponse.statusCode;
  const isHead = req.method === 'HEAD';

  let body;
  const headers = {
    ...cachedResponse.headers,
    ...{[CACHE_CONTROL_HEADER]: `public, max-age=${redisTtl}`},
  };

  if (isHead || clientCached) {
    if (clientCached) {
      delete headers[contentTypeHeader];
    } else {
      // For HEAD requests when status code is not 304, negotiate the encoding and set corresponding headers
      negotiate(cachedResponse, req, res);
    }
  } else {
    const useCompressed = negotiate(cachedResponse, req, res);
    body = useCompressed ? cachedResponse.getBody() : cachedResponse.getUncompressedBody();
  }

  res.set(headers);
  res.status(statusCode);
  if (body !== undefined) {
    res.send(body);
  } else {
    res.end();
  }

  const elapsed = Date.now() - startTime;
  logger.info(
    `${req.ip} ${req.method} ${req.originalUrl} from cache (ttl: ${redisTtl}) in ${elapsed} ms: ${statusCode}`
  );
};

// Response middleware that caches the completed response.
const responseCacheUpdateHandler = async (req, res) => {
  const responseCacheKey = res.locals[responseCacheKeyLabel];
  const responseBody = res.locals[responseBodyLabel];
  const isUnmodified = res.statusCode === httpStatusCodes.UNMODIFIED.code;

  if (responseBody && responseCacheKey && (isUnmodified || httpStatusCodes.isSuccess(res.statusCode))) {
    const ttl = getCacheControlExpiryOrDefault(res.getHeader(CACHE_CONTROL_HEADER));
    if (ttl > 0) {
      // There's no content-type header when code is 304, so get it from the default headers and override with the
      // optional headers from response.locals
      const headers = !isUnmodified
        ? res.getHeaders()
        : {
            ...config.response.headers.default,
            ...res.getHeaders(),
            ...(res.locals[responseHeadersLabel] ?? {}),
          };

      // Delete headers that will be re-computed when response later served by cache hit
      delete headers[CACHE_CONTROL_HEADER];
      delete headers[CONTENT_ENCODING_HEADER];
      delete headers[CONTENT_LENGTH_HEADER];
      delete headers[VARY_HEADER];

      const statusCode = isUnmodified ? httpStatusCodes.OK.code : res.statusCode;
      const cachedResponse = new CachedApiResponse(statusCode, headers, responseBody, shouldCompress(responseBody));
      await getCache().setSingle(responseCacheKey, ttl, cachedResponse);
    }
  }
};

const negotiate = (cachedResponse, req, res) => {
  res.setHeader(VARY_HEADER, 'accept-encoding');

  if (cachedResponse.compressed) {
    const negotiator = new Negotiator(req);
    if (negotiator.encoding([GZIP_ENCODING]) === GZIP_ENCODING) {
      res.setHeader(CONTENT_ENCODING_HEADER, GZIP_ENCODING);
      res.setHeader(CONTENT_LENGTH_HEADER, cachedResponse.getLength());
      return true;
    }
  }

  res.setHeader(CONTENT_LENGTH_HEADER, cachedResponse.getUncompressedLength());
  return false;
};

const shouldCompress = (body) => {
  return config.cache.response.compress && body.length >= config.cache.response.compressThreshold;
};

/*
 * Generate the cache key to access Redis. While Accept-Encoding is specified in the API response Vary
 * header, and therefore that request header value should be used as part of the cache key, the cache
 * implementation stores the response body as the original JSON object without any encoding applied. Thus it
 * is the same regardless of the accept encoding specified, and chosen by the compression middleware.
 *
 * Current key format:
 *
 *   path?query - In the future, this will utilize Edwin's request normalizer (9113).
 */
const cacheKeyGenerator = (req) => {
  return crypto.createHash('md5').update(req.originalUrl).digest('hex') + CACHE_KEY_VERSION_SUFFIX;
};

const getCacheControlExpiryOrDefault = (headerValue) => {
  if (headerValue) {
    const maxAge = headerValue.match(CACHE_CONTROL_REGEX);
    if (maxAge && maxAge.length === 2) {
      return parseInt(maxAge[1], 10);
    }
  }

  return DEFAULT_REDIS_EXPIRY;
};

export {cacheKeyGenerator, getCache, responseCacheCheckHandler, responseCacheUpdateHandler};
