// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isSuccess, RestTestScenarioBuilder} from '../libex/common.js';

const urlTag = '/contracts/results/{id}';

const getUrl = (testParameters) => `/contracts/results/${testParameters['DEFAULT_CONTRACT_RESULT_HASH']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('contractsResultsId') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_CONTRACT_RESULT_HASH')
  .check('Contracts Results id OK', isSuccess)
  .build();

export {getUrl, options, run, setup};
