// SPDX-License-Identifier: Apache-2.0

import basicAuth from 'basic-auth';
import httpContext from 'express-http-context';
import tsscmp from 'tsscmp';

import config from '../config.js';
import {httpStatusCodes, userLimitLabel} from '../constants.js';

const findUser = (username, password) => {
  const users = config.users || [];
  return users.find((user) => tsscmp(user.username, username) && tsscmp(user.password, password)) || null;
};

const authHandler = async (req, res) => {
  const credentials = basicAuth(req);

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
