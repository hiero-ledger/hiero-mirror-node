// SPDX-License-Identifier: Apache-2.0

import {jest} from '@jest/globals';
import {createRequire} from 'module';
import httpContext from 'express-http-context';

import config from '../../config.js';
import {httpStatusCodes, userLimitLabel} from '../../constants.js';

// Load the real tsscmp through CJS require so the mock registry below does not
// recurse into itself. tsscmp is a CommonJS package, so this resolves the genuine
// implementation and comparison semantics stay unchanged.
const require = createRequire(import.meta.url);
const actualTsscmp = require('tsscmp');

// Wrap tsscmp so invocations are countable. The count is what pins the constant-work
// property: a short-circuiting findUser performs N comparisons for an unknown username
// and N+1 for a known one, which is the timing oracle. Must be registered before
// authHandler.js is imported, hence the dynamic import that follows.
const tsscmpSpy = jest.fn(actualTsscmp);
jest.unstable_mockModule('tsscmp', () => ({default: tsscmpSpy}));

const {authHandler} = await import('../../middleware/authHandler.js');

const basicAuth = (username, password) => `Basic ${Buffer.from(`${username}:${password}`).toString('base64')}`;

describe('authHandler middleware', () => {
  let mockRequest, mockResponse;

  beforeEach(() => {
    tsscmpSpy.mockClear();

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
    mockRequest.headers.authorization = basicAuth('testuser', 'testpass');

    await authHandler(mockRequest, mockResponse);

    expect(httpContext.set).toHaveBeenCalledWith(userLimitLabel, 200);
    expect(mockResponse.status).not.toHaveBeenCalled();
  });

  test('Matching user is found regardless of position in the list', async () => {
    mockRequest.headers.authorization = basicAuth('premium', 'secret');

    await authHandler(mockRequest, mockResponse);

    expect(httpContext.set).toHaveBeenCalledWith(userLimitLabel, 500);
    expect(mockResponse.status).not.toHaveBeenCalled();
  });

  test('Invalid credentials - returns 401', async () => {
    mockRequest.headers.authorization = basicAuth('testuser', 'wrongpass');

    await authHandler(mockRequest, mockResponse);

    expect(mockResponse.status).toHaveBeenCalledWith(httpStatusCodes.UNAUTHORIZED.code);
    expect(mockResponse.json).toHaveBeenCalledWith({
      _status: {
        messages: [{message: 'Invalid credentials'}],
      },
    });
  });

  test('Unknown username - returns 401 identical to wrong password', async () => {
    mockRequest.headers.authorization = basicAuth('nosuchuser', 'testpass');

    await authHandler(mockRequest, mockResponse);

    expect(mockResponse.status).toHaveBeenCalledWith(httpStatusCodes.UNAUTHORIZED.code);
    expect(mockResponse.json).toHaveBeenCalledWith({
      _status: {
        messages: [{message: 'Invalid credentials'}],
      },
    });
    expect(httpContext.set).not.toHaveBeenCalled();
  });

  test('Invalid Authorization header format - proceeds without authentication', async () => {
    mockRequest.headers.authorization = 'Bearer invalidtoken';

    await authHandler(mockRequest, mockResponse);

    expect(mockResponse.status).not.toHaveBeenCalled();
  });

  test('User without limit configured - proceeds but does not set limit', async () => {
    config.users = [{username: 'nolimit', password: 'pass'}];
    mockRequest.headers.authorization = basicAuth('nolimit', 'pass');

    await authHandler(mockRequest, mockResponse);

    expect(httpContext.set).not.toHaveBeenCalled();
    expect(mockResponse.status).not.toHaveBeenCalled();
  });

  test('Password with colon - splits only on first colon', async () => {
    config.users = [{username: 'user', password: 'pass:word:123', limit: 100}];
    mockRequest.headers.authorization = basicAuth('user', 'pass:word:123');

    await authHandler(mockRequest, mockResponse);

    expect(httpContext.set).toHaveBeenCalledWith(userLimitLabel, 100);
    expect(mockResponse.status).not.toHaveBeenCalled();
  });

  describe('No users configured', () => {
    test('Empty users list - any credentials return 401 and perform no comparisons', async () => {
      config.users = [];
      mockRequest.headers.authorization = basicAuth('testuser', 'testpass');

      await authHandler(mockRequest, mockResponse);

      expect(mockResponse.status).toHaveBeenCalledWith(httpStatusCodes.UNAUTHORIZED.code);
      expect(mockResponse.json).toHaveBeenCalledWith({
        _status: {
          messages: [{message: 'Invalid credentials'}],
        },
      });
      expect(httpContext.set).not.toHaveBeenCalled();
      expect(tsscmpSpy).not.toHaveBeenCalled();
    });

    test('Empty users list - no Authorization header still proceeds unauthenticated', async () => {
      config.users = [];

      await authHandler(mockRequest, mockResponse);

      expect(mockResponse.status).not.toHaveBeenCalled();
      expect(tsscmpSpy).not.toHaveBeenCalled();
    });

    test('Undefined users - treated the same as an empty list', async () => {
      config.users = undefined;
      mockRequest.headers.authorization = basicAuth('testuser', 'testpass');

      await authHandler(mockRequest, mockResponse);

      expect(mockResponse.status).toHaveBeenCalledWith(httpStatusCodes.UNAUTHORIZED.code);
      expect(httpContext.set).not.toHaveBeenCalled();
      expect(tsscmpSpy).not.toHaveBeenCalled();
    });
  });

  describe('Constant-work comparison (timing oracle regression)', () => {
    const countComparisons = async (username, password) => {
      tsscmpSpy.mockClear();
      await authHandler({headers: {authorization: basicAuth(username, password)}}, mockResponse);
      return tsscmpSpy.mock.calls.length;
    };

    test('Failed auth performs identical comparison count for unknown and known usernames', async () => {
      const unknownUsername = await countComparisons('nosuchuser', 'testpass');
      const knownFirstUserWrongPassword = await countComparisons('testuser', 'wrongpass');
      const knownLastUserWrongPassword = await countComparisons('premium', 'wrongpass');

      expect(knownFirstUserWrongPassword).toEqual(unknownUsername);
      expect(knownLastUserWrongPassword).toEqual(unknownUsername);
      expect(unknownUsername).toEqual(2 * config.users.length);
    });

    test('Successful auth performs the same comparison count as failed auth', async () => {
      const failed = await countComparisons('nosuchuser', 'nopass');
      const succeededFirstUser = await countComparisons('testuser', 'testpass');
      const succeededLastUser = await countComparisons('premium', 'secret');

      expect(succeededFirstUser).toEqual(failed);
      expect(succeededLastUser).toEqual(failed);
    });

    test('Comparison count is independent of username and password length', async () => {
      const shortInputs = await countComparisons('a', 'b');
      const longInputs = await countComparisons('a'.repeat(512), 'b'.repeat(512));

      expect(longInputs).toEqual(shortInputs);
    });
  });
});
