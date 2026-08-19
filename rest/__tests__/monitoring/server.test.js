// SPDX-License-Identifier: Apache-2.0

import {formatRequestLog, sanitize} from '../../monitoring/logFormat';

describe('sanitize', () => {
  test('Leaves printable text unchanged', () => {
    expect(sanitize('abc')).toBe('abc');
    expect(sanitize('/api/v1/status?result=failed')).toBe('/api/v1/status?result=failed');
    expect(sanitize('10.0.0.1')).toBe('10.0.0.1');
  });

  test('Replaces control characters', () => {
    expect(sanitize('a\rb\nc\td')).toBe('a_b_c_d');
    expect(sanitize('error\r\nWARN injected')).toBe('error__WARN injected');
    expect(sanitize('\u0000injected')).toBe('_injected');
  });

  test('Handles null and undefined', () => {
    expect(sanitize(null)).toBeNull();
    expect(sanitize(undefined)).toBeUndefined();
  });
});

describe('formatRequestLog', () => {
  const req = {
    ip: '127.0.0.1',
    method: 'GET',
    originalUrl: '/api/v1/status',
  };

  test('Formats a normal request', () => {
    expect(formatRequestLog(req, 200, 3, 4)).toBe('127.0.0.1 GET /api/v1/status returned 200: 3/4 tests passed');
  });

  test('Sanitizes originalUrl control characters', () => {
    const logged = formatRequestLog({...req, originalUrl: '/api/v1/status\r\nWARN injected'}, 200, 1, 1);
    expect(logged).not.toMatch(/[\r\n\t\0]/);
    expect(logged).toContain('/api/v1/status__WARN injected');
    expect(logged).toContain('returned 200: 1/1 tests passed');
  });

  test('Sanitizes client IP control characters', () => {
    const logged = formatRequestLog({...req, ip: '10.0.0.1\nINFO fake'}, 409, 0, 2);
    expect(logged).not.toMatch(/[\r\n]/);
    expect(logged).toContain('10.0.0.1_INFO fake');
    expect(logged).toContain('returned 409: 0/2 tests passed');
  });

  test('Sanitizes named status path control characters', () => {
    const logged = formatRequestLog(
      {
        ip: '127.0.0.1',
        method: 'GET',
        originalUrl: '/api/v1/status/mainnet\r\nWARN injected?result=all',
      },
      200,
      5,
      5
    );
    expect(logged).not.toMatch(/[\r\n\t\0]/);
    expect(logged).toBe(
      '127.0.0.1 GET /api/v1/status/mainnet__WARN injected?result=all returned 200: 5/5 tests passed'
    );
  });
});
