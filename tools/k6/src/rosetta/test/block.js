// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {TestScenarioBuilder} from '../../lib/common.js';
import {setupTestParameters} from '../libex/parameters.js';

const urlTag = '/rosetta/block';

const {options, run} = new TestScenarioBuilder()
  .name('block') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = testParameters.baseUrl + urlTag;
    const payload = JSON.stringify({
      block_identifier: testParameters.blockIdentifier,
      network_identifier: testParameters.networkIdentifier,
    });
    return http.post(url, payload);
  })
  .check('Block OK', (r) => r.status === 200)
  .build();

export {options, run};

export const setup = setupTestParameters;
