// SPDX-License-Identifier: Apache-2.0

import exec from 'k6/execution';
import {textSummary} from 'https://jslib.k6.io/k6-summary/0.0.3/index.js';

import {getTestReportFilename, markdownReport} from '../lib/common.js';
import {funcs, getUrlFuncs, options, requiredParameters, scenarioDurationGauge, scenarios} from './test/index.js';
import {setupTestParameters} from './libex/parameters.js';

function handleSummary(data) {
  return {
    stdout: textSummary(data, {indent: ' ', enableColors: true}),
    [getTestReportFilename()]: markdownReport(data, true, funcs, scenarios, getUrlFuncs),
  };
}

function run(testParameters) {
  const scenario = exec.scenario;
  funcs[scenario.name](testParameters);
  scenarioDurationGauge.add(Date.now() - scenario.startTime, {scenario: scenario.name});
}

export {handleSummary, options, run};

export const setup = () => setupTestParameters(requiredParameters);
