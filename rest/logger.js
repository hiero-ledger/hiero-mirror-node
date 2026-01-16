// SPDX-License-Identifier: Apache-2.0

import httpContext from 'express-http-context';
import log4js from 'log4js';
import * as constants from './constants';

const configureLogger = (logLevel = 'info') => {
  log4js.configure({
    appenders: {
      console: {
        layout: {
          pattern: '%d{yyyy-MM-ddThh:mm:ss.SSSO} %p %x{requestId} %m',
          type: 'pattern',
          tokens: {
            requestId: (e) => httpContext.get(constants.requestIdLabel) || 'Startup',
          },
        },
        type: 'stdout',
      },
    },
    categories: {
      default: {
        appenders: ['console'],
        level: logLevel,
      },
    },
  });
  global.logger = log4js.getLogger();
};

export default configureLogger;
