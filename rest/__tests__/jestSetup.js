// SPDX-License-Identifier: Apache-2.0

import {jest} from '@jest/globals';
import matchers from 'jest-extended';
import log4js from 'log4js';

global.logger = log4js.getLogger();

expect.extend(matchers); // add matchers from jest-extended
jest.setTimeout(4000);

if (process.env.CI) {
  jest.retryTimes(3, {logErrorsBeforeRetry: true});
}

// set test configuration file path
process.env.CONFIG_PATH = '__tests__';

beforeEach(() => {
  logger.info(expect.getState().currentTestName);
});

const defaultPrepareStackTrace = Error.prepareStackTrace;

Error.prepareStackTrace = (error, structuredStackTrace) => {
  if (defaultPrepareStackTrace) {
    // the defaultPrepareStackTrace in tests oftentimes gets the function name wrong, however the line numbers are
    // correct; the line number from structuredStackTrace[].getLineNumber is way off. So the logic here first gets the
    // stacktrace with correct line numbers and then patches the function name.
    const defaultStackTrace = defaultPrepareStackTrace(error, structuredStackTrace);
    const lines = defaultStackTrace.split('\n');
    if (lines.length === 1) {
      return defaultStackTrace;
    }

    return [
      lines[0],
      ...lines.slice(1).map((line, index) => {
        if (index >= structuredStackTrace.length) {
          return line;
        }
        const functionName = structuredStackTrace[index].getFunctionName();
        return line.replace(/at\s+[^ ]+\s/, `at ${functionName} `);
      }),
    ].join('\n');
  }

  return structuredStackTrace.length === 0
    ? `${error.name}`
    : `${error.name}\n    at ${structuredStackTrace.join('\n    at ')}`;
};
