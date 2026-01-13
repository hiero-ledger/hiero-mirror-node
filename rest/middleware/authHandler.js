// SPDX-License-Identifier: Apache-2.0

import httpContext from 'express-http-context';

import config from '../config.js';
import {httpStatusCodes, userLimitLabel} from '../constants.js';

const extractBasicAuthCredentials = (authHeader) => {
  if (!authHeader || !authHeader.startsWith('Basic ')) {
    return null;
  }

  try {
    const base64Credentials = authHeader.substring(6);
    const credentials = Buffer.from(base64Credentials, 'base64').toString('utf-8');
    const colonIndex = credentials.indexOf(':');

    if (colonIndex === -1) {
      return null;
    }

    const username = credentials.substring(0, colonIndex);
    const password = credentials.substring(colonIndex + 1);

    if (!username || password === undefined) {
      return null;
    }

    return {username, password};
  } catch (err) {
    logger.debug(`Failed to decode Basic Auth credentials: ${err}`);
    return null;
  }
};

const findUser = (username, password) => {
  const users = config.users || [];
  return users.find((user) => user.username === username && user.password === password) || null;
};

const authHandler = async (req, res) => {
  const authHeader = req.headers.authorization;

  if (!authHeader) {
    return;
  }

  const credentials = extractBasicAuthCredentials(authHeader);
  if (!credentials) {
    res.status(httpStatusCodes.UNAUTHORIZED.code).json({
      _status: {
        messages: [{message: 'Invalid Authorization header format'}],
      },
    });
    return;
  }

  const user = findUser(credentials.username, credentials.password);
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
