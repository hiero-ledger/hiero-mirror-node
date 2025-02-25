// SPDX-License-Identifier: Apache-2.0

import {jest} from '@jest/globals';

import config from '../../config';
import {NotFoundError} from '../../errors';
import {responseHandler} from '../../middleware';
import {JSONStringify} from '../../utils';
import {contentTypeHeader, responseHeadersLabel} from '../../constants.js';

const {
  response: {headers},
} = config;

const nextMiddleware = () => {};

describe('Response middleware', () => {
  let mockRequest, mockResponse, responseData;
  const cacheControl = 'cache-control';

  beforeEach(() => {
    responseData = {transactions: [], links: {next: null}};
    mockRequest = {
      ip: '127.0.0.1',
      method: 'GET',
      originalUrl: '/api/v1/transactions/0.0.10-1234567890-000000001',
      requestStartTime: Date.now() - 5,
      route: {
        path: '/api/v1/transactions/:transactionId',
      },
    };
    mockResponse = {
      locals: {
        responseData: responseData,
        statusCode: 200,
      },
      get: jest.fn(),
      send: jest.fn(),
      set: jest.fn(),
      status: jest.fn(),
    };
  });

  test('No response data', async () => {
    mockResponse.locals.responseData = undefined;
    await expect(responseHandler(mockRequest, mockResponse, nextMiddleware)).rejects.toThrow(NotFoundError);
  });

  test('Custom headers', async () => {
    mockResponse.get.mockReturnValue('application/json; charset=utf-8');
    await responseHandler(mockRequest, mockResponse, nextMiddleware);
    expect(mockResponse.send).toHaveBeenCalledWith(JSONStringify(responseData));
    expect(mockResponse.set).toHaveBeenNthCalledWith(1, headers.default);
    expect(mockResponse.status).toHaveBeenCalledWith(mockResponse.locals.statusCode);
  });

  test('Default headers', async () => {
    mockRequest.route.path = '/api/v1/accounts';
    mockResponse.get.mockReturnValue('application/json; charset=utf-8');
    await responseHandler(mockRequest, mockResponse, nextMiddleware);
    expect(mockResponse.send).toHaveBeenCalledWith(JSONStringify(responseData));
    expect(mockResponse.set).toHaveBeenCalledWith({
      [cacheControl]: headers.path[mockRequest.route.path][cacheControl],
      [contentTypeHeader]: 'application/json; charset=utf-8',
    });
    expect(mockResponse.status).toHaveBeenCalledWith(mockResponse.locals.statusCode);
  });

  test('Custom Content-Type', async () => {
    mockResponse.locals[responseHeadersLabel] = {[contentTypeHeader]: 'text/plain; charset=utf-8'};
    mockResponse.locals.responseData = '123';
    await responseHandler(mockRequest, mockResponse, nextMiddleware);
    expect(mockResponse.send).toHaveBeenCalledWith(mockResponse.locals.responseData);
    expect(mockResponse.set).toHaveBeenNthCalledWith(1, {
      [cacheControl]: headers.default[cacheControl],
      [contentTypeHeader]: mockResponse.locals[responseHeadersLabel][contentTypeHeader],
    });
    expect(mockResponse.status).toHaveBeenCalledWith(mockResponse.locals.statusCode);
  });

  test('should set the Link next header and confirm it exists', async () => {
    const MOCK_URL = 'http://mock.url/next';
    mockResponse.locals.responseData.links.next = MOCK_URL;
    const assertNextValue = `<${MOCK_URL}>; rel=\"next\"`;
    await responseHandler(mockRequest, mockResponse, nextMiddleware);
    expect(mockResponse.set).toHaveBeenNthCalledWith(2, 'Link', assertNextValue);
  });

  test('should NOT set the Link next header and confirm it exists', async () => {
    await responseHandler(mockRequest, mockResponse, nextMiddleware);
    expect(mockResponse.set).toHaveBeenCalledTimes(1);
  });
});
