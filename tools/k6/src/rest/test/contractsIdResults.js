// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestTestScenarioBuilder} from '../libex/common.js';
import {resultListName} from '../libex/constants.js';

const urlTag = '/contracts/{id}/results';

const getUrl = (testParameters) =>
  `/contracts/${testParameters['DEFAULT_CONTRACT_ID']}/results?limit=${testParameters['DEFAULT_LIMIT']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('contractsIdResults') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_CONTRACT_ID')
  .check('Contracts id results OK', (r) => isValidListResponse(r, resultListName))
  .build();

export {getUrl, options, run, setup};
