// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {TestScenarioBuilder} from '../../lib/common.js';
import {setupTestParameters} from '../libex/parameters.js';

const payload = JSON.stringify({metadata: {}});
const urlTag = '/rosetta/network/list';

const {options, run} = new TestScenarioBuilder()
  .name('networkList') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = testParameters.baseUrl + urlTag;
    return http.post(url, payload);
  })
  .check('NetworkList OK', (r) => r.status === 200)
  .build();

export {options, run};

export const setup = setupTestParameters;
