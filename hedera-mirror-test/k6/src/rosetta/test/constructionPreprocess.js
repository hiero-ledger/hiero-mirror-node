// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {TestScenarioBuilder} from '../../lib/common.js';
import {setupTestParameters} from '../libex/parameters.js';

const urlTag = '/rosetta/construction/preprocess';

const {options, run} = new TestScenarioBuilder()
  .name('constructionPreprocess') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const payload = JSON.stringify({
      network_identifier: testParameters.networkIdentifier,
      operations: testParameters.operations,
    });
    const url = testParameters.baseUrl + urlTag;
    return http.post(url, payload);
  })
  .check('ConstructionPreprocess OK', (r) => r.status === 200)
  .build();

export {options, run};

export const setup = setupTestParameters;
