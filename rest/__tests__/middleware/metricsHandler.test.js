// SPDX-License-Identifier: Apache-2.0

import {EventEmitter} from 'events';

import {metricsHandler} from '../../middleware/metricsHandler';

const createResponse = () => {
  const res = new EventEmitter();
  res.statusCode = 200;
  res.locals = {};
  res.writableFinished = false;
  res.write = () => true;
  res.end = () => {
    res.writableFinished = true;
    res.emit('finish');
    res.emit('close');
    return res;
  };
  res.set = () => res;
  res.status = () => res;
  res.send = () => res;
  return res;
};

const createRequest = () => ({
  url: '/api/v1/transactions',
  method: 'GET',
  headers: {},
  baseUrl: '/api/v1',
  route: {path: '/transactions'},
});

const scrape = async (middleware) => {
  let body = '';
  const res = {
    set: () => res,
    status: () => res,
    send: (payload) => {
      body = payload;
      return res;
    },
  };
  await middleware({url: '/swagger/metrics/', method: 'GET', headers: {}}, res, () => {});
  return body;
};

describe('metricsHandler', () => {
  test('records a client close when the response never finishes', async () => {
    const middleware = metricsHandler();
    const res = createResponse();
    middleware(createRequest(), res, () => {});

    res.emit('close');

    const body = await scrape(middleware);
    expect(body).toContain('code="499"');
    expect(body).toMatch(/api_request_duration_milliseconds.*code="499"/);
    expect(body).not.toContain('code="200"');
  });

  test('records a finished response once when close follows finish', async () => {
    const middleware = metricsHandler();
    const res = createResponse();
    middleware(createRequest(), res, () => {});

    res.statusCode = 200;
    res.end();

    const body = await scrape(middleware);
    expect(body).toContain('code="200"');
    expect(body).not.toContain('code="499"');
  });
});
