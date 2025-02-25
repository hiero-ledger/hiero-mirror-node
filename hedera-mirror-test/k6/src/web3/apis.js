// SPDX-License-Identifier: Apache-2.0

import exec from 'k6/execution';
import {textSummary} from 'https://jslib.k6.io/k6-summary/0.0.3/index.js';

import {getTestReportFilename, markdownReport} from '../lib/common.js';
import {funcs, options, scenarioDurationGauge, scenarios} from './test/index.js';

function handleSummary(data) {
  return {
    stdout: textSummary(data, {indent: ' ', enableColors: true}),
    [getTestReportFilename()]: markdownReport(data, false, funcs, scenarios),
  };
}

function run() {
  const scenario = exec.scenario;
  funcs[scenario.name]();
  scenarioDurationGauge.add(Date.now() - scenario.startTime, {scenario: scenario.name});
}

export {handleSummary, options, run};
