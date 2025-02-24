// SPDX-License-Identifier: Apache-2.0

/**
 * API response cached in Redis.
 */
class CachedApiResponse {
  constructor(statusCode, headers, body, compressed) {
    this.statusCode = statusCode;
    this.body = body;
    this.headers = headers;
    this.compressed = compressed;
  }
}

export default CachedApiResponse;
