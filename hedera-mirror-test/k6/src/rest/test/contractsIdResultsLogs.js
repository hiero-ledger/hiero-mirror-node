// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestTestScenarioBuilder} from '../libex/common.js';
import {logListName} from '../libex/constants.js';

const urlTag = '/contracts/{id}/results/logs';

const getUrl = (testParameters) =>
  `/contracts/${testParameters['DEFAULT_CONTRACT_ID']}/results/logs?limit=${testParameters['DEFAULT_LIMIT']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('contractsIdResultsLogs') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_CONTRACT_ID')
  .check('Contracts id results logs OK', (r) => isValidListResponse(r, logListName))
  .build();

export {getUrl, options, run, setup};
