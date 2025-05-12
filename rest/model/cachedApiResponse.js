// SPDX-License-Identifier: Apache-2.0

import {gzipSync, unzipSync} from 'zlib';

/**
 * API response cached in Redis.
 */
class CachedApiResponse {
  constructor(statusCode, headers, body, compress) {
    this.statusCode = statusCode;
    this.body = compress ? gzipSync(body).toString('base64url') : body;
    this.headers = headers;
    this.compressed = compress;

    if (compress) {
      // uncompressed data length, only set if compressed
      this.length = body.length;
    }
  }

  getBody() {
    return this.compressed ? Buffer.from(this.body, 'base64url') : this.body;
  }

  getLength() {
    return this.compressed ? Math.floor((this.body.length * 3) / 4) : this.body.length;
  }

  getUncompressedBody() {
    return this.compressed ? unzipSync(Buffer.from(this.body, 'base64url')) : this.body;
  }

  getUncompressedLength() {
    return this.compressed ? this.length : this.body.length;
  }
}

export default CachedApiResponse;
