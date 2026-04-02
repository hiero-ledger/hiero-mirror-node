// SPDX-License-Identifier: Apache-2.0

import {jest} from '@jest/globals';
import httpContext from 'express-http-context';
import * as constants from '../constants';
import Logger from '../logger';

describe('Logger', () => {
  let stdoutSpy;

  beforeEach(() => {
    stdoutSpy = jest.spyOn(process.stdout, 'write').mockImplementation(() => true);
    jest.spyOn(httpContext, 'get').mockReturnValue('1234');
  });

  afterEach(() => {
    stdoutSpy.mockRestore();
    jest.clearAllMocks();
  });

  const getOutput = () =>
    stdoutSpy.mock.calls.map(([arg]) => (typeof arg === 'string' ? arg : arg.toString())).join('');

  describe('log levels', () => {
    test('defaults to info level', () => {
      const testLogger = new Logger({bufferSize: 0});
      testLogger.debug('should not appear');
      expect(getOutput()).not.toMatch(/DEBUG/);
    });

    test('setLevel', () => {
      const testLogger = new Logger({bufferSize: 0});
      testLogger.setLevel('DEBUG');
      testLogger.trace('should not appear');
      testLogger.debug('should appear');
      expect(getOutput()).toMatch(/DEBUG.*should appear/);
      expect(getOutput()).not.toMatch(/TRACE/);
    });

    test('accepts log level as argument', () => {
      const testLogger = new Logger({level: 'debug', bufferSize: 0});
      testLogger.debug('should appear');
      expect(getOutput()).toMatch(/DEBUG/);
    });

    test('is case insensitive for log level', () => {
      const testLogger = new Logger({level: 'DEBUG', bufferSize: 0});
      testLogger.debug('should appear');
      expect(getOutput()).toMatch(/DEBUG/);
    });

    test('defaults to info for unknown log level', () => {
      const testLogger = new Logger({level: 'unknown', bufferSize: 0});
      testLogger.debug('should not appear');
      expect(getOutput()).not.toMatch(/DEBUG/);
    });
  });

  describe('output format', () => {
    var testLogger;

    beforeEach(() => {
      testLogger = new Logger({level: 'trace', bufferSize: 0});
    });

    test('uses Startup as requestId when httpContext has no value', () => {
      jest.spyOn(httpContext, 'get').mockReturnValue(null);
      testLogger.info('hello');
      expect(getOutput()).toMatch(/ Startup /);
    });

    test('log format is correct', () => {
      testLogger.info('hello world');
      expect(getOutput()).toMatch(/\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z INFO 1234 hello world\n/);
    });
  });

  describe('log levels', () => {
    test('trace writes TRACE', () => {
      const testLogger = new Logger({level: 'trace', bufferSize: 0});
      testLogger.trace('msg');
      expect(getOutput()).toMatch(/TRACE/);
    });

    test('debug writes DEBUG', () => {
      const testLogger = new Logger({level: 'debug', bufferSize: 0});
      testLogger.trace('msg');
      testLogger.debug('msg');
      expect(getOutput()).not.toMatch(/TRACE/);
      expect(getOutput()).toMatch(/DEBUG/);
    });

    test('info writes INFO', () => {
      const testLogger = new Logger({level: 'info', bufferSize: 0});
      testLogger.trace('msg');
      testLogger.debug('msg');
      testLogger.info('msg');
      expect(getOutput()).not.toMatch(/TRACE/);
      expect(getOutput()).not.toMatch(/DEBUG/);
      expect(getOutput()).toMatch(/INFO/);
    });

    test('warn writes WARN', () => {
      const testLogger = new Logger({level: 'warn', bufferSize: 0});
      testLogger.trace('msg');
      testLogger.debug('msg');
      testLogger.info('msg');
      testLogger.warn('msg');
      expect(getOutput()).not.toMatch(/TRACE/);
      expect(getOutput()).not.toMatch(/DEBUG/);
      expect(getOutput()).not.toMatch(/INFO/);
      expect(getOutput()).toMatch(/WARN/);
    });

    test('error writes ERROR', () => {
      const testLogger = new Logger({level: 'error', bufferSize: 0});
      testLogger.trace('msg');
      testLogger.debug('msg');
      testLogger.info('msg');
      testLogger.warn('msg');
      testLogger.error('msg');
      expect(getOutput()).not.toMatch(/TRACE/);
      expect(getOutput()).not.toMatch(/DEBUG/);
      expect(getOutput()).not.toMatch(/INFO/);
      expect(getOutput()).not.toMatch(/INFO/);
      expect(getOutput()).toMatch(/ERROR/);
    });
  });

  describe('level filtering', () => {
    test('does not write below configured level', () => {
      const testLogger = new Logger({level: 'warn', bufferSize: 0});
      testLogger.info('should not appear');
      testLogger.debug('should not appear');
      testLogger.trace('should not appear');
      expect(getOutput()).toBe('');
    });

    test('writes at and above configured level', () => {
      const testLogger = new Logger({level: 'warn', bufferSize: 0});
      testLogger.warn('warn msg');
      testLogger.error('error msg');
      const out = getOutput();
      expect(out).toMatch(/warn msg/);
      expect(out).toMatch(/error msg/);
    });
  });

  describe('error with stack trace', () => {
    var testLogger;
    beforeEach(() => {
      testLogger = new Logger({level: 'error', bufferSize: 0});
    });

    test('includes stack trace when err is provided', () => {
      const err = new Error('something failed');
      testLogger.error('request failed', err);
      const out = getOutput();
      expect(out).toMatch(/request failed/);
      expect(out).toMatch(/Error: something failed/);
    });

    test('stack trace is on a new line', () => {
      const err = new Error('boom');
      testLogger.error('failed', err);
      expect(getOutput()).toMatch(/failed\nError: boom/);
    });

    test('no stack trace when err is not provided', () => {
      testLogger.error('plain error');
      const out = getOutput();
      expect(out).toMatch(/plain error/);
      expect(out).not.toMatch(/Error:/);
    });

    test('no stack trace when err has no stack', () => {
      const err = {message: 'no stack'};
      testLogger.error('plain error', err);
      expect(getOutput()).not.toMatch(/at /);
    });
  });

  describe('isTraceEnabled', () => {
    test('returns true when level is trace', () => {
      const testLogger = new Logger({level: 'trace', bufferSize: 0});
      expect(testLogger.isTraceEnabled()).toBe(true);
    });

    test('returns false when level is debug', () => {
      const testLogger = new Logger({level: 'debug', bufferSize: 0});
      expect(testLogger.isTraceEnabled()).toBe(false);
    });
  });

  describe('isDebugEnabled', () => {
    test('returns true when level is trace', () => {
      const testLogger = new Logger({level: 'trace', bufferSize: 0});
      expect(testLogger.isDebugEnabled()).toBe(true);
    });

    test('returns true when level is debug', () => {
      const testLogger = new Logger({level: 'debug', bufferSize: 0});
      expect(testLogger.isDebugEnabled()).toBe(true);
    });

    test('returns false when level is info', () => {
      const testLogger = new Logger({level: 'info', bufferSize: 0});
      expect(testLogger.isDebugEnabled()).toBe(false);
    });
  });

  describe('buffering', () => {
    test('does not write to stdout before flush when buffered', () => {
      const testLogger = new Logger({level: 'info', bufferSize: 4096});
      testLogger.info('hello');
      expect(stdoutSpy).not.toHaveBeenCalled();
    });

    test('writes immediately when bufferSize is 0', () => {
      const testLogger = new Logger({level: 'info', bufferSize: 0});
      testLogger.info('hello');
      expect(stdoutSpy).toHaveBeenCalledTimes(1);
    });

    test('writes directly when line exceeds buffer size', () => {
      const testLogger = new Logger({level: 'info', bufferSize: 4096});
      const longMsg = 'x'.repeat(5000);
      testLogger.info(longMsg);
      expect(stdoutSpy).toHaveBeenCalled();
      expect(getOutput()).toMatch(/x{5000}/);
    });

    test('flushes existing buffer before writing oversized line', () => {
      const testLogger = new Logger({level: 'info', bufferSize: 4096});
      testLogger.info('buffered first');
      const longMsg = 'x'.repeat(5000);
      testLogger.info(longMsg);
      expect(stdoutSpy).toHaveBeenCalledTimes(2);
      const out = getOutput();
      expect(out).toMatch(/buffered first/);
      expect(out).toMatch(/x{5000}/);
    });
  });
});
