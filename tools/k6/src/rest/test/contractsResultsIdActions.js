// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestTestScenarioBuilder} from '../libex/common.js';
import {actionListName} from '../libex/constants.js';

const urlTag = '/contracts/results/{id}/actions';

const getUrl = (testParameters) =>
  `/contracts/results/${testParameters['DEFAULT_CONTRACT_RESULT_HASH']}/actions?limit=${testParameters['DEFAULT_LIMIT']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('contractsResultsIdActions') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_CONTRACT_RESULT_HASH')
  .check('Contracts Results id Actions OK', (r) => isValidListResponse(r, actionListName))
  .build();

export {getUrl, options, run, setup};
