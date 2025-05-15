// SPDX-License-Identifier: Apache-2.0

import crypto from 'crypto';
import cryptoRandomString from 'crypto-random-string';

import {CachedApiResponse} from '../../model';
import {gzipSync} from 'zlib';

describe('CachedApiResponse', () => {
  describe('No compression', () => {
    const cachedApiResponse = new CachedApiResponse(200, {}, 'sample body', false);

    test('getBody', () => {
      expect(cachedApiResponse.getBody()).toEqual('sample body');
    });

    test('getLength', () => {
      expect(cachedApiResponse.getLength()).toEqual(11);
    });

    test('getUncompressedBody', () => {
      expect(cachedApiResponse.getUncompressedBody()).toBe('sample body');
    });

    test('getUncompressedLength', () => {
      expect(cachedApiResponse.getUncompressedLength()).toEqual(11);
    });
  });

  const inputs = Array(6)
    .fill(0)
    .map((_) => {
      return cryptoRandomString({length: crypto.randomInt(1, 64), type: 'alphanumeric'});
    });

  describe.each(inputs)('Compress body "%s"', (body) => {
    const cachedApiResponse = new CachedApiResponse(200, {}, body, true);
    const compressed = gzipSync(body);

    test('getBody', () => {
      expect(cachedApiResponse.getBody()).toEqual(compressed);
    });

    test('getLength', () => {
      expect(cachedApiResponse.getLength()).toEqual(compressed.length);
    });

    test('getUncompressedBody', () => {
      expect(cachedApiResponse.getUncompressedBody().toString()).toBe(body);
    });

    test('getUncompressedLength', () => {
      expect(cachedApiResponse.getUncompressedLength()).toEqual(body.length);
    });
  });
});
