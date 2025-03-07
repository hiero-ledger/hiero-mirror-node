// SPDX-License-Identifier: Apache-2.0

import {jest} from '@jest/globals';
import matchers from 'jest-extended';
import log4js from 'log4js';

global.logger = log4js.getLogger();

expect.extend(matchers); // add matchers from jest-extended
jest.setTimeout(4000);

// set test configuration file path
process.env.CONFIG_PATH = '__tests__';

beforeEach(() => {
  logger.info(expect.getState().currentTestName);
});
