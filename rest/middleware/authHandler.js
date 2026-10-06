// SPDX-License-Identifier: Apache-2.0

import {parse} from 'basic-auth';
import httpContext from 'express-http-context';
import tsscmp from 'tsscmp';

import config from '../config.js';
import {httpStatusCodes, userLimitLabel} from '../constants.js';

const findUser = (username, password) => {
  const users = config.users || [];
  let match = null;

  // Constant-work comparison: evaluate both tsscmp calls for every configured user and
  // never break early, so the number of HMAC operations is independent of whether the
  // supplied username matches. Short-circuiting here leaks username validity via timing.
  for (const user of users) {
    const usernameMatches = tsscmp(user.username, username);
    const passwordMatches = tsscmp(user.password, password);
    if (usernameMatches && passwordMatches) {
      match = user;
    }
  }

  return match;
};

const authHandler = async (req, res) => {
  const credentials = parse(req.headers.authorization || '');

  if (!credentials) {
    return;
  }

  const user = findUser(credentials.name, credentials.pass);
  if (!user) {
    res.status(httpStatusCodes.UNAUTHORIZED.code).json({
      _status: {
        messages: [{message: 'Invalid credentials'}],
      },
    });
    return;
  }

  if (user.limit !== undefined && user.limit > 0) {
    httpContext.set(userLimitLabel, user.limit);
    logger.debug(`Authenticated user ${user.username} with custom limit ${user.limit}`);
  }
};

export {authHandler};
