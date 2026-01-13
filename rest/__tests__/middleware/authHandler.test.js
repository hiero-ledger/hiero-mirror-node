// SPDX-License-Identifier: Apache-2.0

import {jest} from '@jest/globals';
import httpContext from 'express-http-context';

import {authHandler} from '../../middleware/authHandler.js';
import config from '../../config.js';
import {httpStatusCodes, userLimitLabel} from '../../constants.js';

describe('authHandler middleware', () => {
  let mockRequest, mockResponse;

  beforeEach(() => {
    mockRequest = {
      headers: {},
    };
    mockResponse = {
      status: jest.fn().mockReturnThis(),
      json: jest.fn(),
    };

    // Mock config.users
    config.users = [
      {username: 'testuser', password: 'testpass', limit: 200},
      {username: 'premium', password: 'secret', limit: 500},
    ];

    // Mock httpContext
    jest.spyOn(httpContext, 'set');
    jest.spyOn(httpContext, 'get').mockReturnValue(undefined);
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  test('No Authorization header - proceeds without authentication', async () => {
    await authHandler(mockRequest, mockResponse);

    expect(mockResponse.status).not.toHaveBeenCalled();
  });

  test('Valid credentials - sets custom limit in httpContext', async () => {
    const credentials = Buffer.from('testuser:testpass').toString('base64');
    mockRequest.headers.authorization = `Basic ${credentials}`;

    await authHandler(mockRequest, mockResponse);

    expect(httpContext.set).toHaveBeenCalledWith(userLimitLabel, 200);
    expect(mockResponse.status).not.toHaveBeenCalled();
  });

  test('Invalid credentials - returns 401', async () => {
    const credentials = Buffer.from('testuser:wrongpass').toString('base64');
    mockRequest.headers.authorization = `Basic ${credentials}`;

    await authHandler(mockRequest, mockResponse);

    expect(mockResponse.status).toHaveBeenCalledWith(401);
    expect(mockResponse.json).toHaveBeenCalledWith({
      _status: {
        messages: [{message: 'Invalid credentials'}],
      },
    });
  });

  test('Invalid Authorization header format - returns 401', async () => {
    mockRequest.headers.authorization = 'Bearer invalidtoken';

    await authHandler(mockRequest, mockResponse);

    expect(mockResponse.status).toHaveBeenCalledWith(401);
    expect(mockResponse.json).toHaveBeenCalledWith({
      _status: {
        messages: [{message: 'Invalid Authorization header format'}],
      },
    });
  });

  test('Missing password in credentials - returns 401', async () => {
    const credentials = Buffer.from('testuser').toString('base64');
    mockRequest.headers.authorization = `Basic ${credentials}`;

    await authHandler(mockRequest, mockResponse);

    expect(mockResponse.status).toHaveBeenCalledWith(401);
  });

  test('User without limit configured - proceeds but does not set limit', async () => {
    config.users = [{username: 'nolimit', password: 'pass'}];
    const credentials = Buffer.from('nolimit:pass').toString('base64');
    mockRequest.headers.authorization = `Basic ${credentials}`;

    await authHandler(mockRequest, mockResponse);

    expect(httpContext.set).not.toHaveBeenCalled();
    expect(mockResponse.status).not.toHaveBeenCalled();
  });

  test('Password with colon - splits only on first colon', async () => {
    config.users = [{username: 'user', password: 'pass:word:123', limit: 100}];
    const credentials = Buffer.from('user:pass:word:123').toString('base64');
    mockRequest.headers.authorization = `Basic ${credentials}`;

    await authHandler(mockRequest, mockResponse);

    expect(httpContext.set).toHaveBeenCalledWith(userLimitLabel, 100);
    expect(mockResponse.status).not.toHaveBeenCalled();
  });
});
