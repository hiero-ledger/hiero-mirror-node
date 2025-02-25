// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestTestScenarioBuilder} from '../libex/common.js';
import {accountListName} from '../libex/constants.js';

const urlTag = '/accounts';

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('rampUp') // use unique scenario name among all tests
  .tags({url: urlTag})
  .scenario({
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      {
        duration: __ENV.DEFAULT_RAMPUP_DURATION || __ENV.DEFAULT_DURATION,
        target: __ENV.DEFAULT_RAMPUP_VUS || __ENV.DEFAULT_VUS,
      },
    ],
    gracefulRampDown: '0s',
  })
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${urlTag}?limit=${testParameters['DEFAULT_LIMIT']}`;
    return http.get(url);
  })
  .check('Accounts OK', (r) => isValidListResponse(r, accountListName))
  .build();

export {options, run, setup};
