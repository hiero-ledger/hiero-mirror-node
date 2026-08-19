// SPDX-License-Identifier: Apache-2.0

import {jest} from '@jest/globals';

import {
  DbError,
  FileDecodeError,
  InvalidArgumentError,
  InvalidClauseError,
  InvalidConfigError,
  NotFoundError,
} from '../../errors';
import {handleError, handleUncaughtException} from '../../middleware/httpErrorHandler';

describe('Server error handler', () => {
  test('Throws Error for non rest error', () => {
    const exception = () => handleUncaughtException(new InvalidConfigError('Bad Config'));
    expect(exception).toThrow(InvalidConfigError);
  });

  test('Does not throw error for rest error', () => {
    const exception = () => {
      handleUncaughtException(new DbError());
      handleUncaughtException(new FileDecodeError());
      handleUncaughtException(new InvalidArgumentError());
      handleUncaughtException(new InvalidClauseError());
      handleUncaughtException(new NotFoundError());
    };

    expect(exception).not.toThrow(Error);
  });
});

describe('handleError logging', () => {
  let mockRequest;
  let mockResponse;
  let warnSpy;
  let errorSpy;

  beforeEach(() => {
    mockRequest = {
      ip: '127.0.0.1',
      method: 'GET',
      originalUrl: '/api/v1/accounts',
    };
    mockResponse = {
      locals: {},
      json: jest.fn(),
      status: jest.fn().mockReturnThis(),
    };
    warnSpy = jest.spyOn(logger, 'warn').mockImplementation(() => {});
    errorSpy = jest.spyOn(logger, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  test('Sanitizes originalUrl and error message control characters', async () => {
    mockRequest.originalUrl = '/api/v1/accounts\r\nWARN injected';
    const err = new InvalidArgumentError('bad\ninput');

    await handleError(err, mockRequest, mockResponse);

    const logged = warnSpy.mock.calls[0][0];
    expect(logged).not.toMatch(/[\r\n\t\0]/);
    expect(logged).toContain('/api/v1/accounts__WARN injected');
    expect(logged).toContain('bad_input');
    expect(mockResponse.json).toHaveBeenCalledWith({
      _status: {messages: [{message: 'bad\ninput'}]},
    });
  });

  test('Sanitizes client IP control characters', async () => {
    mockRequest.ip = '10.0.0.1\r\nWARN injected';
    const err = new InvalidArgumentError('invalid');

    await handleError(err, mockRequest, mockResponse);

    const logged = warnSpy.mock.calls[0][0];
    expect(logged).not.toMatch(/[\r\n]/);
    expect(logged).toContain('10.0.0.1__WARN injected');
  });
});
