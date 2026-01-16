// SPDX-License-Identifier: Apache-2.0

// Logger
import log4js from 'log4js';

log4js.configure({
  appenders: {
    console: {
      layout: {
        pattern: '%d{yyyy-MM-ddThh:mm:ss.SSSO} %p %m',
        type: 'pattern',
      },
      type: 'stdout',
    },
  },
  categories: {
    default: {
      appenders: ['console'],
      level: 'info',
    },
  },
});
global.logger = log4js.getLogger();

export default logger;
