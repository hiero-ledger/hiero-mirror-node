// SPDX-License-Identifier: Apache-2.0

import {jest} from '@jest/globals';

import limitConcurrency from '../concurrencyLimiter';
import {TooManyRequestsError} from '../errors';

// A handler whose invocations stay in flight until released, so concurrency can be controlled by the test
const pendingHandler = () => {
  const releases = [];
  const handler = jest.fn(
    () =>
      new Promise((resolve, reject) => {
        releases.push({resolve, reject});
      })
  );
  return {handler, releases};
};

const applies = (req) => req.query.limited === true;
const limitedRequest = {query: {limited: true}};
const unlimitedRequest = {query: {}};
const res = {};

describe('limitConcurrency', () => {
  test('rejects matching requests above max and allows them once a slot is freed', async () => {
    const {handler, releases} = pendingHandler();
    const limited = limitConcurrency(handler, {applies, max: 2, name: 'test'});

    const first = limited(limitedRequest, res);
    const second = limited(limitedRequest, res);
    await expect(limited(limitedRequest, res)).rejects.toThrow(TooManyRequestsError);
    await expect(limited(limitedRequest, res)).rejects.toThrow('Too many concurrent test requests, please retry later');
    expect(handler).toHaveBeenCalledTimes(2);

    releases[0].resolve();
    await first;
    const third = limited(limitedRequest, res);
    expect(handler).toHaveBeenCalledTimes(3);

    releases[1].resolve();
    releases[2].resolve();
    await Promise.all([second, third]);
  });

  test('frees the slot when the handler throws', async () => {
    const {handler, releases} = pendingHandler();
    const limited = limitConcurrency(handler, {applies, max: 1, name: 'test'});

    const failing = limited(limitedRequest, res);
    releases[0].reject(new Error('query failed'));
    await expect(failing).rejects.toThrow('query failed');

    const next = limited(limitedRequest, res);
    expect(handler).toHaveBeenCalledTimes(2);
    releases[1].resolve();
    await next;
  });

  test('does not count requests the limit does not apply to', async () => {
    const {handler, releases} = pendingHandler();
    const limited = limitConcurrency(handler, {applies, max: 1, name: 'test'});

    const pending = [limited(unlimitedRequest, res), limited(unlimitedRequest, res), limited(limitedRequest, res)];
    expect(handler).toHaveBeenCalledTimes(3);
    await expect(limited(limitedRequest, res)).rejects.toThrow(TooManyRequestsError);

    releases.forEach((release) => release.resolve());
    await Promise.all(pending);
  });

  test('passes next through to the handler', async () => {
    const handler = jest.fn();
    const next = jest.fn();
    const limited = limitConcurrency(handler, {applies, max: 1, name: 'test'});

    await limited(limitedRequest, res, next);
    await limited(unlimitedRequest, res, next);
    expect(handler).toHaveBeenNthCalledWith(1, limitedRequest, res, next);
    expect(handler).toHaveBeenNthCalledWith(2, unlimitedRequest, res, next);
  });

  test('max of 0 disables the limit', () => {
    const handler = jest.fn();
    expect(limitConcurrency(handler, {applies, max: 0, name: 'test'})).toBe(handler);
  });
});
