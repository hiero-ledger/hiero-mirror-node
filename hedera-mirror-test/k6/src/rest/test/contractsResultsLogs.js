// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestTestScenarioBuilder} from '../libex/common.js';
import {logListName} from '../libex/constants.js';

const urlTag = '/contracts/results/logs';

const getUrl = (testParameters) => `/contracts/results/logs?limit=${testParameters['DEFAULT_LIMIT']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('contractsResultsLogs') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .check('Contracts Results Logs OK', (r) => isValidListResponse(r, logListName))
  .build();

export {getUrl, options, run, setup};
