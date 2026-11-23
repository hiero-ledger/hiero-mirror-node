// SPDX-License-Identifier: Apache-2.0

import {TooManyRequestsError} from '../errors';

/**
 * Creates a concurrency limiter that wraps route handlers so that at most max matching requests run concurrently in this
 * process/pod across every handler it wraps. Excess requests are rejected immediately with TooManyRequestsError (429)
 * instead of waiting for a database connection. The slot is held until the handler settles rather than until the
 * response ends, so a client disconnecting cannot free a slot while its query is still running.
 *
 * Applying the limit after Express routing means it covers every URL form and entry point that reaches the handler.
 *
 * @param {Object} options
 * @param {Function} options.applies returns true if the request counts toward the limit
 * @param {number} options.max the maximum number of concurrent matching requests, 0 disables the limit
 * @param {string} options.name the name used in the error message
 * @return {Function} a function that wraps a route handler with the shared limit
 */
const createConcurrencyLimiter = ({applies, max, name}) => {
  if (max === 0) {
    return (handler) => handler;
  }

  let active = 0;
  return (handler) => async (req, res, next) => {
    if (!applies(req)) {
      return handler(req, res, next);
    }

    if (active >= max) {
      throw new TooManyRequestsError(`Too many concurrent ${name} requests, please retry later`);
    }

    active += 1;
    try {
      return await handler(req, res, next);
    } finally {
      active -= 1;
    }
  };
};

export default createConcurrencyLimiter;
