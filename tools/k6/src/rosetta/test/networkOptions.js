// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {TestScenarioBuilder} from '../../lib/common.js';
import {setupTestParameters} from '../libex/parameters.js';

const urlTag = '/rosetta/network/options';

const {options, run} = new TestScenarioBuilder()
  .name('networkOptions') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = testParameters.baseUrl + urlTag;
    const payload = JSON.stringify({
      network_identifier: testParameters.networkIdentifier,
      metadata: {},
    });
    return http.post(url, payload);
  })
  .check('NetworkOptions OK', (r) => r.status === 200)
  .build();

export {options, run};

export const setup = setupTestParameters;
