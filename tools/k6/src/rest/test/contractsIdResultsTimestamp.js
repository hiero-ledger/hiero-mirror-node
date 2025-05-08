// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isSuccess, RestTestScenarioBuilder} from '../libex/common.js';

const urlTag = '/contracts/{id}/results/{timestamp}';

const getUrl = (testParameters) =>
  `/contracts/${testParameters['DEFAULT_CONTRACT_ID']}/results/${testParameters['DEFAULT_CONTRACT_TIMESTAMP']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('contractsIdResultsTimestamp') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_CONTRACT_ID', 'DEFAULT_CONTRACT_TIMESTAMP')
  .check('Contracts id results timestamp OK', isSuccess)
  .build();

export {getUrl, options, run, setup};
