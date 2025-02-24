// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestTestScenarioBuilder} from '../libex/common.js';
import {stateListName} from '../libex/constants.js';

const urlTag = '/contracts/{id}/state';

const getUrl = (testParameters) =>
  `/contracts/${testParameters['DEFAULT_CONTRACT_ID']}/state?limit=${testParameters['DEFAULT_LIMIT']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('contractsIdState') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_CONTRACT_ID')
  .check('Contracts id state OK', (r) => isValidListResponse(r, stateListName))
  .build();

export {getUrl, options, run, setup};
